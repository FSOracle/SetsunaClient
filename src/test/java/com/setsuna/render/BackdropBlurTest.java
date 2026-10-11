package com.setsuna.render;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackdropBlurTest {
    @Test
    void keepsSamplingAnchoredToScreenInsideTranslatedAndScaledClip() {
        try (Surface backdrop = Surface.makeRasterN32Premul(64, 64);
             Surface target = Surface.makeRasterN32Premul(128, 128);
             Paint paint = new Paint()) {
            backdrop.getCanvas().clear(0xFFFF0000);
            backdrop.getCanvas().drawRect(Rect.makeXYWH(24, 0, 40, 64), paint.setColor(0xFF0000FF));
            Canvas canvas = target.getCanvas();
            canvas.clear(0);
            canvas.scale(2, 2);
            canvas.translate(20, 8);
            canvas.scale(1.5F, 1.25F);
            float[] matrix = canvas.getLocalToDeviceAsMatrix33().getMat();
            var originalClip = canvas.getDeviceClipBounds();
            int saveCount = canvas.getSaveCount();

            try (Image snapshot = backdrop.makeImageSnapshot()) {
                BackdropBlur.draw(canvas,
                        clipped -> clipped.clipRRect(RRect.makeXYWH(0, 0, 16, 16, 4), true),
                        snapshot, 128, 128, 64, 64, 0.1F);
            }

            assertArrayEquals(matrix, canvas.getLocalToDeviceAsMatrix33().getMat());
            assertEquals(originalClip, canvas.getDeviceClipBounds());
            assertEquals(saveCount, canvas.getSaveCount());
            try (Bitmap pixels = pixels(target)) {
                assertEquals(0xFF0000FF, pixels.getColor(72, 36), "Sample must come from screen x=36, not local x=0..16");
                assertTrue(red(pixels.getColor(44, 36)) > 240, "Left side of the clip should sample the red screen region");
                assertEquals(0, pixels.getColor(40, 16), "Rounded corner must stay clipped");
                assertEquals(0, pixels.getColor(38, 36), "Nothing may draw outside the translated clip");
                assertEquals(0, pixels.getColor(90, 36));
                assertEquals(0, pixels.getColor(64, 58));
            }
        }
    }

    @Test
    void preservesEnclosingOpacityLayer() {
        try (Surface backdrop = Surface.makeRasterN32Premul(64, 64);
             Surface target = Surface.makeRasterN32Premul(64, 64)) {
            backdrop.getCanvas().clear(0xFF2080E0);
            Canvas canvas = target.getCanvas();
            canvas.clear(0);
            canvas.translate(16, 12);
            int layer = canvas.saveLayerAlpha(null, 128);
            try (Image snapshot = backdrop.makeImageSnapshot()) {
                BackdropBlur.draw(canvas, clipped -> clipped.clipRect(Rect.makeXYWH(0, 0, 24, 24)),
                        snapshot, 64, 64, 64, 64, 4);
            } finally {
                canvas.restoreToCount(layer);
            }
            try (Bitmap pixels = pixels(target)) {
                assertEquals(128, pixels.getColor(28, 24) >>> 24, "Glass must fade with its HUD layer");
                assertEquals(0, pixels.getColor(8, 8));
            }
        }
    }

    @Test
    void blurReducesCheckerboardContrast() {
        try (Surface backdrop = Surface.makeRasterN32Premul(64, 64);
             Surface target = Surface.makeRasterN32Premul(64, 64);
             Paint paint = new Paint()) {
            for (int y = 0; y < 64; y += 4) {
                for (int x = 0; x < 64; x += 4) {
                    int color = ((x / 4 + y / 4) & 1) == 0 ? 0xFF000000 : 0xFFFFFFFF;
                    backdrop.getCanvas().drawRect(Rect.makeXYWH(x, y, 4, 4), paint.setColor(color));
                }
            }
            target.getCanvas().clear(0);
            try (Image snapshot = backdrop.makeImageSnapshot()) {
                BackdropBlur.draw(target.getCanvas(), clipped -> clipped.clipRect(Rect.makeXYWH(8, 8, 48, 48)),
                        snapshot, 64, 64, 64, 64, 8);
            }
            try (Bitmap original = pixels(backdrop); Bitmap blurred = pixels(target)) {
                int originalContrast = contrast(original, 20, 20, 44, 44);
                int blurredContrast = contrast(blurred, 20, 20, 44, 44);
                assertEquals(255, originalContrast);
                assertTrue(blurredContrast < 40, "Gaussian blur should smooth checkerboard contrast, got " + blurredContrast);
                assertTrue(red(blurred.getColor(32, 32)) > 110 && red(blurred.getColor(32, 32)) < 145);
                assertEquals(255, blurred.getColor(32, 32) >>> 24);
                assertEquals(0, blurred.getColor(4, 4));
            }
        }
    }

    @Test
    void restoresCanvasWhenClipperFails() {
        try (Surface backdrop = Surface.makeRasterN32Premul(16, 16);
             Surface target = Surface.makeRasterN32Premul(16, 16);
             Image snapshot = backdrop.makeImageSnapshot()) {
            Canvas canvas = target.getCanvas();
            canvas.translate(3, 4);
            float[] matrix = canvas.getLocalToDeviceAsMatrix33().getMat();
            int saveCount = canvas.getSaveCount();
            assertThrows(IllegalStateException.class, () -> BackdropBlur.draw(canvas, clipped -> {
                clipped.translate(5, 6);
                throw new IllegalStateException("clip failure");
            }, snapshot, 16, 16, 16, 16, 4));
            assertArrayEquals(matrix, canvas.getLocalToDeviceAsMatrix33().getMat());
            assertEquals(saveCount, canvas.getSaveCount());
        }
    }

    private static Bitmap pixels(Surface surface) {
        Bitmap pixels = new Bitmap();
        assertTrue(pixels.allocN32Pixels(surface.getWidth(), surface.getHeight()));
        assertTrue(surface.readPixels(pixels, 0, 0));
        return pixels;
    }

    private static int contrast(Bitmap pixels, int left, int top, int right, int bottom) {
        int min = 255;
        int max = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int value = red(pixels.getColor(x, y));
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        return max - min;
    }

    private static int red(int color) {
        return (color >>> 16) & 255;
    }
}
