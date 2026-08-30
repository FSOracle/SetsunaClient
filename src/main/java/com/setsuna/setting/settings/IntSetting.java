package com.setsuna.setting.settings;

import com.setsuna.setting.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An integer value constrained to {@code [min, max]} with a UI/scroll step. */
public class IntSetting extends Setting<Integer> {

    private final int min;
    private final int max;
    private final int step;

    public IntSetting(String name, int defaultValue, int min, int max, int step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
    }

    @Override
    public void set(Integer newValue) {
        super.set(clamp(newValue));
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int step() {
        return step;
    }

    /** Position of the current value within {@code [min, max]} as {@code 0..1}. */
    public float fraction() {
        if (max == min) {
            return 0.0F;
        }
        return (float) (get() - min) / (float) (max - min);
    }

    private int clamp(int v) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            set(json.getAsInt());
        }
    }
}
