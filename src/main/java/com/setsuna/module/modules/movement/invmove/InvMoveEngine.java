package com.setsuna.module.modules.movement.invmove;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Minecraft-independent copy of Clap's InvMove state machine. */
public final class InvMoveEngine {
    private static final InvMoveKey[] MOVEMENT_KEYS = {
            InvMoveKey.FORWARD, InvMoveKey.LEFT, InvMoveKey.BACK,
            InvMoveKey.RIGHT, InvMoveKey.JUMP, InvMoveKey.SNEAK
    };

    private final InvMoveContext context;
    private final Map<InvMoveKey, Boolean> eventKeyState = new EnumMap<>(InvMoveKey.class);
    private boolean enabled;
    private boolean inventoryClickPending;
    private boolean sprintWasForced;

    public InvMoveEngine(InvMoveContext context) {
        this.context = Objects.requireNonNull(context, "context");
        for (InvMoveKey key : MOVEMENT_KEYS) {
            eventKeyState.put(key, false);
        }
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        resetState();
    }

    public boolean isKeyAllowed(InvMoveKey key) {
        if (!enabled || !context.isAvailable()) {
            return false;
        }
        return switch (context.screen()) {
            case NONE -> true;
            case INVENTORY -> key != InvMoveKey.SNEAK;
            case CHAT, HANDLED_OTHER, OTHER -> false;
        };
    }

    public boolean effectivePressed(InvMoveKey key, boolean vanillaPressed) {
        return isKeyAllowed(key)
                && (vanillaPressed
                || context.isPhysicalKeyPressed(key)
                || eventKeyState.getOrDefault(key, false));
    }

    public void onKeyEvent(InvMoveKey key, boolean pressed) {
        if (eventKeyState.containsKey(key)) {
            eventKeyState.put(key, isKeyAllowed(key) && pressed);
        }
        if (inventoryClickPending && pressed && isKeyAllowed(key)) {
            context.sendCloseInventoryPacket();
            inventoryClickPending = false;
        }
    }

    public void tick() {
        if (!enabled || !context.isAvailable()) {
            resetState();
            return;
        }
        for (InvMoveKey key : MOVEMENT_KEYS) {
            context.setLogicalKeyPressed(key, effectivePressed(key, false));
        }
        boolean moving = effectivePressed(InvMoveKey.FORWARD, false)
                || effectivePressed(InvMoveKey.BACK, false)
                || effectivePressed(InvMoveKey.LEFT, false)
                || effectivePressed(InvMoveKey.RIGHT, false);
        boolean sneak = effectivePressed(InvMoveKey.SNEAK, false);
        if (context.screen() == InvMoveScreen.INVENTORY && moving && !sneak) {
            context.setLogicalKeyPressed(InvMoveKey.SPRINT, true);
            sprintWasForced = true;
        } else if (sprintWasForced) {
            context.setLogicalKeyPressed(
                    InvMoveKey.SPRINT,
                    context.isPhysicalKeyPressed(InvMoveKey.SPRINT));
            sprintWasForced = false;
        }
    }

    public void onClickSlotPacket(int syncId) {
        if (syncId == 0) {
            inventoryClickPending = true;
        }
    }

    public InvMovePacketAction onCloseInventoryPacket(int syncId) {
        if (syncId != 0) {
            inventoryClickPending = false;
            return InvMovePacketAction.PASS;
        }
        if (!inventoryClickPending) {
            return InvMovePacketAction.CANCEL;
        }
        inventoryClickPending = false;
        return InvMovePacketAction.PASS;
    }

    public void onServerScreenChange() {
        inventoryClickPending = false;
    }

    public boolean inventoryClickPending() {
        return inventoryClickPending;
    }

    private void resetState() {
        inventoryClickPending = false;
        sprintWasForced = false;
        eventKeyState.replaceAll((key, pressed) -> false);
    }
}
