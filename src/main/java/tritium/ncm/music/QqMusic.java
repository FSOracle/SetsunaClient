package tritium.ncm.music;

import com.github.setsuna.Constants;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.setsuna.Setsuna;
import top.fpsmaster.music.AudioQuality;
import top.fpsmaster.music.Lyric;
import top.fpsmaster.music.MusicPlaylist;
import top.fpsmaster.music.MusicSource;
import top.fpsmaster.music.QQMusicApi;
import top.fpsmaster.music.QQUserInfo;
import top.fpsmaster.music.QrCode;
import top.fpsmaster.music.QrLoginState;
import top.fpsmaster.music.SongUrl;
import top.fpsmaster.music.Track;
import tritium.ncm.music.dto.Music;
import tritium.ncm.music.dto.PlayList;
import tritium.utils.Tuple;
import tritium.utils.json.JsonUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;

/** Adapts Cadence's QQ Music data client to the existing Setsuna music UI. */
public final class QqMusic {

    private static final QQMusicApi API = new QQMusicApi();
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final String LOGIN_FILE_NAME = "qqmusic-login.json";

    public static volatile QQUserInfo profile;
    public static volatile List<PlayList> playLists = List.of();
    public static volatile String status = "Not logged in";

    private QqMusic() {
    }

    public static void init() {
        loadCredentials();
        if (!API.getLoggedIn()) {
            status = "Not logged in";
            return;
        }
        reloadAccount();
    }

    public static boolean isLoggedIn() {
        return profile != null && API.getLoggedIn();
    }

    public static boolean hasCredentials() {
        return API.getLoggedIn();
    }

    public static void reloadAccount() {
        if (!API.getLoggedIn()) {
            profile = null;
            playLists = List.of();
            status = "Not logged in";
            return;
        }
        QQUserInfo loaded = API.getUserInfo();
        if (loaded == null) {
            clearLogin();
            status = "QQ Music credentials invalid or expired";
            return;
        }
        profile = loaded;
        playLists = recommendations();
        saveCredentials();
        status = "Logged in as " + loaded.getNickname();
    }

    public static List<Music> search(String keyword) {
        try {
            return API.search(keyword, 30, 1).stream().map(Music::fromQqTrack).toList();
        } catch (RuntimeException primaryFailure) {
            Constants.LOGGER.debug("Cadence QQ search failed, using web search fallback", primaryFailure);
            return legacySearch(keyword).stream().map(Music::fromQqTrack).toList();
        }
    }

    public static List<Music> toplist() {
        return API.getToplist(26, 30).stream().map(Music::fromQqTrack).toList();
    }

    public static List<PlayList> recommendations() {
        return API.getRecommendPlaylists(12).stream().map(PlayList::fromQqPlaylist).toList();
    }

    public static List<Music> playlistTracks(String id) {
        return API.getPlaylistTracks(id, 100).stream().map(Music::fromQqTrack).toList();
    }

    public static Tuple<String, String> playUrl(Music music) {
        Track track = music.qqTrack();
        if (track == null) {
            return null;
        }
        SongUrl result = API.getSongUrl(track, cadenceQuality());
        if (!result.getAvailable()) {
            status = result.getReason() == null ? "QQ Music track is unavailable" : result.getReason();
            return null;
        }
        return new Tuple<>(result.getUrl(), result.getFormat().isBlank() ? "mp3" : result.getFormat());
    }

    public static Lyric lyrics(Music music) {
        Track track = music.qqTrack();
        return track == null ? null : API.getLyric(track);
    }

    public static QrCode createQrCode() {
        QrCode code = API.createQrCode();
        String content = code.getQrContent();
        int separator = content.indexOf(',');
        String encoded = separator >= 0 ? content.substring(separator + 1) : content;
        QRCodeGenerator.loadPng("qq:" + code.getKey(), Base64.getDecoder().decode(encoded));
        return code;
    }

    public static QrLoginState checkQrCode(QrCode code) {
        QrLoginState state = API.checkQrCode(code);
        if (state == QrLoginState.CONFIRMED) {
            saveCredentials();
        }
        return state;
    }

