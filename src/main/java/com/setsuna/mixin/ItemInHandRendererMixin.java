package com.setsuna.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.setsuna.module.modules.combat.KillAura;
import com.setsuna.module.modules.combat.KillAuraPlus;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders KillAura's visual block state and optional first-person block-hit animation. */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

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
                    target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;",
                    ordinal = 0))
    private ItemUseAnimation setsuna$blockAnimation(
            ItemUseAnimation original,
            @Local(argsOnly = true, name = "player") AbstractClientPlayer player,
            @Local(argsOnly = true, name = "itemStack") ItemStack itemStack) {
        return setsuna$shouldRenderBlock(player, itemStack) ? ItemUseAnimation.BLOCK : original;
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
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
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
}
