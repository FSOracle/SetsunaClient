package com.setsuna.module.modules.render;

import com.setsuna.Setsuna;
import com.setsuna.event.Listen;
import com.setsuna.event.events.MoveEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.player.ChatUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.timer.TimerUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * See-through-walls block ESP. Renders a curated set of ores/containers through
 * terrain (via {@code SectionCompilerMixin}), with an optional packet-probe
 * ("Ore Deobf") mode that reveals server-hidden ores.
 */
public final class Xray extends Module {

    public static final Xray INSTANCE = new Xray();

    private enum Plugin {
        OLD,
        NEW
    }

    private static final Set<Block> TARGET_BLOCKS = Set.of(
            Blocks.AMETHYST_CLUSTER, Blocks.ANCIENT_DEBRIS, Blocks.BEACON, Blocks.BONE_BLOCK,
            Blocks.BOOKSHELF, Blocks.BREWING_STAND, Blocks.BUDDING_AMETHYST, Blocks.CHEST,
            Blocks.COAL_BLOCK, Blocks.COAL_ORE, Blocks.COMMAND_BLOCK, Blocks.COPPER_ORE,
            Blocks.CRAFTING_TABLE, Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DIAMOND_BLOCK, Blocks.DIAMOND_ORE, Blocks.DISPENSER, Blocks.DROPPER,
            Blocks.EMERALD_BLOCK, Blocks.EMERALD_ORE, Blocks.ENCHANTING_TABLE, Blocks.END_PORTAL,
            Blocks.END_PORTAL_FRAME, Blocks.ENDER_CHEST, Blocks.FURNACE, Blocks.GLOWSTONE,
            Blocks.GOLD_BLOCK, Blocks.GOLD_ORE, Blocks.HOPPER, Blocks.IRON_BLOCK, Blocks.IRON_ORE,
            Blocks.LADDER, Blocks.LAPIS_BLOCK, Blocks.LAPIS_ORE, Blocks.LAVA, Blocks.LODESTONE,
            Blocks.MOSSY_COBBLESTONE, Blocks.NETHER_GOLD_ORE, Blocks.NETHER_PORTAL,
            Blocks.NETHER_QUARTZ_ORE, Blocks.RAW_COPPER_BLOCK, Blocks.RAW_GOLD_BLOCK,
            Blocks.RAW_IRON_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_ORE,
            Blocks.REPEATING_COMMAND_BLOCK, Blocks.SCULK_CATALYST, Blocks.SCULK_SENSOR,
            Blocks.SCULK_SHRIEKER, Blocks.SPAWNER, Blocks.SUSPICIOUS_GRAVEL, Blocks.SUSPICIOUS_SAND,
            Blocks.TNT, Blocks.TORCH, Blocks.TRAPPED_CHEST, Blocks.TRIAL_SPAWNER, Blocks.VAULT,
            Blocks.WALL_TORCH, Blocks.WATER
    );
    private static final Set<Block> SELECTABLE_ORES = Set.of(
            Blocks.ANCIENT_DEBRIS,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.NETHER_QUARTZ_ORE
    );
    private static final Direction[] DIRECTIONS = Direction.values();

