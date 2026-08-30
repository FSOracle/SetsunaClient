package com.setsuna.event.events;

import com.setsuna.event.Event;

/**
 * Supplies the rotations used to animate the local player's third-person
 * model. Silent rotations can replace these values without moving the camera.
 */
public final class RotationAnimationEvent extends Event {

    private float yaw;
    private float lastYaw;
    private float pitch;
    private float lastPitch;

    public RotationAnimationEvent(float yaw, float lastYaw, float pitch, float lastPitch) {
        this.yaw = yaw;
        this.lastYaw = lastYaw;
        this.pitch = pitch;
        this.lastPitch = lastPitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getLastYaw() {
        return lastYaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getLastPitch() {
        return lastPitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setLastYaw(float lastYaw) {
        this.lastYaw = lastYaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setLastPitch(float lastPitch) {
        this.lastPitch = lastPitch;
    }
}
