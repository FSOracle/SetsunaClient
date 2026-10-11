package com.setsuna.util.client;

import com.google.gson.JsonPrimitive;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.util.KeyBindText;
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

    @Test
    void sideButtonsKeepTheirLegacyEncodingAndReadableLabels() {
        for (int button : new int[]{GLFW.GLFW_MOUSE_BUTTON_4, GLFW.GLFW_MOUSE_BUTTON_5}) {
            int encoded = KeybindUtils.encodeMouseButton(button);
            assertEquals(-2 - button, encoded);
            assertEquals(button, KeybindUtils.decodeMouseButton(encoded));
            assertEquals("Mouse " + (button + 1), KeyBindText.of(encoded));
            InputBind restored = InputBind.fromJson(new JsonPrimitive(encoded));
            assertTrue(restored.matchesMouse(button));
            assertFalse(restored.matchesKey(button));
            assertFalse(restored.isUnbound());
        }
        assertEquals("", KeyBindText.of(KeybindUtils.NONE));
    }

    @Test
    void settingMouseBindingsSurviveConfigRoundTrip() {
        KeybindSetting setting = new KeybindSetting("Action", GLFW.GLFW_KEY_R);
        setting.set(KeybindUtils.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_5));
        KeybindSetting restored = new KeybindSetting("Action", KeybindSetting.NONE);
        restored.fromJson(setting.toJson());

        assertTrue(restored.isBound());
        assertTrue(restored.matchesMouse(GLFW.GLFW_MOUSE_BUTTON_5));
        assertFalse(restored.matchesMouse(GLFW.GLFW_MOUSE_BUTTON_4));
        assertFalse(restored.matches(GLFW.GLFW_KEY_R));
        restored.set(KeybindSetting.NONE);
        assertFalse(restored.matchesMouse(GLFW.GLFW_MOUSE_BUTTON_5));
    }

    @Test
    void primaryClicksRemainAvailableForGuiNavigation() {
        assertFalse(KeybindUtils.isAuxiliaryMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT));
        assertFalse(KeybindUtils.isAuxiliaryMouseButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        assertTrue(KeybindUtils.isAuxiliaryMouseButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
        assertTrue(KeybindUtils.isAuxiliaryMouseButton(GLFW.GLFW_MOUSE_BUTTON_4));
        assertTrue(KeybindUtils.isAuxiliaryMouseButton(GLFW.GLFW_MOUSE_BUTTON_5));
        assertFalse(KeybindUtils.isAuxiliaryMouseButton(GLFW.GLFW_MOUSE_BUTTON_LAST + 1));
        assertFalse(KeybindUtils.isAuxiliaryMouseButton(-1));
    }
}
