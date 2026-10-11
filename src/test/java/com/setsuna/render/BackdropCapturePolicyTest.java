package com.setsuna.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackdropCapturePolicyTest {
    private static final long INTERVAL = 16_666_667L;

    @Test
    void requestsAreThrottledUntilTheExactIntervalBoundary() {
        BackdropCapturePolicy policy = ready();
        assertTrue(policy.shouldCapture(true, false, 0L));
        assertFalse(policy.shouldCapture(false, false, 0L));
        policy.captured(0L);
        assertFalse(policy.shouldCapture(true, true, 0L));
        assertFalse(policy.shouldCapture(true, true, INTERVAL - 1L));
        assertTrue(policy.shouldCapture(true, true, INTERVAL));
        assertFalse(policy.shouldCapture(false, true, INTERVAL));
    }

    @Test
    void missingOrFailedSnapshotsDoNotWaitForTheInterval() {
        BackdropCapturePolicy policy = ready();
        policy.captured(100L);
        assertTrue(policy.shouldCapture(true, false, 101L));
        // A failed replacement does not call captured(), leaving the old deadline intact.
        assertTrue(policy.shouldCapture(true, true, INTERVAL + 100L));
        assertTrue(policy.shouldCapture(true, true, INTERVAL + 101L));
    }

    @Test
    void fullSourceSizeChangesInvalidateEvenWhenDownsampledSizeMatches() {
        BackdropCapturePolicy policy = ready();
        policy.captured(100L);
        assertFalse(policy.updateDimensions(1920, 1080));
        assertFalse(policy.shouldCapture(true, true, 101L));
        assertTrue(policy.updateDimensions(1921, 1081));
        assertTrue(policy.shouldCapture(true, true, 101L));
        assertFalse(policy.shouldCapture(false, false, 101L));
    }

    @Test
    void screenAndWorldIdentityChangesInvalidateImmediately() {
        BackdropCapturePolicy policy = ready();
        Object screen = new String("screen");
        Object level = new String("level");
        assertTrue(policy.updateScene(screen, level));
        policy.captured(100L);
        assertFalse(policy.updateScene(screen, level));
        assertFalse(policy.shouldCapture(true, true, 101L));
        Object nextScreen = new String("screen");
        assertTrue(policy.updateScene(nextScreen, level));
        assertTrue(policy.shouldCapture(true, true, 101L));
        policy.captured(101L);
        assertTrue(policy.updateScene(nextScreen, new String("level")));
        assertTrue(policy.shouldCapture(true, true, 102L));
        assertTrue(policy.updateScene(null, null));
    }

    @Test
    void framebufferSourcesRetainIndependentCaptureDeadlines() {
        BackdropCapturePolicy main = ready();
        BackdropCapturePolicy window = ready();
        main.captured(0L);
        assertTrue(window.shouldCapture(true, false, 1L));
        window.captured(1L);
        assertFalse(main.shouldCapture(true, true, 2L));
        assertFalse(window.shouldCapture(true, true, 2L));
        assertTrue(main.shouldCapture(true, true, INTERVAL));
        assertFalse(window.shouldCapture(true, true, INTERVAL));
        main.reset();
        assertFalse(window.shouldCapture(true, true, INTERVAL));
    }

    @Test
    void resetAndInvalidDimensionsPreventStaleCaptureState() {
        BackdropCapturePolicy policy = ready();
        policy.captured(100L);
        policy.reset();
        assertFalse(policy.shouldCapture(true, false, 101L));
        assertTrue(policy.updateDimensions(1920, 1080));
        assertTrue(policy.shouldCapture(true, true, 101L));
        assertTrue(policy.updateDimensions(0, 1080));
        assertFalse(policy.shouldCapture(true, false, INTERVAL));
        assertTrue(policy.updateDimensions(1920, -1));
        assertFalse(policy.shouldCapture(true, false, INTERVAL));
    }

    @Test
    void elapsedTimeHandlesNanoTimeOriginAndWraparound() {
        BackdropCapturePolicy policy = ready();
        policy.captured(-INTERVAL);
        assertFalse(policy.shouldCapture(true, true, -1L));
        assertTrue(policy.shouldCapture(true, true, 0L));
        long start = Long.MAX_VALUE - INTERVAL / 2;
        policy.captured(start);
        assertFalse(policy.shouldCapture(true, true, start + INTERVAL - 1L));
        assertTrue(policy.shouldCapture(true, true, start + INTERVAL));
    }

    private static BackdropCapturePolicy ready() {
        BackdropCapturePolicy policy = new BackdropCapturePolicy(INTERVAL);
        policy.updateDimensions(1920, 1080);
        return policy;
    }
}
