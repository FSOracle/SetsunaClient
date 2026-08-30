package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.setsuna.module.modules.movement.Velocity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Iterator;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @WrapOperation(
            method = "getFlow",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;hasNext()Z",
                    ordinal = 0))
    private boolean setsuna$cancelWaterPush(Iterator<Direction> iterator, Operation<Boolean> original) {
        if (Velocity.INSTANCE.isEnabled() && Velocity.INSTANCE.waterPush.get()) {
            return false;
        }
        return original.call(iterator);
    }
}
