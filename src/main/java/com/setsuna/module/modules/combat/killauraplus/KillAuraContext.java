package com.setsuna.module.modules.combat.killauraplus;

import java.util.List;

public interface KillAuraContext {
    boolean isAvailable();

    boolean isScreenOpen();

    default boolean isUseInputActive() {
        return false;
    }

    boolean isSupportedWeaponHeld();

    /** Returns whether the held weapon uses the normal entity-attack protocol. */
    default boolean isNormalAttackSupported() {
        return true;
    }

    boolean isSwordHeld();

    boolean isInteractionAvailable();

    float playerYaw();

    float playerPitch();

    float attackCooldownProgress();

    LegitAuraCriticalState criticalState();

    List<KillAuraTargetSnapshot> targets(double searchRange);

    void requestRotation(float yaw, float pitch, float speed);

    boolean rotationRayHitsTarget(int entityId, double reach);

    void attackEntity(int entityId);

    void swingMainHand();
}
