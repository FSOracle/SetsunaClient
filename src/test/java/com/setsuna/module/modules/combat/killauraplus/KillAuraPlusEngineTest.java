package com.setsuna.module.modules.combat.killauraplus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraPlusEngineTest {
    @Test
    void switchAdvancesOnlyAfterSuccessfulAttacksAndSingleRetains() {
        FakeContext context = new FakeContext();
        context.targets.add(target(1, 2.0, 5.0f, 0.0f));
        context.targets.add(target(2, 2.5, 10.0f, 0.0f));
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);

        context.rayHit = false;
        assertEquals(KillAuraAction.ROTATED, engine.tick(defaults(), true));
        assertEquals(1, engine.attackTargetEntityId());
        context.rayHit = true;
        assertEquals(KillAuraAction.ATTACK, engine.tick(defaults(), true));
        assertEquals(1, context.lastAttackedId);
        assertEquals(KillAuraAction.ATTACK, engine.tick(defaults(), true));
        assertEquals(2, context.lastAttackedId);

        engine.reset();
        KillAuraConfig single = withMode(defaults(), KillAuraMode.SINGLE);
        engine.tick(single, true);
        assertEquals(1, context.lastAttackedId);
        context.targets.set(0, target(1, 2.0, 20.0f, 0.0f));
        context.targets.set(1, target(2, 2.5, 1.0f, 0.0f));
        engine.tick(single, true);
        assertEquals(1, context.lastAttackedId);
    }

    @Test
    void keepsAttackAndRotationRangesIndependent() {
        FakeContext context = new FakeContext();
        context.cooldown = 0.0f;
        context.targets.add(target(20, 4.0, 7.0f, 30.0f));
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);

        assertEquals(KillAuraAction.ROTATED, engine.tick(defaults(), true));
        assertNull(engine.attackTargetEntityId());
        assertEquals(20, engine.rotationTargetEntityId());
        assertEquals(180.0f, context.requestedSpeed);

        context.targets.set(0, target(20, 2.0, 7.0f, 30.0f));
        KillAuraConfig smoothBlock = modify(
                defaults(), KillAuraRotationMode.SMOOTH, 75,
                KillAuraCriticalMode.NONE, true, true);
        engine.tick(smoothBlock, true);
        assertEquals(75.0f, context.requestedSpeed);
        assertTrue(engine.isFakeBlocking());
        context.swordHeld = false;
        engine.tick(smoothBlock, true);
        assertFalse(engine.isFakeBlocking());
    }

    @Test
    void appliesCriticalCooldownRayAndScreenGates() {
        FakeContext context = new FakeContext();
        context.targets.add(target(30, 2.0, 10.0f, 0.0f));
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);

        KillAuraConfig critOnly = modify(
                defaults(), KillAuraRotationMode.SNAP, 90,
                KillAuraCriticalMode.CRIT_ONLY, false, true);
        assertEquals(KillAuraAction.ROTATED, engine.tick(critOnly, true));
        context.criticalState = airborne(-0.01, 0.2f);
        assertEquals(KillAuraAction.ATTACK, engine.tick(critOnly, true));

        context.rayHit = false;
        assertEquals(KillAuraAction.ROTATED, engine.tick(critOnly, true));
        context.screenOpen = true;
        assertEquals(KillAuraAction.NONE, engine.tick(critOnly, true));
        assertNull(engine.attackTargetEntityId());
    }

    @Test
    void filtersPlayersExactlyAndDoublesNonPlayerPriority() {
        FakeContext context = new FakeContext();
        KillAuraPlusEngine engine = new KillAuraPlusEngine(context);
        KillAuraTargetSnapshot player = target(1, 2.0, 10.0f, 10.0f);
        assertTrue(engine.isEligible(player, 3.0, defaults()));
        assertFalse(engine.isEligible(copyFlags(player, true, false, false), 3.0, defaults()));
        assertFalse(engine.isEligible(copyFlags(player, false, true, false), 3.0, defaults()));
        assertFalse(engine.isEligible(copyFlags(player, false, false, true), 3.0, defaults()));

        context.targets.add(target(10, 2.0, 12.0f, 4.0f));
        context.targets.add(target(11, 2.5, 4.0f, 20.0f));
        engine.tick(defaults(), true);
        assertEquals(11, context.lastAttackedId);
    }

    private static KillAuraConfig defaults() {
        return new KillAuraConfig(
                KillAuraMode.SWITCH,
                KillAuraPriority.HEALTH,
                KillAuraRotationMode.SNAP,
                KillAuraCriticalMode.NONE,
                5.0, 90, 3.0, 180,
                false, true, false,
                true, false, false, false, true, true);
    }

    private static KillAuraConfig withMode(KillAuraConfig source, KillAuraMode mode) {
        return new KillAuraConfig(
                mode, source.priority(), source.rotationMode(), source.criticalMode(),
                source.rotationRange(), source.rotationSpeed(), source.range(), source.fov(),
                source.onlyWeapon(), source.swing(), source.fakeBlock(), source.players(),
                source.mobs(), source.animals(), source.invisibles(),
                source.ignoreTeam(), source.ignoreFriends());
    }

    private static KillAuraConfig modify(
            KillAuraConfig source,
            KillAuraRotationMode rotationMode,
            int rotationSpeed,
            KillAuraCriticalMode criticalMode,
            boolean fakeBlock,
            boolean swing) {
        return new KillAuraConfig(
                source.mode(), source.priority(), rotationMode, criticalMode,
                source.rotationRange(), rotationSpeed, source.range(), source.fov(),
                source.onlyWeapon(), swing, fakeBlock, source.players(), source.mobs(),
                source.animals(), source.invisibles(), source.ignoreTeam(), source.ignoreFriends());
    }

    private static KillAuraTargetSnapshot target(
            int id, double distance, float health, float yaw) {
        return new KillAuraTargetSnapshot(
                id, KillAuraTargetType.PLAYER, false, true, false, false, true,
                false, false, false, distance, distance * distance, health, yaw, 0.0f);
    }

    private static KillAuraTargetSnapshot copyFlags(
            KillAuraTargetSnapshot source, boolean friend, boolean teammate, boolean antiBot) {
        return new KillAuraTargetSnapshot(
                source.entityId(), source.type(), source.localPlayer(), source.alive(), source.removed(),
                source.invisible(), source.visible(), friend, teammate, antiBot,
                source.closestDistance(), source.distanceSquared(), source.health(),
                source.targetYaw(), source.targetPitch());
    }

    private static LegitAuraCriticalState grounded() {
        return new LegitAuraCriticalState(
                0.0, 0.0f, true, false, false, false,
                false, false, false, false, 5.0f);
    }

    private static LegitAuraCriticalState airborne(double velocity, float fallDistance) {
        return new LegitAuraCriticalState(
                velocity, fallDistance, false, false, false, false,
                false, false, false, false, 5.0f);
    }

    private static final class FakeContext implements KillAuraContext {
        private final List<KillAuraTargetSnapshot> targets = new ArrayList<>();
        private boolean available = true;
        private boolean screenOpen;
        private boolean supportedWeapon = true;
        private boolean swordHeld = true;
        private boolean interactionAvailable = true;
        private boolean rayHit = true;
        private float yaw;
        private float pitch;
        private float cooldown = 1.0f;
        private LegitAuraCriticalState criticalState = grounded();
        private float requestedSpeed;
        private int lastAttackedId = -1;

        @Override public boolean isAvailable() { return available; }
        @Override public boolean isScreenOpen() { return screenOpen; }
        @Override public boolean isSupportedWeaponHeld() { return supportedWeapon; }
        @Override public boolean isSwordHeld() { return swordHeld; }
        @Override public boolean isInteractionAvailable() { return interactionAvailable; }
        @Override public float playerYaw() { return yaw; }
        @Override public float playerPitch() { return pitch; }
        @Override public float attackCooldownProgress() { return cooldown; }
        @Override public LegitAuraCriticalState criticalState() { return criticalState; }
        @Override public List<KillAuraTargetSnapshot> targets(double searchRange) {
            return List.copyOf(targets);
        }
        @Override public void requestRotation(float yaw, float pitch, float speed) {
            requestedSpeed = speed;
        }
        @Override public boolean rotationRayHitsTarget(int entityId, double reach) { return rayHit; }
        @Override public void attackEntity(int entityId) { lastAttackedId = entityId; }
        @Override public void swingMainHand() {}
    }
}
