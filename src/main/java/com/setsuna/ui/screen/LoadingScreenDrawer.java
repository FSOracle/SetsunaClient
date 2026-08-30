package com.setsuna.ui.screen;

import com.setsuna.render.SkijaUi;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;

/**
 * Full-bleed Spotlight overlay that replaces the vanilla {@code LoadingOverlay}
 * (startup + resource reload). Drawn on top of the presented frame, so the
 * dirt/logo/progress bar underneath is fully covered while the vanilla overlay
 * keeps owning its own reload-completion lifecycle.
 */
public final class LoadingScreenDrawer {

    private LoadingScreenDrawer() {
    }

    /**
     * @param progress reload progress in {@code [0, 1]}, or a negative value when
     *                 the true progress is not yet known (renders an indeterminate
     *                 sweep instead of a filled bar).
     */
    public static void draw(Canvas canvas, float width, float height, float progress) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        ScreenBackdrop.draw(canvas, width, height, 176);

        float centerX = width * 0.5F;
        float brandSize = Math.min(38.0F, Math.max(26.0F, width * 0.05F));
        float tracking = brandSize * 0.14F;
        float brandWidth = UiControls.brandWidth("SETSUNA", brandSize, tracking);
        float brandY = height * 0.5F - brandSize * 1.1F - 26.0F;
        UiControls.brand(canvas, "SETSUNA", centerX - brandWidth * 0.5F, brandY, brandSize * 1.1F,
                UiControls.TEXT, brandSize, tracking);

        String subtitle = "LOADING CLIENT RESOURCES";
        float subtitleTracking = 2.4F;
        float subtitleWidth = UiControls.brandWidth(subtitle, 7.2F, subtitleTracking);
        UiControls.brand(canvas, subtitle, centerX - subtitleWidth * 0.5F,
                brandY + brandSize * 1.1F + 10.0F, 12.0F, UiControls.TEXT_MUTED, 7.2F, subtitleTracking);

        drawProgressBar(canvas, width, centerX, brandY + brandSize * 1.1F + 40.0F, progress);
    }

    private static void drawProgressBar(Canvas canvas, float width, float centerX, float y, float progress) {
        float barWidth = Math.min(268.0F, Math.max(160.0F, width * 0.34F));
        float barHeight = 4.0F;
        float barX = centerX - barWidth * 0.5F;
        float radius = barHeight * 0.5F;
        int accent = UiTheme.accent();

        SkijaUi.rounded(canvas, barX, y, barWidth, barHeight, radius, UiControls.CARD_HOVER);

        if (progress < 0.0F) {
            // Indeterminate: a short segment sweeping left to right.
            float segment = barWidth * 0.32F;
            float travel = barWidth - segment;
            float phase = (System.currentTimeMillis() % 1400L) / 1400.0F;
            float eased = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));
            SkijaUi.rounded(canvas, barX + travel * eased, y, segment, barHeight, radius, accent);
        } else {
            float clamped = Math.max(0.0F, Math.min(1.0F, progress));
            if (clamped > 0.0F) {
                SkijaUi.rounded(canvas, barX, y, Math.max(barHeight, barWidth * clamped), barHeight,
                        radius, accent);
            }
            String percent = Math.round(clamped * 100.0F) + "%";
            float percentWidth = SkijaUi.textWidth(percent, 7.6F);
            SkijaUi.text(canvas, percent, centerX - percentWidth * 0.5F, y + 12.0F, 12.0F,
                    UiControls.TEXT_MUTED, 7.6F);
        }
    }
}
