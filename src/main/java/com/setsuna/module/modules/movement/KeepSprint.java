package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.EnumSetting;

public final class KeepSprint extends Module {

    public static final KeepSprint INSTANCE = new KeepSprint();

    public enum Mode {
        Normal
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Normal));

    private KeepSprint() {
        super("Keep Sprint", Category.MOVEMENT);
    }

    @Override
    public String getInfo() {
        return mode.displayValue();
    }

    @Listen
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mode.is(Mode.Normal)) {
            event.setSprint(true);
        }
    }
}
