package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Supplies the view rotation used by local-player fall-flying physics. */
public final class FallFlyingEvent extends Event {

    private final RotationState rotation;

    public FallFlyingEvent(float yaw, float pitch) {
        rotation = new RotationState(yaw, pitch);
    }

    public FallFlyingEvent(float pitch) {
        this(0.0F, pitch);
    }

    public float getYaw() {
        return rotation.horizontal();
    }

    public void setYaw(float yaw) {
        rotation.horizontal(yaw);
    }

    public float getPitch() {
        return rotation.vertical();
    }

    public void setPitch(float pitch) {
        rotation.vertical(pitch);
    }
}
