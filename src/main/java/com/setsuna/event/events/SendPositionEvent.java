package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;

/**
 * Fired at the head of {@code LocalPlayer#sendPosition} with the position/rotation
 * about to be reported to the server. Handlers may rewrite any field; the mixin
 * feeds the (possibly modified) values into the outgoing movement packet, which is
 * how silent aim reports combat rotations without turning the client view.
 */
public final class SendPositionEvent extends CancellableEvent {

    private final double[] position = new double[3];
    private final float[] rotation = new float[2];
    private boolean onGround;

    public SendPositionEvent(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        position[0] = x;
        position[1] = y;
        position[2] = z;
        rotation[0] = yaw;
        rotation[1] = pitch;
        this.onGround = onGround;
    }

    public double getX() {
        return position[0];
    }

    public double getY() {
        return position[1];
    }

    public double getZ() {
        return position[2];
    }

    public float getYaw() {
        return rotation[0];
    }

    public float getPitch() {
        return rotation[1];
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setX(double x) {
        position[0] = x;
    }

    public void setY(double y) {
        position[1] = y;
    }

    public void setZ(double z) {
        position[2] = z;
    }

    public void setYaw(float yaw) {
        rotation[0] = yaw;
    }

    public void setPitch(float pitch) {
        rotation[1] = pitch;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }
}
