package com.setsuna.util.time;

import com.setsuna.util.math.RandomUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortedTimingUtilsTest {

    @Test
    void chronometerSupportsElapsedRecoilAndFutureWaitStates() {
        long now = System.currentTimeMillis();
        Chronometer chronometer = new Chronometer(now - 2_000L);
        assertTrue(chronometer.hasElapsed(1_000L));
        assertTrue(chronometer.hasAtLeastElapsed(1_000L));

        chronometer.reset(now + 2_000L);
        assertFalse(chronometer.hasElapsed());
        assertFalse(chronometer.hasAtLeastElapsed(0L));

        chronometer.reset(now - 2_000L);
        chronometer.waitForAtLeast(1_000L);
        assertFalse(chronometer.hasElapsed());
    }

    @Test
    void inclusiveRandomRangesNormalizeBoundsAndKeepEndpointsValid() {
        assertEquals(4, RandomUtils.nextIntInclusive(4, 4));
        for (int index = 0; index < 200; index++) {
            int value = RandomUtils.nextIntInclusive(9, 3);
            assertTrue(value >= 3 && value <= 9);
            int percent = RandomUtils.inclusivePercentRoll();
            assertTrue(percent >= 0 && percent <= 100);
        }
    }
}
