package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AntiWeb extends Module {

    public static final AntiWeb INSTANCE = new AntiWeb();

    private AntiWeb() {
        super("Anti Web", Category.PLAYER);
    }

    public enum Phase {
        Idle,
        Placing,
        Recycling
    }

    private final IntSetting checkDelay = add(new IntSetting("Check Delay", 5, 1, 20, 1));
    private final IntSetting recycleTimeout = add(new IntSetting("Recycle Timeout", 20, 5, 60, 1));
    private final IntSetting recycleInterval = add(new IntSetting("Recycle Interval", 5, 1, 20, 1));
    private final IntSetting rotationSpeed = add(new IntSetting("Rotation Speed", 10, 1, 20, 1));
    private final BooleanSetting restoreSlot = add(new BooleanSetting("Restore Slot", true));

    private Phase phase = Phase.Idle;
    private BlockPos webPos;
    private BlockPos waterSourcePos;
    private Rot2f targetRotation;
    private int savedSlot = -1;
    private int waterBucketSlot = -1;
    private int checkTicks;
    private int placementTicks;
    private int pickupTicks;
    private boolean sentUsePacket;

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        finishCycle();
    }

    @Listen
    private void onTick(PlayerTickEvent.Post event) {
        if (noPlayer() || mc.gameMode == null) return;
        if (AutoMLG.INSTANCE.isEnabled() && AutoMLG.INSTANCE.blocksOtherActions()) return;

        if (targetRotation != null) {
            RotationManager.INSTANCE.setRotations(targetRotation, rotationSpeed.get(), Priority.Highest);
        }

        switch (phase) {
            case Idle -> tickIdle();
            case Placing -> tickPlacing();
            case Recycling -> tickRecycling();
        }
    }

    private void tickIdle() {
        if (mc.player.isInWater()) {
            return;
        }

        if (++checkTicks < checkDelay.get()) {
            return;
        }
        checkTicks = 0;

        if (!isInCobweb()) {
            return;
        }

        waterBucketSlot = findHotbarSlot(Items.WATER_BUCKET);
        if (waterBucketSlot == -1) {
            return;
        }

        savedSlot = mc.player.getInventory().getSelectedSlot();
        phase = Phase.Placing;
        sentUsePacket = false;
        placementTicks = 0;
        pickupTicks = 0;
    }

    private void tickPlacing() {
        if (webPos == null) {
            finishCycle();
            return;
        }

        placementTicks++;
        pickupTicks++;

        boolean stillInCobweb = isInCobweb();
        BlockPos source = findNearestWaterSource(webPos);
        if (source != null) {
            beginRecycling(source);
            return;
        }

        if (pickupTicks > recycleTimeout.get() || (!stillInCobweb && !sentUsePacket)) {
            finishCycle();
            return;
        }

        if (sentUsePacket
                && !mc.player.getInventory().getItem(waterBucketSlot).is(Items.WATER_BUCKET)) {
            return;
        }

        if (savedSlot == -1) {
            savedSlot = mc.player.getInventory().getSelectedSlot();
        }

        setSelectedSlot(waterBucketSlot);
        Vec3 target = Vec3.atCenterOf(webPos).add(0.0, 0.5, 0.0);
        targetRotation = RotationUtils.calculate(mc.player.getEyePosition(), target);
        RotationManager.INSTANCE.setRotations(targetRotation, rotationSpeed.get(), Priority.Highest);

        // 等平滑旋转对准目标后再放水，避免瞬间抽头
        if (!shouldAttemptUse(sentUsePacket, placementTicks, recycleInterval.get())
                || !isAlignedTo(targetRotation)) {
            return;
        }

        if (useMainHand()) {
            sentUsePacket = true;
            placementTicks = 0;
        }
    }

    private void tickRecycling() {
        pickupTicks++;
        placementTicks++;

        if (pickupTicks > recycleTimeout.get()) {
            finishCycle();
            return;
        }

        boolean holdingWaterBucket = mc.player.getMainHandItem().is(Items.WATER_BUCKET);
        if (holdingWaterBucket) {
            if (isPickupConfirmed(sentUsePacket, holdingWaterBucket)) {
                finishCycle();
            }
            return;
        }

        if (waterSourcePos == null || !isWaterSource(waterSourcePos)) {
            finishCycle();
            return;
        }

        if (!mc.player.getMainHandItem().is(Items.BUCKET)) {
            setSelectedSlot(waterBucketSlot);
            targetRotation = null;
            return;
        }

        targetRotation = RotationUtils.calculate(waterSourcePos.getCenter());
        RotationManager.INSTANCE.setRotations(targetRotation, rotationSpeed.get(), Priority.Highest);

        if (!shouldAttemptUse(sentUsePacket, placementTicks, recycleInterval.get())) {
            return;
        }

        // 首次收水前等平滑旋转对准水源，避免瞬间抽头
        if (!sentUsePacket && !isAlignedTo(targetRotation)) {
            return;
        }

        if (useMainHand()) {
            sentUsePacket = true;
            placementTicks = 0;
        }
    }

    private void beginRecycling(BlockPos source) {
        waterSourcePos = source.immutable();
        phase = Phase.Recycling;
        sentUsePacket = false;
        placementTicks = 0;
        pickupTicks = 0;
    }

    private boolean isInCobweb() {
        AABB box = mc.player.getBoundingBox().inflate(0.1);
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) {
                        webPos = pos;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private BlockPos findNearestWaterSource(BlockPos origin) {
        if (isWaterSource(origin)) {
            return origin;
        }

        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!isWaterSource(pos)) {
                        continue;
                    }

                    double distance = origin.distSqr(pos);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = pos;
                    }
                }
            }
        }
        return nearest;
    }

    private boolean isWaterSource(BlockPos pos) {
        return mc.level.getFluidState(pos).isSourceOfType(Fluids.WATER);
    }

    private int findHotbarSlot(net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    private void setSelectedSlot(int slot) {
        if (slot < 0 || slot > 8 || mc.player.getInventory().getSelectedSlot() == slot) {
            return;
        }

        mc.player.getInventory().setSelectedSlot(slot);
        PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(slot));
    }

    private Rot2f interactionRotation() {
        return RotationManager.INSTANCE.isActive()
                ? RotationManager.INSTANCE.getRotation()
                : targetRotation;
    }

    private void sendRotation(Rot2f rotation) {
        // 使用 RotationManager 的当前平滑旋转发包，与 KillAura 一致，避免瞬间跳转
        PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Rot(
                rotation.getYaw(),
                rotation.getPitch(),
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));
    }

    private boolean useMainHand() {
        Rot2f rotation = interactionRotation();
        if (mc.gameMode == null || rotation == null) {
            return false;
        }

        sendRotation(rotation);
        float oldYaw = mc.player.getYRot();
        float oldPitch = mc.player.getXRot();
        float oldHeadYaw = mc.player.yHeadRot;
        float oldBodyYaw = mc.player.yBodyRot;
        InteractionResult result;
        try {
            mc.player.setYRot(rotation.getYaw());
            mc.player.setXRot(rotation.getPitch());
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
        mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
        return true;
    }

    public boolean hasTargetRotation() {
        return targetRotation != null;
    }

    /** True while AntiWeb owns the player's interaction cycle. */
    public boolean blocksOtherActions() {
        return shouldBlockOtherActions(phase);
    }

    static boolean shouldBlockOtherActions(Phase currentPhase) {
        return currentPhase != null && currentPhase != Phase.Idle;
    }

    /** 平滑旋转是否已接近目标角度（放水/收水前的对齐门槛，避免瞬间抽头动作）。 */
    private boolean isAlignedTo(Rot2f target) {
        if (target == null) return false;
        if (!RotationManager.INSTANCE.isActive()) return false;
        Rot2f cur = RotationManager.INSTANCE.getRotation();
        float dYaw = Math.abs(Mth.wrapDegrees(cur.getYaw() - target.getYaw()));
        float dPitch = Math.abs(cur.getPitch() - target.getPitch());
        return dYaw <= 3.0f && dPitch <= 3.0f;
    }

    static boolean shouldAttemptUse(boolean sentUsePacket, int ticksSinceUse, int interval) {
        return !sentUsePacket || ticksSinceUse >= Math.max(1, interval);
    }

    static boolean isPickupConfirmed(boolean sentPickup, boolean holdingWaterBucket) {
        return sentPickup && holdingWaterBucket;
    }

    static int slotToRestore(int savedSlot, boolean restoreSlot) {
        return restoreSlot && savedSlot >= 0 && savedSlot <= 8 ? savedSlot : -1;
    }

    private void finishCycle() {
        int restoreTarget = slotToRestore(savedSlot, restoreSlot.get());
        if (mc.player != null && restoreTarget != -1) {
            setSelectedSlot(restoreTarget);
        }
        resetState();
    }

    private void resetState() {
        phase = Phase.Idle;
        webPos = null;
        waterSourcePos = null;
        targetRotation = null;
        savedSlot = -1;
        waterBucketSlot = -1;
        checkTicks = 0;
        placementTicks = 0;
        pickupTicks = 0;
        sentUsePacket = false;
    }
}

