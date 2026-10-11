package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;
import com.setsuna.event.Event;

/** Fired immediately before and after the local player performs a ground jump. */
public final class GroundJumpEvent {

    private GroundJumpEvent() {
    }

    public static final class Before extends CancellableEvent {
    }

    public static final class After extends Event {
    }
}
