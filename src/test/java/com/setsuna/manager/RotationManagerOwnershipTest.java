package com.setsuna.manager;

import com.setsuna.event.events.PacketEvent;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationManagerOwnershipTest {

    @Test
    void onlyTheCurrentOwnerCanReleaseAnActiveRotation() throws ReflectiveOperationException {
        RotationManager manager = RotationManager.INSTANCE;
        Field ownerField = RotationManager.class.getDeclaredField("rotationOwner");
        Field activeField = RotationManager.class.getDeclaredField("active");
        ownerField.setAccessible(true);
        activeField.setAccessible(true);

        Object owner = new Object();
        Object other = new Object();
        try {
            ownerField.set(manager, owner);
            activeField.setBoolean(manager, true);

            assertFalse(manager.releaseRotations(other));
            assertTrue(manager.isActive());
            assertTrue(manager.releaseRotations(owner));
            assertFalse(manager.isActive());
        } finally {
            manager.setActive(false);
        }
    }

    @Test
    void serverCorrectionInvalidatesThePreviouslySentRotation() throws ReflectiveOperationException {
        RotationManager manager = RotationManager.INSTANCE;
        Field sentField = RotationManager.class.getDeclaredField("hasSentRotations");
        Field correctionField = RotationManager.class.getDeclaredField("s08");
        Method receiveMethod = RotationManager.class.getDeclaredMethod(
                "onPacketReceive", PacketEvent.Receive.class);
        sentField.setAccessible(true);
        correctionField.setAccessible(true);
        receiveMethod.setAccessible(true);

        boolean originalSent = sentField.getBoolean(manager);
        boolean originalCorrection = correctionField.getBoolean(manager);

        try {
            sentField.setBoolean(manager, true);
            assertTrue(manager.hasSentRotations());

            receiveMethod.invoke(manager, new PacketEvent.Receive(
                    new ClientboundPlayerRotationPacket(90.0F, false, 15.0F, false)));
            assertFalse(manager.hasSentRotations());
        } finally {
            sentField.setBoolean(manager, originalSent);
            correctionField.setBoolean(manager, originalCorrection);
        }
    }
}
