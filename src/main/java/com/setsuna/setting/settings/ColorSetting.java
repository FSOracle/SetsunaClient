package com.setsuna.setting.settings;

import com.setsuna.setting.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.awt.Color;
import java.util.Locale;

/**
 * An RGBA color. When {@code allowAlpha} is false the alpha channel is pinned to
 * fully opaque, both on input and after deserialization.
 */
public class ColorSetting extends Setting<Color> {

    private final boolean allowAlpha;

    public ColorSetting(String name, Color defaultValue, boolean allowAlpha) {
        super(name, allowAlpha ? defaultValue : opaque(defaultValue));
        this.allowAlpha = allowAlpha;
    }

    public ColorSetting(String name, Color defaultValue) {
        this(name, defaultValue, true);
    }

    public boolean allowAlpha() {
        return allowAlpha;
    }

    @Override
    public void set(Color newValue) {
        super.set(allowAlpha ? newValue : opaque(newValue));
    }

    /** The color packed as {@code 0xAARRGGBB}, ready for {@code GuiGraphicsExtractor.fill}. */
    public int argb() {
        return get().getRGB();
    }

    public String hex() {
        return allowAlpha
                ? String.format(Locale.ROOT, "#%08X", get().getRGB())
                : String.format(Locale.ROOT, "#%06X", get().getRGB() & 0xFFFFFF);
    }

    /** Parses {@code RRGGBB} or, when enabled, {@code AARRGGBB}; the leading # is optional. */
    public Color parseHex(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != 6 && (!allowAlpha || value.length() != 8)) return null;
        try {
            long packed = Long.parseLong(value, 16);
            return value.length() == 8
                    ? new Color((int) packed, true)
                    : new Color((int) packed | 0xFF000000, true);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Color opaque(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get().getRGB());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            set(new Color(json.getAsInt(), true));
        }
    }
}
