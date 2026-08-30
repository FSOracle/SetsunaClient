package com.setsuna.manager.target;

import com.setsuna.event.EventBus;
import com.setsuna.event.Listen;
import com.setsuna.event.events.TickEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared target acquisition for combat modules (AntiBot filtering not yet ported). */
public final class TargetManager {

    public static final TargetManager INSTANCE = new TargetManager();

    private static final Minecraft mc = Minecraft.getInstance();

    private static final Comparator<LivingEntity> BY_EYE_DISTANCE =
            Comparator.comparingDouble(RotationUtils::getEyeDistanceToEntity);
    private static final long SHARED_TARGET_TIMEOUT_NANOS = 500_000_000L;

    private LivingEntity sharedTarget;
    private long sharedTargetUpdatedAt;

    private TargetManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    @Listen
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || !isSharedTargetAlive()) {
            clearSharedTarget();
        }
    }

    public LivingEntity acquirePrimary(TargetRequest request) {
        List<LivingEntity> targets = acquireTargets(request);
        return targets.isEmpty() ? null : targets.getFirst();
    }

    public List<LivingEntity> acquireTargets(TargetRequest request) {
        if (mc.player == null || mc.level == null) return List.of();

        List<LivingEntity> candidates = collectTargets(request);
        if (candidates.isEmpty()) return candidates;

        if (!isSharedTargetAlive()) {
            clearSharedTarget();
        }

        if (sharedTarget != null && isValidTarget(sharedTarget, request)) {
            if (candidates.remove(sharedTarget)) {
                candidates.add(0, sharedTarget);
            }
        } else {
            sharedTarget = candidates.getFirst();
        }
        sharedTargetUpdatedAt = System.nanoTime();

        int maxTargets = Math.max(1, request.maxTargets());
        if (candidates.size() > maxTargets) {
            return List.copyOf(candidates.subList(0, maxTargets));
        }
        return List.copyOf(candidates);
    }

    public LivingEntity getSharedTarget() {
        if (!isSharedTargetAlive()) {
            clearSharedTarget();
        }
        return sharedTarget;
    }

    private List<LivingEntity> collectTargets(TargetRequest request) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand) continue;
            if (living == mc.player) continue;
            if (!isValidTarget(living, request)) continue;
            targets.add(living);
        }
        targets.sort(BY_EYE_DISTANCE);
        return targets;
    }

    private boolean isSharedTargetAlive() {
        if (sharedTarget == null) return false;
        if (sharedTarget == mc.player) return false;
        if (!sharedTarget.isAlive() || sharedTarget.isDeadOrDying()) return false;
        if (sharedTarget.level() != mc.level) return false;
        return System.nanoTime() - sharedTargetUpdatedAt <= SHARED_TARGET_TIMEOUT_NANOS;
    }

    private void clearSharedTarget() {
        sharedTarget = null;
        sharedTargetUpdatedAt = 0L;
    }

    private boolean isValidTarget(LivingEntity entity, TargetRequest request) {
        if (!entity.isAlive() || entity.isDeadOrDying()) return false;

        double dist = RotationUtils.getEyeDistanceToEntity(entity);
        if (dist > request.range()) return false;
        if (request.fov() < 360.0f && !RotationUtils.isInFov(entity, request.fov())) return false;

        switch (entity) {
            case Player player -> {
                if (FriendManager.INSTANCE.isFriend(player)) return false;
                if (!request.player()) return false;
                if (entity.isInvisible() && !request.invisible()) return false;
            }
            case Villager ignored -> {
                if (!request.villager()) return false;
            }
            case Animal ignored -> {
                if (!request.animal()) return false;
            }
            case Monster ignored -> {
                if (!request.mob()) return false;
            }
            default -> {
            }
        }
        return request.extraFilter().test(entity);
    }
}
