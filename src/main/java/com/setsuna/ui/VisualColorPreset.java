package com.setsuna.ui;

import java.awt.Color;
import java.util.Locale;

/** Resolves themeable color settings through the active visual color preset. */
public final class VisualColorPreset {

    private static final long TRANSITION_NANOS = 260_000_000L;

    private static volatile boolean enabled;
    private static volatile boolean backgroundColorsEnabled;
    private static volatile int primaryFrom = 0xFFFFFF;
    private static volatile int primaryTarget = 0xFFFFFF;
    private static volatile int secondaryFrom = 0xFFFFFF;
    private static volatile int secondaryTarget = 0xFFFFFF;
    private static volatile long transitionStartedAt;

    private VisualColorPreset() {
    }

    public static synchronized void configure(boolean active, boolean applyBackgroundColors,
                                              Color primary, Color secondary) {
        int nextPrimary = rgb(primary);
        int nextSecondary = rgb(secondary);
        long now = System.nanoTime();
        backgroundColorsEnabled = applyBackgroundColors;
        if (!active) {
            enabled = false;
            primaryFrom = primaryTarget = nextPrimary;
            secondaryFrom = secondaryTarget = nextSecondary;
            transitionStartedAt = now;
            return;
        }
        if (!enabled) {
            enabled = true;
            primaryFrom = primaryTarget = nextPrimary;
            secondaryFrom = secondaryTarget = nextSecondary;
            transitionStartedAt = now;
            return;
        }
        if (nextPrimary == primaryTarget && nextSecondary == secondaryTarget) return;
        float progress = transitionProgress(now);
        primaryFrom = mix(primaryFrom, primaryTarget, progress);
        secondaryFrom = mix(secondaryFrom, secondaryTarget, progress);
        primaryTarget = nextPrimary;
        secondaryTarget = nextSecondary;
        transitionStartedAt = now;
    }

    public static Color resolve(String settingName, Color original) {
        if (!enabled || original == null
                || !backgroundColorsEnabled && isBackground(settingName)) return original;
        long now = System.nanoTime();
        float progress = transitionProgress(now);
        int rgb = isSecondary(settingName)
                ? mix(secondaryFrom, secondaryTarget, progress)
                : mix(primaryFrom, primaryTarget, progress);
        return new Color((original.getAlpha() << 24) | rgb, true);
    }

    private static float transitionProgress(long now) {
        float linear = Math.max(0.0F, Math.min(1.0F,
                (now - transitionStartedAt) / (float) TRANSITION_NANOS));
        float inverse = 1.0F - linear;
        return 1.0F - inverse * inverse * inverse;
    }

    private static int mix(int from, int to, float progress) {
        int red = Math.round(((from >> 16) & 0xFF) * (1.0F - progress)
                + ((to >> 16) & 0xFF) * progress);
        int green = Math.round(((from >> 8) & 0xFF) * (1.0F - progress)
                + ((to >> 8) & 0xFF) * progress);
        int blue = Math.round((from & 0xFF) * (1.0F - progress)
                + (to & 0xFF) * progress);
        return red << 16 | green << 8 | blue;
    }

    private static boolean isSecondary(String settingName) {
        String name = settingName == null ? "" : settingName.toLowerCase(Locale.ROOT);
        return name.contains("end color")
                || name.contains("secondary")
                || name.contains("second color")
                || name.contains("color 2")
                || name.contains("right color")
                || name.endsWith(" end");
    }

    private static boolean isBackground(String settingName) {
        String name = settingName == null ? "" : settingName.toLowerCase(Locale.ROOT).trim();
        return name.equals("background")
                || name.equals("bg color")
                || name.contains("background color")
                || name.contains("background");
    }

    private static int rgb(Color color) {
        return color == null ? 0xFFFFFF : color.getRGB() & 0x00FFFFFF;
    }
}
