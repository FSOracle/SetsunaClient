package com.setsuna.module.modules;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.ui.VisualColorPreset;
import com.setsuna.ui.screen.ColorPresetScreen;

import java.awt.Color;

/** Opens the global two-color palette editor and persists its selected colors. */
public final class ColorPresets extends Module {

    public static final ColorPresets INSTANCE = new ColorPresets();

    public final BooleanSetting active = add(new BooleanSetting("Active", false)
            .visibleWhen(() -> false).onChange(ignored -> sync()));
    public final BooleanSetting applyBackgroundColors = add(new BooleanSetting(
            "Apply Background Colors", false)
            .visibleWhen(() -> false).onChange(ignored -> sync()));
    public final ColorSetting primary = add(new ColorSetting(
            "Primary", new Color(166, 86, 238), false).excludeFromVisualPreset()
            .visibleWhen(() -> false).onChange(ignored -> sync()));
    public final ColorSetting secondary = add(new ColorSetting(
            "Secondary", new Color(70, 206, 222), false).excludeFromVisualPreset()
            .visibleWhen(() -> false).onChange(ignored -> sync()));

    private ColorPresets() {
        super("Color Presets", Category.CLIENT);
        setToggleable(false);
        sync();
    }

    @Override
    protected void onTrigger() {
        mc.setScreen(new ColorPresetScreen(mc.screen));
    }

    public void setActive(boolean value) {
        active.set(value);
    }

    public void setApplyBackgroundColors(boolean value) {
        applyBackgroundColors.set(value);
    }

    public void setPrimary(Color color) {
        primary.set(color);
    }

    public void setSecondary(Color color) {
        secondary.set(color);
    }

    private void sync() {
        VisualColorPreset.configure(active.get(), applyBackgroundColors.get(),
                primary.raw(), secondary.raw());
    }
}
