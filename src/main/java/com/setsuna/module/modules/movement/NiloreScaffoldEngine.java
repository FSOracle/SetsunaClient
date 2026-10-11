package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.RaytraceUtils;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Direct runtime adaptation of Nilore's Scaffold module. */
final class NiloreScaffoldEngine {

    enum BridgeMode {
        Normal,
        TellyBridge,
        KeepY
    }

    enum SwingMode {
        Both,
        Server
    }

    enum BlockCounterStyle {
        Amunix,
        Modern,
        Naven
    }

    enum SwitchMode {
        Normal,
        Hotbar,
        Full
    }

    private record PlacementTarget(BlockPos position, Direction facing) {
    }

    private static final Minecraft mc = Minecraft.getInstance();

    private final Scaffold parent;
    private final Set<Setting<?>> settings = Collections.newSetFromMap(new IdentityHashMap<>());

    private final EnumSetting<BridgeMode> mode;
    private final DoubleSetting tellyAirTicks;
    private final IntSetting tellyPlaceDelay;
    private final BooleanSetting eagle;
    private final BooleanSetting renderItemSpoof;
    private final BooleanSetting clutch;
    private final EnumSetting<SwingMode> swingMode;
    private final BooleanSetting blockCounter;
    private final EnumSetting<BlockCounterStyle> blockCounterStyle;
    private final BooleanSetting onTickRot;
    private final DoubleSetting rotationSpeed;
    private final BooleanSetting syncRotSpeed;
    private final DoubleSetting turnSpeed;
    private final DoubleSetting returnSpeed;
    private final EnumSetting<SwitchMode> switchMode;
    private final BooleanSetting logging;

    private Rot2f rotations = new Rot2f(0.0F, 0.0F);
    private Rot2f lastRotations = new Rot2f(0.0F, 0.0F);
    private int targetYLevel = -1;
    private int velocityDelay;
    private int originalSlot;
    private int alternateSlot = -1;
    private int placementSlot = -1;
    private boolean slotActive;
    private int slotActiveTick;
    private int slotTicks;
    private PlacementTarget currentPlacement;
    private int airTicks;
    private int rotationDelay;
    private boolean canBuildNow;
    private BlockPos lastRescuePosition;
    private int tellyPlaceDelayTimer;

    NiloreScaffoldEngine(Scaffold parent) {
        this.parent = parent;
        mode = setting(new EnumSetting<>("Heypixel/Mode", BridgeMode.Normal).displayAs("Mode"));
        tellyAirTicks = setting(new DoubleSetting("Heypixel/AirTicks", 1.0D, 0.0D, 3.0D, 0.1D)
                .visibleWhen(() -> mode.is(BridgeMode.TellyBridge)).displayAs("AirTicks"));
        tellyPlaceDelay = setting(new IntSetting("Heypixel/PlaceDelay", 0, 0, 20, 1)
                .visibleWhen(() -> mode.is(BridgeMode.TellyBridge)).displayAs("PlaceDelay"));
        eagle = setting(new BooleanSetting("Heypixel/Eagle", true)
                .visibleWhen(() -> mode.is(BridgeMode.Normal)).displayAs("Eagle"));
        renderItemSpoof = setting(new BooleanSetting("Heypixel/Render Item Spoof", true)
                .displayAs("Render Item Spoof"));
        clutch = setting(new BooleanSetting("Heypixel/Clutch", true).displayAs("Clutch"));
        swingMode = setting(new EnumSetting<>("Heypixel/Swing", SwingMode.Both).displayAs("Swing"));
        blockCounter = setting(new BooleanSetting("Heypixel/Block Counter", true).displayAs("Block Counter"));
        blockCounterStyle = setting(new EnumSetting<>(
                "Heypixel/Block Counter Style", BlockCounterStyle.Modern).displayAs("Block Counter Style"));
        onTickRot = setting(new BooleanSetting("Heypixel/OnTickRot", false).displayAs("OnTickRot"));
        syncRotSpeed = setting(new BooleanSetting("Heypixel/Sync RotSpeed", false).displayAs("Sync RotSpeed"));
        rotationSpeed = setting(new DoubleSetting("Heypixel/Rotation Speed", 180.0D, 0.0D, 360.0D, 5.0D)
                .visibleWhen(() -> !syncRotSpeed.get()).displayAs("Rotation Speed"));
        turnSpeed = setting(new DoubleSetting("Heypixel/Turn Speed", 75.0D, 0.0D, 360.0D, 5.0D)
                .visibleWhen(syncRotSpeed::get).displayAs("Turn Speed"));
        returnSpeed = setting(new DoubleSetting("Heypixel/Return Speed", 120.0D, 0.0D, 360.0D, 5.0D)
                .visibleWhen(syncRotSpeed::get).displayAs("Return Speed"));
        switchMode = setting(new EnumSetting<>("Heypixel/Switch Mode", SwitchMode.Hotbar).displayAs("Switch Mode"));
        logging = setting(new BooleanSetting("Heypixel/Log", false).displayAs("Log"));
    }

