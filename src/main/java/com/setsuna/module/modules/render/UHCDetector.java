package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.Canvas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * UHC structure detector. Sign-tower / piston-machinery heuristics plus a buried
 * check identify traps; signature-block counts separate ancient cities from
 * trial chambers. Scanning runs on a background daemon thread; the 3D outlines
 * and 2D labels are drawn on the render thread from an immutable snapshot.
 */
public final class UHCDetector extends Module {

    public static final UHCDetector INSTANCE = new UHCDetector();

    private UHCDetector() {
        super("UHC Detector", Category.RENDER);
    }

    private static boolean chinese() {
        return net.minecraft.client.Minecraft.getInstance().options.languageCode != null
                && net.minecraft.client.Minecraft.getInstance().options.languageCode.startsWith("zh");
    }

    private static String labelFor(DetectionType type) {
        boolean zh = chinese();
        return switch (type) {
            case Trap -> zh ? "陷阱" : "Trap";
            case AncientCity -> zh ? "古城" : "Ancient City";
            case TrialChamber -> zh ? "试炼大厅" : "Trial Chamber";
            case NetherPortal -> zh ? "地狱传送门" : "Nether Portal";
            case EndPortal -> zh ? "末地传送门" : "End Portal";
            case None -> "";
        };
    }

    public final BooleanSetting traps = add(new BooleanSetting("Traps", true));
    public final BooleanSetting ancientCities = add(new BooleanSetting("Ancient Cities", true));
    public final BooleanSetting trialChambers = add(new BooleanSetting("Trial Chambers", true));
    public final BooleanSetting corpses = add(new BooleanSetting("Corpses", true));
    public final BooleanSetting netherPortals = add(new BooleanSetting("Nether Portals", true));
    public final BooleanSetting endPortals = add(new BooleanSetting("End Portals", true));
    public final BooleanSetting ignoreCaveBiomes = add(new BooleanSetting("Ignore Cave Biomes", true)
            .visibleWhen(() -> traps.get()));
    public final BooleanSetting shaftVerify = add(new BooleanSetting("Shaft Verify", true)
            .visibleWhen(() -> traps.get()));
    public final IntSetting minSignStack = add(new IntSetting("Min Sign Stack", 2, 2, 6, 1)
            .visibleWhen(() -> traps.get() && shaftVerify.get()));
    public final IntSetting minPistons = add(new IntSetting("Min Pistons", 2, 1, 8, 1)
            .visibleWhen(() -> traps.get() && shaftVerify.get()));
    public final IntSetting range = add(new IntSetting("Range", 96, 16, 192, 8));
    public final IntSetting verticalRange = add(new IntSetting("Vertical Range", 96, 16, 384, 8));
    public final IntSetting scanDelay = add(new IntSetting("Scan Delay", 750, 250, 3000, 50));
    public final IntSetting clusterGap = add(new IntSetting("Cluster Gap", 8, 1, 24, 1));
    public final IntSetting maxBoxes = add(new IntSetting("Max Boxes", 24, 1, 80, 1));
    public final DoubleSetting lineWidth = add(new DoubleSetting("Line Width", 2.0, 0.5, 6.0, 0.5));
    public final BooleanSetting labels = add(new BooleanSetting("Labels", true));
    public final DoubleSetting labelScale = add(new DoubleSetting("Label Scale", 0.8, 0.3, 1.5, 0.1)
            .visibleWhen(() -> labels.get()));
    public final ColorSetting trapColor = add(new ColorSetting("Trap Color", new Color(255, 75, 75, 230)));
    public final ColorSetting ancientCityColor = add(new ColorSetting("Ancient City Color", new Color(125, 90, 255, 230)));
    public final ColorSetting trialChamberColor = add(new ColorSetting("Trial Chamber Color", new Color(255, 185, 70, 230)));
    public final ColorSetting netherPortalColor = add(new ColorSetting("Nether Portal Color", new Color(190, 70, 255, 230))
            .visibleWhen(netherPortals::get));
    public final ColorSetting endPortalColor = add(new ColorSetting("End Portal Color", new Color(70, 220, 150, 230))
            .visibleWhen(endPortals::get));
    public final ColorSetting labelTextColor = add(new ColorSetting("Label Text Color", new Color(255, 255, 255, 245))
            .visibleWhen(() -> labels.get()));
    public final ColorSetting labelBackgroundColor = add(new ColorSetting("Label Background Color", new Color(0, 0, 0, 130))
            .visibleWhen(() -> labels.get()));

