package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.setsuna.module.modules.combat.Hitbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {

    @ModifyExpressionValue(
            method = "getHitEntitiesAlong(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/component/AttackRange;Ljava/util/function/Predicate;Lnet/minecraft/world/level/ClipContext$Block;)Lcom/mojang/datafixers/util/Either;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/AttackRange;hitboxMargin()F"))
    private static float setsuna$applyHitboxMargin(
            float original,
            @Local(argsOnly = true, name = "attacker") Entity attacker
    ) {
        return Hitbox.INSTANCE.modifyProjectileMargin(original, attacker);
    }

    @ModifyExpressionValue(
            method = "getManyEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;FLnet/minecraft/world/level/ClipContext$Block;Z)Ljava/util/Collection;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB setsuna$applyAttackBox(
            AABB original,
            @Local(argsOnly = true, name = "source") Entity source,
            @Local(name = "entity") Entity entity
    ) {
        return Hitbox.INSTANCE.modifyAttackBox(original, entity, source);
    }
}
