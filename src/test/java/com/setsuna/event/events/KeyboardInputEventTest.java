package com.setsuna.event.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyboardInputEventTest {

    @Test
    void originalImpulsesRemainAvailableAfterMovementCorrection() {
        KeyboardInputEvent event = new KeyboardInputEvent(
                true, false, false, true, false, false, true);

        event.setForward(0.0F);
        event.setStrafe(1.0F);

        assertEquals(1.0F, event.getOriginalForward());
        assertEquals(-1.0F, event.getOriginalStrafe());
        assertEquals(0.0F, event.getForward());
        assertEquals(1.0F, event.getStrafe());
    }
}
