package com.setsuna.util.player;

import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeypixelHealthSourceTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void cachesBothHeypixelHealthObjectivesAndIgnoresOtherScores() {
        HeypixelHealthSource source = new HeypixelHealthSource();

        source.accept(score("Enemy", "BeLoWhEaLtH", 17), "Self");
        source.accept(score("Other", "health", 0), "Self");
        source.accept(score("Ignored", "kills", 99), "Self");
        source.accept(score("Self", "health", 12), "Self");

        assertEquals(17.0F, source.cachedHealth("Enemy"));
        assertEquals(1.0F, source.cachedHealth("Other"));
        assertNull(source.cachedHealth("Ignored"));
        assertNull(source.cachedHealth("Self"));
    }

    @Test
    void resetAndWorldClearRemoveCachedHealth() {
        HeypixelHealthSource source = new HeypixelHealthSource();
        source.accept(score("Enemy", "health", 14), null);
        source.accept(new ClientboundResetScorePacket("Enemy", "health"), null);
        assertNull(source.cachedHealth("Enemy"));

        source.accept(score("Enemy", "belowHealth", 9), null);
        source.clear();
        assertNull(source.cachedHealth("Enemy"));
    }

    @Test
    void onlyHealthAboveTwentyUsesTheSourcePacketGuard() {
        assertFalse(HeypixelHealthSource.shouldCancelLocalHealth(20.0F));
        assertTrue(HeypixelHealthSource.shouldCancelLocalHealth(20.01F));
    }

    private static ClientboundSetScorePacket score(String owner, String objective, int value) {
        return new ClientboundSetScorePacket(owner, objective, value, Optional.empty(), Optional.empty());
    }
}
