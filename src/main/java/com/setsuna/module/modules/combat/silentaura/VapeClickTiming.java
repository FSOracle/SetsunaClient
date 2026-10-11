package com.setsuna.module.modules.combat.silentaura;

import java.util.SplittableRandom;

/** Vape 4.21's default click timing model. */
public final class VapeClickTiming {

    private static final long MIN_DRIFT_INTERVAL_MILLIS = 1_200L;
    private static final long MAX_DRIFT_INTERVAL_MILLIS = 3_200L;
    private static final long MIN_TARGET_INTERVAL_MILLIS = 30_000L;
    private static final long MAX_TARGET_INTERVAL_MILLIS = 90_000L;
    private static final long IDLE_RESET_THRESHOLD_MILLIS = 1_200L;
    private static final double MAX_FATIGUE = 0.4D;

    private final SplittableRandom random;
    private final long timingSalt;
    private double minimumCps;
    private double maximumCps;
    private double currentCps;
    private double targetCps;
    private double targetOffset;
    private double pauseChanceOffset;
    private double repeatDelayChance = 0.06D;
    private double fatigue;
    private long previousDelayMillis;
    private long nextDriftTimestamp;
    private long nextTargetTimestamp;
    private long lastClickTimestamp;
    private int clicksSinceVariation;
    private int burstClicksRemaining;
    private boolean initialized;

    public VapeClickTiming(long seed) {
        long randomSeed = mixSeed(seed ^ 0x9E3779B97F4A7C15L);
        random = new SplittableRandom(randomSeed);
        timingSalt = mixSeed(randomSeed - 3_335_678_366_873_096_957L);
    }

    public void configureCpsRange(int minimum, int maximum) {
        double rawMinimum = Math.max(0, minimum);
        double rawMaximum = Math.max(rawMinimum, maximum);
        minimumCps = Math.max(0.5D, rawMinimum * 1.5D);
        maximumCps = Math.max(minimumCps + 0.1D, Math.max(0.5D, rawMaximum * 1.5D));
        if (!initialized) {
            targetCps = midpoint(minimumCps, maximumCps);
            currentCps = clamp(targetCps + sampleNormalDistribution() * 0.25D,
                    minimumCps, maximumCps);
        }
    }

    public long nextDelayMillis() {
        long now = System.currentTimeMillis();
        if (!initialized) {
            initialized = true;
            initializeTiming(now);
            lastClickTimestamp = now;
        }

        long idleDuration = lastClickTimestamp == 0L ? 0L : now - lastClickTimestamp;
        if (idleDuration >= IDLE_RESET_THRESHOLD_MILLIS) {
            double recoveryAmount = Math.min(5_000L, idleDuration) / 10.0D;
            fatigue = Math.max(0.0D, fatigue - 0.004D * recoveryAmount);
            burstClicksRemaining = random.nextDouble() < 0.7D ? 2 + random.nextInt(4) : 0;
            repeatDelayChance = 0.03D + random.nextDouble() * 0.06D;
            pauseChanceOffset = random.nextDouble() * 0.04D;
        }

        if (++clicksSinceVariation > 80 + random.nextInt(120)) {
            repeatDelayChance = 0.03D + random.nextDouble() * 0.06D;
            pauseChanceOffset = random.nextDouble() * 0.04D;
            clicksSinceVariation = 0;
        }
        if (now >= nextDriftTimestamp) {
            currentCps += 0.25D * (targetCps - currentCps)
                    + 0.45D * sampleNormalDistribution();
            if (random.nextDouble() < 0.03D) {
                currentCps += (random.nextDouble() - 0.5D) * 1.2D;
            }
            currentCps = clamp(currentCps, minimumCps, maximumCps);
            scheduleDrift(now);
        }
        if (now >= nextTargetTimestamp) {
            targetOffset = (random.nextDouble() * 2.0D - 1.0D) * 0.75D;
            targetCps = clamp(midpoint(minimumCps, maximumCps) + targetOffset,
                    minimumCps, maximumCps);
            scheduleTargetChange(now);
        }

        boolean burstClick = burstClicksRemaining > 0;
        double effectiveCps = currentCps;
        if (burstClick) {
            effectiveCps *= 1.0D + 0.05D * (0.4D + 0.8D * random.nextDouble());
            burstClicksRemaining--;
        }
        effectiveCps *= 1.0D - Math.min(MAX_FATIGUE, fatigue);
        effectiveCps = clamp(effectiveCps, minimumCps, maximumCps);

        double delayMillis = 1_000.0D / effectiveCps
                * Math.exp(0.24D * sampleClickNoise());
        int maximumMicroJitter = 35 + random.nextInt(11) - 5;
        delayMillis += random.nextInt(Math.max(1, maximumMicroJitter) + 1);
        if (burstClick) {
            delayMillis += random.nextInt(15);
        }
        if (previousDelayMillis > 0L && random.nextDouble() < repeatDelayChance) {
            delayMillis = previousDelayMillis + random.nextInt(7) - 3L;
        }
        if (random.nextDouble() < 0.07D) {
            delayMillis *= 0.7D + random.nextDouble() * 0.2D;
        }
        double pauseChance = Math.min(0.04D + 0.12D * fatigue + pauseChanceOffset, 0.18D);
        if (random.nextDouble() < pauseChance) {
            delayMillis += 50 + random.nextInt(101);
        }

        fatigue = Math.min(MAX_FATIGUE, fatigue + 0.015D);
        lastClickTimestamp = now;
        previousDelayMillis = (long) Math.max(1.0D, Math.min(delayMillis, Integer.MAX_VALUE));
        return previousDelayMillis;
    }

    private void initializeTiming(long now) {
        scheduleDrift(now);
        scheduleTargetChange(now);
        targetOffset = (random.nextDouble() * 2.0D - 1.0D) * 0.75D;
        targetCps = clamp(midpoint(minimumCps, maximumCps) + targetOffset,
                minimumCps, maximumCps);
    }

    private void scheduleDrift(long now) {
        nextDriftTimestamp = now + randomDuration(now,
                MIN_DRIFT_INTERVAL_MILLIS, MAX_DRIFT_INTERVAL_MILLIS);
    }

    private void scheduleTargetChange(long now) {
        nextTargetTimestamp = now + randomDuration(now,
                MIN_TARGET_INTERVAL_MILLIS, MAX_TARGET_INTERVAL_MILLIS);
    }

    private long randomDuration(long now, long minimum, long maximum) {
        long timeComponent = (now ^ timingSalt) & 0xFFFFL;
        long range = maximum - minimum + 1L;
        return minimum + (Math.abs((int) timeComponent) + random.nextInt((int) range)) % range;
    }

    private double sampleClickNoise() {
        double normal = sampleNormalDistribution();
        double roll = random.nextDouble();
        if (roll < 0.12D) {
            return (random.nextDouble() - 0.5D) * 3.0D;
        }
        if (roll < 0.17D) {
            return normal * (1.4D + random.nextDouble() * 0.6D);
        }
        return normal;
    }

    private double sampleNormalDistribution() {
        double sum = 0.0D;
        for (int index = 0; index < 6; index++) {
            sum += random.nextDouble();
        }
        return (sum - 3.0D) * 1.22474487139D;
    }

    private static double midpoint(double first, double second) {
        return (first + second) * 0.5D;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long mixSeed(long seed) {
        seed = (seed ^ seed >>> 30) * -4_658_895_280_553_007_687L;
        seed = (seed ^ seed >>> 27) * -7_723_592_293_110_705_685L;
        return seed ^ seed >>> 31;
    }
}
