package com.setsuna.module.modules.movement.scaffold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Set;
import java.util.function.Predicate;

/** Produces the same face and outline vertex masks as LiquidBounce's placement renderer. */
public final class ScaffoldRenderCuller {

    private static final int VERTEX_MASK = 0x00FF_FFFF;

    public static final CullMask ALL = new CullMask(VERTEX_MASK, VERTEX_MASK);

    private ScaffoldRenderCuller() {
    }

    public static CullMask cull(BlockPos pos, Set<BlockPos> renderedBlocks) {
        return cull(pos, renderedBlocks::contains);
    }

    private static CullMask cull(BlockPos pos, Predicate<BlockPos> contains) {
        boolean east = contains.test(pos.relative(Direction.EAST));
        boolean west = contains.test(pos.relative(Direction.WEST));
        boolean up = contains.test(pos.relative(Direction.UP));
        boolean down = contains.test(pos.relative(Direction.DOWN));
        boolean south = contains.test(pos.relative(Direction.SOUTH));
        boolean north = contains.test(pos.relative(Direction.NORTH));

        int faces = 0;
        faces = visibleFace(faces, down, 0);
        faces = visibleFace(faces, up, 1);
        faces = visibleFace(faces, north, 2);
        faces = visibleFace(faces, east, 3);
        faces = visibleFace(faces, south, 4);
        faces = visibleFace(faces, west, 5);

        int outlines = 0;
        outlines = visibleEdge(outlines, north, down,
                contains.test(diagonal(pos, Direction.NORTH, Direction.DOWN)), 0);
        outlines = visibleEdge(outlines, east, down,
                contains.test(diagonal(pos, Direction.EAST, Direction.DOWN)), 1);
        outlines = visibleEdge(outlines, south, down,
                contains.test(diagonal(pos, Direction.SOUTH, Direction.DOWN)), 2);
        outlines = visibleEdge(outlines, west, down,
                contains.test(diagonal(pos, Direction.WEST, Direction.DOWN)), 3);
        outlines = visibleEdge(outlines, north, west,
                contains.test(diagonal(pos, Direction.NORTH, Direction.WEST)), 4);
        outlines = visibleEdge(outlines, north, east,
                contains.test(diagonal(pos, Direction.NORTH, Direction.EAST)), 5);
        outlines = visibleEdge(outlines, south, east,
                contains.test(diagonal(pos, Direction.SOUTH, Direction.EAST)), 6);
        outlines = visibleEdge(outlines, south, west,
                contains.test(diagonal(pos, Direction.SOUTH, Direction.WEST)), 7);
        outlines = visibleEdge(outlines, north, up,
                contains.test(diagonal(pos, Direction.NORTH, Direction.UP)), 8);
        outlines = visibleEdge(outlines, east, up,
                contains.test(diagonal(pos, Direction.EAST, Direction.UP)), 9);
        outlines = visibleEdge(outlines, south, up,
                contains.test(diagonal(pos, Direction.SOUTH, Direction.UP)), 10);
        outlines = visibleEdge(outlines, west, up,
                contains.test(diagonal(pos, Direction.WEST, Direction.UP)), 11);

        return new CullMask(faces, outlines);
    }

    private static BlockPos diagonal(BlockPos pos, Direction first, Direction second) {
        return pos.relative(first).relative(second);
    }

    private static int visibleFace(int current, boolean neighborPresent, int faceIndex) {
        return neighborPresent ? current : current | (0xF << (faceIndex * 4));
    }

    private static int visibleEdge(
            int current,
            boolean firstNeighborPresent,
            boolean secondNeighborPresent,
            boolean diagonalPresent,
            int edgeIndex) {
        boolean neitherPresent = !firstNeighborPresent && !secondNeighborPresent;
        boolean concaveEdge = firstNeighborPresent && secondNeighborPresent && !diagonalPresent;
        return neitherPresent || concaveEdge ? current | (0x3 << (edgeIndex * 2)) : current;
    }

    public record CullMask(int faceVertices, int outlineVertices) {
    }
}
