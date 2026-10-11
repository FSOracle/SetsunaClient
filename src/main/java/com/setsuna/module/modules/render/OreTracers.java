package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.WorldChangeEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.client.ViewBobbingSuppressor;
import com.setsuna.util.render.Render3DUtils;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Draws lines from the camera to nearby ores of selected types. The ore
 * positions are cached by scanning a bounded number of loaded chunk sections
 * per tick. Large ranges therefore do not create periodic main-thread spikes.
 */
public final class OreTracers extends Module {

    public static final OreTracers INSTANCE = new OreTracers();

    private static final String SUPPRESSOR_KEY = "ore_tracers";
    private static final int SCAN_BLOCK_BUDGET_PER_TICK = 16_384;
    private static final int REFRESH_TICKS = 100;
    private static final float TRACER_START_DISTANCE = 0.1F;
    private static final double CLOSE_HIGHLIGHT_DISTANCE_SQ = 4.0 * 4.0;

    public final DoubleSetting tracerWidth = add(new DoubleSetting("Tracer Width", 1.5, 0.5, 5.0, 0.5));
    public final IntSetting tracerRange = add(new IntSetting("Range", 16, 8, 64, 8));

    public final BooleanSetting diamond = add(new BooleanSetting("Diamond", true));
    public final ColorSetting diamondColor = add(new ColorSetting("Diamond Color", new Color(0, 255, 255, 220))
            .visibleWhen(diamond::get));
    public final BooleanSetting gold = add(new BooleanSetting("Gold", true));
    public final ColorSetting goldColor = add(new ColorSetting("Gold Color", new Color(255, 215, 0, 220))
            .visibleWhen(gold::get));
    public final BooleanSetting iron = add(new BooleanSetting("Iron", true));
    public final ColorSetting ironColor = add(new ColorSetting("Iron Color", new Color(213, 213, 213, 220))
            .visibleWhen(iron::get));
    public final BooleanSetting emerald = add(new BooleanSetting("Emerald", false));
    public final ColorSetting emeraldColor = add(new ColorSetting("Emerald Color", new Color(0, 255, 77, 220))
            .visibleWhen(emerald::get));
    public final BooleanSetting redstone = add(new BooleanSetting("Redstone", false));
    public final ColorSetting redstoneColor = add(new ColorSetting("Redstone Color", new Color(255, 0, 0, 220))
            .visibleWhen(redstone::get));
    public final BooleanSetting lapis = add(new BooleanSetting("Lapis", false));
    public final ColorSetting lapisColor = add(new ColorSetting("Lapis Color", new Color(38, 97, 156, 220))
            .visibleWhen(lapis::get));
    public final BooleanSetting coal = add(new BooleanSetting("Coal", false));
    public final ColorSetting coalColor = add(new ColorSetting("Coal Color", new Color(40, 40, 40, 220))
            .visibleWhen(coal::get));
    public final BooleanSetting netherite = add(new BooleanSetting("Netherite", false));
    public final ColorSetting netheriteColor = add(new ColorSetting("Netherite Color", new Color(255, 255, 255, 220))
            .visibleWhen(netherite::get));
    public final BooleanSetting quartz = add(new BooleanSetting("Quartz", false));
    public final ColorSetting quartzColor = add(new ColorSetting("Quartz Color", new Color(170, 170, 170, 220))
            .visibleWhen(quartz::get));

    private final List<BlockPos> oreCache = new ArrayList<>();
    private ScanState scanState;
    private BlockPos lastScanCenter;
    private int lastScanRange = -1;
    private int refreshTicks;

    record OreTarget(BlockPos pos, Color color) {
    }

    record TracerLine(Vector3f from, Vector3f to, Vector3f normal) {
    }

    private record SectionCoordinate(int chunkX, int sectionY, int chunkZ) {
    }

    private static final class ScanState {
        private final BlockPos center;
        private final int range;
        private final List<SectionCoordinate> sections;
        private final List<BlockPos> found = new ArrayList<>();
        private int cursor;
        private int blockCursor;

        private ScanState(BlockPos center, int range, List<SectionCoordinate> sections) {
            this.center = center;
            this.range = range;
            this.sections = sections;
        }
    }

    private OreTracers() {
        super("Ore Tracers", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        ViewBobbingSuppressor.acquire(SUPPRESSOR_KEY);
        requestScan(true);
    }

    @Override
    protected void onDisable() {
        ViewBobbingSuppressor.release(SUPPRESSOR_KEY);
        oreCache.clear();
        scanState = null;
        lastScanCenter = null;
        lastScanRange = -1;
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            return;
        }
        requestScan(false);
        scanNextSections();
    }

