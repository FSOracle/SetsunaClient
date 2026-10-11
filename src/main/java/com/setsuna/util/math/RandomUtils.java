package com.setsuna.util.math;

import java.util.concurrent.ThreadLocalRandom;

/** Inclusive random helpers used by the ported combat timing state machines. */
public final class RandomUtils {

    private RandomUtils() {
    }

    public static int nextIntInclusive(int minimum, int maximum) {
        int low = Math.min(minimum, maximum);
        int high = Math.max(minimum, maximum);
        return low + ThreadLocalRandom.current().nextInt(high - low + 1);
    }

    public static int percentRoll() {
        return ThreadLocalRandom.current().nextInt(100);
    }

    public static int inclusivePercentRoll() {
        return ThreadLocalRandom.current().nextInt(101);
    }
}
