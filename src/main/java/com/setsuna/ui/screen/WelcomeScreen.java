package com.setsuna.ui.screen;

import com.setsuna.Setsuna;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** One-time welcome screen shown before the Setsuna main menu. */
public final class WelcomeScreen extends AbstractSkijaScreen {

    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(
            "setsuna", "textures/hud/watermark_logo.png");
    private static final float GLOW_START = 0.16F;
    private static final float GLOW_DURATION = 0.78F;
    private static final float LOGO_START = 0.32F;
    private static final float LOGO_DURATION = 0.66F;
    private static final float PROMPT_START = 1.02F;
    private static final float PROMPT_DURATION = 0.72F;
    private static final float READY_AT = 1.58F;
    private static final Paint IMAGE_PAINT = new Paint().setAntiAlias(true);
    private static final Paint GLOW_IMAGE_PAINT = new Paint().setAntiAlias(true);

    private static boolean completedThisSession;

    private long openedAt = System.nanoTime();
    private boolean starting;

    private WelcomeScreen() {
        super(Component.literal(Setsuna.clientName()));
    }

    /** Returns the welcome screen only once during a client session. */
    public static Screen initialDestination() {
        return completedThisSession ? new MainMenuScreen() : new WelcomeScreen();
    }

    @Override
    protected void init() {
        openedAt = System.nanoTime();
        starting = false;
    }

    @Override
    protected int backdropColor() {
        return 0xFF000000;
    }

    @Override
    protected void drawScreen(Canvas canvas) {
        canvas.drawColor(0xFF000000);
        float elapsed = elapsedSeconds();
        float glow = easeOut(stage(elapsed, GLOW_START, GLOW_DURATION));
        float logo = easeOut(stage(elapsed, LOGO_START, LOGO_DURATION));
        float prompt = easeOut(stage(elapsed, PROMPT_START, PROMPT_DURATION));

        float squareSize = Math.max(88.0F, Math.min(122.0F, Math.min(width, height) * 0.29F));
        float centerX = width * 0.5F;
        float centerY = height * 0.44F;
        float squareX = centerX - squareSize * 0.5F;
        float squareY = centerY - squareSize * 0.5F;

        drawLogo(canvas, centerX, centerY, squareSize, glow, logo);
        drawPrompt(canvas, centerX, squareY + squareSize + 25.0F, prompt, elapsed);

        // TEMP-PREVIEW
        if (elapsed > 2.2F && !starting) {
            starting = true;
            completedThisSession = true;
            minecraft.setScreen(new ChangelogScreen());
        }
    }

    private static void drawLogo(Canvas canvas, float centerX, float centerY,
                                 float squareSize, float glowProgress, float logoProgress) {
        if (glowProgress <= 0.001F && logoProgress <= 0.001F) return;
        try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(LOGO_TEXTURE)) {
            if (borrowed == null) return;
            Image image = borrowed.image();
            float scaleProgress = Math.max(glowProgress, logoProgress);
            float size = squareSize * 0.65F * lerp(0.86F, 1.0F, scaleProgress);
            Rect source = Rect.makeXYWH(0.0F, 0.0F, image.getWidth(), image.getHeight());
            Rect destination = Rect.makeXYWH(centerX - size * 0.5F,
                    centerY - size * 0.5F, size, size);

            if (glowProgress > 0.001F) {
                GLOW_IMAGE_PAINT.setColor(0xFFFFFFFF)
                        .setAlpha(Math.round(235.0F * glowProgress));
                SkijaUi.glowLayer(canvas, destination.getLeft(), destination.getTop(),
                        destination.getWidth(), destination.getHeight(), 18.0F, 9,
                        () -> canvas.drawImageRect(image, source, destination,
                                SamplingMode.MITCHELL, GLOW_IMAGE_PAINT, true));
                GLOW_IMAGE_PAINT.setAlpha(255);
            }

            IMAGE_PAINT.setColor(0xFFFFFFFF).setAlpha(Math.round(255.0F * logoProgress));
            canvas.drawImageRect(image, source, destination,
                    SamplingMode.MITCHELL, IMAGE_PAINT, true);
            IMAGE_PAINT.setAlpha(255);
        }
    }

    private static void drawPrompt(Canvas canvas, float centerX, float y,
                                   float progress, float elapsed) {
        if (progress <= 0.001F) return;
        String text = "CLICK TO START";
        float fontSize = 7.4F;
        float tracking = 2.2F;
        float width = trackedWidth(text, fontSize, tracking);
        float pulse = elapsed > PROMPT_START + PROMPT_DURATION
                ? 0.5F + 0.5F * (float) Math.sin(elapsed * 2.4F)
                : 0.0F;
        int alpha = Math.round(progress * (188.0F + pulse * 42.0F));
        drawTrackedText(canvas, text, centerX - width * 0.5F, y,
                13.0F, UiTheme.withAlpha(0xFFFFFFFF, alpha), fontSize, tracking);
    }

    private static void drawTrackedText(Canvas canvas, String text, float x, float y,
                                        float height, int color, float fontSize, float tracking) {
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String glyph = text.substring(i, i + 1);
            SkijaUi.boldText(canvas, glyph, cursor, y, height, color, fontSize);
            cursor += SkijaUi.boldTextWidth(glyph, fontSize) + tracking;
        }
    }

    private static float trackedWidth(String text, float fontSize, float tracking) {
        float result = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            result += SkijaUi.boldTextWidth(text.substring(i, i + 1), fontSize);
        }
        return result + Math.max(0, text.length() - 1) * tracking;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (!starting && elapsedSeconds() >= READY_AT) {
            starting = true;
            completedThisSession = true;
            // Show changelog if user hasn't read it yet, otherwise go to main menu
            minecraft.setScreen(ChangelogScreen.shouldShow() ? new ChangelogScreen() : new MainMenuScreen());
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private float elapsedSeconds() {
        return (System.nanoTime() - openedAt) / 1_000_000_000.0F;
    }

    private static float stage(float elapsed, float start, float duration) {
        return Math.max(0.0F, Math.min(1.0F, (elapsed - start) / duration));
    }

    private static float easeOut(float value) {
        float remaining = 1.0F - value;
        return 1.0F - remaining * remaining * remaining;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }
}
