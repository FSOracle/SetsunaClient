package com.setsuna.util.network;

import com.setsuna.Setsuna;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Helpers for sending packets that bypass the client's own {@link com.setsuna.event.events.PacketEvent}
 * dispatch, so modules can inject traffic without re-triggering their own handlers.
 */
public final class PacketUtils {

    /** Packets that {@link com.setsuna.mixin.ConnectionMixin} must forward without posting a Send event. */
    public static final Set<Packet<?>> bypassedPackets = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));

    private PacketUtils() {
    }

    /** Sends {@code packet} straight to the server, skipping the Send event. */
    public static void sendSilently(Packet<?> packet) {
        sendSilently(packet, null, true);
    }

    /** Replays an outgoing packet with its original completion listener and flush flag. */
    public static void sendSilently(
            Packet<?> packet,
            @Nullable ChannelFutureListener listener,
            boolean flush) {
        ClientPacketListener connection = Setsuna.mc().getConnection();
        if (connection == null) {
            return;
        }
        bypassedPackets.add(packet);
        try {
            connection.getConnection().send(packet, listener, flush);
        } finally {
            bypassedPackets.remove(packet);
        }
    }

    /** Handles an inbound packet directly without re-entering ConnectionMixin. */
    @SuppressWarnings("unchecked")
    public static void dispatch(ClientPacketListener connection, Packet<?> packet) {
        try {
            ((Packet<ClientGamePacketListener>) packet).handle(connection);
        } catch (Exception exception) {
            Setsuna.LOGGER.warn("Failed to replay incoming packet {}",
                    packet.getClass().getName(), exception);
        }
    }
}
