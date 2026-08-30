package com.setsuna.module.modules.combat.killauraplus;

import com.setsuna.manager.FriendManager;
import com.setsuna.manager.HealthManager;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.modules.combat.AntiBot;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.RaytraceUtils;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Mojmap implementation of the narrow context recovered with Clap KillAura. */
public final class MinecraftKillAuraContext implements KillAuraContext {
    private final Minecraft mc;

    public MinecraftKillAuraContext(Minecraft mc) {
        this.mc = mc;
    }

    @Override
    public boolean isAvailable() {
        return mc.player != null && mc.level != null;
    }

    @Override
    public boolean isScreenOpen() {
        return mc.screen != null;
    }

    @Override
    public boolean isSupportedWeaponHeld() {
        if (mc.player == null) return false;
        var stack = mc.player.getMainHandItem();
        return stack.is(ItemTags.SWORDS)
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof TridentItem;
    }

    @Override
    public boolean isSwordHeld() {
        return mc.player != null && mc.player.getMainHandItem().is(ItemTags.SWORDS);
    }

    @Override
    public boolean isInteractionAvailable() {
        return mc.player != null && mc.gameMode != null;
    }

    @Override
    public float playerYaw() {
        return mc.player == null ? 0.0f : mc.player.getYRot();
    }

    @Override
    public float playerPitch() {
        return mc.player == null ? 0.0f : mc.player.getXRot();
    }

    @Override
    public float attackCooldownProgress() {
        return mc.player == null ? 0.0f : mc.player.getAttackStrengthScale(0.5f);
    }

    @Override
    public LegitAuraCriticalState criticalState() {
        if (mc.player == null) {
            return new LegitAuraCriticalState(
                    0.0, 0.0f, true, false, false, false,
                    false, false, false, false, 0.0f);
        }
        boolean flying = mc.player.isFallFlying()
                || mc.player.isNoGravity()
                || mc.player.getAbilities().flying
                || mc.player.hasEffect(MobEffects.LEVITATION)
                || mc.player.hasEffect(MobEffects.SLOW_FALLING);
        return new LegitAuraCriticalState(
                mc.player.getDeltaMovement().y,
                (float) mc.player.fallDistance,
                mc.player.onGround(),
                mc.player.isInWater(),
                mc.player.isInLava(),
                mc.player.onClimbable(),
                mc.player.hasEffect(MobEffects.BLINDNESS),
                mc.player.isPassenger(),
                flying,
                mc.player.isSprinting(),
                mc.player.getCurrentItemAttackStrengthDelay());
    }

    @Override
    public List<KillAuraTargetSnapshot> targets(double searchRange) {
        if (mc.player == null || mc.level == null) return List.of();
        Vec3 eyes = mc.player.getEyePosition();
        List<KillAuraTargetSnapshot> targets = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;

            AABB box = living.getBoundingBox().inflate(living.getPickRadius());
            Vec3 closest = closestPoint(eyes, box);
            double closestDistance = eyes.distanceTo(closest);
            if (closestDistance > searchRange) continue;

            Rot2f rotation = RotationUtils.calculate(eyes, closest);
            boolean player = living instanceof Player;
            targets.add(new KillAuraTargetSnapshot(
                    living.getId(),
                    targetType(living),
                    living == mc.player,
                    living.isAlive() && !living.isDeadOrDying(),
                    living.isRemoved(),
                    living.isInvisible(),
                    mc.player.hasLineOfSight(living),
                    player && FriendManager.INSTANCE.isFriend((Player) living),
                    isTeammate(living),
                    player && AntiBot.INSTANCE.isBot((Player) living),
                    closestDistance,
                    living.distanceToSqr(mc.player),
                    Math.max(0.0f, HealthManager.INSTANCE.getHealth(living)),
                    rotation.getYaw(),
                    rotation.getPitch()));
        }
        return targets;
    }

    @Override
    public void requestRotation(float yaw, float pitch, float speed) {
        RotationManager.INSTANCE.setRotations(
                new Rot2f(yaw, pitch),
                speed / 18.0,
                Priority.Medium);
    }

    @Override
    public boolean rotationRayHitsTarget(int entityId, double reach) {
        HitResult hit = RaytraceUtils.raytrace(RotationManager.INSTANCE.getRotation(), reach);
        return hit instanceof EntityHitResult entityHit && entityHit.getEntity().getId() == entityId;
    }

    @Override
    public void attackEntity(int entityId) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (entity != null) {
            mc.gameMode.attack(mc.player, entity);
        }
    }

    @Override
    public void swingMainHand() {
        if (mc.player != null) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    public LivingEntity livingEntity(int entityId) {
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(entityId);
        return entity instanceof LivingEntity living ? living : null;
    }

    private boolean isTeammate(LivingEntity entity) {
        if (mc.player == null) return false;
        if (entity instanceof Player playerTarget) {
            if (TeamColorUtils.isFriendlyColor(playerTarget)) return true;
            if (TeamColorUtils.isEnemyColor(playerTarget)) return false;
            return !mc.player.canHarmPlayer(playerTarget);
        }
        return mc.player.isAlliedTo(entity);
    }

    private static KillAuraTargetType targetType(LivingEntity entity) {
        if (entity instanceof Player) return KillAuraTargetType.PLAYER;
        if (entity instanceof Animal) return KillAuraTargetType.ANIMAL;
        if (entity instanceof Monster) return KillAuraTargetType.HOSTILE_MOB;
        if (entity instanceof Mob) return KillAuraTargetType.OTHER_MOB;
        return KillAuraTargetType.OTHER;
    }

    private static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }
}
