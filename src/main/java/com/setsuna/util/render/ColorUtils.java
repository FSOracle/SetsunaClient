package com.setsuna.util.render;

import net.minecraft.util.Mth;

import java.awt.Color;

/** Small color helpers shared by render modules. */
public final class ColorUtils {

    private ColorUtils() {
    }

    /** Linearly interpolates every channel of two colors by {@code fraction} in {@code [0,1]}. */
    public static Color interpolate(Color from, Color to, float fraction) {
        fraction = Mth.clamp(fraction, 0.0F, 1.0F);
        int red = Mth.clamp(Mth.lerpInt(fraction, from.getRed(), to.getRed()), 0, 255);
        int green = Mth.clamp(Mth.lerpInt(fraction, from.getGreen(), to.getGreen()), 0, 255);
        int blue = Mth.clamp(Mth.lerpInt(fraction, from.getBlue(), to.getBlue()), 0, 255);
        int alpha = Mth.clamp(Mth.lerpInt(fraction, from.getAlpha(), to.getAlpha()), 0, 255);
        return new Color(red, green, blue, alpha);
    }

    /** Alias for {@link #interpolate} — the name the ESP render helpers use. */
    public static Color interpolateColor(Color from, Color to, float fraction) {
        return interpolate(from, to, fraction);
    }

    /** A fully saturated color cycling through the hue wheel once every {@code periodMs}. */
    public static Color rainbow(long periodMs, int alpha) {
        float hue = (System.currentTimeMillis() % periodMs) / (float) periodMs;
        Color base = Color.getHSBColor(hue, 0.9F, 1.0F);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }
}
