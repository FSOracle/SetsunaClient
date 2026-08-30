package com.setsuna.util;

import java.util.Locale;

/** Small string helpers used across the mod. */
public final class StringUtil {

    private StringUtil() {
    }

    /**
     * Converts a human-readable name into a translation-key-safe slug:
     * lower-cased, with every run of non-alphanumeric characters collapsed into a
     * single underscore and leading/trailing underscores removed.
     * <p>
     * e.g. {@code "Only On Ground" -> "only_on_ground"}, {@code "ClickGUI" -> "clickgui"}.
     */
    public static String slug(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                sb.append('_');
                lastUnderscore = true;
            }
        }
        int start = 0;
        int end = sb.length();
        while (start < end && sb.charAt(start) == '_') {
            start++;
        }
        while (end > start && sb.charAt(end - 1) == '_') {
            end--;
        }
        return sb.substring(start, end);
    }
}
