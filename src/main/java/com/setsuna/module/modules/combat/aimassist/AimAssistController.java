package com.setsuna.module.modules.combat.aimassist;

import net.minecraft.world.phys.Vec3;

/** Per-frame visible-rotation strategy used by AimAssist. */
public interface AimAssistController {

    RotationDelta update(Frame frame);

    void reset();

    record Frame(
            Vec3 eyePosition,
            Vec3 aimPoint,
            Vec3 targetVelocity,
            Vec3 playerVelocity,
            float playerYaw,
            float playerPitch,
            float targetYaw,
            float targetPitch,
            float horizontalSpeed,
            float verticalSpeed,
            boolean aimVertically,
            boolean strafingAway,
            double targetDistance,
            float deltaTime,
            long nowNanos
    ) {
    }

    record RotationDelta(float yaw, float pitch) {

        public static final RotationDelta ZERO = new RotationDelta(0.0F, 0.0F);
    }
}
