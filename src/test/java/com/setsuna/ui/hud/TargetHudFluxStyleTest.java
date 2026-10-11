package com.setsuna.ui.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetHudFluxStyleTest {

    @Test
    void contentWidthKeepsTheFluxMinimumAndPreventsTextOverlap() {
        assertEquals(75.0F, TargetHud.fluxContentWidth(20.0F, 24.0F, 18.0F));
        assertEquals(118.0F, TargetHud.fluxContentWidth(80.0F, 70.0F, 28.0F));
        assertEquals(125.0F, TargetHud.fluxContentWidth(70.0F, 100.0F, 20.0F));
    }

    @Test
    void healthColorMatchesTheFluxRedYellowGreenRamp() {
        assertEquals(0xFFFF4D4D, TargetHud.fluxHealthColor(0.0F));
        assertEquals(0xFFFFFF4D, TargetHud.fluxHealthColor(0.5F));
        assertEquals(0xFF4DFF4D, TargetHud.fluxHealthColor(1.0F));
        assertEquals(0xFFFFC44D, TargetHud.fluxHealthColor(0.337F));
        assertEquals(TargetHud.fluxHealthColor(0.0F), TargetHud.fluxHealthColor(-1.0F));
        assertEquals(TargetHud.fluxHealthColor(1.0F), TargetHud.fluxHealthColor(2.0F));
    }
}
