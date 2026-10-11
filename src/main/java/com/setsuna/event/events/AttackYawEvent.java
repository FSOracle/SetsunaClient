package com.setsuna.event.events;

import com.setsuna.event.Event;

public final class AttackYawEvent extends Event {

    private final RotationState rotation;

    public AttackYawEvent(float yaw) {
        rotation = new RotationState(yaw, 0.0F);
    }

    public float getYaw() {
        return rotation.horizontal();
    }

    public void setYaw(float yaw) {
        rotation.horizontal(yaw);
    }
}