    private <T extends Setting<?>> T setting(T setting) {
        settings.add(setting);
        return parent.registerNiloreSetting(setting);
    }

    boolean owns(Setting<?> setting) {
        return settings.contains(setting);
    }

    void enable() {
        resetRuntime();
        if (noPlayer()) {
            return;
        }
        originalSlot = mc.player.getInventory().getSelectedSlot();
        alternateSlot = originalSlot;
        rotations = new Rot2f(mc.player.getYRot() - 180.0F, mc.player.getXRot());
        lastRotations = new Rot2f(mc.player.yRotO - 180.0F, mc.player.xRotO);
        targetYLevel = 10000;
        canBuildNow = true;
    }

    void disable() {
        RotationManager.INSTANCE.releaseRotations(this);
        restoreSlot();
        restorePhysicalKeys();
        resetRuntime();
    }

    String info() {
        return switch (mode.get()) {
            case Normal -> "Normal";
            case TellyBridge -> "Telly Bridge";
            case KeepY -> "Keep Y";
        };
    }

    int getBlockCount() {
        if (noPlayer() || !blockCounter.get()) {
            return 0;
        }
        int count = 0;
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int i = 0; i < size; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isPlaceable(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    ItemStack getPlacementStack() {
        if (noPlayer()) {
            return ItemStack.EMPTY;
        }
        int slot = placementSlot >= 0 ? placementSlot : findPlaceableSlot();
        return slot < 0 ? ItemStack.EMPTY : mc.player.getInventory().getItem(slot);
    }

    int modifyServerSelectedSlot(int original) {
        return slotActive && placementSlot >= 0 ? placementSlot : original;
    }

    ItemStack modifyRenderedMainHandStack(ItemStack original) {
        if (noPlayer() || alternateSlot < 0) {
            return original;
        }
        boolean silent = slotActive && !switchMode.is(SwitchMode.Normal);
        return silent || renderItemSpoof.get()
                ? mc.player.getInventory().getItem(alternateSlot)
                : original;
    }

    @Listen(priority = 200)
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            RotationManager.INSTANCE.releaseRotations(this);
            return;
        }
        tickSlotState();
        if (velocityDelay > 0) {
            velocityDelay--;
        }
        if (mc.player.onGround() && velocityDelay <= 30) {
            velocityDelay = 0;
        }

        int placeableSlot = findPlaceableSlot();
        selectPlacementSlot(placeableSlot);
        updateTargetY();
        findPlacement();
        updateClutchState(placeableSlot);
        updateRotation();
        placeBlock(placeableSlot);
        lastRotations = rotations.copy();
    }

    @Listen
    private void onTickPost(PlayerTickEvent.Post event) {
        if (noPlayer()) {
            return;
        }
        airTicks = mc.player.onGround() ? 0 : airTicks + 1;
    }

