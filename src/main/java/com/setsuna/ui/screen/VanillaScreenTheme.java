package com.setsuna.ui.screen;

import com.setsuna.render.SkijaRenderer;
import io.github.humbleui.skija.Canvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Visual skin for the real vanilla world, server, and options screens. */
public final class VanillaScreenTheme {

    private static Screen transitionScreen;
    private static long transitionStartedAt;
    private static volatile Screen lastRenderedScreen;
    private static volatile long lastRenderedAt;

    private VanillaScreenTheme() {
    }

    public static boolean applies(Screen screen) {
        if (screen == null) {
            return false;
        }
        String packageName = screen.getClass().getPackageName();
        return packageName.startsWith("net.minecraft.client.gui.screens.worldselection")
                || packageName.startsWith("net.minecraft.client.gui.screens.multiplayer")
                || packageName.startsWith("net.minecraft.client.gui.screens.options");
    }

    public static void beginFrame(Screen screen) {
        if (transitionScreen != screen) {
            transitionScreen = screen;
            transitionStartedAt = System.nanoTime();
        }
    }

    public static void drawSkijaBackdrop(Canvas canvas, float width, float height,
                                         float mouseX, float mouseY) {
        ScreenBackdrop.drawMainMenu(canvas, width, height,
                (System.nanoTime() & 0x1FFFFFFFFFFFFFL) / 1_000_000_000.0F,
                mouseX, mouseY, 48);
    }

    /** Marks replacement visuals usable only after the complete Skija frame was submitted. */
    public static void markSkijaFrameRendered(Screen screen) {
        lastRenderedScreen = screen;
        lastRenderedAt = System.nanoTime();
    }

    /** Forces vanilla widgets to remain visible while the Skija bridge is recovering. */
    public static void invalidateSkijaFrame() {
        lastRenderedScreen = null;
        lastRenderedAt = 0L;
    }

    public static boolean canReplaceVanillaButtons(Screen screen) {
        return screen != null && !SkijaRenderer.hasFailed() && lastRenderedScreen == screen
                && System.nanoTime() - lastRenderedAt < 1_000_000_000L;
    }

    public static boolean canRenderBackground(Screen screen) {
        return canReplaceVanillaButtons(screen);
    }

    public static void drawFallback(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF07090A);
    }

    public static void drawTransition(GuiGraphicsExtractor graphics) {
        int alpha = PageTransition.overlayAlpha(transitionStartedAt);
        if (alpha > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
        }
    }
}
