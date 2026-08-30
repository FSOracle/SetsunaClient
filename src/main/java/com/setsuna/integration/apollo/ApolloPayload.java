package com.setsuna.integration.apollo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;

/** Raw envelope carried by Lunar Client's shared Apollo plugin channel. */
public record ApolloPayload(byte[] data) implements CustomPacketPayload {

    private static final int MAX_PAYLOAD_BYTES = 1 << 20;

    public static final Type<ApolloPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("lunar", "apollo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApolloPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public ApolloPayload decode(RegistryFriendlyByteBuf buffer) {
                    int length = buffer.readableBytes();
                    if (length > MAX_PAYLOAD_BYTES) {
                        throw new IllegalArgumentException("Apollo payload exceeds 1 MiB");
                    }
                    byte[] data = new byte[length];
                    buffer.readBytes(data);
                    return new ApolloPayload(data);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ApolloPayload payload) {
                    if (payload.data.length > MAX_PAYLOAD_BYTES) {
                        throw new IllegalArgumentException("Apollo payload exceeds 1 MiB");
                    }
                    buffer.writeBytes(payload.data);
                }
            };

    public ApolloPayload {
        data = Arrays.copyOf(data, data.length);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
