package com.setsuna.util.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Converts the shared integer binding format into keyboard or mouse input. */
public final class KeybindUtils {

    public static final int NONE = -1;
    public static final int MOUSE_OFFSET = -2;

    private KeybindUtils() {
    }

    public static boolean isMouseButton(int binding) {
        return classify(binding) == BindingType.MOUSE;
    }

    public static int encodeMouseButton(int button) {
        return MOUSE_OFFSET - button;
    }

    public static int decodeMouseButton(int binding) {
        return MOUSE_OFFSET - binding;
    }

    /** Leaves primary clicks available for navigating or clearing a GUI binding. */
    public static boolean isAuxiliaryMouseButton(int button) {
        return button >= GLFW.GLFW_MOUSE_BUTTON_MIDDLE && button <= GLFW.GLFW_MOUSE_BUTTON_LAST;
    }

    public static boolean isPressed(int binding) {
        BindingType type = classify(binding);
        if (type == BindingType.NONE) {
            return false;
        }

        Window gameWindow = Minecraft.getInstance().getWindow();
        return switch (type) {
            case MOUSE -> GLFW.glfwGetMouseButton(
                    gameWindow.handle(), decodeMouseButton(binding)) == GLFW.GLFW_PRESS;
            case KEYBOARD -> InputConstants.isKeyDown(gameWindow, binding);
            case NONE -> false;
        };
    }

    public static boolean isPressed(KeyMapping mapping) {
        InputConstants.Key key = resolveKey(mapping);
        if (InputConstants.UNKNOWN.equals(key)) return false;
        Window window = Minecraft.getInstance().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    public static boolean click(KeyMapping mapping) {
        InputConstants.Key key = resolveKey(mapping);
        if (InputConstants.UNKNOWN.equals(key)) return false;
        KeyMapping.click(key);
        return true;
    }

    public static boolean matchesKey(KeyMapping mapping, int key, int scancode) {
        return mapping != null && mapping.matches(new net.minecraft.client.input.KeyEvent(key, scancode, 0));
    }

    public static boolean matchesMouse(KeyMapping mapping, int button) {
        InputConstants.Key bound = resolveKey(mapping);
        return bound.getType() == InputConstants.Type.MOUSE && bound.getValue() == button;
    }

    private static InputConstants.Key resolveKey(KeyMapping mapping) {
        return mapping == null || mapping.isUnbound()
                ? InputConstants.UNKNOWN
                : InputConstants.getKey(mapping.saveString());
    }

    public static String format(int binding) {
        return switch (classify(binding)) {
            case NONE -> "None";
            case MOUSE -> "Mouse " + (decodeMouseButton(binding) + 1);
            case KEYBOARD -> InputConstants.Type.KEYSYM
                    .getOrCreate(binding)
                    .getDisplayName()
                    .getString();
        };
    }

    private static BindingType classify(int binding) {
        if (binding == NONE) {
            return BindingType.NONE;
        }
        return binding <= MOUSE_OFFSET ? BindingType.MOUSE : BindingType.KEYBOARD;
    }

    private enum BindingType {
        NONE,
        KEYBOARD,
        MOUSE
    }
}
