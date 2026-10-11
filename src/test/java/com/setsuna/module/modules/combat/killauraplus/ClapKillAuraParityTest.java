package com.setsuna.module.modules.combat.killauraplus;

import clap.refactor.core.ModuleEnableGuard;
import clap.refactor.modules.combat.KillAuraModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static com.setsuna.module.modules.combat.killauraplus.KillAuraPlusEngineTest.airborne;
import static com.setsuna.module.modules.combat.killauraplus.KillAuraPlusEngineTest.target;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClapKillAuraParityTest {
    @Test
    void stateTransitionsMatchRunningOriginalClapModuleDirectly() {
        Harness harness = new Harness();
        KillAuraPlusEngineTest.Configuration settings = new KillAuraPlusEngineTest.Configuration();
        harness.change(context -> {
            context.targets.add(target(1, 2.0, 5.0f, 0.0f));
            context.targets.add(target(2, 2.5, 10.0f, 30.0f));
        });
        harness.tick(settings.build(), true);
        harness.change(context -> context.rayHit = false);
        harness.tick(settings.build(), true);
        harness.change(context -> { context.rayHit = true; context.cooldown = 0.9f; });
        harness.tick(settings.build(), true);
        harness.change(context -> context.cooldown = 1.0f);
        harness.tick(settings.build(), true);
        settings.mode = KillAuraMode.SINGLE;
        harness.tick(settings.build(), true);
        harness.change(context -> context.targets.set(0, target(1, 2.0, 20.0f, 0.0f)));
        harness.tick(settings.build(), true);
        settings.priority = KillAuraPriority.ANGLE;
        harness.tick(settings.build(), true);
        harness.change(context -> context.interactionAvailable = false);
        harness.tick(settings.build(), true);
        harness.change(context -> { context.interactionAvailable = true; context.screenOpen = true; });
        harness.tick(settings.build(), true);
        harness.change(context -> context.screenOpen = false);
        harness.tick(settings.build(), false);
        harness.tick(settings.build(), true);
        harness.change(context -> {
            context.targets.clear();
            context.targets.add(target(3, 4.0, 10.0f, 10.0f));
        });
        harness.tick(settings.build(), true);
        harness.change(context -> context.targets.clear());
        harness.tick(settings.build(), true);
    }

    @Test
    void completeSettingMatrixMatchesOriginalActionsTargetsAndSideEffects() {
        Harness harness = new Harness();
        KillAuraPlusEngineTest.Configuration settings = new KillAuraPlusEngineTest.Configuration();
        harness.change(context -> {
            context.targets.add(target(1, 2.0, 7.0f, 10.0f));
            context.targets.add(target(2, KillAuraTargetType.HOSTILE_MOB, 2.1, 4.41, 4.0f, 5.0f, 2.0f));
            context.targets.add(target(3, KillAuraTargetType.ANIMAL, 4.0, 16.0, 1.0f, 20.0f, 0.0f));
            context.useInputActive = true;
            context.normalAttackSupported = false;
        });
        settings.rotationRange = 4.5;
        settings.rotationSpeed = 65;
        settings.range = 2.8;
        settings.fov = 45;
        settings.mobs = true;
        settings.animals = true;
        settings.invisibles = true;
        settings.ignoreFriends = false;
        settings.ignoreTeam = false;
        for (KillAuraMode mode : KillAuraMode.values()) {
            settings.mode = mode;
            for (KillAuraPriority priority : KillAuraPriority.values()) {
                settings.priority = priority;
                for (KillAuraRotationMode rotation : KillAuraRotationMode.values()) {
                    settings.rotationMode = rotation;
                    for (KillAuraCriticalMode critical : KillAuraCriticalMode.values()) {
                        settings.criticalMode = critical;
                        for (boolean swing : List.of(false, true)) {
                            settings.swing = swing;
                            for (boolean block : List.of(false, true)) {
                                settings.fakeBlock = block;
                                harness.change(context -> context.criticalState = airborne(-0.01, 0.0f));
                                harness.tick(settings.build(), true);
                                harness.change(context -> context.criticalState = airborne(-0.01, 0.2f));
                                harness.tick(settings.build(), true);
                                harness.tick(settings.build(), true);
                            }
                        }
                    }
                }
            }
        }
        settings.onlyWeapon = true;
        harness.change(context -> context.supportedWeapon = false);
        harness.tick(settings.build(), true);
        harness.change(context -> { context.supportedWeapon = true; context.available = false; });
        harness.tick(settings.build(), true);
    }

    private static final class Harness {
        final KillAuraPlusEngineTest.FakeContext expected = new KillAuraPlusEngineTest.FakeContext();
        final KillAuraPlusEngineTest.FakeContext actual = new KillAuraPlusEngineTest.FakeContext();
        final KillAuraModule original = new KillAuraModule(new DirectContext(expected));
        final KillAuraPlusEngine engine = new KillAuraPlusEngine(actual);
        int ticks;

        void change(Consumer<KillAuraPlusEngineTest.FakeContext> change) {
            change.accept(expected);
            change.accept(actual);
        }

        void tick(KillAuraConfig config, boolean enabled) {
            synchronizeOriginal(config);
            if (enabled) original.enable(ignored -> ModuleEnableGuard.EnableDecision.permit());
            else original.disable();
            String label = "tick " + ++ticks + " " + config;
            assertEquals(original.tick().name(), engine.tick(config, enabled).name(), label);
            assertEquals(original.attackTargetEntityId(), engine.attackTargetEntityId(), label);
            assertEquals(original.rotationTargetEntityId(), engine.rotationTargetEntityId(), label);
            assertEquals(original.isFakeBlocking(), engine.isFakeBlocking(), label);
            assertEquals(expected.attackedIds, actual.attackedIds, label);
            assertEquals(expected.attackSteps, actual.attackSteps, label);
            assertEquals(expected.searchRange, actual.searchRange, label);
            assertEquals(expected.rotationCalls, actual.rotationCalls, label);
            assertEquals(expected.requestedYaw, actual.requestedYaw, label);
            assertEquals(expected.requestedPitch, actual.requestedPitch, label);
            assertEquals(expected.requestedSpeed, actual.requestedSpeed, label);
            assertEquals(expected.rayCalls, actual.rayCalls, label);
            assertEquals(expected.rayTargetId, actual.rayTargetId, label);
            assertEquals(expected.rayReach, actual.rayReach, label);
        }

        private void synchronizeOriginal(KillAuraConfig config) {
            var mode = clap.refactor.modules.combat.KillAuraMode.valueOf(config.mode().name());
            var priority = clap.refactor.modules.combat.KillAuraPriority.valueOf(config.priority().name());
            var rotation = clap.refactor.modules.combat.KillAuraRotationMode.valueOf(config.rotationMode().name());
            var critical = clap.refactor.modules.combat.KillAuraCriticalMode.valueOf(config.criticalMode().name());
            // Setting.set invokes callbacks for equal values too; only actual changes are user edits.
            if (original.mode() != mode) original.setMode(mode);
            if (original.priority() != priority) original.setPriority(priority);
            if (original.rotationMode() != rotation) original.setRotationMode(rotation);
            if (original.criticalMode() != critical) original.setCriticalMode(critical);
            if (original.rotationRange() != config.rotationRange()) original.setRotationRange(config.rotationRange());
            if (original.rotationSpeed() != config.rotationSpeed()) original.setRotationSpeed(config.rotationSpeed());
            if (original.range() != config.range()) original.setRange(config.range());
            if (original.fov() != config.fov()) original.setFov(config.fov());
            if (original.onlyWeapon() != config.onlyWeapon()) original.setOnlyWeapon(config.onlyWeapon());
            if (original.swing() != config.swing()) original.setSwing(config.swing());
            if (original.fakeBlock() != config.fakeBlock()) original.setFakeBlock(config.fakeBlock());
            if (original.players() != config.players()) original.setPlayers(config.players());
            if (original.mobs() != config.mobs()) original.setMobs(config.mobs());
            if (original.animals() != config.animals()) original.setAnimals(config.animals());
            if (original.invisibles() != config.invisibles()) original.setInvisibles(config.invisibles());
            if (original.ignoreTeam() != config.ignoreTeam()) original.setIgnoreTeam(config.ignoreTeam());
            if (original.ignoreFriends() != config.ignoreFriends()) original.setIgnoreFriends(config.ignoreFriends());
        }
    }

    private record DirectContext(KillAuraPlusEngineTest.FakeContext context)
            implements clap.refactor.modules.combat.KillAuraContext {
        @Override public boolean isAvailable() { return context.isAvailable(); }
        @Override public boolean isScreenOpen() { return context.isScreenOpen(); }
        @Override public boolean isSupportedWeaponHeld() { return context.isSupportedWeaponHeld(); }
        @Override public boolean isSwordHeld() { return context.isSwordHeld(); }
        @Override public boolean isInteractionAvailable() { return context.isInteractionAvailable(); }
        @Override public float playerYaw() { return context.playerYaw(); }
        @Override public float playerPitch() { return context.playerPitch(); }
        @Override public float attackCooldownProgress() { return context.attackCooldownProgress(); }
        @Override public clap.refactor.modules.combat.LegitAuraCriticalState criticalState() {
            LegitAuraCriticalState state = context.criticalState();
            return new clap.refactor.modules.combat.LegitAuraCriticalState(state.verticalVelocity(),
                    state.fallDistance(), state.onGround(), state.touchingWater(), state.inLava(),
                    state.climbing(), state.blindness(), state.hasVehicle(), state.flying(),
                    state.sprinting(), state.attackCooldownPeriodTicks());
        }
        @Override public List<clap.refactor.modules.combat.KillAuraTargetSnapshot> targets(double range) {
            return context.targets(range).stream().map(target ->
                    new clap.refactor.modules.combat.KillAuraTargetSnapshot(target.entityId(),
                            clap.refactor.modules.combat.KillAuraTargetType.valueOf(target.type().name()),
                            target.localPlayer(), target.alive(), target.removed(), target.invisible(),
                            target.visible(), target.friend(), target.teammate(), target.antiBot(),
                            target.closestDistance(), target.distanceSquared(), target.health(),
                            target.targetYaw(), target.targetPitch())).toList();
        }
        @Override public void requestRotation(float yaw, float pitch, float speed) {
            context.requestRotation(yaw, pitch, speed);
        }
        @Override public boolean rotationRayHitsTarget(int entityId, double reach) {
            return context.rotationRayHitsTarget(entityId, reach);
        }
        @Override public void attackEntity(int entityId) { context.attackEntity(entityId); }
        @Override public void swingMainHand() { context.swingMainHand(); }
    }
}
