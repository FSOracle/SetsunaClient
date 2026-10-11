package com.setsuna.util.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Ray-casting helpers used by the combat/scaffold modules to test whether a
 * given {@link Rot2f} actually lines up with a target entity or block. Mirrors
 * the vanilla pick logic but drives it from an arbitrary rotation rather than the
 * camera, so silent-aim can verify hits before committing.
 */
public final class RaytraceUtils {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final double[] BOX_SCAN_PROPORTIONS = {
            0.05, 0.15, 0.25, 0.35, 0.45,
            0.55, 0.65, 0.75, 0.85, 0.95
    };

    private RaytraceUtils() {
    }

    public static boolean canSeePointFrom(Vec3 eyes, Vec3 target) {
        return mc.level.clip(new ClipContext(eyes, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    public static HitResult raytrace(Rot2f rotation, double range) {
        return raytrace(rotation, range, 0);
    }

    public static HitResult raytrace(Rot2f rotation, double range, float expand) {
        return raytrace(rotation, range, expand, mc.player);
    }

    public static HitResult raytrace(Rot2f rotation, double range, float expand, Entity entity) {
        if (mc.level == null || entity == null) return null;

        RaySegment ray = RaySegment.from(entity, rotation, range,
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        HitResult blockHit = clipFrom(entity, ray.start(), ray.end(), ClipContext.Block.OUTLINE);
        double blockDistance = distanceToBlock(blockHit, ray.start(), range);
        EntitySelection selection = selectEntity(entity, ray, blockDistance, expand);

        if (selection.entity() != null
                && (selection.distance() < blockDistance || blockHit.getType() == HitResult.Type.MISS)) {
            return new EntityHitResult(selection.entity(), selection.hit());
        }
        return blockHit;
    }

    private static HitResult clipFrom(Entity source, Vec3 start, Vec3 end, ClipContext.Block blockMode) {
        return mc.level.clip(new ClipContext(start, end, blockMode, ClipContext.Fluid.NONE, source));
    }

    private static double distanceToBlock(HitResult hit, Vec3 start, double fallback) {
        return hit.getType() == HitResult.Type.MISS ? fallback : hit.getLocation().distanceTo(start);
    }

    private static EntitySelection selectEntity(Entity source, RaySegment ray, double blockDistance, float expand) {
        AABB search = source.getBoundingBox().expandTowards(ray.direction().scale(ray.range())).inflate(1.0D);
        List<Entity> candidates = mc.level.getEntities(source, search, entity -> !entity.isSpectator() && entity.isPickable());
        EntitySelection selection = new EntitySelection(blockDistance);
        for (Entity candidate : candidates) {
            selection.consider(source, candidate, ray, expand);
        }
        return selection;
    }

    private record RaySegment(Vec3 start, Vec3 direction, Vec3 end, double range) {
        static RaySegment from(Entity source, Rot2f rotation, double range, float partialTicks) {
            Vec3 start = source.getEyePosition(partialTicks);
            Vec3 direction = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
            return new RaySegment(start, direction, start.add(direction.scale(range)), range);
        }
    }

    private static final class EntitySelection {
        private Entity entity;
        private Vec3 hit;
        private double distance;

        private EntitySelection(double blockDistance) {
            this.distance = blockDistance;
        }

        private void consider(Entity source, Entity candidate, RaySegment ray, float expand) {
            float radius = candidate.getPickRadius() + expand;
            AABB box = candidate.getBoundingBox().inflate(radius);
            Optional<Vec3> intercept = box.clip(ray.start(), ray.end());

            if (box.contains(ray.start())) {
                if (distance >= 0.0D) {
                    entity = candidate;
                    hit = intercept.orElse(ray.start());
                    distance = 0.0D;
                }
                return;
            }
            if (intercept.isEmpty()) return;

            Vec3 point = intercept.get();
            double candidateDistance = ray.start().distanceTo(point);
            if (!(candidateDistance < distance || distance == 0.0D)) return;

            if (candidate.getRootVehicle() == source.getRootVehicle()) {
                if (distance == 0.0D) {
                    entity = candidate;
                    hit = point;
                }
                return;
            }

            entity = candidate;
            hit = point;
            distance = candidateDistance;
        }

        private Entity entity() {
            return entity;
        }

        private Vec3 hit() {
            return hit;
        }

        private double distance() {
            return distance;
        }
    }

    /** LiquidBounce-style entity-only raycast with optional entity filtering. */
    public static EntityHitResult findEntityInCrosshair(
            Rot2f rotation,
            double range,
            Predicate<Entity> predicate) {
        return findEntityInCrosshair(mc.getCameraEntity(), rotation, range, predicate);
    }

    public static EntityHitResult findEntityInCrosshair(
            Entity source,
            Rot2f rotation,
            double range,
            Predicate<Entity> predicate) {
        if (mc.level == null || source == null || rotation == null || range < 0.0) return null;

        Vec3 eyes = source.getEyePosition();
        Vec3 direction = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 end = eyes.add(direction.scale(range));
        AABB searchBox = source.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0);
        Predicate<Entity> filter = predicate == null
                ? EntitySelector.CAN_BE_PICKED
                : EntitySelector.CAN_BE_PICKED.and(predicate);
        return ProjectileUtil.getEntityHitResult(
                source, eyes, end, searchBox, filter, range * range);
    }

    /**
     * Validates an exact target while applying LiquidBounce's separate normal
     * and through-wall distance rules.
     */
    public static EntityHitResult isLookingAtEntity(
            Rot2f rotation,
            Entity target,
            double range,
            double throughWallsRange) {
        Entity source = mc.getCameraEntity();
        if (source == null || target == null) return null;

        EntityHitResult hit = findEntityInCrosshair(source, rotation, range, entity -> true);
        if (hit == null || hit.getEntity() != target) return null;

        Vec3 eyes = source.getEyePosition();
        double distanceSquared = eyes.distanceToSqr(hit.getLocation());
        double wallRangeSquared = throughWallsRange * throughWallsRange;
        if (distanceSquared <= wallRangeSquared) return hit;
        if (distanceSquared > range * range) return null;

        HitResult wallHit = source.level().clip(new ClipContext(
                eyes,
                hit.getLocation(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                source));
        return wallHit.getType() == HitResult.Type.MISS ? hit : null;
    }

    /**
     * LiquidBounce-style box raytrace. The preferred point is tried first;
     * blocked edge cases scan the box faces visible from the observer and pick
     * the valid rotation with the smallest angular difference.
     */
    public static Rot2f raytraceBox(
            Vec3 eyes,
            AABB box,
            double range,
            double throughWallsRange,
            Vec3 preferredPoint,
            Entity observer) {
        if (eyes == null || box == null || preferredPoint == null || observer == null
                || range < 0.0 || throughWallsRange < 0.0) {
            return null;
        }

        double rangeSquared = range * range;
        double wallRangeSquared = throughWallsRange * throughWallsRange;
        Rot2f preferredRotation = RotationUtils.calculate(eyes, preferredPoint);
        Vec3 preferredHit = firstHit(box, eyes, preferredPoint);
        if (preferredHit != null) {
            double distanceSquared = eyes.distanceToSqr(preferredHit);
            boolean visible = isVisible(observer, eyes, preferredHit);
            if (isWithinAllowedRange(distanceSquared, visible, rangeSquared, wallRangeSquared)) {
                return RotationUtils.calculate(eyes, preferredHit);
            }
        }

        List<Vec3> spots = new java.util.ArrayList<>();
        spots.add(preferredPoint);
        spots.add(closestPoint(eyes, box));
        addProjectedFaceSamples(spots, eyes, box);

        RaytraceBoxCandidate bestVisible = null;
        RaytraceBoxCandidate bestInvisible = null;
        for (Vec3 spot : spots) {
            Vec3 extended = eyes.add(spot.subtract(eyes).scale(2.0));
            Vec3 spotOnBox = firstHit(box, eyes, extended);
            if (spotOnBox == null) continue;

            double distanceSquared = eyes.distanceToSqr(spotOnBox);
            boolean visible = isVisible(observer, eyes, spotOnBox);
            if (!isWithinAllowedRange(distanceSquared, visible, rangeSquared, wallRangeSquared)) {
                continue;
            }

            Rot2f rotation = RotationUtils.calculate(eyes, spot);
            RaytraceBoxCandidate candidate = new RaytraceBoxCandidate(
                    rotation, rotationDifferenceSquared(preferredRotation, rotation));
            if (visible) {
                if (bestVisible == null || candidate.rotationDifferenceSquared()
                        < bestVisible.rotationDifferenceSquared()) {
                    bestVisible = candidate;
                }
            } else if (bestInvisible == null || candidate.rotationDifferenceSquared()
                    < bestInvisible.rotationDifferenceSquared()) {
                bestInvisible = candidate;
            }
        }

        RaytraceBoxCandidate best = bestVisible != null ? bestVisible : bestInvisible;
        return best == null ? null : best.rotation();
    }

    private static void addProjectedFaceSamples(List<Vec3> spots, Vec3 eyes, AABB box) {
        boolean outside = false;
        if (eyes.x < box.minX || eyes.x > box.maxX) {
            outside = true;
            double x = eyes.x < box.minX ? box.minX : box.maxX;
            for (double y : BOX_SCAN_PROPORTIONS) {
                for (double z : BOX_SCAN_PROPORTIONS) {
                    spots.add(new Vec3(
                            x,
                            Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ)));
                }
            }
        }
        if (eyes.y < box.minY || eyes.y > box.maxY) {
            outside = true;
            double y = eyes.y < box.minY ? box.minY : box.maxY;
            for (double x : BOX_SCAN_PROPORTIONS) {
                for (double z : BOX_SCAN_PROPORTIONS) {
                    spots.add(new Vec3(
                            Mth.lerp(x, box.minX, box.maxX),
                            y,
                            Mth.lerp(z, box.minZ, box.maxZ)));
                }
            }
        }
        if (eyes.z < box.minZ || eyes.z > box.maxZ) {
            outside = true;
            double z = eyes.z < box.minZ ? box.minZ : box.maxZ;
            for (double x : BOX_SCAN_PROPORTIONS) {
                for (double y : BOX_SCAN_PROPORTIONS) {
                    spots.add(new Vec3(
                            Mth.lerp(x, box.minX, box.maxX),
                            Mth.lerp(y, box.minY, box.maxY),
                            z));
                }
            }
        }
        if (outside) return;

        for (double x : BOX_SCAN_PROPORTIONS) {
            for (double y : BOX_SCAN_PROPORTIONS) {
                for (double z : BOX_SCAN_PROPORTIONS) {
                    spots.add(new Vec3(
                            Mth.lerp(x, box.minX, box.maxX),
                            Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ)));
                }
            }
        }
    }

    private static boolean isVisible(Entity observer, Vec3 eyes, Vec3 target) {
        return observer.level().clip(new ClipContext(
                eyes,
                target,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                observer)).getType() == HitResult.Type.MISS;
    }

    private static boolean isWithinAllowedRange(
            double distanceSquared,
            boolean visible,
            double rangeSquared,
            double wallRangeSquared) {
        return distanceSquared < (visible ? rangeSquared : wallRangeSquared);
    }

    private static Vec3 firstHit(AABB box, Vec3 from, Vec3 to) {
        Optional<Vec3> hit = box.contains(from) ? box.clip(to, from) : box.clip(from, to);
        return hit.orElse(null);
    }

    private static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    private static double rotationDifferenceSquared(Rot2f first, Rot2f second) {
        float yaw = Mth.wrapDegrees(second.getYaw() - first.getYaw());
        float pitch = second.getPitch() - first.getPitch();
        return yaw * yaw + pitch * pitch;
    }

    private record RaytraceBoxCandidate(Rot2f rotation, double rotationDifferenceSquared) {
    }

    public static boolean overBlock(Rot2f rotation, Direction dir, BlockPos pos, boolean strict) {
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 end = eye.add(Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw()).scale(4.5D));
        BlockHitResult hit = (BlockHitResult) clipFrom(mc.player, eye, end, ClipContext.Block.OUTLINE);
        return hit.getType() != HitResult.Type.MISS
                && hit.getBlockPos().equals(pos)
                && (!strict || hit.getDirection() == dir);
    }

    public static boolean overBlock(Rot2f rotation, BlockPos pos, boolean strict) {
        return overBlock(rotation, Direction.UP, pos, strict);
    }

    public static boolean overBlock(Rot2f rotation, BlockPos pos) {
        return overBlock(rotation, Direction.UP, pos, false);
    }

    public static boolean overBlock(Rot2f rotation, BlockPos pos, Direction facing) {
        return overBlock(rotation, facing, pos, true);
    }
}
