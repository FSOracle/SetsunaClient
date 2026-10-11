package Combat.Killaura.modules.combat;

import Combat.Killaura.catalog.ModuleCategory;
import Combat.Killaura.core.ModuleBase;
import Combat.Killaura.core.Setting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

public final class LegitAuraModule extends ModuleBase {
    public static final double ATTACK_RANGE = 3.0;
    public static final float TARGET_ESP_STRENGTH = 0.65f;

    private final Setting<Double> rotationRange = this.addSetting(
            Setting.ranged("RotationRange", 5.0, 2.0, 8.0, Comparator.naturalOrder()));
    private final Setting<Integer> switchDelay = this.addSetting(
            Setting.ranged("SwitchDelay", 350, 0, 1200, Comparator.naturalOrder())
                    .withSuffix("ms"));
    private final Setting<Integer> fov = this.addSetting(
            Setting.ranged("FOV", 30, 1, 90, Comparator.naturalOrder()));
    private final Setting<Boolean> onlyWeapon = this.addSetting(Setting.of("OnlyWeapon", false));
    private final Setting<Boolean> players = this.addSetting(Setting.of("Players", true));
    private final Setting<Boolean> mobs = this.addSetting(Setting.of("Mobs", false));
    private final Setting<Boolean> animals = this.addSetting(Setting.of("Animals", false));
    private final Setting<Boolean> invisibles = this.addSetting(Setting.of("Invisibles", false));
    private final Setting<Boolean> ignoreTeam = this.addSetting(Setting.of("IgnoreTeam", true));
    private final Setting<Boolean> ignoreFriends = this.addSetting(Setting.of("IgnoreFriends", true));
    private final Setting<Boolean> targetEsp = this.addSetting(Setting.of("TargetESP", false));
    private final Setting<Boolean> smartCrit = this.addSetting(Setting.of("SmartCrit", false));
    private final LegitAuraContext context;
    private final LongSupplier clock;
    private final DoubleSupplier random;
    private final TargetSelection attackSelection = new TargetSelection();
    private final TargetSelection rotationSelection = new TargetSelection();
    private float yawVelocity;
    private float pitchVelocity;
    private LegitAuraAirPhase airPhase = LegitAuraAirPhase.GROUNDED;
    private long lastJumpMillis;
    private long lastAttackMillis;

