package com.setsuna.setting.settings;

import com.setsuna.setting.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

/**
 * A single GLFW key code, or {@link #NONE} when unbound.
 */
public class KeybindSetting extends Setting<Integer> {

    public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

    public KeybindSetting(String name, int defaultKey) {
        super(name, defaultKey);
    }

    public boolean isBound() {
        return get() != NONE;
    }

    public boolean matches(int key) {
        return isBound() && get() == key;
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
