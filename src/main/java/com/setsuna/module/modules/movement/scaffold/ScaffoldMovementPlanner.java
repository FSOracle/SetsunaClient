package com.setsuna.module.modules.movement.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks the support blocks used by Scaffold and derives the stable eight-way
 * movement line used by LiquidBounce's placement prediction.
 */
public final class ScaffoldMovementPlanner {

    private static final int MAX_LAST_PLACED_BLOCKS = 4;
    private static final float DIRECTION_HYSTERESIS_DEGREES = 30.0F;
    private static final double SUPPORT_SURFACE_EPSILON = 1.0E-3D;
    private static final double SUPPORT_OVERLAP_HYSTERESIS = 0.02D;
    private static final double[] SUPPORT_SAMPLE_OFFSETS = {0.301D, 0.0D, -0.301D};

    private final Minecraft mc = Minecraft.getInstance();
    private final ArrayDeque<BlockPos> lastPlacedBlocks = new ArrayDeque<>(MAX_LAST_PLACED_BLOCKS);

    private BlockPos lastPosition;
    private SupportReference lastSupportReference;
    private float lastDirectionAngle = Float.NaN;

    public Line getOptimalMovementLine(float forward, float strafe) {
        LocalPlayer player = mc.player;
        if (player == null || (forward == 0.0F && strafe == 0.0F)) {
            return null;
        }

        Vec3 direction = chooseDirection(movementAngle(player.getYRot(), forward, strafe));
        SupportReference supportReference = findSupportReferenceUnderPlayer();
        if (supportReference == null) {
            return null;
        }
        lastSupportReference = supportReference;

        Line placedBlocksLine = fitLineThroughLastPlacedBlocks();
        Vec3 anchor;
        if (placedBlocksLine != null && placedBlocksLine.direction().dot(direction) >= 0.5D) {
            anchor = placedBlocksLine.nearestPoint(player.position());
        } else {
            anchor = new Vec3(
                    supportReference.blockPos().getX() + 0.5D + supportReference.offsetX(),
                    player.getY(),
                    supportReference.blockPos().getZ() + 0.5D + supportReference.offsetZ());
        }

        return new Line(new Vec3(anchor.x, player.getY(), anchor.z), direction);
    }

    private SupportReference findSupportReferenceUnderPlayer() {
        Map<BlockPos, SupportCandidate> candidates = collectSupportCandidates();
        if (candidates.isEmpty()) {
            lastSupportReference = null;
            lastPosition = null;
            return null;
        }

        SupportCandidate best = null;
        for (SupportCandidate candidate : candidates.values()) {
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }

        SupportCandidate selected = stableCandidate(candidates.get(lastPlacedBlocks.peekLast()), best);
        if (selected == null) {
            selected = stableCandidate(candidates.get(lastPosition), best);
        }
        if (selected == null) {
            selected = best;
        }

        lastPosition = selected.blockPos();
        LocalPlayer player = mc.player;
        return new SupportReference(
                selected.blockPos(),
                player.getX() - (selected.blockPos().getX() + 0.5D),
                player.getZ() - (selected.blockPos().getZ() + 0.5D));
    }

    private Map<BlockPos, SupportCandidate> collectSupportCandidates() {
        Map<BlockPos, SupportCandidate> candidates = new LinkedHashMap<>(9);
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return candidates;
        }

