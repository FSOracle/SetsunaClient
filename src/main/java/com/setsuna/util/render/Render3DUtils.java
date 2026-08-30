package com.setsuna.util.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;

/**
 * World-space debug drawing (filled boxes, outlines, faces) built entirely on
 * vanilla {@link RenderType}/{@link Tesselator}. Depth testing is disabled so
 * shapes show through walls, matching the source client's behaviour.
 */
public final class Render3DUtils {

    private static final Minecraft mc = Minecraft.getInstance();

    private Render3DUtils() {
    }

    private static final RenderPipeline FILLED_BOX_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("setsuna", "pipeline/filled_box"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final RenderType FILLED_BOX = RenderType.create("setsuna_filled_box",
            RenderSetup.builder(FILLED_BOX_PIPELINE)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup());

    private static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("setsuna", "pipeline/lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    public static final RenderType LINES = RenderType.create("setsuna_lines", RenderSetup.builder(LINES_PIPELINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup());

    public static void drawFilledBox(BlockPos blockPos, Color color) {
        drawFilledBox(new AABB(blockPos), color.getRGB());
    }

    public static void drawFilledBox(AABB box, Color color) {
        int c = color.getRGB();
        drawFilledFadeBox(box, c, c);
    }

    public static void drawFilledBox(AABB box, Color color, int faceVertices) {
        int c = color.getRGB();
        drawFilledFadeBox(box, c, c, faceVertices);
    }

    public static void drawFilledBox(AABB box, int c) {
        drawFilledFadeBox(box, c, c);
    }

    public static void drawFilledSide(BlockPos blockPos, Color color, Direction direction) {
        drawFilledSide(new AABB(blockPos), color, direction);
    }

    public static void drawFilledSide(AABB box, Color color, Direction direction) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);
        int c = color.getRGB();

        Matrix4f matrix = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;

        switch (direction) {
            case DOWN -> {
                vertex(buffer, matrix, minX, minY, minZ, c);
                vertex(buffer, matrix, maxX, minY, minZ, c);
                vertex(buffer, matrix, maxX, minY, maxZ, c);
                vertex(buffer, matrix, minX, minY, maxZ, c);
            }
            case NORTH -> {
                vertex(buffer, matrix, minX, minY, minZ, c);
                vertex(buffer, matrix, minX, maxY, minZ, c);
                vertex(buffer, matrix, maxX, maxY, minZ, c);
                vertex(buffer, matrix, maxX, minY, minZ, c);
            }
            case EAST -> {
                vertex(buffer, matrix, maxX, minY, minZ, c);
                vertex(buffer, matrix, maxX, maxY, minZ, c);
                vertex(buffer, matrix, maxX, maxY, maxZ, c);
                vertex(buffer, matrix, maxX, minY, maxZ, c);
            }
            case SOUTH -> {
                vertex(buffer, matrix, minX, minY, maxZ, c);
                vertex(buffer, matrix, maxX, minY, maxZ, c);
                vertex(buffer, matrix, maxX, maxY, maxZ, c);
                vertex(buffer, matrix, minX, maxY, maxZ, c);
            }
            case WEST -> {
                vertex(buffer, matrix, minX, minY, minZ, c);
                vertex(buffer, matrix, minX, minY, maxZ, c);
                vertex(buffer, matrix, minX, maxY, maxZ, c);
                vertex(buffer, matrix, minX, maxY, minZ, c);
            }
            case UP -> {
                vertex(buffer, matrix, minX, maxY, minZ, c);
                vertex(buffer, matrix, minX, maxY, maxZ, c);
                vertex(buffer, matrix, maxX, maxY, maxZ, c);
                vertex(buffer, matrix, maxX, maxY, minZ, c);
            }
        }

        FILLED_BOX.draw(buffer.buildOrThrow());
    }

    public static void drawFilledFadeBox(AABB box, int c, int c1) {
        drawFilledFadeBox(box, c, c1, -1);
    }

