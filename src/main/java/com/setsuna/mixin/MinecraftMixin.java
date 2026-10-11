package com.setsuna.mixin;

import com.setsuna.Setsuna;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.StartUseItemEvent;
import com.setsuna.event.events.CrosshairUpdateEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.event.events.UseCooldownEvent;
import com.setsuna.event.events.WorldChangeEvent;
import com.setsuna.accessor.MinecraftSessionAccessor;
import com.setsuna.module.modules.combat.Backtrack;
import com.setsuna.module.modules.player.AutoTool;
import com.setsuna.module.modules.render.TeamViewer;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.runtime.FeatureRuntime;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.ui.clickgui.PopClickGuiScreen;
import com.setsuna.util.network.BlinkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.objectweb.asm.Opcodes;

/**
 * Fires {@link TickEvent.Pre} and {@link TickEvent.Post} around the client's
 * main tick, giving modules a stable per-tick hook.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin implements MinecraftSessionAccessor {

    @Mutable
    @Final
    @Shadow
    private User user;

    @Shadow
    private int rightClickDelay;

    @Unique
    private ClientLevel setsuna$previousLevel;

    @Override
    public void setsuna$setUser(User user) {
        this.user = user;
    }

    @ModifyArg(
            method = "updateTitle",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/Window;setTitle(Ljava/lang/String;)V"))
    private String setsuna$title(String title) {
        return Setsuna.clientName() + " " + Setsuna.VERSION;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void setsuna$preTick(CallbackInfo ci) {
        EventBus.INSTANCE.post(new TickEvent.Pre());
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;pick(F)V",
                    shift = At.Shift.AFTER),
            require = 1)
    private void setsuna$prepareAutoTool(CallbackInfo ci) {
        EventBus.INSTANCE.post(new CrosshairUpdateEvent());
        AutoTool.INSTANCE.prepareMiningTick();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void setsuna$postTick(CallbackInfo ci) {
        EventBus.INSTANCE.post(new TickEvent.Post());
        AutoTool.INSTANCE.finishMiningTick();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void setsuna$capturePreviousLevel(ClientLevel nextLevel, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        setsuna$previousLevel = minecraft.level;
        if (minecraft.level != nextLevel) {
            EventBus.INSTANCE.post(new WorldChangeEvent(minecraft.level, nextLevel));
        }
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void setsuna$afterLevelChange(ClientLevel nextLevel, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        ClientLevel previousLevel = setsuna$previousLevel;
        setsuna$previousLevel = null;
        TeamViewer.INSTANCE.handleLevelChange(previousLevel, minecraft.level);
    }

    @Inject(
            method = "startUseItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/InteractionHand;values()[Lnet/minecraft/world/InteractionHand;"),
            cancellable = true)
    private void setsuna$startUseItem(CallbackInfo ci) {
        if (EventBus.INSTANCE.post(new StartUseItemEvent()).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void setsuna$processBlinkQueue(boolean advanceGameTime, CallbackInfo ci) {
        BlinkManager.INSTANCE.onFrame();
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void setsuna$backtrackAfterAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && minecraft.hitResult instanceof EntityHitResult entityHit) {
            Backtrack.INSTANCE.onAttack(entityHit.getEntity());
        }
    }

    @Inject(
            method = "startUseItem",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER))
    private void setsuna$useCooldown(CallbackInfo ci) {
        UseCooldownEvent event = EventBus.INSTANCE.post(new UseCooldownEvent(rightClickDelay));
        rightClickDelay = event.cooldown();
    }

    @Inject(
            method = "renderFrame(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V",
                    shift = At.Shift.AFTER))
    private void setsuna$renderSkija(boolean advanceGameTime, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        Overlay overlay = minecraft.getOverlay();
        if (overlay instanceof LoadingOverlay loading) {
            // The reload overlay sits above any screen; cover it with our own
            // visuals while its fade/completion lifecycle keeps running underneath.
            float progress = loading instanceof LoadingOverlayAccessor accessor
                    ? accessor.setsuna$getCurrentProgress()
                    : -1.0F;
            SkijaRenderer.renderLoading(progress);
        } else if (minecraft.screen instanceof SkijaScreen skijaScreen) {
            if (minecraft.screen instanceof PopClickGuiScreen) {
                SkijaRenderer.renderOverlay();
            }
            SkijaRenderer.render(skijaScreen);
        } else if (minecraft.screen == null && FeatureRuntime.INSTANCE.isActive()) {
            // HUD/world overlays belong to gameplay only. Touching the presented
            // framebuffer while a vanilla screen (notably TitleScreen) owns it can
            // replace that screen's frame during a Skija-to-vanilla transition.
            SkijaRenderer.renderOverlay();
        }
    }
}
