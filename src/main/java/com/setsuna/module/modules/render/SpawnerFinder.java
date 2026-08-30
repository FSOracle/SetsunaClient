package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.Canvas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Finds likely dungeon spawners from the structure of loaded chunks. */
public final class SpawnerFinder extends Module {

    public static final SpawnerFinder INSTANCE = new SpawnerFinder();

    private static final int VERTICAL_SCAN_RANGE = 100;
    private static final int MAX_SCAN_Y_EXCLUSIVE = 65;
    private static final float OUTLINE_WIDTH = 5.0F;
    private static final Direction[] DIRECTIONS = Direction.values();

    public final IntSetting range = add(new IntSetting("Range", 80, 16, 256, 1));
    public final IntSetting scanDelay = add(new IntSetting("ScanDelay", 5, 1, 15, 1));
    public final IntSetting exploredDist = add(new IntSetting("ExploredDist", 10, 3, 30, 1));
    public final DoubleSetting tagScale = add(new DoubleSetting("TagScale", 1.0, 0.5, 3.0, 0.1));
    public final IntSetting alpha = add(new IntSetting("Alpha", 215, 40, 255, 1));

    private volatile Map<BlockPos, Candidate> candidates = Map.of();
    private final Set<BlockPos> explored = ConcurrentHashMap.newKeySet();
    private final List<LabelDrawData> labels = new ArrayList<>();

    private volatile boolean scanning;
    private volatile Thread scanThread;
    private Level trackedLevel;
    private ResourceKey<Level> trackedDimension;
    private long lastScanTime;

    private SpawnerFinder() {
        super("SpawnerFinder", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        resetState();
        if (!noPlayer()) {
            trackedLevel = mc.level;
            trackedDimension = mc.level.dimension();
            startScan();
        }
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            resetState();
            return;
        }

        Level level = mc.level;
        ResourceKey<Level> dimension = level.dimension();
        if (trackedLevel != level || trackedDimension != dimension) {
            stopScan();
            candidates = Map.of();
            explored.clear();
            trackedLevel = level;
            trackedDimension = dimension;
            lastScanTime = 0L;
        }

        markExploredCandidates();

        long now = System.currentTimeMillis();
        if (!scanning && now - lastScanTime >= scanDelay.get() * 1_000L) {
            startScan();
        }
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer() || candidates.isEmpty()) {
            return;
        }

        labels.clear();
        double maxDistanceSq = range.get() * (double) range.get();
        Vec3 playerPosition = mc.player.position();
        for (Candidate candidate : candidates.values()) {
            if (explored.contains(candidate.center())
                    || candidate.center().distToCenterSqr(playerPosition.x, playerPosition.y, playerPosition.z) > maxDistanceSq) {
                continue;
            }
            Render3DUtils.drawOutlineBox(event.getPoseStack(), candidate.box(), withAlpha(candidate.color()), OUTLINE_WIDTH);
            addLabel(candidate, playerPosition);
        }
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (labels.isEmpty()) {
            return;
        }

