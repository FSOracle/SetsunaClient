package com.setsuna.ui.clickgui;

import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.ColorSetting;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;

import java.awt.Color;
import java.util.IdentityHashMap;
import java.util.Map;

/** Shared collapsed color editor state used by every client UI. */
public final class ColorPickerState {
    public enum Mode { RGB, PALETTE }

    private static final Map<ColorSetting, Boolean> EXPANDED = new IdentityHashMap<>();
    private static final Map<ColorSetting, Mode> MODES = new IdentityHashMap<>();
    private static final int[] SWATCHES = {
            0xFFFF3B3B, 0xFFFF8A34, 0xFFFFD83B, 0xFF45E06F,
            0xFF42E5E9, 0xFF4583FF, 0xFF884DFF, 0xFFFF3BBA,
            0xFFFFFFFF, 0xFF9AA2B0, 0xFF3B404A, 0xFF050607
    };
    private static final int[] WHEEL_COLORS = {
            0xFFFF3B3B, 0xFFFFD83B, 0xFF45E06F, 0xFF42E5E9,
            0xFF4583FF, 0xFFB34DFF, 0xFFFF3BBA, 0xFFFF3B3B
    };
    private static final Paint WHEEL_PAINT = new Paint().setAntiAlias(true).setDither(true);
    private static final Paint MARKER_PAINT = new Paint().setAntiAlias(true);

    private ColorPickerState() {
    }

    public static boolean expanded(ColorSetting setting) {
        return EXPANDED.getOrDefault(setting, false);
    }

    public static void toggle(ColorSetting setting) {
        boolean next = !expanded(setting);
        if (next) {
            EXPANDED.replaceAll((other, ignored) -> false);
        }
        EXPANDED.put(setting, next);
    }

    public static Mode mode(ColorSetting setting) {
        return MODES.getOrDefault(setting, Mode.RGB);
    }

    public static void toggleMode(ColorSetting setting) {
        MODES.put(setting, mode(setting) == Mode.RGB ? Mode.PALETTE : Mode.RGB);
    }

    public static void setMode(ColorSetting setting, Mode mode) {
        MODES.put(setting, mode);
    }

    public static float height(ColorSetting setting, float header, float rgbRow, float paletteHeight) {
        if (!expanded(setting)) return header;
        return header + 22.0F + (mode(setting) == Mode.RGB
                ? (setting.allowAlpha() ? 4 : 3) * rgbRow
                : paletteHeight);
    }

    /** Height of the compact version of the same wheel used by Color Presets. */
    public static float paletteHeight(float width) {
        float radius = paletteRadius(width);
        float swatchSize = paletteSwatchSize(width);
        return radius * 2.0F + 13.0F + swatchSize * 2.0F + 5.0F;
    }

    private static float paletteRadius(float width) {
        return Math.max(24.0F, Math.min(48.0F, (width - 24.0F) * 0.5F));
    }

    private static float paletteSwatchSize(float width) {
        return Math.max(8.0F, Math.min(13.0F, (width - 15.0F) / 6.0F));
    }

    public static void drawMode(Canvas canvas, float x, float y, float width, int text, int accent) {
        float half = Math.max(22.0F, (width - 4.0F) * 0.5F);
        SkijaUi.rounded(canvas, x, y, half, 13.0F, 3.0F, 0x261FFFFF);
        SkijaUi.rounded(canvas, x + half + 4.0F, y, half, 13.0F, 3.0F, 0x261FFFFF);
        SkijaUi.text(canvas, "RGB", x + 6.0F, y + 1.0F, 11.0F, text, 6.5F);
        SkijaUi.text(canvas, "Palette", x + half + 10.0F, y + 1.0F, 11.0F, text, 6.5F);
    }

    public static void drawModeSelection(Canvas canvas, float x, float y, float width,
                                         Mode mode, int text, int accent) {
        float half = Math.max(22.0F, (width - 4.0F) * 0.5F);
        drawMode(canvas, x, y, width, text, accent);
        float selectedX = mode == Mode.RGB ? x : x + half + 4.0F;
        SkijaUi.outline(canvas, selectedX, y, half, 13.0F, 3.0F, 0.8F, accent);
    }

    public static boolean clickMode(ColorSetting setting, float mouseX, float mouseY,
                                    float x, float y, float width) {
        float half = Math.max(22.0F, (width - 4.0F) * 0.5F);
        if (mouseY < y || mouseY > y + 13.0F) return false;
        if (mouseX >= x && mouseX < x + half) {
            setMode(setting, Mode.RGB);
            return true;
        }
        if (mouseX >= x + half + 4.0F && mouseX < x + width) {
            setMode(setting, Mode.PALETTE);
            return true;
        }
        return false;
    }