    @Listen
    private void onWorldChange(WorldChangeEvent event) {
        oreCache.clear();
        scanState = null;
        lastScanCenter = null;
        lastScanRange = -1;
        refreshTicks = 0;
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer() || oreCache.isEmpty()) {
            return;
        }

        List<OreTarget> targets = collectTargets(oreCache, mc.player.blockPosition(), pos -> {
            LevelChunk chunk = mc.level.getChunkSource().getChunk(
                    pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
            return chunk == null ? null : chunk.getBlockState(pos).getBlock();
        });

        if (!targets.isEmpty()) {
            drawTracers(event.getPoseStack(), targets);
        }
    }

    List<OreTarget> collectTargets(List<BlockPos> candidates, BlockPos playerPos,
                                  Function<BlockPos, Block> blockAt) {
        List<OreTarget> targets = new ArrayList<>();
        double rangeSq = tracerRange.get() * (double) tracerRange.get();
        // A scan can finish after an ore was mined, so every published candidate
        // must still be checked against the loaded world before it is drawn.
        candidates.removeIf(pos -> {
            if (pos.distSqr(playerPos) > rangeSq) {
                return false;
            }
            Block block = blockAt.apply(pos);
            if (block == null || !isOre(block)) {
                return true;
            }
            Color c = getTracerColor(block);
            if (c != null) {
                targets.add(new OreTarget(pos, c));
            }
            return false;
        });
        return targets;
    }

    private void requestScan(boolean immediate) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        int r = tracerRange.get();
        BlockPos center = mc.player.blockPosition();
        boolean moved = lastScanCenter == null
                || Math.abs(center.getX() - lastScanCenter.getX()) >= 8
                || Math.abs(center.getY() - lastScanCenter.getY()) >= 8
                || Math.abs(center.getZ() - lastScanCenter.getZ()) >= 8;
        boolean rangeChanged = r != lastScanRange;
        if (!immediate && scanState != null && !rangeChanged) {
            return;
        }
        if (!immediate && !moved && !rangeChanged && refreshTicks++ < REFRESH_TICKS) {
            return;
        }

