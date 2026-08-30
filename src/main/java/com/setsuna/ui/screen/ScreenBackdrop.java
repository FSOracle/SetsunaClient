package com.setsuna.ui.screen;

import com.setsuna.Setsuna;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

/** Full-screen animated architectural backdrop shared by standalone client screens. */
final class ScreenBackdrop {

    private static final Paint GRADIENT_PAINT = new Paint().setAntiAlias(true).setDither(true);
    private static final Paint LINE_PAINT = new Paint().setAntiAlias(true).setStrokeWidth(1.0F);
    private static final Paint IMAGE_PAINT = new Paint().setAntiAlias(true).setDither(true);
    private static final Image MAIN_MENU_BACKGROUND = loadImage(
            "/assets/setsuna/textures/mainmenu/background.png");
    private static final long MAX_IMPORT_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_IMPORT_PIXELS = 40_000_000L;
    private static Image customMainMenuBackground;
    private static boolean backgroundStateLoaded;
    private static boolean gridBackground;

    private ScreenBackdrop() {
    }

    static void draw(Canvas canvas, float width, float height, int shadeAlpha) {
        draw(canvas, width, height, seconds(), width * 0.5F, height * 0.5F, shadeAlpha);
    }

    static void drawMainMenu(Canvas canvas, float width, float height, int shadeAlpha) {
        drawMainMenu(canvas, width, height, seconds(), width * 0.5F, height * 0.5F,
                shadeAlpha);
    }

    static void drawMainMenu(Canvas canvas, float width, float height, float time,
                             float pointerX, float pointerY, int shadeAlpha) {
        ensureBackgroundStateLoaded();
        if (gridBackground) {
            draw(canvas, width, height, time, pointerX, pointerY, 18);
            return;
        }
        Image background = mainMenuBackground();
        if (background == null || width <= 0.0F || height <= 0.0F) {
            draw(canvas, width, height, shadeAlpha);
            return;
        }

        float imageWidth = background.getWidth();
        float imageHeight = background.getHeight();
        float viewportAspect = width / height;
        float imageAspect = imageWidth / imageHeight;
        float sourceWidth = imageWidth;
        float sourceHeight = imageHeight;
        if (viewportAspect > imageAspect) {
            sourceHeight = imageWidth / viewportAspect;
        } else {
            sourceWidth = imageHeight * viewportAspect;
        }
        float sourceX = (imageWidth - sourceWidth) * 0.5F;
        float sourceY = (imageHeight - sourceHeight) * 0.5F;
        canvas.drawImageRect(background,
                Rect.makeXYWH(sourceX, sourceY, sourceWidth, sourceHeight),
                Rect.makeXYWH(0.0F, 0.0F, width, height),
                SamplingMode.MITCHELL, IMAGE_PAINT, true);
        if (shadeAlpha > 0) {
            canvas.drawColor(UiTheme.argb(Math.min(255, shadeAlpha), 3, 4, 7));
        }
        drawEdgeShade(canvas, width, height);
    }

    static boolean canResetMainMenuBackground() {
        ensureBackgroundStateLoaded();
        return !gridBackground;
    }

    static void importMainMenuBackground(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("The selected image does not exist.");
        }
        long byteCount = Files.size(source);
        if (byteCount <= 0L || byteCount > MAX_IMPORT_BYTES) {
            throw new IOException("The image must be smaller than 32 MB.");
        }

