package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Supplies the view rotation used by local-player fall-flying physics. */
public final class FallFlyingEvent extends Event {

    private float yaw;
    private float pitch;

    public FallFlyingEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public FallFlyingEvent(float pitch) {
        this(0.0F, pitch);
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
}
