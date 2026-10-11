package com.setsuna.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.module.modules.render.EquipmentDisguise;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The humanoid armor layer already submits a successful chest replacement. */
@Mixin(WingsLayer.class)
public class WingsLayerMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void setsuna$hideReplacedWings(PoseStack poses, SubmitNodeCollector collector,
                                          int light, HumanoidRenderState state,
                                          float yRot, float xRot, CallbackInfo ci) {
        if (EquipmentDisguise.INSTANCE.replacesArmor(state, state.chestEquipment, EquipmentSlot.CHEST)) {
            ci.cancel();
        }
    }
}
