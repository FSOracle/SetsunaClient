package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.SendPositionEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.util.client.KeybindUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

/** Kawasaki AutoMLG port for the 26.1.2 client runtime. */
public final class AutoMLG extends Module {

    public static final AutoMLG INSTANCE = new AutoMLG();

    private static final double INTERACTION_RANGE = 4.5D;
    private static final int MAX_PREDICTION_TICKS = 20;

    private final DoubleSetting fallDistance = add(new DoubleSetting(
            "FallDistance", 3.0D, 1.0D, 10.0D, 0.1D));
    private final IntSetting predictTicks = add(new IntSetting(
            "PredictTicks", 2, 1, 5, 1));
    private final BooleanSetting solidCheck = add(new BooleanSetting("SolidCheck", true));
    private final BooleanSetting onlyMainHand = add(new BooleanSetting("OnlyMainHand", false));
    private final BooleanSetting retrieve = add(new BooleanSetting("Retrieve", true));
    private final DoubleSetting retrieveWindow = add(new DoubleSetting(
            "RetrieveWindow", 2.5D, 0.5D, 8.0D, 0.1D).visibleWhen(retrieve::get));
    private final BooleanSetting renderTarget = add(new BooleanSetting("RenderTarget", true));
    private final ColorSetting targetColor = add(new ColorSetting(
            "TargetColor", new Color(47, 203, 255, 220)).visibleWhen(renderTarget::get));
    private final KeybindSetting extinguishKey = add(new KeybindSetting(
            "Extinguish Key", KeybindSetting.NONE));

    private float accumulatedFallDistance;
    private double lastY;
    private Integer previousSlot;
    private boolean handledFall;

    private boolean retrieving;
    private int retrieveDelayTicks;
    private int retrieveAttempts;
    private Integer retrieveBucketSlot;
    private boolean retrieveUseSent;
    private int retrieveConfirmationTicks;
    private BlockPos placedWaterPos;
    private long retrieveDeadline;

    private int interactionCooldown;
    private boolean rotationActive;
    private Rot2f serverRotation;
    private boolean releaseRotationAfterMovement;

    private boolean extinguishLatched;
    private int placementAttempts;
    private boolean awaitingPlacement;
    private int placementConfirmationTicks;
    private boolean retrieveAfterPlacement;
    private BlockPos placementCandidate;
    private BlockPos upperPlacementCandidate;
    private Integer placementBucketSlot;

    private AutoMLG() {
        super("Auto MLG", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetState();
        accumulatedFallDistance = 0.0F;
        lastY = mc.player != null ? mc.player.getY() : 0.0D;
    }

    @Override
    protected void onDisable() {
        releaseRotation();
        resetState();
        accumulatedFallDistance = 0.0F;
    }

    public boolean blocksOtherActions() {
        return interactionCooldown > 0
                || awaitingPlacement
                || retrieving
                || placedWaterPos != null
                || rotationActive;
    }

    @Listen
    private void onClientTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        if (mc.player.isFallFlying()) {
            releaseRotation();
            return;
        }

        if (isHoldingMace()) {
            releaseRotation();
            resetState();
            accumulatedFallDistance = 0.0F;
            lastY = mc.player.getY();
            return;
        }

        updateAccumulatedFallDistance();
        tickCooldowns();

        if (tickPlacementConfirmation()) {
            return;
        }

        if (mc.player.onGround() || accumulatedFallDistance <= 0.0F) {
            handledFall = false;
            placementAttempts = 0;
            if (!retrieving) {
                restorePreviousSlot();
                releaseRotation();
            }
        }

        if (mc.player.isUsingItem()) {
            releaseRotation();
            return;
        }

        if (tickRetrieval()) {
            return;
        }

        if (tickExtinguish()) {
            return;
        }

        if (handledFall || accumulatedFallDistance < fallDistance.get()) {
            return;
        }

        int waterBucketSlot = findPlacementBucketSlot();
        LandingPrediction prediction = predictLanding(MAX_PREDICTION_TICKS);
        int allowedTicks = predictTicks.get() + pingCompensationTicks();
        if (waterBucketSlot < 0 || prediction == null || prediction.ticks() > allowedTicks) {
            return;
        }

        BlockHitResult target = findReachableTopHit(prediction.hit(), INTERACTION_RANGE);
        if (target == null || target.getType() == HitResult.Type.MISS) {
            return;
        }
        if (solidCheck.get() && !isSolidSupport(target.getBlockPos())) {
            return;
        }

        placeWater(waterBucketSlot, target, retrieve.get());
    }

