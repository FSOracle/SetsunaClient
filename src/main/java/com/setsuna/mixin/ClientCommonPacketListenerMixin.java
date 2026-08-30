package com.setsuna.mixin;

import com.setsuna.module.modules.player.AntiResourcePack;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks vanilla's server resource-pack handling before it can prompt or download. */
@Mixin(value = ClientCommonPacketListenerImpl.class, priority = 1100)
public abstract class ClientCommonPacketListenerMixin {

    @Shadow
    public abstract void send(Packet<?> packet);

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void setsuna$handleResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (AntiResourcePack.INSTANCE.handlePush(packet, this::send)) {
            ci.cancel();
        }
    }
}
