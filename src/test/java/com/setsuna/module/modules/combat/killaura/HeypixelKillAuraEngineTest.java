package com.setsuna.module.modules.combat.killaura;

import com.setsuna.util.rotation.Rot2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeypixelKillAuraEngineTest {

    @Test
    void targetAttackWindowMatchesOpenOpal() {
        assertTrue(HeypixelKillAuraEngine.isTrackedAttackAvailable(
                false, 0L, 20.0D, 1.0D, 0.0D));
        assertFalse(HeypixelKillAuraEngine.isTrackedAttackAvailable(
                true, 469L, 20.0D, 4.0D, 4.0D));
        assertTrue(HeypixelKillAuraEngine.isTrackedAttackAvailable(
                true, 470L, 20.0D, 4.0D, 4.0D));
        assertTrue(HeypixelKillAuraEngine.isTrackedAttackAvailable(
                true, 1L, 4.0D, 4.0D, 4.0D));
        assertTrue(HeypixelKillAuraEngine.isTrackedAttackAvailable(
                true, 1L, 20.0D, 5.0D, 4.0D));
    }

    @Test
    void heypixelBypassUsesTwentyPercentJitter() {
        assertEquals(80L, HeypixelKillAuraEngine.randomizedBypassDelay(10, 0.0D));
        assertEquals(100L, HeypixelKillAuraEngine.randomizedBypassDelay(10, 0.5D));
        assertEquals(120L, HeypixelKillAuraEngine.randomizedBypassDelay(10, 1.0D));
    }

    @Test
    void rotationStepDistributesMaxAngleAndAppliesSensitivityGcd() {
        Rot2f straight = HeypixelKillAuraEngine.stepRotation(
                new Rot2f(0.0F, 0.0F),
                new Rot2f(90.0F, 0.0F),
                30.0F,
                0.5D
        );
        assertEquals(30.0F, straight.getYaw(), 1.0E-4F);
        assertEquals(0.0F, straight.getPitch(), 1.0E-4F);

        Rot2f diagonal = HeypixelKillAuraEngine.stepRotation(
                new Rot2f(0.0F, 0.0F),
                new Rot2f(90.0F, 90.0F),
                90.0F,
                0.5D
        );
        assertEquals(63.6F, diagonal.getYaw(), 1.0E-4F);
        assertEquals(63.6F, diagonal.getPitch(), 1.0E-4F);
    }
}
