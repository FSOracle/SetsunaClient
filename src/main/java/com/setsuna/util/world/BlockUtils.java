package com.setsuna.util.world;

import com.setsuna.Setsuna;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.phys.AABB;

public final class BlockUtils {

    private BlockUtils() {
    }

    public static boolean canPlaceAt(BlockPos pos) {
        if (!Setsuna.mc().level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        return Setsuna.mc().level.getEntities(
                (Entity) null,
                new AABB(pos),
                entity -> !(entity instanceof ItemEntity
                        || entity instanceof ExperienceOrb
                        || entity instanceof ThrownExperienceBottle
                        || entity instanceof Arrow)
        ).isEmpty();
    }

    public static boolean isSolidBlock(BlockPos pos) {
        return Setsuna.mc().level.getBlockState(pos).isSolidRender();
    }
}
