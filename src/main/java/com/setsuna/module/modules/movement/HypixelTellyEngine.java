package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.event.events.WorldChangeEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.Rot2f;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Direct runtime port of Exhibobo's Telly / Hypixel Scaffold implementation. */
public final class HypixelTellyEngine {

    private static final Minecraft mc = Minecraft.getInstance();

    private static final double[] PLACE_OFFSETS = new double[]{
            0.03125D,
            0.09375D,
            0.15625D,
            0.21875D,
            0.28125D,
            0.34375D,
            0.40625D,
            0.46875D,
            0.53125D,
            0.59375D,
            0.65625D,
            0.71875D,
            0.78125D,
            0.84375D,
            0.90625D,
            0.96875D
    };

    public enum RotationsMode {
        None,
        Default,
        Backwards,
        Sideways
    }

    public enum SprintMode {
        None,
        Vanilla
    }

    public enum TowerMode {
        None,
        Vanilla,
        Extra,
        Telly,
        Hypixel
    }

    public enum KeepYMode {
        None,
        Vanilla,
        Extra,
        Telly,
        Hypixel
    }

    private final Scaffold parent;
    private final Set<Setting<?>> settings = Collections.newSetFromMap(new IdentityHashMap<>());

    public final EnumSetting<RotationsMode> rotations;
    public final EnumSetting<SprintMode> sprintMode;
    public final EnumSetting<TowerMode> towerMode;
    public final EnumSetting<KeepYMode> keepyMode;
    public final DoubleSetting firstMaxRotate;
    public final IntSetting stopTicks;
    public final BooleanSetting keepYOnPress;
    public final BooleanSetting swing;
    public final BooleanSetting itemSpoof;
    public final BooleanSetting multiplace;
    public final BooleanSetting safewalk;
    public final BooleanSetting raycast;

    private int rotationTick = 0;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private int towerTick = 0;
    private int towerDelay = 0;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private Direction targetFacing = null;
    private int groundTicks = 0;
    private int airTicks = 0;
    private int timerticks = 0;
    private float lastYaw = 0.0F;
    private float lastPitch = 0.0F;
    private float last = 0.0F;
    private boolean shouldJump = false;
    private BlockData pendingBlockData;
    private Vec3 pendingHitVec;

    HypixelTellyEngine(Scaffold parent) {
        this.parent = parent;

        this.rotations = register(new EnumSetting<>("HypixelTelly/Rotations", RotationsMode.Backwards).displayAs("Rotations"));
        this.sprintMode = register(new EnumSetting<>("HypixelTelly/Sprint", SprintMode.Vanilla).displayAs("Sprint"));
        this.towerMode = register(new EnumSetting<>("HypixelTelly/Tower", TowerMode.Hypixel).displayAs("Tower"));
        this.keepyMode = register(new EnumSetting<>("HypixelTelly/Keep Y", KeepYMode.Telly).displayAs("Keep Y"));
        this.firstMaxRotate = register(new DoubleSetting("HypixelTelly/First Rotate", 30.0D, 30.0D, 135.0D, 0.1D).displayAs("First Rotate"));
        this.stopTicks = register(new IntSetting("HypixelTelly/Stop Ticks", 1, 0, 15, 1).displayAs("Stop Ticks"));
        this.keepYOnPress = register(new BooleanSetting("HypixelTelly/Keep Y On Press", true).displayAs("Keep Y On Press"));
        this.swing = register(new BooleanSetting("HypixelTelly/Swing", true).displayAs("Swing"));
        this.itemSpoof = register(new BooleanSetting("HypixelTelly/Item Spoof", true).displayAs("Item Spoof"));
        this.multiplace = register(new BooleanSetting("HypixelTelly/Multi Place", true).displayAs("Multi Place"));
        this.safewalk = register(new BooleanSetting("HypixelTelly/Safewalk", true).displayAs("Safewalk"));
        this.raycast = register(new BooleanSetting("HypixelTelly/Raycast", false).displayAs("Raycast"));
    }

    boolean owns(Setting<?> setting) {
        return settings.contains(setting);
    }

    private <T extends Setting<?>> T register(T setting) {
        settings.add(setting);
        return parent.registerHypixelTellySetting(setting);
    }

