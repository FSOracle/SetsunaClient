package com.setsuna.module.modules.combat;

import com.setsuna.manager.FriendManager;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;

final class VapeTargeting {

    private VapeTargeting() {
    }

    static boolean isValidTarget(
            Minecraft mc,
            Entity entity,
            boolean players,
            boolean mobs,
            boolean peaceful,
            boolean neutral,
            boolean ignoreNaked,
            boolean ignoreInvisible,
            boolean ignoreBehindWalls) {
        if (mc.player == null || !(entity instanceof LivingEntity target)
                || target == mc.player || target instanceof ArmorStand
                || target.isSpectator() || !target.isPickable()
                || !target.isAlive() || target.isDeadOrDying()) {
            return false;
        }
        if (ignoreInvisible && target.isInvisible()) {
            return false;
        }
        if (ignoreBehindWalls && !mc.player.hasLineOfSight(target)) {
            return false;
        }

        if (target instanceof Player player) {
            if (!players || FriendManager.INSTANCE.isFriend(player)) {
                return false;
            }
            return !ignoreNaked || player.getArmorValue() > 0;
        }
        if (target instanceof NeutralMob) {
            return neutral;
        }
        if (target instanceof Animal || target instanceof Villager) {
            return peaceful;
        }
        if (target instanceof Enemy) {
            return mobs;
        }
        return peaceful;
    }

    static boolean isAllowedItem(
            ItemStack stack,
            boolean swords,
            boolean axes,
            boolean maces,
            boolean spears,
            boolean pickaxes,
            boolean shovels,
            boolean hoes,
            boolean otherItems) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.SWORDS)) return swords;
        if (stack.is(ItemTags.AXES)) return axes;
        if (stack.getItem() instanceof MaceItem) return maces;
        if (stack.is(ItemTags.SPEARS)) return spears;
        if (stack.is(ItemTags.PICKAXES)) return pickaxes;
        if (stack.is(ItemTags.SHOVELS)) return shovels;
        if (stack.is(ItemTags.HOES)) return hoes;
        return otherItems;
    }
}
