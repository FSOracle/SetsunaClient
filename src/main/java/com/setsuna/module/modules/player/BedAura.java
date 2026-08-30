package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyInputEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.mixin.MultiPlayerGameModeAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.player.ChatUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** LiquidBounce Fucker port, exposed as BedAura. */
public final class BedAura extends Module {

    public static final BedAura INSTANCE = new BedAura();

    private static final double[] TARGET_POINT_PROPORTIONS = {0.1D, 0.3D, 0.5D, 0.7D, 0.9D};
    private static final double[] AIM_POINT_PROPORTIONS = {
            0.05D, 0.15D, 0.25D, 0.35D, 0.45D, 0.55D, 0.65D, 0.75D, 0.85D, 0.95D
    };
    private static final Direction[] DIRECTIONS_EXCLUDING_DOWN = {
            Direction.UP, Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH
    };
    private static final int MAX_SURROUNDING_PATH_BLOCKS = 8;
    private static final double RAYCAST_TARGET_EPSILON = 0.005D;
    private static final double SHAPE_EPSILON = 1.0E-7D;

    private final DoubleSetting range = add(new DoubleSetting("Range", 5.0D, 1.0D, 6.0D, 0.1D));
    private final DoubleSetting wallRange = add(new DoubleSetting("WallRange", 0.0D, 0.0D, 6.0D, 0.1D));

    private final BooleanSetting entrance = add(new BooleanSetting("Entrance", false));
    private final BooleanSetting breakFree = add(new BooleanSetting("BreakFree", true)
            .visibleWhen(entrance::get));
    private final BooleanSetting surroundings = add(new BooleanSetting("Surroundings", true));
    private final StringSetting targets = add(new StringSetting(
            "Targets", "*_bed, minecraft:dragon_egg"));
    private final IntSetting delay = add(new IntSetting("Delay", 0, 0, 20, 1));
    private final EnumSetting<DestroyAction> action = add(new EnumSetting<>("Action", DestroyAction.DESTROY));
    private final BooleanSetting forceImmediateBreak = add(new BooleanSetting("ForceImmediateBreak", false));

    private final BooleanSetting ignoreOpenInventory = add(new BooleanSetting("IgnoreOpenInventory", true));
    private final BooleanSetting ignoreUsingItem = add(new BooleanSetting("IgnoreUsingItem", true));
    private final BooleanSetting prioritizeOverKillAura = add(new BooleanSetting("PrioritizeOverKillAura", false));
    private final BooleanSetting chestAsFullBlock = add(new BooleanSetting("ChestAsFullBlock", false));

    private final EnumSetting<SelfBedMode> selfBed = add(new EnumSetting<>("SelfBed", SelfBedMode.NONE));
    private final StringSetting selfBedSlots = add(new StringSetting("SelfBedSlots", "head")
            .visibleWhen(() -> selfBed.is(SelfBedMode.COLOR)));
    private final BooleanSetting selfBedLoose = add(new BooleanSetting("SelfBedLoose", false)
            .visibleWhen(() -> selfBed.is(SelfBedMode.COLOR)));
    private final DoubleSetting selfBedDistance = add(new DoubleSetting("SelfBedDistance", 24.0D, 16.0D, 48.0D, 1.0D)
            .visibleWhen(() -> selfBed.is(SelfBedMode.SPAWN_LOCATION)));
    private final KeybindSetting selfBedTrackKey = add(new KeybindSetting("SelfBedTrack", GLFW.GLFW_KEY_KP_ADD)
            .visibleWhen(() -> selfBed.is(SelfBedMode.MANUAL)));
    private final KeybindSetting selfBedUntrackKey = add(new KeybindSetting("SelfBedUntrack", GLFW.GLFW_KEY_KP_SUBTRACT)
            .visibleWhen(() -> selfBed.is(SelfBedMode.MANUAL)));

    private final DoubleSetting rotationSpeed = add(new DoubleSetting("RotationSpeed", 10.0D, 1.0D, 10.0D, 0.5D));
    private final BooleanSetting render = add(new BooleanSetting("TargetRendering", true));
    private final ColorSetting fillColor = add(new ColorSetting("TargetFill", new Color(255, 0, 0, 90))
            .visibleWhen(render::get));
    private final ColorSetting outlineColor = add(new ColorSetting("TargetOutline", new Color(255, 0, 0, 220))
            .visibleWhen(render::get));

