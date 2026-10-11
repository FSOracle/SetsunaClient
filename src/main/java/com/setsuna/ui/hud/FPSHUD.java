package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.types.Rect;

import java.awt.Color;

/** Small frame-rate readout using the shared compact HUD language. */
public final class FPSHUD extends HUDModuel {

    public static final FPSHUD INSTANCE = new FPSHUD();
    private float fusionContentWidth = 58.0F;

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 1.6, 0.05));
    public final BooleanSetting background = add(new BooleanSetting("Background", true));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color",
            new Color(UiTheme.withAlpha(UiTheme.SURFACE, 174), true), true)
            .visibleWhen(background::get));
    public final BooleanSetting blur = add(new BooleanSetting("Blur", false));
    public final IntSetting blurStrength = add(new IntSetting("Blur Strength", 6, 1, 16, 1)
            .visibleWhen(blur::get));
    public final BooleanSetting border = add(new BooleanSetting("Border", true));
    public final DoubleSetting borderWidth = add(new DoubleSetting(
            "Border Width", 1.2, 0.5, 4.0, 0.1).visibleWhen(border::get));
    public final DoubleSetting borderRadius = add(new DoubleSetting(
            "Border Radius", 4.0, 0.0, 14.0, 0.5)
            .visibleWhen(() -> background.get() || border.get() || blur.get()));
    public final EnumSetting<HudRenderUtil.BorderMode> borderMode = add(
            new EnumSetting<>("Border Mode", HudRenderUtil.BorderMode.Single)
                    .visibleWhen(border::get));
    public final ColorSetting borderColor = add(new ColorSetting("Border Color",
            new Color(UiTheme.accent(), true), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Single)));
    public final ColorSetting borderStart = add(new ColorSetting("Border Start",
            new Color(64, 224, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));
    public final ColorSetting borderEnd = add(new ColorSetting("Border End",
            new Color(130, 92, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));
    public final BooleanSetting glow = add(new BooleanSetting("Glow", false));
    public final IntSetting glowStrength = add(new IntSetting("Glow Strength", 8, 1, 10, 1)
            .visibleWhen(glow::get));

    private FPSHUD() {
        super("FPS HUD", 8, 70, 58.0F, 20.0F);
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer()) return;
        boolean fused = HudFusionManager.isFused(this, event);
        float s = fused ? HudFusionManager.barScale() : scale.get().floatValue();
        String value = Integer.toString(mc.getFps());
        float labelSize = fused ? HudFusionManager.barFontSize() : 6.8F * s;
        float valueSize = fused ? HudFusionManager.barFontSize() : 8.2F * s;
        float padding = fused ? HudFusionManager.barPadding() : 5.0F * s;
        float gap = 3.0F * s;
        float height = fused ? HudFusionManager.barHeight() : 20.0F * s;
        fusionContentWidth = padding * 2.0F + SkijaUi.textWidth("FPS", labelSize) + gap
                + SkijaUi.boldTextWidth(HudRenderUtil.stableDigits(value), valueSize);
        float width = fusionContentWidth;
        float x = renderX(event, width);
        float y = renderY(event, height);
        updateBounds(width, height);
        float radius = Math.min(borderRadius.get().floatValue() * s, height * 0.5F);
        if (!fused) {
            float activeBlur = hudBlurStrength(blur.get(), blurStrength.get(), background.get(), backgroundColor.argb());
            if (activeBlur > 0.01F) {
                HudRenderUtil.blur(event.canvas(), x, y, width, height, radius,
                        activeBlur);
            }
            if (background.get()) {
                HudRenderUtil.coloredSurface(event.canvas(), x, y, width, height, radius,
                        backgroundColor.argb(), HudFusionManager.Edges.NONE);
            }
            if (border.get()) drawBorder(event, x, y, width, height, s, HudFusionManager.Edges.NONE);
        }
        Rect content = HudFusionManager.contentBounds(this, event);
        float labelX = content.getLeft() + padding;
        float valueX = labelX + SkijaUi.textWidth("FPS", labelSize) + gap;
        Runnable drawText = () -> {
            SkijaUi.text(event.canvas(), "FPS", labelX,
                    content.getTop(), content.getHeight(),
                    UiTheme.TEXT_FAINT, labelSize);
            SkijaUi.boldText(event.canvas(), value, valueX,
                    content.getTop(), content.getHeight(), UiTheme.TEXT, valueSize);
        };
        if (glow.get()) {
            SkijaUi.glowLayer(event.canvas(), content.getLeft(), content.getTop(),
                    content.getWidth(), content.getHeight(),
                    4.0F * s, glowStrength.get(), drawText);
        }
        drawText.run();
    }

    private void drawBorder(Render2DEvent event, float x, float y, float width,
                            float height, float scale, HudFusionManager.Edges edges) {
        HudRenderUtil.border(event.canvas(), x, y, width, height,
                Math.min(borderRadius.get().floatValue() * scale, height * 0.5F),
                borderWidth.get().floatValue() * scale,
                1.0F, borderMode.get(), borderColor.argb(), borderStart.argb(),
                borderEnd.argb(), 255, edges);
    }

    @Override
    public boolean supportsHudFusion() {
        return true;
    }

    @Override
    public boolean hudFusionBorderEnabled() {
        return border.get();
    }

    @Override
    public boolean hudFusionBackgroundEnabled() {
        return background.get();
    }

    @Override
    public HudFusionManager.FusionStyle hudFusionStyle() {
        float s = scale.get().floatValue();
        return new HudFusionManager.FusionStyle(background.get(),
                backgroundColor.argb(), blur.get(),
                blurStrength.get(), false, 0.0F, border.get(),
                borderRadius.get().floatValue() * s, borderWidth.get().floatValue() * s,
                borderMode.get(), borderColor.argb(), borderStart.argb(), borderEnd.argb());
    }

    @Override
    public float hudFusionContentWidth(float screenWidth) {
        return Math.max(4.0F, fusionContentWidth);
    }

    @Override
    public int editorColor() {
        return UiTheme.accent();
    }
}
