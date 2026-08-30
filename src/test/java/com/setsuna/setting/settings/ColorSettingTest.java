package com.setsuna.setting.settings;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ColorSettingTest {

    @Test
    void parsesRgbWithOptionalHash() {
        ColorSetting setting = new ColorSetting("Color", Color.BLACK, false);

        assertEquals(new Color(0x12, 0xAB, 0xEF), setting.parseHex("#12ABEF"));
        assertEquals(new Color(0x12, 0xAB, 0xEF), setting.parseHex("12abef"));
    }

    @Test
    void parsesArgbWhenAlphaIsEnabled() {
        ColorSetting setting = new ColorSetting("Color", Color.BLACK, true);

        Color expected = new Color(0x12, 0x34, 0x56, 0x80);
        assertEquals(expected, setting.parseHex("80123456"));
        assertEquals("#80123456", colorHex(setting, expected));
    }

    @Test
    void rejectsBlankMalformedAndDisallowedAlpha() {
        ColorSetting opaque = new ColorSetting("Color", Color.BLACK, false);

        assertNull(opaque.parseHex(""));
        assertNull(opaque.parseHex("#12345"));
        assertNull(opaque.parseHex("#GG1122"));
        assertNull(opaque.parseHex("80123456"));
    }

    private static String colorHex(ColorSetting setting, Color color) {
        setting.set(color);
        return setting.hex();
    }
}
