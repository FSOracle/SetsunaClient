package com.setsuna.util.render.animation;

import net.minecraft.util.Mth;

import java.util.function.Function;

public enum Easing {

    LINEAR,
    DECELERATE,
    SMOOTH_STEP,
    EASE_IN_QUAD,
    EASE_OUT_QUAD,
    EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC,
    EASE_OUT_CUBIC,
    EASE_IN_OUT_CUBIC,
    EASE_IN_QUART,
    EASE_OUT_QUART,
    EASE_IN_OUT_QUART,
    EASE_IN_QUINT,
    EASE_OUT_QUINT,
    EASE_IN_OUT_QUINT,
    EASE_IN_SINE,
    EASE_OUT_SINE,
    EASE_IN_OUT_SINE,
    EASE_IN_EXPO,
    EASE_OUT_EXPO,
    EASE_IN_OUT_EXPO,
    EASE_IN_CIRC,
    EASE_OUT_CIRC,
    EASE_IN_OUT_CIRC,
    SIGMOID,
    EASE_OUT_ELASTIC,
    EASE_IN_BACK,
    DYNAMIC_ISLAND;

    private static final float BACK_OVERSHOOT = 1.70158F;

    private final Function<Float, Float> function = this::evaluate;

    public Function<Float, Float> getFunction() {
        return function;
    }

    private float evaluate(float progress) {
        return switch (this) {
            case LINEAR -> progress;
            case DECELERATE -> {
                float remaining = progress - 1.0F;
                yield 1.0F - remaining * remaining;
            }
            case SMOOTH_STEP -> (float) (-2.0D * Math.pow(progress, 3.0D)
                    + 3.0D * Math.pow(progress, 2.0D));
            case EASE_IN_QUAD -> progress * progress;
            case EASE_OUT_QUAD -> progress * (2.0F - progress);
            case EASE_IN_OUT_QUAD -> progress < 0.5F
                    ? 2.0F * progress * progress
                    : -1.0F + (4.0F - 2.0F * progress) * progress;
            case EASE_IN_CUBIC -> cube(progress);
            case EASE_OUT_CUBIC -> cube(progress - 1.0F) + 1.0F;
            case EASE_IN_OUT_CUBIC -> progress < 0.5F
                    ? 4.0F * cube(progress)
                    : (progress - 1.0F) * (2.0F * progress - 2.0F) * (2.0F * progress - 2.0F) + 1.0F;
            case EASE_IN_QUART -> fourthPower(progress);
            case EASE_OUT_QUART -> 1.0F - fourthPower(progress - 1.0F);
            case EASE_IN_OUT_QUART -> progress < 0.5F
                    ? 8.0F * fourthPower(progress)
                    : 1.0F - 8.0F * fourthPower(progress - 1.0F);
            case EASE_IN_QUINT -> fifthPower(progress);
            case EASE_OUT_QUINT -> 1.0F + fifthPower(progress - 1.0F);
            case EASE_IN_OUT_QUINT -> progress < 0.5F
                    ? 16.0F * fifthPower(progress)
                    : 1.0F + 16.0F * fifthPower(progress - 1.0F);
            case EASE_IN_SINE -> 1.0F - Mth.cos((float) (progress * Math.PI * 0.5D));
            case EASE_OUT_SINE -> Mth.sin((float) (progress * Math.PI * 0.5D));
            case EASE_IN_OUT_SINE -> 1.0F - Mth.cos((float) (Math.PI * progress * 0.5D));
            case EASE_IN_EXPO -> progress == 0.0F
                    ? 0.0F
                    : (float) Math.pow(2.0D, 10.0F * progress - 10.0F);
            case EASE_OUT_EXPO -> progress == 1.0F
                    ? 1.0F
                    : 1.0F - (float) Math.pow(2.0D, -10.0F * progress);
            case EASE_IN_OUT_EXPO -> exponentialInOut(progress);
            case EASE_IN_CIRC -> 1.0F - (float) Math.sqrt(1.0F - progress * progress);
            case EASE_OUT_CIRC -> {
                float shifted = progress - 1.0F;
                yield (float) Math.sqrt(1.0F - shifted * shifted);
            }
            case EASE_IN_OUT_CIRC -> progress < 0.5F
                    ? (1.0F - (float) Math.sqrt(1.0F - 4.0F * progress * progress)) * 0.5F
                    : ((float) Math.sqrt(1.0F - 4.0F * (progress - 1.0F) * progress) + 1.0F) * 0.5F;
            case SIGMOID -> 1.0F / (1.0F + (float) Math.exp(-progress));
            case EASE_OUT_ELASTIC -> elasticOut(progress);
            case EASE_IN_BACK -> (BACK_OVERSHOOT + 1.0F) * cube(progress)
                    - BACK_OVERSHOOT * progress * progress;
            case DYNAMIC_ISLAND -> (float) (1.0D
                    - Math.cos(progress * Math.PI * (0.2D + 2.5D * Math.pow(progress, 3.0D)))
                    * Math.exp(-progress * 5.0F));
        };
    }

    private static float exponentialInOut(float progress) {
        if (progress == 0.0F || progress == 1.0F) {
            return progress;
        }
        if (progress < 0.5F) {
            return (float) Math.pow(2.0D, 20.0F * progress - 10.0F) * 0.5F;
        }
        return (2.0F - (float) Math.pow(2.0D, -20.0F * progress + 10.0F)) * 0.5F;
    }

    private static float elasticOut(float progress) {
        if (progress == 0.0F || progress == 1.0F) {
            return progress;
        }
        double period = (2.0D * Math.PI) / 3.0D;
        return (float) (Math.pow(2.0D, -10.0F * progress)
                * Math.sin((progress * 10.0F - 0.75F) * period) * 0.5F + 1.0D);
    }

    private static float cube(float value) {
        return value * value * value;
    }

    private static float fourthPower(float value) {
        float square = value * value;
        return square * square;
    }

    private static float fifthPower(float value) {
        return fourthPower(value) * value;
    }
}
