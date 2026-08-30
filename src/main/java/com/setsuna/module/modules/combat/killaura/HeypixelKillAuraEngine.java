package com.setsuna.module.modules.combat.killaura;

import com.setsuna.manager.HealthManager;
import com.setsuna.manager.RotationManager;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** OpenOpal KillAura behavior adapted to Setsuna's Mojmap runtime. */
public final class HeypixelKillAuraEngine {

    private static final long TARGET_REATTACK_DELAY_MS = 470L;

    private final Minecraft mc;
    private Map<Integer, TargetState> targetStates = new HashMap<>();

    private CurrentTarget attackTarget;
    private CurrentTarget rotationTarget;
    private double closestDistance = Double.MAX_VALUE;

    private int queuedAttacks;
    private long lastBypassAttackTime;
    private long swingTimerResetAt = Long.MIN_VALUE / 2L;
    private long nextAttackDelay;
    private long nextFakeSwingDelay;

    public HeypixelKillAuraEngine(Minecraft mc) {
        this.mc = mc;
    }

    public void update(Config config, Predicate<LivingEntity> extraFilter) {
        if (mc.player == null || mc.level == null) {
            resetTargets();
            return;
        }

        closestDistance = Double.MAX_VALUE;
        List<Candidate> candidates = collectCandidates(config, extraFilter);
        attackTarget = selectTarget(candidates, config.range(), false, config);
        if (attackTarget != null) {
            rotationTarget = attackTarget;
            retainTargetStates(candidates, config.range());
        } else {
            rotationTarget = selectTarget(candidates, config.rotationRange(), true, config);
            retainTargetStates(candidates, config.rotationRange());
        }
    }

    private List<Candidate> collectCandidates(Config config, Predicate<LivingEntity> extraFilter) {
        Vec3 eyes = mc.player.getEyePosition();
        double stateRange = Math.max(config.range(), config.rotationRange());
        List<Candidate> candidates = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living instanceof ArmorStand) continue;
            if (living == mc.player || !living.isAlive() || living.isDeadOrDying()) continue;
            if (!living.isAttackable() || living.isSpectator()) continue;
            if (living.isInvisible() && !config.invisible()) continue;
            if (!isSelectedType(living, config) || !isInFov(living, config.fov())) continue;
            if (!extraFilter.test(living)) continue;

            AABB box = targetingBox(living);
            double distance = eyes.distanceTo(closestPoint(eyes, box));
            closestDistance = Math.min(closestDistance, distance);
            if (distance > stateRange) continue;

