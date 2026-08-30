package com.setsuna.util.player;

/** Shared one-shot counter for modules that need to suppress a local player tick. */
public final class SkipTickUtility {

    private static int skipTicks;

    private SkipTickUtility() {
    }

    public static void addSkipTicks(int ticks) {
        if (ticks > 0) {
            skipTicks += ticks;
        }
    }

    public static boolean consumeSkipTick() {
        if (skipTicks <= 0) {
            return false;
        }
        skipTicks--;
        return true;
    }

    public static void reset() {
        skipTicks = 0;
    }
}
