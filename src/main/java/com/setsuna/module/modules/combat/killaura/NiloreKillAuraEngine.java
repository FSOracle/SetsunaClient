package com.setsuna.module.modules.combat.killaura;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.manager.FriendManager;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.modules.combat.AntiBot;
import com.setsuna.module.modules.movement.Scaffold;
import com.setsuna.module.modules.movement.Velocity;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.render.esp.CircleESP;
import com.setsuna.util.rotation.NiloreRotationModel;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.RaytraceUtils;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Direct runtime adaptation of Nilore's KillAura state machine. */
public final class NiloreKillAuraEngine {

    public enum Style {
        New,
        Old,
        onTickRot
    }

    public enum DelayMode {
        OnePointEight,
        OnePointNine
    }

    public enum PriorityMode {
        Distance,
        FoV,
        Health,
        None
    }

    public enum TargetEsp {
        None,
        Spiral,
        Box,
        Tab,
        NurikZapen
    }

    public record Config(
            boolean attackPlayer,
            boolean attackInvisible,
            boolean attackAnimals,
            boolean attackMobs,
            boolean multiAttack,
            boolean infinitySwitch,
            boolean preferBaby,
            boolean moreParticles,
            Style style,
            boolean fix,
            boolean overrideRaycast,
            boolean throughWalls,
            double throughWallsRange,
            boolean ignoreSkipTicks,
            boolean fakeAutoBlock,
            boolean test,
            double aimRange,
            int maxAps,
            int minAps,
            int switchSize,
            int switchDelay,
            int fov,
            int hurtTime,
            DelayMode delayMode,
            PriorityMode priority,
            TargetEsp targetEsp,
            boolean prediction,
            int enemyDelayTicks,
            int selfDelayTicks,
            double rotationSpeed,
            double rotationDrift,
            double rotationJitter,
            boolean teamCheck) {
    }

    private final Minecraft mc;
    private final List<LivingEntity> targetList = new ArrayList<>();

    private LivingEntity target;
    private LivingEntity aimingTarget;
    private NiloreRotationModel.BestHit currentBestHit;
    private NiloreRotationModel.BestHit previousBestHit;
    private Rot2f rotation;
    private int attackTimes;
    private double attacks;
    private int targetIndex;

    private Random organicRandom;
    private double organicTimeAccumulator;
    private double yawFrequencyOne;
    private double yawFrequencyTwo;
    private double pitchFrequencyOne;
    private double pitchFrequencyTwo;
    private double yawPhaseOne;
    private double yawPhaseTwo;
    private double pitchPhaseOne;
    private double pitchPhaseTwo;

    public NiloreKillAuraEngine(Minecraft mc) {
        this.mc = mc;
        resetOrganicModel();
    }

    public void enable() {
        reset();
        resetOrganicModel();
    }

    public void disable() {
        RotationManager.INSTANCE.releaseRotations(this);
        reset();
    }

    public void reset() {
        target = null;
        aimingTarget = null;
        currentBestHit = null;
        previousBestHit = null;
        rotation = null;
        targetList.clear();
        attackTimes = 0;
        attacks = 0.0D;
        targetIndex = 0;
    }

    public LivingEntity target() {
        return target;
    }

    public LivingEntity aimingTarget() {
        return aimingTarget;
    }

    public Rot2f rotation() {
        return rotation;
    }

    public boolean isFakeBlocking(Config config) {
        return config.fakeAutoBlock()
                && aimingTarget != null
                && aimingTarget.isAlive()
                && mc.player != null
                && mc.player.getMainHandItem().is(ItemTags.SWORDS);
    }