    @Listen
    private void onClientTickPost(TickEvent.Post event) {
        if (releaseRotationAfterMovement) {
            releaseRotation();
        }
    }

    @Listen(priority = -1000)
    private void onSendPosition(SendPositionEvent event) {
        if (rotationActive && serverRotation != null) {
            event.setYaw(serverRotation.getYaw());
            event.setPitch(serverRotation.getPitch());
        }
        if (releaseRotationAfterMovement) {
            releaseRotation();
        }
    }

    @Listen
    private void onRenderTarget(Render3DEvent event) {
        if (!renderTarget.get()
                || mc.player == null
                || mc.level == null
                || findPlacementBucketSlot() < 0
                || handledFall
                || awaitingPlacement
                || retrieving
                || accumulatedFallDistance < fallDistance.get()
                || mc.player.isFallFlying()
                || isHoldingMace()
                || mc.player.isUsingItem()) {
            return;
        }

        LandingPrediction prediction = predictLanding(MAX_PREDICTION_TICKS);
        int allowedTicks = predictTicks.get() + pingCompensationTicks();
        if (prediction == null
                || prediction.ticks() > allowedTicks
                || prediction.hit().getDirection() != Direction.UP) {
            return;
        }

        BlockHitResult target = findReachableTopHit(prediction.hit(), INTERACTION_RANGE);
        if (target == null || target.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos support = target.getBlockPos();
        if (solidCheck.get() && !isSolidSupport(support)) {
            return;
        }

        double y = target.getLocation().y + 0.006D;
        AABB box = new AABB(
                support.getX() + 0.08D, y, support.getZ() + 0.08D,
                support.getX() + 0.92D, y + 0.035D, support.getZ() + 0.92D);
        double pulse = (Math.sin(System.currentTimeMillis() / 145.0D) + 1.0D) * 0.5D;
        Color base = targetColor.get();
        int configuredAlpha = Math.max(1, base.getAlpha());
        int outlineAlpha = (int) ((185.0D + pulse * 55.0D) * configuredAlpha / 255.0D);
        int fillAlpha = Math.max(12, outlineAlpha / 4);
        Color outline = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(255, outlineAlpha));
        Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(110, fillAlpha));

        Render3DUtils.drawFilledBox(box, fill);
        Render3DUtils.drawOutlineBox(event.getPoseStack(), box, outline, 2.4F);

