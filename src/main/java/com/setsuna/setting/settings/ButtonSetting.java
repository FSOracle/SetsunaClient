package com.setsuna.setting.settings;

import com.setsuna.setting.Setting;
import com.google.gson.JsonElement;

/**
 * A clickable action with no stored value. Not persisted.
 */
public class ButtonSetting extends Setting<Runnable> {

    public ButtonSetting(String name, Runnable action) {
        super(name, action);
    }

    /** Runs the bound action. */
    public void press() {
        Runnable action = get();
        if (action != null) {
            action.run();
        }
    }

    @Override
    public JsonElement toJson() {
        return null;
    }

    @Override
    public void fromJson(JsonElement json) {
        // Nothing to restore.
    }
}
