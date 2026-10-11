package com.setsuna.module.modules.misc;

import com.setsuna.module.Category;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisablerTest {

    @Test
    void preservesNiloreDefaultsAndCategory() {
        Disabler disabler = Disabler.INSTANCE;
        assertEquals(Category.MISC, disabler.category());
        assertEquals(14, disabler.settings().size());
        assertTrue(booleanSetting("Grim Bad PacketsA"));
        assertTrue(booleanSetting("Grim Duplicate RotPlace"));
        assertTrue(booleanSetting("ACA Fast Switch"));
        assertTrue(booleanSetting("ACA Aim Step"));
        assertTrue(booleanSetting("ACA Perfect Rotation"));
        assertTrue(booleanSetting("Themis Blink"));
        assertFalse(booleanSetting("Grim Bad PacketsF"));
        assertFalse(booleanSetting("ACA Inventory Frequency"));
    }

    @Test
    void clampsHotbarAndBuildsIntermediateSwitchPath() {
        assertEquals(0, Disabler.clampHotbarSlot(-3));
        assertEquals(8, Disabler.clampHotbarSlot(20));
        assertEquals(4, Disabler.clampHotbarSlot(4));
        assertEquals(List.of(2, 3, 4), Disabler.intermediateSlots(1, 5));
        assertEquals(List.of(4, 3, 2), Disabler.intermediateSlots(5, 1));
        assertTrue(Disabler.intermediateSlots(0, 8).isEmpty());
        assertTrue(Disabler.intermediateSlots(4, 5).isEmpty());
    }

    @Test
    void aimStepPerturbsOnlyAStuckAxis() {
        assertTrue(Disabler.isAimStepRotation(20.0F, 10.0F, 20.0F, 15.0F));
        assertTrue(Disabler.isAimStepRotation(20.0F, 10.0F, 30.0F, 10.0F));
        assertFalse(Disabler.isAimStepRotation(20.0F, 10.0F, 30.0F, 15.0F));

        float[] result = Disabler.applyAimStep(
                20.0F, 10.0F, 20.0F, 15.0F, new Random(7L));
        assertNotEquals(20.0F, result[0]);
        assertEquals(15.0F, result[1]);
    }

    @Test
    void perfectRotationAddsNiloreJitterToKnownSteps() {
        float[] result = Disabler.applyPerfectRotation(
                10.0F, 10.0F, 21.25F, 15.625F, new Random(11L));
        assertNotEquals(21.25F, result[0]);
        assertNotEquals(15.625F, result[1]);
    }

    private static boolean booleanSetting(String name) {
        return Disabler.INSTANCE.settings().stream()
                .filter(setting -> setting.name().equals(name))
                .findFirst()
                .orElseThrow()
                .toJson()
                .getAsBoolean();
    }
}
