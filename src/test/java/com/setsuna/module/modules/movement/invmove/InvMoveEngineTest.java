package com.setsuna.module.modules.movement.invmove;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvMoveEngineTest {
    @Test
    void movesOnlyInPlayerInventoryAndForcesSprint() {
        FakeContext context = new FakeContext();
        InvMoveEngine engine = new InvMoveEngine(context);
        engine.setEnabled(true);

        assertTrue(engine.isKeyAllowed(InvMoveKey.FORWARD));
        assertFalse(engine.isKeyAllowed(InvMoveKey.SNEAK));
        context.physical.put(InvMoveKey.FORWARD, true);
        engine.tick();
        assertTrue(context.logical.get(InvMoveKey.FORWARD));
        assertTrue(context.logical.get(InvMoveKey.SPRINT));

        context.screen = InvMoveScreen.CHAT;
        engine.tick();
        assertFalse(context.logical.get(InvMoveKey.FORWARD));
        assertFalse(context.logical.get(InvMoveKey.SPRINT));
    }

    @Test
    void preservesInventoryClickCloseSynchronization() {
        FakeContext context = new FakeContext();
        InvMoveEngine engine = new InvMoveEngine(context);
        engine.setEnabled(true);

        engine.onClickSlotPacket(0);
        assertTrue(engine.inventoryClickPending());
        engine.onKeyEvent(InvMoveKey.FORWARD, true);
        assertEquals(1, context.closePackets);
        assertFalse(engine.inventoryClickPending());
        assertEquals(InvMovePacketAction.CANCEL, engine.onCloseInventoryPacket(0));

        engine.onClickSlotPacket(0);
        assertEquals(InvMovePacketAction.PASS, engine.onCloseInventoryPacket(0));
        engine.onClickSlotPacket(0);
        engine.onServerScreenChange();
        assertFalse(engine.inventoryClickPending());
    }

    private static final class FakeContext implements InvMoveContext {
        private boolean available = true;
        private InvMoveScreen screen = InvMoveScreen.INVENTORY;
        private final Map<InvMoveKey, Boolean> physical = new EnumMap<>(InvMoveKey.class);
        private final Map<InvMoveKey, Boolean> logical = new EnumMap<>(InvMoveKey.class);
        private int closePackets;

        @Override public boolean isAvailable() { return available; }
        @Override public InvMoveScreen screen() { return screen; }
        @Override public boolean isPhysicalKeyPressed(InvMoveKey key) {
            return physical.getOrDefault(key, false);
        }
        @Override public void setLogicalKeyPressed(InvMoveKey key, boolean pressed) {
            logical.put(key, pressed);
        }
        @Override public void sendCloseInventoryPacket() { closePackets++; }
    }
}