    public void tick(Config config) {
        if (!ready() || shouldSuspend(config)) {
            RotationManager.INSTANCE.releaseRotations(this);
            resetTransientState();
            return;
        }

        boolean switching = config.switchSize() > 1 || config.infinitySwitch() || config.multiAttack();
        updateTargets(config);
        aimingTarget = currentOrFirstTarget(config);
        previousBestHit = currentBestHit;
        currentBestHit = null;

        if (aimingTarget != null) {
            currentBestHit = NiloreRotationModel.findBestHit(
                    aimingTarget, RotationManager.INSTANCE.getLastRotation());
            if (currentBestHit != null && currentBestHit.rotation() != null) {
                if (config.style() == Style.onTickRot) {
                    rotation = null;
                    RotationManager.INSTANCE.releaseRotations(this);
                } else {
                    Rot2f from = RotationManager.INSTANCE.getLastRotation();
                    if (from == null) {
                        from = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
                    }
                    Rot2f organic = applyOrganicRotation(from, currentBestHit.rotation(), config, 1.0F);
                    rotation = isFinite(organic) ? organic : currentBestHit.rotation();
                    RotationManager.INSTANCE.setRotationsDirect(this, rotation, Priority.Medium);
                }
            } else {
                rotation = null;
                RotationManager.INSTANCE.releaseRotations(this);
            }
        } else {
            rotation = null;
            RotationManager.INSTANCE.releaseRotations(this);
        }

        if (targetList.isEmpty()) {
            target = null;
            RotationManager.INSTANCE.releaseRotations(this);
            return;
        }
        if (targetIndex >= targetList.size()) {
            targetIndex = 0;
        }
        if (targetList.size() > 1
                && (attackTimes >= config.switchDelay()
                || currentBestHit != null && currentBestHit.distance() > 3.0D)) {
            attackTimes = 0;
            for (int i = 0; i < targetList.size(); i++) {
                targetIndex = (targetIndex + 1) % targetList.size();
                NiloreRotationModel.BestHit next = NiloreRotationModel.findBestHit(
                        targetList.get(targetIndex), RotationManager.INSTANCE.getLastRotation());
                if (next != null && next.distance() < 3.0D) {
                    break;
                }
            }
        }
        if (!switching || targetIndex >= targetList.size()) {
            targetIndex = 0;
        }
        target = targetList.get(targetIndex);

        int reduction = Velocity.INSTANCE.getNiloreAttackReduction();
        double minimum = Math.max(0.0D, config.minAps() - reduction);
        double maximum = Math.max(minimum, config.maxAps() - reduction);
        attacks += randomDouble(minimum, maximum) / 20.0D;
        runPendingAttacks(config);
    }

    public void render(PoseStack poseStack, Config config) {
        LivingEntity entity = aimingTarget;
        if (entity == null || config.targetEsp() == TargetEsp.None) {
            return;
        }
        switch (config.targetEsp()) {
            case Spiral -> CircleESP.render(
                    poseStack, entity, 0.75F,
                    new Color(0, 255, 255, 150), new Color(255, 255, 255, 220), 1.0F);
            case Box -> {
                Color color = entity.hurtTime == 0
                        ? new Color(0, 0, 0, 130)
                        : entity.hurtTime >= 9
                        ? new Color(0, 255, 255, 200)
                        : new Color(255, 0, 0, 200);
                AABB box = entity.getBoundingBox().inflate(0.175D, 0.125D, 0.175D)
                        .expandTowards(0.0D, 0.1D, 0.0D);
                Render3DUtils.drawFilledBox(box, color);
                Render3DUtils.drawOutlineBox(poseStack, box, color, 1.5F);
            }
            case Tab -> {
                Color color = entity.hurtTime == 0
                        ? new Color(0, 0, 0, 130)
                        : entity.hurtTime == 3
                        ? new Color(255, 255, 255, 200)
                        : new Color(255, 0, 0, 200);
                AABB box = entity.getBoundingBox();
                double minY = entity.getY() + entity.getEyeHeight() + 0.11D;
                double maxY = Math.max(minY + 0.02D, box.maxY - 0.13D);
                Render3DUtils.drawFilledBox(
                        new AABB(box.minX, minY, box.minZ, box.maxX, maxY, box.maxZ), color);
            }
            case NurikZapen -> CircleESP.render(
                    poseStack, entity, 1.05F,
                    new Color(255, 255, 255, 180), new Color(0, 255, 255, 220), 1.25F);
            default -> {
            }
        }
    }

    private void runPendingAttacks(Config config) {
        if (mc.player == null || mc.screen != null || mc.player.isUsingItem()) {
            attacks = 0.0D;
            return;
        }
        while (attacks >= 1.0D) {
            boolean attacked = doAttack(config);
            if (config.style() == Style.Old && config.fix() && !attacked) {
                break;
            }
            attacks -= 1.0D;
        }
    }

