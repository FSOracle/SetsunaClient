package com.setsuna.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.setsuna.module.modules.combat.KillAura;
import com.setsuna.module.modules.combat.KillAuraPlus;
import com.setsuna.module.modules.movement.Scaffold;
import com.setsuna.module.modules.render.MCS;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders KillAura's visual block state and optional first-person block-hit animation. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow
    protected abstract void applyItemArmTransform(
            PoseStack poseStack, HumanoidArm arm, float equipProgress);

    @Shadow
    public abstract void renderItem(
            net.minecraft.world.entity.LivingEntity entity,
            ItemStack itemStack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords);

    @Shadow
    protected abstract void renderPlayerArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            float equipProgress,
            float swingProgress,
            HumanoidArm arm);

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack setsuna$scaffoldRenderedMainHand(
            ItemStack original,
            @Local(argsOnly = true, name = "player") AbstractClientPlayer player,
            @Local(argsOnly = true, name = "hand") InteractionHand hand) {
        if (player == Minecraft.getInstance().player && hand == InteractionHand.MAIN_HAND) {
            ItemStack rendered = Scaffold.INSTANCE.modifyRenderedMainHandStack(original);
            return MCS.INSTANCE.modifyRenderedMainHandStack(rendered);
        }
        return original;
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void setsuna$renderNiloreBlockAnimation(
            AbstractClientPlayer player,
            float frameInterp,
            float xRot,
            InteractionHand hand,
            float attack,
            ItemStack itemStack,
            float inverseArmHeight,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            CallbackInfo ci) {
        MCS mcs = MCS.INSTANCE;
        if (mcs.shouldSuppressOffhand(player, hand)) {
            ci.cancel();
            return;
        }
        if (mcs.shouldRenderFirstPersonWeapon(player, hand)) {
            ci.cancel();
            setsuna$renderMcsWeapon(
                    player, hand, inverseArmHeight, poseStack,
                    submitNodeCollector, lightCoords, mcs);
            return;
        }

        KillAura aura = KillAura.INSTANCE;
        if (player != Minecraft.getInstance().player
                || hand != InteractionHand.MAIN_HAND
                || !itemStack.is(ItemTags.SWORDS)
                || !aura.shouldUseNiloreBlockAnimation()) {
            return;
        }

        ci.cancel();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        aura.applyNiloreBlockAnimation(poseStack, attack, arm, inverseArmHeight);
        renderItem(
                player,
                itemStack,
                arm == HumanoidArm.RIGHT
                        ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                poseStack,
                submitNodeCollector,
                lightCoords);
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
    private boolean setsuna$keepVisualBlockHandHeight(boolean original) {
        return original && !setsuna$hasVisualAutoBlock();
    }

    @ModifyExpressionValue(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;isUsingItem()Z",
                    ordinal = 1))
    private boolean setsuna$isUsingItem(
            boolean original,
            @Local(argsOnly = true, name = "player") AbstractClientPlayer player,
            @Local(argsOnly = true, name = "itemStack") ItemStack itemStack) {
        return original || setsuna$shouldRenderBlock(player, itemStack);
    }

    @ModifyExpressionValue(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;",
                    ordinal = 1))
    private InteractionHand setsuna$usedHand(
            InteractionHand original,
            @Local(argsOnly = true, name = "player") AbstractClientPlayer player,
            @Local(argsOnly = true, name = "itemStack") ItemStack itemStack) {
        return setsuna$shouldRenderBlock(player, itemStack) ? InteractionHand.MAIN_HAND : original;
    }

    @ModifyExpressionValue(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUseItemRemainingTicks()I",
                    ordinal = 2))
    private int setsuna$useTicks(
            int original,
            @Local(argsOnly = true, name = "player") AbstractClientPlayer player,
            @Local(argsOnly = true, name = "itemStack") ItemStack itemStack) {
        return setsuna$shouldRenderBlock(player, itemStack) ? 7200 : original;
    }

    @Inject(
            method = "renderArmWithItem",
            slice = @Slice(from = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;")),
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER))
    private void setsuna$transformBlockAnimation(
            AbstractClientPlayer player,
            float frameInterp,
            float xRot,
            InteractionHand hand,
            float attack,
            ItemStack itemStack,
            float inverseArmHeight,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            CallbackInfo ci) {
        if (!setsuna$shouldAnimateBlockHit(player, itemStack) || !itemStack.is(ItemTags.SWORDS)) {
            return;
        }

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();

        poseStack.translate(arm == HumanoidArm.RIGHT ? -0.1F : 0.1F, 0.1F, 0.0F);
        setsuna$applySwingOffset(poseStack, arm, attack * 0.9F);
    }

    private static void setsuna$applySwingOffset(PoseStack poseStack, HumanoidArm arm, float swingProgress) {
        int armSide = arm == HumanoidArm.RIGHT ? 1 : -1;
        float firstSwing = Mth.sin(swingProgress * swingProgress * Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(armSide * (45.0F + firstSwing * -20.0F)));

        float secondSwing = Mth.sin(Mth.sqrt(swingProgress) * Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees(armSide * secondSwing * -20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(secondSwing * -80.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(armSide * -45.0F));
    }

    private static boolean setsuna$shouldRenderBlock(AbstractClientPlayer player, ItemStack itemStack) {
        if (player != Minecraft.getInstance().player || !itemStack.is(ItemTags.WEAPON_ENCHANTABLE)) {
            return false;
        }
        return setsuna$hasVisualAutoBlock();
    }

    private static boolean setsuna$hasVisualAutoBlock() {
        Minecraft minecraft = Minecraft.getInstance();
        KillAura aura = KillAura.INSTANCE;
        KillAuraPlus auraPlus = KillAuraPlus.INSTANCE;
        return aura.isAutoBlockVisualActive()
                || aura.shouldFakeBlock()
                || auraPlus.isFakeBlocking();
    }

    private static boolean setsuna$shouldAnimateBlockHit(AbstractClientPlayer player, ItemStack itemStack) {
        if (player != Minecraft.getInstance().player || !itemStack.is(ItemTags.WEAPON_ENCHANTABLE)) {
            return false;
        }
        KillAura aura = KillAura.INSTANCE;
        return aura.isAutoBlockVisualActive()
                || aura.shouldFakeBlock();
    }

    private void setsuna$renderMcsWeapon(
            AbstractClientPlayer player,
            InteractionHand hand,
            float inverseArmHeight,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            MCS mcs) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        poseStack.pushPose();
        try {
            applyItemArmTransform(poseStack, arm, inverseArmHeight);
            mcs.transformFirstPersonWeapon(poseStack, arm);
            renderItem(
                    player,
                    mcs.getVisualWeapon(),
                    arm == HumanoidArm.RIGHT
                            ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                            : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    poseStack,
                    submitNodeCollector,
                    lightCoords);
        } finally {
            poseStack.popPose();
        }

        if (mcs.shouldRenderSupportHand()) {
            HumanoidArm supportArm = arm.getOpposite();
            poseStack.pushPose();
            try {
                applyItemArmTransform(poseStack, supportArm, 0.0F);
                mcs.transformSupportHand(poseStack, supportArm);
                renderPlayerArm(poseStack, submitNodeCollector, lightCoords,
                        0.0F, 0.0F, supportArm);
            } finally {
                poseStack.popPose();
            }
        }
    }

}
