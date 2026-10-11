package com.setsuna.util;

import java.text.Normalizer;

/** Normalizes user-controlled client and module display names. */
public final class DisplayNames {

    public static final int MAX_CODE_POINTS = 64;

    private DisplayNames() {
    }

    public static String normalize(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("Display name cannot be null");
        }

        String normalized = Normalizer.normalize(rawName, Normalizer.Form.NFKC).trim();
        StringBuilder result = new StringBuilder();
        boolean pendingSpace = false;
        int count = 0;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                pendingSpace = result.length() > 0;
                continue;
            }
            if (Character.isISOControl(codePoint)) {
                continue;
            }
            if (pendingSpace && count < MAX_CODE_POINTS) {
                result.append(' ');
                count++;
            }
            pendingSpace = false;
            if (count >= MAX_CODE_POINTS) {
                break;
            }
            result.appendCodePoint(codePoint);
            count++;
        }

        String displayName = result.toString();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Display name cannot be empty");
        }
        return displayName;
    }
}
