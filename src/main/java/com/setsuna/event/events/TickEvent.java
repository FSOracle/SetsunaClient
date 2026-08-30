package com.setsuna.event.events;

import com.setsuna.event.Event;

/**
 * Fired around the client's main {@code tick} loop.
 */
public abstract class TickEvent extends Event {

    /** Fired at the very start of the client tick, before vanilla logic runs. */
    public static final class Pre extends TickEvent {
    }

    /** Fired at the end of the client tick, after vanilla logic has run. */
    public static final class Post extends TickEvent {
    }
}