    // Replaced atomically by the background scan thread (immutable snapshot); the
    // render thread only reads it, avoiding locks and copies.
    private volatile List<Detection> detections = List.of();
    private final List<LabelDrawData> labelDrawList = new ArrayList<>();
    private long lastScanTime;

    private volatile boolean scanning;
    private volatile Thread scanThread;

    @Override
    protected void onEnable() {
        lastScanTime = 0L;
        detections = List.of();
        labelDrawList.clear();
    }

    @Override
    protected void onDisable() {
        stopScan();
        detections = List.of();
        labelDrawList.clear();
    }

    private void stopScan() {
        scanning = false;
        Thread thread = scanThread;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        scanThread = null;
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (scanning || now - lastScanTime < scanDelay.get()) {
            return;
        }
        lastScanTime = now;
        startScan();
    }

    /** Starts a background daemon scan; results are validated for world/dimension/thread consistency before being committed, to avoid cross-world leftovers. */
    private void startScan() {
        Level level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        var dimension = level.dimension();
        BlockPos origin = mc.player.blockPosition();

        scanning = true;
        Thread thread = new Thread(() -> {
            try {
                List<Detection> fresh = computeDetections(level, origin);
                if (!Thread.currentThread().isInterrupted()
                        && scanning
                        && Thread.currentThread() == scanThread
                        && mc.level == level
                        && mc.level.dimension() == dimension) {
                    detections = List.copyOf(fresh);
                }
            } catch (Throwable ignored) {
                // Reading loaded chunks off-thread can rarely race; skip this round.
            } finally {
                if (Thread.currentThread() == scanThread) {
                    scanning = false;
                }
            }
        }, "UHCDetector-Scanner");
        thread.setDaemon(true);
        scanThread = thread;
        thread.start();
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer()) {
            return;
        }
        labelDrawList.clear();
        float thickness = lineWidth.get().floatValue();
        Vec3 eyePos = mc.player.getEyePosition();

