package com.setsuna.module.modules.movement.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Exact collision-box edge traversal used by LiquidBounce's Scaffold prediction. */
public final class ScaffoldEdgeCollision {

    private ScaffoldEdgeCollision() {
    }

    public static Vec3 find(Minecraft mc, Vec3 from, Vec3 to, double allowedDropDown) {
        if (mc == null || mc.player == null || mc.level == null || from == null || to == null) {
            return null;
        }
        return findAlongBoxes(from, to, collectCollisionBoxes(mc, from, to, allowedDropDown));
    }

    static Vec3 findAlongBoxes(Vec3 from, Vec3 to, List<AABB> sourceBoxes) {
        Vec3 line = to.subtract(from);
        if (line.lengthSqr() <= 1.0E-12D) {
            return null;
        }

        List<AABB> boxes = new ArrayList<>(sourceBoxes);
        Vec3 current = from;
        Vec3 extendedFrom = from.add(line.scale(-1000.0D));
        Vec3 extendedTo = to.add(line.scale(1000.0D));

        while (true) {
            List<AABB> containing = new ArrayList<>();
            for (AABB box : boxes) {
                if (box.contains(current)) {
                    containing.add(box);
                }
            }
            if (containing.isEmpty()) {
                return current;
            }
            if (containing.stream().anyMatch(box -> box.contains(to))) {
                return null;
            }

            Vec3 next = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (AABB box : containing) {
                Vec3 clipped = box.clip(extendedTo, extendedFrom).orElseThrow(() ->
                        new IllegalStateException("Scaffold edge ray missed a containing collision box"));
                double distance = clipped.distanceToSqr(to);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    next = clipped;
                }
            }

            current = next;
            boxes.removeAll(containing);
        }
    }

    private static List<AABB> collectCollisionBoxes(
            Minecraft mc,
            Vec3 from,
            Vec3 to,
            double allowedDropDown) {
        AABB fromBox = mc.player.getDimensions(Pose.STANDING).makeBoundingBox(from);
        AABB toBox = mc.player.getDimensions(Pose.STANDING).makeBoundingBox(to);
        AABB union = fromBox.minmax(toBox);
        BlockPos start = BlockPos.containing(
                union.minX - 0.3D - 1.0E-7D,
                union.minY - allowedDropDown - 1.0E-7D,
                union.minZ - 0.3D - 1.0E-7D);
        BlockPos end = BlockPos.containing(
                union.maxX + 0.3D + 1.0E-7D,
                union.minY + 1.0E-7D,
                union.maxZ + 0.3D + 1.0E-7D);

        Vec3 line = to.subtract(from);
        Vec3 extendedFrom = from.add(line.scale(-1000.0D));
        Vec3 extendedTo = to.add(line.scale(1000.0D));
        List<AABB> boxes = new ArrayList<>();
        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int y = start.getY(); y <= end.getY(); y++) {
                for (int z = start.getZ(); z <= end.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).forAllBoxes(
                            (minX, minY, minZ, maxX, maxY, maxZ) -> {
                                AABB adjusted = new AABB(
                                        minX - 0.3D,
                                        minY - 1.0D,
                                        minZ - 0.3D,
                                        maxX + 0.3D,
                                        maxY + allowedDropDown + 0.05D,
                                        maxZ + 0.3D).move(pos.getX(), pos.getY(), pos.getZ());
                                if (adjusted.clip(extendedFrom, extendedTo).isPresent()) {
                                    boxes.add(adjusted);
                                }
                            });
                }
            }
        }
        return boxes;
    }
}
