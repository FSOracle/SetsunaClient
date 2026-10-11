package com.setsuna.module.modules.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiloreScaffoldEngineDecisionTest {

    @Test
    void regularTellyPlacementStillWaitsForAirTicksAndPlaceDelay() {
        assertTrue(NiloreScaffoldEngine.shouldDelayTellyPlacement(
                true, false, 0, 1, 2, 2));
        assertTrue(NiloreScaffoldEngine.shouldDelayTellyPlacement(
                true, false, 1, 1, 1, 2));
        assertFalse(NiloreScaffoldEngine.shouldDelayTellyPlacement(
                true, false, 1, 1, 2, 2));
    }

    @Test
    void clutchRescueBypassesTellyTimingGate() {
        assertFalse(NiloreScaffoldEngine.shouldDelayTellyPlacement(
                true, true, 0, 3, 0, 20));
    }

    @Test
    void regularTellyAirborneUpPlacementRemainsBlocked() {
        assertTrue(NiloreScaffoldEngine.shouldBlockAirborneUpPlacement(
                true, false, true, false, false, false));
    }

    @Test
    void clutchRescueAllowsTellyAirborneUpPlacement() {
        assertFalse(NiloreScaffoldEngine.shouldBlockAirborneUpPlacement(
                true, false, true, false, false, true));
    }
}
