package com.setsuna.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
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
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.function.Function;

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
            // World overlays run after the level pass; use its final main target explicitly.
            .setOutputTarget(OutputTarget.MAIN_TARGET)
            .createRenderSetup());

    private static final RenderPipeline FOOTBALL_SKIN_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("setsuna", "pipeline/football_player"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderType> FOOTBALL_SKIN_LAYER = Util.memoize(texture ->
            RenderType.create("setsuna_football_player", RenderSetup.builder(FOOTBALL_SKIN_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()));

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

    /** Draws a smooth, depth-independent sphere using one batched triangle mesh. */
    public static void drawFilledSphere(Vec3 center, double radius, Color color,
                                        int latitudeSegments, int longitudeSegments) {
        if (center == null || color == null || radius <= 0.0D) {
            return;
        }

        int latitudes = Math.max(6, latitudeSegments);
        int longitudes = Math.max(8, longitudeSegments);
        Vec3 camera = mc.getEntityRenderDispatcher().camera.position();
        Matrix4f matrix = mc.gameRenderer.getGameRenderState()
                .levelRenderState.cameraRenderState.viewRotationMatrix;
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int latitude = 0; latitude < latitudes; latitude++) {
            double phi0 = -Math.PI * 0.5D + Math.PI * latitude / latitudes;
            double phi1 = -Math.PI * 0.5D + Math.PI * (latitude + 1) / latitudes;
            for (int longitude = 0; longitude < longitudes; longitude++) {
                double theta0 = Math.PI * 2.0D * longitude / longitudes;
                double theta1 = Math.PI * 2.0D * (longitude + 1) / longitudes;

                sphereVertex(buffer, matrix, center, camera, radius, phi0, theta0, color);
                sphereVertex(buffer, matrix, center, camera, radius, phi1, theta0, color);
                sphereVertex(buffer, matrix, center, camera, radius, phi1, theta1, color);
                sphereVertex(buffer, matrix, center, camera, radius, phi0, theta0, color);
                sphereVertex(buffer, matrix, center, camera, radius, phi1, theta1, color);
                sphereVertex(buffer, matrix, center, camera, radius, phi0, theta1, color);
            }
        }
        FILLED_BOX.draw(buffer.buildOrThrow());
    }

    private static void sphereVertex(BufferBuilder buffer, Matrix4f matrix,
                                     Vec3 center, Vec3 camera, double radius,
                                     double phi, double theta, Color color) {
        double cosPhi = Math.cos(phi);
        double normalX = cosPhi * Math.cos(theta);
        double normalY = Math.sin(phi);
        double normalZ = cosPhi * Math.sin(theta);
        float x = (float) (center.x + normalX * radius - camera.x);
        float y = (float) (center.y + normalY * radius - camera.y);
        float z = (float) (center.z + normalZ * radius - camera.z);
        double light = Math.max(0.0D,
                normalX * -0.35D + normalY * 0.82D + normalZ * -0.45D);
        float brightness = (float) (0.58D + light * 0.42D);
        int shaded = new Color(
                Math.max(0, Math.min(255, Math.round(color.getRed() * brightness))),
                Math.max(0, Math.min(255, Math.round(color.getGreen() * brightness))),
                Math.max(0, Math.min(255, Math.round(color.getBlue() * brightness))),
                color.getAlpha()).getRGB();
        vertex(buffer, matrix, x, y, z, shaded);
    }

    /** Draws a collision-free Minecraft-shaped player directly into the world render pass. */
    public static void drawSkinnedPlayer(PoseStack stack, Vec3 feetPosition, float yawDegrees,
                                         float limbSwing, Identifier skinTexture) {
        if (stack == null || feetPosition == null || skinTexture == null) {
            return;
        }

        Vec3 camera = mc.getEntityRenderDispatcher().camera.position();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        stack.pushPose();
        stack.translate(feetPosition.x - camera.x, feetPosition.y - camera.y,
                feetPosition.z - camera.z);
        stack.mulPose(Axis.YP.rotationDegrees(-yawDegrees));
        stack.scale(0.9F, 0.9F, 0.9F);

        drawSkinPart(buffer, stack.last().pose(), 0.0F, 1.75F, 0.0F,
                0.5F, 0.5F, 0.5F, 0, 0, 8, 8, 8);
        drawSkinPart(buffer, stack.last().pose(), 0.0F, 1.75F, 0.0F,
                0.535F, 0.535F, 0.535F, 32, 0, 8, 8, 8);
        drawSkinPart(buffer, stack.last().pose(), 0.0F, 1.125F, 0.0F,
                0.5F, 0.75F, 0.25F, 16, 16, 8, 12, 4);
        drawSkinPart(buffer, stack.last().pose(), 0.0F, 1.125F, 0.0F,
                0.53F, 0.77F, 0.28F, 16, 32, 8, 12, 4);

        drawSkinArm(buffer, stack, -0.34375F, 1.375F, limbSwing,
                40, 16, 40, 32);
        drawSkinArm(buffer, stack, 0.34375F, 1.375F, -limbSwing,
                32, 48, 48, 48);
        drawSkinLeg(buffer, stack, -0.125F, 0.75F, -limbSwing,
                0, 16, 0, 32);
        drawSkinLeg(buffer, stack, 0.125F, 0.75F, limbSwing,
                16, 48, 0, 48);

        stack.popPose();
        FOOTBALL_SKIN_LAYER.apply(skinTexture).draw(buffer.buildOrThrow());
    }

    private static void drawSkinArm(BufferBuilder buffer, PoseStack stack,
                                    float x, float pivotY, float swingDegrees,
                                    int baseU, int baseV, int overlayU, int overlayV) {
        stack.pushPose();
        stack.translate(x, pivotY, 0.0F);
        stack.mulPose(Axis.XP.rotationDegrees(swingDegrees));
        stack.translate(0.0F, -0.25F, 0.0F);
        drawSkinPart(buffer, stack.last().pose(), 0.0F, 0.0F, 0.0F,
                0.1875F, 0.75F, 0.25F, baseU, baseV, 3, 12, 4);
        drawSkinPart(buffer, stack.last().pose(), 0.0F, 0.0F, 0.0F,
                0.2125F, 0.77F, 0.275F, overlayU, overlayV, 3, 12, 4);
        stack.popPose();
    }

    private static void drawSkinLeg(BufferBuilder buffer, PoseStack stack,
                                    float x, float pivotY, float swingDegrees,
                                    int baseU, int baseV, int overlayU, int overlayV) {
        stack.pushPose();
        stack.translate(x, pivotY, 0.0F);
        stack.mulPose(Axis.XP.rotationDegrees(swingDegrees));
        stack.translate(0.0F, -0.375F, 0.0F);
        drawSkinPart(buffer, stack.last().pose(), 0.0F, 0.0F, 0.0F,
                0.25F, 0.75F, 0.25F, baseU, baseV, 4, 12, 4);
        drawSkinPart(buffer, stack.last().pose(), 0.0F, 0.0F, 0.0F,
                0.275F, 0.77F, 0.275F, overlayU, overlayV, 4, 12, 4);
        stack.popPose();
    }

    private static void drawSkinPart(BufferBuilder buffer, Matrix4f matrix,
                                     float centerX, float centerY, float centerZ,
                                     float width, float height, float depth,
                                     int textureU, int textureV,
                                     int pixelWidth, int pixelHeight, int pixelDepth) {
        float minX = centerX - width * 0.5F;
        float maxX = centerX + width * 0.5F;
        float minY = centerY - height * 0.5F;
        float maxY = centerY + height * 0.5F;
        float minZ = centerZ - depth * 0.5F;
        float maxZ = centerZ + depth * 0.5F;

        float topU0 = uv(textureU + pixelDepth);
        float topV0 = uv(textureV);
        float topU1 = uv(textureU + pixelDepth + pixelWidth);
        float topV1 = uv(textureV + pixelDepth);
        float bottomU0 = topU1;
        float bottomU1 = uv(textureU + pixelDepth + pixelWidth * 2);
        float sideV0 = uv(textureV + pixelDepth);
        float sideV1 = uv(textureV + pixelDepth + pixelHeight);
        float rightU0 = uv(textureU);
        float rightU1 = uv(textureU + pixelDepth);
        float frontU0 = rightU1;
        float frontU1 = uv(textureU + pixelDepth + pixelWidth);
        float leftU0 = frontU1;
        float leftU1 = uv(textureU + pixelDepth + pixelWidth + pixelDepth);
        float backU0 = leftU1;
        float backU1 = uv(textureU + pixelDepth * 2 + pixelWidth * 2);

        skinQuad(buffer, matrix,
                minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                frontU0, sideV0, frontU1, sideV1);
        skinQuad(buffer, matrix,
                maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ,
                backU0, sideV0, backU1, sideV1);
        skinQuad(buffer, matrix,
                minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
                rightU0, sideV0, rightU1, sideV1);
        skinQuad(buffer, matrix,
                maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ,
                leftU0, sideV0, leftU1, sideV1);
        skinQuad(buffer, matrix,
                minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ,
                topU0, topV0, topU1, topV1);
        skinQuad(buffer, matrix,
                minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                bottomU0, topV0, bottomU1, topV1);
    }

    private static void skinQuad(BufferBuilder buffer, Matrix4f matrix,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float x3, float y3, float z3, float x4, float y4, float z4,
                                 float u0, float v0, float u1, float v1) {
        skinVertex(buffer, matrix, x1, y1, z1, u0, v1);
        skinVertex(buffer, matrix, x2, y2, z2, u1, v1);
        skinVertex(buffer, matrix, x3, y3, z3, u1, v0);
        skinVertex(buffer, matrix, x4, y4, z4, u0, v0);
    }

    private static void skinVertex(BufferBuilder buffer, Matrix4f matrix,
                                   float x, float y, float z, float u, float v) {
        buffer.addVertex(matrix, x, y, z).setUv(u, v).setColor(0xFFFFFFFF);
    }

    private static float uv(int pixel) {
        return pixel / 64.0F;
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
