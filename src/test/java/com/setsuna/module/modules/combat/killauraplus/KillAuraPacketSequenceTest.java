package com.setsuna.module.modules.combat.killauraplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraPacketSequenceTest {
    @Test
    void keepsUseRotationStableUntilMovement() {
        KillAuraPacketSequence sequence = new KillAuraPacketSequence();

        assertEquals(
                new KillAuraPacketSequence.UseRotation(35.0F, -12.0F),
                sequence.beginUseAction(35.0F, -12.0F, false));
        assertEquals(
                new KillAuraPacketSequence.UseRotation(35.0F, -12.0F),
                sequence.beginUseAction(80.0F, 20.0F, false));
        assertTrue(sequence.isUseActionPending());

        sequence.onMovementPacket();

        assertFalse(sequence.isUseActionPending());
        assertEquals(
                new KillAuraPacketSequence.UseRotation(80.0F, 20.0F),
                sequence.beginUseAction(80.0F, 20.0F, false));
    }

    @Test
    void latchesEntityInteractionSuppressionUntilMovement() {
        KillAuraPacketSequence sequence = new KillAuraPacketSequence();

        sequence.beginUseAction(35.0F, -12.0F, true);
        sequence.beginUseAction(80.0F, 20.0F, false);
        assertTrue(sequence.shouldSuppressEntityInteraction());

        sequence.onMovementPacket();

        assertFalse(sequence.shouldSuppressEntityInteraction());
        sequence.beginUseAction(80.0F, 20.0F, false);
        assertFalse(sequence.shouldSuppressEntityInteraction());
    }
}
