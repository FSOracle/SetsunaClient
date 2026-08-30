package com.setsuna.module.modules.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedCompatibilityTest {

    @Test
    void predictionSynchronizedIsTheProductionDefault() {
        assertEquals(
                Speed.StrafeMode.PREDICTION_SYNCHRONIZED,
                Speed.INSTANCE.strafeMode.defaultValue()
        );
    }

    @Test
    void vanillaBhopDoesNotExposeAnAbsoluteSpeedOverride() {
        Speed speed = Speed.INSTANCE;
        Speed.Mode previous = speed.mode.get();
        try {
            speed.mode.set(Speed.Mode.BHOP);
            assertFalse(speed.speed.visible());

            speed.mode.set(Speed.Mode.STRAFE);
            assertTrue(speed.speed.visible());
        } finally {
            speed.mode.set(previous);
        }
    }
}
