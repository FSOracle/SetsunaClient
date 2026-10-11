package com.setsuna.ui.clickgui;

import com.setsuna.render.SkijaUi;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickGuiTextTest {
    @Test
    void keepsWhitespaceAndCodePointWrappingRules() {
        SkijaUi.withFont(SkijaUi.CLIENT_FONT, () -> {
            assertEquals(List.of(), ClickGuiText.wrap(null, 80, 9, false));
            assertEquals(List.of(), ClickGuiText.wrap("  ", 80, 9, false));
            assertEquals(List.of(), ClickGuiText.wrap("Label", 0, 9, false));
            assertEquals(1, ClickGuiText.lineCount("", 80, 9, false));
            assertEquals(List.of("Global Blur"), ClickGuiText.wrap(" \tGlobal Blur\n ", 200, 9, false));

            float twoWords = SkijaUi.textWidth("Global Blur", 9) + 0.1F;
            assertEquals(List.of("Global Blur", "Strength"),
                    ClickGuiText.wrap("Global Blur Strength", twoWords, 9, false));
            assertEquals(List.of("mm", "mm"),
                    ClickGuiText.wrap("mmmm", SkijaUi.textWidth("mm", 9) + 0.1F, 9, false));
            assertEquals(List.of("A", "\uD83D\uDE80", "B"),
                    ClickGuiText.wrap("A\uD83D\uDE80B", 0.1F, 9, false));
            assertEquals(List.of("\u5168", "\u5c40", "\u6bdb", "\u73bb", "\u7483"),
                    ClickGuiText.wrap("\u5168\u5c40\u6bdb\u73bb\u7483", 0.1F, 9, false));
        });
    }

    @Test
    void immediatelyUsesScopedFontChangesAndRestoresPreviousFont() {
        String label = "WWWW iiii Global Blur";
        float[] widths = new float[2];
        SkijaUi.withFont(SkijaUi.CLIENT_FONT, () -> widths[0] = SkijaUi.textWidth(label, 9));
        SkijaUi.withFont(SkijaUi.MICROSOFT_YAHEI_FONT, () -> widths[1] = SkijaUi.textWidth(label, 9));
        assertTrue(Math.abs(widths[0] - widths[1]) > 0.1F);
        float available = (widths[0] + widths[1]) * 0.5F;

        SkijaUi.withFont(SkijaUi.CLIENT_FONT, () -> {
            List<String> original = ClickGuiText.wrap(label, available, 9, false);
            assertEquals(widths[0] <= available, original.size() == 1);
            SkijaUi.withFont(SkijaUi.MICROSOFT_YAHEI_FONT, () ->
                    assertEquals(widths[1] <= available,
                            ClickGuiText.wrap(label, available, 9, false).size() == 1));
            assertEquals(original, ClickGuiText.wrap(label, available, 9, false));
        });
    }

    @Test
    void centeredDrawingMatchesTheSameWrappedLinesPixelForPixel() {
        for (String font : List.of(SkijaUi.CLIENT_FONT, SkijaUi.MICROSOFT_YAHEI_FONT)) {
            SkijaUi.withFont(font, () -> {
                for (boolean bold : new boolean[]{false, true}) {
                    for (boolean rightAligned : new boolean[]{false, true}) {
                        compareCenteredDrawing("Global Blur Strength", 38, bold, rightAligned);
                        compareCenteredDrawing("\u5168\u5c40\u6bdb\u73bb\u7483", 24, bold, rightAligned);
                        compareCenteredDrawing("Label", 90, bold, rightAligned);
                    }
                }
            });
        }
    }

    private static void compareCenteredDrawing(String text, float maxWidth, boolean bold, boolean rightAligned) {
        List<String> lines = ClickGuiText.wrap(text, maxWidth, 9, bold);
        try (Surface expected = Surface.makeRasterN32Premul(128, 80);
             Surface actual = Surface.makeRasterN32Premul(128, 80)) {
            expected.getCanvas().clear(0);
            actual.getCanvas().clear(0);
            float top = 8 + Math.max(0, (64 - lines.size() * 12) * 0.5F);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                float width = bold ? SkijaUi.boldTextWidth(line, 9) : SkijaUi.textWidth(line, 9);
                float x = rightAligned ? 112 - width : 8;
                if (bold) {
                    SkijaUi.boldText(expected.getCanvas(), line, x, top + index * 12, 12, 0xFFFFFFFF, 9);
                } else {
                    SkijaUi.text(expected.getCanvas(), line, x, top + index * 12, 12, 0xFFFFFFFF, 9);
                }
            }
            Canvas canvas = actual.getCanvas();
            if (rightAligned) {
                ClickGuiText.drawRightCentered(canvas, text, 112, 8, maxWidth, 64, 12, 0xFFFFFFFF, 9, bold);
            } else {
                ClickGuiText.drawVerticallyCentered(canvas, text, 8, 8, maxWidth, 64, 12, 0xFFFFFFFF, 9, bold);
            }
            assertArrayEquals(pixels(expected), pixels(actual));
        }
    }

    private static int[] pixels(Surface surface) {
        int width = surface.getWidth();
        int height = surface.getHeight();
        int[] colors = new int[width * height];
        try (Bitmap bitmap = new Bitmap()) {
            assertTrue(bitmap.allocN32Pixels(width, height));
            assertTrue(surface.readPixels(bitmap, 0, 0));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) colors[y * width + x] = bitmap.getColor(x, y);
            }
        }
        return colors;
    }
}
