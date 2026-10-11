package com.setsuna.util.player;

import com.setsuna.manager.HealthManager;
import com.setsuna.ui.hud.TargetHud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Resolves the health value exposed to HUDs and combat target selection. */
public final class HealthDetectionUtils {

    private HealthDetectionUtils() {
    }

    /**
     * Returns the configured health value for an entity.
     * <p>
     * Hoplite reads a player's LIST-slot scoreboard value (the value shown by
     * the tab list) and falls back to vanilla health when that score is absent.
     * Heypixel reads the belowHealth/health scoreboard values sent by the server.
     * Other follows OpenOpal and always uses the entity's synchronized health plus
     * absorption.
     */
    public static float getHealth(LivingEntity entity) {
        return getHealth(entity, TargetHud.INSTANCE.healthDetection.get());
    }

    /** Resolves health using the mode selected by the calling HUD. */
    public static float getHealth(LivingEntity entity, TargetHud.HealthDetection detection) {
        if (entity == null) {
            return 0.0F;
        }

        if (detection == TargetHud.HealthDetection.Hoplite
                && entity instanceof Player player) {
            Float tabHealth = TabHealthUtils.getTabHealth(player);
            if (tabHealth != null) {
                return tabHealth;
            }
        }
        if (detection == TargetHud.HealthDetection.Heypixel
                && entity instanceof Player player) {
            Float scoreboardHealth = HealthManager.INSTANCE.getHeypixelHealth(player);
            if (scoreboardHealth != null) {
                return scoreboardHealth;
            }
        }
        return getEntityHealth(entity);
    }

    /** OpenOpal's effective health calculation. */
    public static float getEntityHealth(LivingEntity entity) {
        if (entity == null) {
            return 0.0F;
        }
        return entity.getHealth() + entity.getAbsorptionAmount();
    }
}
