package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;

/** Fired for one Unicode code point before vanilla text input handling. */
public final class CharInputEvent extends CancellableEvent {

    private final int codepoint;
    private final String text;
    private final boolean allowedChatCharacter;

    public CharInputEvent(int codepoint, String text, boolean allowedChatCharacter) {
        this.codepoint = codepoint;
        this.text = text;
        this.allowedChatCharacter = allowedChatCharacter;
    }

    public int codepoint() {
        return codepoint;
    }

    public String text() {
        return text;
    }

    public boolean allowedChatCharacter() {
        return allowedChatCharacter;
    }
}
