package com.setsuna.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.render.OverlayGuiRenderer;
import com.setsuna.ui.clickgui.PopClickGuiScreen;
import com.setsuna.ui.hud.EpsilonHudModule;
import com.setsuna.ui.hud.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Shadow
    @Final
    private SubmitNodeCollector submitNodeCollector;

    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Unique
    private GuiRenderState setsuna$overlayRenderState;

    @Unique
    private OverlayGuiRenderer setsuna$overlayRenderer;

    @Inject(method = "render", at = @At("TAIL"))
    private void setsuna$renderVanillaHud(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        // The mixin also applies to OverlayGuiRenderer; only the game's renderer
        // is allowed to create and invoke the secondary pass.
        if (((GuiRenderer) (Object) this).getClass() != GuiRenderer.class) return;

        Minecraft minecraft = Minecraft.getInstance();
        boolean hudEditor = minecraft.screen instanceof HudEditorScreen;
        boolean popClickGui = minecraft.screen instanceof PopClickGuiScreen;
        if (minecraft.level == null || minecraft.player == null
                || minecraft.screen != null && !hudEditor && !popClickGui) return;

        if (setsuna$overlayRenderState == null || setsuna$overlayRenderer == null) {
            setsuna$overlayRenderState = new GuiRenderState();
            setsuna$overlayRenderer = new OverlayGuiRenderer(
                    setsuna$overlayRenderState,
                    bufferSource,
                    submitNodeCollector,
                    featureRenderDispatcher
            );
        }

        int mouseX = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        int mouseY = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(
                minecraft, setsuna$overlayRenderState, mouseX, mouseY);
        try {
            VanillaHudRenderEvent event = new VanillaHudRenderEvent(
                    graphics,
                    minecraft.getDeltaTracker(),
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight()
            );
            if (hudEditor) {
                EventBus.INSTANCE.postTo(event, subscriber -> subscriber instanceof EpsilonHudModule);
            } else {
                EventBus.INSTANCE.post(event);
            }
            setsuna$overlayRenderer.render(fogBuffer);
        } finally {
            setsuna$overlayRenderer.endFrame();
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void setsuna$closeVanillaHudRenderer(CallbackInfo ci) {
        if (((GuiRenderer) (Object) this).getClass() != GuiRenderer.class) return;
        if (setsuna$overlayRenderer != null) {
            setsuna$overlayRenderer.close();
            setsuna$overlayRenderer = null;
            setsuna$overlayRenderState = null;
        }
    }
}