    public static void clearLogin() {
        API.clearLogin();
        profile = null;
        playLists = List.of();
        status = "Not logged in";
        try {
            Files.deleteIfExists(loginFile());
        } catch (Exception error) {
            Constants.LOGGER.warn("Failed to delete QQ Music credentials", error);
        }
    }

    static void loadCredentials() {
        try {
            Path file = loginFile();
            if (!Files.exists(file)) return;
            JsonObject login = JsonUtils.toJsonObject(Files.readString(file, StandardCharsets.UTF_8));
            API.setMusicid(string(login.get("musicid")));
            API.setMusicKey(string(login.get("musicKey")));
        } catch (Exception error) {
            Constants.LOGGER.warn("Failed to load QQ Music credentials", error);
        }
    }

    static void saveCredentials() {
        Path temporary = null;
        try {
            if (!API.getLoggedIn()) {
                Files.deleteIfExists(loginFile());
                return;
            }
            Path file = loginFile();
            Files.createDirectories(file.getParent());
            JsonObject login = new JsonObject();
            login.addProperty("version", 1);
            login.addProperty("musicid", API.getMusicid());
            login.addProperty("musicKey", API.getMusicKey());
            login.addProperty("updatedAt", System.currentTimeMillis());
            temporary = Files.createTempFile(file.getParent(), ".qqmusic-login-", ".tmp");
            Files.writeString(temporary, JsonUtils.toJsonString(login), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            Constants.LOGGER.warn("Failed to save QQ Music credentials", error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static AudioQuality cadenceQuality() {
        return switch (CloudMusic.quality) {
            case LOSSLESS, HIRES, JYEFFECT, SKY, JYMASTER -> AudioQuality.LOSSLESS;
            case HIGHER, EXHIGH -> AudioQuality.HIGH;
            case STANDARD -> AudioQuality.STANDARD;
        };
    }

    private static List<Track> legacySearch(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            URI uri = URI.create("https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
                    + "?p=1&n=30&format=json&w=" + encoded);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Referer", "https://y.qq.com/")
                    .header("User-Agent", "Mozilla/5.0 Setsuna/1.0")
                    .GET().build();
            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("QQ search HTTP " + response.statusCode());
            }
            JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
            return parseLegacySearch(root);
        } catch (Exception error) {
            throw new IllegalStateException("QQ Music search failed", error);
        }
    }

    static List<Track> parseLegacySearch(JsonObject root) {
        JsonObject data = object(root, "data");
        JsonObject song = object(data, "song");
        JsonArray list = song == null ? null : song.getAsJsonArray("list");
        if (list == null) return List.of();

        List<Track> tracks = new ArrayList<>();
        for (JsonElement element : list) {
            JsonObject item = element.getAsJsonObject();
            String id = text(item, "songid");
            String mid = text(item, "songmid");
            if (id.isBlank() || mid.isBlank()) continue;
            List<String> artists = new ArrayList<>();
            JsonArray singers = item.getAsJsonArray("singer");
            if (singers != null) {
                singers.forEach(singer -> {
                    String name = text(singer.getAsJsonObject(), "name");
                    if (!name.isBlank()) artists.add(name);
                });
            }
            String albumMid = text(item, "albummid");
            String cover = albumMid.isBlank() ? null
                    : "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg";
            JsonObject pay = object(item, "pay");
            boolean vip = pay != null && integer(pay, "payplay") == 1;
            tracks.add(new Track(MusicSource.QQ, id, mid, text(item, "songname"),
                    artists.isEmpty() ? "Unknown" : String.join(" / ", artists),
                    text(item, "albumname"), integer(item, "interval") * 1000L, cover, vip));
        }
        return tracks;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent == null || !parent.has(key) || !parent.get(key).isJsonObject()
                ? null : parent.getAsJsonObject(key);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value == null || value.isJsonNull() ? 0 : value.getAsInt();
    }

    private static String string(JsonElement element) {
        return element == null || element.isJsonNull() ? "" : element.getAsString().trim();
    }

    private static Path loginFile() {
        return Setsuna.mc().gameDirectory.toPath().resolve(Setsuna.MOD_ID).resolve(LOGIN_FILE_NAME);
    }
}