        BufferedImage decoded = decodeImport(source);
        Path target = customBackgroundPath();
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            if (!ImageIO.write(decoded, "png", temporary.toFile())) {
                throw new IOException("The image could not be converted to PNG.");
            }
            Image replacement = loadImage(temporary);
            if (replacement == null) {
                throw new IOException("The converted image could not be loaded.");
            }
            try {
                Files.deleteIfExists(gridBackgroundMarker());
                moveReplacing(temporary, target);
            } catch (IOException exception) {
                replacement.close();
                throw exception;
            }
            replaceCustomBackground(replacement);
            gridBackground = false;
            backgroundStateLoaded = true;
        } finally {
            Files.deleteIfExists(temporary);
            decoded.flush();
        }
    }

    static void resetMainMenuBackground() throws IOException {
        Path marker = gridBackgroundMarker();
        Files.createDirectories(marker.getParent());
        Files.write(marker, new byte[0]);
        Files.deleteIfExists(customBackgroundPath());
        replaceCustomBackground(null);
        gridBackground = true;
        backgroundStateLoaded = true;
    }

    static void draw(Canvas canvas, float width, float height, float time,
                     float pointerX, float pointerY, int shadeAlpha) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }

        drawGradient(canvas, Rect.makeXYWH(0.0F, 0.0F, width, height),
                0.0F, 0.0F, width, height,
                new int[]{0xFF06070A, 0xFF10141A, 0xFF090A0E},
                new float[]{0.0F, 0.58F, 1.0F});

        float parallaxX = clamp((pointerX / width - 0.5F) * 10.0F, -5.0F, 5.0F);
        float parallaxY = clamp((pointerY / height - 0.5F) * 8.0F, -4.0F, 4.0F);
        drawPerspectivePlane(canvas, width, height, time, parallaxX, parallaxY);
        drawGrid(canvas, width, height, time, parallaxX, parallaxY);
        drawScan(canvas, width, height, time);

        if (shadeAlpha > 0) {
            canvas.drawColor(UiTheme.argb(Math.min(255, shadeAlpha), 4, 6, 9));
        }
        drawEdgeShade(canvas, width, height);
    }

    private static void drawPerspectivePlane(Canvas canvas, float width, float height, float time,
                                             float parallaxX, float parallaxY) {
        int accent = UiTheme.accent();
        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 0.55F);

        canvas.save();
        canvas.translate(width * 0.73F + parallaxX, height * 0.46F + parallaxY);
        canvas.rotate(-13.0F);
        float planeWidth = Math.max(120.0F, width * 0.22F);
        float planeHeight = height * 2.1F;
        drawGradient(canvas, Rect.makeXYWH(-planeWidth * 0.5F, -planeHeight * 0.5F,
                        planeWidth, planeHeight),
                -planeWidth * 0.5F, 0.0F, planeWidth * 0.5F, 0.0F,
                new int[]{UiTheme.withAlpha(accent, 0), UiTheme.withAlpha(accent, 12 + Math.round(pulse * 9.0F)),
                        UiTheme.withAlpha(0xFFF1A45D, 8), UiTheme.withAlpha(accent, 0)},
                new float[]{0.0F, 0.28F, 0.72F, 1.0F});
        canvas.restore();

        TraceLine trace = traceLine(width, height, parallaxX);
        LINE_PAINT.setColor(UiTheme.withAlpha(0xFFF1A45D, 38)).setStrokeWidth(1.0F);
        canvas.drawLine(trace.startX(), trace.startY(), trace.endX(), trace.endY(), LINE_PAINT);
    }

    static TraceLine traceLine(float width, float height, float parallaxX) {
        float startX = width * 0.82F + parallaxX * 0.7F;
        return new TraceLine(startX, height * 0.12F,
                startX - height * 0.22F, height * 0.88F);
    }

    private static void drawGrid(Canvas canvas, float width, float height, float time,
                                 float parallaxX, float parallaxY) {
        float spacing = Math.max(34.0F, Math.min(58.0F, width / 12.0F));
        float offsetX = positiveModulo(time * 3.5F + parallaxX, spacing);
        float offsetY = positiveModulo(time * 2.0F + parallaxY, spacing);
        LINE_PAINT.setColor(0x0EFFFFFF).setStrokeWidth(1.0F);
        for (float x = -spacing + offsetX; x < width + spacing; x += spacing) {
            canvas.drawLine(x, 0.0F, x, height, LINE_PAINT);
        }
        for (float y = -spacing + offsetY; y < height + spacing; y += spacing) {
            canvas.drawLine(0.0F, y, width, y, LINE_PAINT);
        }

        LINE_PAINT.setColor(0x18FFFFFF).setStrokeWidth(1.0F);
        canvas.drawLine(width * 0.08F, 0.0F, width * 0.08F, height, LINE_PAINT);
        canvas.drawLine(width * 0.92F, 0.0F, width * 0.92F, height, LINE_PAINT);
    }

    private static void drawScan(Canvas canvas, float width, float height, float time) {
        float travel = height + 120.0F;
        float y = positiveModulo(time * 23.0F, travel) - 60.0F;
        int accent = UiTheme.accent();
        drawGradient(canvas, Rect.makeXYWH(0.0F, y - 38.0F, width, 76.0F),
                0.0F, y - 38.0F, 0.0F, y + 38.0F,
                new int[]{UiTheme.withAlpha(accent, 0), UiTheme.withAlpha(accent, 11),
                        UiTheme.withAlpha(accent, 0)},
                new float[]{0.0F, 0.5F, 1.0F});
        LINE_PAINT.setColor(UiTheme.withAlpha(accent, 24)).setStrokeWidth(1.0F);
        canvas.drawLine(0.0F, y, width, y, LINE_PAINT);
    }

    private static void drawEdgeShade(Canvas canvas, float width, float height) {
        float edge = Math.min(150.0F, width * 0.24F);
        drawGradient(canvas, Rect.makeXYWH(0.0F, 0.0F, edge, height),
                0.0F, 0.0F, edge, 0.0F,
                new int[]{0x8C020306, 0x00020306});
        drawGradient(canvas, Rect.makeXYWH(width - edge, 0.0F, edge, height),
                width, 0.0F, width - edge, 0.0F,
                new int[]{0x76020306, 0x00020306});
        float vertical = Math.min(100.0F, height * 0.25F);
        drawGradient(canvas, Rect.makeXYWH(0.0F, height - vertical, width, vertical),
                0.0F, height, 0.0F, height - vertical,
                new int[]{0x76020306, 0x00020306});
    }

    private static void drawGradient(Canvas canvas, Rect bounds, float x0, float y0, float x1, float y1,
                                     int[] colors) {
        drawGradient(canvas, bounds, x0, y0, x1, y1, colors, null);
    }

    private static void drawGradient(Canvas canvas, Rect bounds, float x0, float y0, float x1, float y1,
                                     int[] colors, float[] positions) {
        try (Shader shader = positions == null
                ? Shader.makeLinearGradient(x0, y0, x1, y1, colors)
                : Shader.makeLinearGradient(x0, y0, x1, y1, colors, positions)) {
            GRADIENT_PAINT.setShader(shader);
            canvas.drawRect(bounds, GRADIENT_PAINT);
            GRADIENT_PAINT.setShader(null);
        }
    }

    private static float seconds() {
        return (System.nanoTime() & 0x1FFFFFFFFFFFFFL) / 1_000_000_000.0F;
    }

    private static Image loadImage(String resource) {
        try (InputStream stream = ScreenBackdrop.class.getResourceAsStream(resource)) {
            return stream == null ? null : Image.makeFromEncoded(stream.readAllBytes());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Image loadImage(Path file) {
        try {
            return Files.isRegularFile(file) ? Image.makeFromEncoded(Files.readAllBytes(file)) : null;
        } catch (Exception exception) {
            Setsuna.LOGGER.warn("Unable to load custom menu background {}", file, exception);
            return null;
        }
    }

    private static BufferedImage decodeImport(Path source) throws IOException {
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
                int imageWidth = reader.getWidth(0);
                int imageHeight = reader.getHeight(0);
                long pixels = (long) imageWidth * imageHeight;
                if (imageWidth <= 0 || imageHeight <= 0 || pixels > MAX_IMPORT_PIXELS) {
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

    private static Image mainMenuBackground() {
        ensureBackgroundStateLoaded();
        return customMainMenuBackground != null ? customMainMenuBackground : MAIN_MENU_BACKGROUND;
    }

    private static void ensureBackgroundStateLoaded() {
        if (backgroundStateLoaded) {
            return;
        }
        gridBackground = Files.isRegularFile(gridBackgroundMarker());
        customMainMenuBackground = gridBackground ? null : loadImage(customBackgroundPath());
        backgroundStateLoaded = true;
    }

    private static void replaceCustomBackground(Image replacement) {
        Image previous = customMainMenuBackground;
        customMainMenuBackground = replacement;
        if (previous != null && previous != replacement) {
            previous.close();
        }
    }

    private static Path customBackgroundPath() {
        return backgroundDirectory()
                .resolve("menu-background.png");
    }

    private static Path gridBackgroundMarker() {
        return backgroundDirectory()
                .resolve("use-grid-background");
    }

    private static Path backgroundDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(".setsuna")
                .resolve("ui");
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    record TraceLine(float startX, float startY, float endX, float endY) {
    }
}
