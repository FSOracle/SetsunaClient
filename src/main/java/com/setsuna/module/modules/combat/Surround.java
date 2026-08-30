package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.player.FindItemResult;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.world.BlockPlaceHelper;
import com.setsuna.util.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Surround extends Module {

    public static final Surround INSTANCE = new Surround();

    private final IntSetting blocksPerTick = add(new IntSetting("Blocks Per Tick", 3, 1, 8, 1));
    private final IntSetting delay = add(new IntSetting("Delay", 0, 0, 10, 1));
    private final BooleanSetting center = add(new BooleanSetting("Center", true));
    private final BooleanSetting extend = add(new BooleanSetting("Extend", true));
    private final BooleanSetting disableOnJump = add(new BooleanSetting("Disable On Jump", true));
    private final BooleanSetting disableOnMove = add(new BooleanSetting("Disable On Move", false));
    private final BooleanSetting pauseOnUse = add(new BooleanSetting("Pause On Use", true));
    private final BooleanSetting render = add(new BooleanSetting("Render", true));
    private final ColorSetting sideColor = add(new ColorSetting(
            "Side Color", new Color(90, 160, 255, 70)).visibleWhen(render::get));
    private final ColorSetting lineColor = add(new ColorSetting(
            "Line Color", new Color(90, 160, 255, 220)).visibleWhen(render::get));

    private final Set<BlockPos> renderPositions = new LinkedHashSet<>();
    private BlockPos startPosition;
    private int delayTimer;

    private Surround() {
        super("Surround", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        if (noPlayer()) {
            setEnabled(false);
            return;
        }

        startPosition = mc.player.blockPosition();
        delayTimer = 0;
        renderPositions.clear();
        if (center.get()) {
            mc.player.setPos(
                    startPosition.getX() + 0.5,
                    mc.player.getY(),
                    startPosition.getZ() + 0.5);
            mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
        }
    }

    @Override
    protected void onDisable() {
        renderPositions.clear();
        startPosition = null;
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            renderPositions.clear();
            return;
        }
        if (pauseOnUse.get() && mc.player.isUsingItem()) {
            return;
        }
        if (disableOnJump.get() && mc.options.keyJump.isDown()) {
            setEnabled(false);
            return;
        }
        if (disableOnMove.get() && startPosition != null
                && !mc.player.blockPosition().equals(startPosition)) {
            setEnabled(false);
            return;
        }
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        FindItemResult block = BlockPlaceHelper.findHotbar(Items.OBSIDIAN, Items.ENDER_CHEST);
        if (!block.found()) {
            return;
        }

        int placed = 0;
        renderPositions.clear();
        for (BlockPos target : surroundTargets()) {
            renderPositions.add(target);
            if (!BlockUtils.canPlaceAt(target)) {
                continue;
            }
            BlockPlaceHelper.PlaceInfo info = BlockPlaceHelper.findPlaceInfo(target, false);
            if (BlockPlaceHelper.place(info, block, true, true)) {
                placed++;
                if (placed >= blocksPerTick.get()) {
                    break;
                }
            }
        }
        if (placed > 0) {
            delayTimer = delay.get();
        }
    }

    @Listen
    private void onRender(Render3DEvent event) {
        if (!render.get() || noPlayer()) {
            return;
        }
        for (BlockPos position : renderPositions) {
            if (!BlockUtils.canPlaceAt(position)) {
                continue;
            }
            Render3DUtils.drawFilledBox(position, sideColor.get());
            Render3DUtils.drawOutlineBox(event.getPoseStack(), position, lineColor.get());
        }
    }

    private Set<BlockPos> surroundTargets() {
        Set<BlockPos> feet = feetPositions();
        Set<BlockPos> targets = new LinkedHashSet<>();
        for (BlockPos foot : feet) {
            BlockPos below = foot.below();
            if (BlockUtils.canPlaceAt(below)) {
                targets.add(below);
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = foot.relative(direction);
                if (!feet.contains(side)) {
                    targets.add(side);
                }
            }
        }
        return targets;
    }

    private Set<BlockPos> feetPositions() {
        Set<BlockPos> positions = new LinkedHashSet<>();
        if (!extend.get()) {
            positions.add(mc.player.blockPosition());
            return positions;
        }

        AABB box = mc.player.getBoundingBox().deflate(0.001);
        int minimumX = (int) Math.floor(box.minX);
        int maximumX = (int) Math.floor(box.maxX);
        int minimumZ = (int) Math.floor(box.minZ);
        int maximumZ = (int) Math.floor(box.maxZ);
        int y = mc.player.blockPosition().getY();
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                positions.add(new BlockPos(x, y, z));
            }
        }
        return positions;
    }
}
