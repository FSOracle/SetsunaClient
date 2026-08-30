package com.setsuna.ui.screen;

import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Common Minecraft-to-Skija screen bridge with a deterministic backdrop. */
abstract class AbstractSkijaScreen extends Screen implements SkijaScreen {

    protected int mouseX;
    protected int mouseY;
    private long transitionStartedAt = System.nanoTime();

    protected AbstractSkijaScreen(Component title) {
        super(title);
    }

    @Override
    public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The Skija pass owns the complete frame.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (SkijaRenderer.hasFailed()) {
            graphics.fill(0, 0, width, height, 0xFF080A0B);
            graphics.text(font, "Skija renderer failed - check latest.log", 8, 8, 0xFFF0F4F2, false);
        }
    }

    @Override
    public final void renderSkija(Canvas canvas) {
        SkijaUi.fill(canvas, 0, 0, width, height, backdropColor());
        drawScreen(canvas);
        int transitionAlpha = PageTransition.overlayAlpha(transitionStartedAt);
        if (transitionAlpha > 0) {
            SkijaUi.fill(canvas, 0, 0, width, height, transitionAlpha << 24);
        }
    }

    @Override
    public void added() {
        super.added();
        transitionStartedAt = System.nanoTime();
    }

    /** Full-screen backdrop colour. Opaque by default so the game frame stays hidden. */
    protected int backdropColor() {
        return UiTheme.rgb(7, 9, 10);
    }

    protected abstract void drawScreen(Canvas canvas);

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
