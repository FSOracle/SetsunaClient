package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Supplies the yaw used by Entity#moveRelative for local-player movement. */
public final class StrafeEvent extends Event {

    private final RotationState rotation;

    public StrafeEvent(float yaw) {
        rotation = new RotationState(yaw, 0.0F);
    }

    public float getYaw() {
        return rotation.horizontal();
    }

    public void setYaw(float yaw) {
        rotation.horizontal(yaw);
    }
}
