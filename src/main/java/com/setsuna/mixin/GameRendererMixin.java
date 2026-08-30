package com.setsuna.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.module.modules.render.NoRender;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.ui.screen.VanillaScreenTheme;
import com.setsuna.util.client.ViewBobbingSuppressor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Final
    @Shadow
    private Minecraft minecraft;

    @Inject(method = "extractOptions", at = @At("TAIL"))
    private void setsuna$disableViewBobbing(CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        boolean noRenderBob = noRender.isEnabled() && noRender.bobView.get();
        if (noRenderBob || ViewBobbingSuppressor.isSuppressed()) {
            minecraft.gameRenderer.getGameRenderState().optionsRenderState.bobView = false;
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void setsuna$disableHurtCamera(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender.isEnabled() && noRender.hurtCam.get()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void setsuna$renderThemedBackdrop(DeltaTracker deltaTracker, boolean renderLevel,
                                            CallbackInfo ci) {
        if (VanillaScreenTheme.applies(minecraft.screen)) {
            SkijaRenderer.renderVanillaScreenTheme();
        }
    }
}
