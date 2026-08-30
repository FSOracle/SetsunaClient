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
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
        if (!noPlayer() && savedSlot != -1 && restoreSlot.get()) {
            setSelectedSlot(savedSlot);
        }
        resetState();
    }

    @Listen
    private void onTick(PlayerTickEvent.Post event) {
        if (noPlayer()) return;
        if (AutoMLG.INSTANCE.isEnabled() && AutoMLG.INSTANCE.blocksOtherActions()) return;

        if (targetRotation != null) {
            RotationManager.INSTANCE.setRotations(targetRotation, rotationSpeed.get(), Priority.Highest);
        }

        if (phase == Phase.Placing && webPos != null && mc.level.getBlockState(webPos).is(Blocks.WATER)) {
            waterSourcePos = findNearestWaterSource(webPos);
            if (waterSourcePos != null) {
                phase = Phase.Recycling;
                sentUsePacket = false;
                placementTicks = 0;
                pickupTicks = 0;
            } else {
                resetState();
            }
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

        phase = Phase.Placing;
        sentUsePacket = false;
    }

    private void tickPlacing() {
        if (webPos == null || !isInCobweb()) {
            resetState();
            return;
        }

        if (sentUsePacket) {
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
        if (!isAlignedTo(targetRotation)) {
            return;
        }

        sendRotation(targetRotation);
        sendUseItem();
        sentUsePacket = true;
    }

    private void tickRecycling() {
        pickupTicks++;
        placementTicks++;

        if (pickupTicks > recycleTimeout.get()) {
            resetState();
            return;
        }

        if (mc.player.getMainHandItem().is(Items.WATER_BUCKET)) {
            resetState();
            return;
        }

        if (waterSourcePos == null || !mc.level.getBlockState(waterSourcePos).is(Blocks.WATER)) {
            resetState();
            return;
        }

        if (!mc.player.getMainHandItem().is(Items.BUCKET)) {
            setSelectedSlot(waterBucketSlot);
            targetRotation = null;
            return;
        }

        targetRotation = RotationUtils.calculate(waterSourcePos.getCenter());
        RotationManager.INSTANCE.setRotations(targetRotation, rotationSpeed.get(), Priority.Highest);

        if (sentUsePacket && placementTicks < recycleInterval.get()) {
            return;
        }

        // 首次收水前等平滑旋转对准水源，避免瞬间抽头
        if (!sentUsePacket && !isAlignedTo(targetRotation)) {
            return;
        }

        sendRotation(targetRotation);
        sendUseItem();
        sentUsePacket = true;
        placementTicks = 0;
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
                    if (!isWaterSource(pos) && !mc.level.getBlockState(pos).is(Blocks.WATER)) {
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

    private void sendRotation(Rot2f rotation) {
        // 使用 RotationManager 的当前平滑旋转发包，与 KillAura 一致，避免瞬间跳转
        Rot2f rot = RotationManager.INSTANCE.isActive()
                ? RotationManager.INSTANCE.getRotation()
                : rotation;
        PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Rot(
                rot.getYaw(),
                rot.getPitch(),
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));
    }

    private void sendUseItem() {
        int sequence;
        try (var prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            sequence = prediction.currentSequence();
        }
        Rot2f rot = RotationManager.INSTANCE.isActive()
                ? RotationManager.INSTANCE.getRotation()
                : (targetRotation != null ? targetRotation : new Rot2f(mc.player.getYRot(), mc.player.getXRot()));
        PacketUtils.sendSilently(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND,
                sequence,
                rot.getYaw(),
                rot.getPitch()
        ));
    }

    public boolean hasTargetRotation() {
        return targetRotation != null;
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

