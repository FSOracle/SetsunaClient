package com.setsuna.module.modules.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerFinderTest {

    private static final AABB BOX = new AABB(0.0, 20.0, 0.0, 7.0, 25.0, 7.0);

    @Test
    void directSpawnerCandidateUsesSourceConfidenceAndGreenColor() {
        SpawnerFinder.Candidate candidate = candidate(24, 6, true, true, 3, false, 20);

        assertEquals(1.0F, candidate.confidence(), 1.0E-6F);
        assertEquals(new Color(0, 255, 0), candidate.color());
    }

    @Test
    void structuralCandidateKeepsOrangeThreshold() {
        SpawnerFinder.Candidate candidate = candidate(16, 2, false, false, 1, false, 60);

        assertEquals(0.35F, candidate.confidence(), 1.0E-6F);
        assertEquals(new Color(255, 165, 0), candidate.color());
    }

    @Test
    void uncertainFallbackUsesPurpleConfidenceBands() {
        SpawnerFinder.Candidate medium = candidate(20, 0, false, false, 1, true, 60);
        SpawnerFinder.Candidate high = candidate(20, 0, false, false, 2, true, 60);

        assertEquals(0.55F, medium.confidence(), 1.0E-6F);
        assertEquals(new Color(180, 100, 255), medium.color());
        assertEquals(0.70F, high.confidence(), 1.0E-6F);
        assertEquals(new Color(128, 0, 255), high.color());
    }

    @Test
    void depthBonusesMatchSourceThresholds() {
        SpawnerFinder.Candidate shallow = candidate(16, 2, false, false, 1, false, 50);
        SpawnerFinder.Candidate deep = candidate(16, 2, false, false, 1, false, 29);

        assertEquals(0.35F, shallow.confidence(), 1.0E-6F);
        assertEquals(0.45F, deep.confidence(), 1.0E-6F);
    }

    @Test
    void solidCoverThresholdMatchesSource() {
        assertTrue(SpawnerFinder.hasInsufficientSolidCover(4));
        assertFalse(SpawnerFinder.hasInsufficientSolidCover(5));
    }

    @Test
    void scanCeilingIsStrictlyBelowY65() {
        assertTrue(SpawnerFinder.isBelowScanCeiling(64));
        assertFalse(SpawnerFinder.isBelowScanCeiling(65));
    }

    private static SpawnerFinder.Candidate candidate(int total, int mossy, boolean spawner,
                                                       boolean chest, int embeddedStone,
                                                       boolean uncertain, int y) {
        return new SpawnerFinder.Candidate(BOX, new BlockPos(3, y, 3), total, mossy,
                spawner, chest, embeddedStone, uncertain);
    }
}
