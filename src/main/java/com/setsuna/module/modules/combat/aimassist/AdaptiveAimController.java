package com.setsuna.module.modules.combat.aimassist;

import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Motion-aware controller corresponding to Vape's Adaptive mode. */
public final class AdaptiveAimController implements AimAssistController {

    private boolean initialized;
    private Vec3 smoothedAimPoint = Vec3.ZERO;
    private Vec3 smoothedLead = Vec3.ZERO;
    private float yawVelocity;
    private float pitchVelocity;
    private float smoothedTargetYawRate;
    private float smoothedTargetPitchRate;
    private float lastTargetYaw;
    private float lastTargetPitch;
    private float lastPlayerYaw;
    private float lastPlayerPitch;
    private float pitchDriftPosition;
    private float pitchDriftVelocity;
    private float pitchDriftTarget;
    private long nextPitchDriftNanos;
    private long noiseStartNanos;

    @Override
    public RotationDelta update(Frame frame) {
        Vec3 relativeVelocity = frame.targetVelocity().subtract(frame.playerVelocity());
        double targetHorizontalSpeed = horizontalLength(frame.targetVelocity());
        double relativeHorizontalSpeed = horizontalLength(relativeVelocity);
        float frameScale = Mth.clamp(frame.deltaTime() * 60.0F, 0.45F, 2.4F);
        double aimSmoothing = Mth.clamp(
                0.28D + Math.min(0.35D, relativeHorizontalSpeed * 0.5D), 0.08D, 0.75D);
        double leadSmoothing = Mth.clamp(
                0.18D + Math.min(0.42D,
                        (targetHorizontalSpeed + relativeHorizontalSpeed * 0.25D) * 0.72D),
                0.12D, 0.68D);
        double aimBlend = 1.0D - Math.pow(1.0D - aimSmoothing, frameScale);
        double leadBlend = 1.0D - Math.pow(1.0D - leadSmoothing, frameScale);
        Vec3 targetLead = frame.targetVelocity().add(relativeVelocity.scale(0.08D));

        if (!initialized) {
            smoothedAimPoint = frame.aimPoint();
            smoothedLead = targetLead;
            lastTargetYaw = frame.targetYaw();
            lastTargetPitch = frame.targetPitch();
            lastPlayerYaw = frame.playerYaw();
            lastPlayerPitch = frame.playerPitch();
            noiseStartNanos = frame.nowNanos();
            initialized = true;
        } else {
            smoothedAimPoint = lerp(smoothedAimPoint, frame.aimPoint(), aimBlend);
            smoothedLead = lerp(smoothedLead, targetLead, leadBlend);
        }

        double maximumAimLag = 0.3D + targetHorizontalSpeed * 1.3D
                + relativeHorizontalSpeed * 0.4D;
        if (horizontalLength(frame.aimPoint().subtract(smoothedAimPoint)) > maximumAimLag) {
            smoothedAimPoint = frame.aimPoint();
            smoothedLead = targetLead;
        }

        double distanceLeadScale = Mth.clamp((frame.targetDistance() - 0.8D) / 2.5D, 0.0D, 1.0D);
        double leadAmount = 0.35D + frame.targetDistance() * 0.045D
                + targetHorizontalSpeed * 0.95D + Math.min(0.18D, relativeHorizontalSpeed * 0.12D);
        leadAmount = Mth.clamp(leadAmount, 0.1D, 1.05D) * (0.3D + 0.7D * distanceLeadScale);
        Vec3 adjustedAimPoint = smoothedAimPoint.add(smoothedLead.scale(Math.max(0.05D, leadAmount)));
        Rot2f targetRotation = RotationUtils.calculate(frame.eyePosition(), adjustedAimPoint);
        float targetYaw = targetRotation.getYaw();
        float targetPitch = targetRotation.getPitch();

        float targetYawRate = Mth.wrapDegrees(targetYaw - lastTargetYaw) / frame.deltaTime();
        float targetPitchRate = Mth.wrapDegrees(targetPitch - lastTargetPitch) / frame.deltaTime();
        float playerYawRate = Mth.wrapDegrees(frame.playerYaw() - lastPlayerYaw) / frame.deltaTime();
        float playerPitchRate = Mth.wrapDegrees(frame.playerPitch() - lastPlayerPitch) / frame.deltaTime();
        float rateBlend = Mth.clamp(frame.deltaTime() * 12.0F, 0.05F, 0.45F);
        smoothedTargetYawRate = Mth.lerp(rateBlend, smoothedTargetYawRate, targetYawRate);
        smoothedTargetPitchRate = Mth.lerp(rateBlend, smoothedTargetPitchRate, targetPitchRate);

        float yawError = Mth.wrapDegrees(targetYaw - frame.playerYaw());
        float pitchError = frame.aimVertically()
                ? Mth.wrapDegrees(targetPitch - frame.playerPitch())
                : 0.0F;
        float yawSpeed = SimpleAimController.normalizeSpeed(frame.horizontalSpeed());
        float pitchSpeed = SimpleAimController.normalizeSpeed(frame.verticalSpeed());
        float yawGain = lerp(yawSpeed, 2.5F, 9.0F);
        float pitchGain = lerp(pitchSpeed, 2.2F, 7.5F);
        float maximumYawRate = lerp(yawSpeed, 45.0F, 400.0F);
        float maximumPitchRate = lerp(pitchSpeed, 30.0F, 280.0F);

        float desiredYawVelocity = yawError * yawGain
                + smoothedTargetYawRate * lerp(yawSpeed, 0.45F, 0.9F)
                - playerYawRate * 0.12F;
        float desiredPitchVelocity = pitchError * pitchGain
                + smoothedTargetPitchRate * lerp(pitchSpeed, 0.4F, 0.82F)
                - playerPitchRate * 0.1F;
        if (frame.strafingAway()) {
            desiredYawVelocity *= 1.15F;
            maximumYawRate *= 1.15F;
        }

        float alignment = 1.0F - smoothStep(1.5F, 8.0F,
                (float) Math.hypot(yawError, pitchError));
        float elapsed = (frame.nowNanos() - noiseStartNanos) / 1.0E9F;
        float noiseScale = (0.15F + alignment * 0.85F) * (1.0F - yawSpeed * 0.5F);
        desiredYawVelocity += ((float) Math.sin(elapsed * 12.57F) * 0.8F
                + (float) Math.sin(elapsed * 7.85F) * 0.5F) * noiseScale;
        if (frame.aimVertically()) {
            desiredPitchVelocity += computePitchDrift(frame, alignment);
        } else {
            pitchVelocity = 0.0F;
        }

        desiredYawVelocity = Mth.clamp(desiredYawVelocity, -maximumYawRate, maximumYawRate);
        desiredPitchVelocity = Mth.clamp(desiredPitchVelocity, -maximumPitchRate, maximumPitchRate);
        float yawBlend = SimpleAimController.exponentialBlend(lerp(yawSpeed, 7.0F, 18.0F), frame.deltaTime());
        float pitchBlend = SimpleAimController.exponentialBlend(lerp(pitchSpeed, 6.0F, 16.0F), frame.deltaTime());
        yawVelocity = Mth.lerp(yawBlend, yawVelocity, desiredYawVelocity);
        pitchVelocity = Mth.lerp(pitchBlend, pitchVelocity, desiredPitchVelocity);

        float yawDelta = SimpleAimController.limitToError(yawVelocity * frame.deltaTime(), yawError);
        float pitchDelta = frame.aimVertically()
                ? SimpleAimController.limitToError(pitchVelocity * frame.deltaTime(), pitchError)
                : 0.0F;
        lastTargetYaw = targetYaw;
        lastTargetPitch = targetPitch;
        lastPlayerYaw = frame.playerYaw();
        lastPlayerPitch = frame.playerPitch();
        return new RotationDelta(yawDelta, pitchDelta);
    }