    public static void drawFilledFadeBox(AABB box, int c, int c1, int faceVertices) {
        if ((faceVertices & 0x00FF_FFFF) == 0) {
            return;
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        Matrix4f matrix = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;

        if (usesFaceVertices(faceVertices, 0)) {
            vertex(buffer, matrix, minX, minY, minZ, c);
            vertex(buffer, matrix, minX, minY, maxZ, c);
            vertex(buffer, matrix, maxX, minY, maxZ, c);
            vertex(buffer, matrix, maxX, minY, minZ, c);
        }

        if (usesFaceVertices(faceVertices, 1)) {
            vertex(buffer, matrix, minX, maxY, minZ, c1);
            vertex(buffer, matrix, maxX, maxY, minZ, c1);
            vertex(buffer, matrix, maxX, maxY, maxZ, c1);
            vertex(buffer, matrix, minX, maxY, maxZ, c);
        }

        if (usesFaceVertices(faceVertices, 2)) {
            vertex(buffer, matrix, minX, minY, minZ, c);
            vertex(buffer, matrix, minX, maxY, minZ, c1);
            vertex(buffer, matrix, maxX, maxY, minZ, c1);
            vertex(buffer, matrix, maxX, minY, minZ, c);
        }

        if (usesFaceVertices(faceVertices, 3)) {
            vertex(buffer, matrix, maxX, minY, minZ, c);
            vertex(buffer, matrix, maxX, maxY, minZ, c1);
            vertex(buffer, matrix, maxX, maxY, maxZ, c1);
            vertex(buffer, matrix, maxX, minY, maxZ, c);
        }

        if (usesFaceVertices(faceVertices, 4)) {
            vertex(buffer, matrix, minX, minY, maxZ, c);
            vertex(buffer, matrix, maxX, minY, maxZ, c);
            vertex(buffer, matrix, maxX, maxY, maxZ, c1);
            vertex(buffer, matrix, minX, maxY, maxZ, c1);
        }

        if (usesFaceVertices(faceVertices, 5)) {
            vertex(buffer, matrix, minX, minY, minZ, c);
            vertex(buffer, matrix, minX, minY, maxZ, c);
            vertex(buffer, matrix, minX, maxY, maxZ, c1);
            vertex(buffer, matrix, minX, maxY, minZ, c1);
        }

        FILLED_BOX.draw(buffer.buildOrThrow());
    }

    public static void drawOutlineBox(PoseStack stack, BlockPos blockPos, Color color) {
        drawOutlineBox(stack, new AABB(blockPos), color.getRGB(), 2f);
    }

    public static void drawOutlineBox(PoseStack stack, AABB box, Color color) {
        drawOutlineBox(stack, box, color.getRGB(), 2f);
    }

    public static void drawOutlineBox(PoseStack stack, AABB box, Color color, int outlineVertices) {
        drawOutlineBox(stack, box, color.getRGB(), 2f, outlineVertices);
    }

    public static void drawOutlineBox(PoseStack stack, AABB box, Color color, float thickness) {
        drawOutlineBox(stack, box, color.getRGB(), thickness);
    }

    public static void drawOutlineBox(PoseStack stack, BlockPos blockPos, Color color, float thickness) {
        drawOutlineBox(stack, new AABB(blockPos), color.getRGB(), thickness);
    }

    public static void drawSideOutline(PoseStack stack, BlockPos blockPos, Color color, float thickness, Direction direction) {
        drawSideOutline(stack, new AABB(blockPos), color.getRGB(), thickness, direction);
    }

    public static void drawSideOutline(PoseStack stack, AABB box, Color color, float thickness, Direction direction) {
        drawSideOutline(stack, box, color.getRGB(), thickness, direction);
    }

    public static void drawSideOutline(PoseStack stack, AABB box, int color, float thickness, Direction direction) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        PoseStack.Pose entry = stack.last();
        Matrix4f matrix = entry.pose();

        switch (direction) {
            case UP -> {
                vertexLine(buffer, matrix, entry, minX, maxY, minZ, maxX, maxY, minZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, maxY, minZ, maxX, maxY, maxZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, maxY, maxZ, minX, maxY, maxZ, color, thickness);
                vertexLine(buffer, matrix, entry, minX, maxY, maxZ, minX, maxY, minZ, color, thickness);
            }
            case DOWN -> {
                vertexLine(buffer, matrix, entry, minX, minY, minZ, maxX, minY, minZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, minY, minZ, maxX, minY, maxZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, minY, maxZ, minX, minY, maxZ, color, thickness);
                vertexLine(buffer, matrix, entry, minX, minY, maxZ, minX, minY, minZ, color, thickness);
            }
            case EAST -> {
                vertexLine(buffer, matrix, entry, maxX, minY, minZ, maxX, maxY, minZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, minY, maxZ, maxX, maxY, maxZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, maxY, maxZ, maxX, maxY, minZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, minY, maxZ, maxX, minY, minZ, color, thickness);
            }
            case WEST -> {
                vertexLine(buffer, matrix, entry, minX, minY, minZ, minX, maxY, minZ, color, thickness);
                vertexLine(buffer, matrix, entry, minX, minY, maxZ, minX, maxY, maxZ, color, thickness);
                vertexLine(buffer, matrix, entry, minX, maxY, maxZ, minX, maxY, minZ, color, thickness);
                vertexLine(buffer, matrix, entry, minX, minY, maxZ, minX, minY, minZ, color, thickness);
            }
            case NORTH -> {
                vertexLine(buffer, matrix, entry, maxX, minY, minZ, maxX, maxY, minZ, color, thickness);
                vertexLine(buffer, matrix, entry, minX, minY, minZ, minX, maxY, minZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, minY, minZ, minX, minY, minZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, maxY, minZ, minX, maxY, minZ, color, thickness);
            }
            case SOUTH -> {
                vertexLine(buffer, matrix, entry, minX, minY, maxZ, minX, maxY, maxZ, color, thickness);
                vertexLine(buffer, matrix, entry, maxX, minY, maxZ, maxX, maxY, maxZ, color, thickness);
                vertexLine(buffer, matrix, entry, minX, minY, maxZ, maxX, minY, maxZ, color, thickness);
                vertexLine(buffer, matrix, entry, minX, maxY, maxZ, maxX, maxY, maxZ, color, thickness);
            }
        }

        LINES.draw(buffer.buildOrThrow());
    }

