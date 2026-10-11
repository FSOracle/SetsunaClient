package com.setsuna.module.modules.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoWaterSafetyTest {

    @Test
    void autoExtinguishDefaultsOnAndCanBeDisabled() {
        assertEquals(Boolean.TRUE, AutoMLG.INSTANCE.settings().stream()
                .filter(setting -> setting.name().equals("Auto Extinguish"))
                .findFirst()
                .orElseThrow()
                .defaultValue());
        assertEquals(72, AutoMLG.INSTANCE.settings().stream()
                .filter(setting -> setting.name().equals("Extinguish Key"))
                .findFirst()
                .orElseThrow()
                .defaultValue());
        assertTrue(AutoMLG.shouldAutoExtinguish(true, true));
        assertFalse(AutoMLG.shouldAutoExtinguish(false, true));
        assertFalse(AutoMLG.shouldAutoExtinguish(true, false));
        assertTrue(AutoMLG.shouldAutoExtinguish(true, true, true));
        assertFalse(AutoMLG.shouldAutoExtinguish(true, true, false));
    }

    @Test
    void antiWebRetriesOnlyAfterTheConfiguredInterval() {
        assertTrue(AntiWeb.shouldAttemptUse(false, 0, 5));
        assertFalse(AntiWeb.shouldAttemptUse(true, 4, 5));
        assertTrue(AntiWeb.shouldAttemptUse(true, 5, 5));
    }

    @Test
    void antiWebDoesNotTreatThePlacementInventoryUpdateAsACompletedPickup() {
        assertFalse(AntiWeb.isPickupConfirmed(false, true));
        assertFalse(AntiWeb.isPickupConfirmed(true, false));
        assertTrue(AntiWeb.isPickupConfirmed(true, true));
    }

    @Test
    void antiWebRestoresOnlyAValidSavedHotbarSlot() {
        assertEquals(4, AntiWeb.slotToRestore(4, true));
        assertEquals(-1, AntiWeb.slotToRestore(4, false));
        assertEquals(-1, AntiWeb.slotToRestore(-1, true));
        assertEquals(-1, AntiWeb.slotToRestore(9, true));
    }

    @Test
    void antiWebBlocksCombatOnlyWhileItsInteractionCycleIsActive() {
        assertFalse(AntiWeb.shouldBlockOtherActions(null));
        assertFalse(AntiWeb.shouldBlockOtherActions(AntiWeb.Phase.Idle));
        assertTrue(AntiWeb.shouldBlockOtherActions(AntiWeb.Phase.Placing));
        assertTrue(AntiWeb.shouldBlockOtherActions(AntiWeb.Phase.Recycling));
    }
}
