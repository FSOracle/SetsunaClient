package com.setsuna.module.modules.combat.killauraplus;

import Combat.Killaura.modules.combat.KillAuraCriticalMode;
import Combat.Killaura.modules.combat.KillAuraMode;
import Combat.Killaura.modules.combat.KillAuraPriority;
import Combat.Killaura.modules.combat.KillAuraRotationMode;
import Combat.Killaura.modules.combat.KillAuraTargetType;
import Combat.Killaura.core.ModuleEnableGuard;
import Combat.Killaura.modules.combat.KillAuraModule;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/** Setsuna configuration and runtime bridge to the unchanged Clap module. */
public final class KillAuraPlusEngine {
    private final KillAuraModule module;
    private KillAuraBlockReason lastBlockReason = KillAuraBlockReason.NONE;
    private float checkedCooldown;

    public KillAuraPlusEngine(KillAuraContext context) {
        module = new KillAuraModule(new ClapContext(Objects.requireNonNull(context, "context")));
    }

    public KillAuraAction tick(KillAuraConfig config, boolean enabled) {
        synchronizeConfig(Objects.requireNonNull(config, "config"));
        lastBlockReason = KillAuraBlockReason.NONE;
        checkedCooldown = 0.0f;
        if (enabled) {
            module.enable(ignored -> ModuleEnableGuard.EnableDecision.permit());
        } else {
            module.disable();
        }

        KillAuraAction action = KillAuraAction.valueOf(module.tick().name());
        if (action == KillAuraAction.ATTACK) {
            lastBlockReason = KillAuraBlockReason.ATTACKED;
        } else if (lastBlockReason == KillAuraBlockReason.NONE) {
            if (!module.isEnabled()) {
                lastBlockReason = KillAuraBlockReason.INACTIVE;
            } else if (module.attackTargetEntityId() == null) {
                lastBlockReason = KillAuraBlockReason.NO_TARGET;
            } else {
                lastBlockReason = checkedCooldown <= 0.9f
                        ? KillAuraBlockReason.COOLDOWN : KillAuraBlockReason.CRITICAL;
            }
        }
        return action;
    }

    public boolean isEligible(KillAuraTargetSnapshot target, double maximumRange, KillAuraConfig config) {
        synchronizeConfig(Objects.requireNonNull(config, "config"));
        return module.isEligible(toClap(target), maximumRange);
    }

    public boolean shouldWaitForSmartCritical(LegitAuraCriticalState state) {
        return module.shouldWaitForSmartCritical(toClap(state));
    }

    public Integer attackTargetEntityId() {
        return module.attackTargetEntityId();
    }

    public Integer rotationTargetEntityId() {
        return module.rotationTargetEntityId();
    }

    public boolean isFakeBlocking() {
        return module.isFakeBlocking();
    }

    public KillAuraBlockReason lastBlockReason() {
        return lastBlockReason;
    }

    public void configureTargetEsp(boolean enabled, float radius, float alpha, Color sideColor, Color lineColor) {
        module.setTargetEsp(enabled);
        module.setTargetEspRadius(radius);
        module.setTargetEspAlpha(alpha);
        module.setTargetEspSideColor(sideColor);
        module.setTargetEspLineColor(lineColor);
    }

    public Integer targetEspEntityId() {
        return module.targetEspEntityId();
    }

    public KillAuraTargetEspStyle targetEspStyle() {
        var style = module.targetEspStyle();
        return new KillAuraTargetEspStyle(style.radius(), style.alpha(), style.sideColor(), style.lineColor());
    }

    public void resetAttackSelection() {
        module.setMode(module.mode());
        lastBlockReason = KillAuraBlockReason.NONE;
    }

    public void clearFakeBlocking() {
        module.setFakeBlock(false);
    }

    public void reset() {
        module.disable();
        lastBlockReason = KillAuraBlockReason.NONE;
    }

