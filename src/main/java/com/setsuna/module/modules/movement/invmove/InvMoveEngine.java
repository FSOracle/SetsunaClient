package com.setsuna.module.modules.movement.invmove;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Minecraft-independent port of the D:\1 InventoryMove input and close guards. */
public final class InvMoveEngine {
    private static final InvMoveKey[] DIRECTIONAL_KEYS = {
            InvMoveKey.FORWARD, InvMoveKey.LEFT, InvMoveKey.BACK, InvMoveKey.RIGHT
    };

    private final InvMoveContext context;
    private final Map<InvMoveKey, Boolean> physicalDown = new EnumMap<>(InvMoveKey.class);
    private InvMoveMode mode = InvMoveMode.LEGIT;
    private Object trackedInventory;
    private boolean enabled;
    private boolean sneakAllowed;
    private boolean interactionSafe;
    private boolean pendingClose;
    private boolean bypassCloseGuard;

    public InvMoveEngine(InvMoveContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        releaseManagedKeys();
        resetLegitState();
    }

    public void setMode(InvMoveMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public void setSneakAllowed(boolean sneakAllowed) {
        this.sneakAllowed = sneakAllowed;
    }

    public void onKeyEvent(InvMoveKey key, boolean pressed) {
        physicalDown.put(Objects.requireNonNull(key, "key"), pressed);
        if (pressed) {
            interactionSafe = false;
        }
    }

    /** Updates Legit-mode interaction state after the local player has ticked. */
    public void tick() {
        if (!enabled) {
            releaseManagedKeys();
            resetLegitState();
            return;
        }

        if (mode != InvMoveMode.LEGIT || context.screen() != InvMoveScreen.INVENTORY) {
            if (trackedInventory != null) {
                resetLegitState();
            }
            return;
        }

        Object inventory = context.screenIdentity();
        if (trackedInventory != inventory) {
            trackedInventory = inventory;
            interactionSafe = false;
            pendingClose = false;
        }

        if (!context.isAvailable()) return;
        interactionSafe = !context.isPlayerMoving() && !context.isPlayerSprinting();

        if (pendingClose
                && interactionSafe
                && context.screen() == InvMoveScreen.INVENTORY
                && context.screenIdentity() == inventory) {
            pendingClose = false;
            bypassCloseGuard = true;
            try {
                context.closeInventory();
            } finally {
                bypassCloseGuard = false;
            }
        }
    }

    /** Supplies only raw physical input; no KeyMapping state is mutated. */
    public boolean suppliesInput(InvMoveKey key) {
        return enabled
                && context.screen() != InvMoveScreen.NONE
                && !pendingClose
                && physicalDown.getOrDefault(key, false)
                && shouldHandle(key);
    }

    public boolean blocksInventoryClick() {
        if (!enabled || mode != InvMoveMode.LEGIT || context.screen() != InvMoveScreen.INVENTORY) {
            return false;
        }
        if (!interactionSafe) return true;

        for (Map.Entry<InvMoveKey, Boolean> entry : physicalDown.entrySet()) {
            InvMoveKey key = entry.getKey();
            if (entry.getValue()
                    && key != InvMoveKey.SNEAK
                    && key != InvMoveKey.SPRINT
                    && shouldHandle(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean interceptInventoryClose() {
        if (bypassCloseGuard
                || !enabled
                || mode != InvMoveMode.LEGIT
                || context.screen() != InvMoveScreen.INVENTORY) {
            return false;
        }
        if (interactionSafe && !hasPhysicalMovement()) {
            return false;
        }

        pendingClose = true;
        interactionSafe = false;
        return true;
    }

    public void releaseManagedKeys() {
        physicalDown.clear();
    }

    public boolean interactionSafe() {
        return interactionSafe;
    }

    public boolean pendingClose() {
        return pendingClose;
    }

    private boolean hasPhysicalMovement() {
        for (InvMoveKey key : DIRECTIONAL_KEYS) {
            if (physicalDown.getOrDefault(key, false)) return true;
        }
        return false;
    }

    private boolean shouldHandle(InvMoveKey key) {
        InvMoveScreen screen = context.screen();
        if (!enabled || screen == InvMoveScreen.CHAT || screen == InvMoveScreen.CREATIVE_INVENTORY) {
            return false;
        }
        if (key == InvMoveKey.SNEAK && !sneakAllowed) {
            return false;
        }

        return mode != InvMoveMode.LEGIT
                || screen == InvMoveScreen.INVENTORY
                || screen != InvMoveScreen.HANDLED_OTHER;
    }

    private void resetLegitState() {
        trackedInventory = null;
        interactionSafe = false;
        pendingClose = false;
        bypassCloseGuard = false;
    }
}
