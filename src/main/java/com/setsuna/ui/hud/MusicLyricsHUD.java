package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.modules.player.NetEaseMusicModule;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import tritium.ncm.music.AudioPlayer;
import tritium.ncm.music.CloudMusic;
import tritium.ncm.music.NcmLyrics;
import tritium.ncm.music.dto.Music;

import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Compact lyric overlay with an optional NetEase-style mini player. */
public final class MusicLyricsHUD extends EpsilonHudModule {

    public static final MusicLyricsHUD INSTANCE = new MusicLyricsHUD();

    private static final HttpClient COVER_HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private static final Paint COVER_PAINT = new Paint().setAntiAlias(true).setDither(true);
    private static final String CONTROL_PREVIOUS = "H";
    private static final String CONTROL_PAUSE = "A";
    private static final String CONTROL_PLAY = "B";
    private static final String CONTROL_NEXT = "E";
    private static final int COVER_CACHE_LIMIT = 24;

    public enum AlignMode { Left, Center, Right }

    public final EnumSetting<AlignMode> align = add(new EnumSetting<>("Align", AlignMode.Center));
    public final DoubleSetting widthSetting = add(new DoubleSetting("Width", 280.0, 160.0, 520.0, 5.0));
    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.6, 1.6, 0.05));
    public final BooleanSetting background = add(new BooleanSetting("Background", true));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color",
            new Color(UiTheme.withAlpha(UiTheme.SURFACE, 174), true), true)
            .visibleWhen(background::get));
    public final BooleanSetting border = add(new BooleanSetting("Border", true));
    public final ColorSetting borderColor = add(new ColorSetting("Border Color",
            new Color(UiTheme.withAlpha(UiTheme.BORDER, 190), true), true)
            .visibleWhen(border::get));
    public final DoubleSetting borderWidth = add(new DoubleSetting(
            "Border Width", 1.0, 0.5, 4.0, 0.1).visibleWhen(border::get));
    public final DoubleSetting borderRadius = add(new DoubleSetting(
            "Border Radius", 4.0, 0.0, 18.0, 0.5)
            .visibleWhen(() -> background.get() || border.get()));
    public final BooleanSetting musicPlayer = add(new BooleanSetting("Music Player", false));
    public final ColorSetting activeColor = add(new ColorSetting("Text Color", new Color(245, 248, 247, 245)));
    public final ColorSetting inactiveColor = add(new ColorSetting("Next Line Color", new Color(166, 178, 174, 175)));

    private final Map<String, Image> coverImages = new LinkedHashMap<>();
    private final Set<String> loadingCovers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> coverRetryAt = new ConcurrentHashMap<>();

    private String lastLine = "";
    private float changeAnimation;

    private MusicLyricsHUD() {
        super("Music HUD", 500, 900, 280.0F, 34.0F);
    }

    @Override
    public String id() {
        return "music-lyrics-hud";
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        SkijaUi.withFont(SkijaUi.CLIENT_FONT, () -> {
            if (musicPlayer.get()) {
                renderPlayer(event);
            } else {
                renderLyrics(event);
            }
        });
    }

    private void renderLyrics(Render2DEvent event) {
        Music music = CloudMusic.currentlyPlaying;
        if (music == null || CloudMusic.player == null) {
            updateBounds(defaultWidth(), defaultHeight());
            return;
        }

        NcmLyrics.ensureLoaded(music);
        List<NcmLyrics.Line> lines = NcmLyrics.getLines();
        if (lines.isEmpty()) {
            updateBounds(defaultWidth(), defaultHeight());
            return;
        }

        float s = scale.get().floatValue();
        float width = Math.min(widthSetting.get().floatValue() * s, Math.max(4.0F, event.width() - 12.0F));
        float height = 34.0F * s;
        float x = renderX(event, width);
        float y = renderY(event, height);
        updateBounds(width, height);

        int index;
        try {
            index = NcmLyrics.currentIndex(CloudMusic.player.getCurrentTimeMillis());
        } catch (Throwable ignored) {
            return;
        }
        index = Math.max(0, Math.min(lines.size() - 1, index));
        String current = lines.get(index).text();
        String next = index + 1 < lines.size() ? lines.get(index + 1).text() : "";
        if (!current.equals(lastLine)) {
            lastLine = current;
            changeAnimation = 1.0F;
        }
        changeAnimation += (0.0F - changeAnimation) * 0.18F;

        Canvas canvas = event.canvas();
        float radius = Math.min(borderRadius.get().floatValue() * s, height * 0.5F);
        if (background.get()) {
            HudRenderUtil.coloredSurface(canvas, x, y, width, height, radius,
                    backgroundColor.argb(), HudFusionManager.Edges.NONE);
        }
        if (border.get()) {
            int color = borderColor.argb();
            HudRenderUtil.border(canvas, x, y, width, height, radius,
                    borderWidth.get().floatValue() * s, 1.0F,
                    HudRenderUtil.BorderMode.Single, color, color, color,
                    (color >>> 24) & 0xFF);
        }

        float inset = background.get() ? 12.0F * s : 4.0F * s;
        float available = width - inset * 2.0F;
        float currentSize = (8.4F + changeAnimation * 0.45F) * s;
        float nextSize = 6.8F * s;
        drawAligned(canvas, current, x + inset, y + 3.0F * s - changeAnimation * s,
                available, 15.0F * s, currentSize, activeColor.argb());
        if (!next.isBlank()) {
            drawAligned(canvas, next, x + inset, y + 18.0F * s,
                    available, 11.0F * s, nextSize, inactiveColor.argb());
        }
    }

    private void renderPlayer(Render2DEvent event) {
        float s = scale.get().floatValue();
        float width = Math.min(Math.max(widthSetting.get().floatValue() * s, 240.0F * s),
                Math.max(4.0F, event.width() - 12.0F));
        float height = 74.0F * s;
        float x = renderX(event, width);
        float y = renderY(event, height);
        updateBounds(width, height);

        Music current = CloudMusic.currentlyPlaying;
        AudioPlayer player = CloudMusic.player;
        NetEaseMusicModule.ColorPreset preset = NetEaseMusicModule.INSTANCE.colorPreset.get();
        float radius = Math.min(borderRadius.get().floatValue() * s, height * 0.5F);
        int gradientStart = UiTheme.withAlpha(preset.accent(), 215);
        int gradientEnd = UiTheme.withAlpha(preset.secondary(), 200);

        Canvas canvas = event.canvas();
        SkijaUi.dropShadowRounded(canvas, x, y, width, height, radius, 4.0F * s, 16.0F * s, 0x98000000);
        SkijaUi.gradientDiagonal(canvas, x, y, width, height, gradientStart, gradientEnd, radius);
        if (background.get()) {
            SkijaUi.rounded(canvas, x, y, width, height, radius,
                    UiTheme.withAlpha(preset.background(), 124));
        }
        if (border.get()) {
            HudRenderUtil.border(canvas, x, y, width, height, radius,
                    Math.max(0.6F, borderWidth.get().floatValue() * s), 1.0F,
                    HudRenderUtil.BorderMode.Single, preset.card(), preset.card(), preset.card(), 210);
        }

        float pad = 10.0F * s;
        float coverSize = Math.max(32.0F * s, Math.min(44.0F * s, height - 16.0F * s));
        float coverX = x + pad;
        float coverY = y + (height - coverSize) * 0.5F;
        float coverRadius = Math.min(6.0F * s, coverSize * 0.25F);
        Box cover = new Box(coverX, coverY, coverSize, coverSize);
        if (current != null) {
            drawCover(canvas, "music-player:" + current.cacheKey(), current.getCoverUrl(96), cover, coverRadius);
        } else {
            SkijaUi.rounded(canvas, cover.x(), cover.y(), cover.width(), cover.height(), coverRadius,
                    UiTheme.withAlpha(preset.card(), 192));
            SkijaUi.boldText(canvas, "NCM", cover.x() + cover.width() * 0.18F, cover.y() + 7.0F * s,
                    cover.height(), UiTheme.withAlpha(preset.secondary(), 220), 7.0F * s);
        }

        float infoX = coverX + coverSize + 10.0F * s;
        float controlSize = 10.0F * s;
        float controlGap = 4.0F * s;
        float controlsWidth = controlSize * 3.0F + controlGap * 2.0F;
        float controlsX = x + width - pad - controlsWidth;
        float titleLimit = Math.max(0.0F, controlsX - infoX - 8.0F * s);

        String title = current == null ? "Not Playing" : current.getName();
        String artist = current == null ? "NetEase / QQ Music" : current.getArtistsName();
        SkijaUi.boldText(canvas, HudRenderUtil.fit(title, titleLimit, 8.8F * s, true),
                infoX, y + 11.0F * s, 14.0F * s, UiTheme.TEXT, 8.8F * s);
        SkijaUi.text(canvas, HudRenderUtil.fit(artist, titleLimit, 6.8F * s, false),
                infoX, y + 26.0F * s, 11.0F * s, 0xD9FFFFFF, 6.8F * s);
        String lyric = currentLyric(current, player);
        if (!lyric.isBlank()) {
            float lyricLimit = Math.max(0.0F, x + width - pad - infoX);
            SkijaUi.text(canvas, HudRenderUtil.fit(lyric, lyricLimit, 6.7F * s, false),
                    infoX, y + 39.0F * s, 10.0F * s, 0xC8FFFFFF, 6.7F * s);
        }

        boolean playing = current != null && player != null && !player.isPausing();
        int controlBg = UiTheme.withAlpha(preset.card(), 160);
        drawControl(canvas, CONTROL_PREVIOUS, controlsX, y + 8.0F * s,
                controlSize, controlBg, 0xF0FFFFFF, 6.2F * s);
        drawControl(canvas, playing ? CONTROL_PAUSE : CONTROL_PLAY, controlsX + controlSize + controlGap,
                y + 8.0F * s, controlSize, controlBg, 0xF0FFFFFF, 6.2F * s);
        drawControl(canvas, CONTROL_NEXT, controlsX + (controlSize + controlGap) * 2.0F,
                y + 8.0F * s, controlSize, controlBg, 0xF0FFFFFF, 6.2F * s);

        String time = formatDuration(currentTime(player)) + " / " + formatDuration(totalTime(current, player));
        float timeWidth = SkijaUi.textWidth(time, 6.2F * s);
        SkijaUi.text(canvas, time, x + width - pad - timeWidth, y + height - 23.0F * s,
                10.0F * s, 0xD0FFFFFF, 6.2F * s);

        float progressX = infoX;
        float progressY = y + height - 11.0F * s;
        float progressWidth = Math.max(0.0F, x + width - pad - progressX);
        SkijaUi.rounded(canvas, progressX, progressY, progressWidth, 3.0F * s, 1.5F * s,
                UiTheme.withAlpha(preset.card(), 78));
        float progress = playbackProgress(player);
        if (progressWidth > 0.0F) {
            SkijaUi.rounded(canvas, progressX, progressY, progressWidth * progress, 3.0F * s,
                    1.5F * s, preset.accent());
        }
    }

    private void drawAligned(Canvas canvas, String raw, float x, float y, float width,
                             float height, float size, int color) {
        String value = HudRenderUtil.fit(raw, width, size, false);
        float textWidth = SkijaUi.textWidth(value, size);
        float textX = switch (align.get()) {
            case Left -> x;
            case Center -> x + (width - textWidth) * 0.5F;
            case Right -> x + width - textWidth;
        };
        SkijaUi.text(canvas, value, textX, y, height, color, size);
    }

    private void drawControl(Canvas canvas, String glyph, float x, float y, float size,
                             int backgroundColor, int color, float iconSize) {
        float radius = Math.max(2.0F, size * 0.28F);
        SkijaUi.rounded(canvas, x, y, size, size, radius, backgroundColor);
        float glyphWidth = SkijaUi.iconWidth(glyph, iconSize, SkijaUi.IconSet.TRITIUM_MUSIC);
        SkijaUi.icon(canvas, glyph, x + (size - glyphWidth) * 0.5F, y, size, color, iconSize,
                SkijaUi.IconSet.TRITIUM_MUSIC);
    }

    @Override
    public int editorColor() {
        return activeColor.argb();
    }

    @Override
    public String getInfo() {
        return musicPlayer.get() ? "PLAYER" : align.get().name();
    }

    private void drawCover(Canvas canvas, String key, String url, Box box, float radius) {
        Image image = coverImages.get(key);
        if (image == null) {
            SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), radius, cardColor());
            requestCover(key, url);
            return;
        }
        int save = canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(box.x(), box.y(), box.width(), box.height(), radius));
            canvas.drawImageRect(image,
                    Rect.makeXYWH(0, 0, image.getWidth(), image.getHeight()),
                    Rect.makeXYWH(box.x(), box.y(), box.width(), box.height()),
                    SamplingMode.MITCHELL, COVER_PAINT, true);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void requestCover(String key, String url) {
        if (url == null || url.isBlank() || loadingCovers.contains(key)
                || coverRetryAt.getOrDefault(key, 0L) > System.currentTimeMillis()) {
            return;
        }
        loadingCovers.add(key);
        CompletableFuture.supplyAsync(() -> downloadCover(url)).whenComplete((bytes, error) ->
                mc.execute(() -> {
                    loadingCovers.remove(key);
                    if (error != null || bytes == null || bytes.length == 0) {
                        coverRetryAt.put(key, System.currentTimeMillis() + 30_000L);
                        return;
                    }
                    try {
                        Image image = Image.makeFromEncoded(bytes);
                        if (image == null) {
                            coverRetryAt.put(key, System.currentTimeMillis() + 30_000L);
                            return;
                        }
                        Image old = coverImages.put(key, image);
                        if (old != null) {
                            old.close();
                        }
                        trimCoverCache();
                    } catch (RuntimeException decodeError) {
                        coverRetryAt.put(key, System.currentTimeMillis() + 30_000L);
                    }
                }));
    }

    private static byte[] downloadCover(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Setsuna/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = COVER_HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void trimCoverCache() {
        while (coverImages.size() > COVER_CACHE_LIMIT) {
            String key = coverImages.keySet().iterator().next();
            Image image = coverImages.remove(key);
            if (image != null) {
                image.close();
            }
        }
    }

    private int cardColor() {
        return NetEaseMusicModule.INSTANCE.colorPreset.get().card();
    }

    private float playbackProgress(AudioPlayer player) {
        if (player == null || player.getTotalTimeMillis() <= 0) {
            return 0;
        }
        return HudRenderUtil.clamp((float) player.getCurrentTimeMillis() / player.getTotalTimeMillis(), 0, 1);
    }

    private float currentTime(AudioPlayer player) {
        return player == null ? 0 : player.getCurrentTimeMillis();
    }

    private float totalTime(Music music, AudioPlayer player) {
        return player == null || player.getTotalTimeMillis() <= 0
                ? (music == null ? 0 : music.getDuration()) : player.getTotalTimeMillis();
    }

    private String currentLyric(Music music, AudioPlayer player) {
        if (music == null || player == null) {
            return "";
        }
        try {
            NcmLyrics.ensureLoaded(music);
            List<NcmLyrics.Line> lines = NcmLyrics.getLines();
            if (lines.isEmpty()) {
                return "";
            }
            int index = NcmLyrics.currentIndex(player.getCurrentTimeMillis());
            if (index < 0 || index >= lines.size()) {
                return "";
            }
            return lines.get(index).text();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String formatDuration(float millis) {
        long seconds = Math.max(0, Math.round(millis / 1000.0));
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private record Box(float x, float y, float width, float height) {
    }
}
