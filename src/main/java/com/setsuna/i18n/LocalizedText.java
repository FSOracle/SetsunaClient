package com.setsuna.i18n;

import com.setsuna.Setsuna;
import com.setsuna.util.StringUtil;

import java.util.regex.Pattern;

/** Shared fallback localization for setting labels and enum values. */
public final class LocalizedText {

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    private static final Pattern ACRONYM_BOUNDARY = Pattern.compile("(?<=[A-Z])(?=[A-Z][a-z])");

    private LocalizedText() {
    }

    public static String setting(TranslationKey specific, String name, String displayOverride) {
        if (displayOverride == null && specific != null && specific.exists()) {
            return specific.get();
        }
        String source = displayOverride == null ? name : displayOverride;
        return shared("setting", source);
    }

    public static String value(TranslationKey specific, String fallback) {
        if (specific != null && specific.exists()) {
            return specific.get();
        }
        return shared("value", fallback);
    }

    public static String freeform(String text) {
        return translateTerms(text);
    }

    private static String shared(String namespace, String source) {
        TranslationKey phrase = TranslationKey.of(
                Setsuna.MOD_ID + "." + namespace + "." + StringUtil.slug(source), source);
        if (phrase.exists()) {
            return phrase.get();
        }
        return translateTerms(source);
    }

    static String translateTerms(String source) {
        String expanded = ACRONYM_BOUNDARY.matcher(CAMEL_BOUNDARY.matcher(source).replaceAll(" "))
                .replaceAll(" ");
        StringBuilder result = new StringBuilder(expanded.length());
        boolean translated = false;
        int start = 0;
        for (int index = 0; index <= expanded.length(); index++) {
            boolean boundary = index == expanded.length()
                    || Character.isWhitespace(expanded.charAt(index))
                    || expanded.charAt(index) == '/'
                    || expanded.charAt(index) == '-';
            if (!boundary) {
                continue;
            }
            if (start < index) {
                String token = expanded.substring(start, index);
                String slug = StringUtil.slug(token);
                TranslationKey term = slug.isEmpty() ? null : TranslationKey.of(
                        Setsuna.MOD_ID + ".term." + slug, token);
                if (term != null && term.exists()) {
                    result.append(term.get());
                    translated = true;
                } else {
                    result.append(token);
                }
            }
            if (index < expanded.length()) {
                char separator = expanded.charAt(index);
                if (separator == '/') {
                    result.append(" / ");
                } else if (separator == '-') {
                    result.append('-');
                }
            }
            start = index + 1;
        }
        return translated ? result.toString() : source;
    }
}
