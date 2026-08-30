package com.setsuna.ui.hud;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;

/** Shared movable layout contract for the individually configurable Epsilon HUD modules. */
public abstract class EpsilonHudModule extends Module {

    /** Shared transparency control (0-100%) honoured by every HUD component. */
    public final IntSetting opacity = add(new IntSetting("Opacity", 100, 0, 100, 1));

    public final IntSetting xPosition;
    public final IntSetting yPosition;

    private final float defaultWidth;
    private final float defaultHeight;
    private float renderedWidth;
    private float renderedHeight;

    protected EpsilonHudModule(String name, int defaultX, int defaultY,
                               float defaultWidth, float defaultHeight) {
        this(name, Category.HUD, defaultX, defaultY, defaultWidth, defaultHeight);
    }

    protected EpsilonHudModule(String name, Category category, int defaultX, int defaultY,
                               float defaultWidth, float defaultHeight) {
        super(name, category);
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.renderedWidth = defaultWidth;
        this.renderedHeight = defaultHeight;
        this.xPosition = add(new IntSetting("X Position", defaultX, 0, 1000, 1));
        this.yPosition = add(new IntSetting("Y Position", defaultY, 0, 1000, 1));
    }

    // --- shared opacity ------------------------------------------------------

    /**
     * Composites the component's Skija drawing through an offscreen layer so the
     * configured {@link #opacity} fades the whole element uniformly. Components
     * that draw with vanilla {@code GuiGraphics} (or manage their own compositing,
     * like the Target HUD) opt out via {@link #usesSharedOpacityLayer()} and bake
     * {@link #opacityFactor()} into their colours instead.
     */
    @Listen
    private void renderOpacityLayer(Render2DEvent event) {
        if (!usesSharedOpacityLayer()) return;
        int alpha = opacityAlpha();
        if (alpha >= 255) {
            renderHud(event);
            return;
        }
        Canvas canvas = event.canvas();
        int save = canvas.saveLayerAlpha(null, Math.max(0, alpha));
        try {
            renderHud(event);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    /** Skija drawing hook for components that render on the {@link Render2DEvent} canvas. */
    protected void renderHud(Render2DEvent event) {
    }

    /** False for components that draw through vanilla {@code GuiGraphics} or self-composite. */
    protected boolean usesSharedOpacityLayer() {
        return true;
    }

    /** Current opacity as a {@code 0..1} multiplier, for baking into vanilla-path colours. */
    public float opacityFactor() {
        return HudRenderUtil.clamp(opacity.get() / 100.0F, 0.0F, 1.0F);
    }

    /** Current opacity as a {@code 0..255} alpha value. */
    public int opacityAlpha() {
        return Math.round(opacityFactor() * 255.0F);
    }

    /** Scales the alpha channel of an {@code 0xAARRGGBB} colour by the current opacity. */
    public int applyOpacity(int argb) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * opacityFactor());
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    protected final float renderX(Render2DEvent event, float width) {
        return HudRenderUtil.normalizedPosition(xPosition, event.width(), width);
    }

    protected final float renderY(Render2DEvent event, float height) {
        return HudRenderUtil.normalizedPosition(yPosition, event.height(), height);
    }

    protected final float renderX(float screenWidth, float width) {
        return HudRenderUtil.normalizedPosition(xPosition, screenWidth, width);
    }

    protected final float renderY(float screenHeight, float height) {
        return HudRenderUtil.normalizedPosition(yPosition, screenHeight, height);
    }

    protected final void updateBounds(float width, float height) {
        renderedWidth = Math.max(4.0F, width);
        renderedHeight = Math.max(4.0F, height);
    }

    public final float hudX(float screenWidth) {
        return HudRenderUtil.normalizedPosition(xPosition, screenWidth, hudWidth(screenWidth));
    }

    public final float hudY(float screenHeight) {
        return HudRenderUtil.normalizedPosition(yPosition, screenHeight, hudHeight(screenHeight));
    }

    public final float hudWidth(float screenWidth) {
        return Math.min(Math.max(4.0F, renderedWidth), Math.max(4.0F, screenWidth - 12.0F));
    }

    public final float hudHeight(float screenHeight) {
        return Math.min(Math.max(4.0F, renderedHeight), Math.max(4.0F, screenHeight - 12.0F));
    }

    public final void moveTo(float x, float y, float screenWidth, float screenHeight) {
        HudRenderUtil.setNormalizedPosition(xPosition, x, screenWidth, hudWidth(screenWidth));
        HudRenderUtil.setNormalizedPosition(yPosition, y, screenHeight, hudHeight(screenHeight));
    }

    public final void resetPosition() {
        xPosition.reset();
        yPosition.reset();
    }

    public int editorColor() {
        return UiTheme.accent();
    }

    public String editorLabel() {
        return name();
    }

    /** Whether this HUD can merge touching surfaces and borders with another HUD. */
    public boolean supportsHudFusion() {
        return false;
    }

    /** Fusion only removes a shared edge when both participants are drawing borders. */
    public boolean hudFusionBorderEnabled() {
        return false;
    }

    /** Whether this element currently requests the shared fused background. */
    public boolean hudFusionBackgroundEnabled() {
        return false;
    }

    /** Complete surface style used when this HUD owns a fused group. */
    public HudFusionManager.FusionStyle hudFusionStyle() {
        return null;
    }

    /** Preferred visual cell size inside a compact fused strip. */
    public float hudFusionContentWidth(float screenWidth) {
        return hudWidth(screenWidth);
    }

    public float hudFusionContentHeight(float screenHeight) {
        return hudHeight(screenHeight);
    }

    protected final float defaultWidth() {
        return defaultWidth;
    }

    protected final float defaultHeight() {
        return defaultHeight;
    }
}
