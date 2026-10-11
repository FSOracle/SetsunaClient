package com.setsuna.module.modules.combat.silentaura;

import java.util.concurrent.ThreadLocalRandom;

/** Slowly moving aim offset used by Vape's SilentAura. */
public final class SilentAuraAimJitter {

    private final double minimum;
    private final double maximum;
    private double current;
    private double target;
    private long nextRetargetAt;

    public SilentAuraAimJitter(double minimum, double maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.nextRetargetAt = System.currentTimeMillis() + nextRetargetDelay();
    }

    public double current() {
        return current;
    }

    public void update() {
        long now = System.currentTimeMillis();
        if (now >= nextRetargetAt) {
            target = ThreadLocalRandom.current().nextDouble(minimum, maximum);
            nextRetargetAt = now + nextRetargetDelay();
        }

        double step = 0.01D + ThreadLocalRandom.current().nextDouble(0.05D);
        if (target > current) {
            current = Math.min(target, current + step);
        } else if (target < current) {
            current = Math.max(target, current - step);
        }
    }

    private static long nextRetargetDelay() {
        return ThreadLocalRandom.current().nextLong(100L, 1_000L);
    }
}
