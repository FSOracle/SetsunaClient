package com.setsuna.render;

/** Render-thread capture timing for one framebuffer source; contains no GPU resources. */
final class BackdropCapturePolicy {
    private final long intervalNanos;
    private int width = -1;
    private int height = -1;
    private Object screen;
    private Object level;
    private boolean captured;
    private long lastCaptureNanos;

    BackdropCapturePolicy(long intervalNanos) {
        if (intervalNanos <= 0L) throw new IllegalArgumentException("Capture interval must be positive");
        this.intervalNanos = intervalNanos;
    }

    /** Returns true when the owner must dispose its old snapshot. */
    boolean updateScene(Object screen, Object level) {
        if (this.screen == screen && this.level == level) return false;
        this.screen = screen;
        this.level = level;
        clearCapture();
        return true;
    }

    /** Compare full source dimensions: adjacent odd/even sizes can downsample identically. */
    boolean updateDimensions(int width, int height) {
        if (this.width == width && this.height == height) return false;
        this.width = width;
        this.height = height;
        clearCapture();
        return true;
    }

    boolean shouldCapture(boolean requested, boolean hasSnapshot, long nowNanos) {
        return requested && width > 0 && height > 0
                && (!hasSnapshot || !captured || nowNanos - lastCaptureNanos >= intervalNanos);
    }

    /** Call only after a non-null snapshot has been installed successfully. */
    void captured(long nowNanos) {
        captured = true;
        lastCaptureNanos = nowNanos;
    }

    void reset() {
        width = -1;
        height = -1;
        screen = null;
        level = null;
        clearCapture();
    }

    private void clearCapture() {
        captured = false;
        lastCaptureNanos = 0L;
    }
}
