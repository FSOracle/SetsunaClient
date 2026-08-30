package com.setsuna.ui;

import com.setsuna.module.Category;

/** Lucide glyphs shared by every client category surface. */
public final class CategoryGlyphs {

    public static final String CONFIG = "\uE247";

    private CategoryGlyphs() {
    }

    public static String forCategory(Category category) {
        return switch (category) {
            case COMBAT -> "\uE2B4";
            case MISC -> "\uE29C";
            case HUD -> "\uE1C1";
            case RENDER -> "\uE1DD";
            case MOVEMENT -> "\uE3B9";
            case PLAYER -> "\uE19F";
            case CLIENT -> "\uE154";
        };
    }
}
