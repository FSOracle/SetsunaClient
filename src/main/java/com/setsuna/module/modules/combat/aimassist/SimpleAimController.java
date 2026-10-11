package com.setsuna.module.modules.combat.aimassist;

import net.minecraft.util.Mth;

/** Lightweight spring controller corresponding to Vape's Simple mode. */
public final class SimpleAimController implements AimAssistController {

    private float yawVelocity;
    private float pitchVelocity;

    @Override
    public RotationDelta update(Frame frame) {
        float yawError = Mth.wrapDegrees(frame.targetYaw() - frame.playerYaw());
        float pitchError = frame.aimVertically()
                ? Mth.wrapDegrees(frame.targetPitch() - frame.playerPitch())
                : 0.0F;

        float yawSpeed = normalizeSpeed(frame.horizontalSpeed());
        float pitchSpeed = normalizeSpeed(frame.verticalSpeed());
        float yawGain = lerp(yawSpeed, 3.5F, 11.0F);
        float pitchGain = lerp(pitchSpeed, 3.0F, 9.0F);
        float maximumYawRate = lerp(yawSpeed, 35.0F, 360.0F);
        float maximumPitchRate = lerp(pitchSpeed, 25.0F, 240.0F);

        if (frame.strafingAway()) {
            yawGain *= 1.35F;
            maximumYawRate *= 1.6F;
        }

        float desiredYawVelocity = Mth.clamp(yawError * yawGain, -maximumYawRate, maximumYawRate);
        float desiredPitchVelocity = Mth.clamp(pitchError * pitchGain, -maximumPitchRate, maximumPitchRate);
        float yawBlend = exponentialBlend(lerp(yawSpeed, 8.0F, 20.0F), frame.deltaTime());
        float pitchBlend = exponentialBlend(lerp(pitchSpeed, 7.0F, 18.0F), frame.deltaTime());
        yawVelocity = Mth.lerp(yawBlend, yawVelocity, desiredYawVelocity);
        pitchVelocity = Mth.lerp(pitchBlend, pitchVelocity, desiredPitchVelocity);

        if (Math.signum(yawVelocity) != Math.signum(yawError)) {
            yawVelocity *= 0.35F;
        }
        if (Math.signum(pitchVelocity) != Math.signum(pitchError)) {
            pitchVelocity *= 0.35F;
        }

        float yawDelta = limitToError(yawVelocity * frame.deltaTime(), yawError);
        float pitchDelta = frame.aimVertically()
                ? limitToError(pitchVelocity * frame.deltaTime(), pitchError)
                : 0.0F;
        return new RotationDelta(yawDelta, pitchDelta);
    }

    @Override
    public void reset() {
        yawVelocity = 0.0F;
        pitchVelocity = 0.0F;
    }

    static float normalizeSpeed(float speed) {
        return Mth.clamp((speed - 1.0F) / 9.0F, 0.0F, 1.0F);
    }

    static float exponentialBlend(float response, float deltaTime) {
        return 1.0F - (float) Math.exp(-response * deltaTime);
    }

    static float limitToError(float delta, float error) {
        if (error == 0.0F || Math.signum(delta) != Math.signum(error)) {
            return 0.0F;
        }
        return Math.copySign(Math.min(Math.abs(delta), Math.abs(error)), error);
    }

    private static float lerp(float factor, float start, float end) {
        return start + factor * (end - start);
    }
}
