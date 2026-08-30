package com.setsuna.module.modules.movement.invmove;

public interface InvMoveContext {
    boolean isAvailable();

    InvMoveScreen screen();

    boolean isPhysicalKeyPressed(InvMoveKey key);

    void setLogicalKeyPressed(InvMoveKey key, boolean pressed);

    void sendCloseInventoryPacket();
}
