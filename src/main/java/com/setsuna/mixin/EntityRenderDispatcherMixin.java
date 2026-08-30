package com.setsuna.mixin;

import com.setsuna.accessor.EntityRenderStateAccessor;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Stamps the source entity onto each extracted render state. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @ModifyReturnValue(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> EntityRenderState setsuna$onExtractEntity(EntityRenderState state, E entity, float partialTicks) {
        if (state instanceof EntityRenderStateAccessor accessor) {
            accessor.setsuna$setEntity(entity);
        }
        return state;
    }
}
