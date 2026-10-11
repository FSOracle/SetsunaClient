package com.setsuna.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DisplayNamesTest {

    @Test
    void normalizesWhitespaceAndControlCharacters() {
        assertEquals("Flux Client", DisplayNames.normalize("  Flux\n\tClient\u0000  "));
    }

    @Test
    void rejectsNamesThatBecomeEmpty() {
        assertThrows(IllegalArgumentException.class, () -> DisplayNames.normalize(" \n\t "));
    }

    @Test
    void capsNamesWithoutSplittingCodePoints() {
        String input = "x".repeat(DisplayNames.MAX_CODE_POINTS + 10);
        assertEquals("x".repeat(DisplayNames.MAX_CODE_POINTS), DisplayNames.normalize(input));
    }
}
