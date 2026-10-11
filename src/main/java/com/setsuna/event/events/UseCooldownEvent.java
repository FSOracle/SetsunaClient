package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Fired after vanilla updates the use-item cooldown. */
public final class UseCooldownEvent extends Event {

    private int cooldown;

    public UseCooldownEvent(int cooldown) {
        this.cooldown = cooldown;
    }

    public int cooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = Math.max(0, cooldown);
    }
}
