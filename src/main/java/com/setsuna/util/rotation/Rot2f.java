package com.setsuna.util.rotation;

/**
 * A mutable yaw/pitch pair (both in degrees). Used throughout the rotation engine
 * as the unit of "where to aim": modules build a target {@code Rot2f}, the manager
 * smooths toward it, and the send-position mixin reports it to the server.
 */
public final class Rot2f {

    private static final int YAW = 0;
    private static final int PITCH = 1;

    private final float[] components;

    public Rot2f(float yaw, float pitch) {
        components = new float[]{yaw, pitch};
    }

    public float getYaw() {
        return components[YAW];
    }

    public float getPitch() {
        return components[PITCH];
    }

    public void setYaw(float yaw) {
        components[YAW] = yaw;
    }

    public void setPitch(float pitch) {
        components[PITCH] = pitch;
    }

    public void set(float yaw, float pitch) {
        components[YAW] = yaw;
        components[PITCH] = pitch;
    }

    public Rot2f copy() {
        return new Rot2f(getYaw(), getPitch());
    }

    @Override
    public String toString() {
        return "Rot2f[yaw=" + getYaw() + ", pitch=" + getPitch() + "]";
    }
}
