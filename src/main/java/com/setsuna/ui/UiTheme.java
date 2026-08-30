package com.setsuna.ui;

import java.awt.Color;

/** Shared visual tokens for all Setsuna screens and HUD surfaces. */
public final class UiTheme {

    public static final int BACKDROP = argb(170, 3, 6, 7);
    public static final int SURFACE = argb(246, 12, 16, 18);
    public static final int SURFACE_ALT = argb(238, 17, 22, 24);
    public static final int SURFACE_RAISED = argb(246, 25, 32, 34);
    public static final int SURFACE_HOVER = argb(250, 31, 40, 42);
    public static final int CONTROL = argb(244, 21, 27, 29);
    public static final int CONTROL_HOVER = argb(250, 34, 43, 45);
    public static final int HEADER = argb(248, 14, 19, 21);
    public static final int BORDER = rgb(53, 65, 67);
    public static final int BORDER_STRONG = rgb(73, 88, 90);
    public static final int BORDER_SOFT = argb(138, 56, 68, 70);
    public static final int SHADOW = argb(105, 0, 0, 0);

    public static final int TEXT = rgb(241, 246, 244);
    public static final int TEXT_MUTED = rgb(166, 178, 174);
    public static final int TEXT_FAINT = rgb(103, 117, 113);

    public static final int ACCENT = rgb(62, 214, 180);
    public static final int ACCENT_DARK = rgb(22, 112, 92);
    public static final int ACCENT_SOFT = argb(46, 62, 214, 180);
    public static final int SUCCESS = rgb(84, 211, 143);
    public static final int WARNING = rgb(244, 183, 86);
    public static final int DANGER = rgb(238, 100, 96);
    public static final int INFO = rgb(91, 174, 255);

    public static final float RADIUS = 6.0F;
    public static final float RADIUS_SMALL = 4.0F;

    private UiTheme() {
    }

    public static int accent() {
        Color color = com.setsuna.module.modules.ClickGui.INSTANCE.accent.get();
        return argb(255, color.getRed(), color.getGreen(), color.getBlue());
    }

    public static int withAlpha(int color, int alpha) {
        return (clamp(alpha) << 24) | (color & 0x00FFFFFF);
    }

    public static int rgb(int red, int green, int blue) {
        return argb(255, red, green, blue);
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (clamp(alpha) << 24) | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
