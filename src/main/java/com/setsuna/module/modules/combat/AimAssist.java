package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.RenderFrameEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.combat.aimassist.AdaptiveAimController;
import com.setsuna.module.modules.combat.aimassist.AimAssistController;
import com.setsuna.module.modules.combat.aimassist.SimpleAimController;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.util.client.KeybindUtils;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/** Visible, mouse-quantized port of Vape 4.21's AimAssist module. */
public final class AimAssist extends Module {

    private static final long RETARGET_INTERVAL_NANOS = 700_000_000L;
    private static final long BLOCK_BREAK_COOLDOWN_NANOS = 250_000_000L;
    private static final float DEFAULT_FRAME_TIME = 1.0F / 60.0F;

    public static final AimAssist INSTANCE = new AimAssist();

    private enum Mode {
        Simple,
        Adaptive
    }

    private enum TargetMode {
        Yaw,
        Distance,
        Armor,
        Threat,
        Health
    }

    private enum TargetArea {
        Center,
        Closest
    }

    private final AimAssistController simpleController = new SimpleAimController();
    private final AimAssistController adaptiveController = new AdaptiveAimController();

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Simple)
            .onChange(ignored -> resetAimState(true)));
    private final BooleanSetting requireMouseDown = add(new BooleanSetting("Require Mouse Down", true));
    private final BooleanSetting verticalAim = add(new BooleanSetting("Vertical Aim", false));
    private final BooleanSetting strafeIncrease = add(new BooleanSetting("Strafe Increase", false));
    private final BooleanSetting checkBlockBreak = add(new BooleanSetting("Check Block Break", false));
    private final BooleanSetting breakBlocksWhitelist = add(new BooleanSetting("Break Blocks Whitelist", false)
            .visibleWhen(checkBlockBreak::get));
    private final BooleanSetting breakPickaxes = add(new BooleanSetting("Break Pickaxes", true)
            .visibleWhen(this::showBreakItems));
    private final BooleanSetting breakShovels = add(new BooleanSetting("Break Shovels", true)
            .visibleWhen(this::showBreakItems));
    private final BooleanSetting breakAxes = add(new BooleanSetting("Break Axes", false)
            .visibleWhen(this::showBreakItems));
    private final BooleanSetting breakHoes = add(new BooleanSetting("Break Hoes", false)
            .visibleWhen(this::showBreakItems));
    private final DoubleSetting verticalSpeed = add(new DoubleSetting("Vertical Speed", 5.0D, 1.0D, 10.0D, 0.1D)
            .visibleWhen(verticalAim::get));
    private final DoubleSetting horizontalSpeed = add(new DoubleSetting("Horizontal Speed", 5.0D, 1.0D, 10.0D, 0.1D));
    private final DoubleSetting maxAngle = add(new DoubleSetting("Max Angle", 180.0D, 1.0D, 360.0D, 1.0D));
    private final DoubleSetting distance = add(new DoubleSetting("Distance", 5.0D, 1.0D, 8.0D, 0.1D));
    private final BooleanSetting limitToItems = add(new BooleanSetting("Limit To Items", false));
    private final BooleanSetting swords = add(new BooleanSetting("Swords", true)
            .visibleWhen(limitToItems::get));
    private final BooleanSetting axes = add(new BooleanSetting("Axes", false)
            .visibleWhen(limitToItems::get));
    private final BooleanSetting maces = add(new BooleanSetting("Maces", false)
            .visibleWhen(limitToItems::get));
    private final BooleanSetting spears = add(new BooleanSetting("Spears", false)
            .visibleWhen(limitToItems::get));
    private final BooleanSetting pickaxes = add(new BooleanSetting("Pickaxes", false)
            .visibleWhen(limitToItems::get));
    private final BooleanSetting shovels = add(new BooleanSetting("Shovels", false)
            .visibleWhen(limitToItems::get));
    private final BooleanSetting hoes = add(new BooleanSetting("Hoes", false)
            .visibleWhen(limitToItems::get));
    private final BooleanSetting otherItems = add(new BooleanSetting("Other Items", false)
            .visibleWhen(limitToItems::get));
    private final EnumSetting<TargetArea> targetArea = add(new EnumSetting<>("Target Area", TargetArea.Center)
            .onChange(ignored -> resetControllers()));
    private final EnumSetting<TargetMode> targetMode = add(new EnumSetting<>("Target Mode", TargetMode.Yaw));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", false));
    private final BooleanSetting peaceful = add(new BooleanSetting("Peaceful", false));
    private final BooleanSetting neutral = add(new BooleanSetting("Neutral", false));
    private final BooleanSetting ignoreNaked = add(new BooleanSetting("Ignore Naked", false));
    private final BooleanSetting ignoreInvisible = add(new BooleanSetting("Ignore Invisible", false));
    private final BooleanSetting ignoreBehindWalls = add(new BooleanSetting("Ignore Behind Walls", false));

    private LivingEntity currentTarget;
    private long nextRetargetNanos;
    private long blockBreakCooldownUntilNanos;
    private long lastFrameNanos;
    private float pendingYaw;
    private float pendingPitch;

    private AimAssist() {
        super("AimAssist", Category.COMBAT);
    }

    @Listen
    private void onRenderFrame(RenderFrameEvent event) {
        long now = System.nanoTime();
        float deltaTime = frameTime(now);
        if (!isEnabled() || !event.renderLevel() || noPlayer() || !canAim(now)) {
            suspendAim(shouldClearTarget());
            return;
        }

        updateTarget(now);
        if (currentTarget == null) {
            resetControllers();
            clearPendingRotation();
            return;
        }

        Vec3 eyePosition = mc.player.getEyePosition(event.partialTick());
        Vec3 aimPoint = resolveAimPoint(currentTarget, eyePosition, event.partialTick());
        Rot2f targetRotation = RotationUtils.calculate(eyePosition, aimPoint);
        float yawError = Mth.wrapDegrees(targetRotation.getYaw() - mc.player.getYRot());
        Vec2 moveVector = mc.player.input.getMoveVector();
        boolean targetOnLeft = yawError > 0.0F;
        boolean strafingAway = Math.abs(moveVector.x) > 0.01F
                && (targetOnLeft ? moveVector.x < 0.0F : moveVector.x > 0.0F);

        AimAssistController.Frame frame = new AimAssistController.Frame(
                eyePosition,
                aimPoint,
                currentTarget.getDeltaMovement(),
                mc.player.getDeltaMovement(),
                mc.player.getYRot(),
                mc.player.getXRot(),
                targetRotation.getYaw(),
                targetRotation.getPitch(),
                horizontalSpeed.get().floatValue(),
                verticalSpeed.get().floatValue(),
                verticalAim.get(),
                strafeIncrease.get() && strafingAway,
                eyeDistance(currentTarget),
                deltaTime,
                now
        );
        AimAssistController.RotationDelta rotation = activeController().update(frame);
        applyRotation(rotation, event.partialTick());
    }

    private boolean canAim(long now) {
        if (mc.gameMode == null || mc.screen != null || mc.isPaused() || !mc.isWindowActive()) {
            return false;
        }
        if (RotationManager.INSTANCE.isActive()) {
            return false;
        }
        if (requireMouseDown.get() && !KeybindUtils.isPressed(mc.options.keyAttack)) {
            return false;
        }
        if (!hasRequiredItem()) {
            return false;
        }
        if (!checkBlockBreak.get() || !matchesBreakWhitelist(mc.player.getMainHandItem())) {
            blockBreakCooldownUntilNanos = 0L;
            return true;
        }
        if (isBreakingBlock()) {
            blockBreakCooldownUntilNanos = now + BLOCK_BREAK_COOLDOWN_NANOS;
            return false;
        }
        return now >= blockBreakCooldownUntilNanos;
    }

    private boolean shouldClearTarget() {
        return noPlayer() || mc.screen != null
                || (requireMouseDown.get() && !KeybindUtils.isPressed(mc.options.keyAttack));
    }

    private boolean isBreakingBlock() {
        if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            return true;
        }
        return KeybindUtils.isPressed(mc.options.keyAttack)
                && mc.hitResult instanceof BlockHitResult
                && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    private void updateTarget(long now) {
        if (currentTarget != null && !isValidTarget(currentTarget)) {
            setTarget(null);
        }

        if (requireMouseDown.get()) {
            if (currentTarget == null) {
                setTarget(findBestTarget());
            }
            return;
        }

        if (currentTarget == null || now >= nextRetargetNanos) {
            setTarget(findBestTarget());
            nextRetargetNanos = now + RETARGET_INTERVAL_NANOS;
        }
    }

    private LivingEntity findBestTarget() {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && isValidTarget(living)) {
                targets.add(living);
            }
        }
        targets.sort(targetComparator());
        return targets.isEmpty() ? null : targets.getFirst();
    }

    private Comparator<LivingEntity> targetComparator() {
        Comparator<LivingEntity> selected = switch (targetMode.get()) {
            case Yaw -> Comparator.comparingDouble(this::yawDifference);
            case Distance -> Comparator.comparingDouble(this::eyeDistance);
            case Armor -> this::compareArmor;
            case Threat -> this::compareThreat;
            case Health -> Comparator.comparingDouble(LivingEntity::getHealth);
        };
        return selected.thenComparingDouble(this::eyeDistance).thenComparingInt(Entity::getId);
    }

    private int compareArmor(LivingEntity first, LivingEntity second) {
        return comparePlayersOrDistance(first, second,
                player -> player.getArmorValue()
                        + player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * 0.25D);
    }

    private int compareThreat(LivingEntity first, LivingEntity second) {
        return comparePlayersOrDistance(first, second,
                player -> player.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    private int comparePlayersOrDistance(LivingEntity first, LivingEntity second,
                                         ToDoubleFunction<Player> score) {
        if (first instanceof Player firstPlayer && second instanceof Player secondPlayer) {
            return Double.compare(score.applyAsDouble(firstPlayer), score.applyAsDouble(secondPlayer));
        }
        return Double.compare(eyeDistance(first), eyeDistance(second));
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target == mc.player || target instanceof ArmorStand || target.isSpectator()
                || !target.isPickable() || !target.isAlive() || target.isDeadOrDying()) {
            return false;
        }
        if (target.level() != mc.level || target == mc.player.getVehicle()) {
            return false;
        }
        if (eyeDistance(target) >= distance.get()) {
            return false;
        }
        if (maxAngle.get() < 360.0D && yawDifference(target) > maxAngle.get() * 0.5D) {
            return false;
        }
        if (ignoreInvisible.get() && target.isInvisible()) {
            return false;
        }
        if (ignoreBehindWalls.get() && !mc.player.hasLineOfSight(target)) {
            return false;
        }

        if (target instanceof Player player) {
            if (!players.get() || FriendManager.INSTANCE.isFriend(player)) {
                return false;
            }
            return !ignoreNaked.get() || player.getArmorValue() > 0;
        }
        if (target instanceof NeutralMob) {
            return neutral.get();
        }
        if (target instanceof Enemy) {
            return mobs.get();
        }
        if (target instanceof Animal || target instanceof Villager) {
            return peaceful.get();
        }
        return target instanceof Mob && neutral.get();
    }

    private Vec3 resolveAimPoint(LivingEntity target, Vec3 eyePosition, float partialTick) {
        Vec3 interpolatedPosition = target.getPosition(partialTick);
        AABB box = target.getBoundingBox().move(interpolatedPosition.subtract(target.position()));
        if (targetArea.is(TargetArea.Center)) {
            return new Vec3(
                    (box.minX + box.maxX) * 0.5D,
                    box.minY + box.getYsize() * 0.65D,
                    (box.minZ + box.maxZ) * 0.5D
            );
        }

        double insetX = Math.min(0.01D, box.getXsize() * 0.25D);
        double insetY = Math.min(0.01D, box.getYsize() * 0.25D);
        double insetZ = Math.min(0.01D, box.getZsize() * 0.25D);
        return new Vec3(
                Mth.clamp(eyePosition.x, box.minX + insetX, box.maxX - insetX),
                Mth.clamp(eyePosition.y, box.minY + insetY, box.maxY - insetY),
                Mth.clamp(eyePosition.z, box.minZ + insetZ, box.maxZ - insetZ)
        );
    }

    private double yawDifference(LivingEntity target) {
        return Math.abs(Mth.wrapDegrees(RotationUtils.getRotationsToEntity(target).getYaw()
                - mc.player.getYRot()));
    }

    private double eyeDistance(LivingEntity target) {
        return RotationUtils.getEyeDistanceToEntity(target);
    }

    private void applyRotation(AimAssistController.RotationDelta rotation, float partialTick) {
        float yawDelta = finiteOrZero(rotation.yaw());
        float pitchDelta = verticalAim.get() ? finiteOrZero(rotation.pitch()) : 0.0F;
        if (yawDelta != 0.0F && pendingYaw != 0.0F
                && Math.signum(yawDelta) != Math.signum(pendingYaw)) {
            pendingYaw = 0.0F;
        }
        if (pitchDelta != 0.0F && pendingPitch != 0.0F
                && Math.signum(pitchDelta) != Math.signum(pendingPitch)) {
            pendingPitch = 0.0F;
        }
        pendingYaw += yawDelta;
        pendingPitch = verticalAim.get() ? pendingPitch + pitchDelta : 0.0F;

        float mouseUnit = mouseAngleUnit();
        int yawSteps = (int) (pendingYaw / mouseUnit);
        int pitchSteps = (int) (pendingPitch / mouseUnit);
        float appliedYaw = yawSteps * mouseUnit;
        float appliedPitch = pitchSteps * mouseUnit;
        pendingYaw -= appliedYaw;
        pendingPitch -= appliedPitch;
        if (yawSteps == 0 && pitchSteps == 0) {
            return;
        }

        mc.player.turn(appliedYaw / 0.15F, appliedPitch / 0.15F);
        mc.pick(partialTick);
    }

    private float frameTime(long now) {
        float deltaTime = lastFrameNanos == 0L
                ? DEFAULT_FRAME_TIME
                : (now - lastFrameNanos) / 1.0E9F;
        lastFrameNanos = now;
        return Mth.clamp(deltaTime, 1.0F / 240.0F, 0.1F);
    }

    private float mouseAngleUnit() {
        float sensitivity = mc.options.sensitivity().get().floatValue();
        float base = sensitivity * 0.6F + 0.2F;
        return Math.max(1.0E-5F, base * base * base * 8.0F * 0.15F);
    }

    private boolean hasRequiredItem() {
        if (!limitToItems.get()) {
            return true;
        }
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.SWORDS)) return swords.get();
        if (stack.is(ItemTags.AXES)) return axes.get();
        if (stack.getItem() instanceof MaceItem) return maces.get();
        if (stack.is(ItemTags.SPEARS)) return spears.get();
        if (stack.is(ItemTags.PICKAXES)) return pickaxes.get();
        if (stack.is(ItemTags.SHOVELS)) return shovels.get();
        if (stack.is(ItemTags.HOES)) return hoes.get();
        return otherItems.get();
    }

    private boolean matchesBreakWhitelist(ItemStack stack) {
        if (!breakBlocksWhitelist.get()) {
            return true;
        }
        return breakPickaxes.get() && stack.is(ItemTags.PICKAXES)
                || breakShovels.get() && stack.is(ItemTags.SHOVELS)
                || breakAxes.get() && stack.is(ItemTags.AXES)
                || breakHoes.get() && stack.is(ItemTags.HOES);
    }

    private boolean showBreakItems() {
        return checkBlockBreak.get() && breakBlocksWhitelist.get();
    }

    private AimAssistController activeController() {
        return mode.is(Mode.Adaptive) ? adaptiveController : simpleController;
    }

    private void setTarget(LivingEntity target) {
        if (currentTarget == target) {
            return;
        }
        currentTarget = target;
        resetControllers();
        clearPendingRotation();
    }

    private void suspendAim(boolean clearTarget) {
        if (clearTarget) {
            currentTarget = null;
            nextRetargetNanos = 0L;
        }
        resetControllers();
        clearPendingRotation();
    }

    private void resetAimState(boolean clearTarget) {
        if (clearTarget) {
            currentTarget = null;
        }
        nextRetargetNanos = 0L;
        blockBreakCooldownUntilNanos = 0L;
        lastFrameNanos = 0L;
        resetControllers();
        clearPendingRotation();
    }

    private void resetControllers() {
        simpleController.reset();
        adaptiveController.reset();
    }

    private void clearPendingRotation() {
        pendingYaw = 0.0F;
        pendingPitch = 0.0F;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    @Override
    protected void onEnable() {
        resetAimState(true);
    }

    @Override
    protected void onDisable() {
        resetAimState(true);
    }

    @Override
    public String getInfo() {
        return String.format(Locale.ROOT, "%.1f", horizontalSpeed.get());
    }
}
