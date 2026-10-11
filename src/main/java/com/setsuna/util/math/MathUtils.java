package com.setsuna.util.math;

import java.util.concurrent.ThreadLocalRandom;

/** Inclusive random-range helpers. */
public final class MathUtils {

    private MathUtils() {
    }

    public static int random(int minimum, int maximum) {
        if (minimum >= maximum) {
            return minimum;
        }
        long exclusiveUpperBound = (long) maximum + 1L;
        return (int) ThreadLocalRandom.current().nextLong(minimum, exclusiveUpperBound);
    }

    public static float random(float minimum, float maximum) {
        if (minimum >= maximum) {
            return minimum;
        }
        float exclusiveUpperBound = Math.nextUp(maximum);
        return ThreadLocalRandom.current().nextFloat(minimum, exclusiveUpperBound);
    }

    public static double random(double minimum, double maximum) {
        if (minimum >= maximum) {
            return minimum;
        }
        double exclusiveUpperBound = Math.nextUp(maximum);
        return ThreadLocalRandom.current().nextDouble(minimum, exclusiveUpperBound);
    }

    public static int getRandom(int minimum, int maximum) {
        return random(minimum, maximum);
    }

    public static float getRandom(float minimum, float maximum) {
        return random(minimum, maximum);
    }

    public static double getRandom(double minimum, double maximum) {
        return random(minimum, maximum);
    }
}