            TargetState state = targetStates.get(living.getId());
            if (state == null || state.entity != living) {
                state = new TargetState(living);
            }
            candidates.add(new Candidate(living, state, distance));
        }
        return candidates;
    }

    private void retainTargetStates(List<Candidate> candidates, double interactionRange) {
        Map<Integer, TargetState> updatedStates = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate.distance <= interactionRange) {
                updatedStates.put(candidate.entity.getId(), candidate.state);
            }
        }
        targetStates = updatedStates;
    }

    private boolean isSelectedType(LivingEntity entity, Config config) {
        return switch (entity) {
            case Player ignored -> config.players();
            case Villager ignored -> config.villagers();
            case Animal ignored -> config.animals();
            case Monster ignored -> config.mobs();
            default -> config.mobs();
        };
    }

    private boolean isInFov(LivingEntity entity, float fov) {
        if (fov >= 180.0F) return true;
        Rot2f rotations = RotationUtils.calculate(mc.player.getEyePosition(), entity.position());
        return Math.abs(Mth.wrapDegrees(rotations.getYaw() - mc.player.getYRot())) < fov;
    }

    private CurrentTarget selectTarget(
            List<Candidate> allCandidates,
            double interactionRange,
            boolean distanceSorting,
            Config config) {
        List<Candidate> candidates = allCandidates.stream()
                .filter(candidate -> candidate.distance <= interactionRange)
                .sorted(distanceSorting
                        ? Comparator.comparingDouble(candidate -> candidate.entity.distanceToSqr(mc.player))
                        : Comparator.comparingDouble(this::healthPriority))
                .toList();

        List<CurrentTarget> converted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            CurrentTarget current = raytraceTarget(candidate, interactionRange);
            if (current != null && (config.throughWalls() || hasLineOfSight(current.hitResult))) {
                converted.add(current);
            }
        }

        if (!distanceSorting && config.targetMode() == TargetMode.SWITCH && converted.size() > 1) {
            converted.sort(Comparator.comparingDouble(current -> -switchPriority(current, System.currentTimeMillis())));
        }
        return converted.isEmpty() ? null : converted.getFirst();
    }

    private double healthPriority(Candidate candidate) {
        double health = HealthManager.INSTANCE.getHealth(candidate.entity);
        return candidate.entity instanceof Player ? health : health * 2.0D;
    }

    private double switchPriority(CurrentTarget current, long now) {
        return HealthManager.INSTANCE.getHealth(current.entity) * 25.0D
                + current.state.elapsedSinceAttack(now);
    }

    private CurrentTarget raytraceTarget(Candidate candidate, double interactionRange) {
        LivingEntity entity = candidate.entity;
        Vec3 eyes = mc.player.getEyePosition();
        AABB box = targetingBox(entity);
        Vec3 closest = closestPoint(eyes, box);

        Rot2f closestRotation = vanillaRotation(randomizedRotation(eyes, closest));
        EntityHitResult closestHit = raycastEntity(entity, interactionRange, closestRotation);
        if (closestHit != null) {
            return new CurrentTarget(entity, candidate.state, closestRotation, closestHit);
        }

        Vec3 center = box.getCenter();
        double widthX = box.getXsize();
        double height = box.getYsize();
        double widthZ = box.getZsize();
        float steps = 8.0F - ThreadLocalRandom.current().nextFloat() * 0.25F;

        Rot2f bestRotation = null;
        EntityHitResult bestHit = null;
        double bestDifference = Double.MAX_VALUE;
        for (double x = -widthX; x < widthX; x += widthX / steps) {
            for (double y = -height; y < height; y += height / steps) {
                for (double z = -widthZ; z < widthZ; z += widthZ / steps) {
                    Rot2f rotation = vanillaRotation(RotationUtils.calculate(
                            eyes,
                            center.add(x, y, z)
                    ));
                    EntityHitResult hit = raycastEntity(entity, interactionRange, rotation);
                    if (hit == null) continue;

                    double difference = rotationDifference(rotation, closestRotation);
                    if (difference < bestDifference) {
                        bestDifference = difference;
                        bestRotation = rotation;
                        bestHit = hit;
                    }
                }
            }
        }

        return bestRotation == null
                ? null
                : new CurrentTarget(entity, candidate.state, bestRotation, bestHit);
    }

    private Rot2f randomizedRotation(Vec3 eyes, Vec3 target) {
        Rot2f rotation = RotationUtils.calculate(eyes, target);
        double range = ThreadLocalRandom.current().nextDouble(0.01D, 0.05D);
        return new Rot2f(
                rotation.getYaw() + (float) ThreadLocalRandom.current().nextDouble(-range, range),
                rotation.getPitch() + (float) ThreadLocalRandom.current().nextDouble(-range, range)
        );
    }

    private Rot2f vanillaRotation(Rot2f rotation) {
        Rot2f playerRotation = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        Rot2f patched = patchConstantRotation(rotation, playerRotation, mc.options.sensitivity().get());
        float yaw = sensitivityModifiedRotation(patched.getYaw(), mc.options.sensitivity().get());
        float pitch = sensitivityModifiedRotation(patched.getPitch(), mc.options.sensitivity().get());
        return new Rot2f(
                mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()),
                pitch
        );
    }

    private boolean hasLineOfSight(EntityHitResult targetHit) {
        HitResult blockHit = mc.level.clip(new ClipContext(
                mc.player.getEyePosition(),
                targetHit.getLocation(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return blockHit.getType() == HitResult.Type.MISS;
    }

    public EntityHitResult raycastEntity(LivingEntity entity, double range, Rot2f rotation) {
        if (mc.player == null || entity == null) return null;
        Vec3 eyes = mc.player.getEyePosition(1.0F);
        Vec3 end = eyes.add(Vec3.directionFromRotation(
                rotation.getPitch(),
                rotation.getYaw()
        ).scale(range));
        AABB box = targetingBox(entity);
        if (box.contains(eyes)) {
            return new EntityHitResult(entity, eyes);
        }
        Optional<Vec3> hit = box.clip(eyes, end);
        return hit.map(location -> new EntityHitResult(entity, location)).orElse(null);
    }

    public Rot2f nextRotation(boolean heypixelBypass, float maxAngle) {
        if (rotationTarget == null) return null;
        if (!heypixelBypass) return rotationTarget.rotation.copy();

        Rot2f from = RotationManager.INSTANCE.isActive()
                ? RotationManager.INSTANCE.getRotation()
                : new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        return stepRotation(from, rotationTarget.rotation, maxAngle, mc.options.sensitivity().get());
    }

    public boolean isAttackAvailable(
            int cps,
            boolean heypixelBypass,
            boolean attackCooldown19,
            boolean smartWeaponAttack) {
        if (attackTarget == null || mc.player == null) return false;
        if (attackCooldown19 && !smartWeaponAttack) {
            return mc.player.getAttackStrengthScale(0.5F) >= 1.0F;
        }

        long now = System.currentTimeMillis();
        if (heypixelBypass) {
            long delay = randomizedBypassDelay(cps, ThreadLocalRandom.current().nextDouble());
            return now - lastBypassAttackTime >= delay;
        }

        double damage = getCurrentDamage();
        double health = HealthManager.INSTANCE.getHealth(attackTarget.entity);
        if (attackTarget.state.isAttackAvailable(now, health, damage) || queuedAttacks > 0) {
            return true;
        }
        return now - swingTimerResetAt >= nextAttackDelay;
    }

    public void recordAttack(int cps, boolean heypixelBypass) {
        if (attackTarget == null) return;
        long now = System.currentTimeMillis();
        attackTarget.state.onAttack(now, queuedAttacks == 0, getCurrentDamage());
        nextAttackDelay = clickDelay(cps);
        swingTimerResetAt = now;
        if (heypixelBypass) {
            lastBypassAttackTime = now;
        }
        if (queuedAttacks > 0) {
            queuedAttacks--;
        } else {
            queuedAttacks = 2;
        }
    }

    public void recordUnavailableAttack() {
        queuedAttacks = 0;
    }

    public boolean isFakeSwingAvailable(int cps) {
        return System.currentTimeMillis() - swingTimerResetAt >= nextFakeSwingDelay;
    }

    public void recordFakeSwing(int cps) {
        nextFakeSwingDelay = clickDelay(cps);
        swingTimerResetAt = System.currentTimeMillis();
    }

    public double getCurrentDamage() {
        if (mc.player == null) return 0.5D;
        ItemStack stack = mc.player.getMainHandItem();
        double[] damage = {0.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                damage[0] += modifier.amount();
            }
        });
        damage[0] = Math.max(0.5D, damage[0]);
        if (isCriticalHitAvailable() && mc.player.fallDistance > 0.0F) {
            damage[0] *= 1.5D;
        }
        return damage[0];
    }

    private boolean isCriticalHitAvailable() {
        return !mc.player.isInWater()
                && !mc.player.onClimbable()
                && !mc.player.hasEffect(MobEffects.BLINDNESS)
                && !mc.player.isPassenger();
    }

    public CurrentTarget getAttackTarget() {
        return attackTarget;
    }

    public CurrentTarget getRotationTarget() {
        return rotationTarget;
    }

    public double getClosestDistance() {
        return closestDistance;
    }

    public void resetTargeting() {
        resetTargets();
    }

    public void reset() {
        resetTargets();
        queuedAttacks = 0;
        lastBypassAttackTime = 0L;
        swingTimerResetAt = Long.MIN_VALUE / 2L;
        nextAttackDelay = 0L;
        nextFakeSwingDelay = 0L;
    }

    private void resetTargets() {
        targetStates.clear();
        attackTarget = null;
        rotationTarget = null;
        closestDistance = Double.MAX_VALUE;
    }

    private static AABB targetingBox(LivingEntity entity) {
        return entity.getBoundingBox().inflate(entity.getPickRadius());
    }

    private static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private static double rotationDifference(Rot2f first, Rot2f second) {
        return Math.abs(Mth.wrapDegrees(first.getYaw() - second.getYaw()))
                + Math.abs(first.getPitch() - second.getPitch());
    }

    static Rot2f stepRotation(Rot2f from, Rot2f to, float speed, double sensitivity) {
        float deltaYaw = Mth.wrapDegrees(to.getYaw() - from.getYaw());
        float deltaPitch = to.getPitch() - from.getPitch();
        double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance <= 1.0E-6D) return from.copy();

        double maxYaw = speed * Math.abs(deltaYaw / distance);
        double maxPitch = speed * Math.abs(deltaPitch / distance);
        float yaw = from.getYaw() + (float) Mth.clamp(deltaYaw, -maxYaw, maxYaw);
        float pitch = Mth.clamp(
                from.getPitch() + (float) Mth.clamp(deltaPitch, -maxPitch, maxPitch),
                -90.0F,
                90.0F
        );
        return patchConstantRotation(new Rot2f(yaw, pitch), from, sensitivity);
    }

    static Rot2f patchConstantRotation(Rot2f rotation, Rot2f previous, double sensitivity) {
        double adjusted = sensitivity * 0.6D + 0.2D;
        double divisor = adjusted * adjusted * adjusted * 8.0D * 0.15D;
        float yaw = previous.getYaw()
                + (float) (Math.round((rotation.getYaw() - previous.getYaw()) / divisor) * divisor);
        float pitch = previous.getPitch()
                + (float) (Math.round((rotation.getPitch() - previous.getPitch()) / divisor) * divisor);
        return new Rot2f(yaw, pitch);
    }

    private static float sensitivityModifiedRotation(double rotation, double sensitivity) {
        double adjusted = sensitivity * 0.6D + 0.2D;
        double multiplier = adjusted * adjusted * adjusted * 8.0D;
        float cursorDelta = (float) (rotation / multiplier) / 0.15F;
        return (float) (cursorDelta * multiplier) * 0.15F;
    }

    static long randomizedBypassDelay(int cps, double randomUnit) {
        double baseDelay = 1000.0D / Math.max(1, cps);
        return (long) (baseDelay + (randomUnit - 0.5D) * baseDelay * 0.4D);
    }

    private static long clickDelay(int cps) {
        return 1000L / Math.max(1, cps);
    }

    public enum TargetMode {
        SINGLE,
        SWITCH
    }

    public record Config(
            double range,
            double rotationRange,
            double swingRange,
            float fov,
            boolean players,
            boolean mobs,
            boolean animals,
            boolean villagers,
            boolean invisible,
            boolean throughWalls,
            TargetMode targetMode) {
    }

    public static final class CurrentTarget {
        private final LivingEntity entity;
        private final TargetState state;
        private final Rot2f rotation;
        private final EntityHitResult hitResult;

        private CurrentTarget(
                LivingEntity entity,
                TargetState state,
                Rot2f rotation,
                EntityHitResult hitResult) {
            this.entity = entity;
            this.state = state;
            this.rotation = rotation;
            this.hitResult = hitResult;
        }

        public LivingEntity entity() {
            return entity;
        }

        public Rot2f rotation() {
            return rotation;
        }

        public EntityHitResult hitResult() {
            return hitResult;
        }
    }

    private record Candidate(LivingEntity entity, TargetState state, double distance) {
    }

    static final class TargetState {
        private final LivingEntity entity;
        private LastAttackData lastAttack;

        private TargetState(LivingEntity entity) {
            this.entity = entity;
        }

        private void onAttack(long now, boolean reset, double damage) {
            if (lastAttack == null) {
                lastAttack = new LastAttackData(now, damage);
            } else {
                lastAttack.reset(now, reset, damage);
            }
        }

        private boolean isAttackAvailable(long now, double health, double damage) {
            return isTrackedAttackAvailable(
                    lastAttack != null,
                    elapsedSinceAttack(now),
                    health,
                    damage,
                    lastAttack == null ? 0.0D : lastAttack.damage
            );
        }

        private long elapsedSinceAttack(long now) {
            return lastAttack == null ? 0L : Math.max(0L, now - lastAttack.startedAt);
        }
    }

    private static final class LastAttackData {
        private long startedAt;
        private double damage;

        private LastAttackData(long now, double damage) {
            reset(now, true, damage);
        }

        private void reset(long now, boolean resetTimer, double damage) {
            if (resetTimer) startedAt = now;
            this.damage = damage;
        }
    }

    static boolean isTrackedAttackAvailable(
            boolean hasLastAttack,
            long elapsed,
            double health,
            double damage,
            double lastDamage) {
        return !hasLastAttack
                || health <= damage
                || elapsed >= TARGET_REATTACK_DELAY_MS
                || damage > lastDamage;
    }
}
