package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.combat.silentaura.SilentAuraAimJitter;
import com.setsuna.module.modules.combat.silentaura.VapeClickTiming;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.client.AttackKeyController;
import com.setsuna.util.client.KeybindUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.rotation.RaytraceUtils;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;

/** Minecraft 26.1.2 port of Vape 4.21's SilentAura module. */
public final class SilentAura extends Module {

    private enum TargetMode {
        Distance,
        Yaw,
        Armor,
        Threat,
        Health
    }

    private enum TargetArea {
        Center,
        Closest
    }

    private enum RenderType {
        Ring,
        Box
    }

    public static final SilentAura INSTANCE = new SilentAura();

    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", false));
    private final BooleanSetting peaceful = add(new BooleanSetting("Peaceful", false));
    private final BooleanSetting neutral = add(new BooleanSetting("Neutral", false));
    private final BooleanSetting ignoreNaked = add(new BooleanSetting("Ignore Naked", false));
    private final BooleanSetting ignoreInvisible = add(new BooleanSetting("Ignore Invisible", false));
    private final BooleanSetting ignoreBehindWalls = add(new BooleanSetting("Ignore Behind Walls", false));

    private final DoubleSetting aimSpeed = add(new DoubleSetting("Aim Speed", 7.0D, 1.0D, 10.0D, 0.1D));
    private final IntSetting minAps = add(new IntSetting("Min APS", 6, 1, 20, 1));
    private final IntSetting maxAps = add(new IntSetting("Max APS", 13, 1, 20, 1));
    private final DoubleSetting extraSwingDistance = add(new DoubleSetting(
            "Extra Swing Distance", 1.0D, 0.0D, 3.0D, 0.1D));
    private final DoubleSetting maxAngle = add(new DoubleSetting("Max Angle", 120.0D, 1.0D, 360.0D, 5.0D));
    private final EnumSetting<TargetMode> targetMode = add(new EnumSetting<>("Target Mode", TargetMode.Distance));
    private final EnumSetting<TargetArea> targetArea = add(new EnumSetting<>("Target Area", TargetArea.Center));

    private final BooleanSetting disableOnDeath = add(new BooleanSetting("Disable On Death", false));
    private final BooleanSetting breakBlocks = add(new BooleanSetting("Break Blocks", false));
    private final IntSetting breakDelayMin = add(new IntSetting("Break Delay Min", 0, 0, 2_000, 1)
            .visibleWhen(breakBlocks::get));
    private final IntSetting breakDelayMax = add(new IntSetting("Break Delay Max", 10, 0, 2_000, 1)
            .visibleWhen(breakBlocks::get));
    private final BooleanSetting breakBlocksWhitelist = add(new BooleanSetting("Break Blocks Whitelist", false)
            .visibleWhen(breakBlocks::get));
    private final BooleanSetting breakPickaxes = add(breakItemSetting("Break Pickaxes", true));
    private final BooleanSetting breakShovels = add(breakItemSetting("Break Shovels", true));
    private final BooleanSetting breakAxes = add(breakItemSetting("Break Axes", false));
    private final BooleanSetting breakHoes = add(breakItemSetting("Break Hoes", false));
    private final BooleanSetting requireMouseDown = add(new BooleanSetting("Require Mouse Down", false));
    private final BooleanSetting showTarget = add(new BooleanSetting("Show Target", false));
    private final ColorSetting targetColor = add(new ColorSetting(
            "Target Color", new Color(255, 200, 112, 50), true).visibleWhen(showTarget::get));
    private final ColorSetting attackColor = add(new ColorSetting(
            "Attack Color", new Color(255, 0, 0, 100), true).visibleWhen(showTarget::get));
    private final EnumSetting<RenderType> renderType = add(new EnumSetting<>("Render Type", RenderType.Ring)
            .visibleWhen(showTarget::get));
    private final BooleanSetting perfectSwing = add(new BooleanSetting("Perfect Swing", false));
    private final BooleanSetting switchTargets = add(new BooleanSetting("Switch", false));

    private final BooleanSetting limitToItems = add(new BooleanSetting("Limit To Items", false));
    private final BooleanSetting swords = add(itemSetting("Swords"));
    private final BooleanSetting axes = add(itemSetting("Axes"));
    private final BooleanSetting maces = add(itemSetting("Maces"));
    private final BooleanSetting spears = add(itemSetting("Spears"));
    private final BooleanSetting pickaxes = add(itemSetting("Pickaxes"));
    private final BooleanSetting shovels = add(itemSetting("Shovels"));
    private final BooleanSetting hoes = add(itemSetting("Hoes"));
    private final BooleanSetting otherItems = add(itemSetting("Other Items"));

