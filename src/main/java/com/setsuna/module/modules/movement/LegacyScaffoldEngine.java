package com.setsuna.module.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.math.MathUtils;
import com.setsuna.util.player.FallingPlayer;
import com.setsuna.util.player.FindItemResult;
import com.setsuna.util.player.InvUtils;
import com.setsuna.util.player.MoveUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.render.animation.Easing;
import com.setsuna.util.rotation.RaytraceUtils;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** The original Setsuna Scaffold implementation, hosted behind Scaffold's legacy modes. */
final class LegacyScaffoldEngine {

    private static final Minecraft mc = Minecraft.getInstance();

    private enum RotationMode {
        Rise,
        Hypixel
    }

    private enum RaytraceMode {
        Normal,
        Strict
    }

    private enum SwapMode {
        None,
        Normal,
        Silent,
        InvSwitch
    }

    private static final List<Block> BLACKLISTED_BLOCKS = List.of(
            Blocks.AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ENCHANTING_TABLE,
            Blocks.GLASS_PANE,
            Blocks.IRON_BARS,
            Blocks.SNOW,
            Blocks.COAL_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.TORCH,
            Blocks.ANVIL,
            Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX,
            Blocks.TNT,
            Blocks.GOLD_ORE,
            Blocks.IRON_ORE,
            Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.STONE_BUTTON,
            Blocks.LEVER,
            Blocks.TALL_GRASS,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL,
            Blocks.CORNFLOWER,
            Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM,
            Blocks.VINE,
            Blocks.SUNFLOWER,
            Blocks.LADDER,
            Blocks.FURNACE,
            Blocks.SAND,
            Blocks.CACTUS,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.CRAFTING_TABLE,
            Blocks.COBWEB,
            Blocks.PUMPKIN,
            Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH,
            Blocks.FLOWER_POT);

    private final Scaffold parent;
    private final Set<Setting<?>> settings = Collections.newSetFromMap(new IdentityHashMap<>());

    private final EnumSetting<SwapMode> swapMode;
    private final BooleanSetting swapBack;
    private final BooleanSetting skipTicks;
    private final BooleanSetting snap;
    private final EnumSetting<RotationMode> rotationMode;
    private final EnumSetting<RaytraceMode> raytrace;
    private final IntSetting rotateSpeed;
    private final IntSetting rotateBackSpeed;
    private final IntSetting tellyTicks;
    private final BooleanSetting keepY;
    private final BooleanSetting multiPlace;
    private final BooleanSetting swingHand;
    private final BooleanSetting render;
    private final BooleanSetting fade;
    private final IntSetting fadeTime;
    private final BooleanSetting shrink;
    private final ColorSetting sideColor;
    private final ColorSetting lineColor;

    private int airTicks;
    private int yLevel;
    private BlockPos blockPos;
    private Direction direction;
    private Rot2f rotation;
    private int rotateCount;
    private int hypixelRotateTick;
    private FindItemResult blockResult;
    private boolean shouldSwapBack;

    private final List<RenderInfo> renderBoxes = new ArrayList<>();