        int minY = Math.max(mc.level.getMinY(), center.getY() - r);
        int maxY = Math.min(mc.level.getMaxY() - 1, center.getY() + r);
        int minChunkX = (center.getX() - r) >> 4;
        int maxChunkX = (center.getX() + r) >> 4;
        int minChunkZ = (center.getZ() - r) >> 4;
        int maxChunkZ = (center.getZ() + r) >> 4;
        int minSectionY = minY >> 4;
        int maxSectionY = maxY >> 4;
        List<SectionCoordinate> sections = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    sections.add(new SectionCoordinate(chunkX, sectionY, chunkZ));
                }
            }
        }
        sections.sort((left, right) -> Integer.compare(
                sectionDistanceSq(left, center), sectionDistanceSq(right, center)));
        scanState = new ScanState(center.immutable(), r, sections);
        lastScanCenter = center.immutable();
        lastScanRange = r;
        refreshTicks = 0;
    }

    private void scanNextSections() {
        if (scanState == null || mc.level == null) {
            return;
        }
        int budget = SCAN_BLOCK_BUDGET_PER_TICK;
        while (scanState.cursor < scanState.sections.size() && budget > 0) {
            budget -= scanSection(scanState, scanState.sections.get(scanState.cursor), budget);
            if (scanState.blockCursor >= 4096) {
                scanState.cursor++;
                scanState.blockCursor = 0;
            }
        }
        if (scanState.cursor >= scanState.sections.size()) {
            oreCache.clear();
            oreCache.addAll(scanState.found);
            scanState = null;
        }
    }

    private int scanSection(ScanState state, SectionCoordinate coordinate, int budget) {
        if (!mc.level.hasChunk(coordinate.chunkX(), coordinate.chunkZ())) {
            state.blockCursor = 4096;
            return 1;
        }
        LevelChunk chunk = mc.level.getChunk(coordinate.chunkX(), coordinate.chunkZ());
        int sectionIndex = mc.level.getSectionIndexFromSectionY(coordinate.sectionY());
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            state.blockCursor = 4096;
            return 1;
        }
        LevelChunkSection section = sections[sectionIndex];
        if (state.blockCursor == 0 && (section.hasOnlyAir()
                || !section.maybeHas(blockState -> getTracerColor(blockState.getBlock()) != null))) {
            state.blockCursor = 4096;
            return 1;
        }

        int minX = Math.max(0, state.center.getX() - state.range - (coordinate.chunkX() << 4));
        int maxX = Math.min(15, state.center.getX() + state.range - (coordinate.chunkX() << 4));
        int minY = Math.max(0, state.center.getY() - state.range - (coordinate.sectionY() << 4));
        int maxY = Math.min(15, state.center.getY() + state.range - (coordinate.sectionY() << 4));
        int minZ = Math.max(0, state.center.getZ() - state.range - (coordinate.chunkZ() << 4));
        int maxZ = Math.min(15, state.center.getZ() + state.range - (coordinate.chunkZ() << 4));
        double rangeSq = state.range * (double) state.range;
        int processed = 0;
        while (state.blockCursor < 4096 && processed < budget) {
            int packed = state.blockCursor++;
            int x = packed & 15;
            int z = packed >> 4 & 15;
            int y = packed >> 8;
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                processed++;
                continue;
            }
            Block block = section.getBlockState(x, y, z).getBlock();
            if (getTracerColor(block) != null) {
                BlockPos pos = new BlockPos(
                        (coordinate.chunkX() << 4) + x,
                        (coordinate.sectionY() << 4) + y,
                        (coordinate.chunkZ() << 4) + z);
                if (pos.distSqr(state.center) <= rangeSq) {
                    state.found.add(pos);
                }
            }
            processed++;
        }
        return Math.max(1, processed);
    }

    private static int sectionDistanceSq(SectionCoordinate section, BlockPos center) {
        int dx = (section.chunkX() << 4) + 8 - center.getX();
        int dy = (section.sectionY() << 4) + 8 - center.getY();
        int dz = (section.chunkZ() << 4) + 8 - center.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void drawTracers(PoseStack stack, List<OreTarget> targets) {
        var camera = mc.getEntityRenderDispatcher().camera;
        Vec3 cameraPos = camera.position();
        PoseStack.Pose entry = stack.last();
        Matrix4f matrix = entry.pose();
        float thickness = tracerWidth.get().floatValue();
        Vector3f forward = new Vector3f(camera.forwardVector());
        Vector3f origin = new Vector3f(forward).mul(TRACER_START_DISTANCE);

        // A tracer aimed straight at an ore collapses to a point on screen.
        // Close ores also get a faint box, including when hidden by terrain.
        // Finish these fills before opening the shared line buffer.
        for (OreTarget target : targets) {
            if (isCloseTarget(target.pos(), cameraPos)) {
                Color color = target.color();
                Render3DUtils.drawFilledBox(target.pos(), new Color(color.getRed(), color.getGreen(),
                        color.getBlue(), Math.round(color.getAlpha() * 0.18F)));
            }
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

        for (OreTarget target : targets) {
            BlockPos pos = target.pos();
            Color color = target.color();
            float ex = (float) (pos.getX() + 0.5 - cameraPos.x);
            float ey = (float) (pos.getY() + 0.5 - cameraPos.y);
            float ez = (float) (pos.getZ() + 0.5 - cameraPos.z);

            appendLine(buffer, matrix, entry, origin, new Vector3f(ex, ey, ez), forward, color, thickness);
            if (isCloseTarget(pos, cameraPos)) {
                appendOutline(buffer, matrix, entry, pos, cameraPos, forward, color, thickness);
            }
        }

        MeshData meshData = buffer.build();
        if (meshData != null) {
            Render3DUtils.LINES.draw(meshData);
        }
    }

    static boolean isCloseTarget(BlockPos pos, Vec3 cameraPos) {
        // Measure to the block surface, so approaching a corner is consistent
        // with approaching a face and a camera inside the block has distance 0.
        double dx = Math.max(0.0, Math.max(pos.getX() - cameraPos.x, cameraPos.x - (pos.getX() + 1.0)));
        double dy = Math.max(0.0, Math.max(pos.getY() - cameraPos.y, cameraPos.y - (pos.getY() + 1.0)));
        double dz = Math.max(0.0, Math.max(pos.getZ() - cameraPos.z, cameraPos.z - (pos.getZ() + 1.0)));
        return dx * dx + dy * dy + dz * dz <= CLOSE_HIGHLIGHT_DISTANCE_SQ;
    }

    static TracerLine prepareLine(Vector3f from, Vector3f to, Vector3f forward) {
        if (!from.isFinite() || !to.isFinite() || !forward.isFinite()) {
            return null;
        }
        float fromDepth = from.dot(forward);
        float toDepth = to.dot(forward);
        if (fromDepth < TRACER_START_DISTANCE && toDepth < TRACER_START_DISTANCE) {
            return null;
        }
        Vector3f start = new Vector3f(from);
        Vector3f end = new Vector3f(to);
        // Keep the line shader's perspective divisions away from the camera
        // plane, with room for its slight view shrink beyond the 0.05 near plane.
        if (fromDepth < TRACER_START_DISTANCE) {
            start.lerp(end, (TRACER_START_DISTANCE - fromDepth) / (toDepth - fromDepth));
        } else if (toDepth < TRACER_START_DISTANCE) {
            end.lerp(start, (TRACER_START_DISTANCE - toDepth) / (fromDepth - toDepth));
        }
        Vector3f normal = new Vector3f(end).sub(start);
        if (normal.lengthSquared() < 1.0E-8F
                || new Vector3f(start).cross(end).lengthSquared()
                <= start.lengthSquared() * end.lengthSquared() * 1.0E-12F) {
            return null;
        }
        normal.normalize();
        // The shader projects Position + Normal at both endpoints. Pointing
        // toward greater depth keeps that sample out of the camera plane too.
        if (normal.dot(forward) < 0.0F) {
            normal.negate();
        }
        return new TracerLine(start, end, normal);
    }

    private static void appendLine(BufferBuilder buffer, Matrix4f matrix, PoseStack.Pose entry,
                                   Vector3f from, Vector3f to, Vector3f forward, Color color, float thickness) {
        TracerLine line = prepareLine(from, to, forward);
        if (line == null) {
            return;
        }
        Vector3f normal = line.normal();
        buffer.addVertex(matrix, line.from().x, line.from().y, line.from().z)
                .setColor(color.getRGB()).setNormal(entry, normal.x, normal.y, normal.z).setLineWidth(thickness);
        buffer.addVertex(matrix, line.to().x, line.to().y, line.to().z)
                .setColor(color.getRGB()).setNormal(entry, normal.x, normal.y, normal.z).setLineWidth(thickness);
    }

    private static void appendOutline(BufferBuilder buffer, Matrix4f matrix, PoseStack.Pose entry,
                                      BlockPos pos, Vec3 cameraPos, Vector3f forward, Color color, float thickness) {
        Vector3f[] corners = new Vector3f[8];
        for (int corner = 0; corner < corners.length; corner++) {
            corners[corner] = new Vector3f(
                    (float) (pos.getX() + (corner & 1) - cameraPos.x),
                    (float) (pos.getY() + ((corner >> 1) & 1) - cameraPos.y),
                    (float) (pos.getZ() + ((corner >> 2) & 1) - cameraPos.z));
        }
        for (int axis = 1; axis <= 4; axis <<= 1) {
            for (int corner = 0; corner < corners.length; corner++) {
                if ((corner & axis) == 0) {
                    appendLine(buffer, matrix, entry, corners[corner], corners[corner | axis],
                            forward, color, thickness);
                }
            }
        }
    }

    private Color getTracerColor(Block block) {
        if (diamond.get() && isDiamondOre(block)) return diamondColor.get();
        if (gold.get() && isGoldOre(block)) return goldColor.get();
        if (iron.get() && isIronOre(block)) return ironColor.get();
        if (emerald.get() && isEmeraldOre(block)) return emeraldColor.get();
        if (redstone.get() && isRedstoneOre(block)) return redstoneColor.get();
        if (lapis.get() && isLapisOre(block)) return lapisColor.get();
        if (coal.get() && isCoalOre(block)) return coalColor.get();
        if (netherite.get() && block == Blocks.ANCIENT_DEBRIS) return netheriteColor.get();
        if (quartz.get() && block == Blocks.NETHER_QUARTZ_ORE) return quartzColor.get();
        return null;
    }

    private boolean isOre(Block block) {
        return isDiamondOre(block) || isGoldOre(block) || isIronOre(block)
                || isEmeraldOre(block) || isRedstoneOre(block) || isLapisOre(block)
                || isCoalOre(block) || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.NETHER_QUARTZ_ORE;
    }

    private boolean isDiamondOre(Block b) {
        return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE;
    }

    private boolean isGoldOre(Block b) {
        return b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE;
    }

    private boolean isIronOre(Block b) {
        return b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE;
    }

    private boolean isEmeraldOre(Block b) {
        return b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE;
    }

    private boolean isRedstoneOre(Block b) {
        return b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE;
    }

    private boolean isLapisOre(Block b) {
        return b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE;
    }

    private boolean isCoalOre(Block b) {
        return b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE;
    }
}
