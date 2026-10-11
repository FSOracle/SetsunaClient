package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.setsuna.module.modules.combat.Hitbox;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {

    @ModifyExpressionValue(
            method = "showHitboxes",
            at = {
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"),
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;getBoundingBox()Lnet/minecraft/world/phys/AABB;")
            })
    private AABB setsuna$applyDebugHitbox(
            AABB original,
            @Local(argsOnly = true, name = "entity") Entity entity
    ) {
        return Hitbox.INSTANCE.modifyDebugBox(original, entity);
    }
}
