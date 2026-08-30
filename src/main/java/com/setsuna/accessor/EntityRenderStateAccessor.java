package com.setsuna.accessor;

import net.minecraft.world.entity.Entity;

/** Carries the source entity alongside vanilla's extracted render state. */
public interface EntityRenderStateAccessor {

    Entity setsuna$getEntity();

    void setsuna$setEntity(Entity entity);
}
