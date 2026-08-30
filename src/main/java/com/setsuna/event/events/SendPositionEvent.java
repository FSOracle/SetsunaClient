package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;

/**
 * Fired at the head of {@code LocalPlayer#sendPosition} with the position/rotation
 * about to be reported to the server. Handlers may rewrite any field; the mixin
 * feeds the (possibly modified) values into the outgoing movement packet, which is
 * how silent aim reports combat rotations without turning the client view.
 */
public final class SendPositionEvent extends CancellableEvent {

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean onGround;

    public SendPositionEvent(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
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

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isOnGround() {
        return onGround;
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

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }
}
