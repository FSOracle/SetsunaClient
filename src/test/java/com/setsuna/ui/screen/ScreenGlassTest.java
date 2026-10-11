package com.setsuna.ui.screen;

import com.setsuna.render.SkijaUi;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreenGlassTest {
    @Test
    void glassSmoothsRealBackgroundPixelsAndKeepsRoundedClipAtScaledCoordinates() {
        try (Surface source = Surface.makeRasterN32Premul(120, 80);
             Surface output = Surface.makeRasterN32Premul(240, 160);
             Bitmap before = new Bitmap(); Bitmap after = new Bitmap()) {
            var canvas = source.getCanvas();
            for (int x = 0; x < 120; x += 4) {
                SkijaUi.fill(canvas, x, 0, 4, 80, x % 8 == 0 ? 0xFFFFFFFF : 0xFF000000);
            }
            try (Image image = source.makeImageSnapshot()) {
                var frame = ScreenGlass.prepare(image, 120, 80);
                var target = output.getCanvas();
                target.scale(2, 2);
                frame.drawBackground(target);
                assertTrue(before.allocN32Pixels(240, 160));
                assertTrue(output.readPixels(before, 0, 0));
                int saves = target.getSaveCount();
                frame.surface(target, new UiControls.Box(20, 20, 80, 40), 10, 0, 0);
                assertEquals(saves, target.getSaveCount());
                assertTrue(after.allocN32Pixels(240, 160));
                assertTrue(output.readPixels(after, 0, 0));
                int originalContrast = Math.abs(red(before.getColor(82, 80)) - red(before.getColor(90, 80)));
                int blurredContrast = Math.abs(red(after.getColor(82, 80)) - red(after.getColor(90, 80)));
                assertTrue(originalContrast > 130);
                assertTrue(blurredContrast < 15, "Glass must filter image detail, beyond changing the tint");
                assertEquals(before.getColor(10, 80), after.getColor(10, 80), "Outside the glass stays sharp");
                assertEquals(before.getColor(41, 41), after.getColor(41, 41), "Rounded corner stays outside the clip");
            }
        }
    }

    @Test
    void reusesBlurUntilViewportOrBackgroundChangesAndBoundsItsResolution() {
        try (Surface first = Surface.makeRasterN32Premul(40, 20);
             Surface second = Surface.makeRasterN32Premul(40, 20)) {
            first.getCanvas().clear(0xFFDD4422);
            second.getCanvas().clear(0xFF2255DD);
            try (Image a = first.makeImageSnapshot(); Image b = second.makeImageSnapshot()) {
                var initial = ScreenGlass.prepare(a, 640, 360).blurred();
                assertSame(initial, ScreenGlass.prepare(a, 640, 360).blurred());
                var resized = ScreenGlass.prepare(a, 320, 180).blurred();
                assertNotSame(initial, resized);
                var changed = ScreenGlass.prepare(b, 320, 180).blurred();
                assertNotSame(resized, changed);
                assertSame(changed, ScreenGlass.prepare(b, 320, 180).blurred());
                var large = ScreenGlass.prepare(b, 3840, 2160).blurred();
                assertTrue(large.getWidth() <= 960 && large.getHeight() <= 960);
            }
        }
    }

    @Test
    void photographCropMatchesGlassSamplingForPortraitAndUltrawideLayouts() {
        assertNotNull(ScreenBackdrop.glassBackground(), "Use the actual packaged mainmenu/background.png");
        try (Surface source = Surface.makeRasterN32Premul(400, 100)) {
            source.getCanvas().clear(0xFF112244);
            SkijaUi.fill(source.getCanvas(), 200, 0, 200, 100, 0xFFBB7733);
            try (Image image = source.makeImageSnapshot()) {
                for (int[] size : new int[][]{{100, 240}, {480, 80}}) {
                    try (Surface output = Surface.makeRasterN32Premul(size[0], size[1]);
                         Bitmap before = new Bitmap(); Bitmap after = new Bitmap()) {
                        var frame = ScreenGlass.prepare(image, size[0], size[1]);
                        frame.drawBackground(output.getCanvas());
                        assertTrue(before.allocN32Pixels(size[0], size[1]));
                        assertTrue(output.readPixels(before, 0, 0));
                        frame.surface(output.getCanvas(), new UiControls.Box(0, 0, size[0], size[1]), 0, 0, 0);
                        assertTrue(after.allocN32Pixels(size[0], size[1]));
                        assertTrue(output.readPixels(after, 0, 0));
                        for (int x : new int[]{10, size[0] - 11}) {
                            assertTrue(Math.abs(red(before.getColor(x, size[1] / 2))
                                    - red(after.getColor(x, size[1] / 2))) < 10);
                        }
                        assertTrue(red(after.getColor(10, size[1] / 2))
                                < red(after.getColor(size[0] - 11, size[1] / 2)));
                    }
                }
            }
        }
    }

    private static int red(int color) { return (color >>> 16) & 255; }
}
