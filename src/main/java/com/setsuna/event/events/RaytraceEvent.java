package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Lets silent-rotation features replace the yaw and pitch used for a view ray. */
public final class RaytraceEvent extends Event {

    private float yaw;
    private float pitch;

    public RaytraceEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
}
