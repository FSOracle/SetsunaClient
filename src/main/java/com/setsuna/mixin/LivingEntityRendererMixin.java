package com.setsuna.mixin;

import com.setsuna.accessor.EntityRenderStateAccessor;
import com.setsuna.accessor.StrafeJumpPoseAccess;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.RotationAnimationEvent;
import com.setsuna.module.modules.movement.Speed;
import com.setsuna.module.modules.render.Chams;
import com.setsuna.module.modules.render.ChamsSubmitNodeCollector;
import com.setsuna.module.modules.render.ESP;
import com.setsuna.module.modules.render.NameTags;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Flux's Normal and Colored Chams passes to other players. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Shadow
    public abstract Identifier getTextureLocation(S state);

    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void setsuna$submitChamsModel(
            SubmitNodeCollector collector,
            Model model,
            Object modelState,
            PoseStack poseStack,
            RenderType renderType,
            int light,
            int overlay,
            int color,
            TextureAtlasSprite sprite,
            int outlineColor,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            Operation<Void> original) {
        Chams chams = Chams.INSTANCE;
        Entity entity = modelState instanceof EntityRenderStateAccessor accessor
                ? accessor.setsuna$getEntity()
                : null;
        if (!chams.appliesTo(entity)) {
            original.call(collector, model, modelState, poseStack, renderType, light, overlay,
                    color, sprite, outlineColor, crumblingOverlay);
            return;
        }

        if (chams.isNormal()) {
            original.call(collector, model, modelState, poseStack,
                    chams.getNormalRenderType(renderType), light, overlay,
                    color, sprite, outlineColor, crumblingOverlay);
            return;
        }

        Identifier texture = getTextureLocation((S) modelState);
        int chamsColor = chams.getColoredArgb(entity);
        boolean flat = chams.flat.get();
        collector.order(-2).submitModel(model, modelState, poseStack,
                chams.getColoredRenderType(texture, flat, true),
                light, OverlayTexture.NO_OVERLAY, chamsColor, null, 0, null);
        collector.order(-1).submitModel(model, modelState, poseStack,
                chams.getColoredRenderType(texture, flat, false),
                light, OverlayTexture.NO_OVERLAY, chamsColor, null, 0, null);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
    private void setsuna$submitChamsLayer(
            RenderLayer layer,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            EntityRenderState state,
            float yRot,
            float xRot,
            Operation<Void> original) {
        Chams chams = Chams.INSTANCE;
        Entity entity = state instanceof EntityRenderStateAccessor accessor
                ? accessor.setsuna$getEntity()
                : null;
        if (chams.appliesTo(entity) && chams.isNormal()) {
            collector = new ChamsSubmitNodeCollector(collector, chams);
        }
        original.call(layer, poseStack, collector, light, state, yRot, xRot);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void setsuna$onExtractRenderState(T entity, S state, float partialTicks, CallbackInfo ci) {
        if (ESP.INSTANCE.shouldRenderOutline(entity)) {
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
