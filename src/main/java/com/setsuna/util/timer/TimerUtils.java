package com.setsuna.util.timer;

/**
 * A simple millisecond stopwatch for rate-limiting periodic work (cache
 * rebuilds, packet probes). {@link #every(long)} resets on each successful
 * trigger, giving a fixed-interval gate.
 */
public final class TimerUtils {

    private long referenceTime;

    public TimerUtils() {
        reset();
    }

    public void reset() {
        referenceTime = now();
    }

    public long getMs() {
        return elapsedAt(now());
    }

    public void setMs(long ms) {
        referenceTime = now() - ms;
    }

    public boolean passedSecond(double seconds) {
        return passedMillis((long) (seconds * 1000L));
    }

    public boolean hasDelayed(int ticks) {
        return passedMillis((long) ticks * 50L);
    }

    /** Returns true and resets once {@code ms} has elapsed since the last reset. */
    public boolean every(long ms) {
        boolean due = passedMillis(ms);
        if (due) reset();
        return due;
    }

    public boolean passedMillis(double ms) {
        return passedMillis((long) ms);
    }

    public boolean passedMillis(long ms) {
        return elapsedAt(now()) >= ms;
    }

    private long elapsedAt(long timestamp) {
        return timestamp - referenceTime;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