    private final EnumSetting<Plugin> plugin = add(new EnumSetting<>("Plugin", Plugin.NEW));
    public final BooleanSetting wallHack = add(new BooleanSetting("WallHack", false)
            .onChange(v -> reloadWorldRenderer()));
    private final BooleanSetting onlyExposed = add(new BooleanSetting("OnlyExposed", false)
            .onChange(v -> {
                refreshRenderState(isEnabled());
                reloadWorldRenderer();
            }));
    private final IntSetting opacity = add(new IntSetting("Opacity", 35, 0, 99, 1));
    private final BooleanSetting brutForce = add(new BooleanSetting("Ore Deobf", false));
    private final BooleanSetting sendPackets = add(new BooleanSetting("Send Packets", false)
            .visibleWhen(brutForce::get));
    private final BooleanSetting grimSafe = add(new BooleanSetting("Grim Safe", true)
            .visibleWhen(() -> brutForce.get() && sendPackets.get()));
    private final BooleanSetting fast = add(new BooleanSetting("Fast", false)
            .visibleWhen(() -> brutForce.get() && sendPackets.get() && !grimSafe.get()));
    private final IntSetting delay = add(new IntSetting("Delay", 35, 5, 200, 1)
            .visibleWhen(() -> brutForce.get() && sendPackets.get()));
    private final IntSetting radius = add(new IntSetting("Radius", 5, 1, 64, 1)
            .visibleWhen(brutForce::get));
    private final IntSetting up = add(new IntSetting("Up", 5, 1, 32, 1)
            .visibleWhen(brutForce::get));
    private final IntSetting down = add(new IntSetting("Down", 5, 1, 32, 1)
            .visibleWhen(brutForce::get));
    private final BooleanSetting netherite = add(oreSetting("Netherite"));
    private final BooleanSetting diamond = add(oreSetting("Diamond"));
    private final BooleanSetting gold = add(oreSetting("Gold"));
    private final BooleanSetting iron = add(oreSetting("Iron"));
    private final BooleanSetting copper = add(oreSetting("Copper"));
    private final BooleanSetting emerald = add(oreSetting("Emerald"));
    private final BooleanSetting redstone = add(oreSetting("Redstone"));
    private final BooleanSetting lapis = add(oreSetting("Lapis"));
    private final BooleanSetting coal = add(oreSetting("Coal"));
    private final BooleanSetting quartz = add(oreSetting("Quartz"));
    private final BooleanSetting water = add(new BooleanSetting("Water", false)
            .onChange(v -> {
                refreshRenderState(isEnabled());
                reloadWorldRenderer();
            }));
    private final BooleanSetting lava = add(new BooleanSetting("Lava", false)
            .onChange(v -> {
                refreshRenderState(isEnabled());
                reloadWorldRenderer();
            }));

    private final TimerUtils delayTimer = new TimerUtils();
    private final List<BlockPos> ores = new CopyOnWriteArrayList<>();
    // Ore memory: remembers the last confirmed ore type per position, so anticheat
    // reverting a block to stone doesn't stop it rendering.
    private final ConcurrentHashMap<BlockPos, Block> oreMemory = new ConcurrentHashMap<>();
    private final ArrayList<BlockPos> toCheck = new ArrayList<>();
    private final ArrayList<BlockMemory> checked = new ArrayList<>();
    private BlockPos displayBlock;
    private int done, all;
    private AABB area = new AABB(BlockPos.ZERO);
    private volatile boolean renderActive;
    private volatile boolean exposedOnly;
    private volatile boolean translucentWalls;
    private volatile boolean renderWater;
    private volatile boolean renderLava;
    private volatile float wallOpacity;
    private volatile int wallAlpha = 255;
    private int lastOpacity = -1;
    private boolean warnedPacketsDisabled;

    private static final int ORE_CACHE_LIMIT = 4096;

    private Xray() {
        super("Xray", Category.RENDER);
    }

    private BooleanSetting oreSetting(String name) {
        return new BooleanSetting(name, false).onChange(ignored -> {
            if (isEnabled()) {
                reloadWorldRenderer();
            }
        });
    }

    @Override
    protected void onEnable() {
        refreshRenderState(true);
        lastOpacity = opacity.get();
        ores.clear();
        oreMemory.clear();
        toCheck.clear();
        checked.clear();
        if (noPlayer()) {
            reloadWorldRenderer();
            return;
        }
        toCheck.addAll(getBlocks());
        prioritizeChecks();
        all = toCheck.size();
        done = 0;
        warnedPacketsDisabled = false;
        mc.smartCull = false;
        mc.levelRenderer.allChanged();
        area = getArea();
    }

