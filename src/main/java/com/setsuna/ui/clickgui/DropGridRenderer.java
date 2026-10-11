package com.setsuna.ui.clickgui;

import com.setsuna.render.SkijaUi;
import io.github.humbleui.skija.Canvas;

/** Shared grid visual used by the Drop ClickGUI and visual preset screens. */
public final class DropGridRenderer {

    public static final float STEP = 16.0F;

    private DropGridRenderer() {
    }

    public static void drawFull(Canvas canvas, float width, float height, float opacity, int accent) {
        float radius = (float) Math.hypot(width, height);
        draw(canvas, width, height, width * 0.5F, height * 0.5F, radius, opacity, accent);
    }

    public static void draw(Canvas canvas, float width, float height,
                            float centerX, float centerY, float radius,
                            float opacity, int accent) {
        if (opacity <= 0.002F || radius <= 0.002F) return;
        float radiusSquared = radius * radius;
        for (float lineX = 0.0F; lineX <= width + 0.1F; lineX += STEP) {
            float dx = lineX - centerX;
            if (Math.abs(dx) > radius) continue;
            float halfHeight = (float) Math.sqrt(Math.max(0.0F, radiusSquared - dx * dx));
            int index = Math.round(lineX / STEP);
            boolean major = index % 4 == 0;
            int alpha = Math.round((major ? 68.0F : 32.0F) * opacity);
            int color = major ? withAlpha(accent, alpha) : argb(alpha, 214, 205, 224);
            SkijaUi.line(canvas, lineX, Math.max(0.0F, centerY - halfHeight),
                    lineX, Math.min(height, centerY + halfHeight),
                    major ? 0.9F : 0.52F, color);
        }
        for (float lineY = 0.0F; lineY <= height + 0.1F; lineY += STEP) {
            float dy = lineY - centerY;
            if (Math.abs(dy) > radius) continue;
            float halfWidth = (float) Math.sqrt(Math.max(0.0F, radiusSquared - dy * dy));
            int index = Math.round(lineY / STEP);
            boolean major = index % 4 == 0;
            int alpha = Math.round((major ? 68.0F : 32.0F) * opacity);
            int color = major ? withAlpha(accent, alpha) : argb(alpha, 214, 205, 224);
            SkijaUi.line(canvas, Math.max(0.0F, centerX - halfWidth), lineY,
                    Math.min(width, centerX + halfWidth), lineY,
                    major ? 0.9F : 0.52F, color);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }
}
