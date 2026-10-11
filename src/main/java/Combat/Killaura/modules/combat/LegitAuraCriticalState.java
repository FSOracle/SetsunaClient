package Combat.Killaura.modules.combat;

public record LegitAuraCriticalState(
        double verticalVelocity,
        float fallDistance,
        boolean onGround,
        boolean touchingWater,
        boolean inLava,
        boolean climbing,
        boolean blindness,
        boolean hasVehicle,
        boolean flying,
        boolean sprinting,
        float attackCooldownPeriodTicks) {
    public LegitAuraCriticalState {
        if (!Double.isFinite(verticalVelocity)) {
            throw new IllegalArgumentException("verticalVelocity must be finite");
        }
        if (!Float.isFinite(fallDistance) || fallDistance < 0.0f) {
            throw new IllegalArgumentException("fallDistance must be finite and non-negative");
        }
        if (!Float.isFinite(attackCooldownPeriodTicks)) {
            throw new IllegalArgumentException("attackCooldownPeriodTicks must be finite");
        }
    }

    public boolean supportsAirCritical() {
        return !this.touchingWater
                && !this.inLava
                && !this.climbing
                && !this.blindness
                && !this.hasVehicle
                && !this.flying;
    }

    public boolean isCriticalNow(float cooldownProgress) {
        return this.supportsAirCritical()
                && !this.onGround
                && this.fallDistance > 0.0f
                && cooldownProgress > 0.9f
                && !this.sprinting;
    }
}
