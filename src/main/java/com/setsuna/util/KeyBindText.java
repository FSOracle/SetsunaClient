package com.setsuna.util;

import com.setsuna.util.client.KeybindUtils;

/** Formats a shared keyboard or mouse binding into a short human-readable label. */
public final class KeyBindText {

    private KeyBindText() {
    }

    /** @return the key's display name, or {@code ""} when unbound. */
    public static String of(int keyCode) {
        if (keyCode == KeybindUtils.NONE) {
            return "";
        }
        try {
            return KeybindUtils.format(keyCode);
        } catch (Exception e) {
            return String.valueOf(keyCode);
        }
    }
}
