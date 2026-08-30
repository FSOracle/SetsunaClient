package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.setsuna.module.modules.render.NoRender;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MobEffectFogEnvironment.class)
public class MobEffectFogEnvironmentMixin {

    @ModifyReturnValue(method = "isApplicable", at = @At("RETURN"))
    private boolean setsuna$hideNegativeEffectFog(boolean original, FogType fogType, Entity entity) {
        NoRender noRender = NoRender.INSTANCE;
        if (!original || !noRender.isEnabled() || !noRender.negativeEffects.get()) {
            return original;
        }
        if (entity instanceof LivingEntity livingEntity
                && (livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS))) {
            return false;
        }
        return true;
    }
}