    private final SilentAuraAimJitter pitchJitter = new SilentAuraAimJitter(-0.3D, 0.25D);
    private final SilentAuraAimJitter xJitter = new SilentAuraAimJitter(-0.15D, 0.15D);
    private final SilentAuraAimJitter zJitter = new SilentAuraAimJitter(-0.15D, 0.15D);
    private final VapeClickTiming clickTiming = new VapeClickTiming(System.nanoTime());

    private LivingEntity target;
    private boolean readyToAttack;
    private boolean onTarget;
    private boolean breakingBlocks;
    private boolean attackPending;
    private long nextBreakCheckAt;
    private long nextClickAt;

    private float pitchIntegral;
    private float yawIntegral;
    private float pitchProportionalScale = 1.0F;
    private float pitchIntegralScale = 1.0F;
    private float yawProportionalScale = 1.0F;
    private float yawIntegralScale = 1.0F;

    private SilentAura() {
        super("SilentAura", Category.COMBAT);
        minAps.andVisibleWhen(() -> !perfectSwing.get());
        maxAps.andVisibleWhen(() -> !perfectSwing.get());
    }

    private BooleanSetting itemSetting(String name) {
        return new BooleanSetting(name, false).visibleWhen(limitToItems::get);
    }

    private BooleanSetting breakItemSetting(String name, boolean defaultValue) {
        return new BooleanSetting(name, defaultValue)
                .visibleWhen(() -> breakBlocks.get() && breakBlocksWhitelist.get());
    }

    @Listen(priority = Priority.LOWEST)
    private void onTick(TickEvent.Pre event) {
        breakingBlocks = handleBreakBlocks();
        if (attackPending) {
            AttackKeyController.releaseAttackKey(this);
            attackPending = false;
        }

        if (noPlayer()) {
            resetTargeting();
            return;
        }
        if (disableOnDeath.get() && (!mc.player.isAlive() || mc.player.isDeadOrDying()
                || mc.player.getHealth() <= 0.0F)) {
            setEnabled(false);
            return;
        }
        if (breakingBlocks || mc.screen != null || mc.isPaused() || !mc.isWindowActive()
                || requireMouseDown.get() && !KeybindUtils.isPressed(mc.options.keyAttack)) {
            resetTargeting();
            return;
        }

        updateTarget();
        updateAim();
        onTarget = isLookingAtTarget();
        if (perfectSwing.get()) {
            if (canAttack()) {
                attackPending = AttackKeyController.requestSyntheticAttack(this);
            }
        } else {
            runClickCycle();
        }
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (!showTarget.get() || target == null || mc.screen != null) {
            return;
        }
        Color color = isLookingAtTarget() ? attackColor.get() : targetColor.get();
        if (renderType.is(RenderType.Ring)) {
            double radius = target instanceof Player ? 0.7D : Math.max(0.1D, target.getBbWidth());
            Render3DUtils.drawCylinder(event.getPoseStack(), target.position(), radius,
                    target.getBbHeight(), color.getRGB(), 2.0F, 50);
        } else {
            AABB box = target.getBoundingBox();
            Render3DUtils.drawFilledBox(box, color);
            Render3DUtils.drawOutlineBox(event.getPoseStack(), box, color, 2.0F);
        }
    }