    @Override
    protected void onDisable() {
        refreshRenderState(false);
        ores.clear();
        oreMemory.clear();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
        mc.smartCull = true;
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (lastOpacity != opacity.get()) {
            lastOpacity = opacity.get();
            refreshRenderState(true);
            reloadWorldRenderer();
        }

        if (plugin.is(Plugin.NEW)) {
            checked.forEach(blockMemory -> {
                if (blockMemory.isDelayed()) {
                    addOre(blockMemory.blockPos);
                }
            });
        }
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundBlockUpdatePacket pac) {
            Block block = pac.getBlockState().getBlock();
            if (isCheckableOre(block)) {
                addOre(pac.getPos(), block);
            }
        }
    }

    @Listen
    private void onMove(MoveEvent event) {
        AABB newArea = getArea();
        if (!newArea.intersects(area)) {
            area = newArea;
            ores.removeIf(pos -> !newArea.inflate(32).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            oreMemory.keySet().removeIf(pos -> !newArea.inflate(32).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));

            if (brutForce.get() && sendPackets.get()) {
                toCheck.clear();
                toCheck.addAll(getBlocks());
                prioritizeChecks();
                checked.clear();
                all = toCheck.size();
                done = 0;
            }
        }

        if (oreMemory.size() > ORE_CACHE_LIMIT && mc.player != null) {
            BlockPos playerPos = mc.player.blockPosition();
            oreMemory.keySet().stream()
                    .sorted((a, b) -> Double.compare(b.distSqr(playerPos), a.distSqr(playerPos)))
                    .limit(oreMemory.size() - ORE_CACHE_LIMIT / 2L)
                    .toList()
                    .forEach(oreMemory::remove);
        }
        if (ores.size() > ORE_CACHE_LIMIT && mc.player != null) {
            BlockPos playerPos = mc.player.blockPosition();
            ores.stream()
                    .sorted((a, b) -> Double.compare(b.distSqr(playerPos), a.distSqr(playerPos)))
                    .limit(ores.size() - ORE_CACHE_LIMIT / 2L)
                    .toList()
                    .forEach(ores::remove);
        }
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer()) {
            return;
        }

        PoseStack stack = event.getPoseStack();

        for (BlockPos pos : new ArrayList<>(ores)) {
            Block block = oreMemory.getOrDefault(pos, mc.level.getBlockState(pos).getBlock());
            if (isDiamondOre(block) && diamond.get()) {
                draw(stack, pos, 0, 255, 255);
            } else if (isGoldOre(block) && gold.get()) {
                draw(stack, pos, 255, 215, 0);
            } else if ((block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) && iron.get()) {
                draw(stack, pos, 213, 213, 213);
            } else if ((block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) && copper.get()) {
                draw(stack, pos, 198, 118, 74);
            } else if ((block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) && emerald.get()) {
                draw(stack, pos, 0, 255, 77);
            } else if ((block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) && redstone.get()) {
                draw(stack, pos, 255, 0, 0);
            } else if ((block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) && coal.get()) {
                draw(stack, pos, 0, 0, 0);
            } else if ((block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) && lapis.get()) {
                draw(stack, pos, 38, 97, 156);
            } else if (block == Blocks.ANCIENT_DEBRIS && netherite.get()) {
                draw(stack, pos, 255, 255, 255);
            } else if (block == Blocks.NETHER_QUARTZ_ORE && quartz.get()) {
                draw(stack, pos, 170, 170, 170);
            }
        }

        if (displayBlock != null && (done != all)) {
            draw(stack, displayBlock, 255, 0, 60);
        }

        if (brutForce.get()) {
            Render3DUtils.drawOutlineBox(stack, area, new Color(149, 149, 149, 100));
        }

        if (toCheck.isEmpty() || !brutForce.get()) {
            return;
        }

        if (!sendPackets.get()) {
            if (!warnedPacketsDisabled) {
                log("Ore Deobf packets are disabled.");
                warnedPacketsDisabled = true;
            }
            return;
        }

        if (mc.isSingleplayer()) {
            log("Ore Deobf is disabled in singleplayer.");
            toggle();
            return;
        }

        if (mc.player.getMainHandItem().is(ItemTags.PICKAXES)) {
            if (mc.player.tickCount % 8 == 0) {
                log("Do not hold a pickaxe while probing ores.");
                toggle();
            }
            return;
        }

        if (delayTimer.every(delay.get())) {
            int probes = grimSafe.get() ? 1 : (fast.get() ? 3 : 1);
            for (int i = 0; i < probes && !toCheck.isEmpty(); i++) {
                BlockPos pos = nextProbe();
                sendProbe(displayBlock = pos);
                checked.add(new BlockMemory(pos));
                ++done;
            }
        }
    }

    private AABB getArea() {
        int radius_ = plugin.is(Plugin.NEW) ? Math.min(4, radius.get()) : radius.get();
        int down_ = plugin.is(Plugin.NEW) ? Math.min(3, down.get()) : down.get();
        int up_ = plugin.is(Plugin.NEW) ? Math.min(4, up.get()) : up.get();
        return new AABB(mc.player.getX() - radius_, mc.player.getY() - down_, mc.player.getZ() - radius_,
                mc.player.getX() + radius_, mc.player.getY() + up_, mc.player.getZ() + radius_);
    }

    private void draw(PoseStack stack, BlockPos pos, int r, int g, int b) {
        Render3DUtils.drawFilledBox(pos, new Color(r, g, b, 100));
        Render3DUtils.drawOutlineBox(stack, pos, new Color(r, g, b, 200));
    }

    public boolean isCheckableOre(Block block) {
        if (diamond.get() && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)) return true;
        if (gold.get() && (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE)) return true;
        if (iron.get() && (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)) return true;
        if (copper.get() && (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)) return true;
        if (emerald.get() && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)) return true;
        if (redstone.get() && (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)) return true;
        if (coal.get() && (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)) return true;
        if (netherite.get() && block == Blocks.ANCIENT_DEBRIS) return true;
        if (water.get() && block == Blocks.WATER) return true;
        if (lava.get() && block == Blocks.LAVA) return true;
        if (quartz.get() && block == Blocks.NETHER_QUARTZ_ORE) return true;
        return lapis.get() && (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE);
    }

    private void addOre(BlockPos pos) {
        if (pos != null && !ores.contains(pos)) {
            ores.add(pos);
            Block current = mc.level != null ? mc.level.getBlockState(pos).getBlock() : null;
            if (current != null && isCheckableOre(current)) {
                oreMemory.put(pos, current);
            }
        }
    }

    private void addOre(BlockPos pos, Block rememberedType) {
        if (pos != null) {
            if (!ores.contains(pos)) ores.add(pos);
            if (rememberedType != null && isCheckableOre(rememberedType)) {
                oreMemory.put(pos, rememberedType);
            }
        }
    }

    private boolean isDiamondOre(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE;
    }

    private boolean isGoldOre(Block block) {
        return block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE;
    }

    public Boolean getRenderDecision(BlockState state, BlockPos pos) {
        if (!renderActive || state == null) {
            return null;
        }
        Block block = state.getBlock();
        if (isDisabledOre(block)) {
            return false;
        }
        boolean target = isTargetBlock(block, pos);
        if (!target && translucentWalls) {
            return null;
        }
        return target;
    }

    public Boolean getSodiumRenderDecision(BlockState state, BlockPos pos) {
        return getRenderDecision(state, pos);
    }

    public boolean shouldRenderTranslucentWall(BlockState state, BlockPos pos) {
        return renderActive && translucentWalls && state != null
                && getRenderDecision(state, pos) == null;
    }

    public boolean shouldApplyWallOpacity(BlockState state, BlockPos pos) {
        return shouldRenderTranslucentWall(state, pos);
    }

    public int applyWallAlpha(int color) {
        return (color & 0x00FFFFFF) | (wallAlpha << 24);
    }

    public float getWallOpacity() {
        return wallOpacity;
    }

    public boolean shouldDisableChunkOcclusion() {
        return renderActive;
    }

    public boolean isRenderActive() {
        return renderActive;
    }

    public boolean shouldHideBlockEntity(BlockState state, BlockPos pos) {
        Boolean decision = getRenderDecision(state, pos);
        return Boolean.FALSE.equals(decision);
    }

    private ArrayList<BlockPos> getBlocks() {
        int radius_ = plugin.is(Plugin.NEW) ? Math.min(4, radius.get()) : radius.get();
        int down_ = plugin.is(Plugin.NEW) ? Math.min(3, down.get()) : down.get();
        int up_ = plugin.is(Plugin.NEW) ? Math.min(4, up.get()) : up.get();

        ArrayList<BlockPos> positions = new ArrayList<>();
        for (int x = (int) (mc.player.getX() - radius_); x < mc.player.getX() + radius_; x++) {
            for (int y = (int) (mc.player.getY() - down_); y < mc.player.getY() + up_; y++) {
                for (int z = (int) (mc.player.getZ() - radius_); z < mc.player.getZ() + radius_; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.level.getBlockState(pos).isAir()
                            || (fast.get() && plugin.is(Plugin.OLD) && (x % 2 == 0 || y % 2 == 0 || z % 2 == 0))) {
                        continue;
                    }
                    positions.add(pos);
                }
            }
        }
        return positions;
    }

    private BlockPos nextProbe() {
        if (grimSafe.get()) {
            return toCheck.remove(0);
        }
        return toCheck.remove(toCheck.size() - 1 <= 1 ? 0 : ThreadLocalRandom.current().nextInt(0, toCheck.size() - 1));
    }

    private void sendProbe(BlockPos pos) {
        PacketUtils.sendSilently(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, getProbeDirection(pos)));
    }

    private Direction getProbeDirection(BlockPos pos) {
        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dy = mc.player.getEyeY() - (pos.getY() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        if (absY >= absX && absY >= absZ) {
            return dy > 0.0 ? Direction.UP : Direction.DOWN;
        }
        if (absX >= absZ) {
            return dx > 0.0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private void prioritizeChecks() {
        if (mc.player != null) {
            toCheck.sort(Comparator.comparingDouble(pos -> pos.distSqr(mc.player.blockPosition())));
        }
    }

    private boolean isTargetBlock(Block block, BlockPos pos) {
        if (block == Blocks.WATER) {
            if (renderWater) return true;
            return mc.player != null && mc.player.isInWater();
        }
        if (block == Blocks.LAVA) {
            if (renderLava) return true;
            return mc.player != null && mc.player.isInLava();
        }
        boolean target = TARGET_BLOCKS.contains(block);
        if (target && exposedOnly && pos != null) {
            return isExposed(pos);
        }
        return target;
    }

    private boolean isDisabledOre(Block block) {
        return SELECTABLE_ORES.contains(block) && !isCheckableOre(block);
    }

    private boolean isExposed(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }
        for (Direction direction : DIRECTIONS) {
            if (mc.level.getBlockState(pos.relative(direction)).isAir()) {
                return true;
            }
        }
        return false;
    }

    public void refreshRenderState(boolean active) {
        renderActive = active;
        exposedOnly = onlyExposed.get();
        int opacityValue = opacity.get();
        translucentWalls = active && opacityValue > 0;
        renderWater = water.get();
        renderLava = lava.get();
        wallOpacity = opacityValue / 100.0f;
        wallAlpha = Math.max(0, Math.min(255, (int) (wallOpacity * 255.0f)));
    }

    private void reloadWorldRenderer() {
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    private void log(String message) {
        ChatUtils.addChatMessage("[Xray] " + message);
    }

    public static final class BlockMemory {

        private final BlockPos blockPos;
        private long time = 0;

        public BlockMemory(BlockPos blockPos) {
            this.blockPos = blockPos;
        }

        private boolean isDelayed() {
            return this.time++ > 10;
        }
    }
}
