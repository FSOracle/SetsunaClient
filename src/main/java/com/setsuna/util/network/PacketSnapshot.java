package com.setsuna.util.network;

import net.minecraft.network.protocol.Packet;

/** Timestamped inbound packet used by Backtrack's private delay queue. */
public record PacketSnapshot(Packet<?> packet, long timestamp) {

    public PacketSnapshot(Packet<?> packet) {
        this(packet, System.currentTimeMillis());
    }
}
