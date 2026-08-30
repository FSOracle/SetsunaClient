package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.render.Render3DUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HoleESP extends Module {

    public static final HoleESP INSTANCE = new HoleESP();

    private final IntSetting range = add(new IntSetting("Range", 16, 4, 64, 2));
    private final IntSetting verticalRange = add(new IntSetting("Vertical Range", 8, 2, 32, 1));
    private final IntSetting scanDelay = add(new IntSetting("Scan Delay", 10, 1, 40, 1));
    private final BooleanSetting doubleHoles = add(new BooleanSetting("Double Holes", true));
    private final BooleanSetting filled = add(new BooleanSetting("Filled", true));
    private final ColorSetting bedrockColor = add(new ColorSetting(
            "Bedrock Color", new Color(70, 210, 120, 125)));
    private final ColorSetting obsidianColor = add(new ColorSetting(
            "Obsidian Color", new Color(95, 140, 255, 125)));
    private final ColorSetting mixedColor = add(new ColorSetting(
            "Mixed Color", new Color(255, 190, 80, 125)));

    private final List<Hole> holes = new ArrayList<>();
    private int scanTimer;

    private HoleESP() {
        super("Hole ESP", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        scanTimer = 0;
        holes.clear();
    }

    @Override
    protected void onDisable() {
        holes.clear();
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            holes.clear();
            return;
        }
        if (scanTimer++ < scanDelay.get()) {
            return;
        }
        scanTimer = 0;
        scan();
    }

    @Listen
    private void onRender(Render3DEvent event) {
        if (noPlayer()) {
            return;
        }

        for (Hole hole : holes) {
            Color color = switch (hole.type()) {
                case Bedrock -> bedrockColor.get();
                case Obsidian -> obsidianColor.get();
                case Mixed -> mixedColor.get();
            };
            if (filled.get()) {
                Render3DUtils.drawFilledBox(hole.box(), color);
            }
            Render3DUtils.drawOutlineBox(event.getPoseStack(), hole.box(), color);
        }
    }

    private void scan() {
        List<Hole> fresh = new ArrayList<>();
        Set<BlockPos> consumed = new HashSet<>();
        BlockPos center = mc.player.blockPosition();
        int horizontal = range.get();
        int vertical = verticalRange.get();

        for (int x = -horizontal; x <= horizontal; x++) {
            for (int y = -vertical; y <= vertical; y++) {
                for (int z = -horizontal; z <= horizontal; z++) {
                    BlockPos position = center.offset(x, y, z);
                    if (position.distSqr(center) > horizontal * (double) horizontal
                            || consumed.contains(position)) {
                        continue;
                    }

                    Hole single = singleHole(position);
                    if (single != null) {
                        fresh.add(single);
                        consumed.add(position);
                        continue;
                    }
                    if (!doubleHoles.get()) {
                        continue;
                    }

                    Hole doubleHole = doubleHole(position);
                    if (doubleHole != null) {
                        fresh.add(doubleHole);
                        consumed.add(position);
                        consumed.add(doubleHole.second());
                    }
                }
            }
        }

        fresh.sort(Comparator.comparingDouble(hole -> hole.center().distSqr(center)));
        holes.clear();
        holes.addAll(fresh);
    }

    private Hole singleHole(BlockPos position) {
        if (!isAirHole(position)) {
            return null;
        }

        List<Block> walls = new ArrayList<>();
        walls.add(block(position.below()));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            walls.add(block(position.relative(direction)));
        }

        HoleType type = classifyWalls(walls);
        return type == null ? null : new Hole(new AABB(position), position, position, type);
    }

    private Hole doubleHole(BlockPos position) {
        for (Direction direction : List.of(Direction.NORTH, Direction.EAST)) {
            BlockPos second = position.relative(direction);
            if (!isAirHole(position) || !isAirHole(second)) {
                continue;
            }

            List<Block> walls = new ArrayList<>();
            walls.add(block(position.below()));
            walls.add(block(second.below()));
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos firstWall = position.relative(side);
                BlockPos secondWall = second.relative(side);
                if (!firstWall.equals(second)) {
                    walls.add(block(firstWall));
                }
                if (!secondWall.equals(position)) {
                    walls.add(block(secondWall));
                }
            }

            HoleType type = classifyWalls(walls);
            if (type == null) {
                continue;
            }
            AABB box = new AABB(
                    Math.min(position.getX(), second.getX()),
                    position.getY(),
                    Math.min(position.getZ(), second.getZ()),
                    Math.max(position.getX(), second.getX()) + 1.0,
                    position.getY() + 1.0,
                    Math.max(position.getZ(), second.getZ()) + 1.0);
            return new Hole(box, position, second, type);
        }
        return null;
    }

    private boolean isAirHole(BlockPos position) {
        return mc.level.getBlockState(position).canBeReplaced()
                && mc.level.getBlockState(position.above()).canBeReplaced();
    }

    private HoleType classifyWalls(List<Block> walls) {
        boolean hasBedrock = false;
        boolean hasOtherSafeBlock = false;
        for (Block wall : walls) {
            if (!isSafe(wall)) {
                return null;
            }
            if (wall == Blocks.BEDROCK) {
                hasBedrock = true;
            } else {
                hasOtherSafeBlock = true;
            }
        }
        if (hasBedrock && !hasOtherSafeBlock) {
            return HoleType.Bedrock;
        }
        if (!hasBedrock) {
            return HoleType.Obsidian;
        }
        return HoleType.Mixed;
    }

    private static boolean isSafe(Block block) {
        return block == Blocks.BEDROCK
                || block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.RESPAWN_ANCHOR
                || block == Blocks.ENDER_CHEST;
    }

    private Block block(BlockPos position) {
        return mc.level.getBlockState(position).getBlock();
    }

    private enum HoleType {
        Bedrock,
        Obsidian,
        Mixed
    }

    private record Hole(AABB box, BlockPos center, BlockPos second, HoleType type) {
    }
}
