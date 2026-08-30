package com.setsuna.mixin;

import com.setsuna.event.EventBus;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.util.network.PacketUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fires {@link PacketEvent.Receive} before an inbound packet is handled and
 * {@link PacketEvent.Send} before an outbound one is written. Packets queued via
 * {@link PacketUtils#sendSilently} bypass the Send event exactly once.
 */
@Mixin(Connection.class)
public class ConnectionMixin {

    @WrapOperation(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"))
    private void setsuna$onReceive(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        PacketEvent.Receive event = EventBus.INSTANCE.post(new PacketEvent.Receive(packet));
        if (!event.isCancelled()) {
            original.call(event.getPacket(), listener);
        }
    }

    @WrapOperation(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"))
    private void setsuna$onSend(Connection instance, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, Operation<Void> original) {
        if (PacketUtils.bypassedPackets.remove(packet)) {
            original.call(instance, packet, listener, flush);
            return;
        }
        PacketEvent.Send event = EventBus.INSTANCE.post(new PacketEvent.Send(packet));
        if (!event.isCancelled()) {
            original.call(instance, event.getPacket(), listener, flush);
        }
    }
}
