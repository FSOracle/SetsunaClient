package com.setsuna.util;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** Formats a GLFW key code into a short human-readable label. */
public final class KeyBindText {

    private KeyBindText() {
    }

    /** @return the key's display name, or {@code ""} when unbound. */
    public static String of(int keyCode) {
        if (keyCode == -1 || keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return "";
        }
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
        } catch (Exception e) {
            return String.valueOf(keyCode);
        }
    }
}
