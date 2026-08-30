package com.setsuna.module.modules;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.clickgui.PopClickGuiScreen;
import com.setsuna.ui.clickgui.WindowClickGuiScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

/**
 * Opens the responsive Pop or Drop ClickGUI. Non-toggleable: pressing its keybind
 * (Right Shift by default) runs {@link #onTrigger()} instead of flipping an
 * enabled flag. The accent colour is shared by all client UI surfaces.
 */
public final class ClickGui extends Module {

    public static final ClickGui INSTANCE = new ClickGui();

    public enum Mode { Drop, Pop }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Pop));
    public final BooleanSetting daylightMode = add(new BooleanSetting("Daylight Mode", false)
            .visibleWhen(() -> mode.is(Mode.Pop)));
    public final IntSetting popBackgroundBlur = add(new IntSetting("Background Blur", 5, 0, 10, 1)
            .visibleWhen(() -> mode.is(Mode.Pop)));
    public final ColorSetting accent = add(new ColorSetting("Accent", new Color(166, 86, 238), false));
    public final IntSetting guiScale = add(new IntSetting("GUI Scale", 100, 65, 125, 5));

    private ClickGui() {
        super("ClickGUI", Category.CLIENT);
        setToggleable(false);
        setDefaultKeyBind(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    protected void onTrigger() {
        mc.setScreen(createScreen(null));
    }

    public Screen createScreen(Screen parent) {
        return switch (mode.get()) {
            case Drop -> new WindowClickGuiScreen(parent);
            case Pop -> new PopClickGuiScreen(parent);
        };
    }
}