    public LegitAuraModule(
            LegitAuraContext context,
            LongSupplier clock,
            DoubleSupplier random) {
        super("LegitAura", "Automatically attacks valid targets around you.", ModuleCategory.COMBAT);
        this.context = Objects.requireNonNull(context, "context");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public double rotationRange() { return this.rotationRange.value(); }
    public boolean setRotationRange(double value) { return this.rotationRange.set(value); }
    public int switchDelayMillis() { return this.switchDelay.value(); }
    public boolean setSwitchDelayMillis(int value) { return this.switchDelay.set(value); }
    public int fov() { return this.fov.value(); }
    public boolean setFov(int value) { return this.fov.set(value); }
    public boolean onlyWeapon() { return this.onlyWeapon.value(); }
    public boolean setOnlyWeapon(boolean value) { return this.onlyWeapon.set(value); }
    public boolean players() { return this.players.value(); }
    public boolean setPlayers(boolean value) { return this.players.set(value); }
    public boolean mobs() { return this.mobs.value(); }
    public boolean setMobs(boolean value) { return this.mobs.set(value); }
    public boolean animals() { return this.animals.value(); }
    public boolean setAnimals(boolean value) { return this.animals.set(value); }
    public boolean invisibles() { return this.invisibles.value(); }
    public boolean setInvisibles(boolean value) { return this.invisibles.set(value); }
    public boolean ignoreTeam() { return this.ignoreTeam.value(); }
    public boolean setIgnoreTeam(boolean value) { return this.ignoreTeam.set(value); }
    public boolean ignoreFriends() { return this.ignoreFriends.value(); }
    public boolean setIgnoreFriends(boolean value) { return this.ignoreFriends.set(value); }
    public boolean targetEsp() { return this.targetEsp.value(); }
    public boolean setTargetEsp(boolean value) { return this.targetEsp.set(value); }
    public boolean smartCrit() { return this.smartCrit.value(); }
    public boolean setSmartCrit(boolean value) { return this.smartCrit.set(value); }
    public Integer attackTargetEntityId() { return this.attackSelection.entityId; }
    public Integer rotationTargetEntityId() { return this.rotationSelection.entityId; }
    public Integer targetEspEntityId() {
        return this.isEnabled() && this.targetEsp() ? this.rotationSelection.entityId : null;
    }
    public float targetEspStrength() { return TARGET_ESP_STRENGTH; }
    public LegitAuraAirPhase airPhase() { return this.airPhase; }
    public long lastJumpMillis() { return this.lastJumpMillis; }
    public long lastAttackMillis() { return this.lastAttackMillis; }

    public LegitAuraAction tick() {
        if (!this.isEnabled()
                || !this.context.isAvailable()
                || this.context.isScreenOpen()
                || this.onlyWeapon() && !this.context.isSupportedWeaponHeld()) {
            this.resetTargeting();
            return LegitAuraAction.NONE;
        }

        long now = this.clock.getAsLong();
        if (this.context.wasJumpPressed()) {
            this.lastJumpMillis = now;
        }
        LegitAuraCriticalState criticalState =
                Objects.requireNonNull(this.context.criticalState(), "criticalState");
        this.updateAirPhase(criticalState);

        List<LegitAuraTargetSnapshot> eligible = new ArrayList<>();
        for (LegitAuraTargetSnapshot target : this.context.targets(this.rotationRange() + 1.0)) {
            if (this.isEligible(target, this.rotationRange())) {
                eligible.add(target);
            }
        }
        List<LegitAuraTargetSnapshot> attackCandidates = eligible.stream()
                .filter(target -> target.closestDistance() <= ATTACK_RANGE)
                .toList();
        LegitAuraTargetSnapshot attackTarget = this.selectTarget(
                attackCandidates, this.attackSelection, false, now);

        LegitAuraTargetSnapshot rotationTarget;
        if (attackTarget != null) {
            this.rotationSelection.entityId = attackTarget.entityId();
            this.rotationSelection.notBeforeMillis = 0L;
            rotationTarget = attackTarget;
        } else {
            rotationTarget = this.selectTarget(eligible, this.rotationSelection, true, now);
        }

        boolean rotated = rotationTarget != null && this.rotateToward(rotationTarget);
        if (attackTarget == null
                || attackTarget.closestDistance() > ATTACK_RANGE
                || this.context.attackCooldownProgress() <= 0.9f
                || this.shouldWaitForCritical(criticalState)
                || !this.context.rotationHitsTarget(attackTarget.entityId(), ATTACK_RANGE)
                || !this.context.isInteractionAvailable()) {
            return rotated ? LegitAuraAction.ROTATED : LegitAuraAction.NONE;
        }

        this.context.attackEntity(attackTarget.entityId());
        this.context.swingMainHand();
        this.lastAttackMillis = now;
        if (!criticalState.onGround()) {
            this.airPhase = LegitAuraAirPhase.ATTACKED_AIRBORNE;
        }
        return LegitAuraAction.ATTACK;
    }

    public boolean isEligible(LegitAuraTargetSnapshot target, double range) {
        if (target == null
                || target.localPlayer()
                || !target.alive()
                || target.removed()
                || !target.visible()
                || !this.invisibles() && target.invisible()
                || target.closestDistance() > range
                || Math.abs(wrapDegrees(target.targetYaw() - this.context.playerYaw())) > this.fov()) {
            return false;
        }
        return switch (target.type()) {
            case PLAYER -> this.players()
                    && !target.antiBot()
                    && (!this.ignoreTeam() || !target.teammate())
                    && (!this.ignoreFriends() || !target.friend());
            case ANIMAL -> this.animals();
            case HOSTILE_MOB, OTHER_MOB -> this.mobs();
            case OTHER -> false;
        };
    }

    public boolean shouldWaitForCritical(LegitAuraCriticalState state) {
        if (!this.smartCrit()
                || state == null
                || !state.supportsAirCritical()
                || state.onGround()
                || state.verticalVelocity() < -0.08
                || state.attackCooldownPeriodTicks() <= 0.0f
                || state.isCriticalNow(this.context.attackCooldownProgress())) {
            return false;
        }
        float period = state.attackCooldownPeriodTicks();
        float cooldown = this.context.attackCooldownProgress();
        float cooldownLead = Math.max(0.0f, period * 0.9f - cooldown * period);
        float verticalLead = (float)(state.verticalVelocity() / 0.08);
        float lead = Math.max(cooldownLead, verticalLead);
        float normalized = (lead + 0.5f) / period;
        float predictedWindow = Math.min(1.0f, 0.2f + normalized * normalized * 0.8f);
        return predictedWindow < 0.375f;
    }

    public static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    @Override
    protected void onEnable() {
        this.resetState();
    }

    @Override
    protected void onDisable() {
        this.resetState();
    }

    private LegitAuraTargetSnapshot selectTarget(
            List<LegitAuraTargetSnapshot> candidates,
            TargetSelection selection,
            boolean rotation,
            long now) {
        if (candidates.isEmpty()) {
            selection.entityId = null;
            return null;
        }
        LegitAuraTargetSnapshot current = find(candidates, selection.entityId);
        if (current != null) {
            selection.notBeforeMillis = 0L;
            return current;
        }
        if (now < selection.notBeforeMillis) {
            selection.entityId = null;
            return null;
        }
        if (selection.entityId != null && this.switchDelayMillis() > 0) {
            selection.entityId = null;
            selection.notBeforeMillis = now + this.switchDelayMillis();
            return null;
        }
        LegitAuraTargetSnapshot best = candidates.stream()
                .min(Comparator.comparingDouble(target -> this.score(target, rotation)))
                .orElse(null);
        selection.entityId = best == null ? null : best.entityId();
        selection.notBeforeMillis = 0L;
        return best;
    }

    private double score(LegitAuraTargetSnapshot target, boolean rotation) {
        double angle = Math.hypot(
                wrapDegrees(target.targetYaw() - this.context.playerYaw()),
                target.targetPitch() - this.context.playerPitch());
        double score = rotation
                ? target.distanceSquared() + angle * angle * 0.015
                : target.health() + target.distanceSquared() * 0.35 + angle * 0.12;
        return target.type() == LegitAuraTargetType.PLAYER ? score : score * 2.0;
    }

    private boolean rotateToward(LegitAuraTargetSnapshot target) {
        float currentYaw = this.context.playerYaw();
        float currentPitch = this.context.playerPitch();
        float yawError = wrapDegrees(target.targetYaw() - currentYaw);
        float pitchError = target.targetPitch() - currentPitch;
        float desiredYawStep = clamp(yawError * 0.32f, -6.0f, 6.0f);
        float desiredPitchStep = clamp(pitchError * 0.28f, -4.5f, 4.5f);
        this.yawVelocity = approach(this.yawVelocity, desiredYawStep, 1.35f);
        this.pitchVelocity = approach(this.pitchVelocity, desiredPitchStep, 0.9f);
        if (Math.abs(yawError) < 1.2f) {
            this.yawVelocity *= 0.72f;
        }
        if (Math.abs(pitchError) < 1.0f) {
            this.pitchVelocity *= 0.68f;
        }
        float yawNoise = (float)((this.nextRandom() - 0.5) * Math.min(0.08, Math.abs(yawError) * 0.015));
        float pitchNoise = (float)((this.nextRandom() - 0.5) * Math.min(0.05, Math.abs(pitchError) * 0.012));
        float yawStep = clamp(this.yawVelocity + yawNoise, -Math.abs(yawError), Math.abs(yawError));
        float pitchStep = clamp(this.pitchVelocity + pitchNoise, -Math.abs(pitchError), Math.abs(pitchError));
        if (Math.signum(yawStep) != Math.signum(yawError)) {
            yawStep = 0.0f;
        }
        if (Math.signum(pitchStep) != Math.signum(pitchError)) {
            pitchStep = 0.0f;
        }
        this.context.setPlayerRotation(
                currentYaw + yawStep,
                clamp(currentPitch + pitchStep, -90.0f, 90.0f));
        return yawStep != 0.0f || pitchStep != 0.0f;
    }

    private void updateAirPhase(LegitAuraCriticalState state) {
        if (state.onGround()) {
            this.airPhase = LegitAuraAirPhase.GROUNDED;
        } else if (this.airPhase != LegitAuraAirPhase.ATTACKED_AIRBORNE) {
            this.airPhase = state.verticalVelocity() > 0.0
                    ? LegitAuraAirPhase.ASCENDING
                    : LegitAuraAirPhase.DESCENDING;
        }
    }

    private void resetTargeting() {
        this.attackSelection.reset();
        this.rotationSelection.reset();
        this.yawVelocity = 0.0f;
        this.pitchVelocity = 0.0f;
    }

    private void resetState() {
        this.resetTargeting();
        this.airPhase = LegitAuraAirPhase.GROUNDED;
        this.lastJumpMillis = 0L;
        this.lastAttackMillis = 0L;
    }

    private double nextRandom() {
        double value = this.random.getAsDouble();
        if (!Double.isFinite(value) || value < 0.0 || value >= 1.0) {
            throw new IllegalStateException("random supplier must return a finite value in [0, 1)");
        }
        return value;
    }

    private static LegitAuraTargetSnapshot find(
            List<LegitAuraTargetSnapshot> candidates, Integer entityId) {
        if (entityId == null) {
            return null;
        }
        for (LegitAuraTargetSnapshot candidate : candidates) {
            if (candidate.entityId() == entityId) {
                return candidate;
            }
        }
        return null;
    }

    private static float approach(float current, float target, float maximumChange) {
        return current + clamp(target - current, -maximumChange, maximumChange);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class TargetSelection {
        private Integer entityId;
        private long notBeforeMillis;

        private void reset() {
            this.entityId = null;
            this.notBeforeMillis = 0L;
        }
    }
}