    private void synchronizeConfig(KillAuraConfig config) {
        var mode = KillAuraMode.valueOf(config.mode().name());
        var priority = KillAuraPriority.valueOf(config.priority().name());
        var rotationMode = KillAuraRotationMode.valueOf(config.rotationMode().name());
        var criticalMode = KillAuraCriticalMode.valueOf(config.criticalMode().name());
        // Clap fires change callbacks even for equal values; reapplying these
        // settings every tick would reset Switch and Single target selection.
        if (module.mode() != mode) module.setMode(mode);
        if (module.priority() != priority) module.setPriority(priority);
        if (module.rotationMode() != rotationMode) module.setRotationMode(rotationMode);
        if (module.criticalMode() != criticalMode) module.setCriticalMode(criticalMode);
        if (module.rotationRange() != config.rotationRange()) module.setRotationRange(config.rotationRange());
        if (module.rotationSpeed() != config.rotationSpeed()) module.setRotationSpeed(config.rotationSpeed());
        if (module.range() != config.range()) module.setRange(config.range());
        if (module.fov() != config.fov()) module.setFov(config.fov());
        if (module.onlyWeapon() != config.onlyWeapon()) module.setOnlyWeapon(config.onlyWeapon());
        if (module.swing() != config.swing()) module.setSwing(config.swing());
        if (module.fakeBlock() != config.fakeBlock()) module.setFakeBlock(config.fakeBlock());
        if (module.players() != config.players()) module.setPlayers(config.players());
        if (module.mobs() != config.mobs()) module.setMobs(config.mobs());
        if (module.animals() != config.animals()) module.setAnimals(config.animals());
        if (module.invisibles() != config.invisibles()) module.setInvisibles(config.invisibles());
        if (module.ignoreTeam() != config.ignoreTeam()) module.setIgnoreTeam(config.ignoreTeam());
        if (module.ignoreFriends() != config.ignoreFriends()) module.setIgnoreFriends(config.ignoreFriends());
    }

    private static Combat.Killaura.modules.combat.KillAuraTargetSnapshot toClap(KillAuraTargetSnapshot target) {
        if (target == null) return null;
        return new Combat.Killaura.modules.combat.KillAuraTargetSnapshot(
                target.entityId(), KillAuraTargetType.valueOf(target.type().name()),
                target.localPlayer(), target.alive(), target.removed(), target.invisible(), target.visible(),
                target.friend(), target.teammate(), target.antiBot(), target.closestDistance(),
                target.distanceSquared(), target.health(), target.targetYaw(), target.targetPitch());
    }

    private static Combat.Killaura.modules.combat.LegitAuraCriticalState toClap(LegitAuraCriticalState state) {
        if (state == null) return null;
        return new Combat.Killaura.modules.combat.LegitAuraCriticalState(
                state.verticalVelocity(), state.fallDistance(), state.onGround(), state.touchingWater(),
                state.inLava(), state.climbing(), state.blindness(), state.hasVehicle(), state.flying(),
                state.sprinting(), state.attackCooldownPeriodTicks());
    }

    private final class ClapContext implements Combat.Killaura.modules.combat.KillAuraContext {
        private final KillAuraContext context;

        private ClapContext(KillAuraContext context) {
            this.context = context;
        }

        @Override
        public boolean isAvailable() {
            boolean available = context.isAvailable();
            if (!available) lastBlockReason = KillAuraBlockReason.INACTIVE;
            return available;
        }

        @Override
        public boolean isScreenOpen() {
            boolean screenOpen = context.isScreenOpen();
            if (screenOpen) lastBlockReason = KillAuraBlockReason.INACTIVE;
            return screenOpen;
        }

        @Override
        public boolean isSupportedWeaponHeld() {
            boolean supported = context.isSupportedWeaponHeld();
            if (!supported) lastBlockReason = KillAuraBlockReason.INACTIVE;
            return supported;
        }

        @Override public boolean isSwordHeld() { return context.isSwordHeld(); }

        @Override
        public boolean isInteractionAvailable() {
            boolean available = context.isInteractionAvailable();
            if (!available) lastBlockReason = KillAuraBlockReason.INTERACTION;
            return available;
        }

        @Override public float playerYaw() { return context.playerYaw(); }
        @Override public float playerPitch() { return context.playerPitch(); }

        @Override
        public float attackCooldownProgress() {
            checkedCooldown = context.attackCooldownProgress();
            return checkedCooldown;
        }

        @Override
        public Combat.Killaura.modules.combat.LegitAuraCriticalState criticalState() {
            return toClap(context.criticalState());
        }

        @Override
        public List<Combat.Killaura.modules.combat.KillAuraTargetSnapshot> targets(double searchRange) {
            return context.targets(searchRange).stream().map(KillAuraPlusEngine::toClap).toList();
        }

        @Override
        public void requestRotation(float yaw, float pitch, float speed) {
            context.requestRotation(yaw, pitch, speed);
        }

        @Override
        public boolean rotationRayHitsTarget(int entityId, double reach) {
            boolean hit = context.rotationRayHitsTarget(entityId, reach);
            if (!hit) lastBlockReason = KillAuraBlockReason.RAYTRACE;
            return hit;
        }

        @Override public void attackEntity(int entityId) { context.attackEntity(entityId); }
        @Override public void swingMainHand() { context.swingMainHand(); }
    }
}