    LegacyScaffoldEngine(Scaffold parent) {
        this.parent = parent;
        swapMode = setting(new EnumSetting<>("Swap Mode", SwapMode.Normal));
        swapBack = setting(new BooleanSetting("Swap Back", true)
                .visibleWhen(() -> swapMode.is(SwapMode.Normal)));
        skipTicks = setting(new BooleanSetting("Skip Ticks", false));
        snap = setting(new BooleanSetting("Snap", false).visibleWhen(parent::isLegacyGodBridge));
        rotationMode = setting(new EnumSetting<>("Rotation Mode", RotationMode.Rise));
        raytrace = setting(new EnumSetting<>("Raytrace Mode", RaytraceMode.Normal));
        rotateSpeed = setting(new IntSetting("Rotation Speed", 10, 1, 10, 1)
                .visibleWhen(() -> rotationMode.is(RotationMode.Rise)));
        rotateBackSpeed = setting(new IntSetting("Rotation Back Speed", 10, 1, 10, 1)
                .visibleWhen(parent::isLegacyTellyBridge));
        tellyTicks = setting(new IntSetting("Telly Ticks", 1, 0, 6, 1)
                .visibleWhen(parent::isLegacyTellyBridge));
        keepY = setting(new BooleanSetting("Keep Y", false).visibleWhen(parent::isLegacyGodBridge));
        multiPlace = setting(new BooleanSetting("Multi Place", true));
        swingHand = setting(new BooleanSetting("Swing Hand", true));
        render = setting(new BooleanSetting("Render", true));
        fade = setting(new BooleanSetting("Fade", true).visibleWhen(render::get));
        fadeTime = setting(new IntSetting("Fade Time", 500, 0, 3000, 50)
                .visibleWhen(() -> render.get() && fade.get()));
        shrink = setting(new BooleanSetting("Shrink", false).visibleWhen(render::get));
        sideColor = setting(new ColorSetting("Side Color", new Color(255, 183, 197, 100))
                .visibleWhen(render::get));
        lineColor = setting(new ColorSetting("Line Color", new Color(255, 105, 180))
                .visibleWhen(render::get));
    }

    private <T extends Setting<?>> T setting(T setting) {
        settings.add(setting);
        return parent.registerLegacySetting(setting);
    }

    boolean owns(Setting<?> setting) {
        return settings.contains(setting);
    }

    void enable() {
        airTicks = 0;
        blockPos = null;
        direction = null;
        rotation = null;
        rotateCount = 0;
        hypixelRotateTick = 0;
        blockResult = null;
        shouldSwapBack = false;
    }

    void disable() {
        yLevel = 0;
        if (noPlayer()) {
            return;
        }
        if (shouldSwapBack) {
            InvUtils.swapBack();
            shouldSwapBack = false;
        }
        boolean physicalShift = InputConstants.isKeyDown(
                mc.getWindow(), mc.options.keyShift.getDefaultKey().getValue());
        mc.options.keyShift.setDown(physicalShift);
    }

    int getBlockCount() {
        if (noPlayer()) {
            return 0;
        }
        int total = 0;
        if (isValidStack(mc.player.getOffhandItem())) {
            total += mc.player.getOffhandItem().getCount();
        }

        int maxSlot = swapMode.is(SwapMode.InvSwitch) ? mc.player.getInventory().getContainerSize() : 9;
        for (int i = 0; i < maxSlot; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValidStack(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    ItemStack getPlacementStack() {
        if (noPlayer()) {
            return ItemStack.EMPTY;
        }
        FindItemResult result = findBlockResult();
        if (!result.found()) {
            return ItemStack.EMPTY;
        }
        return result.isOffhand()
                ? mc.player.getOffhandItem()
                : mc.player.getInventory().getItem(result.slot());
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || renderBoxes.isEmpty()) {
            return;
        }

        long time = System.currentTimeMillis();
        long fadeMs = fadeTime.get().longValue();
        renderBoxes.removeIf(box -> time - box.startTime() > fadeMs);

        for (RenderInfo box : renderBoxes) {
            float progress = fadeMs <= 0L ? 1.0F
                    : Mth.clamp((float) (time - box.startTime()) / fadeMs, 0.0F, 1.0F);
            double scale = box.shrink()
                    ? Math.max(0.0D, 1.0D - Easing.EASE_IN_OUT_EXPO.getFunction().apply(progress))
                    : 1.0D;
            float alphaFactor = box.fade() ? Mth.clamp(1.0F - progress, 0.0F, 1.0F) : 1.0F;

            Color side = withAlpha(box.sideColor(), alphaFactor);
            Color line = withAlpha(box.lineColor(), alphaFactor);
            AABB renderBox = box.aabb();
            if (box.shrink()) {
                renderBox = AABB.ofSize(renderBox.getCenter(),
                        renderBox.getXsize() * scale,
                        renderBox.getYsize() * scale,
                        renderBox.getZsize() * scale);
            }

            Render3DUtils.drawFilledBox(renderBox, side);
            Render3DUtils.drawOutlineBox(event.getPoseStack(), renderBox, line);
        }
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            return;
        }

        blockResult = findBlockResult();
        if (!blockResult.found()) {
            return;
        }

        if (mc.player.onGround()) {
            airTicks = 0;
            yLevel = Mth.floor(mc.player.getY()) - 1;
        } else {
            airTicks++;
        }

        getBlockInfo();
        if (skipTicks.get() && blockPos != null && handleSkippedTick(event)) {
            return;
        }

        if (parent.isLegacyTellyBridge()) {
            handleTelly();
        } else {
            handleGodBridge();
        }
    }

    private boolean handleSkippedTick(PlayerTickEvent.Pre event) {
        boolean reachable = true;
        if (mc.player.getDeltaMovement().y < -0.1D) {
            FallingPlayer fallingPlayer = new FallingPlayer(mc.player);
            fallingPlayer.calculate(2);
            if (blockPos.getY() > fallingPlayer.getY()) {
                reachable = false;
            }
        }

        if ((reachable && mc.player.getDeltaMovement().horizontal().length() < 1.5D)
                || rotateCount > 8 || getBlockCount() < 1) {
            rotateCount = 0;
            return false;
        }

        Rot2f rotation = getRotation(blockPos, direction);
        event.cancel();
        rotateCount++;
        RotationManager.INSTANCE.rotations = rotation;
        RotationManager.INSTANCE.setActive(true);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                rotation.getYaw(), rotation.getPitch(), mc.player.onGround(), mc.player.horizontalCollision));