    public static void drawOutlineBox(PoseStack stack, AABB box, int color, float thickness) {
        drawOutlineBox(stack, box, color, thickness, -1);
    }

    /** Draws one depth-independent world-space line. */
    public static void drawLine(PoseStack stack, Vec3 from, Vec3 to, int color, float thickness) {
        if (from == null || to == null || from.distanceToSqr(to) < 1.0E-8D) {
            return;
        }
        Vec3 camera = mc.getEntityRenderDispatcher().camera.position();
        float x1 = (float) (from.x - camera.x);
        float y1 = (float) (from.y - camera.y);
        float z1 = (float) (from.z - camera.z);
        float x2 = (float) (to.x - camera.x);
        float y2 = (float) (to.y - camera.y);
        float z2 = (float) (to.z - camera.z);

        PoseStack.Pose entry = stack.last();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
        vertexLine(buffer, entry.pose(), entry, x1, y1, z1, x2, y2, z2,
                color, Math.max(0.1F, thickness));
        LINES.draw(buffer.buildOrThrow());
    }

    /** Draws a depth-independent wire cylinder around an absolute world position. */
    public static void drawCylinder(PoseStack stack, Vec3 bottomCenter, double radius, double height,
                                    int color, float thickness, int segments) {
        if (bottomCenter == null || radius <= 0.0 || height <= 0.0) {
            return;
        }

        int slices = Math.max(6, segments);
        Vec3 camera = mc.getEntityRenderDispatcher().camera.position();
        PoseStack.Pose entry = stack.last();
        Matrix4f matrix = entry.pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
        float safeThickness = Math.max(0.1F, thickness);

        for (int i = 0; i < slices; i++) {
            double angle = Math.PI * 2.0 * i / slices;
            double nextAngle = Math.PI * 2.0 * (i + 1) / slices;
            float x1 = (float) (bottomCenter.x + Math.cos(angle) * radius - camera.x);
            float z1 = (float) (bottomCenter.z + Math.sin(angle) * radius - camera.z);
            float x2 = (float) (bottomCenter.x + Math.cos(nextAngle) * radius - camera.x);
            float z2 = (float) (bottomCenter.z + Math.sin(nextAngle) * radius - camera.z);
            float bottom = (float) (bottomCenter.y - camera.y);
            float top = (float) (bottomCenter.y + height - camera.y);

            vertexLine(buffer, matrix, entry, x1, bottom, z1, x2, bottom, z2, color, safeThickness);
            vertexLine(buffer, matrix, entry, x1, top, z1, x2, top, z2, color, safeThickness);
            vertexLine(buffer, matrix, entry, x1, bottom, z1, x1, top, z1, color, safeThickness);
        }

        LINES.draw(buffer.buildOrThrow());
    }

