package com.setsuna.ui.screen;

import com.setsuna.Setsuna;
import net.minecraft.client.Minecraft;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;

/** Native image picker and persistence bridge for the shared menu backdrop. */
public final class MenuBackgroundSettings {

    private MenuBackgroundSettings() {
    }

    public static boolean importImage() {
        Minecraft minecraft = Minecraft.getInstance();
        String selected = TinyFileDialogs.tinyfd_openFileDialog(
                "Import Setsuna background",
                minecraft.gameDirectory.getAbsolutePath(),
                null,
                "PNG, JPG, BMP, or GIF image",
                false);
        if (selected == null || selected.isBlank()) {
            return false;
        }
        try {
            ScreenBackdrop.importMainMenuBackground(Path.of(selected));
            return true;
        } catch (IOException | RuntimeException exception) {
            Setsuna.LOGGER.warn("Unable to import menu background {}", selected, exception);
            showError(exception.getMessage());
            return false;
        }
    }

    public static boolean reset() {
        try {
            ScreenBackdrop.resetMainMenuBackground();
            return true;
        } catch (IOException exception) {
            Setsuna.LOGGER.warn("Unable to reset the custom menu background", exception);
            showError(exception.getMessage());
            return false;
        }
    }

    public static boolean canReset() {
        return ScreenBackdrop.canResetMainMenuBackground();
    }

    private static void showError(String detail) {
        String message = detail == null || detail.isBlank()
                ? "The selected image could not be imported."
                : detail;
        TinyFileDialogs.tinyfd_messageBox(
                "Setsuna background",
                message,
                "ok",
                "error",
                1);
    }
}
