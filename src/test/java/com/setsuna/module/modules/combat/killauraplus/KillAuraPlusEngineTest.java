package com.setsuna.module.modules.combat.killauraplus;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraPlusEngineTest {
    @Test
    void switchAdvancesOnlyAfterSuccessfulAttacks() {
        FakeContext context = twoTargets();
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        context.rayHit = false;
        assertEquals(KillAuraAction.ROTATED, engine.tick(defaults(), true));
        assertEquals(1, engine.attackTargetEntityId());
        assertEquals(KillAuraBlockReason.RAYTRACE, engine.lastBlockReason());
        context.rayHit = true;
        context.cooldown = 0.9f;
        assertEquals(KillAuraAction.ROTATED, engine.tick(defaults(), true));
        assertEquals(1, engine.attackTargetEntityId());
        context.cooldown = 1.0f;
        engine.tick(defaults(), true);
        engine.tick(defaults(), true);
        assertEquals(List.of(1, 2), context.attackedIds);
    }

    @Test
    void singleRetainsTargetAcrossRayMissCooldownAndScoreChanges() {
        FakeContext context = twoTargets();
        Configuration settings = new Configuration();
        settings.mode = KillAuraMode.SINGLE;
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        engine.tick(settings.build(), true);
        context.targets.set(0, target(1, 2.0, 20.0f, 0.0f));
        context.targets.set(1, target(2, 2.5, 1.0f, 0.0f));
        context.rayHit = false;
        engine.tick(settings.build(), true);
        assertEquals(1, engine.attackTargetEntityId());
        context.rayHit = true;
        context.cooldown = 0.2f;
        engine.tick(settings.build(), true);
        assertEquals(1, engine.attackTargetEntityId());
        context.cooldown = 1.0f;
        engine.tick(settings.build(), true);
        assertEquals(List.of(1, 1), context.attackedIds);
        context.targets.remove(0);
        engine.tick(settings.build(), true);
        assertEquals(2, engine.attackTargetEntityId());
    }

    @Test
    void synchronizingEqualConfigurationsDoesNotResetSelection() {
        FakeContext context = twoTargets();
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        engine.tick(defaults(), true);
        assertTrue(engine.isEligible(context.targets.get(0), 3.0, defaults()));
        engine.tick(defaults(), true);
        engine.tick(defaults(), true);
        assertEquals(List.of(1, 2, 1), context.attackedIds);
    }

    @Test
    void changingModeOrPriorityResetsAttackSelection() {
        FakeContext context = twoTargets();
        Configuration settings = new Configuration();
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        engine.tick(settings.build(), true);
        settings.mode = KillAuraMode.SINGLE;
        engine.tick(settings.build(), true);
        settings.mode = KillAuraMode.SWITCH;
        engine.tick(settings.build(), true);
        assertEquals(List.of(1, 1, 1), context.attackedIds);
        context.targets.set(0, target(1, 2.0, 5.0f, 30.0f));
        context.targets.set(1, target(2, 2.5, 10.0f, 1.0f));
        settings.priority = KillAuraPriority.ANGLE;
        engine.tick(settings.build(), true);
        assertEquals(2, context.lastAttackedId);
        settings.priority = KillAuraPriority.HEALTH;
        engine.tick(settings.build(), true);
        assertEquals(1, context.lastAttackedId);
    }

    @Test
    void keepsAttackAndRotationRangesIndependentAndRetainsRotationTarget() {
        FakeContext context = new FakeContext();
        context.targets.add(target(20, 4.0, 7.0f, 30.0f));
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        assertEquals(KillAuraAction.ROTATED, engine.tick(defaults(), true));
        assertNull(engine.attackTargetEntityId());
        assertEquals(20, engine.rotationTargetEntityId());
        assertEquals(6.0, context.searchRange);
        assertEquals(180.0f, context.requestedSpeed);
        context.targets.add(target(21, 3.5, 1.0f, 0.0f));
        engine.tick(defaults(), true);
        assertEquals(20, engine.rotationTargetEntityId());
        context.targets.set(1, target(21, 2.0, 1.0f, 0.0f));
        assertEquals(KillAuraAction.ATTACK, engine.tick(defaults(), true));
        assertEquals(21, engine.attackTargetEntityId());
        assertEquals(21, engine.rotationTargetEntityId());
        assertEquals(3.0, context.rayReach);
    }

    @Test
    void cooldownMustBeStrictlyGreaterThanPointNine() {
        FakeContext context = twoTargets();
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        context.cooldown = 0.9f;
        assertEquals(KillAuraAction.ROTATED, engine.tick(defaults(), true));
        assertEquals(KillAuraBlockReason.COOLDOWN, engine.lastBlockReason());
        assertEquals(0, context.rayCalls);
        context.cooldown = Math.nextUp(0.9f);
        assertEquals(KillAuraAction.ATTACK, engine.tick(defaults(), true));
        assertEquals(KillAuraBlockReason.ATTACKED, engine.lastBlockReason());
    }

    @Test
    void critOnlyRequiresFallingSupportedNonSprintingCriticalState() {
        FakeContext context = twoTargets();
        Configuration settings = new Configuration();
        settings.criticalMode = KillAuraCriticalMode.CRIT_ONLY;
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        assertEquals(KillAuraAction.ROTATED, engine.tick(settings.build(), true));
        assertEquals(KillAuraBlockReason.CRITICAL, engine.lastBlockReason());
        context.criticalState = airborne(-0.01, 0.2f);
        assertEquals(KillAuraAction.ATTACK, engine.tick(settings.build(), true));
        context.criticalState = new LegitAuraCriticalState(-0.01, 0.2f, false,
                false, false, false, false, false, false, true, 5.0f);
        assertEquals(KillAuraAction.ROTATED, engine.tick(settings.build(), true));
        context.criticalState = new LegitAuraCriticalState(-0.01, 0.2f, false,
                true, false, false, false, false, false, false, 5.0f);
        assertEquals(KillAuraAction.ROTATED, engine.tick(settings.build(), true));
    }

    @Test
    void smartCriticalMatchesOriginalLeadTimeConditions() {
        FakeContext context = twoTargets();
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        assertFalse(engine.shouldWaitForSmartCritical(null));
        assertFalse(engine.shouldWaitForSmartCritical(grounded()));
        assertTrue(engine.shouldWaitForSmartCritical(airborne(-0.01, 0.0f)));
        assertFalse(engine.shouldWaitForSmartCritical(airborne(-0.09, 0.0f)));
        assertFalse(engine.shouldWaitForSmartCritical(airborne(0.2, 0.0f)));
        assertFalse(engine.shouldWaitForSmartCritical(airborne(-0.01, 0.2f)));
        assertFalse(engine.shouldWaitForSmartCritical(new LegitAuraCriticalState(0.0, 0.0f,
                false, false, false, false, false, false, false, false, 0.0f)));
        Configuration settings = new Configuration();
        settings.criticalMode = KillAuraCriticalMode.SMART;
        context.criticalState = airborne(-0.01, 0.0f);
        assertEquals(KillAuraAction.ROTATED, engine.tick(settings.build(), true));
        context.criticalState = airborne(-0.01, 0.2f);
        assertEquals(KillAuraAction.ATTACK, engine.tick(settings.build(), true));
    }

    @Test
    void filtersAllTargetTypesAndPlayerFlags() {
        KillAuraPlusEngine engine = new KillAuraPlusEngine(new FakeContext());
        Configuration settings = new Configuration();
        for (KillAuraTargetType type : KillAuraTargetType.values()) {
            KillAuraTargetSnapshot candidate = target(1, type, 2.0, 4.0, 10.0f, 0.0f, 0.0f);
            assertEquals(type == KillAuraTargetType.PLAYER,
                    engine.isEligible(candidate, 3.0, settings.build()), type.name());
        }
        settings.players = false;
        settings.mobs = true;
        settings.animals = true;
        for (KillAuraTargetType type : KillAuraTargetType.values()) {
            KillAuraTargetSnapshot candidate = target(1, type, 2.0, 4.0, 10.0f, 0.0f, 0.0f);
            assertEquals(type == KillAuraTargetType.ANIMAL
                            || type == KillAuraTargetType.HOSTILE_MOB || type == KillAuraTargetType.OTHER_MOB,
                    engine.isEligible(candidate, 3.0, settings.build()), type.name());
        }
        settings.players = true;
        KillAuraTargetSnapshot player = target(1, 2.0, 10.0f, 0.0f);
        assertFalse(engine.isEligible(copyFlags(player, true, false, false), 3.0, settings.build()));
        assertFalse(engine.isEligible(copyFlags(player, false, true, false), 3.0, settings.build()));
        assertFalse(engine.isEligible(copyFlags(player, false, false, true), 3.0, settings.build()));
        settings.ignoreFriends = false;
        settings.ignoreTeam = false;
        assertTrue(engine.isEligible(copyFlags(player, true, true, false), 3.0, settings.build()));
        assertFalse(engine.isEligible(copyFlags(player, true, true, true), 3.0, settings.build()));
        KillAuraTargetSnapshot animal = target(2, KillAuraTargetType.ANIMAL, 2.0, 4.0, 1.0f, 0.0f, 0.0f);
        assertTrue(engine.isEligible(copyFlags(animal, true, true, true), 3.0, settings.build()));
    }

    @Test
    void filtersNullLocalDeadRemovedInvisibleAndOccludedTargets() {
        KillAuraPlusEngine engine = new KillAuraPlusEngine(new FakeContext());
        Configuration settings = new Configuration();
        assertFalse(engine.isEligible(null, 3.0, settings.build()));
        assertFalse(engine.isEligible(lifecycle(true, true, false, false, true), 3.0, settings.build()));
        assertFalse(engine.isEligible(lifecycle(false, false, false, false, true), 3.0, settings.build()));
        assertFalse(engine.isEligible(lifecycle(false, true, true, false, true), 3.0, settings.build()));
        assertFalse(engine.isEligible(lifecycle(false, true, false, false, false), 3.0, settings.build()));
        assertFalse(engine.isEligible(lifecycle(false, true, false, true, true), 3.0, settings.build()));
        settings.invisibles = true;
        assertTrue(engine.isEligible(lifecycle(false, true, false, true, true), 3.0, settings.build()));
        assertFalse(engine.isEligible(lifecycle(false, true, false, true, false), 3.0, settings.build()));
    }

    @Test
    void usesClosestDistanceAndWrappedInclusiveFov() {
        FakeContext context = new FakeContext();
        context.yaw = 179.0f;
        Configuration settings = new Configuration();
        settings.fov = 2;
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        KillAuraTargetSnapshot boundary = target(1, KillAuraTargetType.PLAYER,
                3.0, 100.0, 10.0f, -179.0f, 0.0f);
        assertTrue(engine.isEligible(boundary, 3.0, settings.build()));
        assertFalse(engine.isEligible(target(2, 3.001, 10.0f, -179.0f), 3.0, settings.build()));
        assertFalse(engine.isEligible(target(3, 2.0, 10.0f, -178.0f), 3.0, settings.build()));
        context.yaw = -179.0f;
        assertTrue(engine.isEligible(target(4, 2.0, 10.0f, 179.0f), 3.0, settings.build()));
        settings.fov = 180;
        context.yaw = 0.0f;
        assertTrue(engine.isEligible(target(5, 2.0, 10.0f, 180.0f), 3.0, settings.build()));
        assertTrue(engine.isEligible(target(6, 2.0, 10.0f, -180.0f), 3.0, settings.build()));
    }

    @Test
    void doublesNonPlayerHealthDistanceAndAnglePriorityScores() {
        Configuration settings = new Configuration();
        settings.mobs = true;
        for (KillAuraPriority priority : KillAuraPriority.values()) {
            FakeContext context = new FakeContext();
            context.targets.add(target(1, KillAuraTargetType.PLAYER, 2.0, 7.0, 7.0f, 2.0f, 2.0f));
            context.targets.add(target(2, KillAuraTargetType.HOSTILE_MOB, 2.0, 4.0, 4.0f, 2.0f, 1.0f));
            settings.priority = priority;
            new KillAuraPlusEngine(context).tick(settings.build(), true);
            assertEquals(1, context.lastAttackedId, priority.name());
        }
    }

    @Test
    void rotationFallbackDoublesNonPlayerDistanceScores() {
        FakeContext context = new FakeContext();
        context.targets.add(target(1, KillAuraTargetType.PLAYER, 4.0, 20.0, 20.0f, 90.0f, 0.0f));
        context.targets.add(target(2, KillAuraTargetType.OTHER_MOB, 3.5, 12.25, 1.0f, 0.0f, 0.0f));
        Configuration settings = new Configuration();
        settings.mobs = true;
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        assertEquals(KillAuraAction.ROTATED, engine.tick(settings.build(), true));
        assertEquals(1, engine.rotationTargetEntityId());
    }

    @Test
    void smoothRotationSwingAndFakeBlockUseOriginalSettings() {
        FakeContext context = twoTargets();
        Configuration settings = new Configuration();
        settings.rotationMode = KillAuraRotationMode.SMOOTH;
        settings.rotationSpeed = 75;
        settings.fakeBlock = true;
        settings.swing = false;
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        engine.tick(settings.build(), true);
        assertEquals(75.0f, context.requestedSpeed);
        assertEquals(List.of("attack:1"), context.attackSteps);
        assertTrue(engine.isFakeBlocking());
        context.swordHeld = false;
        engine.tick(settings.build(), true);
        assertFalse(engine.isFakeBlocking());
        context.swordHeld = true;
        settings.swing = true;
        engine.tick(settings.build(), true);
        assertEquals(List.of("attack:1", "attack:2", "attack:1", "swing"), context.attackSteps);
        engine.clearFakeBlocking();
        assertFalse(engine.isFakeBlocking());
        engine.tick(settings.build(), true);
        assertTrue(engine.isFakeBlocking());
        settings.fakeBlock = false;
        engine.tick(settings.build(), true);
        assertFalse(engine.isFakeBlocking());
    }

    @Test
    void interactionFailureRetainsTargetAndDoesNotAdvanceSwitch() {
        FakeContext context = twoTargets();
        context.interactionAvailable = false;
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        assertEquals(KillAuraAction.ROTATED, engine.tick(defaults(), true));
        assertEquals(KillAuraBlockReason.INTERACTION, engine.lastBlockReason());
        assertEquals(1, engine.attackTargetEntityId());
        assertTrue(context.attackedIds.isEmpty());
        context.interactionAvailable = true;
        engine.tick(defaults(), true);
        assertEquals(1, context.lastAttackedId);
    }

    @Test
    void hostOnlyUseAndWeaponChecksAreNotAdditionalEngineGates() {
        FakeContext context = twoTargets();
        context.useInputActive = true;
        context.normalAttackSupported = false;
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        assertEquals(KillAuraAction.ATTACK, engine.tick(defaults(), true));
        assertEquals(List.of("attack:1", "swing"), context.attackSteps);
        assertEquals(0, context.useInputQueries);
        assertEquals(0, context.normalAttackQueries);
    }

    @Test
    void onlyWeaponGateDependsOnOnlyWeaponSetting() {
        FakeContext context = twoTargets();
        context.supportedWeapon = false;
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        assertEquals(KillAuraAction.ATTACK, engine.tick(defaults(), true));
        Configuration settings = new Configuration();
        settings.onlyWeapon = true;
        assertEquals(KillAuraAction.NONE, engine.tick(settings.build(), true));
        assertEquals(KillAuraBlockReason.INACTIVE, engine.lastBlockReason());
        assertNull(engine.attackTargetEntityId());
        assertNull(engine.rotationTargetEntityId());
    }

    @Test
    void disableUnavailableAndScreenGatesClearStateAndReenableRestartsSwitch() {
        FakeContext context = twoTargets();
        Configuration settings = new Configuration();
        settings.fakeBlock = true;
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        engine.tick(settings.build(), true);
        assertTrue(engine.isFakeBlocking());
        assertEquals(KillAuraAction.NONE, engine.tick(settings.build(), false));
        assertCleared(engine);
        engine.tick(settings.build(), true);
        assertEquals(1, context.lastAttackedId);
        context.available = false;
        assertEquals(KillAuraAction.NONE, engine.tick(settings.build(), true));
        assertCleared(engine);
        context.available = true;
        engine.tick(settings.build(), true);
        context.screenOpen = true;
        assertEquals(KillAuraAction.NONE, engine.tick(settings.build(), true));
        assertCleared(engine);
        context.screenOpen = false;
        engine.tick(settings.build(), true);
        assertEquals(1, context.lastAttackedId);
        engine.reset();
        assertNull(engine.attackTargetEntityId());
        assertNull(engine.rotationTargetEntityId());
        assertFalse(engine.isFakeBlocking());
        assertEquals(KillAuraBlockReason.NONE, engine.lastBlockReason());
    }

    @Test
    void resettingAttackSelectionRestartsSwitchWithoutDisabling() {
        FakeContext context = twoTargets();
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        engine.tick(defaults(), true);
        engine.resetAttackSelection();
        assertNull(engine.attackTargetEntityId());
        assertEquals(KillAuraBlockReason.NONE, engine.lastBlockReason());
        engine.tick(defaults(), true);
        assertEquals(List.of(1, 1), context.attackedIds);
    }

    @Test
    void targetEspUsesOriginalDefaultsSettingsAndEnabledAttackTarget() {
        FakeContext context = twoTargets();
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        assertEquals(new KillAuraTargetEspStyle(0.75f, 1.0f,
                new Color(255, 255, 255, 255), new Color(255, 255, 255, 233)), engine.targetEspStyle());
        engine.tick(defaults(), true);
        assertNull(engine.targetEspEntityId());
        Color side = new Color(20, 30, 40, 50);
        Color line = new Color(60, 70, 80, 90);
        engine.configureTargetEsp(true, 1.25f, 0.5f, side, line);
        assertEquals(1, engine.targetEspEntityId());
        assertEquals(new KillAuraTargetEspStyle(1.25f, 0.5f, side, line), engine.targetEspStyle());
        engine.tick(defaults(), false);
        assertNull(engine.targetEspEntityId());
        context.targets.clear();
        context.targets.add(target(3, 4.0, 10.0f, 0.0f));
        engine.tick(defaults(), true);
        assertEquals(3, engine.rotationTargetEntityId());
        assertNull(engine.targetEspEntityId());
    }

    private static void assertCleared(KillAuraPlusEngine engine) {
        assertNull(engine.attackTargetEntityId());
        assertNull(engine.rotationTargetEntityId());
        assertFalse(engine.isFakeBlocking());
        assertEquals(KillAuraBlockReason.INACTIVE, engine.lastBlockReason());
    }

    static FakeContext twoTargets() {
        FakeContext context = new FakeContext();
        context.targets.add(target(1, 2.0, 5.0f, 0.0f));
        context.targets.add(target(2, 2.5, 10.0f, 0.0f));
        return context;
    }

    static KillAuraConfig defaults() { return new Configuration().build(); }

    static KillAuraTargetSnapshot target(int id, double distance, float health, float yaw) {
        return target(id, KillAuraTargetType.PLAYER, distance, distance * distance, health, yaw, 0.0f);
    }

    static KillAuraTargetSnapshot target(int id, KillAuraTargetType type,
            double closestDistance, double distanceSquared, float health, float yaw, float pitch) {
        return new KillAuraTargetSnapshot(id, type, false, true, false, false, true,
                false, false, false, closestDistance, distanceSquared, health, yaw, pitch);
    }

    private static KillAuraTargetSnapshot copyFlags(
            KillAuraTargetSnapshot source, boolean friend, boolean teammate, boolean antiBot) {
        return new KillAuraTargetSnapshot(source.entityId(), source.type(), source.localPlayer(),
                source.alive(), source.removed(), source.invisible(), source.visible(), friend, teammate,
                antiBot, source.closestDistance(), source.distanceSquared(), source.health(),
                source.targetYaw(), source.targetPitch());
    }

    private static KillAuraTargetSnapshot lifecycle(
            boolean local, boolean alive, boolean removed, boolean invisible, boolean visible) {
        return new KillAuraTargetSnapshot(1, KillAuraTargetType.PLAYER, local, alive, removed,
                invisible, visible, false, false, false, 2.0, 4.0, 10.0f, 0.0f, 0.0f);
    }

    static LegitAuraCriticalState grounded() {
        return new LegitAuraCriticalState(0.0, 0.0f, true, false, false, false,
                false, false, false, false, 5.0f);
    }

    static LegitAuraCriticalState airborne(double velocity, float fallDistance) {
        return new LegitAuraCriticalState(velocity, fallDistance, false, false, false, false,
                false, false, false, false, 5.0f);
    }

    static final class Configuration {
        KillAuraMode mode = KillAuraMode.SWITCH;
        KillAuraPriority priority = KillAuraPriority.HEALTH;
        KillAuraRotationMode rotationMode = KillAuraRotationMode.SNAP;
        KillAuraCriticalMode criticalMode = KillAuraCriticalMode.NONE;
        double rotationRange = 5.0;
        int rotationSpeed = 90;
        double range = 3.0;
        int fov = 180;
        boolean onlyWeapon;
        boolean swing = true;
        boolean fakeBlock;
        boolean players = true;
        boolean mobs;
        boolean animals;
        boolean invisibles;
        boolean ignoreTeam = true;
        boolean ignoreFriends = true;

        KillAuraConfig build() {
            return new KillAuraConfig(mode, priority, rotationMode, criticalMode,
                    rotationRange, rotationSpeed, range, fov, onlyWeapon, swing, fakeBlock,
                    players, mobs, animals, invisibles, ignoreTeam, ignoreFriends);
        }
    }

    static final class FakeContext implements KillAuraContext {
        final List<KillAuraTargetSnapshot> targets = new ArrayList<>();
        final List<Integer> attackedIds = new ArrayList<>();
        final List<String> attackSteps = new ArrayList<>();
        boolean available = true;
        boolean screenOpen;
        boolean supportedWeapon = true;
        boolean normalAttackSupported = true;
        boolean swordHeld = true;
        boolean interactionAvailable = true;
        boolean useInputActive;
        boolean rayHit = true;
        float yaw;
        float pitch;
        float cooldown = 1.0f;
        LegitAuraCriticalState criticalState = grounded();
        float requestedYaw;
        float requestedPitch;
        float requestedSpeed;
        double searchRange;
        double rayReach;
        int lastAttackedId = -1;
        int rayTargetId = -1;
        int rayCalls;
        int rotationCalls;
        int useInputQueries;
        int normalAttackQueries;

        @Override public boolean isAvailable() { return available; }
        @Override public boolean isScreenOpen() { return screenOpen; }
        @Override public boolean isUseInputActive() { useInputQueries++; return useInputActive; }
        @Override public boolean isSupportedWeaponHeld() { return supportedWeapon; }
        @Override public boolean isNormalAttackSupported() { normalAttackQueries++; return normalAttackSupported; }
        @Override public boolean isSwordHeld() { return swordHeld; }
        @Override public boolean isInteractionAvailable() { return interactionAvailable; }
        @Override public float playerYaw() { return yaw; }
        @Override public float playerPitch() { return pitch; }
        @Override public float attackCooldownProgress() { return cooldown; }
        @Override public LegitAuraCriticalState criticalState() { return criticalState; }
        @Override public List<KillAuraTargetSnapshot> targets(double range) {
            searchRange = range;
            return List.copyOf(targets);
        }
        @Override public void requestRotation(float yaw, float pitch, float speed) {
            rotationCalls++;
            requestedYaw = yaw;
            requestedPitch = pitch;
            requestedSpeed = speed;
        }
        @Override public boolean rotationRayHitsTarget(int entityId, double reach) {
            rayCalls++;
            rayTargetId = entityId;
            rayReach = reach;
            return rayHit;
        }
        @Override public void attackEntity(int entityId) {
            lastAttackedId = entityId;
            attackedIds.add(entityId);
            attackSteps.add("attack:" + entityId);
        }
        @Override public void swingMainHand() { attackSteps.add("swing"); }
    }
}
