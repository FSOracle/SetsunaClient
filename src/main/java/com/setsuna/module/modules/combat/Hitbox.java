package com.setsuna.module.modules.combat;

import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.util.player.TeamColorUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Enlarges the attack margin of valid local-player targets. */
public final class Hitbox extends Module {

    public static final Hitbox INSTANCE = new Hitbox();

    private final DoubleSetting size = add(new DoubleSetting("Size", 0.1, 0.0, 1.0, 0.01));
    private final BooleanSetting attackBox = add(new BooleanSetting("Attack Box", true));
    private final BooleanSetting applyToDebugHitbox = add(new BooleanSetting("Apply To Debug Hitbox", true));
    private final BooleanSetting applyToComponent = add(new BooleanSetting("Apply To Component", true));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", true));
    private final BooleanSetting animals = add(new BooleanSetting("Animals", true));
    private final BooleanSetting villagers = add(new BooleanSetting("Villagers", false));
    private final BooleanSetting invisible = add(new BooleanSetting("Invisible", false));
    private final BooleanSetting teamCheck = add(new BooleanSetting("Team Check", true));

    private Hitbox() {
        super("Hitbox", Category.COMBAT);
    }

    @Override
    public String getInfo() {
        return String.format("%.2f", size.get());
    }

    public float modifyPickRadius(float original, Entity entity) {
        return isEnabled() && attackBox.get() && shouldExpand(entity)
                ? size.get().floatValue()
                : original;
    }

    public float modifyComponentMargin(float original, Entity attacker) {
        if (!isEnabled() || !applyToComponent.get() || attacker != mc.player) {
            return original;
        }
        return original + size.get().floatValue();
    }

    public float modifyProjectileMargin(float original, Entity attacker) {
        if (attackBox.get()) {
            return original;
        }
        return modifyComponentMargin(original, attacker);
    }

    public AABB modifyAttackBox(AABB original, Entity entity, Entity attacker) {
        if (!isEnabled() || !attackBox.get() || attacker != mc.player || !shouldExpand(entity)) {
            return original;
        }
        return original.inflate(size.get());
    }

    public AABB modifyDebugBox(AABB original, Entity entity) {
        if (!isEnabled() || !applyToDebugHitbox.get() || !shouldExpand(entity)) {
            return original;
        }
        return original.inflate(size.get());
    }

    private boolean shouldExpand(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity == mc.player) {
            return false;
        }
        if (!entity.isAttackable() || !living.isAlive() || living.isDeadOrDying()) {
            return false;
        }
        if (entity.isInvisible() && !invisible.get()) {
            return false;
        }

        if (entity instanceof Player player) {
            if (!players.get() || FriendManager.INSTANCE.isFriend(player)) {
                return false;
            }
        } else if (entity instanceof Villager) {
            if (!villagers.get()) return false;
        } else if (entity instanceof Animal) {
            if (!animals.get()) return false;
        } else if (entity instanceof Monster) {
            if (!mobs.get()) return false;
        } else if (!mobs.get()) {
            return false;
        }

        return !isTeammate(living);
    }

    private boolean isTeammate(LivingEntity entity) {
        if (!teamCheck.get() || mc.player == null) {
            return false;
        }
        if (entity instanceof Player player) {
            if (TeamColorUtils.isFriendlyColor(player)) return true;
            if (TeamColorUtils.isEnemyColor(player)) return false;
            return !mc.player.canHarmPlayer(player);
        }
        return mc.player.isAlliedTo(entity);
    }
}
