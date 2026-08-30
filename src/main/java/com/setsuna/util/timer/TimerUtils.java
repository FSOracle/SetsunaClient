package com.setsuna.util.timer;

/**
 * A simple millisecond stopwatch for rate-limiting periodic work (cache
 * rebuilds, packet probes). {@link #every(long)} resets on each successful
 * trigger, giving a fixed-interval gate.
 */
public final class TimerUtils {

    private long startTime;

    public TimerUtils() {
        reset();
    }

    public void reset() {
        startTime = System.currentTimeMillis();
    }

    public long getMs() {
        return System.currentTimeMillis() - startTime;
    }

    public void setMs(long ms) {
        startTime = System.currentTimeMillis() - ms;
    }

    public boolean passedSecond(double seconds) {
        return passedMillis((long) (seconds * 1000L));
    }

    public boolean hasDelayed(int ticks) {
        return passedMillis((long) ticks * 50L);
    }

    /** Returns true and resets once {@code ms} has elapsed since the last reset. */
    public boolean every(long ms) {
        if (passedMillis(ms)) {
            reset();
            return true;
        }
        return false;
    }

    public boolean passedMillis(double ms) {
        return passedMillis((long) ms);
    }

    public boolean passedMillis(long ms) {
        return System.currentTimeMillis() - startTime >= ms;
    }
}
