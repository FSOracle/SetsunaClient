package com.setsuna.util.math;

import java.util.concurrent.ThreadLocalRandom;

/** Random-number helpers with inclusive bounds. */
public final class MathUtils {

    private MathUtils() {
    }

    /** A random int in the closed interval {@code [min, max]}. */
    public static int random(int min, int max) {
        return min >= max ? min : (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
    }

    /** A random float in the closed interval {@code [min, max]}. */
    public static float random(float min, float max) {
        return min >= max ? min : ThreadLocalRandom.current().nextFloat(min, Math.nextUp(max));
    }

    /** A random double in the closed interval {@code [min, max]}. */
    public static double random(double min, double max) {
        return min >= max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }

    /** Alias for {@link #random(int, int)} (source-client naming). */
    public static int getRandom(int min, int max) {
        return random(min, max);
    }

    /** Alias for {@link #random(float, float)} (source-client naming). */
    public static float getRandom(float min, float max) {
        return random(min, max);
    }

    /** Alias for {@link #random(double, double)} (source-client naming). */
    public static double getRandom(double min, double max) {
        return random(min, max);
    }
}
