package com.setsuna.setting.settings;

import com.setsuna.setting.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A simple on/off toggle. */
public class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            set(json.getAsBoolean());
        }
    }
}
