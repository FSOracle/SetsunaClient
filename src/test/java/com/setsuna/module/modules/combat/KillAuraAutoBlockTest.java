package com.setsuna.module.modules.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraAutoBlockTest {

    @Test
    void yieldsAutoBlockWhilePlayerUsesANonWeaponItem() {
        assertTrue(KillAura.shouldYieldToItemUse(true, false, false));
        assertFalse(KillAura.shouldYieldToItemUse(true, true, false));
        assertFalse(KillAura.shouldYieldToItemUse(false, false, false));
    }

    @Test
    void yieldsBeforeAutoBlockCanPreemptTheUseKey() {
        assertTrue(KillAura.shouldYieldToItemUse(false, false, true));
        assertTrue(KillAura.shouldYieldToItemUse(true, true, true));
    }

    @Test
    void basicChanceKeepsLiquidBounceInclusiveRollSemantics() {
        assertTrue(KillAura.passesBasicBlockChance(0, 0.0));
        assertFalse(KillAura.passesBasicBlockChance(1, 0.0));
        assertTrue(KillAura.passesBasicBlockChance(99, 100.0));
    }

    @Test
    void basicReblockMatchesTheSourceAttackTickOrdering() {
        assertTrue(KillAura.shouldReblockBasicImmediately(0));
        assertFalse(KillAura.shouldReblockBasicImmediately(1));
        assertFalse(KillAura.shouldStartBasicReblock(true, 0, 0));
        assertFalse(KillAura.shouldStartBasicReblock(true, 1, 1));
        assertFalse(KillAura.shouldStartBasicReblock(false, 1, 2));
        assertTrue(KillAura.shouldStartBasicReblock(false, 2, 2));
    }

    @Test
    void basicChangeSlotUsesTheSourcesNextThenCurrentPair() {
        assertTrue(KillAura.getBasicChangeSlot(0) == 1);
        assertTrue(KillAura.getBasicChangeSlot(8) == 0);
    }

    @Test
    void internalChangeSlotKeepsTheSmoothBlockVisual() {
        assertFalse(KillAura.shouldClearBasicBlockVisualOnSlotChange(true));
        assertTrue(KillAura.shouldClearBasicBlockVisualOnSlotChange(false));
    }

    @Test
    void basicBlinkQueuesOnlyBeforeTheRealBlockStarts() {
        assertTrue(KillAura.shouldQueueBasicBlinkPacket(true, false, false, 0, 1));
        assertFalse(KillAura.shouldQueueBasicBlinkPacket(true, false, false, 0, 0));
        assertFalse(KillAura.shouldQueueBasicBlinkPacket(true, true, false, 0, 10));
        assertFalse(KillAura.shouldQueueBasicBlinkPacket(true, false, true, 0, 10));
        assertFalse(KillAura.shouldQueueBasicBlinkPacket(false, false, false, 0, 10));
    }

    @Test
    void basicNoSlowAppliesOnlyToTheEnforcedBlockingHand() {
        assertTrue(KillAura.shouldBypassBasicBlockSlowdownState(true, true, true, true, true));
        assertFalse(KillAura.shouldBypassBasicBlockSlowdownState(false, true, true, true, true));
        assertFalse(KillAura.shouldBypassBasicBlockSlowdownState(true, false, true, true, true));
        assertFalse(KillAura.shouldBypassBasicBlockSlowdownState(true, true, true, false, true));
        assertFalse(KillAura.shouldBypassBasicBlockSlowdownState(true, true, true, true, false));
    }

    @Test
    void globalKeepSprintCancelsOnlyAuraScopedAttackSlowdown() {
        assertTrue(KillAura.shouldCancelAuraAttackSlowdown(true, true));
        assertFalse(KillAura.shouldCancelAuraAttackSlowdown(false, true));
        assertFalse(KillAura.shouldCancelAuraAttackSlowdown(true, false));
    }
}
