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

package com.viaversion.setsunavia.injection.mixin.features.networking.compression;

import com.viaversion.setsunavia.SetsunaViaImpl;
import com.viaversion.setsunavia.features.networking.compression.CompressionCompatibility;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompressionDecoder.class)
public abstract class MixinCompressionDecoder {

    @Shadow
    private int threshold;

    @Shadow
    private boolean validateDecompressed;

    @Unique
    private boolean setsunaVia$reportedRawDeflate;

    @Inject(method = "decode", at = @At("HEAD"), cancellable = true)
    private void decodeCompatibleCompression(final ChannelHandlerContext context, final ByteBuf input, final List<Object> output, final CallbackInfo ci) {
        final ByteBuf packet = input.duplicate();
        final int declaredSize = VarInt.read(packet);
        if (declaredSize <= 0 || packet.readableBytes() == 0) {
            return;
        }
        if (this.validateDecompressed && (declaredSize < this.threshold || declaredSize > CompressionCompatibility.MAXIMUM_UNCOMPRESSED_LENGTH)) {
            return;
        }
        if (declaredSize > CompressionCompatibility.MAXIMUM_UNCOMPRESSED_LENGTH) {
            return;
        }

        final byte[] compressed = new byte[packet.readableBytes()];
        packet.readBytes(compressed);
        byte[] decompressed = CompressionCompatibility.tryInflateZlib(compressed, declaredSize);
        final boolean rawDeflate = decompressed == null;
        if (rawDeflate) {
            decompressed = CompressionCompatibility.tryInflateRaw(compressed, declaredSize);
        }
        if (decompressed == null) {
            return;
        }

        input.skipBytes(input.readableBytes());
        output.add(context.alloc().directBuffer(declaredSize, declaredSize).writeBytes(decompressed));
        ci.cancel();

        if (rawDeflate && !this.setsunaVia$reportedRawDeflate) {
            this.setsunaVia$reportedRawDeflate = true;
            SetsunaViaImpl.INSTANCE.getLogger().warn("Accepted a non-standard raw DEFLATE Minecraft packet from the remote proxy");
        }
    }

}