        Canvas canvas = event.canvas();
        for (LabelDrawData data : labels) {
            canvas.save();
            canvas.translate(data.x(), data.y());
            canvas.scale(data.scale(), data.scale());
            float labelWidth = SkijaUi.textWidth(data.label());
            float distanceWidth = SkijaUi.textWidth(data.distance());
            float x = -(labelWidth + distanceWidth) * 0.5F;
            SkijaUi.text(canvas, data.label(), x, -4.5F, 9.0F, data.color().getRGB());
            SkijaUi.text(canvas, data.distance(), x + labelWidth, -4.5F, 9.0F, Color.WHITE.getRGB());
            canvas.restore();
        }
        labels.clear();
    }

    private void startScan() {
        stopScan();
        Level level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        BlockPos origin = mc.player.blockPosition();
        lastScanTime = System.currentTimeMillis();
        scanning = true;

        Thread thread = new Thread(() -> {
            try {
                Map<BlockPos, Candidate> fresh = new HashMap<>();
                scanMossyCandidates(fresh, level, origin);
                if (scanCancelled()) {
                    return;
                }
                scanCobblestoneCandidates(fresh, level, origin);
                if (!scanCancelled()
                        && Thread.currentThread() == scanThread
                        && mc.level == level
                        && mc.level.dimension() == dimension) {
                    candidates = Map.copyOf(fresh);
                }
            } catch (Throwable ignored) {
                // Loaded chunks can change while the background scan is reading them.
            } finally {
                if (Thread.currentThread() == scanThread) {
                    scanning = false;
                }
            }
        }, "SpawnerFinder-Scanner");
        thread.setDaemon(true);
        scanThread = thread;
        thread.start();
    }

    private void scanMossyCandidates(Map<BlockPos, Candidate> output, Level level, BlockPos origin) {
        Set<BlockPos> cobblestone = new HashSet<>();
        Set<BlockPos> mossyCobblestone = new HashSet<>();
        Set<BlockPos> spawners = new HashSet<>();
        Set<BlockPos> chests = new HashSet<>();
        scanBlocks(level, origin, (pos, state) -> {
            Block block = state.getBlock();
            if (block == Blocks.COBBLESTONE) {
                cobblestone.add(pos);
            } else if (block == Blocks.MOSSY_COBBLESTONE) {
                mossyCobblestone.add(pos);
            } else if (block == Blocks.SPAWNER) {
                spawners.add(pos);
            } else if (block == Blocks.CHEST) {
                chests.add(pos);
            }
        });

        if (mossyCobblestone.isEmpty()) {
            return;
        }

        Set<BlockPos> structural = new HashSet<>(cobblestone);
        structural.addAll(mossyCobblestone);
        Set<BlockPos> visited = new HashSet<>();

        for (BlockPos start : mossyCobblestone) {
            if (scanCancelled()) {
                return;
            }
            if (visited.contains(start)) {
                continue;
            }

            Set<BlockPos> component = floodNearby(start, structural, visited);
            Bounds bounds = Bounds.from(component, cobblestone, mossyCobblestone);
            if (bounds.totalCount() < 16 || bounds.mossyCount() < 2) {
                continue;
            }

            int width = bounds.width();
            int height = bounds.height();
            int depth = bounds.depth();
            if (width > 13 || depth > 13 || height > 8 || width * height * depth > 800
                    || hasInsufficientSolidCover(bounds, level)
                    || containsDecoration(bounds, level)
                    || !hasSolidShell(bounds, 0.5F, level)) {
                continue;
            }

            boolean hasSpawner = containsWithin(spawners, bounds.inflate(1));
            boolean hasChest = containsWithin(chests, bounds.inflate(1));
            int embeddedStone = countEmbeddedStone(bounds, structural, level);
            Candidate candidate = new Candidate(bounds.toBox(), bounds.center(), bounds.totalCount(),
                    bounds.mossyCount(), hasSpawner, hasChest, embeddedStone, false);
            if (candidate.confidence() >= 0.3F) {
                output.put(candidate.center(), candidate);
            }
        }
    }

    private void scanCobblestoneCandidates(Map<BlockPos, Candidate> output, Level level, BlockPos origin) {
        List<BlockPos> cobblestone = new ArrayList<>();
        scanBlocks(level, origin, (pos, state) -> {
            Block block = state.getBlock();
            if (block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE) {
                cobblestone.add(pos);
            }
        });
        if (cobblestone.isEmpty()) {
            return;
        }

        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos start : cobblestone) {
            if (scanCancelled()) {
                return;
            }
            if (visited.contains(start)) {
                continue;
            }

            Set<BlockPos> component = floodConnected(start, level);
            visited.addAll(component);
            if (component.size() < 15) {
                continue;
            }

            Bounds bounds = Bounds.from(component, component, Set.of());
            int width = bounds.width();
            int height = bounds.height();
            int depth = bounds.depth();
            if (width < 5 || width > 13 || depth < 5 || depth > 13 || height < 3 || height > 7
                    || width * height * depth > 800
                    || containsDecoration(bounds, level)
                    || !hasSolidShell(bounds, 0.5F, level)
                    || containsAny(bounds, level, Blocks.SPAWNER, Blocks.CHEST, Blocks.MOSSY_COBBLESTONE)
                    || hasInsufficientSolidCover(bounds, level)
                    || !hasValidInterior(bounds, level)) {
                continue;
            }

            int embeddedStone = countEmbeddedStone(bounds, component, level);
            if (embeddedStone < 1) {
                continue;
            }
            Candidate candidate = new Candidate(bounds.toBox(), bounds.center(), component.size(), 0,
                    false, false, embeddedStone, true);
            if (!output.containsKey(candidate.center()) && candidate.confidence() >= 0.3F) {
                output.put(candidate.center(), candidate);
            }
        }
    }

    private void scanBlocks(Level level, BlockPos origin, BlockVisitor visitor) {
        int scanRange = range.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int offsetX = -scanRange; offsetX <= scanRange; offsetX++) {
            if (scanCancelled()) {
                return;
            }
            for (int offsetZ = -scanRange; offsetZ <= scanRange; offsetZ++) {
                if (offsetX * offsetX + offsetZ * offsetZ > scanRange * scanRange) {
                    continue;
                }
                int x = origin.getX() + offsetX;
                int z = origin.getZ() + offsetZ;
                ChunkAccess chunk = level.getChunk(SectionPos.blockToSectionCoord(x),
                        SectionPos.blockToSectionCoord(z), ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (int offsetY = -VERTICAL_SCAN_RANGE; offsetY <= VERTICAL_SCAN_RANGE; offsetY++) {
                    int y = origin.getY() + offsetY;
                    if (y < level.getMinY() || y >= level.getMaxY() || !isBelowScanCeiling(y)) {
                        continue;
                    }
                    cursor.set(x, y, z);
                    BlockState state = chunk.getBlockState(cursor);
                    if (!state.isAir()) {
                        visitor.visit(cursor.immutable(), state);
                    }
                }
            }
        }
    }

    static boolean isBelowScanCeiling(int blockY) {
        return blockY < MAX_SCAN_Y_EXCLUSIVE;
    }

    private Set<BlockPos> floodNearby(BlockPos start, Set<BlockPos> structural, Set<BlockPos> visited) {
        Set<BlockPos> component = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        component.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        if (structural.contains(next) && visited.add(next)) {
                            component.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return component;
    }

    private Set<BlockPos> floodConnected(BlockPos start, Level level) {
        Set<BlockPos> component = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        component.add(start);
        queue.add(start);

        while (!queue.isEmpty() && component.size() <= 200) {
            BlockPos current = queue.poll();
            for (Direction direction : DIRECTIONS) {
                BlockPos next = current.relative(direction);
                Block block = level.getBlockState(next).getBlock();
                if ((block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE) && component.add(next)) {
                    queue.add(next);
                }
            }
        }
        return component;
    }

    private boolean hasSolidShell(Bounds bounds, float requiredRatio, Level level) {
        int solid = 0;
        int total = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX() - 1; x <= bounds.maxX() + 1; x++) {
            for (int z = bounds.minZ() - 1; z <= bounds.maxZ() + 1; z++) {
                if (x > bounds.minX() && x < bounds.maxX() && z > bounds.minZ() && z < bounds.maxZ()) {
                    continue;
                }
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    total++;
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        solid++;
                    }
                }
            }
        }
        return total > 0 && solid / (float) total >= requiredRatio;
    }

    private boolean containsDecoration(Bounds bounds, Level level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (isDecoration(level.getBlockState(cursor.set(x, y, z)).getBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasInsufficientSolidCover(Bounds bounds, Level level) {
        int solid = 0;
        int maxY = Math.min(bounds.maxY() + 21, level.getMaxY() - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = bounds.maxY() + 1; y <= maxY; y++) {
            if (level.getBlockState(cursor.set(bounds.centerX(), y, bounds.centerZ())).isSolidRender()) {
                solid++;
            }
        }
        return hasInsufficientSolidCover(solid);
    }

    static boolean hasInsufficientSolidCover(int solidBlockCount) {
        return solidBlockCount < 5;
    }

    private boolean hasValidInterior(Bounds bounds, Level level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX() + 1; x < bounds.maxX(); x++) {
            for (int z = bounds.minZ() + 1; z < bounds.maxZ(); z++) {
                for (int y = bounds.minY() + 1; y < bounds.maxY(); y++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    Block block = state.getBlock();
                    if (!state.isAir() && block != Blocks.STONE && block != Blocks.DEEPSLATE
                            && block != Blocks.COBBLESTONE && state.getFluidState().isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private int countEmbeddedStone(Bounds bounds, Set<BlockPos> structure, Level level) {
        if (bounds.width() <= 2 || bounds.height() <= 2 || bounds.depth() <= 2) {
            return 0;
        }

        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX() + 1; x < bounds.maxX(); x++) {
            for (int y = bounds.minY() + 1; y < bounds.maxY(); y++) {
                for (int z = bounds.minZ() + 1; z < bounds.maxZ(); z++) {
                    cursor.set(x, y, z);
                    if (structure.contains(cursor.immutable()) || level.getBlockState(cursor).getBlock() != Blocks.STONE) {
                        continue;
                    }
                    for (Direction direction : DIRECTIONS) {
                        if (level.getBlockState(cursor.relative(direction)).isAir()) {
                            count++;
                            if (count >= 4) {
                                return count;
                            }
                            break;
                        }
                    }
                }
            }
        }
        return count;
    }

    private boolean containsAny(Bounds bounds, Level level, Block... blocks) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Block found = level.getBlockState(cursor.set(x, y, z)).getBlock();
                    for (Block block : blocks) {
                        if (found == block) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean containsWithin(Set<BlockPos> positions, Bounds bounds) {
        for (BlockPos pos : positions) {
            if (bounds.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDecoration(Block block) {
        return block == Blocks.TORCH || block == Blocks.WALL_TORCH
                || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH
                || block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN
                || block == Blocks.IRON_BARS || block == Blocks.GLASS || block == Blocks.GLASS_PANE
                || block == Blocks.LADDER || block == Blocks.STONE_BRICKS
                || block == Blocks.CRACKED_STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS
                || block == Blocks.CHISELED_STONE_BRICKS || block == Blocks.STONE_BRICK_STAIRS
                || block == Blocks.STONE_BRICK_SLAB || block == Blocks.POLISHED_ANDESITE
                || block == Blocks.POLISHED_DIORITE || block == Blocks.POLISHED_GRANITE
                || block == Blocks.SMOOTH_STONE || block == Blocks.BRICKS || block == Blocks.BOOKSHELF
                || block == Blocks.CRAFTING_TABLE || block == Blocks.FURNACE || block == Blocks.ANVIL
                || block == Blocks.ENCHANTING_TABLE || block == Blocks.BREWING_STAND
                || block == Blocks.JACK_O_LANTERN || block == Blocks.PUMPKIN || block == Blocks.CARVED_PUMPKIN
                || block instanceof StairBlock || block instanceof SlabBlock
                || block instanceof FenceBlock || block instanceof WallBlock;
    }

    private void markExploredCandidates() {
        double distance = exploredDist.get();
        double maxDistanceSq = distance * distance;
        Vec3 playerPosition = mc.player.position();
        for (Candidate candidate : candidates.values()) {
            if (!explored.contains(candidate.center())
                    && candidate.center().distToCenterSqr(playerPosition.x, mc.player.getEyeY(), playerPosition.z) <= maxDistanceSq) {
                explored.add(candidate.center());
            }
        }
    }

    private void addLabel(Candidate candidate, Vec3 playerPosition) {
        Vec3 worldPosition = new Vec3(candidate.center().getX() + 0.5,
                candidate.box().maxY + 0.2, candidate.center().getZ() + 0.5);
        Vector3f projected = WorldToScreen.getWorldPositionToScreen(worldPosition);
        if (projected == null || projected.z < 0.0F || projected.z > 1.0F) {
            return;
        }

        double guiScale = mc.getWindow().getGuiScale();
        float x = (float) (projected.x / guiScale);
        float y = (float) (projected.y / guiScale);
        if (x < 0.0F || y < 0.0F || x > mc.getWindow().getGuiScaledWidth() || y > mc.getWindow().getGuiScaledHeight()) {
            return;
        }

        double distance = Math.sqrt(candidate.center().distToCenterSqr(playerPosition.x, playerPosition.y, playerPosition.z));
        String label = chinese() ? (candidate.questionable() ? "\u5237\u602a\u7b3c?" : "\u5237\u602a\u7b3c")
                : (candidate.questionable() ? "Spawner?" : "Spawner");
        labels.add(new LabelDrawData(label, String.format(Locale.ROOT, " [%.0fm]", distance),
                x, y, tagScale.get().floatValue(), candidate.color()));
    }

    private boolean chinese() {
        String language = mc.options.languageCode;
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
    }

    private Color withAlpha(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha.get());
    }

    private boolean scanCancelled() {
        return Thread.currentThread().isInterrupted() || !scanning || Thread.currentThread() != scanThread;
    }

    private void stopScan() {
        scanning = false;
        Thread thread = scanThread;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        scanThread = null;
    }

    private void resetState() {
        stopScan();
        trackedLevel = null;
        trackedDimension = null;
        candidates = Map.of();
        explored.clear();
        labels.clear();
        lastScanTime = 0L;
    }

    @FunctionalInterface
    private interface BlockVisitor {
        void visit(BlockPos pos, BlockState state);
    }

    private record LabelDrawData(String label, String distance, float x, float y, float scale, Color color) {
    }

    record Candidate(AABB box, BlockPos center, int totalCount, int mossyCount,
                     boolean hasSpawner, boolean hasChest, int embeddedStone, boolean uncertain) {

        float confidence() {
            float confidence = 0.0F;
            if (uncertain) {
                if (embeddedStone == 1) {
                    confidence = 0.4F;
                }
                if (embeddedStone == 2) {
                    confidence += 0.55F;
                }
                if (embeddedStone >= 3) {
                    confidence += 0.7F;
                }
                confidence += 0.15F;
                if (center.getY() < 50) {
                    confidence += 0.05F;
                }
                if (center.getY() < 30) {
                    confidence += 0.05F;
                }
                return Math.min(1.0F, confidence);
            }

            if (mossyCount >= 3) {
                confidence = 0.25F;
            }
            if (mossyCount >= 6) {
                confidence += 0.15F;
            }
            if (totalCount >= 16) {
                confidence += 0.1F;
            }
            if (totalCount >= 24) {
                confidence += 0.1F;
            }
            float mossyRatio = mossyCount / (float) Math.max(1, totalCount);
            if (mossyRatio > 0.15F && mossyRatio < 0.7F) {
                confidence += 0.1F;
            }
            if (hasSpawner) {
                confidence += 0.5F;
            }
            if (hasChest) {
                confidence += 0.15F;
            }
            if (embeddedStone == 1) {
                confidence += 0.25F;
            }
            if (embeddedStone == 2) {
                confidence += 0.35F;
            }
            if (embeddedStone >= 3) {
                confidence += 0.4F;
            }
            if (center.getY() < 50) {
                confidence += 0.05F;
            }
            if (center.getY() < 30) {
                confidence += 0.05F;
            }
            return Math.min(1.0F, confidence);
        }

        boolean questionable() {
            return uncertain;
        }

        Color color() {
            float confidence = confidence();
            if (uncertain) {
                if (confidence >= 0.7F) {
                    return new Color(128, 0, 255);
                }
                if (confidence >= 0.5F) {
                    return new Color(180, 100, 255);
                }
                return new Color(200, 150, 255);
            }
            if (hasSpawner) {
                return new Color(0, 255, 0);
            }
            if (confidence >= 0.7F) {
                return new Color(0, 255, 128);
            }
            if (confidence >= 0.5F) {
                return new Color(255, 255, 0);
            }
            if (confidence >= 0.3F) {
                return new Color(255, 165, 0);
            }
            return new Color(255, 80, 80);
        }
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          int cobblestoneCount, int mossyCount) {

        private static Bounds from(Set<BlockPos> positions, Set<BlockPos> cobblestone, Set<BlockPos> mossy) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            int cobblestoneCount = 0;
            int mossyCount = 0;
            for (BlockPos pos : positions) {
                if (cobblestone.contains(pos)) {
                    cobblestoneCount++;
                }
                if (mossy.contains(pos)) {
                    mossyCount++;
                }
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ, cobblestoneCount, mossyCount);
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }

        private int depth() {
            return maxZ - minZ + 1;
        }

        private int totalCount() {
            return cobblestoneCount + mossyCount;
        }

        private int centerX() {
            return (minX + maxX) / 2;
        }

        private int centerZ() {
            return (minZ + maxZ) / 2;
        }

        private BlockPos center() {
            return new BlockPos(centerX(), (minY + maxY) / 2, centerZ());
        }

        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        private Bounds inflate(int amount) {
            return new Bounds(minX - amount, minY - amount, minZ - amount,
                    maxX + amount, maxY + amount, maxZ + amount, cobblestoneCount, mossyCount);
        }

        private AABB toBox() {
            return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
        }
    }
}
