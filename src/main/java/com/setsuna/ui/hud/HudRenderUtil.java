package com.setsuna.ui.hud;

import com.setsuna.render.SkijaUi;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.PathDirection;
import io.github.humbleui.skija.PathMeasure;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.awt.Color;

/** Small shared primitives for HUD surfaces and width-aware labels. */
final class HudRenderUtil {

    enum BorderMode {
        Single,
        Gradient,
        Rainbow
    }

    private static final Paint BORDER_PAINT = new Paint()
            .setAntiAlias(true)
            .setMode(PaintMode.STROKE)
            .setStrokeCap(PaintStrokeCap.ROUND)
            .setStrokeJoin(PaintStrokeJoin.ROUND);
    private static final Paint SURFACE_PAINT = new Paint().setAntiAlias(true);

    private HudRenderUtil() {
    }

    static void panel(Canvas canvas, float x, float y, float width, float height, int opacity) {
        int alpha = clamp(opacity, 0, 255);
        coloredPanel(canvas, x, y, width, height, UiTheme.withAlpha(UiTheme.SURFACE, alpha));
    }

    static void coloredPanel(Canvas canvas, float x, float y, float width, float height, int color) {
        if (width <= 1.0F || height <= 1.0F) return;
        int alpha = (color >>> 24) & 0xFF;
        float radius = Math.min(UiTheme.RADIUS_SMALL, Math.min(width, height) * 0.5F);
        SkijaUi.rounded(canvas, x, y, width, height, radius,
                UiTheme.withAlpha(UiTheme.BORDER, Math.min(224, Math.max(104, alpha + 16))));
        SkijaUi.rounded(canvas, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F,
                Math.max(1.0F, radius - 1.0F), color);
    }

    static void surface(Canvas canvas, float x, float y, float width, float height, int opacity) {
        surface(canvas, x, y, width, height, opacity, HudFusionManager.Edges.NONE);
    }

    static void surface(Canvas canvas, float x, float y, float width, float height, int opacity,
                        HudFusionManager.Edges edges) {
        coloredSurface(canvas, x, y, width, height,
                UiTheme.withAlpha(UiTheme.SURFACE, clamp(opacity, 0, 255)), edges);
    }

    static void coloredSurface(Canvas canvas, float x, float y, float width, float height, int color) {
        coloredSurface(canvas, x, y, width, height, color, HudFusionManager.Edges.NONE);
    }

    static void coloredSurface(Canvas canvas, float x, float y, float width, float height,
                               int color, HudFusionManager.Edges edges) {
        if (width <= 1.0F || height <= 1.0F) return;
        float radius = Math.min(UiTheme.RADIUS_SMALL, Math.min(width, height) * 0.5F);
        coloredSurface(canvas, x, y, width, height, radius, color, edges);
    }

    static void coloredSurface(Canvas canvas, float x, float y, float width, float height,
                               float radius, int color, HudFusionManager.Edges edges) {
        if (width <= 1.0F || height <= 1.0F) return;
        SURFACE_PAINT.setColor(color);
        canvas.drawRRect(fusionShape(x, y, width, height, radius, edges), SURFACE_PAINT);
    }

    static void coloredPath(Canvas canvas, Path path, int color) {
        if (path == null) return;
        SURFACE_PAINT.setColor(color);
        canvas.drawPath(path, SURFACE_PAINT);
    }

    static void blur(Canvas canvas, float x, float y, float width, float height,
                     float radius, float strength) {
        blur(canvas, x, y, width, height, radius, strength, HudFusionManager.Edges.NONE);
    }

    static void blur(Canvas canvas, float x, float y, float width, float height,
                     float radius, float strength, HudFusionManager.Edges edges) {
        if (width <= 1.0F || height <= 1.0F || strength <= 0.01F) return;
        SkijaRenderer.drawBlurredBackdrop(canvas,
                fusionShape(x, y, width, height, radius, edges),
                x, y, width, height, strength);
    }

    static void border(Canvas canvas, float x, float y, float width, float height,
                       float radius, float strokeWidth, float progress,
                       BorderMode mode, int singleColor, int startColor, int endColor,
                       int alpha) {
        border(canvas, x, y, width, height, radius, strokeWidth, progress,
                mode, singleColor, startColor, endColor, alpha,
                HudFusionManager.Edges.NONE);
    }

