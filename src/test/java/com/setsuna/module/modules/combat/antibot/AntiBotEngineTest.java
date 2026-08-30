package com.setsuna.module.modules.combat.antibot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiBotEngineTest {
    @Test
    void correlatesPlayerListCandidatesWithSpawnPackets() {
        AntiBotEngine engine = new AntiBotEngine();
        UUID candidate = UUID.randomUUID();
        UUID creative = UUID.randomUUID();
        UUID decorated = UUID.randomUUID();

        engine.handlePlayerListAdd(List.of(
                new AntiBotPlayerListEntry(candidate, true, 0, CombatGameMode.SURVIVAL),
                new AntiBotPlayerListEntry(creative, true, 0, CombatGameMode.CREATIVE),
                new AntiBotPlayerListEntry(decorated, true, 1, CombatGameMode.SURVIVAL)
        ), 100L);

        assertEquals(Set.of(candidate), engine.pendingPlayerSpawns().keySet());
        assertTrue(engine.handlePlayerSpawn(candidate, 42));
        assertFalse(engine.handlePlayerSpawn(candidate, 43));
        assertTrue(engine.isBot(new CombatPlayerIdentity(42, candidate), Set.of(candidate)));
        assertEquals(1, engine.handleEntitiesDestroyed(List.of(7, 42)));
        assertTrue(engine.botEntityIds().isEmpty());
    }

    @Test
    void preservesTheExactExpiryBoundaryAndNetworkFallback() {
        AntiBotEngine engine = new AntiBotEngine();
        UUID listed = UUID.randomUUID();
        UUID missing = UUID.randomUUID();

        engine.handlePlayerListAdd(List.of(
                new AntiBotPlayerListEntry(listed, true, 0, CombatGameMode.SURVIVAL)
        ), 100L);
        engine.tick(600L, true, 10);
        assertEquals(1, engine.pendingPlayerSpawns().size());
        engine.tick(601L, true, 10);
        assertTrue(engine.pendingPlayerSpawns().isEmpty());

        assertFalse(engine.isBot(new CombatPlayerIdentity(1, listed), Set.of(listed)));
        assertTrue(engine.isBot(new CombatPlayerIdentity(2, missing), Set.of(listed)));
        engine.tick(700L, true, 1);
        assertTrue(engine.botEntityIds().isEmpty());
    }
}
