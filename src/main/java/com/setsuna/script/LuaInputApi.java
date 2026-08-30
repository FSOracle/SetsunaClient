package com.setsuna.script;

import com.mojang.blaze3d.platform.InputConstants;
import com.setsuna.Setsuna;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Locale;

/** Raw keyboard and mouse state queries for runtime callbacks. */
final class LuaInputApi {

    private LuaInputApi() {
    }

    static LuaTable create() {
        LuaTable input = new LuaTable();
        input.set("is_down", LuaApiSupport.method(input, (args, first) -> {
            LuaExecutionGuard.requireRuntimeCallback();
            int key = keyCode(args.arg(first));
            return LuaValue.valueOf(InputConstants.isKeyDown(Setsuna.mc().getWindow(), key));
        }));
        input.set("mouse_down", LuaApiSupport.method(input, (args, first) -> {
            LuaExecutionGuard.requireRuntimeCallback();
            int button = args.arg(first).checkint();
            if (button < 0 || button > GLFW.GLFW_MOUSE_BUTTON_LAST) {
                throw new LuaError("mouse button is outside the GLFW range");
            }
            return LuaValue.valueOf(GLFW.glfwGetMouseButton(
                    Setsuna.mc().getWindow().handle(), button) == GLFW.GLFW_PRESS);
        }));
        input.set("cursor", LuaApiSupport.method(input, (args, first) -> {
            LuaExecutionGuard.requireRuntimeCallback();
            return cursorSnapshot();
        }));
        input.set("cursor_grabbed", LuaApiSupport.method(input, (args, first) -> {
            LuaExecutionGuard.requireRuntimeCallback();
            return LuaValue.valueOf(Setsuna.mc().mouseHandler.isMouseGrabbed());
        }));
        input.set("key_code", LuaApiSupport.method(input, (args, first) ->
                LuaValue.valueOf(keyCode(args.arg(first)))));
        return input;
    }

    static LuaTable cursorSnapshot() {
        MouseHandler mouse = Setsuna.mc().mouseHandler;
        var window = Setsuna.mc().getWindow();
        double rawX = mouse.xpos();
        double rawY = mouse.ypos();
        double x = MouseHandler.getScaledXPos(window, rawX);
        double y = MouseHandler.getScaledYPos(window, rawY);
        LuaTable result = new LuaTable();
        result.set("x", x);
        result.set("y", y);
        result.set("raw_x", rawX);
        result.set("raw_y", rawY);
        result.set("grabbed", LuaValue.valueOf(mouse.isMouseGrabbed()));
        result.set("inside", LuaValue.valueOf(
                x >= 0.0 && y >= 0.0
                        && x <= window.getGuiScaledWidth()
                        && y <= window.getGuiScaledHeight()));
        return result;
    }

    static int keyCode(LuaValue value) {
        if (value.isnumber()) return checkedKey(value.checkint());
        String name = value.checkjstring().trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        if (name.startsWith("GLFW_KEY_")) name = name.substring("GLFW_KEY_".length());
        if (name.length() == 1) {
            char character = name.charAt(0);
            if ((character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')) {
                return checkedKey(character);
            }
        }
        if (name.matches("F(?:[1-9]|1[0-9]|2[0-5])")) {
            return checkedKey(GLFW.GLFW_KEY_F1 + Integer.parseInt(name.substring(1)) - 1);
        }
        return checkedKey(switch (name) {
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "APOSTROPHE" -> GLFW.GLFW_KEY_APOSTROPHE;
            case "COMMA" -> GLFW.GLFW_KEY_COMMA;
            case "MINUS" -> GLFW.GLFW_KEY_MINUS;
            case "PERIOD", "DOT" -> GLFW.GLFW_KEY_PERIOD;
            case "SLASH" -> GLFW.GLFW_KEY_SLASH;
            case "SEMICOLON" -> GLFW.GLFW_KEY_SEMICOLON;
            case "EQUAL" -> GLFW.GLFW_KEY_EQUAL;
            case "LEFT_BRACKET" -> GLFW.GLFW_KEY_LEFT_BRACKET;
            case "BACKSLASH" -> GLFW.GLFW_KEY_BACKSLASH;
            case "RIGHT_BRACKET" -> GLFW.GLFW_KEY_RIGHT_BRACKET;
            case "GRAVE_ACCENT", "GRAVE" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
            case "INSERT" -> GLFW.GLFW_KEY_INSERT;
            case "DELETE" -> GLFW.GLFW_KEY_DELETE;
            case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            case "LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "DOWN" -> GLFW.GLFW_KEY_DOWN;
            case "UP" -> GLFW.GLFW_KEY_UP;
            case "PAGE_UP" -> GLFW.GLFW_KEY_PAGE_UP;
            case "PAGE_DOWN" -> GLFW.GLFW_KEY_PAGE_DOWN;
            case "HOME" -> GLFW.GLFW_KEY_HOME;
            case "END" -> GLFW.GLFW_KEY_END;
            case "CAPS_LOCK" -> GLFW.GLFW_KEY_CAPS_LOCK;
            case "SCROLL_LOCK" -> GLFW.GLFW_KEY_SCROLL_LOCK;
            case "NUM_LOCK" -> GLFW.GLFW_KEY_NUM_LOCK;
            case "PRINT_SCREEN" -> GLFW.GLFW_KEY_PRINT_SCREEN;
            case "PAUSE" -> GLFW.GLFW_KEY_PAUSE;
            case "LEFT_SHIFT", "LSHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "LEFT_CONTROL", "LEFT_CTRL", "LCTRL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "LEFT_ALT", "LALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "LEFT_SUPER" -> GLFW.GLFW_KEY_LEFT_SUPER;
            case "RIGHT_SHIFT", "RSHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "RIGHT_CONTROL", "RIGHT_CTRL", "RCTRL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "RIGHT_ALT", "RALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "RIGHT_SUPER" -> GLFW.GLFW_KEY_RIGHT_SUPER;
            case "MENU" -> GLFW.GLFW_KEY_MENU;
            default -> throw new LuaError("Unknown GLFW key: " + name);
        });
    }

    private static int checkedKey(int key) {
        if (key < GLFW.GLFW_KEY_SPACE || key > GLFW.GLFW_KEY_LAST) {
            throw new LuaError("key is outside the GLFW keyboard range");
        }
        return key;
    }
}
