package com.setsuna.mixin;

import com.setsuna.accessor.EntityRenderStateAccessor;
import com.setsuna.accessor.StrafeJumpPoseAccess;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.RotationAnimationEvent;
import com.setsuna.module.modules.movement.Speed;
import com.setsuna.module.modules.render.Chams;
import com.setsuna.module.modules.render.ESP;
import com.setsuna.module.modules.render.NameTags;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies {@link Chams}: swaps the entity render type for a depth-ignoring one
 * ("No Depth"), and tags the render state with a glow outline colour.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Shadow
    public abstract Identifier getTextureLocation(S state);

    @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"))
    private RenderType setsuna$modifyRenderType(RenderType original, S state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
        Chams chams = Chams.INSTANCE;
        if (chams.isEnabled() && chams.noDepth.get()
                && ((EntityRenderStateAccessor) state).setsuna$getEntity() instanceof Player player
                && player != Minecraft.getInstance().player) {
            return chams.getRenderType(getTextureLocation(state));
        }
        return original;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void setsuna$onExtractRenderState(T entity, S state, float partialTicks, CallbackInfo ci) {
        Chams chams = Chams.INSTANCE;
        if (chams.isEnabled() && chams.shouldRenderGlow(entity)) {
            state.outlineColor = chams.getGlowColor(entity);
        } else if (ESP.INSTANCE.shouldRenderOutline(entity)) {
            state.outlineColor = ESP.INSTANCE.getPlayerColor((Player) entity);
        }
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;solveBodyRot(Lnet/minecraft/world/entity/LivingEntity;FF)F"))
    private float setsuna$modifyBodyYaw(float original, T entity, S state, float partialTicks) {
        if (entity == Minecraft.getInstance().player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(
                    entity.yBodyRot, entity.yBodyRotO, 0.0f, 0.0f));
            float bodyYaw = Mth.rotLerp(partialTicks, event.getLastYaw(), event.getYaw());
            if (entity instanceof StrafeJumpPoseAccess pose
                    && Speed.INSTANCE.shouldRenderFortyFivePose()) {
                bodyYaw += pose.setsuna$getVisualBodyOffset(partialTicks);
            }
            return bodyYaw;
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F"))
    private float setsuna$modifyHeadYaw(float original, T entity, S state, float partialTicks) {
        if (entity == Minecraft.getInstance().player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(
                    entity.yHeadRot, entity.yHeadRotO, 0.0f, 0.0f));
            float headYaw = Mth.rotLerp(partialTicks, event.getLastYaw(), event.getYaw());
            if (entity instanceof StrafeJumpPoseAccess pose
                    && Speed.INSTANCE.shouldRenderFortyFivePose()) {
                headYaw += pose.setsuna$getVisualHeadOffset(partialTicks);
            }
            return headYaw;
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F"))
    private float setsuna$modifyPitch(float original, T entity, S state, float partialTicks) {
        if (entity == Minecraft.getInstance().player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(
                    0.0f, 0.0f, entity.getXRot(), entity.getXRot(0.0f)));
            return Mth.rotLerp(partialTicks, event.getLastPitch(), event.getPitch());
        }
        return original;
    }

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void setsuna$onShouldShowName(T entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player && NameTags.INSTANCE.isEnabled() && !NameTags.INSTANCE.vanillaNameTags.get()) {
            cir.setReturnValue(false);
        }
    }
}