    static void border(Canvas canvas, float x, float y, float width, float height,
                       float radius, float strokeWidth, float progress,
                       BorderMode mode, int singleColor, int startColor, int endColor,
                       int alpha, HudFusionManager.Edges edges) {
        float clamped = clamp(progress, 0.0F, 1.0F);
        if (clamped <= 0.001F || width <= strokeWidth || height <= strokeWidth) return;

        float inset = strokeWidth * 0.5F;
        float left = x + (edges.left() ? 0.0F : inset);
        float top = y + (edges.top() ? 0.0F : inset);
        float right = x + width - (edges.right() ? 0.0F : inset);
        float bottom = y + height - (edges.bottom() ? 0.0F : inset);
        RRect bounds = fusionShape(left, top, right - left, bottom - top,
                Math.max(0.0F, radius - inset), edges);
        try (PathBuilder outlineBuilder = new PathBuilder()) {
            outlineBuilder.addRRect(bounds, PathDirection.CLOCKWISE, 0);
            try (Path outline = outlineBuilder.detach()) {
                Path visible = outline;
                if (clamped < 0.999F) {
                    try (PathMeasure measure = new PathMeasure(outline, true);
                         PathBuilder visibleBuilder = new PathBuilder()) {
                        float length = measure.getLength();
                        if (!measure.getSegment(0.0F, length * clamped, visibleBuilder, true)) return;
                        visible = visibleBuilder.detach();
                    }
                }
                try {
                    int clipSave = canvas.save();
                    clipAttachedEdges(canvas, x, y, width, height, strokeWidth, edges);
                    try {
                        drawBorderPath(canvas, visible, x, y, width, height, strokeWidth,
                                mode, singleColor, startColor, endColor, alpha);
                    } finally {
                        canvas.restoreToCount(clipSave);
                    }
                } finally {
                    if (visible != outline) visible.close();
                }
            }
        }
    }

    private static RRect fusionShape(float x, float y, float width, float height,
                                     float radius, HudFusionManager.Edges edges) {
        float safeRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
        float topLeft = edges.left() || edges.top() ? 0.0F : safeRadius;
        float topRight = edges.right() || edges.top() ? 0.0F : safeRadius;
        float bottomRight = edges.right() || edges.bottom() ? 0.0F : safeRadius;
        float bottomLeft = edges.left() || edges.bottom() ? 0.0F : safeRadius;
        return RRect.makeComplexXYWH(x, y, width, height, new float[]{
                topLeft, topLeft, topRight, topRight,
                bottomRight, bottomRight, bottomLeft, bottomLeft
        });
    }

    private static void clipAttachedEdges(Canvas canvas, float x, float y,
                                          float width, float height, float strokeWidth,
                                          HudFusionManager.Edges edges) {
        float half = Math.max(0.5F, strokeWidth * 0.58F);
        if (edges.left()) {
            canvas.clipRect(Rect.makeLTRB(x - half, y, x + half, y + height),
                    ClipMode.DIFFERENCE, false);
        }
        if (edges.right()) {
            canvas.clipRect(Rect.makeLTRB(x + width - half, y,
                    x + width + half, y + height), ClipMode.DIFFERENCE, false);
        }
        if (edges.top()) {
            canvas.clipRect(Rect.makeLTRB(x, y - half, x + width, y + half),
                    ClipMode.DIFFERENCE, false);
        }
        if (edges.bottom()) {
            canvas.clipRect(Rect.makeLTRB(x, y + height - half,
                    x + width, y + height + half), ClipMode.DIFFERENCE, false);
        }
    }

    private static void drawBorderPath(Canvas canvas, Path path,
                                       float x, float y, float width, float height,
                                       float strokeWidth, BorderMode mode,
                                       int singleColor, int startColor, int endColor,
                                       int alpha) {
        BORDER_PAINT.setStrokeWidth(strokeWidth).setShader(null);
        if (mode == BorderMode.Single) {
            BORDER_PAINT.setColor(withAlpha(singleColor, alpha));
            canvas.drawPath(path, BORDER_PAINT);
            return;
        }

        int[] colors = mode == BorderMode.Gradient
                ? new int[]{withAlpha(startColor, alpha), withAlpha(endColor, alpha)}
                : rainbowColors(alpha);
        try (Shader shader = Shader.makeLinearGradient(
                x, y, x + width, y + height, colors)) {
            BORDER_PAINT.setShader(shader).setColor(0xFFFFFFFF);
            canvas.drawPath(path, BORDER_PAINT);
        } finally {
            BORDER_PAINT.setShader(null).setColor(0xFFFFFFFF);
        }
    }

