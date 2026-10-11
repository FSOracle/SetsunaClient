package com.setsuna.event.events;

import com.setsuna.event.Event;
import net.minecraft.client.multiplayer.ClientLevel;

/** Fired immediately before Minecraft replaces the active client level. */
public final class WorldChangeEvent extends Event {

    private final ClientLevel previousLevel;
    private final ClientLevel nextLevel;

    public WorldChangeEvent(ClientLevel previousLevel, ClientLevel nextLevel) {
        this.previousLevel = previousLevel;
        this.nextLevel = nextLevel;
    }

    public ClientLevel previousLevel() {
        return previousLevel;
    }

    public ClientLevel nextLevel() {
        return nextLevel;
    }
}
