package com.setsuna.ui.hud;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.module.modules.movement.Scaffold;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.awt.Color;

/** Compact block counter visible while Scaffold has placeable blocks. */
public final class ScaffoldBlockHUD extends HUDModuel {

    public static final ScaffoldBlockHUD INSTANCE = new ScaffoldBlockHUD();

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.65, 1.6, 0.05));
    public final BooleanSetting background = add(new BooleanSetting("Background", true));
    public final ColorSetting backgroundColor = add(new ColorSetting("Background Color",
            new Color(UiTheme.withAlpha(UiTheme.SURFACE, 174), true), true)
            .visibleWhen(background::get));
    public final BooleanSetting border = add(new BooleanSetting("Border", true));
    public final DoubleSetting borderRadius = add(new DoubleSetting(
            "Border Radius", 4.0, 0.0, 14.0, 0.5)
            .visibleWhen(() -> background.get() || border.get()));
    public final EnumSetting<HudRenderUtil.BorderMode> borderMode = add(
            new EnumSetting<>("Border Mode", HudRenderUtil.BorderMode.Single)
                    .visibleWhen(border::get));
    public final ColorSetting borderColor = add(new ColorSetting("Border Color",
            new Color(75, 155, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Single)));
    public final ColorSetting borderStart = add(new ColorSetting("Border Start",
            new Color(66, 225, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));
    public final ColorSetting borderEnd = add(new ColorSetting("Border End",
            new Color(126, 92, 255), false).visibleWhen(() -> border.get()
            && borderMode.is(HudRenderUtil.BorderMode.Gradient)));

    private float visibility;
    private float displayedCount;
    private PanelFrame frame;
    private float displayedOutline = 1.0F;
    private long lastFrame;
    private long observedBlockCountSession = Long.MIN_VALUE;
    private int initialCount;

    private ScaffoldBlockHUD() {
        super("Scaffold Block HUD", 500, 760, 70.0F, 20.0F);
    }

    @Override
    protected void renderHud(Render2DEvent event) {
        if (noPlayer()) {
            visibility = 0.0F;
            frame = null;
            return;
        }
        int count = Math.max(0, Scaffold.INSTANCE.getBlockCount());
        boolean scaffoldEnabled = Scaffold.INSTANCE.isEnabled();
        long blockCountSession = Scaffold.INSTANCE.getBlockCountSession();
        if (scaffoldEnabled && observedBlockCountSession != blockCountSession) {
            observedBlockCountSession = blockCountSession;
            initialCount = Math.max(0, Scaffold.INSTANCE.getInitialBlockCount());
            displayedCount = count;
            displayedOutline = 1.0F;
        } else if (scaffoldEnabled && initialCount <= 0 && count > 0) {
            initialCount = count;
            displayedOutline = 1.0F;
        }
        boolean active = scaffoldEnabled && count > 0;
        long now = System.currentTimeMillis();
        float delta = lastFrame == 0L ? 0.05F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        visibility += ((active ? 1.0F : 0.0F) - visibility) * Math.min(1.0F, delta * 12.0F);
        displayedCount += (count - displayedCount) * Math.min(1.0F, delta * 10.0F);
        float outlineTarget = initialCount <= 0
                ? 0.0F
                : HudRenderUtil.clamp(count / (float) initialCount, 0.0F, 1.0F);
        displayedOutline += (outlineTarget - displayedOutline) * Math.min(1.0F, delta * 9.0F);
        if (!active && visibility < 0.01F) {
            frame = null;
            return;
        }

        float s = scale.get().floatValue();
        String value = Integer.toString(Math.max(0, Math.round(displayedCount)));
        float valueFont = 9.0F * s;
        float labelFont = 6.8F * s;
        float contentHeight = 20.0F * s;
        float height = 22.0F * s;
        float width = 29.0F * s + SkijaUi.boldTextWidth(value, valueFont)
                + SkijaUi.textWidth("BLOCKS", labelFont);
        float x = renderX(event, width);
        float y = renderY(event, height);
        updateBounds(width, height);

        float eased = 1.0F - (float) Math.pow(1.0F - HudRenderUtil.clamp(visibility, 0.0F, 1.0F), 3.0);
        float visibleWidth = Math.max(2.0F, width * eased);
        float panelX = x + (width - visibleWidth) * 0.5F;
        float radius = Math.min(borderRadius.get().floatValue() * s, height * 0.5F);
        float contentX = x + 1.0F * s;
        float contentY = y + 1.0F * s;
        ItemStack blockStack = Scaffold.INSTANCE.getPlacementStack();
        frame = new PanelFrame(panelX, y, visibleWidth, height, radius, s,
                eased, displayedOutline, blockStack, contentX + 3.0F * s,
                contentY + 2.0F * s);
        if (eased < 0.12F) return;

        int textAlpha = Math.round(255.0F * eased);
        float valueX = contentX + 22.0F * s;
        SkijaUi.boldText(event.canvas(), value, valueX, contentY, contentHeight,
                UiTheme.withAlpha(UiTheme.TEXT, textAlpha), valueFont);
        float labelX = valueX + 3.0F * s + SkijaUi.boldTextWidth(value, valueFont);
        SkijaUi.text(event.canvas(), "BLOCKS", labelX, contentY, contentHeight,
                UiTheme.withAlpha(UiTheme.TEXT_FAINT, textAlpha), labelFont);

    }

    @Listen
    private void onVanillaHud(VanillaHudRenderEvent event) {
        PanelFrame current = frame;
        if (current == null || current.eased() < 0.12F) return;
        boolean drawBackground = background.get();
        boolean drawBorder = border.get();
        if (drawBackground || drawBorder) {
            java.util.function.Consumer<Canvas> painter = canvas -> {
                int opacityLayer = canvas.saveLayerAlpha(null, opacityAlpha());
                try {
                    if (drawBackground) {
                        int color = backgroundColor.argb();
                        int alpha = Math.round(((color >>> 24) & 0xFF) * current.eased());
                        drawGlobalBlur(canvas, current.x(), current.y(), current.width(),
                                current.height(), current.radius(), color);
                        HudRenderUtil.coloredSurface(canvas, current.x(), current.y(),
                                current.width(), current.height(), current.radius(),
                                alpha << 24 | color & 0x00FFFFFF, HudFusionManager.Edges.NONE);
                    }
                    if (drawBorder) {
                        HudRenderUtil.border(canvas, current.x(), current.y(), current.width(),
                                current.height(), current.radius(), 1.35F * current.scale(),
                                current.outline(), borderMode.get(), borderColor.argb(),
                                borderStart.argb(), borderEnd.argb(),
                                Math.round(255.0F * current.eased()));
                    }
                } finally {
                    canvas.restoreToCount(opacityLayer);
                }
            };
            if (drawBackground) {
                SkijaRenderer.renderMainTargetWithBackdrop(painter);
            } else {
                SkijaRenderer.renderMainTarget(painter);
            }
        }
        ItemStack stack = current.stack();
        if (stack == null || stack.isEmpty()) return;
        GuiGraphicsExtractor graphics = event.graphics();
        float itemScale = current.scale();
        graphics.pose().pushMatrix();
        graphics.pose().translate(current.itemX(), current.itemY());
        graphics.pose().scale(itemScale, itemScale);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    private record PanelFrame(float x, float y, float width, float height, float radius,
                              float scale, float eased, float outline, ItemStack stack,
                              float itemX, float itemY) {
    }

    @Override
    public int editorColor() {
        return UiTheme.accent();
    }
}
