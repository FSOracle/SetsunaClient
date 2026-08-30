package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;

/**
 * Fired when a raw keyboard key changes state, before vanilla handling.
 * Cancelling it swallows the key press for this frame.
 */
public final class KeyInputEvent extends CancellableEvent {

    private final int key;
    private final int scancode;
    private final int action;
    private final int modifiers;

    public KeyInputEvent(int key, int scancode, int action, int modifiers) {
        this.key = key;
        this.scancode = scancode;
        this.action = action;
        this.modifiers = modifiers;
    }

    /** GLFW key code. */
    public int key() {
        return key;
    }

    public int scancode() {
        return scancode;
    }

    /** One of the GLFW {@code PRESS}/{@code RELEASE}/{@code REPEAT} constants. */
    public int action() {
        return action;
    }

    public int modifiers() {
        return modifiers;
    }
}
