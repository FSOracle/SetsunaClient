package com.setsuna.util.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/** Rotation sampling and sensitivity quantization used by the Nilore ports. */
public final class NiloreRotationModel {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final double SAMPLE_STEP = 0.1D;

    private NiloreRotationModel() {
    }

    public record BestHit(Vec3 eyePosition, Vec3 hitPosition, double distance, Rot2f rotation) {
    }

    /**
     * Scans the target's six bounding-box faces in the same insertion order as
     * Nilore and returns the first point that survives the regular pick raycast.
     */
    public static BestHit findBestHit(Entity entity, Rot2f previousRotation) {
        if (MC.player == null || MC.level == null || entity == null) {
            return null;
        }

        Vec3 eyes = MC.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        Set<Vec3> points = new LinkedHashSet<>();
        points.add(box.getCenter());
        points.add(closestPoint(eyes, box));

        for (double x = box.minX; x <= box.maxX + 1.0E-7D; x += SAMPLE_STEP) {
            for (double y = box.minY; y <= box.maxY + 1.0E-7D; y += SAMPLE_STEP) {
                points.add(new Vec3(x, y, box.minZ));
                points.add(new Vec3(x, y, box.maxZ));
            }
        }
        for (double x = box.minX; x <= box.maxX + 1.0E-7D; x += SAMPLE_STEP) {
            for (double z = box.minZ; z <= box.maxZ + 1.0E-7D; z += SAMPLE_STEP) {
                points.add(new Vec3(x, box.minY, z));
                points.add(new Vec3(x, box.maxY, z));
            }
        }
        for (double y = box.minY; y <= box.maxY + 1.0E-7D; y += SAMPLE_STEP) {
            for (double z = box.minZ; z <= box.maxZ + 1.0E-7D; z += SAMPLE_STEP) {
                points.add(new Vec3(box.minX, y, z));
                points.add(new Vec3(box.maxX, y, z));
            }
        }

        Rot2f previous = previousRotation == null
                ? new Rot2f(MC.player.getYRot(), MC.player.getXRot())
                : previousRotation;
        double pickRange = Math.max(6.0D, MC.player.entityInteractionRange());
        for (Vec3 point : points) {
            Rot2f rotation = RotationUtils.calculate(eyes, point);
            HitResult result = RaytraceUtils.raytrace(rotation, pickRange);
            if (!(result instanceof EntityHitResult hit) || hit.getEntity() != entity) {
                continue;
            }
            if (!box.contains(eyes) && hit.getLocation().distanceTo(eyes) > 3.0D) {
                continue;
            }
            Rot2f snapped = snapToSensitivity(rotation, previous);
            return new BestHit(eyes, hit.getLocation(), hit.getLocation().distanceTo(eyes), snapped);
        }
        return new BestHit(eyes, eyes, 1000.0D, null);
    }

    public static Rot2f snapToSensitivity(Rot2f rotation, Rot2f previous) {
        if (rotation == null || previous == null || MC.options == null) {
            return rotation;
        }
        float sensitivity = (float) (MC.options.sensitivity().get() * 0.6D + 0.2D);
        float gcd = sensitivity * sensitivity * sensitivity * 1.2F;
        if (gcd <= 0.0F || !Float.isFinite(gcd)) {
            return rotation;
        }
        float yawDelta = rotation.getYaw() - previous.getYaw();
        float pitchDelta = rotation.getPitch() - previous.getPitch();
        return new Rot2f(
                previous.getYaw() + yawDelta - yawDelta % gcd,
                Mth.clamp(previous.getPitch() + pitchDelta - pitchDelta % gcd, -90.0F, 90.0F));
    }

    /** Quantizes a finished organic step to the exact mouse sensitivity divisor. */
    public static Rot2f patchConstantRotation(Rot2f rotation, Rot2f previous) {
        if (rotation == null || previous == null || MC.options == null) {
            return rotation;
        }
        double sensitivity = MC.options.sensitivity().get() * 0.6D + 0.2D;
        double divisor = sensitivity * sensitivity * sensitivity * 8.0D * 0.15D;
        if (divisor <= 0.0D || !Double.isFinite(divisor)) {
            return rotation;
        }
        float yaw = previous.getYaw()
                + (float) (Math.round((rotation.getYaw() - previous.getYaw()) / divisor) * divisor);
        float pitch = previous.getPitch()
                + (float) (Math.round((rotation.getPitch() - previous.getPitch()) / divisor) * divisor);
        return new Rot2f(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
    }

    public static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    public static double rotationDifference(float first, float second) {
        return Math.abs(Mth.wrapDegrees(first - second));
    }
}
