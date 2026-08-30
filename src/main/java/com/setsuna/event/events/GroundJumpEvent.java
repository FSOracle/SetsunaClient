package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Fired immediately before and after the local player performs a ground jump. */
public final class GroundJumpEvent {

    private GroundJumpEvent() {
    }

    public static final class Before extends Event {
    }

    public static final class After extends Event {
    }
}
