package com.setsuna.util.network;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;

/** Complete packet snapshot retained by the shared FakeLag queue. */
public record BlinkPacketSnapshot(
        Packet<?> packet,
        TransferOrigin origin,
        long timestamp,
        @Nullable ChannelFutureListener listener,
        boolean flush) {

    public static BlinkPacketSnapshot outgoing(
            Packet<?> packet,
            @Nullable ChannelFutureListener listener,
            boolean flush) {
        return new BlinkPacketSnapshot(
                packet, TransferOrigin.OUTGOING, System.currentTimeMillis(), listener, flush);
    }

    public static BlinkPacketSnapshot incoming(Packet<?> packet) {
        return new BlinkPacketSnapshot(
                packet, TransferOrigin.INCOMING, System.currentTimeMillis(), null, false);
    }
}
