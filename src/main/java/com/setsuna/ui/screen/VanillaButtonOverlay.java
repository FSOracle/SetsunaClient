package com.setsuna.ui.screen;

import com.setsuna.render.SkijaUi;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.RRect;

import java.util.ArrayList;
import java.util.List;

/** Skija overlay that replaces themed vanilla button visuals without changing their input logic. */
public final class VanillaButtonOverlay implements SkijaScreen {

    public static final VanillaButtonOverlay INSTANCE = new VanillaButtonOverlay();

    private static final int BLUE = 0xFF72BDF4;
    private static final Paint OUTLINE = new Paint()
            .setAntiAlias(true)
            .setMode(PaintMode.STROKE)
            .setStrokeWidth(1.0F);
    private static final List<ButtonState> PENDING_BUTTONS = new ArrayList<>();
    private static volatile List<ButtonState> renderedButtons = List.of();

    private VanillaButtonOverlay() {
    }

    public static synchronized void beginFrame() {
        PENDING_BUTTONS.clear();
    }

    public static synchronized void add(int x, int y, int width, int height, String label,
                                        boolean hovered, boolean active, float alpha) {
        if (width <= 0 || height <= 0) {
            return;
        }
        PENDING_BUTTONS.add(new ButtonState(x, y, width, height, label == null ? "" : label,
                hovered, active, Math.max(0.0F, Math.min(1.0F, alpha))));
    }

    public static synchronized void endFrame() {
        renderedButtons = List.copyOf(PENDING_BUTTONS);
    }

    @Override
    public void renderSkija(Canvas canvas) {
        for (ButtonState button : renderedButtons) {
            drawButton(canvas, button);
        }
    }

    private static void drawButton(Canvas canvas, ButtonState button) {
        float x = button.x;
        float y = button.y;
        float width = button.width;
        float height = button.height;
        float radius = Math.max(3.5F, Math.min(6.0F, height * 0.28F));
        int opacity = Math.round(button.alpha * 255.0F);
        int borderAlpha = button.active ? (button.hovered ? 235 : 118) : 42;

        if (button.active && button.hovered) {
            SkijaUi.rounded(canvas, x, y, width, height, radius,
                    UiTheme.withAlpha(BLUE, 38 * opacity / 255));
            SkijaUi.rounded(canvas, x + 3.0F, y + 5.0F, 3.0F,
                    Math.max(1.0F, height - 10.0F), 1.5F,
                    UiTheme.withAlpha(BLUE, 225 * opacity / 255));
            OUTLINE.setColor(UiTheme.withAlpha(BLUE, 115 * opacity / 255));
            canvas.drawLine(x + radius, y + height - 1.5F,
                    x + width - radius, y + height - 1.5F, OUTLINE);
        }

        OUTLINE.setColor(UiTheme.withAlpha(BLUE, borderAlpha * opacity / 255));
        canvas.drawRRect(RRect.makeXYWH(x + 0.5F, y + 0.5F,
                Math.max(0.0F, width - 1.0F), Math.max(0.0F, height - 1.0F), radius), OUTLINE);

        float fontSize = Math.max(7.0F, Math.min(9.0F, height * 0.43F));
        String label = fit(button.label, Math.max(0.0F, width - 16.0F), fontSize);
        float textWidth = SkijaUi.boldTextWidth(label, fontSize);
        int textColor = button.active
                ? (button.hovered ? UiControls.TEXT : UiControls.TEXT_MUTED)
                : UiControls.TEXT_FAINT;
        SkijaUi.boldText(canvas, label, x + (width - textWidth) * 0.5F, y, height,
                UiTheme.withAlpha(textColor, opacity), fontSize);
    }

    private static String fit(String text, float maxWidth, float fontSize) {
        if (SkijaUi.boldTextWidth(text, fontSize) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int end = text.length();
        while (end > 0 && SkijaUi.boldTextWidth(text.substring(0, end) + suffix, fontSize) > maxWidth) {
            end = text.offsetByCodePoints(end, -1);
        }
        return end == 0 ? "" : text.substring(0, end) + suffix;
    }

    private record ButtonState(float x, float y, float width, float height, String label,
                               boolean hovered, boolean active, float alpha) {
    }
}
