package com.setsuna.ui.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudBlurPolicyTest {
    @Test
    void sharedGlassOverridesLocalStrengthOnlyForVisibleBackgrounds() {
        assertEquals(8.0F, HudBlurPolicy.strength(true, 8, true, 0xAF101010, true, 3));
        assertEquals(0.0F, HudBlurPolicy.strength(true, 8, false, 0xAF101010, false, 3));
        assertEquals(0.0F, HudBlurPolicy.strength(true, 8, true, 0x00101010, false, 3));
        assertEquals(3.0F, HudBlurPolicy.strength(true, 8, false, 0xAF101010, true, 3));
        assertEquals(3.0F, HudBlurPolicy.strength(false, 8, true, 0xAF101010, true, 3));
        assertEquals(0.0F, HudBlurPolicy.strength(false, 8, true, 0xAF101010, false, 3));
    }

    @Test
    void glassTintPreservesColorAndExistingTransparency() {
        assertEquals(0xAC102030, HudBlurPolicy.surfaceColor(true, 0xFF102030));
        assertEquals(0x40102030, HudBlurPolicy.surfaceColor(true, 0x40102030));
        assertEquals(0x00102030, HudBlurPolicy.surfaceColor(true, 0x00102030));
        assertEquals(0xFF102030, HudBlurPolicy.surfaceColor(false, 0xFF102030));
    }
}
