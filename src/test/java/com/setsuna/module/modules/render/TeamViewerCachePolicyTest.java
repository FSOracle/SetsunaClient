package com.setsuna.module.modules.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamViewerCachePolicyTest {

    @Test
    void onlyDifferentWorldReferencesClearTheCache() {
        Object firstWorld = new Object();
        Object secondWorld = new Object();

        assertTrue(TeamViewerCachePolicy.shouldClear(null, firstWorld));
        assertTrue(TeamViewerCachePolicy.shouldClear(firstWorld, null));
        assertFalse(TeamViewerCachePolicy.shouldClear(null, null));
        assertFalse(TeamViewerCachePolicy.shouldClear(firstWorld, firstWorld));
        assertTrue(TeamViewerCachePolicy.shouldClear(firstWorld, secondWorld));
    }
}
