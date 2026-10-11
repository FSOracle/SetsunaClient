package com.setsuna.module.modules.movement;

import com.google.gson.JsonPrimitive;
import com.setsuna.setting.Setting;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoSlowNiloreSettingsTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void heypixelModeKeepsItsSettingsSeparateFromExistingImplementations() {
        NoSlow noSlow = NoSlow.INSTANCE;
        Setting<?> mode = setting(noSlow, "Mode");
        Setting<?> niloreMode = setting(noSlow, "Heypixel/Mode");
        Setting<?> niloreBow = setting(noSlow, "Heypixel/Bow");

        try {
            mode.fromJson(new JsonPrimitive("Nilore"));
            assertEquals("Heypixel", mode.toJson().getAsString());
            assertTrue(niloreMode.legacyNames().contains("Nilore/Mode"));
            mode.fromJson(new JsonPrimitive("Heypixel"));
            niloreMode.fromJson(new JsonPrimitive("GrimV3"));
            niloreBow.fromJson(new JsonPrimitive(false));
            assertVisible(noSlow, "Heypixel/Mode", "Heypixel/Bow", "Heypixel/Food");
            assertHidden(noSlow, "Keep Sprinting", "Food", "Heypixel/Use Item Ticks");

            niloreBow.fromJson(new JsonPrimitive(true));
            assertVisible(noSlow, "Heypixel/Use Item Ticks");

            niloreMode.fromJson(new JsonPrimitive("NoSlow"));
            assertHidden(noSlow, "Heypixel/Bow", "Heypixel/Use Item Ticks");

            mode.fromJson(new JsonPrimitive("NCP"));
            assertVisible(noSlow, "Keep Sprinting", "Food", "Bow", "Crossbow");
            assertHidden(noSlow, "Heypixel/Mode", "Heypixel/Food");
        } finally {
            mode.fromJson(new JsonPrimitive("FastNoSlow"));
            niloreMode.fromJson(new JsonPrimitive("GrimV3"));
            niloreBow.fromJson(new JsonPrimitive(false));
        }
    }

    private static Setting<?> setting(NoSlow module, String name) {
        return module.settings().stream()
                .filter(setting -> setting.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> visibleNames(NoSlow module) {
        return module.settings().stream()
                .filter(Setting::visible)
                .map(Setting::name)
                .collect(Collectors.toSet());
    }

    private static void assertVisible(NoSlow module, String... names) {
        Set<String> visible = visibleNames(module);
        for (String name : names) {
            assertTrue(visible.contains(name), () -> name + " should be visible, got " + visible);
        }
    }

    private static void assertHidden(NoSlow module, String... names) {
        Set<String> visible = visibleNames(module);
        for (String name : names) {
            assertFalse(visible.contains(name), () -> name + " should be hidden, got " + visible);
        }
    }
}
