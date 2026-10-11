package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;

/**
 * Fired around network packet flow on the {@code Connection}. {@link Receive} is
 * posted before an inbound packet is handled; {@link Send} before an outbound
 * packet is written. Cancelling drops the packet; {@link #setPacket} swaps it.
 */
public final class PacketEvent {

    private PacketEvent() {
    }

    /** Inbound (clientbound) packet, before it is handled. */
    public static final class Receive extends CancellableEvent {

        private final PacketSlot packet;

        public Receive(Packet<?> packet) {
            this.packet = new PacketSlot(packet);
        }

        public Packet<?> getPacket() {
            return packet.value();
        }

        public void setPacket(Packet<?> packet) {
            this.packet.value(packet);
        }
    }

    /** Outbound (serverbound) packet, before it is written. */
    public static final class Send extends CancellableEvent {

        private final PacketSlot packet;
        private final @Nullable ChannelFutureListener listener;
        private final boolean flush;

        public Send(Packet<?> packet) {
            this(packet, null, true);
        }

        public Send(
                Packet<?> packet,
                @Nullable ChannelFutureListener listener,
                boolean flush) {
            this.packet = new PacketSlot(packet);
            this.listener = listener;
            this.flush = flush;
        }

        public Packet<?> getPacket() {
            return packet.value();
        }

        public void setPacket(Packet<?> packet) {
            this.packet.value(packet);
        }

        public @Nullable ChannelFutureListener listener() {
            return listener;
        }

        public boolean flush() {
            return flush;
        }
    }

    private static final class PacketSlot {
        private Packet<?> current;

        private PacketSlot(Packet<?> current) {
            this.current = current;
        }

        private Packet<?> value() {
            return current;
        }

        private void value(Packet<?> replacement) {
            current = replacement;
        }
    }
}
