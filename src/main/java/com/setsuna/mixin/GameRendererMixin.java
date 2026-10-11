package com.setsuna.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.RenderFrameEvent;
import com.setsuna.module.modules.render.NoRender;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.ui.hud.ScoreboardHUD;
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

    @Inject(method = "render", at = @At("HEAD"))
    private void setsuna$preRenderFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        EventBus.INSTANCE.post(new RenderFrameEvent(
                deltaTracker.getGameTimeDeltaPartialTick(true), renderLevel));
    }

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
        ScoreboardHUD.INSTANCE.renderBackdrop();
        // Vanilla screens render their own complete framebuffer. Injecting a
        // second Skija pass here races their extract/render-state pipeline and
        // can hide the world-selection list behind the custom backdrop.
    }
}
