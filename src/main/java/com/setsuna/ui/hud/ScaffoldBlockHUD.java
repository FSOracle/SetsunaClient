package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.modules.movement.Scaffold;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.awt.Color;

/** Compact block counter visible while Scaffold has placeable blocks. */
public final class ScaffoldBlockHUD extends EpsilonHudModule {

    public static final ScaffoldBlockHUD INSTANCE = new ScaffoldBlockHUD();

    private static final Paint BLOCK_PAINT = new Paint().setAntiAlias(false);

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
        if (background.get()) {
            int color = backgroundColor.argb();
            int alpha = Math.round(((color >>> 24) & 0xFF) * eased);
            HudRenderUtil.coloredSurface(event.canvas(), panelX, y, visibleWidth, height,
                    radius, alpha << 24 | color & 0x00FFFFFF, HudFusionManager.Edges.NONE);
        }
        if (eased < 0.12F) return;

        int textAlpha = Math.round(255.0F * eased);
        if (border.get()) {
            HudRenderUtil.border(event.canvas(), panelX, y, visibleWidth, height, radius,
                    1.35F * s, displayedOutline, borderMode.get(), borderColor.argb(),
                    borderStart.argb(), borderEnd.argb(), textAlpha);
        }
        drawBlockTexture(event.canvas(), Scaffold.INSTANCE.getPlacementStack(),
                contentX + 3.0F * s, contentY + 2.0F * s, 16.0F * s, textAlpha);
        float valueX = contentX + 22.0F * s;
        SkijaUi.boldText(event.canvas(), value, valueX, contentY, contentHeight,
                UiTheme.withAlpha(UiTheme.TEXT, textAlpha), valueFont);
        float labelX = valueX + 3.0F * s + SkijaUi.boldTextWidth(value, valueFont);
        SkijaUi.text(event.canvas(), "BLOCKS", labelX, contentY, contentHeight,
                UiTheme.withAlpha(UiTheme.TEXT_FAINT, textAlpha), labelFont);

    }

    private void drawBlockTexture(Canvas canvas, ItemStack stack, float x, float y,
                                  float size, int alpha) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return;
        BlockState state = blockItem.getBlock().defaultBlockState();
        TextureAtlasSprite sprite = mc.getModelManager().getBlockStateModelSet()
                .getParticleMaterial(state).sprite();
        try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(
                sprite.atlasLocation())) {
            if (borrowed == null) return;
            float sourceX = sprite.getU0() * borrowed.image().getWidth();
            float sourceY = sprite.getV0() * borrowed.image().getHeight();
            float sourceWidth = (sprite.getU1() - sprite.getU0()) * borrowed.image().getWidth();
            float sourceHeight = (sprite.getV1() - sprite.getV0()) * borrowed.image().getHeight();
            BLOCK_PAINT.setAlpha(alpha);
            canvas.drawImageRect(borrowed.image(),
                    Rect.makeXYWH(sourceX, sourceY, sourceWidth, sourceHeight),
                    Rect.makeXYWH(x, y, size, size), SamplingMode.DEFAULT,
                    BLOCK_PAINT, true);
        } finally {
            BLOCK_PAINT.setAlpha(255);
        }
    }

    @Override
    public int editorColor() {
        return UiTheme.accent();
    }
}
