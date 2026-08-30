package com.setsuna.util.rotation;

/**
 * Relative importance of a rotation request. When several modules ask
 * {@link com.setsuna.manager.RotationManager} to aim in the same tick, the one with
 * the highest {@link #priority} wins; equal or higher priority replaces the current
 * request, lower priority is ignored.
 */
public enum Priority {

    Lowest(0),
    Low(1),
    Medium(2),
    High(3),
    Highest(4);

    public final int priority;

    Priority(int priority) {
        this.priority = priority;
    }
}
