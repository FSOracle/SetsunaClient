package com.setsuna.module.modules.movement.scaffold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldGeometryTest {

    @Test
    void movementAngleFollowsMinecraftInputAxes() {
        assertAngle(0.0F, ScaffoldMovementPlanner.movementAngle(0.0F, 1.0F, 0.0F));
        assertAngle(-180.0F, ScaffoldMovementPlanner.movementAngle(0.0F, -1.0F, 0.0F));
        assertAngle(-90.0F, ScaffoldMovementPlanner.movementAngle(0.0F, 0.0F, 1.0F));
        assertAngle(90.0F, ScaffoldMovementPlanner.movementAngle(0.0F, 0.0F, -1.0F));
        assertAngle(-45.0F, ScaffoldMovementPlanner.movementAngle(0.0F, 1.0F, 1.0F));
        assertAngle(45.0F, ScaffoldMovementPlanner.movementAngle(90.0F, 1.0F, 1.0F));
    }

    @Test
    void scaffoldOffsetsMatchLiquidBounceSearchSpaces() {
        List<BlockPos> normal = ScaffoldTargetFinder.normalOffsets();
        List<BlockPos> down = ScaffoldTargetFinder.downOffsets();

        assertEquals(18, normal.size());
        assertEquals(50, down.size());
        assertEquals(BlockPos.ZERO, normal.getFirst());
        assertEquals(new BlockPos(0, -1, 0), normal.get(1));
        assertTrue(normal.contains(new BlockPos(1, -1, -1)));
        assertFalse(normal.contains(new BlockPos(2, 0, 0)));
        assertTrue(down.contains(new BlockPos(2, -1, -2)));
        assertTrue(normal.stream().allMatch(pos -> pos.getY() == 0 || pos.getY() == -1));
        assertTrue(down.stream().allMatch(pos -> pos.getY() == 0 || pos.getY() == -1));
    }

    @Test
    void lineProjectionUsesTheNearestUnboundedPoint() {
        ScaffoldMovementPlanner.Line line = new ScaffoldMovementPlanner.Line(
                new Vec3(1.0D, 2.0D, 3.0D),
                new Vec3(2.0D, 0.0D, 0.0D));
        Vec3 point = new Vec3(4.0D, 5.0D, 9.0D);

        assertEquals(new Vec3(4.0D, 2.0D, 3.0D), line.nearestPoint(point));
        assertEquals(45.0D, line.distanceToSqr(point), 1.0E-9D);
    }

    @Test
    void targetPriorityMeasuresTheOutlineInsteadOfItsCenter() {
        ScaffoldMovementPlanner.Line line = new ScaffoldMovementPlanner.Line(
                new Vec3(2.0D, 2.0D, 2.0D),
                new Vec3(1.0D, 2.0D, 1.0D));
        AABB face = new AABB(0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 1.0D);

        assertEquals(0.8D, ScaffoldTargetFinder.distanceToLineSqr(line, face), 1.0E-9D);
        Vec3 nearest = ScaffoldTargetFinder.nearestPointOnBoxToLine(line.point(), line.direction(), face);
        assertEquals(0.0D, nearest.x, 1.0E-9D);
        assertEquals(0.0D, nearest.y, 1.0E-9D);
        assertEquals(0.8D, nearest.z, 1.0E-9D);
    }

    @Test
    void oppositeSupportDoesNotHideTheLastGapEdge() {
        Vec3 from = new Vec3(0.5D, -0.1D, 0.5D);
        Vec3 to = new Vec3(3.0D, -0.1D, 0.5D);
        AABB currentSupport = new AABB(-0.3D, -1.0D, -0.3D, 1.3D, 1.55D, 1.3D);
        AABB oppositeSupport = new AABB(1.7D, -1.0D, -0.3D, 3.3D, 1.55D, 1.3D);

        Vec3 edge = ScaffoldEdgeCollision.findAlongBoxes(
                from, to, List.of(currentSupport, oppositeSupport));

        assertEquals(1.3D, edge.x, 1.0E-7D);
        assertEquals(from.y, edge.y, 1.0E-7D);
        assertEquals(from.z, edge.z, 1.0E-7D);
    }

    @Test
    void placementClumpingHidesSharedFacesAndTheirPerimeter() {
        BlockPos center = BlockPos.ZERO;
        Set<BlockPos> rendered = Set.of(center, center.relative(Direction.EAST));

        ScaffoldRenderCuller.CullMask mask = ScaffoldRenderCuller.cull(center, rendered);

        assertEquals(ScaffoldRenderCuller.ALL.faceVertices() & ~(0xF << 12), mask.faceVertices());
        int eastEdges = (0x3 << 2) | (0x3 << 10) | (0x3 << 12) | (0x3 << 18);
        assertEquals(ScaffoldRenderCuller.ALL.outlineVertices() & ~eastEdges, mask.outlineVertices());
    }

    @Test
    void placementClumpingKeepsConcaveEdgesUntilTheDiagonalIsFilled() {
        BlockPos center = BlockPos.ZERO;
        Set<BlockPos> rendered = new HashSet<>(Set.of(
                center,
                center.relative(Direction.NORTH),
                center.relative(Direction.EAST)));

        int northEastEdge = 0x3 << 10;
        assertEquals(northEastEdge,
                ScaffoldRenderCuller.cull(center, rendered).outlineVertices() & northEastEdge);

        rendered.add(center.relative(Direction.NORTH).relative(Direction.EAST));
        assertEquals(0, ScaffoldRenderCuller.cull(center, rendered).outlineVertices() & northEastEdge);
    }

    private static void assertAngle(float expected, float actual) {
        assertEquals(0.0F, Mth.wrapDegrees(actual - expected), 1.0E-4F);
    }
}
