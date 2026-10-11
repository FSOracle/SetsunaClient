package com.setsuna.module.modules.movement.invmove;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InvMoveEngineTest {
    @Test
    void routesRawInputByModeScreenAndSneakSetting() {
        FakeContext context = new FakeContext();
        InvMoveEngine engine = enabledEngine(context);

        engine.onKeyEvent(InvMoveKey.FORWARD, true);
        engine.onKeyEvent(InvMoveKey.SNEAK, true);
        assertTrue(engine.suppliesInput(InvMoveKey.FORWARD));
        assertFalse(engine.suppliesInput(InvMoveKey.SNEAK));

        engine.setSneakAllowed(true);
        assertTrue(engine.suppliesInput(InvMoveKey.SNEAK));

        context.screen = InvMoveScreen.HANDLED_OTHER;
        assertFalse(engine.suppliesInput(InvMoveKey.FORWARD));
        engine.setMode(InvMoveMode.NORMAL);
        assertTrue(engine.suppliesInput(InvMoveKey.FORWARD));

        context.screen = InvMoveScreen.CHAT;
        assertFalse(engine.suppliesInput(InvMoveKey.FORWARD));
        context.screen = InvMoveScreen.CREATIVE_INVENTORY;
        assertFalse(engine.suppliesInput(InvMoveKey.FORWARD));

        engine.setMode(InvMoveMode.LEGIT);
        context.screen = InvMoveScreen.OTHER;
        assertTrue(engine.suppliesInput(InvMoveKey.FORWARD));
        context.screen = InvMoveScreen.NONE;
        assertFalse(engine.suppliesInput(InvMoveKey.FORWARD));
    }

    @Test
    void legitModeBlocksSlotActionsOnlyUntilInputIsSafe() {
        FakeContext context = new FakeContext();
        InvMoveEngine engine = enabledEngine(context);
        engine.tick();
        assertTrue(engine.interactionSafe());
        assertFalse(engine.blocksInventoryClick());

        engine.onKeyEvent(InvMoveKey.SNEAK, true);
        engine.tick();
        assertFalse(engine.blocksInventoryClick());

        engine.onKeyEvent(InvMoveKey.SPRINT, true);
        engine.tick();
        assertFalse(engine.blocksInventoryClick());

        engine.onKeyEvent(InvMoveKey.JUMP, true);
        engine.tick();
        assertTrue(engine.blocksInventoryClick());
        engine.onKeyEvent(InvMoveKey.JUMP, false);

        context.playerMoving = true;
        engine.onKeyEvent(InvMoveKey.FORWARD, true);
        engine.tick();
        assertTrue(engine.blocksInventoryClick());

        engine.setMode(InvMoveMode.NORMAL);
        assertFalse(engine.blocksInventoryClick());
    }

    @Test
    void delaysInventoryCloseUntilVanillaHasStoppedMovement() {
        FakeContext context = new FakeContext();
        InvMoveEngine engine = enabledEngine(context);
        context.engine = engine;
        context.playerMoving = true;
        context.playerSprinting = true;
        engine.onKeyEvent(InvMoveKey.FORWARD, true);
        engine.tick();

        assertTrue(engine.interceptInventoryClose());
        assertTrue(engine.pendingClose());
        assertFalse(engine.suppliesInput(InvMoveKey.FORWARD));

        engine.onKeyEvent(InvMoveKey.FORWARD, false);
        context.playerMoving = false;
        context.playerSprinting = false;
        engine.tick();

        assertEquals(1, context.closeCalls);
        assertFalse(context.closeWasIntercepted);
        assertFalse(engine.pendingClose());
        assertFalse(engine.interceptInventoryClose());
    }

    @Test
    void resetsPendingCloseOnScreenChangeAndClearsKeysOnDisable() {
        FakeContext context = new FakeContext();
        InvMoveEngine engine = enabledEngine(context);
        context.playerMoving = true;
        engine.onKeyEvent(InvMoveKey.FORWARD, true);
        engine.tick();
        assertTrue(engine.interceptInventoryClose());

        context.screen = InvMoveScreen.OTHER;
        context.screenIdentity = new Object();
        engine.tick();
        assertFalse(engine.pendingClose());

        context.screen = InvMoveScreen.INVENTORY;
        engine.setEnabled(false);
        engine.setEnabled(true);
        assertFalse(engine.suppliesInput(InvMoveKey.FORWARD));
    }

    private static InvMoveEngine enabledEngine(FakeContext context) {
        InvMoveEngine engine = new InvMoveEngine(context);
        engine.setMode(InvMoveMode.LEGIT);
        engine.setEnabled(true);
        return engine;
    }

    private static final class FakeContext implements InvMoveContext {
        private boolean available = true;
        private InvMoveScreen screen = InvMoveScreen.INVENTORY;
        private Object screenIdentity = new Object();
        private boolean playerMoving;
        private boolean playerSprinting;
        private int closeCalls;
        private boolean closeWasIntercepted;
        private InvMoveEngine engine;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public InvMoveScreen screen() {
            return screen;
        }

        @Override
        public Object screenIdentity() {
            return screenIdentity;
        }

        @Override
        public boolean isPlayerMoving() {
            return playerMoving;
        }

        @Override
        public boolean isPlayerSprinting() {
            return playerSprinting;
        }

        @Override
        public void closeInventory() {
            closeCalls++;
            closeWasIntercepted = engine != null && engine.interceptInventoryClose();
        }
    }
}
