package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;

/** Fired for every raw mouse-button state change before vanilla handling. */
public final class MouseButtonEvent extends CancellableEvent {

    private final int button;
    private final int action;
    private final int modifiers;
    private final double x;
    private final double y;
    private final double rawX;
    private final double rawY;

    public MouseButtonEvent(int button, int action, int modifiers) {
        this(button, action, modifiers, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public MouseButtonEvent(
            int button,
            int action,
            int modifiers,
            double x,
            double y,
            double rawX,
            double rawY) {
        this.button = button;
        this.action = action;
        this.modifiers = modifiers;
        this.x = x;
        this.y = y;
        this.rawX = rawX;
        this.rawY = rawY;
    }

    public int button() {
        return button;
    }

    public int action() {
        return action;
    }

    public int modifiers() {
        return modifiers;
    }

    /** GUI-scaled cursor X at the time of the click. */
    public double x() {
        return x;
    }

    /** GUI-scaled cursor Y at the time of the click. */
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