    private DestroyerTarget currentTarget;
    private DestroyerTarget oldTarget;
    private int targetDelayTicks;
    private int useDelayTicks;
    private boolean targetChangedThisTick;
    private BlockPos miningPos;
    private int originalSlot = -1;
    private int activeToolSlot = -1;
    private Vec3 trackedSpawnLocation;
    private BlockPos manuallyTrackedBed = BlockPos.ZERO;

    private BedAura() {
        super("BedAura", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetRuntimeState();
    }

    @Override
    protected void onDisable() {
        clearCurrentTarget(true);
        oldTarget = null;
        trackedSpawnLocation = null;
        manuallyTrackedBed = BlockPos.ZERO;
    }

    /** Equivalent to LiquidBounce's RotationUpdateEvent target updater. */
    @Listen
    private void onTargetUpdate(PlayerTickEvent.Pre event) {
        targetChangedThisTick = false;
        if (noPlayer() || mc.gameMode == null || mc.player.isSpectator()) {
            clearCurrentTarget(true);
            oldTarget = null;
            return;
        }
        if (!ignoreOpenInventory.get() && mc.screen instanceof AbstractContainerScreen<?>) {
            return;
        }
        if (!ignoreUsingItem.get() && mc.player.isUsingItem()) {
            return;
        }

        oldTarget = currentTarget;
        updateCurrentTarget();
        targetChangedThisTick = !Objects.equals(oldTarget, currentTarget);

        if (oldTarget != null && currentTarget == null) {
            stopMining(true);
        } else if (targetChangedThisTick && delay.get() > 0) {
            stopMining(true);
            targetDelayTicks = delay.get();
        }
    }

    /** Runs after RotationManager's low-priority pre-tick handler. */
    @Listen(priority = -2000)
    private void onBreaker(PlayerTickEvent.Pre event) {
        if (noPlayer() || mc.gameMode == null || mc.player.isSpectator()) {
            return;
        }
        if (!ignoreOpenInventory.get() && mc.screen instanceof AbstractContainerScreen<?>) {
            return;
        }
        if (targetChangedThisTick && targetDelayTicks > 0) {
            return;
        }
        if (targetDelayTicks > 0 && --targetDelayTicks > 0) {
            return;
        }
        if (useDelayTicks > 0) {
            useDelayTicks--;
            return;
        }

        DestroyerTarget target = currentTarget;
        if (target == null) {
            return;
        }

        BlockState state = mc.level.getBlockState(target.pos());
        BlockHitResult hit = raytraceTarget(
                target.pos(), state, RotationManager.INSTANCE.getRotation(), Math.max(range.get(), wallRange.get()));
        if (hit == null || state.isAir() || !isBreakable(target.pos(), state)) {
            return;
        }

        if (target.action() == DestroyAction.USE) {
            if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit) == InteractionResult.SUCCESS) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            useDelayTicks = delay.get();
            return;
        }

