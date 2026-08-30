package com.setsuna.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Invoker("getJumpPower")
    float setsuna$invokeGetJumpPower();

    @Accessor("noJumpDelay")
    int setsuna$getNoJumpDelay();
}
