package Combat.Killaura.modules.combat;

import java.util.List;

public interface LegitAuraContext {
    boolean isAvailable();

    boolean isScreenOpen();

    boolean isSupportedWeaponHeld();

    boolean isInteractionAvailable();

    boolean wasJumpPressed();

    float playerYaw();

    float playerPitch();

    float attackCooldownProgress();

    LegitAuraCriticalState criticalState();

    List<LegitAuraTargetSnapshot> targets(double searchRange);

    void setPlayerRotation(float yaw, float pitch);

    boolean rotationHitsTarget(int entityId, double reach);

    void attackEntity(int entityId);

    void swingMainHand();
}