        for (Detection detection : detections) {
            if (isPlayerInside(detection.box())) {
                continue;
            }
            Render3DUtils.drawOutlineBox(event.getPoseStack(), detection.box(), detection.color(), thickness);
            if (labels.get()) {
                addLabel(detection, eyePos);
            }
        }
        if (corpses.get()) {
            renderCorpses(event, thickness);
        }
    }

    private void renderCorpses(Render3DEvent event, float thickness) {
        double maxDistanceSq = range.get() * (double) range.get();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand armorStand)
                    || !armorStand.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD)
                    || armorStand.distanceToSqr(mc.player) > maxDistanceSq) {
                continue;
            }
            Vec3 pos = armorStand.position();
            AABB playerBox = new AABB(pos.x - 0.3, pos.y, pos.z - 0.3,
                    pos.x + 0.3, pos.y + 1.8, pos.z + 0.3);
            Render3DUtils.drawOutlineBox(event.getPoseStack(), playerBox, 0xFFFF3030, thickness);
        }
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (!labels.get() || labelDrawList.isEmpty()) {
            return;
        }
        Canvas canvas = event.canvas();
        float paddingX = 4.0f;
        float paddingY = 2.0f;
        float textHeight = 9.0f;
        int background = labelBackgroundColor.argb();
        int text = labelTextColor.argb();

        for (LabelDrawData data : labelDrawList) {
            canvas.save();
            canvas.translate(data.x(), data.y());
            canvas.scale(data.scale(), data.scale());
            float width = SkijaUi.textWidth(data.text()) + paddingX * 2.0f;
            float height = textHeight + paddingY * 2.0f;
            float x = -width * 0.5f;
            float y = -height;
            SkijaUi.fill(canvas, x, y, width, height, background);
            SkijaUi.text(canvas, data.text(), x + paddingX, y + paddingY, textHeight, text);
            canvas.restore();
        }
        labelDrawList.clear();
    }

    /** Runs on the background thread: scan → cluster → verify → sort/truncate by distance. Touches no shared state. */
    private List<Detection> computeDetections(Level level, BlockPos origin) {
        ScanBuckets buckets = scanLoadedChunks(level, origin);
        List<Detection> fresh = new ArrayList<>();
        int gap = clusterGap.get();

        if (traps.get()) {
            List<MutableDetectionBox> trapClusters = buildClusters(buckets.traps(), gap, 1, true);
            if (shaftVerify.get()) {
                trapClusters.removeIf(cluster -> !verifyTrapCluster(level, cluster));
            }
            addDetections(fresh, trapClusters, DetectionType.Trap, trapColor.get());
        }
        if (ancientCities.get()) {
            List<MutableDetectionBox> ancientCityClusters = buildAncientCityClusters(buckets.ancientCities(), gap + 4);
            retainUndergroundClusters(level, ancientCityClusters);
            addDetections(fresh, ancientCityClusters, DetectionType.AncientCity, ancientCityColor.get());
        }
        if (trialChambers.get()) {
            List<MutableDetectionBox> trialChamberClusters = buildClusters(buckets.trialChambers(), gap + 4, 8, true);
            retainUndergroundClusters(level, trialChamberClusters);
            addDetections(fresh, trialChamberClusters, DetectionType.TrialChamber, trialChamberColor.get());
        }
        if (netherPortals.get()) {
            addDetections(fresh, buildClusters(buckets.netherPortals(), 1, 1, true),
                    DetectionType.NetherPortal, netherPortalColor.get());
        }
        if (endPortals.get()) {
            addDetections(fresh, buildClusters(buckets.endPortals(), 1, 1, true),
                    DetectionType.EndPortal, endPortalColor.get());
        }
        fresh.sort(Comparator.comparingDouble(detection -> detection.center().distSqr(origin)));
        if (fresh.size() > maxBoxes.get()) {
            fresh = new ArrayList<>(fresh.subList(0, maxBoxes.get()));
        }
        return fresh;
    }

    private ScanBuckets scanLoadedChunks(Level level, BlockPos center) {
        List<DetectionPoint> trapPoints = new ArrayList<>();
        List<DetectionPoint> ancientCityPoints = new ArrayList<>();
        List<DetectionPoint> trialChamberPoints = new ArrayList<>();
        List<DetectionPoint> netherPortalPoints = new ArrayList<>();
        List<DetectionPoint> endPortalPoints = new ArrayList<>();

        int blockRange = range.get();
        int yRange = verticalRange.get();
        int minY = Math.max(level.getMinY(), center.getY() - yRange);
        int maxY = Math.min(level.getMaxY(), center.getY() + yRange);
        int chunkRadius = SectionPos.blockToSectionCoord(blockRange) + 1;
        int centerChunkX = SectionPos.blockToSectionCoord(center.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(center.getZ());
        double maxDistanceSq = blockRange * (double) blockRange;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                if (Thread.currentThread().isInterrupted()) {
                    return new ScanBuckets(trapPoints, ancientCityPoints, trialChamberPoints,
                            netherPortalPoints, endPortalPoints);
                }
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }

                chunk.findBlocks(state -> classify(state).enabled(), (pos, state) -> {
                    if (pos.getY() < minY || pos.getY() > maxY || pos.distSqr(center) > maxDistanceSq) {
                        return;
                    }
                    BlockClass blockClass = classify(state);
                    if (blockClass.type() == DetectionType.Trap && shouldIgnoreTrapBiome(level, pos)) {
                        return;
                    }
                    DetectionPoint point = new DetectionPoint(pos.immutable(), blockClass.strong(), blockClass.marker());
                    switch (blockClass.type()) {
                        case Trap -> trapPoints.add(point);
                        case AncientCity -> ancientCityPoints.add(point);
                        case TrialChamber -> trialChamberPoints.add(point);
                        case NetherPortal -> netherPortalPoints.add(point);
                        case EndPortal -> endPortalPoints.add(point);
                        case None -> { }
                    }
                });
            }
        }

        return new ScanBuckets(trapPoints, ancientCityPoints, trialChamberPoints,
                netherPortalPoints, endPortalPoints);
    }

    private BlockClass classify(BlockState state) {
        Block block = state.getBlock();

        if (traps.get() && isTrapBlock(block)) {
            MarkerKind marker = block instanceof SignBlock ? MarkerKind.TrapSign : MarkerKind.TrapPiston;
            return new BlockClass(DetectionType.Trap, true, marker);
        }
        if (ancientCities.get() && isAncientCityBlock(block)) {
            return new BlockClass(DetectionType.AncientCity, false, getAncientMarker(block));
        }
        if (trialChambers.get() && isTrialChamberBlock(block)) {
            return new BlockClass(DetectionType.TrialChamber, block == Blocks.TRIAL_SPAWNER || block == Blocks.VAULT, MarkerKind.Generic);
        }
        if (netherPortals.get() && block == Blocks.NETHER_PORTAL) {
            return new BlockClass(DetectionType.NetherPortal, true, MarkerKind.Generic);
        }
        if (endPortals.get() && block == Blocks.END_PORTAL_FRAME) {
            return new BlockClass(DetectionType.EndPortal, true, MarkerKind.Generic);
        }
        return BlockClass.NONE;
    }

    /** Ignore underground cave biomes (Dripstone / Lush Caves / Deep Dark) to cut false positives. */
    private boolean shouldIgnoreTrapBiome(Level level, BlockPos pos) {
        if (!ignoreCaveBiomes.get()) {
            return false;
        }
        var biome = level.getBiome(pos);
        return biome.is(Biomes.DRIPSTONE_CAVES)
                || biome.is(Biomes.LUSH_CAVES)
                || biome.is(Biomes.DEEP_DARK);
    }

    private void retainUndergroundClusters(Level level, List<MutableDetectionBox> clusters) {
        clusters.removeIf(cluster -> !isUndergroundCluster(level, cluster));
    }

    private boolean verifyTrapCluster(Level level, MutableDetectionBox cluster) {
        if (maxSignStackHeight(level, cluster) >= minSignStack.get()) {
            return true;
        }
        if (hasPistonMachinery(level, cluster)) {
            return true;
        }
        return hasTrapShaft(level, cluster);
    }

    private boolean hasPistonMachinery(Level level, MutableDetectionBox cluster) {
        if (!isUndergroundCluster(level, cluster)) {
            return false;
        }
        double cx = (cluster.minX + cluster.maxX + 1) * 0.5;
        double cy = (cluster.minY + cluster.maxY + 1) * 0.5;
        double cz = (cluster.minZ + cluster.maxZ + 1) * 0.5;

        int pistons = 0;
        int facingCenter = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = cluster.minX; x <= cluster.maxX; x++) {
            for (int y = cluster.minY; y <= cluster.maxY; y++) {
                for (int z = cluster.minZ; z <= cluster.maxZ; z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (!isPistonBlock(state.getBlock())) {
                        continue;
                    }
                    pistons++;
                    if (facesToward(state, x, y, z, cx, cy, cz)) {
                        facingCenter++;
                    }
                }
            }
        }
        return pistons >= minPistons.get() && facingCenter * 2 >= pistons;
    }

    private boolean facesToward(BlockState state, int x, int y, int z, double cx, double cy, double cz) {
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return false;
        }
        Direction facing = state.getValue(BlockStateProperties.FACING);
        double toCenterX = cx - (x + 0.5);
        double toCenterY = cy - (y + 0.5);
        double toCenterZ = cz - (z + 0.5);
        double dot = facing.getStepX() * toCenterX
                + facing.getStepY() * toCenterY
                + facing.getStepZ() * toCenterZ;
        return dot > 0.0;
    }

    private boolean isUndergroundCluster(Level level, MutableDetectionBox cluster) {
        final int margin = 3;
        int[] sx = { cluster.minX - margin, cluster.maxX + margin, cluster.minX - margin, cluster.maxX + margin };
        int[] sz = { cluster.minZ - margin, cluster.minZ - margin, cluster.maxZ + margin, cluster.maxZ + margin };

        int buried = 0;
        for (int i = 0; i < sx.length; i++) {
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, sx[i], sz[i]);
            if (cluster.maxY < surface) {
                buried++;
            }
        }
        return buried * 2 >= sx.length;
    }

    private int maxSignStackHeight(Level level, MutableDetectionBox cluster) {
        int maxStack = 0;
        for (int x = cluster.minX; x <= cluster.maxX; x++) {
            for (int z = cluster.minZ; z <= cluster.maxZ; z++) {
                int stack = 0;
                for (int y = cluster.minY; y <= cluster.maxY; y++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof SignBlock) {
                        stack++;
                        maxStack = Math.max(maxStack, stack);
                    } else {
                        stack = 0;
                    }
                }
            }
        }
        return maxStack;
    }

    private boolean hasTrapShaft(Level level, MutableDetectionBox cluster) {
        int cx = (cluster.minX + cluster.maxX) / 2;
        int cz = (cluster.minZ + cluster.maxZ) / 2;
        int startY = cluster.maxY + 1;
        int endY = Math.min(level.getMaxY(), startY + 24);

        int airCount = 0;
        int totalSides = 0;
        int openSides = 0;

        for (int y = startY; y <= endY; y++) {
            BlockPos pos = new BlockPos(cx, y, cz);
            if (!level.getBlockState(pos).isAir()) {
                break;
            }
            airCount++;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                totalSides++;
                if (level.getBlockState(pos.relative(dir)).isAir()) {
                    openSides++;
                }
            }
        }

        if (airCount < 4 || totalSides == 0) {
            return false;
        }
        return (double) openSides / totalSides >= 0.45;
    }

    private boolean isTrapBlock(Block block) {
        return block instanceof SignBlock || isPistonBlock(block);
    }

    private boolean isPistonBlock(Block block) {
        return block instanceof PistonBaseBlock
                || block instanceof PistonHeadBlock
                || block instanceof MovingPistonBlock;
    }

    private boolean isAncientCityBlock(Block block) {
        return block == Blocks.REINFORCED_DEEPSLATE
                || block == Blocks.CRACKED_DEEPSLATE_BRICKS
                || block == Blocks.CRACKED_DEEPSLATE_TILES
                || block == Blocks.CHISELED_DEEPSLATE
                || block == Blocks.SCULK_SHRIEKER
                || block == Blocks.SCULK_CATALYST
                || block == Blocks.SCULK_SENSOR
                || block == Blocks.CALIBRATED_SCULK_SENSOR;
    }

    private MarkerKind getAncientMarker(Block block) {
        if (block == Blocks.REINFORCED_DEEPSLATE) {
            return MarkerKind.AncientPortal;
        }
        if (block == Blocks.CRACKED_DEEPSLATE_BRICKS
                || block == Blocks.CRACKED_DEEPSLATE_TILES
                || block == Blocks.CHISELED_DEEPSLATE) {
            return MarkerKind.AncientMasonry;
        }
        return MarkerKind.AncientSculkDevice;
    }

    private boolean isTrialChamberBlock(Block block) {
        return block == Blocks.TRIAL_SPAWNER
                || block == Blocks.VAULT
                || block == Blocks.COPPER_GRATE
                || block == Blocks.EXPOSED_COPPER_GRATE
                || block == Blocks.WEATHERED_COPPER_GRATE
                || block == Blocks.OXIDIZED_COPPER_GRATE
                || block == Blocks.WAXED_COPPER_GRATE
                || block == Blocks.WAXED_EXPOSED_COPPER_GRATE
                || block == Blocks.WAXED_WEATHERED_COPPER_GRATE
                || block == Blocks.WAXED_OXIDIZED_COPPER_GRATE
                || block == Blocks.COPPER_BULB
                || block == Blocks.EXPOSED_COPPER_BULB
                || block == Blocks.WEATHERED_COPPER_BULB
                || block == Blocks.OXIDIZED_COPPER_BULB
                || block == Blocks.WAXED_COPPER_BULB
                || block == Blocks.WAXED_EXPOSED_COPPER_BULB
                || block == Blocks.WAXED_WEATHERED_COPPER_BULB
                || block == Blocks.WAXED_OXIDIZED_COPPER_BULB
                || block == Blocks.TUFF_BRICKS
                || block == Blocks.TUFF_BRICK_SLAB
                || block == Blocks.TUFF_BRICK_STAIRS
                || block == Blocks.TUFF_BRICK_WALL
                || block == Blocks.CHISELED_TUFF_BRICKS;
    }

    private List<MutableDetectionBox> buildClusters(List<DetectionPoint> points, int gap, int minBlocks, boolean strongMarkerBypass) {
        List<MutableDetectionBox> clusters = new ArrayList<>();

        for (DetectionPoint point : points) {
            MutableDetectionBox target = null;
            for (MutableDetectionBox cluster : clusters) {
                if (cluster.isNear(point.pos(), gap)) {
                    target = cluster;
                    break;
                }
            }

            if (target == null) {
                target = new MutableDetectionBox(point.pos(), point.marker());
                clusters.add(target);
            } else {
                target.include(point.pos(), point.marker());
            }

            if (point.strong()) {
                target.strong = true;
            }
            mergeNearbyClusters(clusters, target, gap);
        }

        clusters.removeIf(cluster -> cluster.count < minBlocks && !(strongMarkerBypass && cluster.strong));
        return clusters;
    }

    private List<MutableDetectionBox> buildAncientCityClusters(List<DetectionPoint> points, int gap) {
        List<MutableDetectionBox> clusters = buildClusters(points, gap, 8, false);
        clusters.removeIf(cluster -> !cluster.hasAncientCitySignature());
        return clusters;
    }

    private void mergeNearbyClusters(List<MutableDetectionBox> clusters, MutableDetectionBox target, int gap) {
        for (int i = clusters.size() - 1; i >= 0; i--) {
            MutableDetectionBox other = clusters.get(i);
            if (other == target || !target.isNear(other, gap)) {
                continue;
            }
            target.include(other);
            clusters.remove(i);
        }
    }

    private void addDetections(List<Detection> output, List<MutableDetectionBox> clusters, DetectionType type, Color color) {
        for (MutableDetectionBox cluster : clusters) {
            output.add(cluster.toDetection(type, color));
        }
    }

    private void addLabel(Detection detection, Vec3 eyePos) {
        Vec3 labelPos = new Vec3(
                (detection.box().minX + detection.box().maxX) * 0.5,
                detection.box().maxY + 0.75,
                (detection.box().minZ + detection.box().maxZ) * 0.5);
        Vector3f projected = WorldToScreen.getWorldPositionToScreen(labelPos);
        if (projected.z < 0.0f || projected.z > 1.0f) {
            return;
        }

        double guiScale = mc.getWindow().getGuiScale();
        float x = (float) (projected.x / guiScale);
        float y = (float) (projected.y / guiScale);
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        if (x < 0.0f || y < 0.0f || x > screenWidth || y > screenHeight) {
            return;
        }

        double dist = Math.sqrt(detection.box().getCenter().distanceToSqr(eyePos));
        String text = labelFor(detection.type()) + " [" + Math.round(dist) + "m]";
        labelDrawList.add(new LabelDrawData(text, x, y, labelScale.get().floatValue()));
    }

    private boolean isPlayerInside(AABB box) {
        return mc.player != null && box.inflate(0.35).intersects(mc.player.getBoundingBox());
    }

    private record Detection(AABB box, BlockPos center, DetectionType type, Color color) {
    }

    private record DetectionPoint(BlockPos pos, boolean strong, MarkerKind marker) {
    }

    private record ScanBuckets(List<DetectionPoint> traps, List<DetectionPoint> ancientCities,
                               List<DetectionPoint> trialChambers,
                               List<DetectionPoint> netherPortals, List<DetectionPoint> endPortals) {
    }

    private record LabelDrawData(String text, float x, float y, float scale) {
    }

    private record BlockClass(DetectionType type, boolean strong, MarkerKind marker) {
        private static final BlockClass NONE = new BlockClass(DetectionType.None, false, MarkerKind.Generic);

        private boolean enabled() {
            return type != DetectionType.None;
        }
    }

    private enum DetectionType {
        None,
        Trap,
        AncientCity,
        TrialChamber,
        NetherPortal,
        EndPortal
    }

    private enum MarkerKind {
        Generic,
        TrapSign,
        TrapPiston,
        AncientMasonry,
        AncientSculkDevice,
        AncientPortal
    }

    private static final class MutableDetectionBox {
        private int minX;
        private int minY;
        private int minZ;
        private int maxX;
        private int maxY;
        private int maxZ;
        private int count = 1;
        private boolean strong;
        private final EnumSet<MarkerKind> markers = EnumSet.noneOf(MarkerKind.class);

        private MutableDetectionBox(BlockPos pos, MarkerKind marker) {
            minX = maxX = pos.getX();
            minY = maxY = pos.getY();
            minZ = maxZ = pos.getZ();
            markers.add(marker);
        }

        private boolean isNear(BlockPos pos, int gap) {
            return pos.getX() >= minX - gap && pos.getX() <= maxX + gap
                    && pos.getY() >= minY - gap && pos.getY() <= maxY + gap
                    && pos.getZ() >= minZ - gap && pos.getZ() <= maxZ + gap;
        }

        private boolean isNear(MutableDetectionBox other, int gap) {
            return other.maxX >= minX - gap && other.minX <= maxX + gap
                    && other.maxY >= minY - gap && other.minY <= maxY + gap
                    && other.maxZ >= minZ - gap && other.minZ <= maxZ + gap;
        }

        private void include(BlockPos pos, MarkerKind marker) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            count++;
            markers.add(marker);
        }

        private void include(MutableDetectionBox other) {
            minX = Math.min(minX, other.minX);
            minY = Math.min(minY, other.minY);
            minZ = Math.min(minZ, other.minZ);
            maxX = Math.max(maxX, other.maxX);
            maxY = Math.max(maxY, other.maxY);
            maxZ = Math.max(maxZ, other.maxZ);
            count += other.count;
            strong |= other.strong;
            markers.addAll(other.markers);
        }

        private boolean hasAncientCitySignature() {
            boolean hasStructure = markers.contains(MarkerKind.AncientMasonry) || markers.contains(MarkerKind.AncientPortal);
            return hasStructure && markers.contains(MarkerKind.AncientSculkDevice);
        }

        private Detection toDetection(DetectionType type, Color color) {
            AABB box = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0).inflate(0.08);
            BlockPos center = new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
            return new Detection(box, center, type, color);
        }
    }
}
