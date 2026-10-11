package com.setsuna.event.events;

final class RotationState {

    private float horizontal;
    private float vertical;

    RotationState(float horizontal, float vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    float horizontal() {
        return horizontal;
    }

    void horizontal(float value) {
        horizontal = value;
    }

    float vertical() {
        return vertical;
    }

    void vertical(float value) {
        vertical = value;
    }
}
