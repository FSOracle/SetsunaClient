package com.setsuna.mixin;

import com.setsuna.accessor.EntityRenderStateAccessor;
import com.setsuna.module.modules.render.ArmorTrimOverride;
import com.setsuna.module.modules.render.EquipmentDisguise;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces local armor only after its full appearance has successfully loaded. */
@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @Shadow
    private HumanoidModel<?> getArmorModel(HumanoidRenderState state, EquipmentSlot slot) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void setsuna$clearReloadedArmor(CallbackInfo ci) {
        EquipmentDisguise.INSTANCE.clearModels();
    }

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void setsuna$replaceVanillaArmor(PoseStack poseStack, SubmitNodeCollector collector,
            ItemStack stack, EquipmentSlot slot, int light, HumanoidRenderState state,
            CallbackInfo ci) {
        EquipmentDisguise disguise = EquipmentDisguise.INSTANCE;
        if (disguise.renderArmorDetails(state, stack, slot, getArmorModel(state, slot),
                poseStack, collector, light)) {
            ci.cancel();
        }
    }

    @ModifyVariable(
            method = "renderArmorPiece",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private ItemStack setsuna$overrideLocalArmorTrim(
            ItemStack stack,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            SubmitNodeCollector collector,
            ItemStack original,
            EquipmentSlot slot,
            int light,
            HumanoidRenderState state
    ) {
        if (!(state instanceof EntityRenderStateAccessor accessor)
                || !(accessor.setsuna$getEntity() instanceof Player player)) {
            return stack;
        }
        return ArmorTrimOverride.INSTANCE.applyToLocalPlayer(player, stack, slot);
    }
}
