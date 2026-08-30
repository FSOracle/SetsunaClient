package com.setsuna.module.modules.combat.killauraplus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Exact Clap target selection, rotation, critical and attack state machine. */
public final class KillAuraPlusEngine {
    private final KillAuraContext context;
    private Integer attackTargetEntityId;
    private Integer rotationTargetEntityId;
    private int switchIndex;
    private boolean fakeBlocking;

    public KillAuraPlusEngine(KillAuraContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public KillAuraAction tick(KillAuraConfig config, boolean enabled) {
        Objects.requireNonNull(config, "config");
        if (!enabled
                || !context.isAvailable()
                || context.isScreenOpen()
                || config.onlyWeapon() && !context.isSupportedWeaponHeld()) {
            reset();
            return KillAuraAction.NONE;
        }

        double searchRange = Math.max(config.range(), config.rotationRange()) + 1.0;
        List<KillAuraTargetSnapshot> allTargets = context.targets(searchRange);
        List<KillAuraTargetSnapshot> attackCandidates = eligibleTargets(allTargets, config.range(), config);
        attackCandidates.sort(Comparator.comparingDouble(target -> priorityScore(target, config)));
        KillAuraTargetSnapshot attackTarget = selectAttackTarget(attackCandidates, config.mode());
        attackTargetEntityId = attackTarget == null ? null : attackTarget.entityId();

        KillAuraTargetSnapshot rotationTarget;
        if (attackTarget != null) {
            rotationTarget = attackTarget;
        } else {
            List<KillAuraTargetSnapshot> rotationCandidates =
                    eligibleTargets(allTargets, config.rotationRange(), config);
            rotationCandidates.sort(Comparator.comparingDouble(this::rotationScore));
            rotationTarget = retainOrFirst(rotationCandidates, rotationTargetEntityId);
        }
        rotationTargetEntityId = rotationTarget == null ? null : rotationTarget.entityId();

        boolean rotated = false;
        if (rotationTarget != null) {
            float speed = config.rotationMode() == KillAuraRotationMode.SNAP
                    ? 180.0f
                    : config.rotationSpeed();
            context.requestRotation(rotationTarget.targetYaw(), rotationTarget.targetPitch(), speed);
            rotated = true;
        }

        fakeBlocking = config.fakeBlock() && attackTarget != null && context.isSwordHeld();
        if (attackTarget == null
                || context.attackCooldownProgress() <= 0.9f
                || !criticalAllowsAttack(config.criticalMode())
                || !context.rotationRayHitsTarget(attackTarget.entityId(), config.range())
                || !context.isInteractionAvailable()) {
            return rotated ? KillAuraAction.ROTATED : KillAuraAction.NONE;
        }

        context.attackEntity(attackTarget.entityId());
        if (config.swing()) {
            context.swingMainHand();
        }
        if (config.mode() == KillAuraMode.SWITCH && attackCandidates.size() > 1) {
            switchIndex = (switchIndex + 1) % attackCandidates.size();
        }
        return KillAuraAction.ATTACK;
    }

    public boolean isEligible(
            KillAuraTargetSnapshot target,
            double maximumRange,
            KillAuraConfig config) {
        if (target == null
                || target.localPlayer()
                || !target.alive()
                || target.removed()
                || !target.visible()
                || !config.invisibles() && target.invisible()
                || target.closestDistance() > maximumRange
                || Math.abs(wrapDegrees(target.targetYaw() - context.playerYaw())) > config.fov()) {
            return false;
        }
        return switch (target.type()) {
            case PLAYER -> config.players()
                    && !target.antiBot()
                    && (!config.ignoreTeam() || !target.teammate())
                    && (!config.ignoreFriends() || !target.friend());
            case ANIMAL -> config.animals();
            case HOSTILE_MOB, OTHER_MOB -> config.mobs();
            case OTHER -> false;
        };
    }

    public boolean shouldWaitForSmartCritical(LegitAuraCriticalState state) {
        if (state == null
                || !state.supportsAirCritical()
                || state.onGround()
                || state.verticalVelocity() < -0.08
                || state.attackCooldownPeriodTicks() <= 0.0f
                || state.isCriticalNow(context.attackCooldownProgress())) {
            return false;
        }
        float period = state.attackCooldownPeriodTicks();
        float cooldownLead = Math.max(
                0.0f, period * 0.9f - context.attackCooldownProgress() * period);
        float verticalLead = (float) (state.verticalVelocity() / 0.08);
        float lead = Math.max(cooldownLead, verticalLead);
        float normalized = (lead + 0.5f) / period;
        return Math.min(1.0f, 0.2f + normalized * normalized * 0.8f) < 0.375f;
    }

    public Integer attackTargetEntityId() {
        return attackTargetEntityId;
    }

    public Integer rotationTargetEntityId() {
        return rotationTargetEntityId;
    }

    public boolean isFakeBlocking() {
        return fakeBlocking;
    }

    public void resetAttackSelection() {
        switchIndex = 0;
        attackTargetEntityId = null;
    }

    public void clearFakeBlocking() {
        fakeBlocking = false;
    }

    public void reset() {
        attackTargetEntityId = null;
        rotationTargetEntityId = null;
        switchIndex = 0;
        fakeBlocking = false;
    }

    private List<KillAuraTargetSnapshot> eligibleTargets(
            List<KillAuraTargetSnapshot> targets,
            double maximumRange,
            KillAuraConfig config) {
        List<KillAuraTargetSnapshot> result = new ArrayList<>();
        for (KillAuraTargetSnapshot target : targets) {
            if (isEligible(target, maximumRange, config)) {
                result.add(target);
            }
        }
        return result;
    }

    private KillAuraTargetSnapshot selectAttackTarget(
            List<KillAuraTargetSnapshot> candidates,
            KillAuraMode mode) {
        if (candidates.isEmpty()) return null;
        KillAuraTargetSnapshot current = find(candidates, attackTargetEntityId);
        if (mode == KillAuraMode.SINGLE && current != null) return current;
        if (mode != KillAuraMode.SWITCH || candidates.size() <= 1) return candidates.getFirst();
        switchIndex %= candidates.size();
        return candidates.get(switchIndex);
    }

    private boolean criticalAllowsAttack(KillAuraCriticalMode mode) {
        LegitAuraCriticalState state = Objects.requireNonNull(context.criticalState(), "criticalState");
        return switch (mode) {
            case NONE -> true;
            case CRIT_ONLY -> state.isCriticalNow(context.attackCooldownProgress());
            case SMART -> !shouldWaitForSmartCritical(state);
        };
    }

    private double priorityScore(KillAuraTargetSnapshot target, KillAuraConfig config) {
        double score = switch (config.priority()) {
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
        float yaw = wrapDegrees(target.targetYaw() - context.playerYaw());
        float pitch = wrapDegrees(target.targetPitch() - context.playerPitch());
        return yaw * yaw + pitch * pitch;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private static KillAuraTargetSnapshot retainOrFirst(
            List<KillAuraTargetSnapshot> candidates,
            Integer currentEntityId) {
        KillAuraTargetSnapshot current = find(candidates, currentEntityId);
        return current != null ? current : candidates.isEmpty() ? null : candidates.getFirst();
    }

    private static KillAuraTargetSnapshot find(
            List<KillAuraTargetSnapshot> candidates,
            Integer entityId) {
        if (entityId == null) return null;
        for (KillAuraTargetSnapshot candidate : candidates) {
            if (candidate.entityId() == entityId) return candidate;
        }
        return null;
    }
}
