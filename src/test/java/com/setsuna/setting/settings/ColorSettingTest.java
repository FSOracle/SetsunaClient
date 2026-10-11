package com.setsuna.setting.settings;

import com.setsuna.ui.VisualColorPreset;
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

    @Test
    void globalVisualPresetMapsPrimaryAndSecondaryWithoutReplacingStoredColors() {
        ColorSetting primary = new ColorSetting("Single Color", new Color(12, 34, 56, 90), true);
        ColorSetting secondary = new ColorSetting("End Color", new Color(65, 43, 21, 120), true);
        ColorSetting excluded = new ColorSetting("Preset Color", new Color(7, 8, 9), false)
                .excludeFromVisualPreset();
        try {
            VisualColorPreset.configure(true, false,
                    new Color(200, 30, 40), new Color(20, 180, 220));

            assertEquals(new Color(200, 30, 40, 90), primary.get());
            assertEquals(new Color(20, 180, 220, 120), secondary.get());
            assertEquals(new Color(7, 8, 9), excluded.get());
            assertEquals(new Color(12, 34, 56, 90).getRGB(), primary.toJson().getAsInt());
        } finally {
            VisualColorPreset.configure(false, false, Color.WHITE, Color.WHITE);
        }
    }

    @Test
    void globalVisualPresetOnlyMapsBackgroundColorsWhenEnabled() {
        ColorSetting background = new ColorSetting(
                "Background Color", new Color(12, 34, 56, 90), true);
        ColorSetting infoBackground = new ColorSetting(
                "Info Background", new Color(40, 50, 60, 110), true);
        Color primary = new Color(200, 30, 40);
        Color secondary = new Color(20, 180, 220);
        try {
            VisualColorPreset.configure(true, false, primary, secondary);

            assertEquals(new Color(12, 34, 56, 90), background.get());
            assertEquals(new Color(40, 50, 60, 110), infoBackground.get());

            VisualColorPreset.configure(true, true, primary, secondary);

            assertEquals(new Color(200, 30, 40, 90), background.get());
            assertEquals(new Color(200, 30, 40, 110), infoBackground.get());
        } finally {
            VisualColorPreset.configure(false, false, Color.WHITE, Color.WHITE);
        }
    }

    private static String colorHex(ColorSetting setting, Color color) {
        setting.set(color);
        return setting.hex();
    }
}
