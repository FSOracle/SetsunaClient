package com.setsuna.module.modules.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutoHeadTest {

    @Test
    void recognizesOrdinaryHeadsByEnglishOrChineseDisplayName() {
        assertTrue(AutoHead.isOrdinaryHeadName("MaherMiner's Avaricious Head"));
        assertTrue(AutoHead.isOrdinaryHeadName("HEAD OF THE FALLEN"));
        assertTrue(AutoHead.isOrdinaryHeadName("贪婪之头"));
    }

    @Test
    void excludesGoldenHeadsAndNamesWithoutAHeadMarker() {
        assertFalse(AutoHead.isOrdinaryHeadName("Golden Head"));
        assertFalse(AutoHead.isOrdinaryHeadName("Gold Head"));
        assertFalse(AutoHead.isOrdinaryHeadName("金头"));
        assertFalse(AutoHead.isOrdinaryHeadName("Regeneration Token"));
        assertFalse(AutoHead.isOrdinaryHeadName(null));
    }

    @Test
    void staysIdleAboveTriggerHealth() {
        assertEquals(AutoHead.HeadMode.NONE, AutoHead.chooseMode(12.5F, 12.0, 6.0));
    }

    @Test
    void prefersGoldenHeadsAtNormalLowHealth() {
        assertEquals(AutoHead.HeadMode.GOLDEN_FIRST, AutoHead.chooseMode(8.0F, 12.0, 6.0));
    }

    @Test
    void requiresGoldenHeadsAtCriticalHealthAndClampsMisorderedThresholds() {
        assertEquals(AutoHead.HeadMode.GOLDEN_ONLY, AutoHead.chooseMode(6.0F, 12.0, 6.0));
        assertEquals(AutoHead.HeadMode.GOLDEN_ONLY, AutoHead.chooseMode(10.0F, 10.0, 20.0));
    }

    @Test
    void prioritizesGoldenHeadsThenTheOffhandPlayerHead() {
        assertEquals(AutoHead.HeadLocation.OFFHAND_GOLDEN,
                AutoHead.chooseHeadLocation(AutoHead.HeadMode.GOLDEN_FIRST,
                        true, true, true, true));
        assertEquals(AutoHead.HeadLocation.HOTBAR_GOLDEN,
                AutoHead.chooseHeadLocation(AutoHead.HeadMode.GOLDEN_FIRST,
                        false, true, true, true));
        assertEquals(AutoHead.HeadLocation.OFFHAND_PLAYER,
                AutoHead.chooseHeadLocation(AutoHead.HeadMode.GOLDEN_FIRST,
                        false, false, true, true));
        assertEquals(AutoHead.HeadLocation.NONE,
                AutoHead.chooseHeadLocation(AutoHead.HeadMode.GOLDEN_ONLY,
                        false, false, true, true));
    }
}
