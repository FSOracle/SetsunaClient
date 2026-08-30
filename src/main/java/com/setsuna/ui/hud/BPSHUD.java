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

import java.util.Locale;
import java.awt.Color;

/** Compact horizontal movement-speed readout. */
public final class BPSHUD extends EpsilonHudModule {

    public static final BPSHUD INSTANCE = new BPSHUD();
    private static final float BASE_WIDTH = 64.0F;
    private float fusionContentWidth = 64.0F;

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 1.6, 0.05));
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
            new Color(UiTheme.INFO, true), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Single)));
    public final ColorSetting borderStart = add(new ColorSetting("Border Start",
            new Color(82, 226, 190), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));
    public final ColorSetting borderEnd = add(new ColorSetting("Border End",
            new Color(72, 148, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));
    public final BooleanSetting glow = add(new BooleanSetting("Glow", false));
    public final IntSetting glowStrength = add(new IntSetting("Glow Strength", 8, 1, 10, 1)
            .visibleWhen(glow::get));
    public final BooleanSetting smooth = add(new BooleanSetting("Smooth", true));

    private double lastX;
    private double lastZ;
    private int lastTick;
    private long lastFrameNanos;
    private float targetSpeed;
    private float displayed;
    private boolean initialized;

    private BPSHUD() {
        super("BPS HUD", 8, 95, 64.0F, 20.0F);
    }

    @Override
    protected void onEnable() {
        resetTracking();
    }

    @Override
    protected void onDisable() {
        resetTracking();
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer()) {
            resetTracking();
            return;
        }
        sampleSpeed();
        updateDisplayed();
        float s = scale.get().floatValue();
        String value = String.format(Locale.ROOT, "%.1f", displayed);
        float labelSize = 6.8F * s;
        float valueSize = 8.2F * s;
        float height = 20.0F * s;
        fusionContentWidth = 12.0F * s + SkijaUi.textWidth("BPS", labelSize)
                + SkijaUi.boldTextWidth(HudRenderUtil.stableDigits(value), valueSize);
        float width = Math.max(BASE_WIDTH * s, fusionContentWidth);
        float x = renderX(event, width);
        float y = renderY(event, height);
        updateBounds(width, height);
        float radius = Math.min(borderRadius.get().floatValue() * s, height * 0.5F);
        if (!HudFusionManager.isFused(this, event)) {
            if (blur.get()) {
                HudRenderUtil.blur(event.canvas(), x, y, width, height, radius,
                        blurStrength.get() * 0.55F);
            }
            if (background.get()) {
                HudRenderUtil.coloredSurface(event.canvas(), x, y, width, height, radius,
                        backgroundColor.argb(), HudFusionManager.Edges.NONE);
            }
            if (border.get()) {
                HudRenderUtil.border(event.canvas(), x, y, width, height,
                        radius, borderWidth.get().floatValue() * s,
                        1.0F, borderMode.get(), borderColor.argb(), borderStart.argb(),
                        borderEnd.argb(), 255);
            }
        }
        Rect content = HudFusionManager.contentBounds(this, event);
        float labelX = content.getLeft() + 5.0F * s;
        float valueX = labelX + SkijaUi.textWidth("BPS", labelSize) + 3.0F * s;
        Runnable drawText = () -> {
            SkijaUi.text(event.canvas(), "BPS", labelX,
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

    private void sampleSpeed() {
        int tick = mc.player.tickCount;
        if (initialized && tick == lastTick) return;
        double x = mc.player.getX();
        double z = mc.player.getZ();
        if (!initialized) {
            initialized = true;
            lastX = x;
            lastZ = z;
            lastTick = tick;
            return;
        }
        if (tick < lastTick) {
            lastX = x;
            lastZ = z;
            lastTick = tick;
            targetSpeed = 0.0F;
            return;
        }
        int elapsedTicks = Math.max(1, tick - lastTick);
        targetSpeed = HudRenderUtil.clamp(
                (float) (Math.hypot(x - lastX, z - lastZ) * 20.0 / elapsedTicks),
                0.0F, 60.0F);
        lastX = x;
        lastZ = z;
        lastTick = tick;
    }

    private void updateDisplayed() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            displayed = targetSpeed;
            return;
        }
        float delta = Math.min(0.1F,
                Math.max(0.0F, (now - lastFrameNanos) / 1_000_000_000.0F));
        lastFrameNanos = now;
        float amount = smooth.get()
                ? 1.0F - (float) Math.exp(-11.0F * delta) : 1.0F;
        displayed += (targetSpeed - displayed) * amount;
        if (Math.abs(targetSpeed - displayed) < 0.001F) displayed = targetSpeed;
    }

    private void resetTracking() {
        initialized = false;
        targetSpeed = 0.0F;
        displayed = 0.0F;
        lastTick = 0;
        lastFrameNanos = 0L;
    }

    @Override
    public int editorColor() {
        return UiTheme.INFO;
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
    public float hudFusionContentWidth(float screenWidth) {
        return Math.min(hudWidth(screenWidth), Math.max(4.0F, fusionContentWidth));
    }
}
