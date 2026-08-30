package com.setsuna.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.setsuna.module.modules.render.FullBright;
import com.setsuna.module.modules.render.Xray;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lightmap.class)
public class LightmapMixin {

    @Final
    @Shadow
    private GpuTexture texture;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void setsuna$hookLightmap(LightmapRenderState renderState, CallbackInfo ci) {
        if (Xray.INSTANCE.isEnabled() || FullBright.INSTANCE.isGammaMode()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.texture, -1);
            ci.cancel();
        }
    }
}
