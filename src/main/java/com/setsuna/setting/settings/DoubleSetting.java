package com.setsuna.setting.settings;

import com.setsuna.setting.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A double value constrained to {@code [min, max]} with a UI/scroll step. */
public class DoubleSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;

    public DoubleSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step <= 0.0 ? 0.01 : step;
    }

    @Override
    public void set(Double newValue) {
        if (newValue == null || !Double.isFinite(newValue)) {
            return;
        }
        super.set(clamp(newValue));
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    /** Position of the current value within {@code [min, max]} as {@code 0..1}. */
    public float fraction() {
        if (max == min) {
            return 0.0F;
        }
        return (float) ((get() - min) / (max - min));
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            double parsed = json.getAsDouble();
            if (Double.isFinite(parsed)) {
                set(parsed);
            }
        }
    }
}
