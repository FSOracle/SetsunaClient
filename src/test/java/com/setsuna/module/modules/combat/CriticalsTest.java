package com.setsuna.module.modules.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalsTest {

    @Test
    void exposesTheHopliteMode() {
        assertEquals(Criticals.Mode.Hoplite, Criticals.Mode.valueOf("Hoplite"));
    }

    @Test
    void hopliteMatchesClapAirborneParticleGate() {
        assertTrue(Criticals.shouldSpawnHopliteCritical(true, false));
        assertFalse(Criticals.shouldSpawnHopliteCritical(true, true));
        assertFalse(Criticals.shouldSpawnHopliteCritical(false, false));
    }
}
