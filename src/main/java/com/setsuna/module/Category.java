package com.setsuna.module;

import com.setsuna.Setsuna;
import com.setsuna.i18n.TranslationKey;

import java.util.Locale;

/**
 * Top-level grouping for modules. Each category owns an i18n key
 * ({@code setsuna.category.<name>}) used for its panel header in the GUI.
 */
public enum Category {

    COMBAT(true),
    MISC(true),
    RENDER(true),
    MOVEMENT(true),
    PLAYER(true),
    CLIENT(true),
    HUD(false);

    private final TranslationKey title;
    private final boolean guiVisible;

    Category(boolean guiVisible) {
        this.guiVisible = guiVisible;
        String slug = name().toLowerCase(Locale.ROOT);
        String fallback = name().charAt(0) + slug.substring(1);
        this.title = TranslationKey.of(Setsuna.MOD_ID + ".category." + slug, fallback);
    }

    /** Localized category label for the GUI. */
    public String displayName() {
        return title.get();
    }

    /** Internal categories still group modules, but never occupy a ClickGUI slot. */
    public boolean guiVisible() {
        return guiVisible;
    }

    public static int guiVisibleCount() {
        int count = 0;
        for (Category category : values()) {
            if (category.guiVisible) count++;
        }
        return count;
    }
}
