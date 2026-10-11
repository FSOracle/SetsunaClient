package Combat.Killaura.modules.combat;

import Combat.Killaura.catalog.ModuleCategory;
import Combat.Killaura.core.ModuleBase;
import Combat.Killaura.core.Setting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class KillAuraModule extends ModuleBase {
    private final Setting<KillAuraMode> mode =
            this.addSetting(Setting.of("Mode", KillAuraMode.SWITCH));
    private final Setting<KillAuraPriority> priority =
            this.addSetting(Setting.of("Priority", KillAuraPriority.HEALTH));
    private final Setting<KillAuraRotationMode> rotationMode =
            this.addSetting(Setting.of("RotationMode", KillAuraRotationMode.SNAP));
    private final Setting<KillAuraCriticalMode> criticalMode =
            this.addSetting(Setting.of("Critical", KillAuraCriticalMode.NONE));
    private final Setting<Double> rotationRange = this.addSetting(
            Setting.ranged("RotationRange", 5.0, 2.0, 8.0, Comparator.naturalOrder()));
    private final Setting<Integer> rotationSpeed = this.addSetting(
            Setting.ranged("RotationSpeed", 90, 5, 180, Comparator.naturalOrder())
                    .visibleWhen(() -> this.rotationMode() == KillAuraRotationMode.SMOOTH));
    private final Setting<Double> range = this.addSetting(
            Setting.ranged("Range", 3.0, 2.0, 6.0, Comparator.naturalOrder()));
    private final Setting<Integer> fov = this.addSetting(
            Setting.ranged("FOV", 180, 1, 180, Comparator.naturalOrder()));
    private final Setting<Boolean> onlyWeapon = this.addSetting(Setting.of("OnlyWeapon", false));
    private final Setting<Boolean> swing = this.addSetting(Setting.of("Swing", true));
    private final Setting<Boolean> fakeBlock = this.addSetting(Setting.of("FakeBlock", false));
    private final Setting<Boolean> players = this.addSetting(Setting.of("Players", true));
    private final Setting<Boolean> mobs = this.addSetting(Setting.of("Mobs", false));
    private final Setting<Boolean> animals = this.addSetting(Setting.of("Animals", false));
    private final Setting<Boolean> invisibles = this.addSetting(Setting.of("Invisibles", false));
    private final Setting<Boolean> ignoreTeam = this.addSetting(Setting.of("IgnoreTeam", true));
    private final Setting<Boolean> ignoreFriends = this.addSetting(Setting.of("IgnoreFriends", true));
    private final Setting<Boolean> targetEsp = this.addSetting(Setting.of("TargetESP", false));
    private final Setting<Float> targetEspRadius = this.addSetting(
            Setting.ranged("TargetESPRadius", 0.75f, 0.1f, 2.0f, Comparator.naturalOrder())
                    .visibleWhen(this::targetEsp));
    private final Setting<Float> targetEspAlpha = this.addSetting(
            Setting.ranged("TargetESPAlpha", 1.0f, 0.0f, 2.0f, Comparator.naturalOrder())
                    .visibleWhen(this::targetEsp));
    private final Setting<Color> targetEspSideColor = this.addSetting(
            Setting.of("TargetESPSideColor", new Color(255, 255, 255, 255))
                    .visibleWhen(this::targetEsp));
    private final Setting<Color> targetEspLineColor = this.addSetting(
            Setting.of("TargetESPLineColor", new Color(255, 255, 255, 233))
                    .visibleWhen(this::targetEsp));
    private final KillAuraContext context;
    private Integer attackTargetEntityId;
    private Integer rotationTargetEntityId;
    private int switchIndex;
    private boolean fakeBlocking;

    public KillAuraModule(KillAuraContext context) {
        super("KillAura", "Automatically attacks valid targets around you.", ModuleCategory.COMBAT);
        this.context = Objects.requireNonNull(context, "context");
    }

    public KillAuraMode mode() { return this.mode.value(); }
    public boolean setMode(KillAuraMode value) { return this.mode.set(value); }
    public KillAuraPriority priority() { return this.priority.value(); }
    public boolean setPriority(KillAuraPriority value) { return this.priority.set(value); }
    public KillAuraRotationMode rotationMode() { return this.rotationMode.value(); }
    public boolean setRotationMode(KillAuraRotationMode value) { return this.rotationMode.set(value); }
    public KillAuraCriticalMode criticalMode() { return this.criticalMode.value(); }
    public boolean setCriticalMode(KillAuraCriticalMode value) { return this.criticalMode.set(value); }
    public double rotationRange() { return this.rotationRange.value(); }
    public boolean setRotationRange(double value) { return this.rotationRange.set(value); }
    public int rotationSpeed() { return this.rotationSpeed.value(); }
    public boolean setRotationSpeed(int value) { return this.rotationSpeed.set(value); }
    public double range() { return this.range.value(); }
    public boolean setRange(double value) { return this.range.set(value); }
    public int fov() { return this.fov.value(); }
    public boolean setFov(int value) { return this.fov.set(value); }
    public boolean onlyWeapon() { return this.onlyWeapon.value(); }
    public boolean setOnlyWeapon(boolean value) { return this.onlyWeapon.set(value); }
    public boolean swing() { return this.swing.value(); }
    public boolean setSwing(boolean value) { return this.swing.set(value); }
    public boolean fakeBlock() { return this.fakeBlock.value(); }
    public boolean setFakeBlock(boolean value) { return this.fakeBlock.set(value); }
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
    public float targetEspRadius() { return this.targetEspRadius.value(); }
    public boolean setTargetEspRadius(float value) { return this.targetEspRadius.set(value); }
    public float targetEspAlpha() { return this.targetEspAlpha.value(); }
    public boolean setTargetEspAlpha(float value) { return this.targetEspAlpha.set(value); }
    public Color targetEspSideColor() { return this.targetEspSideColor.value(); }
    public boolean setTargetEspSideColor(Color value) { return this.targetEspSideColor.set(value); }
    public Color targetEspLineColor() { return this.targetEspLineColor.value(); }
    public boolean setTargetEspLineColor(Color value) { return this.targetEspLineColor.set(value); }
    public Integer attackTargetEntityId() { return this.attackTargetEntityId; }
    public Integer rotationTargetEntityId() { return this.rotationTargetEntityId; }
    public boolean isFakeBlocking() { return this.isEnabled() && this.fakeBlocking; }
    public Integer targetEspEntityId() {
        return this.isEnabled() && this.targetEsp() ? this.attackTargetEntityId : null;
    }
    public KillAuraTargetEspStyle targetEspStyle() {
        return new KillAuraTargetEspStyle(this.targetEspRadius(), this.targetEspAlpha(),
                this.targetEspSideColor(), this.targetEspLineColor());
    }

    public KillAuraAction tick() {
        if (!this.isEnabled()
                || !this.context.isAvailable()
                || this.context.isScreenOpen()
                || this.onlyWeapon() && !this.context.isSupportedWeaponHeld()) {
            this.resetState();
            return KillAuraAction.NONE;
        }

        double searchRange = Math.max(this.range(), this.rotationRange()) + 1.0;
        List<KillAuraTargetSnapshot> allTargets = this.context.targets(searchRange);
        List<KillAuraTargetSnapshot> attackCandidates = this.eligibleTargets(allTargets, this.range());
        attackCandidates.sort(Comparator.comparingDouble(this::priorityScore));
        KillAuraTargetSnapshot attackTarget = this.selectAttackTarget(attackCandidates);
        this.attackTargetEntityId = attackTarget == null ? null : attackTarget.entityId();

        KillAuraTargetSnapshot rotationTarget;
        if (attackTarget != null) {
            rotationTarget = attackTarget;
        } else {
            List<KillAuraTargetSnapshot> rotationCandidates =
                    this.eligibleTargets(allTargets, this.rotationRange());
            rotationCandidates.sort(Comparator.comparingDouble(this::rotationScore));
            rotationTarget = retainOrFirst(rotationCandidates, this.rotationTargetEntityId);
        }
        this.rotationTargetEntityId = rotationTarget == null ? null : rotationTarget.entityId();
        boolean rotated = false;
        if (rotationTarget != null) {
            float speed = this.rotationMode() == KillAuraRotationMode.SNAP
                    ? 180.0f
                    : this.rotationSpeed();
            this.context.requestRotation(rotationTarget.targetYaw(), rotationTarget.targetPitch(), speed);
            rotated = true;
        }

        this.fakeBlocking = this.fakeBlock()
                && attackTarget != null
                && this.context.isSwordHeld();
        if (attackTarget == null
                || this.context.attackCooldownProgress() <= 0.9f
                || !this.criticalAllowsAttack()
                || !this.context.rotationRayHitsTarget(attackTarget.entityId(), this.range())
                || !this.context.isInteractionAvailable()) {
            return rotated ? KillAuraAction.ROTATED : KillAuraAction.NONE;
        }

        this.context.attackEntity(attackTarget.entityId());
        if (this.swing()) {
            this.context.swingMainHand();
        }
        if (this.mode() == KillAuraMode.SWITCH && attackCandidates.size() > 1) {
            this.switchIndex = (this.switchIndex + 1) % attackCandidates.size();
        }
        return KillAuraAction.ATTACK;
    }

    public boolean isEligible(KillAuraTargetSnapshot target, double maximumRange) {
        if (target == null
                || target.localPlayer()
                || !target.alive()
                || target.removed()
                || !target.visible()
                || !this.invisibles() && target.invisible()
                || target.closestDistance() > maximumRange
                || Math.abs(LegitAuraModule.wrapDegrees(
                        target.targetYaw() - this.context.playerYaw())) > this.fov()) {
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

    public boolean shouldWaitForSmartCritical(LegitAuraCriticalState state) {
        if (state == null
                || !state.supportsAirCritical()
                || state.onGround()
                || state.verticalVelocity() < -0.08
                || state.attackCooldownPeriodTicks() <= 0.0f
                || state.isCriticalNow(this.context.attackCooldownProgress())) {
            return false;
        }
        float period = state.attackCooldownPeriodTicks();
        float cooldownLead = Math.max(
                0.0f, period * 0.9f - this.context.attackCooldownProgress() * period);
        float verticalLead = (float)(state.verticalVelocity() / 0.08);
        float lead = Math.max(cooldownLead, verticalLead);
        float normalized = (lead + 0.5f) / period;
        return Math.min(1.0f, 0.2f + normalized * normalized * 0.8f) < 0.375f;
    }

    @Override
    protected String hudSuffix() {
        return switch (this.mode()) {
            case SINGLE -> "Single";
            case SWITCH -> "Switch";
        };
    }

    @Override
    protected void onEnable() {
        this.resetState();
    }

    @Override
    protected void onDisable() {
        this.resetState();
    }

    @Override
    protected void onSettingChanged(Setting<?> setting, Object oldValue, Object newValue) {
        if (setting == this.mode || setting == this.priority) {
            this.switchIndex = 0;
            this.attackTargetEntityId = null;
        }
        if (setting == this.fakeBlock && !this.fakeBlock()) {
            this.fakeBlocking = false;
        }
    }

    private List<KillAuraTargetSnapshot> eligibleTargets(
            List<KillAuraTargetSnapshot> targets, double maximumRange) {
        List<KillAuraTargetSnapshot> result = new ArrayList<>();
        for (KillAuraTargetSnapshot target : targets) {
            if (this.isEligible(target, maximumRange)) {
                result.add(target);
            }
        }
        return result;
    }

    private KillAuraTargetSnapshot selectAttackTarget(List<KillAuraTargetSnapshot> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        KillAuraTargetSnapshot current = find(candidates, this.attackTargetEntityId);
        if (this.mode() == KillAuraMode.SINGLE && current != null) {
            return current;
        }
        if (this.mode() != KillAuraMode.SWITCH || candidates.size() <= 1) {
            return candidates.get(0);
        }
        this.switchIndex %= candidates.size();
        return candidates.get(this.switchIndex);
    }

    private boolean criticalAllowsAttack() {
        LegitAuraCriticalState state = Objects.requireNonNull(
                this.context.criticalState(), "criticalState");
        return switch (this.criticalMode()) {
            case NONE -> true;
            case CRIT_ONLY -> state.isCriticalNow(this.context.attackCooldownProgress());
            case SMART -> !this.shouldWaitForSmartCritical(state);
        };
    }

    private double priorityScore(KillAuraTargetSnapshot target) {
        double score = switch (this.priority()) {
            case HEALTH -> target.health();
            case DISTANCE -> target.distanceSquared();
            case ANGLE -> angularDistanceSquared(target);
        };
        return target.type() == KillAuraTargetType.PLAYER ? score : score * 2.0;
    }

    private double rotationScore(KillAuraTargetSnapshot target) {
        double score = target.distanceSquared();
        return target.type() == KillAuraTargetType.PLAYER ? score : score * 2.0;
    }

    private double angularDistanceSquared(KillAuraTargetSnapshot target) {
        float yaw = LegitAuraModule.wrapDegrees(target.targetYaw() - this.context.playerYaw());
        float pitch = LegitAuraModule.wrapDegrees(target.targetPitch() - this.context.playerPitch());
        return yaw * yaw + pitch * pitch;
    }

    private void resetState() {
        this.attackTargetEntityId = null;
        this.rotationTargetEntityId = null;
        this.switchIndex = 0;
        this.fakeBlocking = false;
    }

    private static KillAuraTargetSnapshot retainOrFirst(
            List<KillAuraTargetSnapshot> candidates, Integer currentEntityId) {
        KillAuraTargetSnapshot current = find(candidates, currentEntityId);
        return current != null ? current : candidates.isEmpty() ? null : candidates.get(0);
    }

    private static KillAuraTargetSnapshot find(
            List<KillAuraTargetSnapshot> candidates, Integer entityId) {
        if (entityId == null) {
            return null;
        }
        for (KillAuraTargetSnapshot candidate : candidates) {
            if (candidate.entityId() == entityId) {
                return candidate;
            }
        }
        return null;
    }
}
