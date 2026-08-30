package com.setsuna.event.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LuaInputEventDataTest {

    @Test
    void mouseButtonCarriesScaledAndRawCoordinates() {
        MouseButtonEvent event = new MouseButtonEvent(1, 0, 2, 12.5, 24.5, 25.0, 49.0);

        assertEquals(1, event.button());
        assertEquals(12.5, event.x());
        assertEquals(24.5, event.y());
        assertEquals(25.0, event.rawX());
        assertEquals(49.0, event.rawY());
        assertFalse(event.isCancelled());
    }

    @Test
    void scrollAndCharacterEventsRetainPayloads() {
        MouseScrollEvent scroll = new MouseScrollEvent(0.25, -1.0, 10.0, 20.0, 30.0, 40.0);
        CharInputEvent character = new CharInputEvent(0x4E2D, "中", true);

        assertEquals(0.25, scroll.horizontal());
        assertEquals(-1.0, scroll.vertical());
        assertEquals(10.0, scroll.x());
        assertEquals(0x4E2D, character.codepoint());
        assertEquals("中", character.text());
        assertEquals(true, character.allowedChatCharacter());
    }
}