    private boolean doAttack(Config config) {
        if (targetList.isEmpty() || mc.player == null || mc.gameMode == null) {
            return false;
        }
        if (rotation == null && config.style() != Style.onTickRot) {
            return false;
        }

        HitResult hitResult;
        if ((config.style() == Style.Old || config.style() == Style.onTickRot)
                && config.overrideRaycast()
                && currentBestHit != null
                && currentBestHit.rotation() != null) {
            hitResult = RaytraceUtils.raytrace(
                    currentBestHit.rotation(), Math.max(6.0D, mc.player.entityInteractionRange()));
            if (!(hitResult instanceof EntityHitResult)) {
                return false;
            }
        } else {
            hitResult = mc.hitResult;
        }
        if (hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player player
                && AntiBot.INSTANCE.isBot(player)) {
            return false;
        }

        if (config.multiAttack()) {
            int attacked = 0;
            Rot2f aim = currentBestHit != null && currentBestHit.rotation() != null
                    ? currentBestHit.rotation()
                    : rotation;
            if (aim == null) {
                aim = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            }
            for (LivingEntity entity : targetList) {
                if (hitDistance(entity, mc.player.getEyePosition(), aim) >= 3.0D) {
                    continue;
                }
                if (attackEntity(entity, config)) {
                    attacked++;
                }
                if (attacked >= 2) {
                    break;
                }
            }
            return attacked > 0;
        }
        if (hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living) {
            return attackEntity(living, config);
        }
        return target != null && targetList.contains(target) && attackEntity(target, config);
    }

    private boolean attackEntity(LivingEntity entity, Config config) {
        if (!isValidTarget(entity, config) || mc.player == null || mc.gameMode == null) {
            return false;
        }
        if (config.style() == Style.onTickRot) {
            return attackOnTickRotation(entity, config);
        }

        float originalYaw = mc.player.getYRot();
        float originalPitch = mc.player.getXRot();
        Rot2f managed = rotation != null ? rotation : RotationManager.INSTANCE.getRotation();
        if (managed != null) {
            mc.player.setYRot(managed.getYaw());
            mc.player.setXRot(managed.getPitch());
        }
        attackTimes++;
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (config.moreParticles()) {
            mc.player.magicCrit(entity);
            mc.player.crit(entity);
        }
        mc.player.setYRot(originalYaw);
        mc.player.setXRot(originalPitch);
        return true;
    }

    private boolean attackOnTickRotation(LivingEntity entity, Config config) {
        if (mc.getConnection() == null || currentBestHit == null || currentBestHit.rotation() == null) {
            return false;
        }
        float originalYaw = mc.player.getYRot();
        float originalPitch = mc.player.getXRot();
        Rot2f aimed = currentBestHit.rotation();
        float first = rotationJitter();
        float second = rotationJitter();
        float third = rotationJitter();
        sendPositionRotation(aimed.getYaw() + first, aimed.getPitch() + first);
        sendPositionRotation(aimed.getYaw() + second, aimed.getPitch() + second);
        attackTimes++;
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (config.moreParticles()) {
            mc.player.magicCrit(entity);
            mc.player.crit(entity);
        }
        sendPositionRotation(originalYaw + third, originalPitch + third);
        return true;
    }

    private void sendPositionRotation(float yaw, float pitch) {
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
    }