        swap();
        InteractionHand hand = blockResult.getHand();
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand,
                new BlockHitResult(getVec3(blockPos, direction), direction, blockPos, false));
        if (result.consumesAction()) {
            swing(hand);
            addRenderBox(blockPos.relative(direction));
        }
        swapBackAfterPlace();
        return true;
    }

    @Listen
    private void onMoveInput(KeyboardInputEvent event) {
        if (!noPlayer() && mc.player.onGround() && !mc.options.keyJump.isDown()
                && MoveUtils.isMoving() && parent.isLegacyTellyBridge()) {
            event.setJump(true);
        }
    }

    private void handleTelly() {
        if (mc.player.onGround()) {
            hypixelRotateTick = 0;
            RotationManager.INSTANCE.setRotations(new Rot2f(
                    mc.player.getYRot(), rotation == null ? mc.player.getXRot() : rotation.getPitch()),
                    rotateBackSpeed.get());
            return;
        }

        rotation = getRotation(blockPos, direction);
        RotationManager.INSTANCE.setRotations(rotation, currentRotationSpeed());
        if (airTicks > tellyTicks.get()) {
            place();
        }
    }

    private void handleGodBridge() {
        if (onAir() || !snap.get()) {
            rotation = getRotation(blockPos, direction);
            RotationManager.INSTANCE.setRotations(rotation, currentRotationSpeed());
        }
        place();
    }

    private double currentRotationSpeed() {
        if (rotationMode.is(RotationMode.Rise)) {
            hypixelRotateTick = 0;
            return rotateSpeed.get();
        }
        hypixelRotateTick++;
        return hypixelRotateTick <= 1 ? 7.055D : 1.944D;
    }

    private void place() {
        if (!onAir() || blockPos == null || direction == null || !isLookingAtTarget(blockPos, direction)) {
            return;
        }

        swap();
        InteractionHand hand = blockResult.getHand();
        if (!isValidStack(mc.player.getItemInHand(hand))) {
            swapBackAfterPlace();
            return;
        }

        InteractionResult result = interact(hand, blockPos, direction);
        if (result.consumesAction()) {
            swing(hand);
            addRenderBox(blockPos.relative(direction));
            if (multiPlace.get()) {
                placeAdditionalBlocks(hand);
            }
        }
        swapBackAfterPlace();
    }

    private void placeAdditionalBlocks(InteractionHand hand) {
        for (int i = 0; i < 3; i++) {
            getBlockInfo();
            if (blockPos == null || direction == null || !isValidStack(mc.player.getItemInHand(hand))
                    || !isLookingAtTarget(blockPos, direction)) {
                return;
            }
            if (!interact(hand, blockPos, direction).consumesAction()) {
                return;
            }
            swing(hand);
            addRenderBox(blockPos.relative(direction));
        }
    }

    private InteractionResult interact(InteractionHand hand, BlockPos pos, Direction face) {
        return mc.gameMode.useItemOn(mc.player, hand,
                new BlockHitResult(getVec3(pos, face), face, pos, false));
    }

    private boolean isLookingAtTarget(BlockPos pos, Direction face) {
        return raytrace.is(RaytraceMode.Normal)
                ? RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), pos)
                : RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), pos, face);
    }

    private int getYLevel() {
        if (!mc.options.keyJump.isDown() && MoveUtils.isMoving() && mc.player.fallDistance <= 0.25F
                && (parent.isLegacyTellyBridge() || keepY.get())) {
            return yLevel;
        }
        return Mth.floor(mc.player.getY()) - 1;
    }

    private void getBlockInfo() {
        blockPos = null;
        direction = null;

        Vec3 baseVec = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(baseVec.x, getYLevel(), baseVec.z);
        int baseX = base.getX();
        int baseZ = base.getZ();
        if (!onAir() || checkBlock(baseVec, base)) {
            return;
        }

        for (int distance = 1; distance <= 6; distance++) {
            if (checkBlock(baseVec, new BlockPos(baseX, getYLevel() - distance, baseZ))) {
                return;
            }
            for (int x = 0; x <= distance; x++) {
                for (int z = 0; z <= distance - x; z++) {
                    int y = distance - x - z;
                    for (int reverseX = 0; reverseX <= 1; reverseX++) {
                        for (int reverseZ = 0; reverseZ <= 1; reverseZ++) {
                            BlockPos pos = new BlockPos(
                                    baseX + (reverseX == 0 ? x : -x),
                                    getYLevel() - y,
                                    baseZ + (reverseZ == 0 ? z : -z));
                            if (checkBlock(baseVec, pos)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean checkBlock(Vec3 baseVec, BlockPos pos) {
        if (!onAir() || pos.getY() > getYLevel()) {
            return false;
        }

        Vec3 center = pos.getBottomCenter();
        for (Direction candidate : Direction.values()) {
            Vec3 normal = candidate.getUnitVec3();
            Vec3 hit = center.add(normal.scale(0.5D));
            BlockPos support = pos.relative(candidate);
            BlockState state = mc.level.getBlockState(support);
            if (state.getCollisionShape(mc.level, support).isEmpty()
                    || state.getMenuProvider(mc.level, support) != null) {
                continue;
            }

            Direction face = candidate.getOpposite();
            if (hit.distanceToSqr(baseVec) > 4.5D * 4.5D || hit.subtract(baseVec).dot(normal) < 0.0D) {
                continue;
            }
            if (face == Direction.UP && MoveUtils.isMoving() && !mc.options.keyJump.isDown()) {
                continue;
            }

            blockPos = support;
            direction = face;
            return true;
        }
        return false;
    }

    private Rot2f getRotation(BlockPos pos, Direction face) {
        if (rotation == null) {
            return new Rot2f(Mth.wrapDegrees(mc.player.getYRot() - 135.0F), 82.0F);
        }
        if (!onAir() || pos == null || face == null) {
            return rotation;
        }

        Rot2f calculated = RotationUtils.calculate(pos, face);
        Float[] yaws = {-135.0F, -90.0F, -45.0F, 0.0F, 45.0F, 90.0F, 135.0F, 180.0F,
                calculated.getYaw()};
        Arrays.sort(yaws, (first, second) -> Float.compare(
                Math.abs(Mth.wrapDegrees(mc.player.getYRot() - 180.0F - first)),
                Math.abs(Mth.wrapDegrees(mc.player.getYRot() - 180.0F - second))));

        float[] pitches = {75.0F, 82.0F, 87.0F};
        for (float yaw : yaws) {
            for (float pitch : pitches) {
                Rot2f candidate = new Rot2f(
                        yaw + MathUtils.getRandom(-0.3F, 0.3F),
                        pitch + MathUtils.getRandom(-0.3F, 0.3F));
                if (matchesRaytrace(candidate, pos, face)) {
                    return candidate;
                }
            }
            for (int pitch = -90; pitch < 90; pitch++) {
                Rot2f candidate = new Rot2f(yaw, pitch);
                if (matchesRaytrace(candidate, pos, face)) {
                    return candidate;
                }
            }
        }
        return calculated;
    }

    private boolean matchesRaytrace(Rot2f candidate, BlockPos pos, Direction face) {
        return raytrace.is(RaytraceMode.Normal)
                ? RaytraceUtils.overBlock(candidate, pos)
                : RaytraceUtils.overBlock(candidate, pos, face);
    }

    private boolean onAir() {
        Vec3 eye = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(eye.x, getYLevel(), eye.z);
        return mc.level.getBlockState(base).canBeReplaced();
    }

    private Vec3 getVec3(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        if (face != Direction.UP && face != Direction.DOWN) {
            y += 0.08D;
        } else {
            x += MathUtils.getRandom(-0.3D, 0.3D);
            z += MathUtils.getRandom(-0.3D, 0.3D);
        }
        if (face == Direction.WEST || face == Direction.EAST) {
            z += MathUtils.getRandom(-0.3D, 0.3D);
        }
        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += MathUtils.getRandom(-0.3D, 0.3D);
        }
        return new Vec3(x, y, z);
    }

    private FindItemResult findBlockResult() {
        ItemStack offhand = mc.player.getOffhandItem();
        if (isValidStack(offhand)) {
            return new FindItemResult(40, offhand.getCount(), offhand.getMaxStackSize());
        }
        return swapMode.is(SwapMode.InvSwitch)
                ? InvUtils.find(this::isValidStack)
                : InvUtils.findInHotbar(this::isValidStack);
    }

    private void swap() {
        if (blockResult.isOffhand()) {
            return;
        }
        switch (swapMode.get()) {
            case Normal -> {
                int selectedSlot = mc.player.getInventory().getSelectedSlot();
                InvUtils.swap(blockResult.slot(), true);
                if (swapBack.get() && blockResult.slot() != selectedSlot) {
                    shouldSwapBack = true;
                }
            }
            case Silent -> InvUtils.swap(blockResult.slot(), true);
            case InvSwitch -> InvUtils.invSwap(blockResult.slot());
            case None -> {
            }
        }
    }

    private void swapBackAfterPlace() {
        if (blockResult.isOffhand()) {
            return;
        }
        switch (swapMode.get()) {
            case Silent -> InvUtils.swapBack();
            case InvSwitch -> InvUtils.invSwapBack();
            default -> {
            }
        }
    }

    private void swing(InteractionHand hand) {
        if (swingHand.get()) {
            mc.player.swing(hand);
        } else {
            mc.getConnection().send(new ServerboundSwingPacket(hand));
        }
    }

    private void addRenderBox(BlockPos placed) {
        if (render.get()) {
            renderBoxes.add(new RenderInfo(
                    new AABB(placed),
                    lineColor.get(),
                    sideColor.get(),
                    System.currentTimeMillis(),
                    fade.get(),
                    shrink.get()));
        }
    }

    private boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        String name = stack.getDisplayName().getString();
        if (name.contains("Click") || name.contains("点击")) {
            return false;
        }
        if (stack.getItem() instanceof StandingAndWallBlockItem) {
            return false;
        }

        Block block = blockItem.getBlock();
        if (block instanceof FlowerBlock || block instanceof BushBlock || block instanceof NetherFungusBlock
                || block instanceof CropBlock) {
            return false;
        }
        return !(block instanceof SlabBlock) && !BLACKLISTED_BLOCKS.contains(block);
    }

    private static boolean noPlayer() {
        return mc.player == null || mc.level == null || mc.gameMode == null;
    }

    private static Color withAlpha(Color color, float factor) {
        int alpha = Mth.clamp(Math.round(color.getAlpha() * factor), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private record RenderInfo(
            AABB aabb,
            Color lineColor,
            Color sideColor,
            long startTime,
            boolean fade,
            boolean shrink) {
    }
}
