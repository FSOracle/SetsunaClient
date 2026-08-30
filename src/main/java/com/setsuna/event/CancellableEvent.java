package com.setsuna.event;

/**
 * An {@link Event} that a listener can veto. Once cancelled, the bus stops
 * dispatching it to lower-priority listeners and the caller decides how to react.
 */
public abstract class CancellableEvent extends Event {

    private boolean cancelled;

    public void cancel() {
        this.cancelled = true;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
