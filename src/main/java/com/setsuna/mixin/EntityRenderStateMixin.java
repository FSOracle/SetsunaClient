package com.setsuna.mixin;

import com.setsuna.accessor.EntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds a back-reference to the source entity on the render state. */
@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateAccessor {

    @Unique
    private Entity setsuna$entity;

    @Override
    public Entity setsuna$getEntity() {
        return setsuna$entity;
    }

    @Override
    public void setsuna$setEntity(Entity entity) {
        this.setsuna$entity = entity;
    }
}
