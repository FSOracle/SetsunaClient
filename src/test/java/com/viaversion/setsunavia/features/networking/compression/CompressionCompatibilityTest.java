package com.viaversion.setsunavia.features.networking.compression;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

class CompressionCompatibilityTest {

    private static final byte[] PAYLOAD = "configuration packet payload".repeat(64).getBytes(StandardCharsets.UTF_8);

    @Test
    void recognizesStandardZlibHeader() {
        final byte[] zlib = deflate(PAYLOAD, false);

        assertTrue(CompressionCompatibility.isZlibHeader(zlib[0] & 0xFF, zlib[1] & 0xFF));
        assertArrayEquals(PAYLOAD, CompressionCompatibility.tryInflateZlib(zlib, PAYLOAD.length));
    }

    @Test
    void inflatesOnlyCompleteRawStreamsWithTheDeclaredSize() {
        final byte[] raw = deflate(PAYLOAD, true);

        assertFalse(CompressionCompatibility.isZlibHeader(raw[0] & 0xFF, raw[1] & 0xFF));
        assertArrayEquals(PAYLOAD, CompressionCompatibility.tryInflateRaw(raw, PAYLOAD.length));
        assertNull(CompressionCompatibility.tryInflateRaw(raw, PAYLOAD.length - 1));
        assertNull(CompressionCompatibility.tryInflateRaw(raw, PAYLOAD.length + 1));
    }

    @Test
    void inflatesRawStreamWhoseFirstBytesLookLikeAZlibHeader() {
        final byte[] raw = rawWithZlibLookingHeader(PAYLOAD);
        final byte[] expected = new byte[PAYLOAD.length + 1];
        expected[0] = '!';
        System.arraycopy(PAYLOAD, 0, expected, 1, PAYLOAD.length);

        assertTrue(CompressionCompatibility.isZlibHeader(raw[0] & 0xFF, raw[1] & 0xFF));
        assertNull(CompressionCompatibility.tryInflateZlib(raw, expected.length));
        assertArrayEquals(expected, CompressionCompatibility.tryInflateRaw(raw, expected.length));
    }

    @Test
    void rejectsInvalidTruncatedAndTrailingData() {
        final byte[] raw = deflate(PAYLOAD, true);
        final byte[] zlib = deflate(PAYLOAD, false);

        assertNull(CompressionCompatibility.tryInflateRaw(new byte[]{0x07}, PAYLOAD.length));
        assertNull(CompressionCompatibility.tryInflateRaw(Arrays.copyOf(raw, raw.length - 1), PAYLOAD.length));
        assertNull(CompressionCompatibility.tryInflateRaw(Arrays.copyOf(raw, raw.length + 1), PAYLOAD.length));
        assertNull(CompressionCompatibility.tryInflateZlib(new byte[]{0x78, 0x01, 0x07}, PAYLOAD.length));
        assertNull(CompressionCompatibility.tryInflateZlib(Arrays.copyOf(zlib, zlib.length - 1), PAYLOAD.length));
        assertNull(CompressionCompatibility.tryInflateZlib(Arrays.copyOf(zlib, zlib.length + 1), PAYLOAD.length));
    }

    private static byte[] rawWithZlibLookingHeader(final byte[] input) {
        final byte[] remainder = deflate(input, true);
        final byte[] raw = new byte[6 + remainder.length];

        // A non-final stored block with ignored padding bits set makes the stream begin with the valid zlib header 78 01.
        raw[0] = 0x78;
        raw[1] = 0x01;
        raw[2] = 0x00;
        raw[3] = (byte) 0xFE;
        raw[4] = (byte) 0xFF;
        raw[5] = '!';
        System.arraycopy(remainder, 0, raw, 6, remainder.length);
        return raw;
    }

    private static byte[] deflate(final byte[] input, final boolean raw) {
        final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, raw);
        try {
            deflater.setInput(input);
            deflater.finish();
            final byte[] compressed = new byte[input.length];
            final int length = deflater.deflate(compressed);
            assertTrue(deflater.finished());
            return Arrays.copyOf(compressed, length);
        } finally {
            deflater.end();
        }
    }

}
