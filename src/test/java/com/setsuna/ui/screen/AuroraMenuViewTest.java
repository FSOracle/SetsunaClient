package com.setsuna.ui.screen;

import com.setsuna.ui.hud.HUD;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuroraMenuViewTest {

    @Test
    void menuStyleDefaultsToClassicAndCanRestoreTheSavedChoice() {
        var setting = HUD.INSTANCE.mainMenuStyle;
        var previous = setting.get();
        try {
            assertEquals(MainMenuStyle.CLASSIC, setting.defaultValue());
            setting.set(MainMenuStyle.CLASSIC);
            var saved = setting.toJson();
            setting.reset();
            setting.fromJson(saved);
            assertEquals(MainMenuStyle.CLASSIC, setting.get());
        } finally {
            setting.set(previous);
        }
    }
}
