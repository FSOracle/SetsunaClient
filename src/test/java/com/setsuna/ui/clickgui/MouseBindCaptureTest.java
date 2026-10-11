package com.setsuna.ui.clickgui;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.util.client.InputBind;
import com.setsuna.util.client.KeybindUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MouseBindCaptureTest {
    private static final List<Class<? extends Screen>> MODES = List.of(
            ClickGuiScreen.class, WindowClickGuiScreen.class,
            FluxClickGuiScreen.class, PopClickGuiScreen.class);

    @Test
    void everyModeCapturesBothSideButtonsBeforeDispatchingToControls() throws Exception {
        for (Class<? extends Screen> mode : MODES) {
            for (int button : new int[]{GLFW.GLFW_MOUSE_BUTTON_4, GLFW.GLFW_MOUSE_BUTTON_5}) {
                Screen screen = newScreen(mode);
                Module module = new Module("Bound module", Category.COMBAT) {};
                module.setBind(new InputBind(GLFW.GLFW_KEY_R, InputBind.BindAction.HOLD,
                        Set.of(InputBind.Modifier.SHIFT)));
                field(mode, "capturingModule").set(screen, module);

                // Off-screen coordinates prove capture does not depend on the hovered control.
                assertTrue(screen.mouseClicked(click(button), false), mode.getSimpleName());
                assertEquals(KeybindUtils.encodeMouseButton(button), module.keyBind(), mode.getSimpleName());
                assertEquals(InputBind.BindAction.HOLD, module.bind().action());
                assertEquals(Set.of(InputBind.Modifier.SHIFT), module.bind().modifiers());
                assertNull(field(mode, "capturingModule").get(screen));
            }
        }
    }

    @Test
    void everyModeCapturesSettingSideButtonsAndEndsCapture() throws Exception {
        for (Class<? extends Screen> mode : MODES) {
            for (int button : new int[]{GLFW.GLFW_MOUSE_BUTTON_4, GLFW.GLFW_MOUSE_BUTTON_5}) {
                Screen screen = newScreen(mode);
                KeybindSetting setting = new KeybindSetting("Action", GLFW.GLFW_KEY_R);
                field(mode, "capturingKeybind").set(screen, setting);

                assertTrue(screen.mouseClicked(click(button), false), mode.getSimpleName());
                assertTrue(setting.matchesMouse(button), mode.getSimpleName());
                assertNull(field(mode, "capturingKeybind").get(screen));
                assertNull(field(mode, "capturingModule").get(screen));
            }
        }
    }

    private static MouseButtonEvent click(int button) {
        return new MouseButtonEvent(-1000, -1000, new MouseButtonInfo(button, 0));
    }

    private static Screen newScreen(Class<? extends Screen> type) throws Exception {
        // Screen construction needs a game window; input capture only uses these two local fields.
        Field unsafe = field(Unsafe.class, "theUnsafe");
        return (Screen) ((Unsafe) unsafe.get(null)).allocateInstance(type);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