    private float computePitchDrift(Frame frame, float alignment) {
        if (nextPitchDriftNanos == 0L || frame.nowNanos() >= nextPitchDriftNanos) {
            float phase = (float) Math.sin(frame.nowNanos() * 1.0E-9D * 3.17D);
            pitchDriftTarget = phase * lerp(alignment, 0.05F, 0.15F);
            nextPitchDriftNanos = frame.nowNanos() + 500_000_000L;
        }
        float strength = lerp(alignment, 2.0F, 5.0F);
        pitchDriftVelocity += (pitchDriftTarget - pitchDriftPosition)
                * strength * frame.deltaTime();
        pitchDriftVelocity *= (float) Math.pow(0.04F, frame.deltaTime());
        pitchDriftPosition += pitchDriftVelocity * frame.deltaTime();
        float pitchLimitScale = 1.0F - 0.85F
                * smoothStep(72.0F, 88.0F, Math.abs(frame.playerPitch()));
        return Mth.clamp(pitchDriftVelocity * 0.25F - pitchDriftPosition * 1.4F,
                -0.55F * pitchLimitScale, 0.55F * pitchLimitScale);
    }

    @Override
    public void reset() {
        initialized = false;
        smoothedAimPoint = Vec3.ZERO;
        smoothedLead = Vec3.ZERO;
        yawVelocity = 0.0F;
        pitchVelocity = 0.0F;
        smoothedTargetYawRate = 0.0F;
        smoothedTargetPitchRate = 0.0F;
        lastTargetYaw = 0.0F;
        lastTargetPitch = 0.0F;
        lastPlayerYaw = 0.0F;
        lastPlayerPitch = 0.0F;
        pitchDriftPosition = 0.0F;
        pitchDriftVelocity = 0.0F;
        pitchDriftTarget = 0.0F;
        nextPitchDriftNanos = 0L;
        noiseStartNanos = 0L;
    }

    private static Vec3 lerp(Vec3 start, Vec3 end, double factor) {
        return start.add(end.subtract(start).scale(factor));
    }

    private static double horizontalLength(Vec3 vector) {
        return Math.hypot(vector.x, vector.z);
    }

    private static float smoothStep(float edgeStart, float edgeEnd, float value) {
        float normalized = Mth.clamp((value - edgeStart) / (edgeEnd - edgeStart), 0.0F, 1.0F);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static float lerp(float factor, float start, float end) {
        return start + factor * (end - start);
    }
}
