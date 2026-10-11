package com.setsuna.module.modules.combat;

import com.google.gson.JsonPrimitive;
import com.setsuna.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeLagDecisionTest {

    @Test
    void incomingHealthAndLocalNonZeroVelocityForceAFlush() {
        assertTrue(FakeLag.shouldFlushOutgoing(
                new ClientboundSetHealthPacket(20.0F, 20, 5.0F), 7));
        assertTrue(FakeLag.shouldFlushOutgoing(
                new ClientboundSetEntityMotionPacket(7, new Vec3(0.2, 0.0, 0.0)), 7));
        assertFalse(FakeLag.shouldFlushOutgoing(
                new ClientboundSetEntityMotionPacket(8, new Vec3(0.2, 0.0, 0.0)), 7));
        assertFalse(FakeLag.shouldFlushOutgoing(
                new ClientboundSetEntityMotionPacket(7, Vec3.ZERO), 7));
    }

    @Test
    void configuredInteractionGroupsMatchTheSourcePacketWhitelist() {
        FakeLag fakeLag = FakeLag.INSTANCE;
        Setting<?> entityInteract = setting(fakeLag, "Entity Interact");
        Setting<?> action = setting(fakeLag, "Action");
        try {
            assertTrue(fakeLag.matchesConfiguredFlush(
                    new ServerboundSwingPacket(InteractionHand.MAIN_HAND)));
            assertTrue(fakeLag.matchesConfiguredFlush(new ServerboundAttackPacket(7)));
            assertTrue(fakeLag.matchesConfiguredFlush(
                    new ServerboundTeleportToEntityPacket(UUID.randomUUID())));
            assertTrue(fakeLag.matchesConfiguredFlush(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    BlockPos.ZERO,
                    Direction.DOWN)));

            entityInteract.fromJson(new JsonPrimitive(false));
            action.fromJson(new JsonPrimitive(false));
            assertFalse(fakeLag.matchesConfiguredFlush(
                    new ServerboundSwingPacket(InteractionHand.MAIN_HAND)));
            assertTrue(fakeLag.matchesConfiguredFlush(new ServerboundAttackPacket(7)));
            assertFalse(fakeLag.matchesConfiguredFlush(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    BlockPos.ZERO,
                    Direction.DOWN)));
        } finally {
            entityInteract.reset();
            action.reset();
        }
    }

    @Test
    void squaredDistanceUsesTheNearestPointOnTheEntityBox() {
        AABB box = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        assertEquals(0.0, FakeLag.squaredBoxDistance(box, new Vec3(0.5, 0.5, 0.5)));
        assertEquals(8.0, FakeLag.squaredBoxDistance(box, new Vec3(3.0, 0.5, -2.0)));
    }

    private static Setting<?> setting(FakeLag module, String name) {
        return module.settings().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
