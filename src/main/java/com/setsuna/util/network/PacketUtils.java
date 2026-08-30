package com.setsuna.util.network;

import com.setsuna.Setsuna;
import net.minecraft.network.protocol.Packet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Helpers for sending packets that bypass the client's own {@link com.setsuna.event.events.PacketEvent}
 * dispatch, so modules can inject traffic without re-triggering their own handlers.
 */
public final class PacketUtils {

    /** Packets that {@link com.setsuna.mixin.ConnectionMixin} must forward without posting a Send event. */
    public static final Set<Packet<?>> bypassedPackets = Collections.synchronizedSet(new HashSet<>());

    private PacketUtils() {
    }

    /** Sends {@code packet} straight to the server, skipping the Send event. */
    public static void sendSilently(Packet<?> packet) {
        if (Setsuna.mc().getConnection() == null) {
            return;
        }
        bypassedPackets.add(packet);
        Setsuna.mc().getConnection().send(packet);
    }
}
