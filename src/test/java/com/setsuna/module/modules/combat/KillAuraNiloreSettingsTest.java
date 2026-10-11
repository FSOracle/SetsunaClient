package com.setsuna.module.modules.combat;

import com.google.gson.JsonPrimitive;
import com.setsuna.setting.Setting;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraNiloreSettingsTest {

    @Test
    void heypixelModeOwnsItsTargetingRotationAndAnimationSettings() {
        KillAura aura = KillAura.INSTANCE;
        Setting<?> mode = setting(aura, "Mode");
        Setting<?> fakeBlock = setting(aura, "Heypixel/Fake AutoBlock");
        Setting<?> animation = setting(aura, "Heypixel/Animation");
        Setting<?> throughWalls = setting(aura, "Heypixel/Through Walls");

        try {
            mode.fromJson(new JsonPrimitive("Nilore"));
            assertEquals("Heypixel", mode.toJson().getAsString());
            assertTrue(fakeBlock.legacyNames().contains("Nilore/Fake AutoBlock"));
            mode.fromJson(new JsonPrimitive("Heypixel"));
            assertVisible(aura,
                    "Heypixel/Attack Player",
                    "Heypixel/Style",
                    "Heypixel/Rotation Speed",
                    "Heypixel/Animation");
            assertHidden(aura, "Range", "Heypixel/Animation Hand X", "Heypixel/Through Walls Range");

            throughWalls.fromJson(new JsonPrimitive(true));
            assertVisible(aura, "Heypixel/Through Walls Range");

            animation.fromJson(new JsonPrimitive("NewPushDown"));
            assertVisible(aura,
                    "Heypixel/Animation Hand X",
                    "Heypixel/Animation Hand Y",
                    "Heypixel/Animation Hand Z");

            fakeBlock.fromJson(new JsonPrimitive(false));
            assertHidden(aura, "Heypixel/Animation", "Heypixel/Animation Size", "Heypixel/Animation Hand X");

            mode.fromJson(new JsonPrimitive("OnePointEight"));
            assertVisible(aura, "Range");
            assertHidden(aura, "Heypixel/Attack Player", "Heypixel/Rotation Speed");
        } finally {
            mode.fromJson(new JsonPrimitive("OnePointEight"));
            fakeBlock.fromJson(new JsonPrimitive(true));
            animation.fromJson(new JsonPrimitive("Vanilla"));
            throughWalls.fromJson(new JsonPrimitive(false));
        }
    }

    private static Setting<?> setting(KillAura module, String name) {
        return module.settings().stream()
                .filter(setting -> setting.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> visibleNames(KillAura module) {
        return module.settings().stream()
                .filter(Setting::visible)
                .map(Setting::name)
                .collect(Collectors.toSet());
    }

    private static void assertVisible(KillAura module, String... names) {
        Set<String> visible = visibleNames(module);
        for (String name : names) {
            assertTrue(visible.contains(name), () -> name + " should be visible, got " + visible);
        }
    }

    private static void assertHidden(KillAura module, String... names) {
        Set<String> visible = visibleNames(module);
        for (String name : names) {
            assertFalse(visible.contains(name), () -> name + " should be hidden, got " + visible);
        }
    }
}
