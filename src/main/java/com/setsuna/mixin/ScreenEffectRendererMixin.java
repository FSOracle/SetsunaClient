package com.setsuna.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.module.modules.render.NoRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Shadow
    private @Nullable ItemStack itemActivationItem;

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void setsuna$hideBlockOverlay(
            TextureAtlasSprite texture,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci
    ) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender.isEnabled() && noRender.blockOverlay.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void setsuna$hideFireOverlay(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            TextureAtlasSprite texture,
            CallbackInfo ci
    ) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender.isEnabled() && noRender.fireOverlay.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void setsuna$hideTotemAnimation(
            PoseStack poseStack,
            float partialTick,
            SubmitNodeCollector nodeCollector,
            CallbackInfo ci
    ) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender.isEnabled()
                && noRender.totemAnimation.get()
                && itemActivationItem != null
                && itemActivationItem.is(Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        }
    }
}
