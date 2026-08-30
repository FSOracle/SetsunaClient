package com.setsuna.manager;

import com.setsuna.util.player.HealthDetectionUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Compatibility facade for the shared health source used by combat modules. */
public final class HealthManager {

    public static final HealthManager INSTANCE = new HealthManager();

    private HealthManager() {
    }

    public float getHealth(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            return HealthDetectionUtils.getHealth(livingEntity);
        }
        return 0f;
    }
}
