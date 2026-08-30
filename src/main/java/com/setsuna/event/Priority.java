package com.setsuna.event;

/**
 * Standard listener priorities. Larger values run earlier.
 */
public final class Priority {

    public static final int HIGHEST = 200;
    public static final int HIGH = 100;
    public static final int NORMAL = 0;
    public static final int LOW = -100;
    public static final int LOWEST = -200;

    private Priority() {
    }
}
