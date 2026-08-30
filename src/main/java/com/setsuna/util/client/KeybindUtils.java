package com.setsuna.util.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Encoding/formatting helpers for module keybinds stored as a single int.
 * <ul>
 *   <li>{@code -1} — unbound</li>
 *   <li>{@code >= 0} — GLFW key code</li>
 *   <li>{@code <= -2} — mouse button, {@code button = MOUSE_OFFSET - keyBind}</li>
 * </ul>
 */
public final class KeybindUtils {

    public static final int NONE = -1;
    public static final int MOUSE_OFFSET = -2;

    private static final Minecraft mc = Minecraft.getInstance();

    private KeybindUtils() {
    }

    public static boolean isMouseButton(int keyBind) {
        return keyBind <= MOUSE_OFFSET;
    }

    public static int encodeMouseButton(int button) {
        return MOUSE_OFFSET - button;
    }

    public static int decodeMouseButton(int keyBind) {
        return MOUSE_OFFSET - keyBind;
    }

    public static boolean isPressed(int keyBind) {
        if (keyBind == NONE) {
            return false;
        }
        Window window = mc.getWindow();
        if (isMouseButton(keyBind)) {
            return GLFW.glfwGetMouseButton(window.handle(), decodeMouseButton(keyBind)) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, keyBind);
    }

    public static String format(int keyBind) {
        if (keyBind == NONE) {
            return "None";
        }
        if (isMouseButton(keyBind)) {
            return "Mouse " + (decodeMouseButton(keyBind) + 1);
        }
        return InputConstants.Type.KEYSYM.getOrCreate(keyBind).getDisplayName().getString();
    }
}
