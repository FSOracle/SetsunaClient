package com.setsuna.event.events;

import com.setsuna.event.Event;

/**
 * Fired after the client handles a respawn packet, so state-holding managers
 * (rotations, targets) can reset cleanly across deaths and dimension changes.
 */
public final class RespawnEvent extends Event {
}
