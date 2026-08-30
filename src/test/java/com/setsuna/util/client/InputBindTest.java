package com.setsuna.util.client;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InputBindTest {

    @Test
    void migratesLegacyIntegerBindingsToToggle() {
        InputBind bind = InputBind.fromJson(new JsonPrimitive(GLFW.GLFW_KEY_R));

        assertEquals(GLFW.GLFW_KEY_R, bind.keyBind());
        assertEquals(InputBind.BindAction.TOGGLE, bind.action());
        assertTrue(bind.modifiers().isEmpty());
    }

    @Test
    void roundTripsActionsModifiersAndMouseButtons() {
        InputBind original = new InputBind(
                KeybindUtils.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE),
                InputBind.BindAction.SMART,
                EnumSet.of(InputBind.Modifier.SHIFT, InputBind.Modifier.ALT));

        InputBind restored = InputBind.fromJson(original.toJson());

        assertEquals(original, restored);
        assertTrue(restored.matchesMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
        assertFalse(restored.matchesKey(GLFW.GLFW_KEY_M));
        assertTrue(restored.matchesModifiers(GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT));
        assertFalse(restored.matchesModifiers(GLFW.GLFW_MOD_SHIFT));
    }
}
