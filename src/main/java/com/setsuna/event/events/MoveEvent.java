package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;

/**
 * Fired for each {@code LocalPlayer#move} with the intended motion delta.
 * Handlers may rewrite {@link #x}/{@link #y}/{@link #z} and {@link #cancel()} to
 * have the mixin re-issue the move with the modified vector.
 */
public final class MoveEvent extends CancellableEvent {

    private double x;
    private double y;
    private double z;

    public MoveEvent(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }
}