    public void enable() {
        if (mc.player != null) {
            this.lastSlot = mc.player.getInventory().getSelectedSlot();
        } else {
            this.lastSlot = -1;
        }
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.timerticks = 0;
        this.towerTick = 0;
        this.towerDelay = 0;
        this.towering = false;
        this.targetFacing = null;
        this.startY = mc.player != null ? Mth.floor(mc.player.getY()) : 256;
        this.shouldKeepY = false;
        this.stage = 0;
        this.groundTicks = 0;
        this.airTicks = 0;
        this.lastYaw = 0.0F;
        this.lastPitch = 0.0F;
        this.last = 0.0F;
        this.shouldJump = false;
        this.pendingBlockData = null;
        this.pendingHitVec = null;
    }

    public void disable() {
        if (mc.player != null && this.lastSlot != -1 && this.itemSpoof.get()) {
            mc.player.getInventory().setSelectedSlot(this.lastSlot);
        }
        this.pendingBlockData = null;
        this.pendingHitVec = null;
        this.rotationTick = 0;
        this.groundTicks = 0;
        this.airTicks = 0;
        this.lastYaw = 0.0F;
        this.lastPitch = 0.0F;
        this.last = 0.0F;
        this.shouldJump = false;
        this.targetFacing = null;
        RotationManager.INSTANCE.releaseRotations(this);
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) return;
        onUpdate();
    }

    @Listen
    private void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (noPlayer()) return;
        if (this.isTowering() && mc.options.keyUp.isDown()) {
            mc.player.setSprinting(true);
        } else if (this.shouldStopSprint()) {
            mc.player.setSprinting(false);
        }
    }

    @Listen
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (noPlayer()) return;
        if (this.shouldJump()) {
            event.setJump(true);
        }
    }

    @Listen
    private void onRespawn(RespawnEvent event) {
        enable();
    }

    @Listen
    private void onWorldChange(WorldChangeEvent event) {
        disable();
    }

    private void onUpdate() {
        this.pendingBlockData = null;
        this.pendingHitVec = null;

        if (this.rotationTick > 0) {
            this.rotationTick--;
        }

        if (mc.player.onGround()) {
            if (this.stage > 0) {
                this.stage--;
            }
            if (this.stage < 0) {
                this.stage++;
            }
            if (this.stage == 0
                    && !this.keepyMode.is(KeepYMode.None)
                    && (!this.keepYOnPress.get()
                    || mc.options.keyUse.isDown()
                    || Speed.INSTANCE.isEnabled())
                    && !mc.options.keyJump.isDown()) {
                this.stage = 1;
            }
            this.startY = this.shouldKeepY ? this.startY : Mth.floor(mc.player.getY());
            this.shouldKeepY = false;
            this.towering = false;
            this.groundTicks++;
            this.airTicks = 0;
            this.last = 0.0F;
            this.shouldJump = !this.shouldJump;
        } else {
            this.groundTicks = 0;
            this.airTicks++;
        }

        ItemStack stack = mc.player.getMainHandItem();
        int count = isBlock(stack) ? stack.getCount() : 0;
        this.blockCount = Math.min(this.blockCount, count);
        if (this.blockCount <= 0) {
            int slot = mc.player.getInventory().getSelectedSlot();
            if (this.blockCount == 0) {
                slot--;
            }
            for (int i = slot; i > slot - 9; i--) {
                int hotbarSlot = (i % 9 + 9) % 9;
                ItemStack candidate = mc.player.getInventory().getItem(hotbarSlot);
                if (isBlock(candidate)) {
                    mc.player.getInventory().setSelectedSlot(hotbarSlot);
                    this.blockCount = candidate.getCount();
                    break;
                }
            }
        }

        float[] currentRotations = RotationManager.INSTANCE.isActive()
                ? new float[]{RotationManager.INSTANCE.getYaw(), RotationManager.INSTANCE.getPitch()}
                : new float[]{mc.player.getYRot(), mc.player.getXRot()};
        float currentYaw = this.getCurrentYaw();
        float yawDiffTo180 = this.wrapAngleDiff(currentYaw - 180.0F, currentRotations[0]);
        float normalYaw = this.wrapAngleDiff(
                currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                currentRotations[0]
        );
        float diagonalYaw = this.isDiagonal(currentYaw) ? yawDiffTo180 : normalYaw;
        if (!this.canRotate) {
            switch (this.rotations.get()) {
                case Backwards -> {
                    if (this.yaw == -180.0F && this.pitch == 0.0F) {
                        this.yaw = this.quantizeAngle(yawDiffTo180);
                        this.pitch = this.quantizeAngle(85.0F);
                    } else {
                        this.yaw = this.quantizeAngle(yawDiffTo180);
                    }
                }
                case Default, Sideways -> {
                    if (this.yaw == -180.0F && this.pitch == 0.0F) {
                        this.yaw = this.quantizeAngle(diagonalYaw);
                        this.pitch = this.quantizeAngle(85.0F);
                    } else {
                        this.yaw = this.quantizeAngle(diagonalYaw);
                    }
                }
                default -> {}
            }
        }

        BlockData blockData = this.getBlockData();
        Vec3 hitVec = null;
        if (blockData != null) {
            double[] x = new double[]{0.5D};
            double[] y = new double[]{0.5D};
            double[] z = new double[]{0.5D};
            switch (blockData.facing()) {
                case UP -> y = new double[]{1.0D};
                case DOWN -> y = new double[]{0.0D};
                case NORTH -> {
                    y = PLACE_OFFSETS;
                    x = PLACE_OFFSETS;
                    z = new double[]{0.0D};
                }
                case SOUTH -> {
                    y = PLACE_OFFSETS;
                    x = PLACE_OFFSETS;
                    z = new double[]{1.0D};
                }
                case EAST -> {
                    y = PLACE_OFFSETS;
                    x = new double[]{1.0D};
                    z = PLACE_OFFSETS;
                }
                case WEST -> {
                    y = PLACE_OFFSETS;
                    x = new double[]{0.0D};
                    z = PLACE_OFFSETS;
                }
            }

            float bestYaw = -180.0F;
            float bestPitch = 0.0F;
            float bestDiff = 0.0F;
            for (double dx : x) {
                for (double dy : y) {
                    for (double dz : z) {
                        double relX = (double) blockData.blockPos().getX() + dx - mc.player.getX();
                        double relY = (double) blockData.blockPos().getY() + dy - mc.player.getEyeY();
                        double relZ = (double) blockData.blockPos().getZ() + dz - mc.player.getZ();
                        float baseYaw = this.wrapAngleDiff(this.yaw, currentRotations[0]);
                        float[] candidateRots = this.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                        BlockHitResult mop = this.rayTrace(candidateRots[0], candidateRots[1]);
                        if (mop != null
                                && mop.getType() == HitResult.Type.BLOCK
                                && mop.getBlockPos().equals(blockData.blockPos())
                                && mop.getDirection() == blockData.facing()) {
                            float totalDiff = Math.abs(candidateRots[0] - baseYaw)
                                    + Math.abs(candidateRots[1] - this.pitch);
                            if ((bestYaw == -180.0F && bestPitch == 0.0F) || totalDiff < bestDiff) {
                                bestYaw = candidateRots[0];
                                bestPitch = candidateRots[1];
                                bestDiff = totalDiff;
                                hitVec = mop.getLocation();
                            }
                        }
                    }
                }
            }
            if (bestYaw != -180.0F || bestPitch != 0.0F) {
                this.yaw = bestYaw;
                this.pitch = bestPitch;
                this.canRotate = true;
            }
        }

        if (this.canRotate && this.isForwardPressed() && Math.abs(Mth.wrapDegrees(yawDiffTo180 - this.yaw)) < 90.0F) {
            switch (this.rotations.get()) {
                case Backwards -> this.yaw = this.quantizeAngle(yawDiffTo180);
                case Sideways -> this.yaw = this.quantizeAngle(normalYaw);
                default -> {}
            }
        }

        boolean rotationReady = true;

        if (!this.rotations.is(RotationsMode.None)) {
            float targetYaw = this.yaw;
            float targetPitch = this.pitch;
            if (this.towering
                    && (mc.player.getDeltaMovement().y > 0.0D
                    || mc.player.getY() > (double) (this.startY + 1))) {
                float yawDiff = Mth.wrapDegrees(this.yaw - currentRotations[0]);
                float tolerance = this.getTolerance();
                if (Math.abs(yawDiff) > tolerance) {
                    float clampedYaw = Mth.clamp(yawDiff, -tolerance, tolerance);
                    targetYaw = this.quantizeAngle(currentRotations[0] + clampedYaw);
                    this.rotationTick = Math.max(this.rotationTick, 1);
                    if (this.raycast.get()) {
                        rotationReady = false;
                    }
                }
            }

            if (this.isTowering()) {
                float moveYaw = mc.player.getYRot();
                targetYaw = this.quantizeAngle(moveYaw);
                targetPitch = this.quantizeAngle((float) ThreadLocalRandom.current().nextDouble(30.0D, 89.0D));
                this.rotationTick = 3;
                this.last = moveYaw;
                this.towering = true;
                if (this.raycast.get()) {
                    rotationReady = false;
                }
                mc.player.setSprinting(true);
            }

            RotationManager.INSTANCE.setRotationsDirect(this, new Rot2f(targetYaw, targetPitch), Priority.High);
        } else {
            RotationManager.INSTANCE.releaseRotations(this);
        }

        boolean extraBlockPriority = this.isExtraBlockPriority();
        if (blockData != null && hitVec != null) {
            this.pendingBlockData = blockData;
            this.pendingHitVec = hitVec;
        }

        boolean canPlace = !this.raycast.get() || rotationReady;
        boolean placed = false;
        boolean hypixelTower = mc.options.keyJump.isDown() && this.towerMode.is(TowerMode.Hypixel);
        if ((canPlace || hypixelTower) && this.pendingBlockData != null && this.pendingHitVec != null && this.rotationTick <= 1 && !extraBlockPriority) {
            placed = this.place(this.pendingBlockData.blockPos(), this.pendingBlockData.facing(), this.pendingHitVec);
            if (this.multiplace.get()) {
                this.placeMultipleBlocks();
            }
        }

        if (this.targetFacing != null) {
            if (canPlace && this.rotationTick <= 1) {
                BlockPos belowPlayer = new BlockPos(Mth.floor(mc.player.getX()), Mth.floor(mc.player.getY()) - 1, Mth.floor(mc.player.getZ()));
                hitVec = this.getHitVec(belowPlayer, this.targetFacing);
                this.place(belowPlayer, this.targetFacing, hitVec);
            }
            this.targetFacing = null;
        } else if (extraBlockPriority) {
            this.shouldKeepY = true;
            blockData = this.getBlockData();
            if (canPlace && blockData != null && this.rotationTick <= 2) {
                hitVec = this.getHitVec(blockData.blockPos(), blockData.facing());
                if (!placed) {
                    this.place(blockData.blockPos(), blockData.facing(), hitVec);
                }
            }
        }

        this.pendingBlockData = null;
        this.pendingHitVec = null;

        if (!mc.player.horizontalCollision
                && mc.player.hurtTime <= 5
                && !mc.player.hasEffect(MobEffects.JUMP_BOOST)
                && mc.options.keyJump.isDown()
                && isHoldingBlock()) {
            if (this.towerMode.is(TowerMode.Hypixel)) {
                if (Math.hypot(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z) <= 0.02D) {
                    if (mc.player.onGround()) {
                        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.41999998688697815D, mc.player.getDeltaMovement().z);
                    } else if (mc.player.getDeltaMovement().y <= 0.0D && mc.player.getDeltaMovement().y >= -0.09D) {
                        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, -0.38D, mc.player.getDeltaMovement().z);
                    }
                }
            }
        }
    }

    private void placeMultipleBlocks() {
        for (int i = 0; i < 3; i++) {
            BlockData blockData = this.getBlockData();
            if (blockData == null) {
                break;
            }

            BlockHitResult mop = this.rayTrace(this.yaw, this.pitch);
            if (mop != null
                    && mop.getType() == HitResult.Type.BLOCK
                    && mop.getBlockPos().equals(blockData.blockPos())
                    && mop.getDirection() == blockData.facing()) {
                this.place(blockData.blockPos(), blockData.facing(), mop.getLocation());
            } else {
                Vec3 hitVec = this.getClickVec(blockData.blockPos(), blockData.facing());
                float[] current = RotationManager.INSTANCE.isActive()
                        ? new float[]{RotationManager.INSTANCE.getYaw(), RotationManager.INSTANCE.getPitch()}
                        : new float[]{mc.player.getYRot(), mc.player.getXRot()};
                double rdx = hitVec.x - mc.player.getX();
                double rdy = hitVec.y - mc.player.getEyeY();
                double rdz = hitVec.z - mc.player.getZ();
                float[] rotations = this.getRotationsTo(rdx, rdy, rdz, current[0], current[1]);
                if (!(Math.abs(rotations[0] - this.yaw) < 120.0F)
                        || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) {
                    break;
                }

                mop = this.rayTrace(rotations[0], rotations[1]);
                if (mop == null
                        || mop.getType() != HitResult.Type.BLOCK
                        || !mop.getBlockPos().equals(blockData.blockPos())
                        || mop.getDirection() != blockData.facing()) {
                    break;
                }
                this.place(blockData.blockPos(), blockData.facing(), mop.getLocation());
            }
        }
    }

    private boolean place(BlockPos blockPos, Direction direction, Vec3 vec3d) {
        if (isHoldingBlock() && this.blockCount > 0) {
            BlockHitResult hitResult = new BlockHitResult(vec3d, direction, blockPos, false);
            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            if (result.consumesAction()) {
                this.blockCount--;
                if (this.swing.get()) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                } else if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                }
                return true;
            }
        }
        return false;
    }

    private boolean isHoldingBlock() {
        return isBlock(mc.player.getMainHandItem());
    }

    private boolean isBlock(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        if (itemStack.getItem() instanceof BlockItem blockItem) {
            return isPlaceable(blockItem.getBlock());
        }
        return false;
    }

    private boolean isPlaceable(Block block) {
        if (block == Blocks.AIR || block instanceof BaseEntityBlock || block instanceof FallingBlock) return false;
        BlockState state = block.defaultBlockState();
        return state.isSolid() || state.isSolidRender() || state.isCollisionShapeFullBlock(mc.level, BlockPos.ZERO) || block instanceof SlabBlock || block instanceof StairBlock;
    }

    private boolean isReplaceable(BlockPos blockPos) {
        return mc.level.getBlockState(blockPos).canBeReplaced();
    }

    private boolean isInteractable(BlockPos blockPos) {
        BlockState state = mc.level.getBlockState(blockPos);
        return state.getBlock() instanceof BaseEntityBlock
                || state.getMenuProvider(mc.level, blockPos) != null;
    }

    private Vec3 getClickVec(BlockPos blockPos, Direction direction) {
        return Vec3.atCenterOf(blockPos).add(
                direction.getStepX() * 0.5D,
                direction.getStepY() * 0.5D,
                direction.getStepZ() * 0.5D
        );
    }

    private Vec3 getHitVec(BlockPos blockPos, Direction direction) {
        BlockHitResult hitResult = this.rayTrace(this.yaw, this.pitch);
        if (hitResult != null
                && hitResult.getType() == HitResult.Type.BLOCK
                && hitResult.getBlockPos().equals(blockPos)
                && hitResult.getDirection() == direction) {
            return hitResult.getLocation();
        }
        return this.getClickVec(blockPos, direction);
    }

    private BlockHitResult rayTrace(float yaw, float pitch) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = getVectorForRotation(yaw, pitch);
        double range = mc.player.blockInteractionRange();
        Vec3 endPos = eyePos.add(lookVec.scale(range));
        HitResult result = mc.level.clip(new ClipContext(
                eyePos,
                endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return result instanceof BlockHitResult blockHitResult ? blockHitResult : null;
    }

    private BlockData getBlockData() {
        int currentY = Mth.floor(mc.player.getY());
        BlockPos targetPos = new BlockPos(
                Mth.floor(mc.player.getX()),
                (this.stage != 0 && !this.shouldKeepY
                        ? Math.min(currentY, this.startY)
                        : currentY) - 1,
                Mth.floor(mc.player.getZ())
        );
        if (!this.isReplaceable(targetPos)) {
            return null;
        } else {
            ArrayList<BlockPos> positions = new ArrayList<>();
            double rangeSq = mc.player.blockInteractionRange() * mc.player.blockInteractionRange();
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 0; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos pos = targetPos.offset(x, y, z);
                        if (!this.isReplaceable(pos)
                                && !this.isInteractable(pos)
                                && !(mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > rangeSq)
                                && (this.stage == 0
                                || this.shouldKeepY
                                || pos.getY() < this.startY)) {
                            for (Direction facing : Direction.values()) {
                                if (facing != Direction.DOWN) {
                                    BlockPos blockPos = pos.relative(facing);
                                    if (this.isReplaceable(blockPos)) {
                                        positions.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (positions.isEmpty()) {
                return null;
            } else {
                positions.sort(
                        Comparator.comparingDouble(
                                o -> o.distSqr(targetPos)
                        )
                );
                BlockPos blockPos = positions.get(0);
                Direction facing = this.getBestFacing(blockPos, targetPos);
                return facing == null ? null : new BlockData(blockPos, facing);
            }
        }
    }

    private Direction getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0D;
        Direction direction = null;
        for (Direction facing : Direction.values()) {
            if (facing != Direction.DOWN) {
                BlockPos pos = blockPos1.relative(facing);
                if (pos.getY() <= blockPos3.getY() && this.isReplaceable(pos)) {
                    double distance = pos.distSqr(blockPos3);
                    if (direction == null || distance < offset || (distance == offset && facing == Direction.UP)) {
                        offset = distance;
                        direction = facing;
                    }
                }
            }
        }
        return direction;
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private float adjustYaw(float yaw, float forward, float strafe) {
        if (forward < 0.0F) {
            yaw += 180.0F;
        }
        if (strafe != 0.0F) {
            float multiplier = forward == 0.0F ? 1.0F : 0.5F * Math.signum(forward);
            yaw += -90.0F * multiplier * Math.signum(strafe);
        }
        return Mth.wrapDegrees(yaw);
    }

    private boolean isForwardPressed() {
        boolean forward = mc.options.keyUp.isDown();
        boolean back = mc.options.keyDown.isDown();
        if (forward != back) {
            return true;
        }
        return mc.options.keyLeft.isDown() != mc.options.keyRight.isDown();
    }

    private int getForwardValue() {
        int forward = 0;
        if (mc.options.keyUp.isDown()) {
            forward++;
        }
        if (mc.options.keyDown.isDown()) {
            forward--;
        }
        return forward;
    }

    private int getLeftValue() {
        int left = 0;
        if (mc.options.keyLeft.isDown()) {
            left++;
        }
        if (mc.options.keyRight.isDown()) {
            left--;
        }
        return left;
    }

    private float getCurrentYaw() {
        return this.adjustYaw(
                mc.player.getYRot(),
                (float) this.getForwardValue(),
                (float) this.getLeftValue()
        );
    }

    private float wrapAngleDiff(float yaw, float previousYaw) {
        return previousYaw + Mth.wrapDegrees(yaw - previousYaw);
    }

    private float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % 0.0096D);
    }

    private float getTolerance() {
        float firstRotate = (float) this.firstMaxRotate.get().doubleValue();
        if (mc.options.keyJump.isDown()) {
            if (this.towerMode.is(TowerMode.Hypixel)) {
                switch (rotationTick) {
                    case 2 -> { return (float) ThreadLocalRandom.current().nextDouble(40.0D, 45.0D); }
                    case 1 -> { return (float) ThreadLocalRandom.current().nextDouble(60.0D, 65.0D); }
                    case 0 -> { return (float) ThreadLocalRandom.current().nextDouble(30.0D, 35.0D); }
                }
            }
        } else {
            if (this.keepyMode.is(KeepYMode.Hypixel)) {
                switch (rotationTick) {
                    case 2 -> { return (float) ThreadLocalRandom.current().nextDouble(40.0D, 45.0D); }
                    case 1 -> { return (float) ThreadLocalRandom.current().nextDouble(30.0D, 35.0D); }
                    case 0 -> { return (float) ThreadLocalRandom.current().nextDouble(30.0D, 35.0D); }
                }
            }
        }

        return this.rotationTick >= 2
                ? (float) ThreadLocalRandom.current().nextDouble(firstRotate, firstRotate + 5.0D)
                : (float) ThreadLocalRandom.current().nextDouble(30.0D, 35.0D);
    }

    private boolean isTowering() {
        boolean keepY = this.keepyMode.is(KeepYMode.Telly) || this.keepyMode.is(KeepYMode.Hypixel);
        boolean tower = this.towerMode.is(TowerMode.Telly) || this.towerMode.is(TowerMode.Hypixel);
        if (!mc.options.keyUp.isDown() || this.hasCollisionAbove()) {
            return false;
        }

        if (mc.player.hasEffect(MobEffects.JUMP_BOOST)) {
            return false;
        }

        return this.groundTicks >= this.stopTicks.get() + 1
                && ((keepY && this.stage > 0) || (tower && mc.options.keyJump.isDown()));
    }

    private boolean shouldStopSprint() {
        if (this.isTowering()) {
            return false;
        }
        boolean stageMode = this.keepyMode.is(KeepYMode.Vanilla) || this.keepyMode.is(KeepYMode.Extra);
        return (!stageMode || this.stage <= 0) && this.sprintMode.is(SprintMode.None);
    }

    public boolean shouldJump() {
        if (this.stage <= 0
                || !mc.options.keyUp.isDown()
                || Speed.INSTANCE.isEnabled()
                || mc.options.keyJump.isDown()) {
            return false;
        }
        if (mc.player.hasEffect(MobEffects.JUMP_BOOST)) {
            return false;
        }
        return this.groundTicks >= this.stopTicks.get() + 1;
    }

    private boolean isExtraBlockPriority() {
        if (!this.keepyMode.is(KeepYMode.Extra)) {
            return false;
        }
        if (this.stage <= 0 || mc.player.onGround()) {
            return false;
        }
        if (mc.player.getDeltaMovement().y > 0.0D) {
            return false;
        }
        int nextBlockY = Mth.floor(mc.player.getY() + mc.player.getDeltaMovement().y);
        double posY = mc.player.getY();
        return nextBlockY <= this.startY + 1
                && posY > (double) this.startY + 0.85D
                && posY <= (double) this.startY + 1.15D;
    }

    private boolean hasCollisionBelow() {
        return !mc.level.noCollision(
                mc.player,
                mc.player.getBoundingBox().move(0.0D, -1.0D, 0.0D)
        );
    }

    private boolean hasCollisionAbove() {
        return !mc.level.noCollision(
                mc.player,
                mc.player.getBoundingBox().move(0.0D, 1.0D, 0.0D)
        );
    }

    private boolean canMove(double motionX, double motionZ, double motionY) {
        return mc.level.noCollision(
                mc.player,
                mc.player.getBoundingBox().move(motionX, motionY, motionZ)
        );
    }

    public boolean shouldSafeWalk() {
        if (!safewalk.get() || mc.player == null || mc.level == null) {
            return false;
        }
        return mc.player.onGround()
                && mc.player.getDeltaMovement().y <= 0.0D
                && canMove(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z, -1.0D);
    }

    public float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return getRotationsRelative(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0F, 0.0F);
    }

    public float[] getRotationsRelative(
            double targetX, double targetY, double targetZ,
            float currentYaw, float currentPitch, float maxAngle, float smoothFactor
    ) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = Mth.wrapDegrees(
                (float) (Math.atan2(targetZ, targetX) * 180.0D / Math.PI) - 90.0F - currentYaw
        );
        float pitchDelta = Mth.wrapDegrees(
                (float) (-Math.atan2(targetY, horizontalDistance) * 180.0D / Math.PI) - currentPitch
        );
        yawDelta = Math.abs(yawDelta) <= 1.0F ? 0.0F : smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0F ? 0.0F : smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{quantizeAngle(currentYaw + yawDelta), quantizeAngle(currentPitch + pitchDelta)};
    }

    public float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0F, Math.min(180.0F, maxAngle));
        if (angle > maxAngle) {
            return maxAngle;
        }
        if (angle < -maxAngle) {
            return -maxAngle;
        }
        return angle;
    }

    public float smoothAngle(float angle, float smoothFactor) {
        float rand = (float) ThreadLocalRandom.current().nextDouble(-0.1D, 0.1D);
        return angle * (0.5F + 0.5F * (1.0F - Math.max(0.0F, Math.min(1.0F, smoothFactor + rand))));
    }

    private static Vec3 getVectorForRotation(float yaw, float pitch) {
        float yawRad = yaw * ((float) Math.PI / 180.0F);
        float pitchRad = pitch * ((float) Math.PI / 180.0F);
        float cosYaw = Mth.cos(-yawRad - (float) Math.PI);
        float sinYaw = Mth.sin(-yawRad - (float) Math.PI);
        float cosPitch = -Mth.cos(-pitchRad);
        float sinPitch = Mth.sin(-pitchRad);
        return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    public int getBlockCount() {
        if (noPlayer()) return 0;
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isBlock(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public ItemStack getPlacementStack() {
        if (noPlayer()) return ItemStack.EMPTY;
        ItemStack stack = mc.player.getMainHandItem();
        if (isBlock(stack)) return stack;
        for (int i = 0; i < 9; i++) {
            ItemStack candidate = mc.player.getInventory().getItem(i);
            if (isBlock(candidate)) return candidate;
        }
        return ItemStack.EMPTY;
    }

    private static boolean noPlayer() {
        return mc.player == null || mc.level == null || mc.gameMode == null;
    }

    public record BlockData(BlockPos blockPos, Direction facing) {
    }
}
