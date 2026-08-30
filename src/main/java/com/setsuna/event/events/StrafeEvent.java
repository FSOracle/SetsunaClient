package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Supplies the yaw used by Entity#moveRelative for local-player movement. */
public final class StrafeEvent extends Event {

    private float yaw;

    public StrafeEvent(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
}
