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
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.awt.Color;

/** Compact coordinates readout with an optional cross-dimension row. */
public final class CoordinatesHUD extends EpsilonHudModule {

    public static final CoordinatesHUD INSTANCE = new CoordinatesHUD();
    private float fusionContentWidth = 132.0F;

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 1.6, 0.05));
    public final IntSetting decimals = add(new IntSetting("Decimals", 0, 0, 2, 1));
    public final BooleanSetting dimensionCoordinates = add(new BooleanSetting("Dimension Coordinates", true));
    public final BooleanSetting background = add(new BooleanSetting("Background", false));
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

    private CoordinatesHUD() {
        super("Coordinates HUD", 8, 120, 132.0F, 31.0F);
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer()) return;
        float s = scale.get().floatValue();
        boolean nether = Level.NETHER.equals(mc.level.dimension());
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        String primaryLabel = "XYZ";
        String primaryValue = triple(x, y, z);
        String secondaryLabel = nether ? "OW" : "N";
        String secondaryValue = nether
                ? pair(x * 8.0, z * 8.0) : pair(x / 8.0, z / 8.0);

        float labelSize = 6.8F * s;
        float valueSize = 8.2F * s;
        float textGap = 3.0F * s;
        float row = 11.0F * s;
        float height = (dimensionCoordinates.get() ? 31.0F : 20.0F) * s;
        float primaryWidth = SkijaUi.textWidth(primaryLabel, labelSize) + textGap
                + SkijaUi.boldTextWidth(HudRenderUtil.stableDigits(primaryValue), valueSize);
        fusionContentWidth = primaryWidth + 12.0F * s;
        float textWidth = primaryWidth;
        if (dimensionCoordinates.get()) {
            textWidth = Math.max(textWidth, SkijaUi.textWidth(secondaryLabel, labelSize)
                    + textGap + SkijaUi.boldTextWidth(
                            HudRenderUtil.stableDigits(secondaryValue), valueSize));
        }
        float width = textWidth + 12.0F * s;
        float drawX = renderX(event, width);
        float drawY = renderY(event, height);
        updateBounds(width, height);
        float radius = Math.min(borderRadius.get().floatValue() * s, height * 0.5F);
        if (!HudFusionManager.isFused(this, event)) {
            if (blur.get()) {
                HudRenderUtil.blur(event.canvas(), drawX, drawY, width, height, radius,
                        blurStrength.get() * 0.55F);
            }
            if (background.get()) {
                HudRenderUtil.coloredSurface(event.canvas(), drawX, drawY, width, height,
                        radius, backgroundColor.argb(), HudFusionManager.Edges.NONE);
            }
            if (border.get()) {
                HudRenderUtil.border(event.canvas(), drawX, drawY, width, height,
                        radius, borderWidth.get().floatValue() * s,
                        1.0F, borderMode.get(), borderColor.argb(), borderStart.argb(),
                        borderEnd.argb(), 255);
            }
        }
        boolean fused = HudFusionManager.isFused(this, event);
        Rect content = HudFusionManager.contentBounds(this, event);
        Runnable drawText = () -> {
            float labelX = content.getLeft() + 5.0F * s;
            float primaryValueX = labelX + SkijaUi.textWidth(primaryLabel, labelSize) + textGap;
            float primaryTop = fused ? content.getTop() : drawY + 4.0F * s;
            float primaryHeight = fused ? content.getHeight() : row;
            SkijaUi.text(event.canvas(), primaryLabel, labelX, primaryTop,
                    primaryHeight, UiTheme.TEXT_FAINT, labelSize);
            SkijaUi.boldText(event.canvas(), primaryValue, primaryValueX, primaryTop,
                    primaryHeight, UiTheme.TEXT, valueSize);
            if (dimensionCoordinates.get() && !fused) {
                float secondaryTop = drawY + 15.0F * s;
                float secondaryValueX = labelX
                        + SkijaUi.textWidth(secondaryLabel, labelSize) + textGap;
                SkijaUi.text(event.canvas(), secondaryLabel, labelX, secondaryTop,
                        row, UiTheme.TEXT_FAINT, labelSize);
                SkijaUi.boldText(event.canvas(), secondaryValue, secondaryValueX, secondaryTop,
                        row, UiTheme.TEXT, valueSize);
            }
        };
        if (glow.get()) {
            SkijaUi.glowLayer(event.canvas(), content.getLeft(), content.getTop(),
                    content.getWidth(), content.getHeight(),
                    4.0F * s, glowStrength.get(), drawText);
        }
        drawText.run();
    }

    private String triple(double x, double y, double z) {
        String pattern = "% ." + decimals.get() + "f  % ." + decimals.get() + "f  % ." + decimals.get() + "f";
        return String.format(Locale.ROOT, pattern, x, y, z).trim();
    }

    private String pair(double x, double z) {
        String pattern = "% ." + decimals.get() + "f  % ." + decimals.get() + "f";
        return String.format(Locale.ROOT, pattern, x, z).trim();
    }

    @Override
    public int editorColor() {
        return UiTheme.accent();
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
                blurStrength.get() * 0.55F, false, 0.0F, border.get(),
                borderRadius.get().floatValue() * s, borderWidth.get().floatValue() * s,
                borderMode.get(), borderColor.argb(), borderStart.argb(), borderEnd.argb());
    }

    @Override
    public float hudFusionContentHeight(float screenHeight) {
        return Math.min(hudHeight(screenHeight), 20.0F * scale.get().floatValue());
    }

    @Override
    public float hudFusionContentWidth(float screenWidth) {
        return Math.min(hudWidth(screenWidth), Math.max(4.0F, fusionContentWidth));
    }
}
