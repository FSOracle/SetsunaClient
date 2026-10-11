package com.setsuna.module;

import com.setsuna.event.events.MouseButtonEvent;
import com.setsuna.ui.clickgui.ClickGuiScreen;
import com.setsuna.util.client.InputBind;
import com.setsuna.util.client.KeybindUtils;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModuleMouseBindingTest {
    @Test
    void sideButtonsToggleOnlyTheirMatchingModuleOutsideScreens() throws Exception {
        Minecraft game = allocate(Minecraft.class);
        ModuleManager manager = allocate(ModuleManager.class);
        CountingModule first = new CountingModule();
        CountingModule second = new CountingModule();
        first.setKeyBind(KeybindUtils.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_4));
        second.setBind(new InputBind(KeybindUtils.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_5),
                InputBind.BindAction.TOGGLE, Set.of(InputBind.Modifier.SHIFT)));
        field(ModuleManager.class, "mc").set(manager, game);
        field(ModuleManager.class, "modules").set(manager, List.of(first, second));
        Method onMouse = ModuleManager.class.getDeclaredMethod("onMouse", MouseButtonEvent.class);
        onMouse.setAccessible(true);

        onMouse.invoke(manager, new MouseButtonEvent(GLFW.GLFW_MOUSE_BUTTON_4, GLFW.GLFW_PRESS, 0));
        assertEquals(1, first.toggles);
        assertEquals(0, second.toggles);
        onMouse.invoke(manager, new MouseButtonEvent(GLFW.GLFW_MOUSE_BUTTON_4, GLFW.GLFW_RELEASE, 0));
        assertEquals(1, first.toggles);

        onMouse.invoke(manager, new MouseButtonEvent(GLFW.GLFW_MOUSE_BUTTON_5, GLFW.GLFW_PRESS, 0));
        assertEquals(0, second.toggles);
        onMouse.invoke(manager, new MouseButtonEvent(GLFW.GLFW_MOUSE_BUTTON_5, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_SHIFT));
        assertEquals(1, second.toggles);

        game.screen = allocate(ClickGuiScreen.class);
        onMouse.invoke(manager, new MouseButtonEvent(GLFW.GLFW_MOUSE_BUTTON_4, GLFW.GLFW_PRESS, 0));
        assertEquals(1, first.toggles, "Capturing a binding must not toggle its module");
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        return type.cast(unsafe.allocateInstance(type));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class CountingModule extends Module {
        private int toggles;

        private CountingModule() {
            super("Mouse binding", Category.COMBAT);
        }

        @Override
        public void toggle() {
            toggles++;
        }
    }
}
