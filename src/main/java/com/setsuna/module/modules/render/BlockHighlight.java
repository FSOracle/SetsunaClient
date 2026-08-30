package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.util.render.Render3DUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.awt.Color;

/**
 * Highlights the block the player is looking at, as a filled box, an outline, a
 * single face, or combinations thereof.
 */
public final class BlockHighlight extends Module {

    public static final BlockHighlight INSTANCE = new BlockHighlight();

    public enum Mode {
        BOTH,
        BOTH_SIDE,
        FILL,
        FILLED_SIDE,
        OUTLINE,
        OUTLINED_SIDE
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.OUTLINE));
    public final ColorSetting sideColor = add(new ColorSetting("Color", new Color(255, 255, 255, 100))
            .visibleWhen(() -> mode.is(Mode.BOTH) || mode.is(Mode.BOTH_SIDE) || mode.is(Mode.FILL) || mode.is(Mode.FILLED_SIDE)));
    public final ColorSetting lineColor = add(new ColorSetting("Line Color", new Color(255, 255, 255, 255))
            .visibleWhen(() -> mode.is(Mode.BOTH) || mode.is(Mode.BOTH_SIDE) || mode.is(Mode.OUTLINE) || mode.is(Mode.OUTLINED_SIDE)));
    public final DoubleSetting lineWidth = add(new DoubleSetting("Line Width", 1.0, 0.0, 5.0, 0.5));

    private BlockHighlight() {
        super("Block Highlight", Category.RENDER);
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult bhr)) {
            return;
        }

        float thickness = lineWidth.get().floatValue();
        AABB box = new AABB(bhr.getBlockPos());
        Color fillColor = sideColor.get();
        Color outlineColor = lineColor.get();
        Direction direction = bhr.getDirection();

        switch (mode.get()) {
            case BOTH -> {
                Render3DUtils.drawFilledBox(box, fillColor);
                Render3DUtils.drawOutlineBox(event.getPoseStack(), box, outlineColor, thickness);
            }
            case BOTH_SIDE -> {
                Render3DUtils.drawSideOutline(event.getPoseStack(), box, outlineColor, thickness, direction);
                Render3DUtils.drawFilledSide(box, fillColor, direction);
            }
            case FILL -> Render3DUtils.drawFilledBox(box, fillColor);
            case FILLED_SIDE -> Render3DUtils.drawFilledSide(box, fillColor, direction);
            case OUTLINE -> Render3DUtils.drawOutlineBox(event.getPoseStack(), box, outlineColor, thickness);
            case OUTLINED_SIDE -> Render3DUtils.drawSideOutline(event.getPoseStack(), box, outlineColor, thickness, direction);
        }
    }
}
