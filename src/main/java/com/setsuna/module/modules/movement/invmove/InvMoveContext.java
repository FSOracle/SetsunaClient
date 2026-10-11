package com.setsuna.module.modules.movement.invmove;

/** Minecraft-facing operations needed by the inventory-move state machine. */
public interface InvMoveContext {
    boolean isAvailable();

    InvMoveScreen screen();

    Object screenIdentity();

    boolean isPlayerMoving();

    boolean isPlayerSprinting();

    void closeInventory();
}
