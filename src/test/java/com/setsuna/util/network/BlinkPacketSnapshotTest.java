package com.setsuna.util.network;

import com.setsuna.event.events.PacketEvent;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlinkPacketSnapshotTest {

    @Test
    void outgoingSnapshotAndSendEventPreserveTransportMetadata() {
        ServerboundSwingPacket packet = new ServerboundSwingPacket(InteractionHand.MAIN_HAND);
        ChannelFutureListener listener = future -> { };
        long before = System.currentTimeMillis();

        BlinkPacketSnapshot snapshot = BlinkPacketSnapshot.outgoing(packet, listener, false);
        PacketEvent.Send event = new PacketEvent.Send(packet, listener, false);

        assertSame(packet, snapshot.packet());
        assertSame(listener, snapshot.listener());
        assertEquals(TransferOrigin.OUTGOING, snapshot.origin());
        assertFalse(snapshot.flush());
        assertTrue(snapshot.timestamp() >= before);
        assertSame(listener, event.listener());
        assertFalse(event.flush());
    }

    @Test
    void inboundSnapshotsCarryNoOutboundTransportState() {
        ServerboundSwingPacket packet = new ServerboundSwingPacket(InteractionHand.OFF_HAND);
        BlinkPacketSnapshot snapshot = BlinkPacketSnapshot.incoming(packet);

        assertEquals(TransferOrigin.INCOMING, snapshot.origin());
        assertEquals(null, snapshot.listener());
        assertFalse(snapshot.flush());
        assertEquals(0, BlinkManager.Action.FLUSH.priority());
        assertEquals(1, BlinkManager.Action.PASS.priority());
        assertEquals(2, BlinkManager.Action.QUEUE.priority());
    }
}