    private void updateTargets(Config config) {
        targetList.clear();
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && isValidAttack(living, config)) {
                targetList.add(living);
            }
        }
        switch (config.priority()) {
            case Distance -> targetList.sort(Comparator.comparingDouble(
                    entity -> entity.distanceTo(mc.player)));
            case FoV -> targetList.sort(Comparator.comparingDouble(entity -> {
                float baseYaw = rotation != null ? rotation.getYaw() : mc.player.getYRot();
                return NiloreRotationModel.rotationDifference(
                        baseYaw, RotationUtils.getRotationsToEntity(entity).getYaw());
            }));
            case Health -> targetList.sort(Comparator.comparingDouble(LivingEntity::getHealth));
            case None -> {
            }
        }
        if (config.preferBaby() && targetList.stream().anyMatch(LivingEntity::isBaby)) {
            targetList.removeIf(entity -> !entity.isBaby());
        }
        if (!config.infinitySwitch() && targetList.size() > config.switchSize()) {
            targetList.subList(config.switchSize(), targetList.size()).clear();
        }
    }

    private LivingEntity currentOrFirstTarget(Config config) {
        if (target != null && isValidTarget(target, config)) {
            return target;
        }
        return targetList.isEmpty() ? null : targetList.getFirst();
    }

    private boolean isValidAttack(LivingEntity entity, Config config) {
        if (!isValidTarget(entity, config) || entity.hurtTime > config.hurtTime()) {
            return false;
        }
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 closest = NiloreRotationModel.closestPoint(eyes, entity.getBoundingBox());
        double distance = closest.distanceTo(eyes);
        if (distance > config.aimRange()) {
            if (distance > 5.0D || !config.prediction()
                    || predictedDistance(entity, config) >= config.aimRange()) {
                return false;
            }
        }
        if ((config.style() == Style.New || config.style() == Style.onTickRot)
                && !(config.throughWalls() && distance <= config.throughWallsRange())) {
            HitResult wall = mc.level.clip(new ClipContext(
                    eyes, closest, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (wall.getType() == HitResult.Type.BLOCK) {
                return false;
            }
        }
        return RotationUtils.isInFov(entity, config.fov());
    }

    private boolean isValidTarget(LivingEntity entity, Config config) {
        if (!ready() || entity == mc.player || entity instanceof ArmorStand
                || !entity.isAlive() || entity.isDeadOrDying() || entity.getHealth() <= 0.0F) {
            return false;
        }
        if (entity instanceof Player player && AntiBot.INSTANCE.isBot(player)) {
            return false;
        }
        if (entity.isInvisible() && !config.attackInvisible()) {
            return false;
        }
        if (entity instanceof Player player) {
            if (config.test() && player.getY() >= mc.player.getY() + 0.05D) {
                return true;
            }
            if (!config.attackPlayer() || player.isSpectator()
                    || player.getBbWidth() < 0.5F || player.isSleeping()
                    || FriendManager.INSTANCE.isFriend(player)) {
                return false;
            }
            if (config.teamCheck() && mc.player.isAlliedTo(player)) {
                return false;
            }
        }
        if (entity instanceof Mob && !config.attackMobs()) {
            return false;
        }
        if ((entity instanceof Animal || entity instanceof Squid) && !config.attackAnimals()) {
            return false;
        }
        return !(entity instanceof Villager) || config.attackAnimals();
    }

    private double predictedDistance(LivingEntity entity, Config config) {
        double selfDelay = latency(mc.player);
        double enemyDelay = entity instanceof Player player ? latency(player) : 0.0D;
        double ticks = 2.0D
                + Math.min(selfDelay / 50.0D, config.selfDelayTicks())
                + Math.min(enemyDelay / 50.0D, config.enemyDelayTicks());
        double playerX = mc.player.getX() + (mc.player.getX() - mc.player.xOld) * ticks;
        double playerZ = mc.player.getZ() + (mc.player.getZ() - mc.player.zOld) * ticks;
        double enemyX = entity.getX() + (entity.getX() - entity.xOld) * ticks;
        double enemyZ = entity.getZ() + (entity.getZ() - entity.zOld) * ticks;
        return Math.hypot(enemyX - playerX, enemyZ - playerZ);
    }

    private double latency(Player player) {
        if (mc.getConnection() == null || player == null) {
            return 0.0D;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
        return info == null ? 0.0D : info.getLatency();
    }

    private Rot2f applyOrganicRotation(Rot2f from, Rot2f to, Config config, float timeDelta) {
        float rawYaw = Mth.wrapDegrees(to.getYaw() - from.getYaw());
        float rawPitch = to.getPitch() - from.getPitch();
        double speed = config.rotationSpeed();
        double drift = config.rotationDrift();
        double jitter = config.rotationJitter();
        if (speed <= 0.0D) {
            return to;
        }

        float yawDelta = rawYaw * timeDelta;
        float pitchDelta = rawPitch * timeDelta;
        double distance = Math.hypot(yawDelta, pitchDelta);
        if (distance < drift) {
            return new Rot2f(from.getYaw() + yawDelta, from.getPitch() + pitchDelta);
        }
        if (distance > 0.0D) {
            double maxYaw = speed * Math.abs(yawDelta) / distance * timeDelta;
            double maxPitch = speed * Math.abs(pitchDelta) / distance * timeDelta;
            yawDelta = Mth.clamp(yawDelta, (float) -maxYaw, (float) maxYaw);
            pitchDelta = Mth.clamp(pitchDelta, (float) -maxPitch, (float) maxPitch);
        }

        organicTimeAccumulator += timeDelta;
        double yawWave = Math.sin(organicTimeAccumulator * yawFrequencyOne + yawPhaseOne)
                + (organicRandom.nextDouble() * 0.1D + 0.45D)
                * Math.sin(organicTimeAccumulator * yawFrequencyTwo + yawPhaseTwo);
        double pitchWave = Math.sin(organicTimeAccumulator * pitchFrequencyOne + pitchPhaseOne)
                + (organicRandom.nextDouble() * 0.1D + 0.45D)
                * Math.sin(organicTimeAccumulator * pitchFrequencyTwo + pitchPhaseTwo);
        float moveYaw = yawDelta + (float) (yawWave * drift * timeDelta)
                + (float) ((organicRandom.nextDouble() * 2.0D - 1.0D) * jitter * timeDelta);
        float movePitch = pitchDelta + (float) (pitchWave * drift * timeDelta)
                + (float) ((organicRandom.nextDouble() * 2.0D - 1.0D) * jitter * timeDelta);
        Rot2f moved = new Rot2f(
                from.getYaw() + moveYaw,
                Mth.clamp(from.getPitch() + movePitch, -90.0F, 90.0F));
        return NiloreRotationModel.patchConstantRotation(moved, from);
    }

    private void resetOrganicModel() {
        organicRandom = new Random(System.nanoTime());
        organicTimeAccumulator = 0.0D;
        yawFrequencyOne = organicRandom.nextDouble() * 0.3D + 0.1D;
        yawFrequencyTwo = organicRandom.nextDouble() * 0.5D + 0.5D;
        pitchFrequencyOne = organicRandom.nextDouble() * 0.3D + 0.1D;
        pitchFrequencyTwo = organicRandom.nextDouble() * 0.5D + 0.5D;
        yawPhaseOne = organicRandom.nextDouble() * Math.PI * 2.0D;
        yawPhaseTwo = organicRandom.nextDouble() * Math.PI * 2.0D;
        pitchPhaseOne = organicRandom.nextDouble() * Math.PI * 2.0D;
        pitchPhaseTwo = organicRandom.nextDouble() * Math.PI * 2.0D;
    }

    private boolean shouldSuspend(Config config) {
        return mc.screen instanceof AbstractContainerScreen<?>
                || Scaffold.INSTANCE.isEnabled() && config.style() != Style.onTickRot;
    }

    private void resetTransientState() {
        target = null;
        aimingTarget = null;
        currentBestHit = null;
        previousBestHit = null;
        rotation = null;
        targetList.clear();
        attacks = 0.0D;
    }

    private boolean ready() {
        return mc.player != null && mc.level != null && mc.gameMode != null;
    }

    private static boolean isFinite(Rot2f value) {
        return value != null && Float.isFinite(value.getYaw()) && Float.isFinite(value.getPitch());
    }

    private static double hitDistance(LivingEntity entity, Vec3 eyes, Rot2f rotation) {
        Vec3 direction = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 end = eyes.add(direction.scale(6.0D));
        return entity.getBoundingBox().clip(eyes, end)
                .map(point -> point.distanceTo(eyes))
                .orElse(1000.0D);
    }

    private static float rotationJitter() {
        float value = (float) (Math.random() * 0.04D + 0.03D);
        return Math.random() > 0.5D ? value : -value;
    }

    private static double randomDouble(double first, double second) {
        double min = Math.min(first, second);
        double max = Math.max(first, second);
        return min == max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }
}
