package com.setsuna.ui.screen;

import com.setsuna.Setsuna;
import io.github.humbleui.skija.Image;
import net.minecraft.client.Minecraft;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

/** Imports and persists the optional NetEase music workspace background. */
public final class MusicBackgroundSettings {

    private static final long MAX_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_PIXELS = 40_000_000L;

    private MusicBackgroundSettings() {
    }

    public static void importImage() {
        Minecraft minecraft = Minecraft.getInstance();
        String selected = TinyFileDialogs.tinyfd_openFileDialog(
                "Import NetEase Music background",
                minecraft.gameDirectory.getAbsolutePath(),
                null,
                "PNG, JPG, BMP, or GIF image",
                false);
        if (selected == null || selected.isBlank()) {
            return;
        }

        Path source = Path.of(selected);
        Path target = backgroundPath();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        BufferedImage decoded = null;
        try {
            if (!Files.isRegularFile(source)) {
                throw new IOException("The selected image does not exist.");
            }
            long bytes = Files.size(source);
            if (bytes <= 0 || bytes > MAX_BYTES) {
                throw new IOException("The image must be smaller than 32 MB.");
            }
            decoded = decode(source);
            Files.createDirectories(target.getParent());
            if (!ImageIO.write(decoded, "png", temporary.toFile())) {
                throw new IOException("The image could not be converted to PNG.");
            }
            moveReplacing(temporary, target);
        } catch (IOException | RuntimeException exception) {
            Setsuna.LOGGER.warn("Unable to import NetEase Music background {}", source, exception);
            showError(exception.getMessage());
        } finally {
            if (decoded != null) {
                decoded.flush();
            }
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    public static void reset() {
        try {
            Files.deleteIfExists(backgroundPath());
        } catch (IOException exception) {
            Setsuna.LOGGER.warn("Unable to reset NetEase Music background", exception);
            showError(exception.getMessage());
        }
    }

    public static boolean hasImage() {
        return Files.isRegularFile(backgroundPath());
    }

    static Image loadImage() {
        Path path = backgroundPath();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Image.makeFromEncoded(Files.readAllBytes(path));
        } catch (IOException | RuntimeException exception) {
            Setsuna.LOGGER.warn("Unable to load NetEase Music background", exception);
            return null;
        }
    }

    private static BufferedImage decode(Path source) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(source.toFile())) {
            if (stream == null) {
                throw new IOException("The selected file is not a supported image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Use a PNG, JPG, BMP, or GIF image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("The image resolution is too large.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("The selected image could not be decoded.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static Path backgroundPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(Setsuna.MOD_ID)
                .resolve("backgrounds")
                .resolve("netease-music.png");
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void showError(String detail) {
        TinyFileDialogs.tinyfd_messageBox(
                "NetEase Music background",
                detail == null || detail.isBlank() ? "The selected image could not be imported." : detail,
                "ok",
                "error",
                1);
    }
}
