package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** Fired when vanilla is about to scale local movement for active item use. */
public final class SlowdownEvent extends CancellableEvent {

    private final LocalPlayer player;
    private final ItemStack itemStack;
    private float multiplier;

    public SlowdownEvent(LocalPlayer player, ItemStack itemStack, float multiplier) {
        this.player = player;
        this.itemStack = itemStack;
        this.multiplier = multiplier;
    }

    public LocalPlayer getPlayer() {
        return player;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public float getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(float multiplier) {
        this.multiplier = multiplier;
    }
}