        for (double xOffset : SUPPORT_SAMPLE_OFFSETS) {
            for (double zOffset : SUPPORT_SAMPLE_OFFSETS) {
                BlockPos pos = BlockPos.containing(
                        player.getX() + xOffset,
                        player.getY() - 1.0D,
                        player.getZ() + zOffset);
                if (candidates.containsKey(pos)) {
                    continue;
                }

                VoxelShape shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
                if (!shape.isEmpty()) {
                    candidates.put(pos.immutable(), createSupportCandidate(pos, shape));
                }
            }
        }
        return candidates;
    }

    private SupportCandidate createSupportCandidate(BlockPos pos, VoxelShape shape) {
        AABB playerBox = mc.player.getBoundingBox();
        double bestSurfaceDelta = Double.POSITIVE_INFINITY;
        double overlapOnBestSurface = 0.0D;

        for (AABB local : shape.toAabbs()) {
            double minX = pos.getX() + local.minX;
            double maxX = pos.getX() + local.maxX;
            double maxY = pos.getY() + local.maxY;
            double minZ = pos.getZ() + local.minZ;
            double maxZ = pos.getZ() + local.maxZ;

            double overlapX = Math.min(playerBox.maxX, maxX) - Math.max(playerBox.minX, minX);
            double overlapZ = Math.min(playerBox.maxZ, maxZ) - Math.max(playerBox.minZ, minZ);
            if (overlapX <= 0.0D || overlapZ <= 0.0D) {
                continue;
            }

            double surfaceDelta = Math.abs(playerBox.minY - maxY);
            double overlap = overlapX * overlapZ;
            if (surfaceDelta + SUPPORT_SURFACE_EPSILON < bestSurfaceDelta) {
                bestSurfaceDelta = surfaceDelta;
                overlapOnBestSurface = overlap;
            } else if (Math.abs(surfaceDelta - bestSurfaceDelta) <= SUPPORT_SURFACE_EPSILON) {
                overlapOnBestSurface += overlap;
            }
        }

        double dx = mc.player.getX() - (pos.getX() + 0.5D);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5D);
        return new SupportCandidate(pos.immutable(), overlapOnBestSurface, bestSurfaceDelta, dx * dx + dz * dz);
    }

    private static SupportCandidate stableCandidate(SupportCandidate preferred, SupportCandidate best) {
        if (preferred == null) {
            return null;
        }
        if (preferred.surfaceDelta() > best.surfaceDelta() + SUPPORT_SURFACE_EPSILON) {
            return null;
        }
        if (preferred.overlapArea() + SUPPORT_OVERLAP_HYSTERESIS < best.overlapArea()) {
            return null;
        }
        return preferred;
    }

    private Line fitLineThroughLastPlacedBlocks() {
        if (lastPlacedBlocks.size() < 2) {
            return null;
        }
        BlockPos last = lastPlacedBlocks.peekLast();
        BlockPos previous = null;
        for (BlockPos pos : lastPlacedBlocks) {
            if (!pos.equals(last)) {
                previous = pos;
            }
        }
        if (last == null || previous == null) {
            return null;
        }

        Vec3 a = previous.getBottomCenter();
        Vec3 b = last.getBottomCenter();
        Vec3 direction = b.subtract(a);
        if (direction.horizontalDistanceSqr() < 1.0E-8D) {
            return null;
        }
        return new Line(a.add(b).scale(0.5D), direction.normalize());
    }

    private Vec3 chooseDirection(float currentAngle) {
        if (!Float.isNaN(lastDirectionAngle)
                && Math.abs(Mth.wrapDegrees(currentAngle - lastDirectionAngle)) <= DIRECTION_HYSTERESIS_DEGREES) {
            return Vec3.directionFromRotation(0.0F, lastDirectionAngle);
        }

        float directionNumber = currentAngle / 180.0F * 4.0F + 4.0F;
        float rounded = Math.round(directionNumber);
        lastDirectionAngle = Mth.wrapDegrees((rounded - 4.0F) / 4.0F * 180.0F);
        return Vec3.directionFromRotation(0.0F, lastDirectionAngle);
    }

    public void trackPlacedBlock(BlockPos pos) {
        if (pos == null || pos.equals(lastPlacedBlocks.peekLast())) {
            return;
        }
        while (lastPlacedBlocks.size() >= MAX_LAST_PLACED_BLOCKS) {
            lastPlacedBlocks.removeFirst();
        }
        lastPlacedBlocks.addLast(pos.immutable());
    }

    public void reset() {
        lastPosition = null;
        lastSupportReference = null;
        lastDirectionAngle = Float.NaN;
        lastPlacedBlocks.clear();
    }

    public SupportReference getCurrentSupportReference() {
        return lastSupportReference;
    }

    public static float movementAngle(float yaw, float forward, float strafe) {
        double radians = Math.toRadians(yaw + 90.0F);
        double x = forward * Math.cos(radians) + strafe * Math.sin(radians);
        double z = forward * Math.sin(radians) - strafe * Math.cos(radians);
        if (x * x + z * z < 1.0E-8D) {
            return Mth.wrapDegrees(yaw);
        }
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-x, z)));
    }

    public record SupportReference(BlockPos blockPos, double offsetX, double offsetZ) {
    }

    public record Line(Vec3 point, Vec3 direction) {
        public Line {
            direction = direction.normalize();
        }

        public Vec3 nearestPoint(Vec3 position) {
            double lengthSquared = direction.lengthSqr();
            if (lengthSquared < 1.0E-12D) {
                return point;
            }
            double t = position.subtract(point).dot(direction) / lengthSquared;
            return point.add(direction.scale(t));
        }

        public double distanceToSqr(Vec3 position) {
            return nearestPoint(position).distanceToSqr(position);
        }
    }

    private record SupportCandidate(
            BlockPos blockPos,
            double overlapArea,
            double surfaceDelta,
            double horizontalDistanceSquared) implements Comparable<SupportCandidate> {

        @Override
        public int compareTo(SupportCandidate other) {
            if (surfaceDelta + SUPPORT_SURFACE_EPSILON < other.surfaceDelta) return -1;
            if (other.surfaceDelta + SUPPORT_SURFACE_EPSILON < surfaceDelta) return 1;
            if (overlapArea > other.overlapArea + SUPPORT_OVERLAP_HYSTERESIS) return -1;
            if (overlapArea + SUPPORT_OVERLAP_HYSTERESIS < other.overlapArea) return 1;
            return Double.compare(horizontalDistanceSquared, other.horizontalDistanceSquared);
        }
    }
}
