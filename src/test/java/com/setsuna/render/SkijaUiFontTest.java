package com.setsuna.render;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SkijaUiFontTest {

    @Test
    void resolvesMicrosoftYaHeiAsABuiltInFont() {
        assertEquals(SkijaUi.MICROSOFT_YAHEI_FONT,
                SkijaUi.resolveFontName("microsoft yahei"));
        assertTrue(SkijaUi.textWidth("\u97F3\u4E50 Music", 9.0F,
                SkijaUi.MICROSOFT_YAHEI_FONT) > 0.0F);
    }

    @Test
    void usesMalgunGothicForKoreanRuns() {
        String korean = "\uD55C\uAE00";
        Typeface typeface = FontMgr.getDefault().matchFamilyStyle("Malgun Gothic", FontStyle.NORMAL);
        assertNotNull(typeface);
        try (typeface; Font font = new Font(typeface, 9.0F)) {
            font.setSubpixel(false)
                    .setBaselineSnapped(true)
                    .setHinting(FontHinting.NORMAL)
                    .setEdging(FontEdging.ANTI_ALIAS);
            assertNotEquals(0, font.getUTF32Glyph(korean.codePointAt(0)));
            assertNotEquals(0, font.getUTF32Glyph(korean.codePointAt(1)));
            assertEquals(font.measureTextWidth(korean),
                    SkijaUi.textWidth(korean, 9.0F, SkijaUi.MICROSOFT_YAHEI_FONT), 0.01F);
        }
        assertTrue(SkijaUi.textWidth(korean + " ANGEL", 9.0F,
                SkijaUi.MICROSOFT_YAHEI_FONT) > 0.0F);
    }
}
