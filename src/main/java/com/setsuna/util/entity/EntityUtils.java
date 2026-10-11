package com.setsuna.util.entity;

import net.minecraft.world.entity.Entity;

/** Entity helpers required by the ported Backtrack status text. */
public final class EntityUtils {

    private EntityUtils() {
    }

    public static String safeName(Entity entity) {
        try {
            return entity.getName() == null ? "?" : entity.getName().getString();
        } catch (RuntimeException ignored) {
            return "?";
        }
    }
}
