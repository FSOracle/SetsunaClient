package com.setsuna.module.modules.combat;

import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktrackFakeLagSettingsTest {

    @Test
    void backtrackMatchesTheSourceSettingsAndRemovesLiquidBounceExtras() {
        Backtrack backtrack = Backtrack.INSTANCE;
        assertEquals(List.of(
                "Min Range", "Max Range", "Min Delay", "Max Delay", "Next Delay",
                "Tracking Buffer", "Chance", "Attack Timeout", "Target Mode",
                "Pause On Hurt", "Hurt Time Threshold"), names(backtrack));

        assertDouble(backtrack, "Min Range", 1.0, 0.0, 10.0, 0.1);
        assertDouble(backtrack, "Max Range", 3.0, 0.0, 10.0, 0.1);
        assertInt(backtrack, "Min Delay", 100, 0, 1000, 10);
        assertInt(backtrack, "Max Delay", 150, 0, 1000, 10);
        assertInt(backtrack, "Next Delay", 10, 0, 2000, 5);
        assertInt(backtrack, "Tracking Buffer", 500, 0, 2000, 50);
        assertInt(backtrack, "Attack Timeout", 1000, 0, 5000, 100);
        assertInt(backtrack, "Hurt Time Threshold", 3, 0, 10, 1);
        assertEquals("Attack", setting(backtrack, "Target Mode").toJson().getAsString());
        assertTrue(backtrack.settings().stream().allMatch(Setting::visible));
    }

    @Test
    void fakeLagMatchesTheSourceSettingsAndDefaultFlushSelection() {
        FakeLag fakeLag = FakeLag.INSTANCE;
        assertEquals(List.of(
                "Min Range", "Max Range", "Min Delay", "Max Delay", "Recoil Time",
                "Mode", "Entity Interact", "Block Interact", "Action"), names(fakeLag));

        assertDouble(fakeLag, "Min Range", 2.0, 0.0, 10.0, 0.1);
        assertDouble(fakeLag, "Max Range", 5.0, 0.0, 10.0, 0.1);
        assertInt(fakeLag, "Min Delay", 300, 0, 1000, 10);
        assertInt(fakeLag, "Max Delay", 600, 0, 1000, 10);
        assertInt(fakeLag, "Recoil Time", 250, 0, 1000, 10);
        assertEquals("Dynamic", setting(fakeLag, "Mode").toJson().getAsString());
        assertEquals(true, setting(fakeLag, "Entity Interact").defaultValue());
        assertEquals(true, setting(fakeLag, "Block Interact").defaultValue());
        assertEquals(true, setting(fakeLag, "Action").defaultValue());
    }

    private static List<String> names(com.setsuna.module.Module module) {
        return module.settings().stream().map(Setting::name).toList();
    }

    private static Setting<?> setting(com.setsuna.module.Module module, String name) {
        return module.settings().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static void assertInt(
            com.setsuna.module.Module module,
            String name,
            int value,
            int minimum,
            int maximum,
            int step) {
        IntSetting setting = (IntSetting) setting(module, name);
        assertEquals(value, setting.defaultValue());
        assertEquals(minimum, setting.min());
        assertEquals(maximum, setting.max());
        assertEquals(step, setting.step());
    }

    private static void assertDouble(
            com.setsuna.module.Module module,
            String name,
            double value,
            double minimum,
            double maximum,
            double step) {
        DoubleSetting setting = (DoubleSetting) setting(module, name);
        assertEquals(value, setting.defaultValue());
        assertEquals(minimum, setting.min());
        assertEquals(maximum, setting.max());
        assertEquals(step, setting.step());
    }
}