    private void updateTarget() {
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && isValidTarget(living)) {
                candidates.add(living);
            }
        }
        Comparator<LivingEntity> comparator = targetComparator();
        if (switchTargets.get()) {
            comparator = Comparator.comparingInt((LivingEntity living) -> living.hurtTime)
                    .thenComparing(comparator);
        }
        candidates.sort(comparator);
        if (candidates.isEmpty()) {
            resetTargeting();
            return;
        }

        LivingEntity selected = candidates.getFirst();
        if (target != selected) {
            AttackKeyController.releaseAttackKey(this);
            attackPending = false;
            randomizeGainFactors();
        }
        target = selected;
        readyToAttack = false;
        if (RotationManager.INSTANCE.ownsSilentRotation(this)) {
            if (perfectSwing.get()) {
                readyToAttack = isLookingAtTarget();
            } else if (horizontalAngleToTarget(target) < 3.0D && isInRange(target)) {
                readyToAttack = true;
            }
        }
    }

    private Comparator<LivingEntity> targetComparator() {
        return switch (targetMode.get()) {
            case Distance -> Comparator.comparingDouble(mc.player::distanceTo);
            case Yaw -> Comparator.comparingDouble(this::cameraYawDifference);
            case Armor -> this::compareArmor;
            case Threat -> this::compareThreat;
            case Health -> Comparator.comparingDouble(LivingEntity::getHealth);
        };
    }

    private int compareArmor(LivingEntity first, LivingEntity second) {
        return comparePlayersOrDistance(first, second, Player::getArmorValue);
    }

    private int compareThreat(LivingEntity first, LivingEntity second) {
        return comparePlayersOrDistance(first, second,
                player -> player.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    private int comparePlayersOrDistance(
            LivingEntity first,
            LivingEntity second,
            ToDoubleFunction<Player> score) {
        if (first instanceof Player firstPlayer && second instanceof Player secondPlayer) {
            return Double.compare(score.applyAsDouble(firstPlayer), score.applyAsDouble(secondPlayer));
        }
        return Double.compare(mc.player.distanceTo(first), mc.player.distanceTo(second));
    }

    private boolean isValidTarget(LivingEntity candidate) {
        if (!VapeTargeting.isValidTarget(
                mc, candidate, players.get(), mobs.get(), peaceful.get(), neutral.get(),
                ignoreNaked.get(), ignoreInvisible.get(), ignoreBehindWalls.get())) {
            return false;
        }
        if (candidate.level() != mc.level || candidate == mc.player.getVehicle() || !isInRange(candidate)) {
            return false;
        }
        if (maxAngle.get() < 360.0D && cameraYawDifference(candidate) > maxAngle.get() * 0.5D) {
            return false;
        }
        return !limitToItems.get() || hasRequiredItem();
    }

    private void updateAim() {
        if (target == null || !isValidTarget(target)) {
            resetTargeting();
            return;
        }

        RotationManager rotationManager = RotationManager.INSTANCE;
        if (!rotationManager.ownsSilentRotation(this)
                && !rotationManager.claimSilentRotation(
                        this, rotationManager.getRotation(),
                        com.setsuna.util.rotation.Priority.Highest)) {
            resetTargeting();
            return;
        }

        pitchJitter.update();
        xJitter.update();
        zJitter.update();
        AimCoordinates coordinates = computeAimCoordinates(target);
        double targetMotionX = coordinates.x - coordinates.previousX;
        double targetMotionZ = coordinates.z - coordinates.previousZ;
        double horizontalTargetMotion = Math.hypot(targetMotionX, targetMotionZ);
        double jitteredX = coordinates.x + xJitter.current() * (1.0D + horizontalTargetMotion);
        double jitteredZ = coordinates.z + zJitter.current() * (1.0D + horizontalTargetMotion);
        double playerEyeY = mc.player.getEyeY();
        double jitteredY = playerEyeY < coordinates.y
                ? coordinates.y + pitchJitter.current() * 0.5D
                : Math.min(playerEyeY, coordinates.y + target.getBbHeight())
                        - 0.275D + pitchJitter.current();

        Rot2f managed = rotationManager.getRotation();
        Rot2f previous = rotationManager.getLastRotation();
        Rot2f desired = RotationUtils.calculate(mc.player.getEyePosition(),
                new Vec3(jitteredX, jitteredY, jitteredZ));
        float targetPitch = RotationUtils.calculate(mc.player.getEyePosition(),
                new Vec3(coordinates.x, jitteredY, coordinates.z)).getPitch();
        float managedYaw = managed.getYaw();
        float managedPitch = managed.getPitch();
        float yawError = Mth.wrapDegrees(desired.getYaw() - managedYaw);
        float pitchError = Mth.wrapDegrees(targetPitch - managedPitch);
        float previousPitchStep = managedPitch - previous.getPitch();
        float previousYawStep = Mth.wrapDegrees(managedYaw - previous.getYaw());
        float integrationStep = 0.05F;
        boolean yawContinuing = Math.signum(yawError) == Math.signum(previousYawStep);
        double playerHorizontalSpeed = Math.hypot(
                mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z);
        float pitchProportionalGain = 0.45F * pitchProportionalScale;
        float pitchIntegralGain = 0.91F * pitchIntegralScale;
        float yawProportionalGain = (onTarget ? 0.05F : 0.1F) * yawProportionalScale;
        float yawIntegralGain = 0.33F * yawIntegralScale;

        if (Math.abs(mc.player.getDeltaMovement().y) > 0.1D) {
            pitchError *= (float) (1.0D + Math.random() * 0.32D);
        }
        if (yawContinuing && Math.abs(yawError) < 20.0F) {
            yawProportionalGain *= 2.5F;
            previousYawStep *= (float) (1.0D
                    + Math.min(horizontalTargetMotion + playerHorizontalSpeed, 0.25D));
        }

        double distance = mc.player.distanceToSqr(coordinates.x, coordinates.y, coordinates.z);
        distance = Math.sqrt(distance);
        if (distance < 0.8D) {
            double distanceScale = distance / 0.8D;
            pitchError *= (float) (distanceScale * distanceScale);
            yawError *= (float) distanceScale;
        }

        float pitchControlError = pitchError - previousPitchStep
                + previousYawStep * integrationStep * (Math.random() >= 0.5D ? -1.0F : 1.0F);
        float yawControlError = yawError - previousYawStep;
        pitchIntegral += pitchControlError * integrationStep;
        yawIntegral += yawControlError * integrationStep;
        float pitchAdjustment = pitchProportionalGain * pitchControlError
                + pitchIntegralGain * pitchIntegral;
        float yawAdjustment = yawProportionalGain * yawControlError
                + yawIntegralGain * yawIntegral;
        if (Math.abs(yawError) > 120.0F) {
            yawIntegral = 0.0F;
            yawAdjustment = 0.0F;
        }

        float requestedYaw = managedYaw + yawError + yawAdjustment / 3.0F;
        float requestedPitch = Mth.clamp(managedPitch + pitchAdjustment, -90.0F, 90.0F);
        float maximumStep = calculateRotationStep(distance);
        float nextYaw = managedYaw + Mth.clamp(
                Mth.wrapDegrees(requestedYaw - managedYaw), -maximumStep, maximumStep);
        float nextPitch = managedPitch + Mth.clamp(
                requestedPitch - managedPitch, -maximumStep, maximumStep);
        Rot2f next = RotationUtils.applySensitivityPatch(
                new Rot2f(nextYaw, Mth.clamp(nextPitch, -90.0F, 90.0F)), managed);
        if (!rotationManager.claimSilentRotation(
                this, next, com.setsuna.util.rotation.Priority.Highest)) {
            readyToAttack = false;
        }
    }

    private float calculateRotationStep(double distance) {
        double first = Math.max(1.0E-4D, Math.random());
        double gaussian = Math.sqrt(-2.0D * Math.log(first))
                * Math.cos(Math.PI * 2.0D * Math.random());
        double multiplier = Math.exp(0.65D + 0.25D * gaussian);
        multiplier = Mth.clamp(multiplier, 1.4D, 3.0D);
        double speed = aimSpeed.get() * multiplier;
        if (distance < 0.8D) {
            speed *= distance / 0.8D;
        }

        float sensitivity = mc.options.sensitivity().get().floatValue();
        float sensitivityBase = sensitivity * 0.6F + 0.2F;
        double mouseScale = sensitivityBase * sensitivityBase * sensitivityBase * 8.0D;
        double framesPerTick = Mth.clamp(mc.getFps() / 20.0D, 1.0D, 10.0D);
        return (float) Math.max(0.01D, speed * 0.25D * mouseScale * 0.15D * framesPerTick);
    }

    private void runClickCycle() {
        if (!canAttack()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextClickAt) {
            return;
        }
        if (!AttackKeyController.requestSyntheticAttack(this)) {
            return;
        }
        attackPending = true;
        clickTiming.configureCpsRange(Math.min(minAps.get(), maxAps.get()),
                Math.max(minAps.get(), maxAps.get()));
        long delay = clickTiming.nextDelayMillis() - 5L;
        if (delay <= 50L) {
            delay = 45L;
        }
        nextClickAt = now + delay;
    }

    private boolean canAttack() {
        if (noPlayer() || mc.gameMode == null || mc.screen != null || breakingBlocks
                || target == null || !readyToAttack) {
            return false;
        }
        return !perfectSwing.get() || mc.player.getAttackStrengthScale(0.0F) == 1.0F;
    }

    private boolean handleBreakBlocks() {
        if (!breakBlocks.get() || noPlayer()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < nextBreakCheckAt) {
            return false;
        }
        if (breakBlocksWhitelist.get() && !matchesBreakWhitelist(mc.player.getMainHandItem())) {
            return false;
        }
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            return false;
        }

        HitResult physicalHit = physicalBlockRayTrace();
        if (physicalHit != null && physicalHit.getType() == HitResult.Type.BLOCK
                && KeybindUtils.isPressed(mc.options.keyAttack)) {
            mc.options.keyAttack.setDown(true);
            return true;
        }
        nextBreakCheckAt = now + Math.round(randomRange(breakDelayMin.get(), breakDelayMax.get()));
        return false;
    }

    private HitResult physicalBlockRayTrace() {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 direction = Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot());
        return mc.level.clip(new ClipContext(
                eye, eye.add(direction.scale(3.0D)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, mc.player));
    }

    private boolean matchesBreakWhitelist(ItemStack stack) {
        return breakPickaxes.get() && stack.is(net.minecraft.tags.ItemTags.PICKAXES)
                || breakShovels.get() && stack.is(net.minecraft.tags.ItemTags.SHOVELS)
                || breakAxes.get() && stack.is(net.minecraft.tags.ItemTags.AXES)
                || breakHoes.get() && stack.is(net.minecraft.tags.ItemTags.HOES);
    }

    private boolean hasRequiredItem() {
        return VapeTargeting.isAllowedItem(
                mc.player.getMainHandItem(), swords.get(), axes.get(), maces.get(), spears.get(),
                pickaxes.get(), shovels.get(), hoes.get(), otherItems.get());
    }

    private boolean isInRange(LivingEntity entity) {
        AimCoordinates coordinates = computeAimCoordinates(entity);
        return mc.player.distanceToSqr(coordinates.x, coordinates.y, coordinates.z)
                <= getAttackRange() * getAttackRange();
    }

    private double getAttackRange() {
        return 3.0D + extraSwingDistance.get();
    }

    private AimCoordinates computeAimCoordinates(LivingEntity entity) {
        if (targetArea.is(TargetArea.Closest)) {
            AABB box = entity.getBoundingBox();
            double currentX = Mth.clamp(mc.player.getX(), box.minX, box.maxX);
            double currentZ = Mth.clamp(mc.player.getZ(), box.minZ, box.maxZ);
            double motionX = entity.getX() - entity.xo;
            double motionZ = entity.getZ() - entity.zo;
            return new AimCoordinates(currentX, entity.getY(), currentZ,
                    currentX - motionX, entity.yo, currentZ - motionZ);
        }
        return new AimCoordinates(entity.getX(), entity.getY(), entity.getZ(),
                entity.xo, entity.yo, entity.zo);
    }

    private double cameraYawDifference(LivingEntity entity) {
        float yaw = RotationUtils.calculate(mc.player.getEyePosition(), entity.getBoundingBox().getCenter()).getYaw();
        return Math.abs(Mth.wrapDegrees(yaw - mc.player.getYRot()));
    }

    private double horizontalAngleToTarget(LivingEntity entity) {
        float targetYaw = RotationUtils.calculate(mc.player.getEyePosition(),
                entity.getBoundingBox().getCenter()).getYaw();
        return Math.abs(Mth.wrapDegrees(targetYaw - RotationManager.INSTANCE.getYaw()));
    }

    private boolean isLookingAtTarget() {
        if (target == null || !RotationManager.INSTANCE.ownsSilentRotation(this)
                || !isInRange(target)) {
            return false;
        }
        HitResult result = RaytraceUtils.raytrace(
                RotationManager.INSTANCE.getRotation(), getAttackRange());
        return result instanceof EntityHitResult hit && hit.getEntity() == target;
    }

    private void randomizeGainFactors() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        pitchProportionalScale = 0.85F + random.nextFloat() * 0.3F;
        pitchIntegralScale = 0.85F + random.nextFloat() * 0.3F;
        yawProportionalScale = 0.8F + random.nextFloat() * 0.4F;
        yawIntegralScale = 0.85F + random.nextFloat() * 0.3F;
    }

    private static double randomRange(double first, double second) {
        double minimum = Math.min(first, second);
        double maximum = Math.max(first, second);
        return minimum == maximum
                ? minimum
                : ThreadLocalRandom.current().nextDouble(minimum, maximum);
    }

    private void resetTargeting() {
        pitchIntegral = 0.0F;
        yawIntegral = 0.0F;
        target = null;
        readyToAttack = false;
        onTarget = false;
        RotationManager.INSTANCE.releaseSilentRotation(this);
    }

    private void resetState() {
        AttackKeyController.releaseAttackKey(this);
        attackPending = false;
        breakingBlocks = false;
        nextBreakCheckAt = 0L;
        nextClickAt = System.currentTimeMillis() + 50L;
        resetTargeting();
        randomizeGainFactors();
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @Override
    public String getInfo() {
        if (perfectSwing.get() && mc.player != null) {
            return mc.player.getAttackStrengthScale(0.0F) == 1.0F
                    ? "Ready"
                    : String.format(Locale.ROOT, "%.1f", mc.player.getAttackStrengthScale(0.0F));
        }
        return Math.min(minAps.get(), maxAps.get()) + "-"
                + Math.max(minAps.get(), maxAps.get()) + " APS";
    }

    public LivingEntity getTarget() {
        return target;
    }

    private record AimCoordinates(
            double x,
            double y,
            double z,
            double previousX,
            double previousY,
            double previousZ) {
    }
}
