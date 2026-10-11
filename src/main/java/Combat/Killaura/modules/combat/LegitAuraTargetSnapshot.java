package Combat.Killaura.modules.combat;

import java.util.Objects;

public record LegitAuraTargetSnapshot(
        int entityId,
        LegitAuraTargetType type,
        boolean localPlayer,
        boolean alive,
        boolean removed,
        boolean invisible,
        boolean visible,
        boolean friend,
        boolean teammate,
        boolean antiBot,
        boolean onGround,
        double closestDistance,
        double distanceSquared,
        float health,
        float targetYaw,
        float targetPitch) {
    public LegitAuraTargetSnapshot {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(closestDistance) || closestDistance < 0.0) {
            throw new IllegalArgumentException("closestDistance must be finite and non-negative");
        }
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
            throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
        }
        if (!Float.isFinite(health) || health < 0.0f) {
            throw new IllegalArgumentException("health must be finite and non-negative");
        }
        if (!Float.isFinite(targetYaw) || !Float.isFinite(targetPitch)) {
            throw new IllegalArgumentException("target rotation must be finite");
        }
    }
}
