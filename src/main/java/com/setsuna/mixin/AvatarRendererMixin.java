package com.setsuna.mixin;

import com.setsuna.module.modules.render.LocalSkinCape;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.client.entity.ClientAvatarEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
    private void setsuna$localSkin(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        if (entity instanceof net.minecraft.client.player.AbstractClientPlayer player
                && player == com.setsuna.Setsuna.mc().player) {
            state.skin = LocalSkinCape.INSTANCE.apply(state.skin);
        }
    }
}
