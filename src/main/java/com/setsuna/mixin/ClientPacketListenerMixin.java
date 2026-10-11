package com.setsuna.mixin;

import com.setsuna.event.EventBus;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.module.modules.combat.Backtrack;
import com.setsuna.module.modules.render.ChestESP;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes world-session resets after vanilla finishes applying a respawn packet. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void setsuna$afterRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        EventBus.INSTANCE.post(new RespawnEvent());
    }

    @Inject(method = "handleSetHealth", at = @At("HEAD"))
    private void setsuna$backtrackFlushOnDeath(
            ClientboundSetHealthPacket packet,
            CallbackInfo ci) {
        if (packet.getHealth() <= 0.0F && Backtrack.INSTANCE.isLagging()) {
            Backtrack.INSTANCE.forceFlush();
        }
    }

    @Inject(method = "handleOpenScreen", at = @At("RETURN"))
    private void setsuna$afterOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        ChestESP.INSTANCE.confirmContainerOpened(packet);
    }

    @Inject(method = "handleBlockEvent", at = @At("RETURN"))
    private void setsuna$afterBlockEvent(ClientboundBlockEventPacket packet, CallbackInfo ci) {
        ChestESP.INSTANCE.handleContainerBlockEvent(packet);
    }
}
