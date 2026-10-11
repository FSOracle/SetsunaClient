package com.setsuna.util.rotation;

import com.setsuna.manager.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RotationUtils {

    private static final Minecraft mc = Minecraft.getInstance();

    private RotationUtils() {
    }

    public static Direction getClickSide(BlockPos pos) {
        Direction bestSide = findBestDirection(pos, true);
        if (bestSide != null) {
            return bestSide;
        }
        bestSide = findBestDirection(pos, false);
        return bestSide != null ? bestSide : Direction.UP;
    }

    private static Direction findBestDirection(BlockPos pos, boolean useGrimCheck) {
        Direction bestSide = null;
        double minDistSqr = Double.MAX_VALUE;
        Vec3 eyePos = mc.player.getEyePosition();

        for (Direction side : Direction.values()) {
            if (useGrimCheck) {
                if (!canSee(pos, side)) {
                    continue;
                }
            } else {
                if (!isGrimDirection(pos, side)) {
                    continue;
                }
            }
            double distSqr = eyePos.distanceToSqr(Vec3.atCenterOf(pos.relative(side)));
            if (distSqr < minDistSqr) {
                minDistSqr = distSqr;
                bestSide = side;
            }
        }
        return bestSide;
    }

    public static boolean canSee(BlockPos pos, Direction side) {
        Vec3 testVec = pos.getCenter().add(side.getUnitVec3i().getX() * 0.5, side.getUnitVec3i().getY() * 0.5, side.getUnitVec3i().getZ() * 0.5);
        HitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), testVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS;
    }

    private static boolean isIntersected(AABB bb, AABB other) {
        return overlaps(bb.minX, bb.maxX, other.minX, other.maxX)
                && overlaps(bb.minY, bb.maxY, other.minY, other.maxY)
                && overlaps(bb.minZ, bb.maxZ, other.minZ, other.maxZ);
    }

    private static boolean overlaps(double firstMin, double firstMax, double secondMin, double secondMax) {
        return secondMax - Shapes.EPSILON > firstMin
                && secondMin + Shapes.EPSILON < firstMax;
    }

    private static AABB getCombinedBox(BlockPos pos, Level level) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos).move(pos);
        AABB combined = new AABB(pos);
        for (AABB box : shape.toAabbs()) {
            double minX = Math.max(box.minX, combined.minX);
            double minY = Math.max(box.minY, combined.minY);
            double minZ = Math.max(box.minZ, combined.minZ);
            double maxX = Math.min(box.maxX, combined.maxX);
            double maxY = Math.min(box.maxY, combined.maxY);
            double maxZ = Math.min(box.maxZ, combined.maxZ);
            combined = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return combined;
    }

    public static boolean isGrimDirection(BlockPos pos, Direction direction) {
        AABB combined = getCombinedBox(pos, mc.level);
        LocalPlayer player = mc.player;
        AABB eyePositions = new AABB(player.getX(), player.getY() + 0.4, player.getZ(), player.getX(), player.getY() + 1.62, player.getZ()).inflate(0.0002);
        if (isIntersected(eyePositions, combined)) {
            return true;
        }
        return switch (direction) {
            case NORTH -> notGreater(eyePositions.minZ, combined.minZ);
            case SOUTH -> notLess(eyePositions.maxZ, combined.maxZ);
            case EAST -> notLess(eyePositions.maxX, combined.maxX);
            case WEST -> notGreater(eyePositions.minX, combined.minX);
            case UP -> notLess(eyePositions.maxY, combined.maxY);
            case DOWN -> notGreater(eyePositions.minY, combined.minY);
        };
    }

    private static boolean notGreater(double value, double boundary) {
        return !(value > boundary);
    }

    private static boolean notLess(double value, double boundary) {
        return !(value < boundary);
    }

    public static Direction getDirection(BlockPos blockPos) {
        double eyePos = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
        VoxelShape outline = mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos);

        Direction vertical = verticalClickSide(blockPos, outline, eyePos);
        return vertical != null ? vertical : horizontalClickSide(blockPos);
    }

    private static Direction verticalClickSide(BlockPos blockPos, VoxelShape outline, double eyeY) {
        double blockY = blockPos.getY();
        if (eyeY > blockY + outline.max(Direction.Axis.Y)
                && mc.level.getBlockState(blockPos.above()).canBeReplaced()) {
            return Direction.UP;
        }
        if (eyeY < blockY + outline.min(Direction.Axis.Y)
                && mc.level.getBlockState(blockPos.below()).canBeReplaced()) {
            return Direction.DOWN;
        }
        return null;
    }

    private static Direction horizontalClickSide(BlockPos target) {
        BlockPos delta = target.subtract(mc.player.blockPosition());
        int x = delta.getX();
        int z = delta.getZ();
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? Direction.WEST : Direction.EAST;
        return z > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    public static boolean isInFov(Entity entity, float fov) {
        if (fov >= 360.0) return true;
        float yawDiff = Math.abs(Mth.wrapDegrees(getRotationsToEntity(entity).getYaw() - mc.player.getYRot()));
        return yawDiff <= fov / 2.0;
    }

    public static Rot2f getRotationsToEntity(Entity entity) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 targetPos = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
        return centerRotation(eyePos, targetPos);
    }

    public static double getEyeDistanceToEntity(LivingEntity entity) {
        Vec3 eyePos = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        Vec3 outside = new Vec3(
                distanceOutside(eyePos.x, box.minX, box.maxX),
                distanceOutside(eyePos.y, box.minY, box.maxY),
                distanceOutside(eyePos.z, box.minZ, box.maxZ));
        return outside.length();
    }

    public static Rot2f calculate(final Vec3 from, final Vec3 to) {
        return RotationDelta.between(from, to).asRotation();
    }

    public static Rot2f calculate(final Entity entity) {
        return calculate(entity.position().add(0, Mth.clamp(
                mc.player.getY() - entity.getY() + mc.player.getEyeHeight(),
                0.0,
                (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) * 0.9
        ), 0));
    }

    public static Rot2f calculate(final Entity entity, final boolean adaptive, final double range) {
        Rot2f normalRotations = calculate(entity);
        HitResult result = RaytraceUtils.raytrace(normalRotations, range, 0.0f);
        if (!adaptive || (result != null && result.getType() == HitResult.Type.ENTITY)) {
            return normalRotations;
        }
        Rot2f visiblePoint = new AdaptiveAimSearch(entity, range).find();
        return visiblePoint == null ? normalRotations : visiblePoint;
    }

    public static Rot2f calculate(BlockPos to) {
        return calculate(mc.player.getEyePosition(), to.getCenter());
    }

    public static Rot2f calculate(Vec3 to) {
        return calculate(mc.player.getEyePosition(), to);
    }

    public static Rot2f calculate(Vec3 position, Direction direction) {
        return calculate(offsetPoint(position.x, position.y, position.z, direction));
    }

    public static Rot2f calculate(BlockPos position, Direction direction) {
        return calculate(offsetPoint(position.getX(), position.getY(), position.getZ(), direction));
    }

    public static Rot2f applySensitivityPatch(Rot2f rotation) {
        Rot2f previousRotation = new Rot2f(RotationManager.INSTANCE.getLastRotation().getYaw(), RotationManager.INSTANCE.getLastRotation().getPitch());
        return applySensitivityPatch(rotation, previousRotation);
    }

    public static Rot2f applySensitivityPatch(Rot2f rotation, Rot2f previousRotation) {
        double multiplier = sensitivityStep();
        float yaw = quantizeAngle(rotation.getYaw(), previousRotation.getYaw(), multiplier);
        float pitch = quantizeAngle(rotation.getPitch(), previousRotation.getPitch(), multiplier);
        return new Rot2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    public static Rot2f relateToPlayerRotation(Rot2f rotation) {
        Rot2f previousRotation = new Rot2f(RotationManager.INSTANCE.getLastRotation().getYaw(), RotationManager.INSTANCE.getLastRotation().getPitch());
        float yaw = previousRotation.getYaw() + Mth.wrapDegrees(rotation.getYaw() - previousRotation.getYaw());
        float pitch = Mth.clamp(rotation.getPitch(), -90, 90);
        return new Rot2f(yaw, pitch);
    }

    public static Rot2f resetRotation(final Rot2f rotation) {
        if (rotation == null) return null;
        final float yaw = rotation.getYaw() + Mth.wrapDegrees(mc.player.getYRot() - rotation.getYaw());
        final float pitch = mc.player.getXRot();
        return new Rot2f(yaw, pitch);
    }

    public static Rot2f move(Rot2f targetRotation, double speed) {
        return move(RotationManager.INSTANCE.lastRotations, targetRotation, speed);
    }

    public static Rot2f move(Rot2f lastRotation, Rot2f targetRotation, double speed) {
        if (speed != 0) {
            AngularDelta delta = AngularDelta.between(lastRotation, targetRotation);
            return delta.limit(speed);
        }
        return new Rot2f(0, 0);
    }

    public static Rot2f smooth(final Rot2f targetRotation, final double speed) {
        return smooth(RotationManager.INSTANCE.lastRotations, targetRotation, speed);
    }

    public static Rot2f smooth(final Rot2f lastRotation, final Rot2f targetRotation, final double speed) {
        float yaw = targetRotation.getYaw();
        float pitch = targetRotation.getPitch();
        final float lastYaw = lastRotation.getYaw();
        final float lastPitch = lastRotation.getPitch();

        if (speed != 0) {
            Rot2f move = move(targetRotation, speed);
            yaw = lastYaw + move.getYaw();
            pitch = lastPitch + move.getPitch();

            for (int i = 1; i <= (int) (mc.getFps() / 20f + Math.random() * 10); ++i) {
                if (Math.abs(move.getYaw()) + Math.abs(move.getPitch()) > 0.0001) {
                    yaw += (float) ((Math.random() - 0.5) / 1000);
                    pitch -= (float) (Math.random() / 200);
                }
                Rot2f corrected = applySensitivityPatch(new Rot2f(yaw, pitch));
                yaw = corrected.getYaw();
                pitch = corrected.getPitch();
            }
        }
        return new Rot2f(yaw, pitch);
    }

    private static Rot2f centerRotation(Vec3 eye, Vec3 target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
        return new Rot2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    private static double distanceOutside(double coordinate, double minimum, double maximum) {
        return Math.max(minimum - coordinate, Math.max(0.0D, coordinate - maximum));
    }

    private static boolean hitsEntity(Rot2f rotation, double range) {
        HitResult result = RaytraceUtils.raytrace(rotation, range, 0.0F);
        return result != null && result.getType() == HitResult.Type.ENTITY;
    }

    private static Vec3 offsetPoint(double x, double y, double z, Direction direction) {
        return new Vec3(
                x + 0.5D + direction.getStepX() * 0.5D,
                y + 0.5D + direction.getStepY() * 0.5D,
                z + 0.5D + direction.getStepZ() * 0.5D
        );
    }

    private static float quantizeAngle(float value, float base, double multiplier) {
        return base + (float) (Math.round((value - base) / multiplier) * multiplier);
    }

    private static float clampDelta(double delta, double limit) {
        return (float) Math.max(Math.min(delta, limit), -limit);
    }

    private static double sensitivityStep() {
        double option = mc.options.sensitivity().get();
        float scaled = (float) (option * (1 + Math.random() / 10000000) * 0.6F + 0.2F);
        return scaled * scaled * scaled * 8.0F * 0.15D;
    }

    private record RotationDelta(double x, double y, double z) {
        private static RotationDelta between(Vec3 from, Vec3 to) {
            Vec3 difference = to.subtract(from);
            return new RotationDelta(difference.x, difference.y, difference.z);
        }

        private Rot2f asRotation() {
            double horizontal = Math.hypot(x, z);
            float yaw = (float) Math.toDegrees(Mth.atan2(z, x)) - 90.0F;
            float pitch = (float) -Math.toDegrees(Mth.atan2(y, horizontal));
            return new Rot2f(yaw, pitch);
        }
    }

    private record AngularDelta(double yaw, double pitch) {
        private static AngularDelta between(Rot2f from, Rot2f to) {
            return new AngularDelta(
                    Mth.wrapDegrees(to.getYaw() - from.getYaw()),
                    to.getPitch() - from.getPitch());
        }

        private Rot2f limit(double speed) {
            double distance = Math.sqrt(yaw * yaw + pitch * pitch);
            double yawLimit = speed * Math.abs(yaw / distance);
            double pitchLimit = speed * Math.abs(pitch / distance);
            return new Rot2f(clampDelta(yaw, yawLimit), clampDelta(pitch, pitchLimit));
        }
    }

    private static final class AdaptiveAimSearch {
        private final Vec3 origin;
        private final double width;
        private final double height;
        private final double depth;
        private final double range;

        private AdaptiveAimSearch(Entity entity, double range) {
            AABB bounds = entity.getBoundingBox();
            origin = entity.position();
            width = bounds.maxX - bounds.minX;
            height = bounds.maxY - bounds.minY;
            depth = bounds.maxZ - bounds.minZ;
            this.range = range;
        }

        private Rot2f find() {
            double y = 1.0D;
            while (y >= 0.0D) {
                double x = 1.0D;
                while (x >= -0.5D) {
                    double z = 1.0D;
                    while (z >= -0.5D) {
                        Rot2f candidate = calculate(origin.add(width * x, height * y, depth * z));
                        if (hitsEntity(candidate, range)) return candidate;
                        z -= 0.5D;
                    }
                    x -= 0.5D;
                }
                y -= 0.25D + Math.random() * 0.1D;
            }
            return null;
        }
    }
}
