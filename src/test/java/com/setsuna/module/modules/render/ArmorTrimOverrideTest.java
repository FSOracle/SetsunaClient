package com.setsuna.module.modules.render;

import com.google.gson.JsonPrimitive;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.EnumSetting;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorTrimOverrideTest {

    @Test
    void exposesIndependentTrimSettingsForEveryArmorPiece() {
        ArmorTrimOverride module = ArmorTrimOverride.INSTANCE;
        String[] pieces = {"Helmet", "Chestplate", "Leggings", "Boots"};
        Set<String> patterns = Arrays.stream(ArmorTrimOverride.Pattern.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        Set<String> materials = Arrays.stream(ArmorTrimOverride.Material.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertEquals(19, patterns.size());
        assertEquals(11, materials.size());

        for (String piece : pieces) {
            Setting<?> pattern = setting(module, piece + " Pattern");
            Setting<?> material = setting(module, piece + " Material");
            assertTrue(((EnumSetting<?>) pattern).isSlider());
            assertTrue(((EnumSetting<?>) material).isSlider());
            pattern.fromJson(new JsonPrimitive("SILENCE"));
            material.fromJson(new JsonPrimitive("AMETHYST"));
            assertEquals("SILENCE", pattern.toJson().getAsString());
            assertEquals("AMETHYST", material.toJson().getAsString());
            pattern.fromJson(new JsonPrimitive("SENTRY"));
            material.fromJson(new JsonPrimitive("DIAMOND"));
        }
        assertTrue(patterns.contains("NONE"));
        assertTrue(materials.contains("RESIN"));
    }

    @Test
    void trimSliderSelectsWholeEnumValuesAndIgnoresInvalidInput() {
        EnumSetting<ArmorTrimOverride.Pattern> pattern =
                new EnumSetting<>("Test Pattern", ArmorTrimOverride.Pattern.SENTRY).asSlider();
        pattern.setSliderFraction(1.0F);
        assertEquals(ArmorTrimOverride.Pattern.BOLT, pattern.get());
        assertEquals(1.0F, pattern.sliderFraction());
        pattern.setSliderFraction(0.0F);
        assertEquals(ArmorTrimOverride.Pattern.NONE, pattern.get());
        pattern.setSliderFraction(Float.NaN);
        assertEquals(ArmorTrimOverride.Pattern.NONE, pattern.get());
    }

    @Test
    void oldSingleTrimSettingsRemainReadableForEachPiece() {
        ArmorTrimOverride module = ArmorTrimOverride.INSTANCE;
        for (String piece : new String[]{"Helmet", "Chestplate", "Leggings", "Boots"}) {
            assertTrue(setting(module, piece + " Pattern").legacyNames().contains("Pattern"));
            assertTrue(setting(module, piece + " Material").legacyNames().contains("Material"));
        }
    }

    private static Setting<?> setting(ArmorTrimOverride module, String name) {
        return module.settings().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
