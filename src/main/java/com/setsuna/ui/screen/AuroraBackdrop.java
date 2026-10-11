package com.setsuna.ui.screen;

import com.setsuna.render.SkijaUi;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Rect;

/** Vector backdrop shared by the Aurora home and sign-in screens. */
final class AuroraBackdrop {
    static final int TEXT = 0xFFF0F5F5;
    static final int MUTED = 0xFF98A9B5;
    static final int FAINT = 0xFF657985;
    static final int ACCENT = 0xFF99E5D5;
    private static final Paint GLOW_PAINT = new Paint().setAntiAlias(true);

    private AuroraBackdrop() {}

    static void draw(Canvas canvas, float width, float height, float time) {
        SkijaUi.gradientDiagonal(canvas, 0, 0, width, height, 0xFF101B27, 0xFF070C14, 0);
        float drift = (float) Math.sin(time * 0.13F) * width * 0.035F;
        glow(canvas, width * 0.78F + drift, height * 0.23F, Math.max(width, height) * 0.68F, 0x254FBDAD);
        glow(canvas, width * 0.2F, height * 0.94F, Math.max(width, height) * 0.6F, 0x233F629B);
        // Broad contour lines are cheap native paths; there is no fullscreen blur pass.
        try (PathBuilder path = new PathBuilder()) {
            for (int i = 0; i < 5; i++) {
                float offset = i * Math.max(22, height * 0.065F);
                path.moveTo(width * 0.32F, height + offset);
                path.cubicTo(width * 0.48F, height * 0.16F + offset,
                        width * 0.89F, height * 0.95F - offset,
                        width + 30, -height * 0.24F + offset);
            }
            try (Path outline = path.detach()) {
                SkijaUi.outlinePath(canvas, outline, 0.8F, 0x1267C9BB);
            }
        }
        SkijaUi.gradient(canvas, 0, 0, width, height, 0x00070C14, 0x80070C14, true, 0);
    }

    private static void glow(Canvas canvas, float x, float y, float radius, int color) {
        if (radius <= 0) return;
        try (Shader shader = Shader.makeRadialGradient(x, y, radius, new int[]{color, color & 0x00FFFFFF})) {
            GLOW_PAINT.setShader(shader);
            canvas.drawRect(Rect.makeXYWH(x - radius, y - radius, radius * 2, radius * 2), GLOW_PAINT);
        } finally {
            GLOW_PAINT.setShader(null);
        }
    }

    static void surface(Canvas canvas, UiControls.Box box, float radius) {
        SkijaUi.rounded(canvas, box.x(), box.y() + 3, box.width(), box.height(), radius, 0x38000000);
        SkijaUi.gradientDiagonal(canvas, box.x(), box.y(), box.width(), box.height(),
                0xED172731, 0xED101923, radius);
        SkijaUi.outline(canvas, box.x(), box.y(), box.width(), box.height(), radius, 0.75F, 0x286DA498);
        SkijaUi.line(canvas, box.x() + radius, box.y() + 0.5F,
                box.x() + box.width() - radius, box.y() + 0.5F, 0.7F, 0x3099E5D5);
    }

    static void mark(Canvas canvas, float x, float y, float size) {
        try (PathBuilder mark = new PathBuilder()) {
            mark.moveTo(x + size * 0.62F, y);
            mark.lineTo(x + size * 0.12F, y + size * 0.57F);
            mark.lineTo(x + size * 0.48F, y + size * 0.57F);
            mark.lineTo(x + size * 0.33F, y + size);
            mark.lineTo(x + size * 0.88F, y + size * 0.38F);
            mark.lineTo(x + size * 0.53F, y + size * 0.38F);
            mark.closePath();
            try (Path shape = mark.detach()) {
                SkijaUi.fillPath(canvas, shape, ACCENT);
            }
        }
    }
}