        double crossY = y + 0.042D;
        Vec3 first = new Vec3(support.getX() + 0.27D, crossY, support.getZ() + 0.27D);
        Vec3 second = new Vec3(support.getX() + 0.73D, crossY, support.getZ() + 0.73D);
        Vec3 third = new Vec3(support.getX() + 0.73D, crossY, support.getZ() + 0.27D);
        Vec3 fourth = new Vec3(support.getX() + 0.27D, crossY, support.getZ() + 0.73D);
        Render3DUtils.drawLine(event.getPoseStack(), first, second, outline.getRGB(), 2.1F);
        Render3DUtils.drawLine(event.getPoseStack(), third, fourth, outline.getRGB(), 2.1F);
    }

    private void resetState() {
        previousSlot = null;
        handledFall = false;
        retrieving = false;
        retrieveDelayTicks = 0;
        retrieveAttempts = 0;
        retrieveBucketSlot = null;
        retrieveUseSent = false;
        retrieveConfirmationTicks = 0;
        placedWaterPos = null;
        retrieveDeadline = 0L;
        interactionCooldown = 0;
        rotationActive = false;
        serverRotation = null;
        releaseRotationAfterMovement = false;
        extinguishLatched = false;
        placementAttempts = 0;
        clearPlacementConfirmation();
    }

    private void updateAccumulatedFallDistance() {
        if (mc.player.onGround()
                || mc.player.getAbilities().flying
                || mc.player.isInWaterOrRain()
                || mc.player.isInLava()) {
            accumulatedFallDistance = 0.0F;
        } else {
            double deltaY = mc.player.getY() - lastY;
            if (deltaY < 0.0D) {
                accumulatedFallDistance -= (float) deltaY;
            }
        }
        lastY = mc.player.getY();
    }

    private void tickCooldowns() {
        if (interactionCooldown > 0) {
            interactionCooldown--;
        }
    }

    private boolean tickPlacementConfirmation() {
        if (!awaitingPlacement) {
            return false;
        }

        BlockPos confirmed = isWaterSource(placementCandidate)
                ? placementCandidate
                : isWaterSource(upperPlacementCandidate) ? upperPlacementCandidate : null;
        if (confirmed != null) {
            boolean shouldRetrieve = retrieveAfterPlacement;
            Integer bucketSlot = placementBucketSlot;
            clearPlacementConfirmation();
            handledFall = true;

            if (shouldRetrieve) {
                placedWaterPos = confirmed.immutable();
                retrieving = true;
                retrieveDelayTicks = 2;
                retrieveAttempts = 3;
                retrieveBucketSlot = bucketSlot;
                retrieveUseSent = false;
                retrieveConfirmationTicks = 0;
                retrieveDeadline = System.currentTimeMillis()
                        + (long) (retrieveWindow.get() * 1000.0D);
                holdRotation(rotationTo(Vec3.atCenterOf(placedWaterPos)));
            } else {
                restorePreviousSlot();
                scheduleRotationRelease();
            }
            return true;
        }

        if (--placementConfirmationTicks > 0) {
            return true;
        }

        clearPlacementConfirmation();
        handledFall = true;
        restorePreviousSlot();
        scheduleRotationRelease();
        return true;
    }

    private void clearPlacementConfirmation() {
        awaitingPlacement = false;
        placementConfirmationTicks = 0;
        retrieveAfterPlacement = false;
        placementCandidate = null;
        upperPlacementCandidate = null;
        placementBucketSlot = null;
    }

    private boolean tickRetrieval() {
        if (!retrieving) {
            return false;
        }

        if (!withinRetrieveWindow()) {
            finishRetrieval();
            return true;
        }

        if (placedWaterPos != null) {
            holdRotation(rotationTo(Vec3.atCenterOf(placedWaterPos)));
        }

        if (retrieveUseSent) {
            if (retrieveBucketSlot != null
                    && mc.player.getInventory().getItem(retrieveBucketSlot).is(Items.WATER_BUCKET)) {
                finishRetrieval();
                interactionCooldown = Math.max(interactionCooldown, 1);
                return true;
            }
            if (--retrieveConfirmationTicks <= 0) {
                finishRetrieval();
            }
            return true;
        }

        if (retrieveDelayTicks > 0) {
            retrieveDelayTicks--;
            return true;
        }

        if (!mc.player.onGround()
                && !mc.player.isInWater()
                && mc.player.getDeltaMovement().y < -0.01D) {
            return true;
        }

        if (retrieveBucketSlot == null) {
            retrieveBucketSlot = findHotbarSlot(Items.BUCKET);
            if (retrieveBucketSlot < 0) {
                return true;
            }
        }

        if (!mc.player.getInventory().getItem(retrieveBucketSlot).is(Items.BUCKET)
                || !isWaterSource(placedWaterPos)) {
            return true;
        }

        Rot2f rotation = rotationTo(Vec3.atCenterOf(placedWaterPos));
        BlockHitResult hit = raycast(rotation, INTERACTION_RANGE, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(placedWaterPos)) {
            return true;
        }

        holdRotation(rotation);
        selectSlot(retrieveBucketSlot);
        if (useMainHand()) {
            retrieveUseSent = true;
            retrieveConfirmationTicks = Math.max(8, pingCompensationTicks() * 2 + 6);
        } else if (--retrieveAttempts <= 0) {
            finishRetrieval();
        } else {
            retrieveDelayTicks = 1;
        }
        return true;
    }

    private boolean tickExtinguish() {
        boolean pressed = KeybindUtils.isPressed(extinguishKey.get());
        if (!pressed || !mc.player.isOnFire()) {
            extinguishLatched = false;
            return false;
        }
        if (extinguishLatched || retrieving) {
            return true;
        }

        int waterBucketSlot = findHotbarSlot(Items.WATER_BUCKET);
        if (waterBucketSlot < 0) {
            extinguishLatched = true;
            return true;
        }

        BlockHitResult target = findCurrentGroundTarget(INTERACTION_RANGE);
        if (target == null || target.getType() == HitResult.Type.MISS) {
            extinguishLatched = true;
            return true;
        }

        placeWater(waterBucketSlot, target, true);
        extinguishLatched = true;
        return true;
    }

    private void placeWater(int waterBucketSlot, BlockHitResult target, boolean shouldRetrieve) {
        Rot2f rotation = rotationTo(target.getLocation());
        BlockHitResult verified = raycast(rotation, INTERACTION_RANGE, ClipContext.Fluid.NONE);
        if (verified.getType() != HitResult.Type.BLOCK
                || verified.getDirection() != Direction.UP
                || !verified.getBlockPos().equals(target.getBlockPos())) {
            return;
        }

        BlockPos waterPosition = verified.getBlockPos().above();
        BlockState waterState = mc.level.getBlockState(waterPosition);
        if (!waterState.isAir()
                && !waterState.canBeReplaced()
                && !isWaterSource(waterPosition)) {
            return;
        }

        holdRotation(rotation);
        selectSlot(waterBucketSlot);
        placementAttempts++;

        if (!useMainHand()) {
            if (placementAttempts >= 2) {
                handledFall = true;
                restorePreviousSlot();
                scheduleRotationRelease();
            }
            return;
        }

        awaitingPlacement = true;
        placementConfirmationTicks = Math.max(8, pingCompensationTicks() * 2 + 6);
        retrieveAfterPlacement = shouldRetrieve;
        placementCandidate = verified.getBlockPos().immutable();
        upperPlacementCandidate = waterPosition.immutable();
        placementBucketSlot = waterBucketSlot;
    }

    private void finishRetrieval() {
        retrieving = false;
        retrieveBucketSlot = null;
        retrieveUseSent = false;
        retrieveConfirmationTicks = 0;
        retrieveDelayTicks = 0;
        retrieveAttempts = 0;
        placedWaterPos = null;
        retrieveDeadline = 0L;
        restorePreviousSlot();
        releaseRotation();
    }

    private boolean withinRetrieveWindow() {
        return retrieveDeadline > 0L && System.currentTimeMillis() <= retrieveDeadline;
    }

    private LandingPrediction predictLanding(int maxTicks) {
        if (mc.player == null || mc.level == null || mc.player.getDeltaMovement().y >= 0.0D) {
            return null;
        }

        AABB box = mc.player.getBoundingBox();
        double halfWidth = Math.max(0.05D, (box.maxX - box.minX) * 0.5D - 0.03D);
        double halfDepth = Math.max(0.05D, (box.maxZ - box.minZ) * 0.5D - 0.03D);
        double[][] offsets = footprintOffsets(halfWidth, halfDepth);
        Vec3 position = new Vec3(
                (box.minX + box.maxX) * 0.5D,
                box.minY + 0.02D,
                (box.minZ + box.maxZ) * 0.5D);
        Vec3 velocity = mc.player.getDeltaMovement();
        double verticalDrop = 0.0D;

        for (int tick = 1; tick <= maxTicks; tick++) {
            Vec3 nextPosition = position.add(velocity);
            BlockHitResult nearest = null;
            double nearestProgress = Double.POSITIVE_INFINITY;

            for (double[] offset : offsets) {
                Vec3 start = position.add(offset[0], 0.0D, offset[1]);
                Vec3 end = nextPosition.add(offset[0], 0.0D, offset[1]);
                BlockHitResult hit = mc.level.clip(new ClipContext(
                        start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) {
                    continue;
                }

                double segmentLengthSqr = start.distanceToSqr(end);
                double progress = segmentLengthSqr <= 1.0E-8D
                        ? 0.0D
                        : start.distanceToSqr(hit.getLocation()) / segmentLengthSqr;
                if (progress < nearestProgress) {
                    nearest = hit;
                    nearestProgress = progress;
                }
            }

            verticalDrop += Math.max(0.0D, position.y - nextPosition.y);
            if (nearest != null) {
                double correctedDrop = verticalDrop
                        - Math.max(0.0D, nearest.getLocation().y - nextPosition.y);
                return new LandingPrediction(nearest, tick, correctedDrop);
            }

            position = nextPosition;
            velocity = new Vec3(
                    velocity.x * 0.91D,
                    (velocity.y - 0.08D) * 0.98D,
                    velocity.z * 0.91D);
        }
        return null;
    }

    private BlockHitResult findCurrentGroundTarget(double range) {
        LandingPrediction prediction = predictLanding(MAX_PREDICTION_TICKS);
        BlockHitResult seed = prediction == null ? findClosestDownwardHit(range) : prediction.hit();
        if (seed == null || seed.getType() == HitResult.Type.MISS) {
            return null;
        }
        return findReachableTopHit(seed, range);
    }

    private BlockHitResult findClosestDownwardHit(double range) {
        BlockHitResult closest = null;
        double closestDrop = Double.POSITIVE_INFINITY;
        for (Vec3 start : footprintPoints()) {
            Vec3 end = start.add(0.0D, -range, 0.0D);
            BlockHitResult hit = mc.level.clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            double drop = start.y - hit.getLocation().y;
            if (drop >= 0.0D && drop < closestDrop) {
                closest = hit;
                closestDrop = drop;
            }
        }
        return closest;
    }

    private BlockHitResult findReachableTopHit(BlockHitResult seed, double range) {
        BlockPos support = seed.getBlockPos();
        Vec3 eye = mc.player.getEyePosition();
        BlockHitResult best = null;
        double bestDistanceSqr = Double.POSITIVE_INFINITY;

        for (Vec3 point : targetPoints(support, seed.getLocation())) {
            if (eye.distanceToSqr(point) > range * range) {
                continue;
            }
            Rot2f rotation = rotationTo(point);
            BlockHitResult hit = raycast(rotation, range, ClipContext.Fluid.NONE);
            if (hit.getType() != HitResult.Type.BLOCK
                    || hit.getDirection() != Direction.UP
                    || !hit.getBlockPos().equals(support)) {
                continue;
            }
            double distanceSqr = eye.distanceToSqr(hit.getLocation());
            if (distanceSqr < bestDistanceSqr) {
                best = hit;
                bestDistanceSqr = distanceSqr;
            }
        }
        return best;
    }

    private Vec3[] targetPoints(BlockPos support, Vec3 hitPosition) {
        double y = hitPosition.y + 0.001D;
        double minX = support.getX() + 0.12D;
        double maxX = support.getX() + 0.88D;
        double minZ = support.getZ() + 0.12D;
        double maxZ = support.getZ() + 0.88D;
        double centerX = support.getX() + 0.5D;
        double centerZ = support.getZ() + 0.5D;
        double clampedX = Mth.clamp(hitPosition.x, minX, maxX);
        double clampedZ = Mth.clamp(hitPosition.z, minZ, maxZ);
        return new Vec3[]{
                new Vec3(clampedX, y, clampedZ),
                new Vec3(centerX, y, centerZ),
                new Vec3(minX, y, minZ),
                new Vec3(minX, y, maxZ),
                new Vec3(maxX, y, minZ),
                new Vec3(maxX, y, maxZ),
                new Vec3(centerX, y, minZ),
                new Vec3(centerX, y, maxZ),
                new Vec3(minX, y, centerZ),
                new Vec3(maxX, y, centerZ)
        };
    }

    private Vec3[] footprintPoints() {
        AABB box = mc.player.getBoundingBox();
        double y = box.minY + 0.02D;
        double minX = box.minX + 0.03D;
        double maxX = box.maxX - 0.03D;
        double minZ = box.minZ + 0.03D;
        double maxZ = box.maxZ - 0.03D;
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        return new Vec3[]{
                new Vec3(centerX, y, centerZ),
                new Vec3(minX, y, minZ),
                new Vec3(minX, y, maxZ),
                new Vec3(maxX, y, minZ),
                new Vec3(maxX, y, maxZ),
                new Vec3(centerX, y, minZ),
                new Vec3(centerX, y, maxZ),
                new Vec3(minX, y, centerZ),
                new Vec3(maxX, y, centerZ)
        };
    }

    private static double[][] footprintOffsets(double halfWidth, double halfDepth) {
        return new double[][]{
                {0.0D, 0.0D},
                {-halfWidth, -halfDepth},
                {-halfWidth, halfDepth},
                {halfWidth, -halfDepth},
                {halfWidth, halfDepth},
                {0.0D, -halfDepth},
                {0.0D, halfDepth},
                {-halfWidth, 0.0D},
                {halfWidth, 0.0D}
        };
    }

    private BlockHitResult raycast(Rot2f rotation, double range, ClipContext.Fluid fluidMode) {
        Vec3 start = mc.player.getEyePosition();
        Vec3 direction = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        return mc.level.clip(new ClipContext(
                start,
                start.add(direction.scale(range)),
                ClipContext.Block.OUTLINE,
                fluidMode,
                mc.player));
    }

    private Rot2f rotationTo(Vec3 target) {
        Rot2f raw = RotationUtils.calculate(mc.player.getEyePosition(), target);
        return new Rot2f(Mth.wrapDegrees(raw.getYaw()), Mth.wrapDegrees(raw.getPitch()));
    }

    private void holdRotation(Rot2f target) {
        if (target == null || mc.player == null) {
            return;
        }

        Rot2f last = RotationManager.INSTANCE.getLastRotation();
        float baseYaw = last != null && Float.isFinite(last.getYaw())
                ? last.getYaw()
                : mc.player.getYRot();
        float yaw = baseYaw + Mth.wrapDegrees(target.getYaw() - baseYaw);
        float pitch = Mth.clamp(target.getPitch(), -90.0F, 90.0F);
        serverRotation = new Rot2f(yaw, pitch);

        if (!RotationManager.INSTANCE.claimSilentRotation(this, serverRotation, Priority.Highest)) {
            RotationManager.INSTANCE.setActive(false);
            RotationManager.INSTANCE.claimSilentRotation(this, serverRotation, Priority.Highest);
        }
        rotationActive = true;
        releaseRotationAfterMovement = false;
    }

    private boolean useMainHand() {
        if (mc.gameMode == null || mc.player == null || serverRotation == null) {
            return false;
        }

        float oldYaw = mc.player.getYRot();
        float oldPitch = mc.player.getXRot();
        float oldHeadYaw = mc.player.yHeadRot;
        float oldBodyYaw = mc.player.yBodyRot;
        InteractionResult result;
        try {
            mc.player.setYRot(serverRotation.getYaw());
            mc.player.setXRot(serverRotation.getPitch());
            result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        } finally {
            mc.player.setYRot(oldYaw);
            mc.player.setXRot(oldPitch);
            mc.player.yHeadRot = oldHeadYaw;
            mc.player.yBodyRot = oldBodyYaw;
        }

        if (!result.consumesAction()) {
            return false;
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private void selectSlot(int slot) {
        if (previousSlot == null) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        }
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private void restorePreviousSlot() {
        if (previousSlot == null || mc.player == null) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = null;
    }

    private void scheduleRotationRelease() {
        releaseRotationAfterMovement = true;
    }

    private void releaseRotation() {
        releaseRotationAfterMovement = false;
        if (!rotationActive) {
            return;
        }
        RotationManager.INSTANCE.releaseSilentRotation(this);
        rotationActive = false;
        serverRotation = null;
    }

    private int findPlacementBucketSlot() {
        if (mc.player == null) {
            return -1;
        }
        if (onlyMainHand.get()) {
            int selected = mc.player.getInventory().getSelectedSlot();
            return mc.player.getInventory().getItem(selected).is(Items.WATER_BUCKET) ? selected : -1;
        }
        return findHotbarSlot(Items.WATER_BUCKET);
    }

    private int findHotbarSlot(Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    private int pingCompensationTicks() {
        if (mc.player == null || mc.getConnection() == null) {
            return 0;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info == null) {
            return 0;
        }
        int latency = Math.max(0, info.getLatency());
        return Math.min(3, (latency + 99) / 100);
    }

    private boolean isSolidSupport(BlockPos position) {
        BlockState state = mc.level.getBlockState(position);
        return !state.getCollisionShape(mc.level, position).isEmpty()
                && state.getMenuProvider(mc.level, position) == null;
    }

    private boolean isWaterSource(BlockPos position) {
        return position != null
                && mc.level.getFluidState(position).isSourceOfType(Fluids.WATER);
    }

    private boolean isHoldingMace() {
        return mc.player.getMainHandItem().is(Items.MACE)
                || mc.player.getOffhandItem().is(Items.MACE);
    }

    private record LandingPrediction(BlockHitResult hit, int ticks, double verticalDrop) {
    }
}
