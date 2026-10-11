package com.setsuna.util.player;

import net.minecraft.world.InteractionHand;

/** Inventory lookup summary containing the first slot and aggregate capacity. */
public record FindItemResult(int slot, int count, int maxCount) {

    private static final int NOT_FOUND = -1;
    private static final int OFFHAND_SLOT = 40;

    public boolean found() {
        return slot != NOT_FOUND;
    }

    public InteractionHand getHand() {
        return isOffhandSlot() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    public boolean isMainHand() {
        return !isOffhandSlot();
    }

    public boolean isOffhand() {
        return isOffhandSlot();
    }

    private boolean isOffhandSlot() {
        return slot == OFFHAND_SLOT;
    }
}
