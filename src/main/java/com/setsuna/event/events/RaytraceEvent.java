package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Lets silent-rotation features replace the yaw and pitch used for a view ray. */
public final class RaytraceEvent extends Event {

    private final RotationState rotation;

    public RaytraceEvent(float yaw, float pitch) {
        rotation = new RotationState(yaw, pitch);
    }

    public float getYaw() {
        return rotation.horizontal();
    }

    public float getPitch() {
        return rotation.vertical();
    }

    public void setYaw(float yaw) {
        rotation.horizontal(yaw);
    }

    public void setPitch(float pitch) {
        rotation.vertical(pitch);
    }
}
