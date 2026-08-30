package com.setsuna.ui.clickgui;

import com.setsuna.module.modules.player.NetEaseMusicModule;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.Setting;
import io.github.humbleui.skija.Canvas;

/** Draws the read-only palette preview for the NetEase Music color presets. */
public final class MusicPresetPreview {

    public static final float EXTRA_HEIGHT = 23.0F;

    private MusicPresetPreview() {
    }

    public static boolean matches(Setting<?> setting) {
        return setting == NetEaseMusicModule.INSTANCE.colorPreset;
    }

    public static void draw(Canvas canvas, float x, float y, float width, float height, float alpha) {
        if (canvas == null || width <= 4.0F || height <= 3.0F) return;

        NetEaseMusicModule.ColorPreset preset = NetEaseMusicModule.INSTANCE.colorPreset.get();
        int[] colors = {
                preset.accent(),
                preset.secondary(),
                preset.surface(),
                preset.background()
        };
        float gap = Math.min(2.0F, Math.max(0.5F, width * 0.01F));
        float swatchWidth = Math.max(1.0F, (width - gap * (colors.length - 1)) / colors.length);
        float swatchHeight = Math.max(6.0F, Math.min(13.0F, height - 7.0F));
        float swatchY = y + Math.max(2.0F, (height - swatchHeight) * 0.5F);
        for (int index = 0; index < colors.length; index++) {
            float swatchX = x + index * (swatchWidth + gap);
            SkijaUi.rounded(canvas, swatchX, swatchY, swatchWidth, swatchHeight,
                    Math.min(3.0F, swatchHeight * 0.35F), withAlpha(colors[index], alpha));
            SkijaUi.outline(canvas, swatchX, swatchY, swatchWidth, swatchHeight,
                    Math.min(3.0F, swatchHeight * 0.35F), 0.45F, withAlpha(0xFFFFFFFF, alpha * 0.28F));
        }
    }

    private static int withAlpha(int color, float amount) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * clamp(amount));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
