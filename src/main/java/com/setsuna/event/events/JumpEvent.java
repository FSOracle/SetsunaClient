package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Supplies the yaw used to apply the local player's ground-jump impulse. */
public final class JumpEvent extends Event {

    private final RotationState rotation;

    public JumpEvent(float yaw) {
        rotation = new RotationState(yaw, 0.0F);
    }

    public float getYaw() {
        return rotation.horizontal();
    }

    public void setYaw(float yaw) {
        rotation.horizontal(yaw);
    }
}