    public static void drawPalette(Canvas canvas, float x, float y, float width, float height,
                                   ColorSetting setting, int text) {
        float radius = paletteRadius(width);
        float centerX = x + width * 0.5F;
        float centerY = y + radius + 2.0F;
        drawClockTicks(canvas, centerX, centerY, radius, text);
        drawColorWheel(canvas, centerX, centerY, radius);

        Color selected = setting.get();
        float[] hsb = Color.RGBtoHSB(selected.getRed(), selected.getGreen(), selected.getBlue(), null);
        float hue = hsb[1] < 0.01F ? 0.0F : hsb[0];
        float handleX = centerX + (float)Math.cos(hue * Math.PI * 2.0) * hsb[1] * radius;
        float handleY = centerY + (float)Math.sin(hue * Math.PI * 2.0) * hsb[1] * radius;
        MARKER_PAINT.setColor(0xFF05070A);
        canvas.drawCircle(handleX, handleY, 7.0F, MARKER_PAINT);
        MARKER_PAINT.setColor(selected.getRGB());
        canvas.drawCircle(handleX, handleY, 5.0F, MARKER_PAINT);
        SkijaUi.outline(canvas, handleX - 5.0F, handleY - 5.0F, 10.0F, 10.0F,
                5.0F, 1.0F, text);

        int columns = 6;
        float gap = 3.0F;
        float size = paletteSwatchSize(width);
        float total = columns * size + (columns - 1) * gap;
        float startX = x + Math.max(0.0F, (width - total) * 0.5F);
        float swatchY = centerY + radius + 8.0F;
        for (int i = 0; i < SWATCHES.length; i++) {
            float swatchX = startX + (i % columns) * (size + gap);
            float rowY = swatchY + (i / columns) * (size + gap);
            SkijaUi.rounded(canvas, swatchX, rowY, size, size, 1.5F, SWATCHES[i]);
            if ((setting.get().getRGB() & 0x00FFFFFF) == (SWATCHES[i] & 0x00FFFFFF)) {
                SkijaUi.outline(canvas, swatchX - 1.0F, rowY - 1.0F,
                        size + 2.0F, size + 2.0F, 1.5F, 1.0F, text);
            }
        }
    }

    private static void drawColorWheel(Canvas canvas, float centerX, float centerY, float radius) {
        try (Shader hue = Shader.makeSweepGradient(centerX, centerY, WHEEL_COLORS);
             Shader saturation = Shader.makeRadialGradient(centerX, centerY, radius,
                     new int[]{0xFFFFFFFF, 0x00FFFFFF});
             Shader combined = Shader.makeBlend(BlendMode.SRC_OVER, hue, saturation)) {
            WHEEL_PAINT.setShader(combined);
            canvas.drawCircle(centerX, centerY, radius, WHEEL_PAINT);
        } finally {
            WHEEL_PAINT.setShader(null);
        }
    }

    private static void drawClockTicks(Canvas canvas, float centerX, float centerY,
                                       float radius, int color) {
        for (int index = 0; index < 12; index++) {
            double angle = index / 12.0 * Math.PI * 2.0 - Math.PI * 0.5;
            float inner = radius + (index % 3 == 0 ? 6.0F : 8.0F);
            float outer = radius + 12.0F;
            SkijaUi.line(canvas,
                    centerX + (float)Math.cos(angle) * inner,
                    centerY + (float)Math.sin(angle) * inner,
                    centerX + (float)Math.cos(angle) * outer,
                    centerY + (float)Math.sin(angle) * outer,
                    index % 3 == 0 ? 1.5F : 0.8F,
                    index % 3 == 0 ? 0xB0FFFFFF : 0x50FFFFFF);
        }
    }

    public static boolean clickPalette(ColorSetting setting, float mouseX, float mouseY,
                                       float x, float y, float width) {
        float radius = paletteRadius(width);
        float centerX = x + width * 0.5F;
        float centerY = y + radius + 2.0F;
        float dx = mouseX - centerX;
        float dy = mouseY - centerY;
        if (dx * dx + dy * dy <= radius * radius) {
            float saturation = Math.max(0.0F, Math.min(1.0F,
                    (float)Math.hypot(dx, dy) / radius));
            float hue = (float)(Math.atan2(dy, dx) / (Math.PI * 2.0));
            if (hue < 0.0F) hue += 1.0F;
            Color next = new Color(Color.HSBtoRGB(hue, saturation, 1.0F));
            Color current = setting.get();
            setting.set(new Color(next.getRed(), next.getGreen(), next.getBlue(), current.getAlpha()));
            return true;
        }
        int columns = 6;
        float gap = 3.0F;
        float size = paletteSwatchSize(width);
        float total = columns * size + (columns - 1) * gap;
        float startX = x + Math.max(0.0F, (width - total) * 0.5F);
        float swatchY = centerY + radius + 8.0F;
        for (int i = 0; i < SWATCHES.length; i++) {
            float swatchX = startX + (i % columns) * (size + gap);
            float rowY = swatchY + (i / columns) * (size + gap);
            if (mouseX >= swatchX && mouseX <= swatchX + size
                    && mouseY >= rowY && mouseY <= rowY + size) {
                Color next = new Color(SWATCHES[i], true);
                setting.set(new Color(next.getRed(), next.getGreen(), next.getBlue(), setting.get().getAlpha()));
                return true;
            }
        }
        return false;
    }
}