    static void borderPath(Canvas canvas, Path path, Rect bounds, float strokeWidth,
                           BorderMode mode, int singleColor, int startColor, int endColor,
                           int alpha) {
        if (path == null || bounds == null || strokeWidth <= 0.0F) return;
        drawBorderPath(canvas, path, bounds.getLeft(), bounds.getTop(),
                bounds.getWidth(), bounds.getHeight(), strokeWidth,
                mode, singleColor, startColor, endColor, alpha);
    }

    private static int[] rainbowColors(int alpha) {
        float phase = (System.currentTimeMillis() % 4000L) / 4000.0F;
        int[] colors = new int[5];
        for (int index = 0; index < colors.length; index++) {
            int rgb = Color.HSBtoRGB((phase + index * 0.25F) % 1.0F, 0.82F, 1.0F);
            colors[index] = withAlpha(rgb, alpha);
        }
        return colors;
    }

    private static int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    static void strip(Canvas canvas, float x, float y, float width, float height, int opacity) {
        if (width <= 1.0F || height <= 1.0F) return;
        int alpha = clamp(opacity, 0, 255);
        SkijaUi.rounded(canvas, x, y, width, height, 3.0F,
                UiTheme.withAlpha(UiTheme.BORDER, Math.min(190, Math.max(88, alpha))));
        SkijaUi.rounded(canvas, x + 0.75F, y + 0.75F, width - 1.5F, height - 1.5F, 2.25F,
                UiTheme.withAlpha(UiTheme.SURFACE, Math.min(232, alpha)));
    }

    static void hairline(Canvas canvas, float x, float y, float width, float height, int color) {
        if (width <= 0.0F || height <= 0.0F) return;
        SkijaUi.fill(canvas, x, y, width, height, color);
    }

    static void progress(Canvas canvas, float x, float y, float width, float height,
                         float progress, int color) {
        if (width <= 0.0F || height <= 0.0F) return;
        float radius = Math.min(2.0F, height * 0.5F);
        SkijaUi.rounded(canvas, x, y, width, height, radius,
                UiTheme.withAlpha(UiTheme.BORDER, 180));
        float filled = width * clamp(progress, 0.0F, 1.0F);
        if (filled > 0.0F) {
            SkijaUi.rounded(canvas, x, y, filled, height, Math.min(radius, filled * 0.5F), color);
        }
    }

    static boolean overlaps(float startA, float sizeA, float startB, float sizeB) {
        return startA < startB + sizeB && startA + sizeA > startB;
    }

    /** Maps a persisted 0..1000 coordinate onto the available top-left travel. */
    static float normalizedPosition(IntSetting setting, float screenSize, float elementSize) {
        float travel = Math.max(0.0F, screenSize - elementSize);
        return travel * clamp(setting.get() / 1000.0F, 0.0F, 1.0F);
    }

    static void setNormalizedPosition(IntSetting setting, float pixel, float screenSize, float elementSize) {
        float travel = Math.max(0.0F, screenSize - elementSize);
        int value = travel <= 0.0F ? 0 : Math.round(clamp(pixel / travel, 0.0F, 1.0F) * 1000.0F);
        setting.set(value);
    }

    static String fit(String value, float maxWidth, float fontSize, boolean bold) {
        String text = value == null ? "" : value;
        if (maxWidth <= 0.0F) return "";
        if (width(text, fontSize, bold) <= maxWidth) return text;

        String suffix = "...";
        float suffixWidth = width(suffix, fontSize, bold);
        if (suffixWidth > maxWidth) return "";

        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String candidate = text.substring(0, mid) + suffix;
            if (width(candidate, fontSize, bold) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low) + suffix;
    }

    static float width(String text, float fontSize, boolean bold) {
        return bold ? SkijaUi.boldTextWidth(text, fontSize) : SkijaUi.textWidth(text, fontSize);
    }

    /** Uses one wide digit for every numeric glyph so changing values keep a stable width. */
    static String stableDigits(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            result.append(character >= '0' && character <= '9' ? '8' : character);
        }
        return result.toString();
    }

    static float clamp(float value, float min, float max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
