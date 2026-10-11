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

    private final SharedSelection shared = new SharedSelection();

    private TargetManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    @Listen
    private void onTick(TickEvent.Pre event) {
        boolean unavailable = mc.player == null || mc.level == null;
        if (unavailable || !sharedSelectionAlive()) {
            shared.clear();
        }
    }

    public LivingEntity acquirePrimary(TargetRequest request) {
        List<LivingEntity> targets = acquireTargets(request);
        return targets.isEmpty() ? null : targets.get(0);
    }

    public List<LivingEntity> acquireTargets(TargetRequest request) {
        if (mc.player == null || mc.level == null) return List.of();

        List<LivingEntity> candidates = collectTargets(request);
        if (candidates.isEmpty()) return candidates;

        promoteSelection(candidates, request);
        shared.touch(System.nanoTime());

        int maxTargets = Math.max(1, request.maxTargets());
        return candidates.size() > maxTargets
                ? List.copyOf(candidates.subList(0, maxTargets))
                : List.copyOf(candidates);
    }

    public LivingEntity getSharedTarget() {
        if (!sharedSelectionAlive()) shared.clear();
        return shared.entity();
    }

    private List<LivingEntity> collectTargets(TargetRequest request) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            LivingEntity living = asTarget(entity);
            if (living != null && living != mc.player && isValidTarget(living, request)) {
                targets.add(living);
            }
        }
        targets.sort(BY_EYE_DISTANCE);
        return targets;
    }

    private LivingEntity asTarget(Entity entity) {
        if (entity instanceof ArmorStand || !(entity instanceof LivingEntity living)) {
            return null;
        }
        return living;
    }

    private void promoteSelection(List<LivingEntity> candidates, TargetRequest request) {
        if (!sharedSelectionAlive()) shared.clear();

        LivingEntity previous = shared.entity();
        if (previous != null && isValidTarget(previous, request)) {
            if (candidates.remove(previous)) {
                candidates.add(0, previous);
            }
            return;
        }
        shared.select(candidates.get(0));
    }

    private boolean sharedSelectionAlive() {
        LivingEntity entity = shared.entity();
        if (entity == null || entity == mc.player) return false;
        if (!entity.isAlive() || entity.isDeadOrDying()) return false;
        if (entity.level() != mc.level) return false;
        return shared.ageAt(System.nanoTime()) <= SHARED_TARGET_TIMEOUT_NANOS;
    }

    private boolean isValidTarget(LivingEntity entity, TargetRequest request) {
        if (!entity.isAlive() || entity.isDeadOrDying()) return false;

        double dist = RotationUtils.getEyeDistanceToEntity(entity);
        if (dist > request.range()) return false;
        if (request.fov() < 360.0f && !RotationUtils.isInFov(entity, request.fov())) return false;

        if (!requestAllows(entity, request)) return false;
        return request.extraFilter().test(entity);
    }

    private boolean requestAllows(LivingEntity entity, TargetRequest request) {
        return switch (targetKind(entity)) {
            case PLAYER -> allowsPlayer((Player) entity, request);
            case VILLAGER -> request.villager();
            case ANIMAL -> request.animal();
            case MONSTER -> request.mob();
            case OTHER -> true;
        };
    }

    private boolean allowsPlayer(Player player, TargetRequest request) {
        if (FriendManager.INSTANCE.isFriend(player)) return false;
        if (!request.player()) return false;
        return !player.isInvisible() || request.invisible();
    }

    private static TargetKind targetKind(LivingEntity entity) {
        if (entity instanceof Player) return TargetKind.PLAYER;
        if (entity instanceof Villager) return TargetKind.VILLAGER;
        if (entity instanceof Animal) return TargetKind.ANIMAL;
        if (entity instanceof Monster) return TargetKind.MONSTER;
        return TargetKind.OTHER;
    }

    private enum TargetKind {
        PLAYER,
        VILLAGER,
        ANIMAL,
        MONSTER,
        OTHER
    }

    private static final class SharedSelection {
        private LivingEntity selected;
        private long updatedAt;

        private LivingEntity entity() {
            return selected;
        }

        private void select(LivingEntity entity) {
            selected = entity;
        }

        private void touch(long timestamp) {
            updatedAt = timestamp;
        }

        private long ageAt(long timestamp) {
            return timestamp - updatedAt;
        }

        private void clear() {
            selected = null;
            updatedAt = 0L;
        }
    }
}
