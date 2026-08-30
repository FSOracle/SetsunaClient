package com.setsuna.setting.settings;

import com.setsuna.setting.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A free-form text value. */
public class StringSetting extends Setting<String> {

    public StringSetting(String name, String defaultValue) {
        super(name, defaultValue);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            set(json.getAsString());
        }
    }
}
