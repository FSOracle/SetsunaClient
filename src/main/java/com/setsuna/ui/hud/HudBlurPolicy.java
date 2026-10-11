package com.setsuna.ui.hud;

/** Resolves shared glass settings without changing component background preferences. */
final class HudBlurPolicy {
    private HudBlurPolicy() {
    }

    static float strength(boolean global, float globalStrength, boolean background,
                          int color, boolean local, float localStrength) {
        if (global && background && (color >>> 24) != 0) return globalStrength;
        return local ? localStrength : 0.0F;
    }

    static int surfaceColor(boolean global, int color) {
        if (!global) return color;
        return (Math.min(color >>> 24, 172) << 24) | (color & 0x00FFFFFF);
    }
}
