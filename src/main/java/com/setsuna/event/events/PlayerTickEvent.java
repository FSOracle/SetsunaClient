package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;
import com.setsuna.event.Event;

/**
 * Fired around {@code LocalPlayer#tick}, giving modules a hook that runs in
 * lock-step with the local player's simulation (unlike {@link TickEvent}, which
 * brackets the whole client tick). {@link Pre} is cancellable to skip the
 * player's own tick for the frame.
 */
public final class PlayerTickEvent {

    private PlayerTickEvent() {
    }

    /** Fired before the player ticks; cancel to skip vanilla player logic. */
    public static final class Pre extends CancellableEvent {
    }

    /** Fired after the player has ticked. */
    public static final class Post extends Event {
    }
}
