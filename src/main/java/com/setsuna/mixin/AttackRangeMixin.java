package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.setsuna.module.modules.combat.Hitbox;
import com.setsuna.module.modules.combat.Reach;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.AttackRange;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AttackRange.class)
public abstract class AttackRangeMixin {

    @ModifyReturnValue(method = "effectiveMaxRange", at = @At("RETURN"))
    private float setsuna$applyReach(float original, Entity attacker) {
        return Reach.INSTANCE.modifyAttackRange(original, attacker);
    }

    @ModifyExpressionValue(
            method = "isInRange(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/ToDoubleFunction;D)Z",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/item/component/AttackRange;hitboxMargin:F",
                    opcode = Opcodes.GETFIELD))
    private float setsuna$applyHitboxMargin(
            float original,
            @Local(argsOnly = true, name = "attacker") LivingEntity attacker
    ) {
        return Hitbox.INSTANCE.modifyComponentMargin(original, attacker);
    }
}