    public static void drawOutlineBox(
            PoseStack stack,
            AABB box,
            int color,
            float thickness,
            int outlineVertices) {
        if ((outlineVertices & 0x00FF_FFFF) == 0) {
            return;
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        PoseStack.Pose entry = stack.last();
        Matrix4f matrix = entry.pose();

        if (usesOutlineVertices(outlineVertices, 0)) {
            vertexLine(buffer, matrix, entry, minX, minY, minZ, maxX, minY, minZ, color, thickness);
        }
        if (usesOutlineVertices(outlineVertices, 1)) {
            vertexLine(buffer, matrix, entry, maxX, minY, minZ, maxX, minY, maxZ, color, thickness);
        }
        if (usesOutlineVertices(outlineVertices, 2)) {
            vertexLine(buffer, matrix, entry, maxX, minY, maxZ, minX, minY, maxZ, color, thickness);
        }
        if (usesOutlineVertices(outlineVertices, 3)) {
            vertexLine(buffer, matrix, entry, minX, minY, maxZ, minX, minY, minZ, color, thickness);
        }

        if (usesOutlineVertices(outlineVertices, 8)) {
            vertexLine(buffer, matrix, entry, minX, maxY, minZ, maxX, maxY, minZ, color, thickness);
        }
        if (usesOutlineVertices(outlineVertices, 9)) {
            vertexLine(buffer, matrix, entry, maxX, maxY, minZ, maxX, maxY, maxZ, color, thickness);
        }
        if (usesOutlineVertices(outlineVertices, 10)) {
            vertexLine(buffer, matrix, entry, maxX, maxY, maxZ, minX, maxY, maxZ, color, thickness);
        }
        if (usesOutlineVertices(outlineVertices, 11)) {
            vertexLine(buffer, matrix, entry, minX, maxY, maxZ, minX, maxY, minZ, color, thickness);
        }

        if (usesOutlineVertices(outlineVertices, 4)) {
            vertexLine(buffer, matrix, entry, minX, minY, minZ, minX, maxY, minZ, color, thickness);
        }
        if (usesOutlineVertices(outlineVertices, 5)) {
            vertexLine(buffer, matrix, entry, maxX, minY, minZ, maxX, maxY, minZ, color, thickness);
        }
        if (usesOutlineVertices(outlineVertices, 6)) {
            vertexLine(buffer, matrix, entry, maxX, minY, maxZ, maxX, maxY, maxZ, color, thickness);
        }
        if (usesOutlineVertices(outlineVertices, 7)) {
            vertexLine(buffer, matrix, entry, minX, minY, maxZ, minX, maxY, maxZ, color, thickness);
        }

        LINES.draw(buffer.buildOrThrow());
    }

    private static boolean usesFaceVertices(int vertices, int groupIndex) {
        int mask = 0xF << (groupIndex * 4);
        return (vertices & mask) == mask;
    }

    private static boolean usesOutlineVertices(int vertices, int edgeIndex) {
        int mask = 0x3 << (edgeIndex * 2);
        return (vertices & mask) == mask;
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, int color) {
        buffer.addVertex(matrix, x, y, z).setColor(color);
    }

    private static void vertexLine(BufferBuilder buffer, Matrix4f matrix, PoseStack.Pose entry, float x1, float y1, float z1, float x2, float y2, float z2, int color, float thickness) {
        Vector3f normal = getNormal(x1, y1, z1, x2, y2, z2);
        buffer.addVertex(matrix, x1, y1, z1).setColor(color).setNormal(entry, normal.x, normal.y, normal.z).setLineWidth(thickness);
        buffer.addVertex(matrix, x2, y2, z2).setColor(color).setNormal(entry, normal.x, normal.y, normal.z).setLineWidth(thickness);
    }

    private static Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);
        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }
}
