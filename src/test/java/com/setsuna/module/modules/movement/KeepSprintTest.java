package com.setsuna.module.modules.movement;

import com.google.gson.JsonPrimitive;
import com.setsuna.setting.Setting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeepSprintTest {

    @Test
    void keepSprintSupportsVanillaAndHypixelModes() {
        KeepSprint module = KeepSprint.INSTANCE;
        Setting<?> mode = module.settings().stream()
                .filter(s -> s.name().equals("Mode"))
                .findFirst()
                .orElseThrow();

        mode.fromJson(new JsonPrimitive("Vanilla"));
        assertTrue(module.isVanillaMode());
        assertEquals("Vanilla", module.getInfo());

        mode.fromJson(new JsonPrimitive("Hypixel"));
        assertTrue(module.isHypixelMode());
        assertEquals("Hypixel", module.getInfo());

        mode.fromJson(new JsonPrimitive("Normal"));
        assertTrue(module.isVanillaMode());

        mode.fromJson(new JsonPrimitive("Vanilla"));
    }
}
