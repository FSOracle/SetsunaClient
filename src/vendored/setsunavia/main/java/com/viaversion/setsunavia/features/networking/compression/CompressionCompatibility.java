/*
 * This file is part of SetsunaVia - https://github.com/ViaVersion/SetsunaVia
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.setsunavia.features.networking.compression;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.jetbrains.annotations.Nullable;

public final class CompressionCompatibility {

    public static final int MAXIMUM_UNCOMPRESSED_LENGTH = 8 * 1024 * 1024;

    private CompressionCompatibility() {
    }

    public static boolean isZlibHeader(final int compressionMethodAndFlags, final int flags) {
        return (compressionMethodAndFlags & 0x0F) == 8
            && (compressionMethodAndFlags >>> 4) <= 7
            && ((compressionMethodAndFlags << 8) | flags) % 31 == 0;
    }

    /**
     * Inflates a zlib-wrapped DEFLATE stream only when it consumes the complete input and produces exactly the
     * declared size.
     */
    @Nullable
    public static byte[] tryInflateZlib(final byte[] compressed, final int declaredSize) {
        return tryInflate(compressed, declaredSize, false);
    }

    /**
     * Inflates a raw DEFLATE stream only when it consumes the complete input and produces exactly the declared size.
     */
    @Nullable
    public static byte[] tryInflateRaw(final byte[] compressed, final int declaredSize) {
        return tryInflate(compressed, declaredSize, true);
    }

    @Nullable
    private static byte[] tryInflate(final byte[] compressed, final int declaredSize, final boolean raw) {
        if (declaredSize <= 0 || declaredSize > MAXIMUM_UNCOMPRESSED_LENGTH || compressed.length == 0) {
            return null;
        }

        final byte[] output = new byte[declaredSize + 1];
        final Inflater inflater = new Inflater(raw);
        try {
            inflater.setInput(compressed);
            int written = 0;
            while (!inflater.finished() && written < output.length) {
                final int count = inflater.inflate(output, written, output.length - written);
                if (count == 0) {
                    break;
                }
                written += count;
            }

            if (!inflater.finished() || inflater.getRemaining() != 0 || written != declaredSize) {
                return null;
            }

            final byte[] exactOutput = new byte[declaredSize];
            System.arraycopy(output, 0, exactOutput, 0, declaredSize);
            return exactOutput;
        } catch (DataFormatException ignored) {
            return null;
        } finally {
            inflater.end();
        }
    }

}
