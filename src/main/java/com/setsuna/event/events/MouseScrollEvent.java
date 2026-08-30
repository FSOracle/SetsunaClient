package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;

/** Fired for raw horizontal/vertical mouse-wheel input before vanilla handling. */
public final class MouseScrollEvent extends CancellableEvent {

    private final double horizontal;
    private final double vertical;
    private final double x;
    private final double y;
    private final double rawX;
    private final double rawY;

    public MouseScrollEvent(
            double horizontal,
            double vertical,
            double x,
            double y,
            double rawX,
            double rawY) {
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.x = x;
        this.y = y;
        this.rawX = rawX;
        this.rawY = rawY;
    }

    public double horizontal() {
        return horizontal;
    }

    public double vertical() {
        return vertical;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double rawX() {
        return rawX;
    }

    public double rawY() {
        return rawY;
    }
}
