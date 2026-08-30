package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;
import net.minecraft.network.protocol.Packet;

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

        private Packet<?> packet;

        public Receive(Packet<?> packet) {
            this.packet = packet;
        }

        public Packet<?> getPacket() {
            return packet;
        }

        public void setPacket(Packet<?> packet) {
            this.packet = packet;
        }
    }

    /** Outbound (serverbound) packet, before it is written. */
    public static final class Send extends CancellableEvent {

        private Packet<?> packet;

        public Send(Packet<?> packet) {
            this.packet = packet;
        }

        public Packet<?> getPacket() {
            return packet;
        }

        public void setPacket(Packet<?> packet) {
            this.packet = packet;
        }
    }
}
