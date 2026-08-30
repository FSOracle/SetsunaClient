package tritium.ncm.music;

import com.github.setsuna.Constants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.setsuna.Setsuna;
import tritium.ncm.OptionsUtil;
import tritium.ncm.api.CloudMusicApi;
import tritium.ncm.music.dto.Music;
import tritium.ncm.music.dto.PlayList;
import tritium.ncm.music.dto.User;
import tritium.utils.Tuple;
import tritium.utils.json.JsonUtils;
import tritium.utils.network.HttpUtils;
import tritium.utils.other.WrappedInputStream;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class CloudMusic {

    private static final Object PLAYBACK_LOCK = new Object();
    private static volatile long playbackGeneration;

    public static volatile AudioPlayer player;
    public static List<Music> playList = new ArrayList<>();
    public static int curIdx = 0;
    public static volatile Music currentlyPlaying;
    public static volatile Thread playThread;

    public static User profile;
    public static List<PlayList> playLists = new CopyOnWriteArrayList<>();
    public static List<Long> likeList = new CopyOnWriteArrayList<>();
    public static PlayMode playMode = PlayMode.Sequential;
    public static Quality quality = Quality.STANDARD;
    public static PlayList playedFrom = null;

    private static final String LOGIN_FILE_NAME = "netease-login.json";
    private static final Path LEGACY_COOKIE_FILE = Path.of(
            System.getProperty("user.home"), ".setsuna", "NCMCookie.txt");

    public static volatile boolean downloading;
    public static volatile double downloadProgress;
    public static volatile String downloadSpeed = "0 b/s";
    public static volatile String status = "Not loaded";

    public static volatile boolean dontAdd;
    static volatile boolean doBreak;
    static AtomicBoolean playing = new AtomicBoolean(true);

    public static void initNCM() {
        QqMusic.init();
        String cookie = loadCookie();
        if (cookie.isBlank()) {
            status = "Not logged in";
            return;
        }
        loadNCM(cookie);
    }

    public static synchronized String loadCookie() {
        Path loginFile = loginFile();
        try {
            if (Files.exists(loginFile)) {
                JsonObject login = JsonUtils.toJsonObject(Files.readString(loginFile, StandardCharsets.UTF_8));
                JsonElement cookie = login.get("cookie");
                return cookie != null && cookie.isJsonPrimitive() && cookie.getAsJsonPrimitive().isString()
                        ? cookie.getAsString().trim() : "";
            }

            String legacyCookie = loadLegacyCookie();
            if (!legacyCookie.isBlank()) {
                saveCookie(legacyCookie);
            }
            return legacyCookie;
        } catch (Exception e) {
            Constants.LOGGER.warn("Failed to load NetEase cookie", e);
            return "";
        }
    }

    public static synchronized void saveCookie(String cookie) {
        Path loginFile = loginFile();
        Path temporary = null;
        try {
            if (cookie == null || cookie.isBlank()) {
                Files.deleteIfExists(loginFile);
                Files.deleteIfExists(LEGACY_COOKIE_FILE);
                return;
            }

            Files.createDirectories(loginFile.getParent());
            JsonObject login = new JsonObject();
            login.addProperty("version", 1);
            login.addProperty("cookie", cookie.trim());
            login.addProperty("updatedAt", System.currentTimeMillis());

            temporary = Files.createTempFile(loginFile.getParent(), ".netease-login-", ".tmp");
            Files.writeString(temporary, JsonUtils.toJsonString(login), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, loginFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, loginFile, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(LEGACY_COOKIE_FILE);
        } catch (Exception e) {
            Constants.LOGGER.warn("Failed to save NetEase cookie", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static Path loginFile() {
        return Setsuna.mc().gameDirectory.toPath().resolve(Setsuna.MOD_ID).resolve(LOGIN_FILE_NAME);
    }

    private static String loadLegacyCookie() {
        try {
            if (!Files.exists(LEGACY_COOKIE_FILE)) {
                return "";
            }
            return Files.readString(LEGACY_COOKIE_FILE, StandardCharsets.UTF_8)
                    .lines().findFirst().orElse("").trim();
        } catch (Exception error) {
            Constants.LOGGER.warn("Failed to migrate the legacy NetEase cookie", error);
            return "";
        }
    }

    public static void loadNCM(String cookie) {
        OptionsUtil.setCookie(cookie);
        profile = getUserProfile();
        if (profile == null) {
            status = "Cookie invalid or expired";
            OptionsUtil.setCookie("");
            saveCookie("");
            return;
        }

        saveCookie(cookie);
        playLists = loadUserPlaylists();
        likeList = likeList();
        status = "Logged in as " + profile.getName();
    }

    private static List<PlayList> loadUserPlaylists() {
        List<PlayList> userPlaylists = new CopyOnWriteArrayList<>();
        int page = 0;
        while (page < 20) {
            try {
                List<PlayList> pagePlaylists = profile.playLists(page, 30);
                if (pagePlaylists.isEmpty()) {
                    break;
                }
                userPlaylists.addAll(pagePlaylists);
                if (pagePlaylists.size() < 30) {
                    break;
                }
                page++;
            } catch (Exception e) {
                Constants.LOGGER.warn("Failed to load NetEase playlists", e);
                break;
            }
        }
        return userPlaylists;
    }

    public static void onStop() {
        QqMusic.saveCredentials();
        if (!OptionsUtil.getCookie().isBlank()) {
            saveCookie(OptionsUtil.getCookie());
        }
        Thread thread;
        AudioPlayer stoppedPlayer;
        synchronized (PLAYBACK_LOCK) {
            playbackGeneration++;
            doBreak = true;
            playing.set(false);
            thread = playThread;
            playThread = null;
            stoppedPlayer = player;
            player = null;
            currentlyPlaying = null;
            downloading = false;
        }
        if (stoppedPlayer != null) {
            try {
                stoppedPlayer.stopForSwitch();
            } catch (RuntimeException e) {
                Constants.LOGGER.warn("Failed to stop NetEase playback", e);
            }
        }
        if (thread != null) {
            thread.interrupt();
            if (thread != Thread.currentThread()) {
                try {
                    thread.join(2000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (stoppedPlayer != null) {
            stoppedPlayer.close();
        }
    }

    public static void prev() {
        if (playList.isEmpty()) return;
        if (curIdx - 1 >= 0) {
            dontAdd = true;
            curIdx--;
            stopCurrentPlayback();
        } else if (playMode == PlayMode.LoopInList) {
            dontAdd = true;
            curIdx = playList.size() - 1;
            stopCurrentPlayback();
        }
    }

    public static void next() {
        if (playList.isEmpty()) return;
        if (curIdx + 1 > playList.size() - 1 && playMode == PlayMode.Sequential) {
            return;
        }
        dontAdd = true;
        curIdx++;
        if (curIdx >= playList.size()) curIdx = 0;
        stopCurrentPlayback();
    }

    private static void stopCurrentPlayback() {
        if (player != null) {
            player.stopForSwitch();
        }
        playing.set(false);
    }

    public static void play(List<Music> songs, int startIdx) {
        if (songs == null || songs.isEmpty()) {
            status = "No songs to play";
            return;
        }
        List<Music> safeSongs = new ArrayList<>(songs);
        if (playMode == PlayMode.Random) {
            Music selected = startIdx >= 0 && startIdx < safeSongs.size() ? safeSongs.get(startIdx) : null;
            Collections.shuffle(safeSongs);
            startIdx = selected == null ? 0 : safeSongs.indexOf(selected);
        }
        if (startIdx < 0 || startIdx >= safeSongs.size()) {
            startIdx = 0;
        }

        long generation;
        Thread previousThread;
        synchronized (PLAYBACK_LOCK) {
            generation = ++playbackGeneration;
            doBreak = true;
            playing.set(false);
            previousThread = playThread;
            playThread = null;
            if (player != null) {
                try {
                    player.stopForSwitch();
                } catch (RuntimeException e) {
                    Constants.LOGGER.warn("Failed to stop previous NetEase playback", e);
                }
            }
        }
        try {
            if (previousThread != null) {
                previousThread.interrupt();
                if (previousThread != Thread.currentThread()) {
                    previousThread.join(2000L);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (PLAYBACK_LOCK) {
            if (playbackGeneration != generation) {
                return;
            }
            playList = safeSongs;
            curIdx = startIdx;
            doBreak = false;
            playing.set(false);
            PlayThread thread = new PlayThread(generation);
            thread.setName("Tritium Music Play Thread");
            playThread = thread;
            thread.start();
        }
    }

    private static class PlayThread extends Thread {
        private final long generation;

        private PlayThread(long generation) {
            this.generation = generation;
        }

        private boolean isActiveGeneration() {
            return generation == playbackGeneration && playThread == this && !doBreak;
        }

        @Override
        public void run() {
            try {
                while (isActiveGeneration() && !playList.isEmpty()
                        && curIdx >= 0 && curIdx < playList.size()) {
                    Music song = playList.get(curIdx);
                    currentlyPlaying = song;
                    status = "Playing " + song.getName();

                    Tuple<String, String> playUrl;
                    try {
                        playUrl = song.getPlayUrl();
                    } catch (Exception e) {
                        if (!isActiveGeneration()) {
                            return;
                        }
                        Constants.LOGGER.warn("Failed to resolve NetEase song {}", song.getName(), e);
                        status = "Play failed: " + song.getName();
                        updateCurIdx();
                        continue;
                    }

                    if (!isActiveGeneration()) {
                        return;
                    }
                    if (playUrl == null || playUrl.getA() == null || playUrl.getA().isBlank()) {
                        status = "No playable url: " + song.getName();
                        updateCurIdx();
                        continue;
                    }

                    try {
                        File musicFile = getMusicFile(playUrl, song);
                        if (!isActiveGeneration()) {
                            return;
                        }

                        AudioPlayer activePlayer = createAndStartPlayer(musicFile);
                        if (activePlayer == null) {
                            return;
                        }

                        while (isActiveGeneration() && !activePlayer.isFinished()) {
                            Thread.sleep(120L);
                        }
                    } catch (Exception e) {
                        if (!isActiveGeneration()) {
                            return;
                        }
                        Constants.LOGGER.warn("Failed to play NetEase song {}", song.getName(), e);
                        status = "Play failed: " + song.getName();
                    }

                    if (!isActiveGeneration()) {
                        return;
                    }
                    updateCurIdx();
                }
            } finally {
                synchronized (PLAYBACK_LOCK) {
                    if (generation == playbackGeneration && playThread == this) {
                        playThread = null;
                    }
                }
            }
        }

        /**
         * AudioPlayer construction may decode the file and block. Keep it out
         * of the lifecycle lock so stop/play can invalidate this generation,
         * then re-check before the candidate is allowed to make sound.
         */
        private AudioPlayer createAndStartPlayer(File musicFile) {
            if (!isActiveGeneration()) {
                return null;
            }

            AudioPlayer prepared = new AudioPlayer(musicFile);
            AudioPlayer replaced = null;
            boolean started = false;
            try {
                if (!isActiveGeneration()) {
                    return null;
                }

                synchronized (PLAYBACK_LOCK) {
                    if (!isActiveGeneration()) {
                        return null;
                    }

                    replaced = player;
                    if (replaced != null) {
                        prepared.setVolume(replaced.getVolume());
                    }

                    // Starting and publishing are one lifecycle operation. A
                    // stop or newer play generation cannot interleave here.
                    if (!isActiveGeneration()) {
                        return null;
                    }
                    prepared.play();
                    player = prepared;
                    playing.set(true);
                    started = true;
                }
            } finally {
                if (!started) {
                    closePlayer(prepared, "discard stale NetEase player");
                }
            }

            if (replaced != null && replaced != prepared) {
                closePlayer(replaced, "close replaced NetEase player");
            }
            return prepared;
        }

        private void updateCurIdx() {
            if (!isActiveGeneration() || playList.isEmpty()) return;
            if (playMode == PlayMode.LoopSingle) {
                return;
            }
            if (!dontAdd) curIdx++;
            dontAdd = false;
            if (curIdx >= playList.size()) {
                if (playMode == PlayMode.Sequential) {
                    doBreak = true;
                } else {
                    curIdx = 0;
                }
            }
        }
    }

    private static void closePlayer(AudioPlayer audioPlayer, String action) {
        try {
            audioPlayer.close();
        } catch (Exception e) {
            Constants.LOGGER.warn("Failed to {}", action, e);
        }
    }

    private static File getMusicFile(Tuple<String, String> playUrl, Music song) throws Exception {
        String type = playUrl.getB();
        if (type == null || type.isBlank()) type = "mp3";
        File musicCacheDir = new File("MusicCache");
        if (!musicCacheDir.exists()) {
            musicCacheDir.mkdirs();
        }
        File music = new File(musicCacheDir, song.cacheKey() + "_" + quality.getQuality() + "." + type);
        if (!music.exists()) {
            downloadMusic(playUrl.getA(), music);
        }
        return music;
    }

    private static void downloadMusic(String playUrl, File music) throws Exception {
        downloading = true;
        downloadProgress = 0;
        downloadSpeed = "0 b/s";
        try (InputStream stream = new WrappedInputStream(HttpUtils.get(playUrl, null), new WrappedInputStream.ProgressListener() {
            private int lastBytesRead;
            private long lastCheck = System.currentTimeMillis();

            @Override
            public void onProgress(double progress) {
                downloadProgress = progress;
                if (progress >= 1.0) {
                    downloading = false;
                }
            }

            @Override
            public void bytesRead(int bytesRead) {
                long now = System.currentTimeMillis();
                if (now - lastCheck >= 500L) {
                    int diff = (int) ((bytesRead - lastBytesRead) * (1000.0 / Math.max(1L, now - lastCheck)));
                    downloadSpeed = formatSize(diff) + "/s";
                    lastBytesRead = bytesRead;
                    lastCheck = now;
                }
            }
        }); OutputStream output = Files.newOutputStream(music.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            stream.transferTo(output);
        } finally {
            downloading = false;
        }
    }

    private static String formatSize(long size) {
        if (size < 1024) return size + " B";
        double kb = size / 1024.0;
        if (kb < 1024) return "%.2f KB".formatted(kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return "%.2f MB".formatted(mb);
        return "%.2f GB".formatted(mb / 1024.0);
    }

    public static String qrCodeLogin() {
        return qrCodeLogin(ignored -> {
        });
    }

    public static String qrCodeLogin(Consumer<QrLoginState> stateListener) {
        Consumer<QrLoginState> listener = stateListener == null ? ignored -> {
        } : stateListener;
        try {
            listener.accept(QrLoginState.CREATING);
            String key = qrKey();
            if (!QRCodeGenerator.generateAndLoadTexture("https://music.163.com/login?codekey=" + key)) {
                throw new IllegalStateException("Unable to generate the NetEase QR code");
            }
            listener.accept(QrLoginState.WAITING_SCAN);

            long deadline = System.currentTimeMillis() + 180_000L;
            while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
                JsonObject json = CloudMusicApi.loginQrCheck(key).toJsonObject();
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int code = json.has("code") ? json.get("code").getAsInt() : 0;
                switch (code) {
                    case 800 -> {
                        listener.accept(QrLoginState.EXPIRED);
                        return "";
                    }
                    case 801 -> listener.accept(QrLoginState.WAITING_SCAN);
                    case 802 -> listener.accept(QrLoginState.WAITING_CONFIRMATION);
                    case 803 -> {
                        if (!json.has("cookie") || json.get("cookie").isJsonNull()) {
                            listener.accept(QrLoginState.FAILED);
                            return "";
                        }
                        String cookie = normalizeLoginCookie(json.get("cookie").getAsString());
                        if (cookie.isBlank()) {
                            listener.accept(QrLoginState.FAILED);
                            return "";
                        }
                        OptionsUtil.setCookie(cookie);
                        saveCookie(cookie);
                        listener.accept(QrLoginState.AUTHORIZED);
                        return cookie;
                    }
                    default -> {
                        if (code >= 400 || code == 0) {
                            listener.accept(QrLoginState.FAILED);
                            return "";
                        }
                    }
                }

                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!Thread.currentThread().isInterrupted()) {
                listener.accept(QrLoginState.EXPIRED);
            }
            return "";
        } catch (RuntimeException error) {
            Constants.LOGGER.warn("NetEase QR login failed", error);
            listener.accept(QrLoginState.FAILED);
            return "";
        }
    }

    private static String normalizeLoginCookie(String cookie) {
        StringBuilder sb = new StringBuilder();
        for (String item : cookie.split(";")) {
            String part = item.trim();
            if (part.startsWith("MUSIC_U=") || part.startsWith("__csrf=")) {
                if (!sb.isEmpty()) sb.append("; ");
                sb.append(part);
            }
        }
        return sb.isEmpty() ? cookie : sb.toString();
    }

    public static User getUserProfile() {
        JsonObject jsonObject = CloudMusicApi.loginStatus().toJsonObject();
        JsonObject data = jsonObject.getAsJsonObject("data");
        if (data == null || !data.has("account") || data.get("account") instanceof JsonNull
                || !data.has("profile") || data.get("profile") instanceof JsonNull) {
            return null;
        }
        return JsonUtils.parse(data.getAsJsonObject("profile"), User.class);
    }

    public static List<Music> search(String keyWord) {
        List<Music> searchResults = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        JsonObject searchResponse = CloudMusicApi.cloudSearch(keyWord, CloudMusicApi.SearchType.Single).toJsonObject();
        JsonObject result = searchResponse.getAsJsonObject("result");
        JsonArray songs = result == null ? null : result.getAsJsonArray("songs");
        if (songs != null) {
            for (JsonElement song : songs) {
                JsonObject songObject = song.getAsJsonObject();
                searchResults.add(JsonUtils.parse(normalizeSong(songObject), Music.class));
                if (songObject.has("id") && !songObject.get("id").isJsonNull()) {
                    ids.add(songObject.get("id").getAsLong());
                }
            }
        }
        if (!ids.isEmpty()) {
            List<Music> detailResults = loadSongDetails(ids);
            if (!detailResults.isEmpty()) {
                return detailResults;
            }
        }
        return searchResults;
    }

    public static JsonObject normalizeSong(JsonObject song) {
        if (song.has("al") && song.has("ar") && song.has("dt")) {
            return normalizeSongCover(song);
        }

        JsonObject normalized = song.deepCopy();
        if (!normalized.has("al") && song.has("album")) {
            normalized.add("al", song.get("album"));
        }
        if (!normalized.has("ar") && song.has("artists")) {
            normalized.add("ar", song.get("artists"));
        }
        if (!normalized.has("dt") && song.has("duration")) {
            normalized.add("dt", song.get("duration"));
        }
        if (!normalized.has("alia") && song.has("alias")) {
            normalized.add("alia", song.get("alias"));
        }
        if (!normalized.has("tns") && song.has("transNames")) {
            normalized.add("tns", song.get("transNames"));
        }
        return normalizeSongCover(normalized);
    }

    private static JsonObject normalizeSongCover(JsonObject song) {
        JsonObject normalized = song.deepCopy();
        JsonObject album = getObject(normalized, "al");
        if (album == null) {
            album = getObject(normalized, "album");
        }

        String cover = firstString(normalized, "picUrl", "coverUrl", "coverImgUrl", "image", "blurPicUrl");
        if (album == null && !cover.isBlank()) {
            album = new JsonObject();
            album.addProperty("id", 0L);
            album.addProperty("name", "");
        }
        if (album != null) {
            if (!hasUsableString(album, "picUrl")) {
                String albumCover = cover.isBlank() ? firstString(album, "coverUrl", "coverImgUrl", "image", "blurPicUrl") : cover;
                if (!albumCover.isBlank()) {
                    album.addProperty("picUrl", albumCover);
                }
            }
            normalized.add("al", album);
        }
        return normalized;
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key).deepCopy();
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (hasUsableString(object, key)) {
                return object.get(key).getAsString();
            }
        }
        return "";
    }

    private static boolean hasUsableString(JsonObject object, String key) {
        return object.has(key)
                && !object.get(key).isJsonNull()
                && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isString()
                && !object.get(key).getAsString().isBlank()
                && !"null".equalsIgnoreCase(object.get(key).getAsString());
    }

    private static List<Music> loadSongDetails(List<Long> ids) {
        try {
            JsonObject detailResponse = CloudMusicApi.songDetail(ids).toJsonObject();
            JsonArray detailSongs = detailResponse.getAsJsonArray("songs");
            if (detailSongs == null || detailSongs.isEmpty()) {
                return Collections.emptyList();
            }

            List<Music> details = new ArrayList<>();
            for (JsonElement song : detailSongs) {
                details.add(JsonUtils.parse(normalizeSong(song.getAsJsonObject()), Music.class));
            }
            return details;
        } catch (Exception e) {
            Constants.LOGGER.warn("Failed to load NetEase song details for search covers", e);
            return Collections.emptyList();
        }
    }

    public static List<Long> likeList() {
        List<Long> list = new ArrayList<>();
        if (profile == null) return list;
        JsonObject json = CloudMusicApi.likeList(profile.getId()).toJsonObject();
        JsonArray ids = json.getAsJsonArray("ids");
        if (ids != null) {
            for (JsonElement id : ids) {
                list.add(id.getAsLong());
            }
        }
        return list;
    }

    public static String qrKey() {
        JsonObject json = CloudMusicApi.loginQrKey().toJsonObject();
        JsonObject data = json.getAsJsonObject("data");
        if (data == null || !data.has("unikey") || data.get("unikey").isJsonNull()) {
            throw new IllegalStateException("NetEase did not return a QR login key");
        }
        return data.get("unikey").getAsString();
    }

    public enum QrLoginState {
        IDLE,
        CREATING,
        WAITING_SCAN,
        WAITING_CONFIRMATION,
        AUTHORIZED,
        EXPIRED,
        FAILED
    }

    public enum PlayMode {
        Random("F"),
        LoopInList("I"),
        LoopSingle("L"),
        Sequential("G");

        private final String icon;

        PlayMode(String icon) {
            this.icon = icon;
        }

        public String getIcon() {
            return icon;
        }
    }
}