        equipBestTool(target.pos());
        doBreak(hit);
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || currentTarget == null || noPlayer()) {
            return;
        }
        BlockState state = mc.level.getBlockState(currentTarget.pos());
        if (state.isAir()) {
            return;
        }
        VoxelShape shape = state.getShape(mc.level, currentTarget.pos(), CollisionContext.of(mc.player));
        AABB box = shape.isEmpty() ? new AABB(currentTarget.pos()) : shape.bounds().move(currentTarget.pos());
        Render3DUtils.drawFilledBox(box, fillColor.get());
        Render3DUtils.drawOutlineBox(event.getPoseStack(), box, outlineColor.get(), 2.0F);
    }

    @Listen
    private void onPacket(PacketEvent.Receive event) {
        if (!selfBed.is(SelfBedMode.SPAWN_LOCATION) || mc.player == null
                || !(event.getPacket() instanceof ClientboundPlayerPositionPacket packet)) {
            return;
        }
        Vec3 position = packet.change().position();
        if (mc.player.position().distanceToSqr(position) > 16.0D * 16.0D) {
            trackedSpawnLocation = position;
        }
    }

    @Listen
    private void onKey(KeyInputEvent event) {
        if (!selfBed.is(SelfBedMode.MANUAL) || event.action() != GLFW.GLFW_PRESS || noPlayer()) {
            return;
        }
        if (selfBedTrackKey.matches(event.key())) {
            BlockPos closest = findClosestBed(16.0D);
            if (closest == null) {
                ChatUtils.addChatMessage("Cannot find any bed around you.");
            } else {
                manuallyTrackedBed = closest;
                ChatUtils.addChatMessage("Tracked bed at " + closest.getX() + ", "
                        + closest.getY() + ", " + closest.getZ() + ".");
            }
        } else if (selfBedUntrackKey.matches(event.key()) && !manuallyTrackedBed.equals(BlockPos.ZERO)) {
            manuallyTrackedBed = BlockPos.ZERO;
            ChatUtils.addChatMessage("Self bed untracked.");
        }
    }

    @Listen
    private void onRespawn(RespawnEvent event) {
        clearCurrentTarget(true);
        oldTarget = null;
        trackedSpawnLocation = null;
        manuallyTrackedBed = BlockPos.ZERO;
    }

    @Override
    public String getInfo() {
        if (currentTarget == null) {
            return null;
        }
        return currentTarget.isTarget() ? "Target" : "Surrounding";
    }

    private void updateCurrentTarget() {
        List<BlockPos> possibleBlocks = searchPossibleTargetPositions();
        validateCurrentTarget(possibleBlocks);
        if (possibleBlocks.isEmpty()) {
            return;
        }

        double normalRange = range.get();
        for (BlockPos pos : possibleBlocks) {
            double throughWalls = entrance.get() && hasEntrance(pos) ? normalRange : wallRange.get();
            Boolean selected = considerAsTarget(
                    new DestroyerTarget(pos, action.get(), null, true), normalRange, throughWalls, false);
            if (Boolean.TRUE.equals(selected)) {
                return;
            }
        }
        if (currentTarget != null) {
            return;
        }

        for (BlockPos pos : possibleBlocks) {
            if (entrance.get() && breakFree.get()) {
                BlockPos weakBlock = weakestNeighbor(pos);
                if (weakBlock != null) {
                    considerAsTarget(
                            new DestroyerTarget(weakBlock, DestroyAction.DESTROY, null, false),
                            normalRange, normalRange, false);
                }
            } else if (surroundings.get()) {
                updateSurroundings(pos);
            }
        }
    }

    private void validateCurrentTarget(List<BlockPos> possibleBlocks) {
        DestroyerTarget target = currentTarget;
        if (target == null) {
            return;
        }

        boolean removed = !possibleBlocks.contains(target.actualTargetPos());
        if (target.isTarget() && target.action() != action.get()) {
            removed = true;
        }
        if (Boolean.FALSE.equals(considerAsTarget(
                target, range.get(), wallRange.get(), true))) {
            removed = true;
        }
        if (removed) {
            clearCurrentTarget(true);
        }
    }

    /**
     * @return true if selected, false if invalid, null if valid but no better than the current target.
     */
    private Boolean considerAsTarget(
            DestroyerTarget target, double normalRange, double throughWallsRange, boolean current) {
        BlockState state = mc.level.getBlockState(target.pos());
        if (state.isAir()) {
            return Boolean.FALSE;
        }
        Aim aim = findAim(target.pos(), state, normalRange, throughWallsRange);
        if (aim == null) {
            return Boolean.FALSE;
        }

        if (!current && currentTarget != null && target.compareTo(currentTarget) >= 0) {
            return null;
        }

        RotationManager.INSTANCE.setRotations(
                RotationUtils.calculate(aim.point()),
                rotationSpeed.get(),
                prioritizeOverKillAura.get() ? Priority.High : Priority.Low);

        clearCurrentTarget(false);
        currentTarget = target;
        return Boolean.TRUE;
    }

    private List<BlockPos> searchPossibleTargetPositions() {
        Vec3 eyes = mc.player.getEyePosition();
        double scanRange = range.get() + 1.0D;
        int minX = Mth.floor(eyes.x - scanRange);
        int minY = Mth.floor(eyes.y - scanRange);
        int minZ = Mth.floor(eyes.z - scanRange);
        int maxX = Mth.ceil(eyes.x + scanRange);
        int maxY = Mth.ceil(eyes.y + scanRange);
        int maxZ = Mth.ceil(eyes.z + scanRange);
        double rangeSquared = range.get() * range.get();

        List<WeightedPos> found = new ArrayList<>();
        for (BlockPos mutable : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockPos pos = mutable.immutable();
            BlockState state = mc.level.getBlockState(pos);
            if (!matchesTarget(state.getBlock())) {
                continue;
            }
            if (state.getBlock() instanceof BedBlock bed && isSelfBed(bed, pos, state)) {
                continue;
            }
            double distance = shapeDistanceSquared(eyes, pos, state);
            if (distance <= rangeSquared) {
                found.add(new WeightedPos(pos, distance));
            }
        }
        found.sort(Comparator.comparingDouble(WeightedPos::distance));
        return found.stream().map(WeightedPos::pos).toList();
    }

    private boolean updateSurroundings(BlockPos initialPosition) {
        SurroundingPath path = findBestSurroundingPath(initialPosition, mc.player.getEyePosition());
        if (path == null) {
            return false;
        }
        return Boolean.TRUE.equals(considerAsTarget(
                new DestroyerTarget(path.firstBlock(), DestroyAction.DESTROY, path.info(), false),
                range.get(), wallRange.get(), false));
    }

    private SurroundingPath findBestSurroundingPath(BlockPos target, Vec3 eyes) {
        BlockState targetState = mc.level.getBlockState(target);
        if (targetState.isAir()) {
            return null;
        }
        VoxelShape targetShape = chestAsFullBlock.get() && isAnyChest(targetState.getBlock())
                ? Shapes.block()
                : targetState.getShape(mc.level, target);
        if (targetShape.isEmpty()) {
            return null;
        }

        SurroundingPath best = null;
        double rangeSquared = range.get() * range.get();
        List<AABB> targetBoxes = targetShape.toAabbs().stream().map(box -> box.move(target)).toList();
        for (AABB box : targetBoxes) {
            for (Direction face : Direction.values()) {
                for (double a : TARGET_POINT_PROPORTIONS) {
                    for (double b : TARGET_POINT_PROPORTIONS) {
                        Vec3 targetPoint = pointOnFace(box, face, a, b);
                        if (!isSurfacePoint(targetBoxes, targetPoint, face)) {
                            continue;
                        }
                        if (eyes.distanceToSqr(targetPoint) > rangeSquared) {
                            continue;
                        }
                        SurroundingPath path = createSurroundingPath(target, eyes, targetPoint);
                        if (path != null && (best == null || best.compareTo(path) >= 0)) {
                            best = path;
                        }
                    }
                }
            }
        }
        return best;
    }

    private SurroundingPath createSurroundingPath(BlockPos target, Vec3 eyes, Vec3 targetPoint) {
        List<BlockPos> blocks = traceBlocksToTarget(target, eyes, targetPoint);
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        double resistance = 0.0D;
        for (BlockPos pos : blocks) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) {
                return null;
            }
            resistance += miningDuration(pos, state);
        }
        BlockPos first = blocks.getFirst();
        SurroundingInfo info = new SurroundingInfo(
                target,
                targetPoint,
                resistance,
                blocks.size(),
                first.getCenter().distanceToSqr(targetPoint),
                first.getCenter().distanceToSqr(eyes));
        return new SurroundingPath(first, blocks, info);
    }

    private List<BlockPos> traceBlocksToTarget(BlockPos target, Vec3 eyes, Vec3 targetPoint) {
        Vec3 direction = targetPoint.subtract(eyes);
        if (direction.lengthSqr() < 1.0E-12D) {
            return null;
        }
        Vec3 end = targetPoint.add(direction.normalize().scale(RAYCAST_TARGET_EPSILON));
        TraceContext context = new TraceContext(eyes, end, target);
        Boolean reached = BlockGetter.traverseBlocks(
                eyes,
                end,
                context,
                (trace, pos) -> trace.visit(pos),
                ignored -> Boolean.FALSE);
        return reached ? List.copyOf(context.blockers) : null;
    }

    private Aim findAim(BlockPos pos, BlockState state, double normalRange, double throughWallsRange) {
        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(mc.player));
        if (shape.isEmpty()) {
            return null;
        }
        Vec3 eyes = mc.player.getEyePosition();
        Rot2f base = RotationUtils.calculate(pos.getCenter());
        AimTracker tracker = new AimTracker(base);

        List<AABB> boxes = new ArrayList<>(shape.toAabbs());
        boxes.sort(Comparator.comparingDouble(AABB::getSize).reversed());
        for (AABB local : boxes) {
            AABB box = local.move(pos);
            tracker.consider(pos, nearestPoint(box, eyes), normalRange, throughWallsRange);
            tracker.consider(pos, firstIntersectionToward(box, eyes, pos.getCenter()), normalRange, throughWallsRange);
            for (Direction face : Direction.values()) {
                for (double a : AIM_POINT_PROPORTIONS) {
                    for (double b : AIM_POINT_PROPORTIONS) {
                        tracker.consider(pos, pointOnFace(box, face, a, b), normalRange, throughWallsRange);
                    }
                }
            }
        }
        return tracker.bestVisible != null ? tracker.bestVisible : tracker.bestInvisible;
    }

    private BlockHitResult visibleHit(BlockPos target, Vec3 eyes, Vec3 point) {
        Vec3 direction = point.subtract(eyes);
        if (direction.lengthSqr() < 1.0E-12D) {
            return null;
        }
        HitResult result = mc.level.clip(new ClipContext(
                eyes,
                point.add(direction.normalize().scale(RAYCAST_TARGET_EPSILON)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player));
        return result instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(target)
                ? blockHit
                : null;
    }

    private BlockHitResult raytraceTarget(BlockPos pos, BlockState state, Rot2f rotation, double reach) {
        if (state.isAir()) {
            return null;
        }
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 end = eyes.add(look.scale(reach));
        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(mc.player));
        if (shape.isEmpty()) {
            return null;
        }
        BlockHitResult result = shape.clip(eyes, end, pos);
        return result != null && result.getLocation().distanceToSqr(eyes) <= reach * reach ? result : null;
    }

    private void doBreak(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction direction = hit.getDirection();
        if (mc.player.isCreative()) {
            if (mc.gameMode.startDestroyBlock(pos, direction)) {
                mc.player.swing(InteractionHand.MAIN_HAND);
                miningPos = pos;
                return;
            }
        }

        if (forceImmediateBreak.get()) {
            MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) mc.gameMode;
            accessor.setsuna$startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence));
            mc.player.swing(InteractionHand.MAIN_HAND);
            accessor.setsuna$startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence));
            miningPos = null;
            return;
        }

        if (mc.gameMode.continueDestroyBlock(pos, direction)) {
            mc.player.swing(InteractionHand.MAIN_HAND);
            mc.level.addBreakingBlockEffect(pos, direction);
        }
        miningPos = pos;
    }

    private boolean hasEntrance(BlockPos pos) {
        Block targetBlock = mc.level.getBlockState(pos).getBlock();
        for (Direction direction : DIRECTIONS_EXCLUDING_DOWN) {
            BlockPos neighbor = pos.relative(direction);
            BlockState state = mc.level.getBlockState(neighbor);
            if (state.getBlock() != targetBlock
                    && state.getShape(mc.level, neighbor, CollisionContext.of(mc.player)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private BlockPos weakestNeighbor(BlockPos pos) {
        Block targetBlock = mc.level.getBlockState(pos).getBlock();
        List<Neighbor> neighbors = new ArrayList<>();
        for (Direction direction : DIRECTIONS_EXCLUDING_DOWN) {
            BlockPos neighbor = pos.relative(direction);
            BlockState state = mc.level.getBlockState(neighbor);
            if (state.getBlock() != targetBlock && !state.isAir()) {
                neighbors.add(new Neighbor(neighbor.immutable(), state));
            }
        }
        return neighbors.stream()
                .min(Comparator
                        .comparingDouble((Neighbor value) -> miningDuration(value.pos(), value.state()))
                        .thenComparingDouble(value -> shapeDistanceSquared(
                                mc.player.getEyePosition(), value.pos(), value.state())))
                .map(Neighbor::pos)
                .orElse(null);
    }

    private double miningDuration(BlockPos pos, BlockState state) {
        float bestSpeed = 0.0F;
        for (int slot = 0; slot < 9; slot++) {
            bestSpeed = Math.max(bestSpeed, mc.player.getInventory().getItem(slot).getDestroySpeed(state));
        }
        return state.getDestroySpeed(mc.level, pos) / Math.max(bestSpeed, 0.001F);
    }

    private void equipBestTool(BlockPos pos) {
        if (!AutoTool.INSTANCE.isEnabled()) {
            return;
        }
        int toolSlot = AutoTool.INSTANCE.getTool(pos);
        if (toolSlot < 0 || toolSlot > 8) {
            return;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (originalSlot != -1 && selectedSlot != originalSlot && selectedSlot != activeToolSlot) {
            originalSlot = selectedSlot;
        }
        if (selectedSlot == toolSlot) {
            activeToolSlot = toolSlot;
            return;
        }
        if (originalSlot == -1) {
            originalSlot = selectedSlot;
        }
        mc.player.getInventory().setSelectedSlot(toolSlot);
        PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(toolSlot));
        activeToolSlot = toolSlot;
    }

    private void clearCurrentTarget(boolean restoreTool) {
        stopMining(restoreTool);
        currentTarget = null;
    }

    private void stopMining(boolean restoreTool) {
        if (mc.gameMode != null && miningPos != null) {
            mc.gameMode.stopDestroyBlock();
        }
        miningPos = null;
        if (restoreTool) {
            restoreTool();
        }
    }

    private void restoreTool() {
        if (mc.player != null && originalSlot >= 0 && originalSlot < 9
                && mc.player.getInventory().getSelectedSlot() != originalSlot) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(originalSlot));
        }
        originalSlot = -1;
        activeToolSlot = -1;
    }

    private void resetRuntimeState() {
        currentTarget = null;
        oldTarget = null;
        targetDelayTicks = 0;
        useDelayTicks = 0;
        targetChangedThisTick = false;
        miningPos = null;
        originalSlot = -1;
        activeToolSlot = -1;
    }

    private boolean matchesTarget(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return false;
        }
        String full = id.toString().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        for (String raw : targets.get().split("[,;]")) {
            String pattern = raw.trim().toLowerCase(Locale.ROOT);
            if (pattern.isEmpty()) {
                continue;
            }
            String tested = pattern.indexOf(':') >= 0 ? full : path;
            if (wildcardMatches(tested, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wildcardMatches(String value, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        boolean starts = pattern.startsWith("*");
        boolean ends = pattern.endsWith("*");
        String core = pattern.substring(starts ? 1 : 0, pattern.length() - (ends ? 1 : 0));
        if (starts && ends) return value.contains(core);
        if (starts) return value.endsWith(core);
        if (ends) return value.startsWith(core);
        return value.equals(pattern);
    }

    private boolean isSelfBed(BedBlock bed, BlockPos pos, BlockState state) {
        return switch (selfBed.get()) {
            case NONE -> false;
            case COLOR -> isSelfBedColor(bed);
            case SPAWN_LOCATION -> trackedSpawnLocation != null
                    && trackedSpawnLocation.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())
                    <= selfBedDistance.get() * selfBedDistance.get();
            case MANUAL -> pos.equals(manuallyTrackedBed)
                    || pos.relative(BedBlock.getConnectedDirection(state)).equals(manuallyTrackedBed);
        };
    }

    private boolean isSelfBedColor(BedBlock bed) {
        for (String raw : selfBedSlots.get().split("[,;]")) {
            EquipmentSlot slot = parseEquipmentSlot(raw.trim());
            if (slot == null) {
                continue;
            }
            ItemStack stack = mc.player.getItemBySlot(slot);
            var dyed = stack.get(DataComponents.DYED_COLOR);
            if (dyed == null) {
                continue;
            }
            int armorColor = ARGB.opaque(dyed.rgb());
            if (selfBedLoose.get()) {
                if (closestDyeColor(armorColor) == bed.getColor()) {
                    return true;
                }
            } else if (armorColor == bed.getColor().getTextureDiffuseColor()) {
                return true;
            }
        }
        return false;
    }

    private static EquipmentSlot parseEquipmentSlot(String name) {
        if (name.isEmpty()) return null;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "mainhand", "main_hand" -> EquipmentSlot.MAINHAND;
            case "offhand", "off_hand" -> EquipmentSlot.OFFHAND;
            case "feet", "boots" -> EquipmentSlot.FEET;
            case "legs", "pants" -> EquipmentSlot.LEGS;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "body" -> EquipmentSlot.BODY;
            case "saddle" -> EquipmentSlot.SADDLE;
            default -> null;
        };
    }

    private static DyeColor closestDyeColor(int color) {
        int red = ARGB.red(color);
        int green = ARGB.green(color);
        int blue = ARGB.blue(color);
        DyeColor best = DyeColor.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (DyeColor dye : DyeColor.values()) {
            int candidate = dye.getTextureDiffuseColor();
            long dr = red - ARGB.red(candidate);
            long dg = green - ARGB.green(candidate);
            long db = blue - ARGB.blue(candidate);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = dye;
            }
        }
        return best;
    }

    private BlockPos findClosestBed(double radius) {
        Vec3 eyes = mc.player.getEyePosition();
        int minX = Mth.floor(eyes.x - radius);
        int minY = Mth.floor(eyes.y - radius);
        int minZ = Mth.floor(eyes.z - radius);
        int maxX = Mth.ceil(eyes.x + radius);
        int maxY = Mth.ceil(eyes.y + radius);
        int maxZ = Mth.ceil(eyes.z + radius);
        BlockPos closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos mutable : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (!(mc.level.getBlockState(mutable).getBlock() instanceof BedBlock)) {
                continue;
            }
            double distance = mutable.getCenter().distanceToSqr(eyes);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = mutable.immutable();
            }
        }
        return closest;
    }

    private double shapeDistanceSquared(Vec3 point, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(mc.player)).move(pos);
        return shape.closestPointTo(point).map(value -> value.distanceToSqr(point))
                .orElse(Double.POSITIVE_INFINITY);
    }

    private boolean isBreakable(BlockPos pos, BlockState state) {
        return !state.isAir() && (mc.player.isCreative() || state.getDestroySpeed(mc.level, pos) >= 0.0F);
    }

    private static boolean isAnyChest(Block block) {
        return block instanceof ChestBlock || block instanceof EnderChestBlock;
    }

    private static Vec3 nearestPoint(AABB box, Vec3 point) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    private static Vec3 firstIntersectionToward(AABB box, Vec3 eyes, Vec3 point) {
        Vec3 direction = point.subtract(eyes);
        if (direction.lengthSqr() < 1.0E-12D) {
            return point;
        }
        return AABB.clip(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                eyes, eyes.add(direction.scale(2.0D))).orElse(point);
    }

    private static Vec3 pointOnFace(AABB box, Direction face, double first, double second) {
        double x = Mth.lerp(first, box.minX, box.maxX);
        double y = Mth.lerp(first, box.minY, box.maxY);
        double z = Mth.lerp(second, box.minZ, box.maxZ);
        return switch (face) {
            case DOWN -> new Vec3(x, box.minY, z);
            case UP -> new Vec3(x, box.maxY, z);
            case NORTH -> new Vec3(x, Mth.lerp(second, box.minY, box.maxY), box.minZ);
            case SOUTH -> new Vec3(x, Mth.lerp(second, box.minY, box.maxY), box.maxZ);
            case WEST -> new Vec3(box.minX, y, z);
            case EAST -> new Vec3(box.maxX, y, z);
        };
    }

    private static boolean isSurfacePoint(List<AABB> boxes, Vec3 point, Direction face) {
        Vec3 inward = point.add(
                -face.getStepX() * SHAPE_EPSILON,
                -face.getStepY() * SHAPE_EPSILON,
                -face.getStepZ() * SHAPE_EPSILON);
        Vec3 outward = point.add(
                face.getStepX() * SHAPE_EPSILON,
                face.getStepY() * SHAPE_EPSILON,
                face.getStepZ() * SHAPE_EPSILON);
        return contains(boxes, inward) && !contains(boxes, outward);
    }

    private static boolean contains(List<AABB> boxes, Vec3 point) {
        for (AABB box : boxes) {
            if (point.x >= box.minX && point.x <= box.maxX
                    && point.y >= box.minY && point.y <= box.maxY
                    && point.z >= box.minZ && point.z <= box.maxZ) {
                return true;
            }
        }
        return false;
    }

    private final class AimTracker {
        private final Rot2f base;
        private Aim bestVisible;
        private Aim bestInvisible;
        private double bestVisibleDifference = Double.POSITIVE_INFINITY;
        private double bestInvisibleDifference = Double.POSITIVE_INFINITY;

        private AimTracker(Rot2f base) {
            this.base = base;
        }

        private void consider(BlockPos target, Vec3 point, double normalRange, double throughWallsRange) {
            if (point == null) {
                return;
            }
            Vec3 eyes = mc.player.getEyePosition();
            BlockHitResult visible = visibleHit(target, eyes, point);
            boolean isVisible = visible != null;
            Vec3 hitPoint = isVisible ? visible.getLocation() : point;
            double allowedRange = isVisible ? normalRange : throughWallsRange;
            if (eyes.distanceToSqr(hitPoint) >= allowedRange * allowedRange) {
                return;
            }

            Rot2f rotation = RotationUtils.calculate(point);
            double yaw = Mth.wrapDegrees(rotation.getYaw() - base.getYaw());
            double pitch = rotation.getPitch() - base.getPitch();
            double difference = Math.hypot(yaw, pitch);
            Aim aim = new Aim(point, isVisible ? visible.getDirection() : Direction.UP);
            if (isVisible && difference < bestVisibleDifference) {
                bestVisibleDifference = difference;
                bestVisible = aim;
            } else if (!isVisible && difference < bestInvisibleDifference) {
                bestInvisibleDifference = difference;
                bestInvisible = aim;
            }
        }
    }

    private final class TraceContext {
        private final Vec3 start;
        private final Vec3 end;
        private final BlockPos target;
        private final List<BlockPos> blockers = new ArrayList<>(MAX_SURROUNDING_PATH_BLOCKS);
        private final Set<Long> visited = new HashSet<>(MAX_SURROUNDING_PATH_BLOCKS);

        private TraceContext(Vec3 start, Vec3 end, BlockPos target) {
            this.start = start;
            this.end = end;
            this.target = target;
        }

        private Boolean visit(BlockPos pos) {
            if (pos.equals(target)) {
                BlockState targetState = mc.level.getBlockState(pos);
                VoxelShape targetShape = targetState.getShape(
                        mc.level, pos, CollisionContext.of(mc.player));
                return !targetShape.isEmpty() && targetShape.clip(start, end, pos) != null;
            }
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) {
                return null;
            }
            VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(mc.player));
            if (shape.isEmpty() || shape.clip(start, end, pos) == null) {
                return null;
            }
            if (!visited.add(pos.asLong()) || !isBreakable(pos, state)
                    || blockers.size() >= MAX_SURROUNDING_PATH_BLOCKS) {
                return Boolean.FALSE;
            }
            blockers.add(pos.immutable());
            return null;
        }
    }

    private enum DestroyAction {
        DESTROY,
        USE
    }

    private enum SelfBedMode {
        NONE,
        COLOR,
        SPAWN_LOCATION,
        MANUAL
    }

    private record Aim(Vec3 point, Direction side) {
    }

    private record WeightedPos(BlockPos pos, double distance) {
    }

    private record Neighbor(BlockPos pos, BlockState state) {
    }

    private record DestroyerTarget(
            BlockPos pos,
            DestroyAction action,
            SurroundingInfo surroundingInfo,
            boolean isTarget
    ) implements Comparable<DestroyerTarget> {
        private BlockPos actualTargetPos() {
            return surroundingInfo == null ? pos : surroundingInfo.actualTargetPos();
        }

        @Override
        public int compareTo(DestroyerTarget other) {
            if (isTarget && !other.isTarget) return -1;
            if (!isTarget && other.isTarget) return 1;
            if (isTarget) return 0;
            if (surroundingInfo == null && other.surroundingInfo != null) return -1;
            if (surroundingInfo != null && other.surroundingInfo == null) return 1;
            if (surroundingInfo == null) return 0;
            return surroundingInfo.compareTo(other.surroundingInfo);
        }
    }

    private record SurroundingPath(
            BlockPos firstBlock,
            List<BlockPos> blocks,
            SurroundingInfo info
    ) implements Comparable<SurroundingPath> {
        @Override
        public int compareTo(SurroundingPath other) {
            return info.compareTo(other.info);
        }
    }

    private record SurroundingInfo(
            BlockPos actualTargetPos,
            Vec3 targetPoint,
            double resistance,
            int blockerCount,
            double firstBlockDistanceToTarget,
            double firstBlockDistanceToEyes
    ) implements Comparable<SurroundingInfo> {
        @Override
        public int compareTo(SurroundingInfo other) {
            int result = Double.compare(resistance, other.resistance);
            if (result == 0) result = Integer.compare(blockerCount, other.blockerCount);
            if (result == 0) result = Double.compare(firstBlockDistanceToTarget, other.firstBlockDistanceToTarget);
            if (result == 0) result = Double.compare(firstBlockDistanceToEyes, other.firstBlockDistanceToEyes);
            return result;
        }
    }
}
