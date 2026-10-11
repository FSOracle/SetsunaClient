package com.setsuna.util.time;

/** Wall-clock timer with the same strict/inclusive comparisons as the source client. */
public final class Chronometer {

    private long lastUpdate;

    public Chronometer() {
        this(System.currentTimeMillis());
    }

    public Chronometer(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public long elapsed() {
        return System.currentTimeMillis() - lastUpdate;
    }

    public boolean hasElapsed(long milliseconds) {
        return lastUpdate + milliseconds < System.currentTimeMillis();
    }

    public boolean hasElapsed() {
        return hasElapsed(0L);
    }

    public boolean hasAtLeastElapsed(long milliseconds) {
        return lastUpdate + milliseconds <= System.currentTimeMillis();
    }

    public void reset() {
        lastUpdate = System.currentTimeMillis();
    }

    public void reset(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public void waitForAtLeast(long milliseconds) {
        lastUpdate = Math.max(lastUpdate, System.currentTimeMillis() + milliseconds);
    }

    @Override
    public String toString() {
        return "Chronometer(lastUpdate=" + lastUpdate + ")";
    }
}
