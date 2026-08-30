package com.setsuna.setting.settings;

import com.setsuna.i18n.TranslationKey;
import com.setsuna.i18n.LocalizedText;
import com.setsuna.setting.Setting;
import com.setsuna.util.StringUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.EnumMap;
import java.util.Map;

/**
 * A choice among the constants of an enum. Values are stored by
 * {@link Enum#name()} and displayed via per-value child i18n keys, e.g.
 * {@code setsuna.module.demo.shape.circle}.
 *
 * @param <E> the enum type
 */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {

    private final E[] values;
    private final Map<E, TranslationKey> valueTitles;

    public EnumSetting(String name, E defaultValue) {
        super(name, defaultValue);
        this.values = defaultValue.getDeclaringClass().getEnumConstants();
        this.valueTitles = new EnumMap<>(defaultValue.getDeclaringClass());
    }

    @Override
    public void bindTitle(TranslationKey title) {
        super.bindTitle(title);
        valueTitles.clear();
        for (E constant : values) {
            valueTitles.put(constant, title.child(StringUtil.slug(constant.name()), prettify(constant)));
        }
    }

    /** Advances the selection by {@code direction} (wrapping), e.g. +1 / -1. */
    public void cycle(int direction) {
        int next = Math.floorMod(get().ordinal() + direction, values.length);
        set(values[next]);
    }

    public E[] values() {
        return values;
    }

    public boolean is(E other) {
        return get() == other;
    }

    /** Localized label of the current value, falling back to a prettified name. */
    public String displayValue() {
        TranslationKey key = valueTitles.get(get());
        return LocalizedText.value(key, prettify(get()));
    }

    private static String prettify(Enum<?> constant) {
        String name = constant.name();
        return name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get().name());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) {
            return;
        }
        String name = json.getAsString();
        for (E constant : values) {
            if (constant.name().equals(name)) {
                set(constant);
                return;
            }
        }
    }
}
