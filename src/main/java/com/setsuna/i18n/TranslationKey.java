package com.setsuna.i18n;

import net.minecraft.client.resources.language.I18n;

/**
 * A lazily-resolved i18n key with a human-readable fallback.
 * <p>
 * Keys form a hierarchy: a module owns a root key such as
 * {@code setsuna.module.sprint}, and its settings/enum values hang off it via
 * {@link #child(String, String)} (e.g. {@code setsuna.module.sprint.only_on_ground}).
 * Resolution goes through vanilla {@link I18n}, so it honours the player's
 * selected language and updates on resource reload; when no entry exists the
 * fallback (the original English name) is shown instead of a raw key.
 */
public final class TranslationKey {

    private final String key;
    private final String fallback;

    private TranslationKey(String key, String fallback) {
        this.key = key;
        this.fallback = fallback;
    }

    public static TranslationKey of(String key, String fallback) {
        return new TranslationKey(key, fallback);
    }

    /** The full translation key, e.g. {@code setsuna.module.sprint}. */
    public String key() {
        return key;
    }

    /** The resolved, localized text, or the fallback when untranslated. */
    public String get() {
        return I18n.exists(key) ? I18n.get(key) : fallback;
    }

    public boolean exists() {
        return I18n.exists(key);
    }

    /** Derives a nested key, e.g. a setting or enum-value under this one. */
    public TranslationKey child(String suffix, String fallback) {
        return new TranslationKey(key + "." + suffix, fallback);
    }
}
