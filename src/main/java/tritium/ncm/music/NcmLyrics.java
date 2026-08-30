package tritium.ncm.music;

import com.google.gson.JsonObject;
import tritium.ncm.api.CloudMusicApi;
import tritium.ncm.music.dto.Music;
import top.fpsmaster.music.Lyric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class NcmLyrics {

    private static String songKey = "";
    private static volatile boolean loading;
    private static volatile List<Line> lines = new CopyOnWriteArrayList<>();

    private NcmLyrics() {
    }

    public static void ensureLoaded(Music music) {
        if (music == null) return;
        String requestedKey = music.cacheKey();
        if (requestedKey.equals(songKey)) return;
        songKey = requestedKey;
        lines = new CopyOnWriteArrayList<>();
        loading = true;
        CompletableFuture.supplyAsync(() -> music.getProvider() == MusicProvider.QQ
                ? parseQqLyrics(QqMusic.lyrics(music))
                : parseLyrics(CloudMusicApi.lyricNew(music.getId()).toJsonObject())).thenAccept(result -> {
            if (requestedKey.equals(songKey)) {
                lines = new CopyOnWriteArrayList<>(result);
                loading = false;
            }
        }).exceptionally(error -> {
            if (requestedKey.equals(songKey)) loading = false;
            return null;
        });
    }

    public static boolean isLoading() {
        return loading;
    }

    public static List<Line> getLines() {
        return lines;
    }

    public static Line getCurrentLine(float millis) {
        List<Line> currentLines = lines;
        if (currentLines.isEmpty()) return null;
        return currentLines.get(currentIndex(millis));
    }

    public static int currentIndex(float millis) {
        int index = 0;
        List<Line> currentLines = lines;
        for (int i = 0; i < currentLines.size(); i++) {
            if (currentLines.get(i).timeMillis() <= millis) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private static List<Line> parseLyrics(JsonObject json) {
        List<Line> result = new ArrayList<>();
        JsonObject lrc = json == null ? null : json.getAsJsonObject("lrc");
        if (lrc == null || !lrc.has("lyric")) return result;
        String raw = lrc.get("lyric").getAsString();
        for (String rawLine : raw.split("\\R")) {
            List<Long> times = new ArrayList<>();
            int cursor = 0;
            while (cursor < rawLine.length() && rawLine.charAt(cursor) == '[') {
                int end = rawLine.indexOf(']', cursor);
                if (end < 0) break;
                long time = parseLyricTime(rawLine.substring(cursor + 1, end));
                if (time >= 0) times.add(time);
                cursor = end + 1;
            }
            String text = rawLine.substring(Math.min(cursor, rawLine.length())).trim();
            if (text.isBlank()) text = "...";
            for (Long time : times) {
                result.add(new Line(time, text));
            }
        }
        result.sort(Comparator.comparingLong(Line::timeMillis));
        return result;
    }

    private static List<Line> parseQqLyrics(Lyric lyric) {
        if (lyric == null || lyric.getLines() == null) return List.of();
        List<Line> result = new ArrayList<>();
        lyric.getLines().forEach(line -> {
            if (!line.isMetadata() && line.getText() != null && !line.getText().isBlank()) {
                result.add(new Line(line.getStartMs(), line.getText()));
            }
        });
        return result;
    }

    private static long parseLyricTime(String token) {
        try {
            String[] parts = token.split(":");
            if (parts.length != 2) return -1;
            long minutes = Long.parseLong(parts[0]);
            double seconds = Double.parseDouble(parts[1]);
            return minutes * 60_000L + Math.round(seconds * 1000.0);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public record Line(long timeMillis, String text) {
    }
}
