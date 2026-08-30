package com.setsuna.module.modules;

import com.setsuna.Setsuna;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.notification.NotificationManager;
import com.setsuna.notification.NotificationType;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.FontSetting;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Imports and selects fonts used by the client's Skija text renderer. */
public final class FontModule extends Module {

    public static final FontModule INSTANCE = new FontModule();

    public final FontSetting font = add(new FontSetting("Font", SkijaUi.CLIENT_FONT,
            SkijaUi::availableFontNames));
    public final ButtonSetting importFont = add(new ButtonSetting("Import Font", this::chooseFont));
    public final ButtonSetting reload = add(new ButtonSetting("Reload Fonts", this::reloadFonts));
    public final ButtonSetting openFolder = add(new ButtonSetting("Open Font Folder", this::openFontFolder));

    private FontModule() {
        super("Font", Category.CLIENT);
        setToggleable(false);
        font.onChange(SkijaUi::selectFont);
    }

    @Override
    protected void onTrigger() {
        chooseFont();
    }

    private void chooseFont() {
        if (isWindows()) {
            chooseWindowsFont();
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                FileDialog dialog = new FileDialog((Frame) null, "Import Setsuna font", FileDialog.LOAD);
                dialog.setFilenameFilter((directory, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc");
                });
                dialog.setVisible(true);
                String file = dialog.getFile();
                String directory = dialog.getDirectory();
                dialog.dispose();
                if (file != null && directory != null) {
                    Path selected = Path.of(directory, file);
                    mc.execute(() -> importFont(selected));
                }
            } catch (RuntimeException error) {
                Setsuna.LOGGER.warn("Could not open the font file picker", error);
                mc.execute(() -> notifyResult(NotificationType.ERROR, "Could not open file picker"));
            }
        });
    }

    private void chooseWindowsFont() {
        CompletableFuture.supplyAsync(() -> {
            String command = "$OutputEncoding=[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                    + "Add-Type -AssemblyName System.Windows.Forms;"
                    + "$dialog=New-Object System.Windows.Forms.OpenFileDialog;"
                    + "$dialog.Title='Import Setsuna font';"
                    + "$dialog.Filter='Font files (*.ttf;*.otf;*.ttc)|*.ttf;*.otf;*.ttc';"
                    + "if($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK)"
                    + "{[Console]::Out.Write($dialog.FileName)}";
            try {
                Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA",
                        "-WindowStyle", "Hidden", "-Command", command)
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int exitCode = process.waitFor();
                if (exitCode != 0) throw new IOException("Font picker exited with code " + exitCode);
                return output.isBlank() ? null : Path.of(output);
            } catch (IOException error) {
                throw new RuntimeException(error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(error);
            }
        }).thenAccept(selected -> {
            if (selected != null) mc.execute(() -> importFont(selected));
        }).exceptionally(error -> {
            Setsuna.LOGGER.warn("Could not open the Windows font file picker", error);
            mc.execute(() -> notifyResult(NotificationType.ERROR, "Could not open file picker"));
            return null;
        });
    }

    private void importFont(Path source) {
        try {
            String imported = SkijaUi.importFont(source);
            font.set(imported);
            notifyResult(NotificationType.SUCCESS, "Imported " + imported);
        } catch (IOException | RuntimeException error) {
            Setsuna.LOGGER.warn("Could not import font from {}", source, error);
            notifyResult(NotificationType.ERROR, "Invalid or unreadable font file");
        }
    }

    private void reloadFonts() {
        SkijaUi.reloadImportedFonts();
        font.reconcile();
        SkijaUi.selectFont(font.get());
        notifyResult(NotificationType.INFO, "Font list reloaded");
    }

    private void openFontFolder() {
        try {
            Path directory = SkijaUi.fontDirectory();
            if (isWindows()) {
                new ProcessBuilder("explorer.exe", directory.toString()).start();
                return;
            }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException("Desktop folder opening is unavailable");
            }
            Desktop.getDesktop().open(directory.toFile());
        } catch (IOException | RuntimeException error) {
            Setsuna.LOGGER.warn("Could not open the font directory", error);
            notifyResult(NotificationType.ERROR, "Could not open font folder");
        }
    }

    private static void notifyResult(NotificationType type, String message) {
        NotificationManager.INSTANCE.post(type, "Font", message);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
