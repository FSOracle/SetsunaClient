package com.setsuna.event.events;

import com.setsuna.event.Event;

/**
 * Supplies the rotations used to animate the local player's third-person
 * model. Silent rotations can replace these values without moving the camera.
 */
public final class RotationAnimationEvent extends Event {

    private static final int YAW = 0;
    private static final int LAST_YAW = 1;
    private static final int PITCH = 2;
    private static final int LAST_PITCH = 3;
    private final float[] values = new float[4];

    public RotationAnimationEvent(float yaw, float lastYaw, float pitch, float lastPitch) {
        values[YAW] = yaw;
        values[LAST_YAW] = lastYaw;
        values[PITCH] = pitch;
        values[LAST_PITCH] = lastPitch;
    }

    public float getYaw() {
        return values[YAW];
    }

    public float getLastYaw() {
        return values[LAST_YAW];
    }

    public float getPitch() {
        return values[PITCH];
    }

    public float getLastPitch() {
        return values[LAST_PITCH];
    }

    public void setYaw(float yaw) {
        values[YAW] = yaw;
    }

    public void setLastYaw(float lastYaw) {
        values[LAST_YAW] = lastYaw;
    }

    public void setPitch(float pitch) {
        values[PITCH] = pitch;
    }

    public void setLastPitch(float lastPitch) {
        values[LAST_PITCH] = lastPitch;
    }
}
