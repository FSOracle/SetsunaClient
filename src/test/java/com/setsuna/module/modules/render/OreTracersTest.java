package com.setsuna.module.modules.render;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreTracersTest {
    private static final BlockPos PLAYER = new BlockPos(0, 20, 0);
    private static final BlockPos ORE = new BlockPos(3, 20, 0);

    private OreTracers module;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void createModule() throws ReflectiveOperationException {
        var constructor = OreTracers.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        module = constructor.newInstance();
    }

    @Test
    void minedOreDisappearsOnTheNextFrameAndIsRemovedFromTheCache() {
        List<BlockPos> candidates = candidates(ORE);
        Map<BlockPos, Block> world = new HashMap<>();
        world.put(ORE, Blocks.DIAMOND_ORE);

        assertEquals(1, module.collectTargets(candidates, PLAYER, world::get).size());

        world.put(ORE, Blocks.AIR);

        assertTrue(module.collectTargets(candidates, PLAYER, world::get).isEmpty());
        assertTrue(candidates.isEmpty());
        module.collectTargets(candidates, PLAYER, ignored -> {
            throw new AssertionError("Removed ore must not be queried again");
        });
    }

    @Test
    void staleScanResultsCannotRestoreAMinedOreTracer() {
        List<BlockPos> candidates = candidates(ORE);
        List<BlockPos> pendingScanResults = List.of(ORE);

        assertTrue(module.collectTargets(candidates, PLAYER, ignored -> Blocks.AIR).isEmpty());
        candidates.addAll(pendingScanResults);

        assertTrue(module.collectTargets(candidates, PLAYER, ignored -> Blocks.AIR).isEmpty());
        assertTrue(candidates.isEmpty());
    }

    @Test
    void unloadedAndReplacedBlocksArePrunedWithoutDroppingRemainingOres() {
        BlockPos unloaded = new BlockPos(4, 20, 0);
        BlockPos replaced = new BlockPos(5, 20, 0);
        List<BlockPos> candidates = candidates(ORE, unloaded, replaced);
        Map<BlockPos, Block> world = Map.of(ORE, Blocks.DIAMOND_ORE, replaced, Blocks.STONE);

        List<OreTracers.OreTarget> targets = module.collectTargets(candidates, PLAYER, world::get);

        assertEquals(List.of(ORE), candidates);
        assertEquals(List.of(new OreTracers.OreTarget(ORE, module.diamondColor.get())), targets);
    }

    @Test
    void replacementOreUsesItsCurrentTypeAndColor() {
        List<BlockPos> candidates = candidates(ORE);

        List<OreTracers.OreTarget> diamond = module.collectTargets(candidates, PLAYER,
                ignored -> Blocks.DIAMOND_ORE);
        List<OreTracers.OreTarget> gold = module.collectTargets(candidates, PLAYER,
                ignored -> Blocks.DEEPSLATE_GOLD_ORE);

        assertEquals(module.diamondColor.get(), diamond.getFirst().color());
        assertEquals(module.goldColor.get(), gold.getFirst().color());
        assertEquals(List.of(ORE), candidates);
    }

    @Test
    void disablingAndReenablingAnOreTypeKeepsItsCachedPosition() {
        List<BlockPos> candidates = candidates(ORE);
        module.diamond.set(false);

        assertTrue(module.collectTargets(candidates, PLAYER, ignored -> Blocks.DIAMOND_ORE).isEmpty());
        assertEquals(List.of(ORE), candidates);

        module.diamond.set(true);

        assertEquals(1, module.collectTargets(candidates, PLAYER, ignored -> Blocks.DIAMOND_ORE).size());
    }

    @Test
    void outOfRangeCandidatesAreRetainedWithoutWorldReads() {
        BlockPos distant = new BlockPos(17, 20, 0);
        List<BlockPos> candidates = candidates(distant);

        assertTrue(module.collectTargets(candidates, PLAYER, ignored -> {
            throw new AssertionError("Out-of-range positions must not query the world");
        }).isEmpty());
        assertEquals(List.of(distant), candidates);
        assertEquals(1, module.collectTargets(candidates, new BlockPos(1, 20, 0),
                ignored -> Blocks.DIAMOND_ORE).size());
    }

    @Test
    void validatesOnlyCachedPositionsOncePerFrame() {
        List<BlockPos> candidates = candidates(ORE, new BlockPos(-3, 20, 0));
        AtomicInteger reads = new AtomicInteger();

        List<OreTracers.OreTarget> targets = module.collectTargets(candidates, PLAYER, ignored -> {
            reads.incrementAndGet();
            return Blocks.DIAMOND_ORE;
        });

        assertEquals(2, targets.size());
        assertEquals(2, reads.get());
    }

    @Test
    void closeHighlightIncludesFourBlocksFromTheSurfaceButNotBeyond() {
        assertTrue(OreTracers.isCloseTarget(BlockPos.ZERO, new Vec3(5.0, 0.5, 0.5)));
        assertFalse(OreTracers.isCloseTarget(BlockPos.ZERO, new Vec3(5.001, 0.5, 0.5)));
        assertTrue(OreTracers.isCloseTarget(BlockPos.ZERO, new Vec3(-4.0, 0.5, 0.5)));
        assertFalse(OreTracers.isCloseTarget(BlockPos.ZERO, new Vec3(-4.001, 0.5, 0.5)));
    }

    @Test
    void closeHighlightUsesEuclideanDistanceToCornersAndIncludesInsideTheBlock() {
        assertTrue(OreTracers.isCloseTarget(BlockPos.ZERO, new Vec3(3.0, 3.0, 3.0)));
        assertFalse(OreTracers.isCloseTarget(BlockPos.ZERO, new Vec3(4.0, 4.0, 0.5)));
        assertTrue(OreTracers.isCloseTarget(ORE, new Vec3(3.5, 20.5, 0.5)));
    }

    @Test
    void coincidentOrDirectlyAimedSegmentsDoNotReachTheLineShader() {
        Vector3f forward = new Vector3f(0, 0, 1);
        assertNull(OreTracers.prepareLine(new Vector3f(1, 0, 2), new Vector3f(1, 0, 2), forward));
        assertNull(OreTracers.prepareLine(new Vector3f(0, 0, 0.1F), new Vector3f(0, 0, 3), forward));
        assertNull(OreTracers.prepareLine(new Vector3f(0, 0, 0.1F), new Vector3f(0, 0, -3), forward));
        assertNull(OreTracers.prepareLine(new Vector3f(0.1F, 0, 0.1F), new Vector3f(3, 0, 3), forward));
    }

    @Test
    void offAxisTracerHasFiniteUnitDirectionWithoutChangingItsInputs() {
        Vector3f start = new Vector3f(0, 0, 0.1F);
        Vector3f end = new Vector3f(1, 0, 3);
        OreTracers.TracerLine line = OreTracers.prepareLine(start, end, new Vector3f(0, 0, 1));

        assertNotNull(line);
        assertTrue(line.normal().isFinite());
        assertEquals(1.0F, line.normal().length(), 1.0E-6F);
        assertEquals(new Vector3f(0, 0, 0.1F), start);
        assertEquals(new Vector3f(1, 0, 3), end);
    }

    @Test
    void crossingEdgesAreClippedBeyondTheNearPlaneInEitherVertexOrder() {
        Vector3f forward = new Vector3f(0, 0, 1);
        Vector3f behind = new Vector3f(1, 0, -1);
        Vector3f inFront = new Vector3f(1, 0, 1);
        OreTracers.TracerLine line = OreTracers.prepareLine(behind, inFront, forward);
        OreTracers.TracerLine reversed = OreTracers.prepareLine(inFront, behind, forward);

        assertNotNull(line);
        assertNotNull(reversed);
        assertEquals(0.1F, line.from().dot(forward), 1.0E-6F);
        assertEquals(0.1F, reversed.to().dot(forward), 1.0E-6F);
        assertTrue(line.from().z * (255.0F / 256.0F) * (4095.0F / 4096.0F) > 0.05F);
        assertTrue(line.normal().dot(forward) >= 0.0F);
        assertTrue(reversed.normal().dot(forward) >= 0.0F);
    }

    @Test
    void geometryBehindTheCameraOrWithNonFiniteCoordinatesIsSkipped() {
        Vector3f forward = new Vector3f(0, 0, 1);
        assertNull(OreTracers.prepareLine(new Vector3f(1, 0, -2), new Vector3f(1, 1, -1), forward));
        assertNull(OreTracers.prepareLine(new Vector3f(Float.NaN, 0, 1), new Vector3f(1, 1, 2), forward));
        assertNull(OreTracers.prepareLine(new Vector3f(1, 0, 1),
                new Vector3f(Float.POSITIVE_INFINITY, 1, 2), forward));
    }

    @Test
    void clippingAndProjectionChecksFollowTheCameraDirection() {
        Vector3f forward = new Vector3f(1, 0, 0);
        OreTracers.TracerLine line = OreTracers.prepareLine(
                new Vector3f(-1, 1, 0), new Vector3f(1, 1, 0), forward);

        assertNotNull(line);
        assertEquals(0.1F, line.from().x, 1.0E-6F);
        assertTrue(line.normal().x >= 0.0F);
        assertNull(OreTracers.prepareLine(new Vector3f(0.1F, 0, 0), new Vector3f(3, 0, 0), forward));
    }

    private static List<BlockPos> candidates(BlockPos... positions) {
        return new ArrayList<>(List.of(positions));
    }
}
