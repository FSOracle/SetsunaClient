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
        var level = Setsuna.mc().level;
        if (level.getBlockState(pos).canBeReplaced()) {
            AABB occupiedVolume = new AABB(pos);
            for (Entity entity : level.getEntities((Entity) null, occupiedVolume, BlockUtils::blocksPlacement)) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static boolean isSolidBlock(BlockPos pos) {
        return Setsuna.mc().level.getBlockState(pos).isSolidRender();
    }

    private static boolean blocksPlacement(Entity entity) {
        return !(entity instanceof ItemEntity)
                && !(entity instanceof ExperienceOrb)
                && !(entity instanceof ThrownExperienceBottle)
                && !(entity instanceof Arrow);
    }
}
