package com.setsuna.ui.screen;

/** Session-scoped choice between Setsuna's home and Minecraft's title screen. */
public final class TitleScreenMode {

    private static boolean vanilla;

    private TitleScreenMode() {
    }

    public static void useVanilla() {
        vanilla = true;
    }

    public static void useSetsuna() {
        vanilla = false;
    }

    public static boolean isVanilla() {
        return vanilla;
    }
}