    @Listen(priority = 250)
    private void onMovementInput(KeyboardInputEvent event) {
        if (noPlayer()) {
            return;
        }
        boolean moving = event.getForward() != 0.0F || event.getStrafe() != 0.0F;
        if (!canBuildNow && currentPlacement != null && rotationDelay > 0) {
            event.setJump(false);
        } else if ((mode.is(BridgeMode.TellyBridge) || mode.is(BridgeMode.KeepY)) && moving) {
            event.setJump(true);
        }
        if (mode.is(BridgeMode.Normal) && eagle.get() && mc.player.onGround()
                && isOnBlockEdge(0.3F)) {
            event.setSneak(true);
        }
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (noPlayer()) {
            return;
        }
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket motion
                && motion.id() == mc.player.getId()) {
            Vec3 movement = motion.movement();
            if (Math.hypot(movement.x, movement.z) >= 1.5D) {
                velocityDelay = 60;
            }
        }
    }

    @Listen
    private void onRespawn(RespawnEvent event) {
        enable();
    }

    private void tickSlotState() {
        if (slotActive && slotActiveTick != slotTicks) {
            mc.player.getInventory().setSelectedSlot(alternateSlot);
            slotActive = false;
        }
        slotTicks++;
    }

    private void selectPlacementSlot(int slot) {
        placementSlot = slot;
        if (slot < 0 || slot == mc.player.getInventory().getSelectedSlot()) {
            return;
        }
        if (!slotActive) {
            alternateSlot = mc.player.getInventory().getSelectedSlot();
        }
        mc.player.getInventory().setSelectedSlot(slot);
        slotActive = true;
        slotActiveTick = slotTicks;
    }

    private void updateTargetY() {
        boolean jumpHeld = mc.options.keyJump.isDown();
        if (targetYLevel == -1
                || targetYLevel > Mth.floor(mc.player.getY()) - 1
                || mc.player.onGround()
                || !isMoving()
                || jumpHeld
                || mode.is(BridgeMode.Normal)) {
            targetYLevel = Mth.floor(mc.player.getY()) - 1;
        }
    }

    private void findPlacement() {
        Vec3 eye = mc.player.getEyePosition();
        if (!canBuildNow) {
            eye = eye.add(mc.player.getDeltaMovement().scale(2.0D));
        }
        if (clutch.get() && mc.player.getDeltaMovement().y < 0.01D) {
            eye = new Vec3(eye.x, Math.max(predictY(2) + mc.player.getEyeHeight(), eye.y), eye.z);
        }
        currentPlacement = null;
        BlockPos belowFeet = BlockPos.containing(eye.x, targetYLevel + 0.1D, eye.z);
        if (mc.level.getBlockState(belowFeet).entityCanStandOn(mc.level, belowFeet, mc.player)) {
            return;
        }
        if (findPlaceableNeighbour(eye, belowFeet)) {
            return;
        }
        int feetX = belowFeet.getX();
        int feetZ = belowFeet.getZ();
        for (int radius = 1; radius <= 6; radius++) {
            if (findPlaceableNeighbour(eye, new BlockPos(feetX, targetYLevel - radius, feetZ))) {
                return;
            }
            for (int x = 1; x <= radius; x++) {
                for (int z = 0; z <= radius - x; z++) {
                    int yOffset = radius - x - z;
                    for (int signX = 0; signX <= 1; signX++) {
                        for (int signZ = 0; signZ <= 1; signZ++) {
                            BlockPos test = new BlockPos(
                                    feetX + (signX == 0 ? x : -x),
                                    targetYLevel - yOffset,
                                    feetZ + (signZ == 0 ? z : -z));
                            if (findPlaceableNeighbour(eye, test)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean findPlaceableNeighbour(Vec3 from, BlockPos emptyPosition) {
        if (!mc.level.getBlockState(emptyPosition).isAir()) {
            return false;
        }
        Vec3 center = emptyPosition.getCenter();
        for (Direction direction : Direction.values()) {
            BlockPos support = emptyPosition.relative(direction);
            if (!mc.level.getBlockState(support)
                    .entityCanStandOnFace(mc.level, support, mc.player, direction)) {
                continue;
            }
            Vec3 face = center.add(
                    direction.getStepX() * 0.5D,
                    direction.getStepY() * 0.5D,
                    direction.getStepZ() * 0.5D);
            Vec3 delta = face.subtract(from);
            if (delta.lengthSqr() <= 20.25D
                    && delta.normalize().dot(Vec3.atLowerCornerOf(direction.getUnitVec3i()).normalize()) >= 0.0D) {
                currentPlacement = new PlacementTarget(support.immutable(), direction.getOpposite());
                return true;
            }
        }
        return false;
    }

    private void updateClutchState(int placeableSlot) {
        if (currentPlacement == null) {
            tellyPlaceDelayTimer = 0;
        } else {
            tellyPlaceDelayTimer++;
        }
        canBuildNow = true;
        if (currentPlacement != null && placeableSlot >= 0
                && clutch.get() && mc.player.getDeltaMovement().y < -0.1D
                && currentPlacement.position().getY() > predictY(2)) {
            canBuildNow = false;
        }
        if (mc.player.onGround()) {
            canBuildNow = true;
        }
    }

    private void updateRotation() {
        if (currentPlacement == null) {
            rotationDelay = 0;
            return;
        }
        if (clutch.get() && (!canBuildNow || velocityDelay > 0) && rotationDelay <= 8) {
            if (onTickRot.get() || !canBuildNow) {
                rotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            } else {
                Vec3 predictedEye = mc.player.getEyePosition().add(mc.player.getDeltaMovement());
                rotations = RotationUtils.calculate(predictedEye, faceCenter(currentPlacement));
                RotationManager.INSTANCE.setRotationsDirect(this, rotations, Priority.High);
            }
            rotationDelay++;
            return;
        }

        boolean wasRescuing = rotationDelay > 0;
        rotationDelay = 0;
        if (wasRescuing) {
            sendPositionRotation(mc.player.getYRot() + jitter(), mc.player.getXRot() + jitter());
        }
        if (onTickRot.get()) {
            rotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            RotationManager.INSTANCE.releaseRotations(this);
        } else {
            calculateTargetRotation();
        }
    }

    private void calculateTargetRotation() {
        if (currentPlacement == null) {
            return;
        }
        float movementYaw = mc.player.getYRot();
        if (mc.options.keyDown.isDown()) {
            movementYaw += 180.0F;
            if (mc.options.keyLeft.isDown()) movementYaw += 45.0F;
            else if (mc.options.keyRight.isDown()) movementYaw -= 45.0F;
        } else if (mc.options.keyUp.isDown()) {
            if (mc.options.keyLeft.isDown()) movementYaw -= 45.0F;
            else if (mc.options.keyRight.isDown()) movementYaw += 45.0F;
        } else if (mc.options.keyRight.isDown()) {
            movementYaw += 90.0F;
        } else if (mc.options.keyLeft.isDown()) {
            movementYaw -= 90.0F;
        }

        float yaw = movementYaw - 180.0F;
        float pitch = 75.5F;
        Rot2f desired = new Rot2f(yaw, pitch);
        if (!isRotationValid(desired)) {
            Float foundPitch = findValidPitch(yaw);
            if (foundPitch != null) {
                desired = new Rot2f(yaw, foundPitch);
            } else {
                Rot2f found = findOptimalRotation(yaw);
                if (found != null) {
                    desired = found;
                }
            }
        }
        applyRotationWithSpeed(desired);
    }

    private Float findValidPitch(float yaw) {
        for (float pitch = Math.max(rotations.getPitch() - 30.0F, -90.0F);
             pitch <= Math.min(rotations.getPitch() + 20.0F, 90.0F);
             pitch += 0.3F) {
            if (isRotationValid(new Rot2f(yaw, pitch))) {
                return pitch;
            }
        }
        return null;
    }

    private Rot2f findOptimalRotation(float baseYaw) {
        for (float yawOffset = 0.0F; yawOffset < 180.0F; yawOffset += 1.0F) {
            for (float pitchOffset = 0.0F; pitchOffset < 25.0F; pitchOffset += 1.0F) {
                for (int sign = 0; sign < 2; sign++) {
                    float pitch = Mth.clamp(
                            rotations.getPitch() - pitchOffset * (sign == 0 ? 1.0F : -1.0F),
                            -90.0F, 90.0F);
                    Rot2f positive = new Rot2f(baseYaw + yawOffset, pitch);
                    if (isRotationValid(positive)) return positive;
                    Rot2f negative = new Rot2f(baseYaw - yawOffset, pitch);
                    if (isRotationValid(negative)) return negative;
                }
            }
        }
        return null;
    }

    private void applyRotationWithSpeed(Rot2f desired) {
        double speed = rotationSpeed.get();
        if (syncRotSpeed.get()) {
            float currentDistance = Math.abs(Mth.wrapDegrees(rotations.getYaw() - mc.player.getYRot()));
            float nextDistance = Math.abs(Mth.wrapDegrees(desired.getYaw() - mc.player.getYRot()));
            speed = nextDistance < currentDistance ? returnSpeed.get() : turnSpeed.get();
        }
        if (speed < 360.0D) {
            float yaw = rotations.getYaw() + Mth.clamp(
                    Mth.wrapDegrees(desired.getYaw() - rotations.getYaw()), (float) -speed, (float) speed);
            float pitch = rotations.getPitch() + Mth.clamp(
                    desired.getPitch() - rotations.getPitch(), (float) -speed, (float) speed);
            desired = new Rot2f(yaw, pitch);
        }
        rotations = desired;
        RotationManager.INSTANCE.setRotationsDirect(this, rotations, Priority.High);
    }

    private void placeBlock(int placeableSlot) {
        if (currentPlacement == null || placeableSlot < 0 || mc.gameMode == null || mc.screen != null) {
            return;
        }
        boolean clutchRescue = clutch.get() && !canBuildNow;
        if (shouldDelayTellyPlacement(
                mode.is(BridgeMode.TellyBridge), clutchRescue,
                airTicks, tellyAirTicks.get().intValue(),
                tellyPlaceDelayTimer, tellyPlaceDelay.get())) {
            return;
        }
        if (!canBuildNow && !isPlacementReachable()) {
            return;
        }

        if (onTickRot.get() || !canBuildNow && rotationDelay > 0) {
            placeWithRotationPackets(clutchRescue);
            return;
        }
        if (rotationDelay <= 0 && !mode.is(BridgeMode.TellyBridge) && !isRotationValid(rotations)) {
            return;
        }
        placeNormally();
    }

    private void placeWithRotationPackets(boolean clutchRescue) {
        if (!isPlaceable(mc.player.getMainHandItem())) {
            return;
        }
        Direction facing = currentPlacement.facing();
        if (shouldBlockAirborneUpPlacement(
                facing == Direction.UP, mc.player.onGround(), isMoving(),
                mc.options.keyJump.isDown(), mode.is(BridgeMode.Normal), clutchRescue)) {
            return;
        }
        Rot2f aimed = RotationUtils.calculate(mc.player.getEyePosition(), faceCenter(currentPlacement));
        if (!isRotationValid(aimed)) {
            return;
        }
        float originalYaw = mc.player.getYRot();
        float originalPitch = mc.player.getXRot();
        sendPositionRotation(aimed.getYaw() + jitter(), aimed.getPitch() + jitter());
        usePlacement();
        sendPositionRotation(originalYaw + jitter(), originalPitch + jitter());
    }

    static boolean shouldDelayTellyPlacement(
            boolean tellyMode, boolean clutchRescue,
            int airTicks, int requiredAirTicks,
            int placeDelayTimer, int requiredPlaceDelay) {
        return tellyMode && !clutchRescue
                && (airTicks < requiredAirTicks
                || requiredPlaceDelay > 0 && placeDelayTimer < requiredPlaceDelay);
    }

    static boolean shouldBlockAirborneUpPlacement(
            boolean upwardFace, boolean onGround, boolean moving,
            boolean jumpHeld, boolean normalMode, boolean clutchRescue) {
        return upwardFace && !onGround && moving && !jumpHeld
                && !normalMode && !clutchRescue;
    }

    private void placeNormally() {
        if (!isPlaceable(mc.player.getMainHandItem()) || !shouldBuild() || !isRotationValid(rotations)) {
            return;
        }
        if (!canBuildNow && clutch.get() && !currentPlacement.position().equals(lastRescuePosition)) {
            lastRescuePosition = currentPlacement.position();
            Rot2f rescue = RotationUtils.calculate(
                    mc.player.getEyePosition().add(mc.player.getDeltaMovement()), faceCenter(currentPlacement));
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                    rescue.getYaw() + 720.0F,
                    rescue.getPitch(),
                    mc.player.onGround(),
                    mc.player.horizontalCollision));
        }
        usePlacement();
    }

    private void usePlacement() {
        BlockHitResult hit = new BlockHitResult(
                faceCenter(currentPlacement), currentPlacement.facing(),
                currentPlacement.position(), false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) {
            if (!swingMode.is(SwingMode.Server)) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            currentPlacement = null;
            tellyPlaceDelayTimer = 0;
        }
    }

    private boolean isPlacementReachable() {
        if (currentPlacement == null) {
            return false;
        }
        Vec3 face = faceCenter(currentPlacement);
        Vec3 delta = face.subtract(mc.player.getEyePosition());
        return delta.lengthSqr() <= 20.25D
                && delta.normalize().dot(Vec3.atLowerCornerOf(
                currentPlacement.facing().getUnitVec3i()).scale(-1.0D).normalize()) >= 0.0D;
    }

    private boolean isRotationValid(Rot2f rotation) {
        return currentPlacement != null && rotation != null
                && RaytraceUtils.overBlock(
                rotation, currentPlacement.position(), currentPlacement.facing());
    }

    private boolean shouldBuild() {
        BlockPos below = BlockPos.containing(
                mc.player.getX(), mc.player.getY() - 0.5D, mc.player.getZ());
        return mc.level.isEmptyBlock(below) && isPlaceable(mc.player.getMainHandItem());
    }

    private int findPlaceableSlot() {
        if (noPlayer()) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (isPlaceable(mc.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPlaceable(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) {
            return false;
        }
        String name = stack.getHoverName().getString();
        if (name.contains("Click") || name.contains("\u70b9\u51fb")) {
            return false;
        }
        Block block = item.getBlock();
        return !(block instanceof FlowerBlock)
                && !(block instanceof BushBlock)
                && !(block instanceof NetherFungusBlock)
                && !(block instanceof CropBlock)
                && !(block instanceof SlabBlock);
    }

    private boolean isOnBlockEdge(float inflate) {
        double halfWidth = mc.player.getBbWidth() / 2.0D;
        if (inflate >= halfWidth) {
            return true;
        }
        double margin = Math.min(inflate, halfWidth * 0.99D);
        Vec3 position = mc.player.position();
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                BlockPos corner = BlockPos.containing(
                        position.x + x * margin,
                        position.y - 1.0D,
                        position.z + z * margin);
                if (!mc.level.getBlockState(corner).entityCanStandOn(mc.level, corner, mc.player)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Vec3 faceCenter(PlacementTarget placement) {
        return placement.position().getCenter().add(
                placement.facing().getStepX() * 0.5D,
                placement.facing().getStepY() * 0.5D,
                placement.facing().getStepZ() * 0.5D);
    }

    private double predictY(int ticks) {
        double y = mc.player.getY();
        double velocity = mc.player.getDeltaMovement().y;
        for (int i = 0; i < ticks; i++) {
            y += velocity;
            velocity = (velocity - 0.08D) * 0.98D;
        }
        return y;
    }

    private void sendPositionRotation(float yaw, float pitch) {
        if (mc.getConnection() == null) {
            return;
        }
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
    }

    private void restoreSlot() {
        if (!noPlayer() && alternateSlot >= 0 && alternateSlot < 9) {
            mc.player.getInventory().setSelectedSlot(alternateSlot);
        }
        slotActive = false;
        placementSlot = -1;
    }

    private void restorePhysicalKeys() {
        if (mc.options == null || mc.getWindow() == null) {
            return;
        }
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    private void resetRuntime() {
        currentPlacement = null;
        targetYLevel = -1;
        velocityDelay = 0;
        originalSlot = -1;
        alternateSlot = -1;
        placementSlot = -1;
        slotActive = false;
        slotActiveTick = 0;
        slotTicks = 0;
        airTicks = 0;
        rotationDelay = 0;
        canBuildNow = true;
        lastRescuePosition = null;
        tellyPlaceDelayTimer = 0;
    }

    private boolean isMoving() {
        return mc.player != null && mc.player.input != null
                && (mc.player.input.getMoveVector().x != 0.0F
                || mc.player.input.getMoveVector().y != 0.0F);
    }

    private boolean noPlayer() {
        return mc.player == null || mc.level == null;
    }

    private static float jitter() {
        float value = (float) (Math.random() * 0.04D + 0.03D);
        return Math.random() > 0.5D ? value : -value;
    }
}
