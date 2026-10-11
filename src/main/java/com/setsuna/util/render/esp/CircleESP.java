package com.setsuna.util.render.esp;

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
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.function.Function;

/**
 * A soft translucent disc that hovers under the KillAura target and bobs with
 * time. Depth-tested variant when visible, always-pass when behind walls.
 */
public final class CircleESP {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final int CIRCLE_SAMPLES = 64;
    private static final RenderPipeline THROUGH_WALLS = createPipeline(
            "pipeline/triangle_strip", CompareOp.ALWAYS_PASS);
    private static final RenderPipeline DEPTH_TESTED = createPipeline(
            "pipeline/triangle_strip", CompareOp.LESS_THAN_OR_EQUAL);
    private static final Function<RenderPipeline, RenderType> LAYER = Util.memoize(
            pipeline -> RenderType.create("setsuna_triangle_strip", RenderSetup.builder(pipeline)
                    .createRenderSetup())
    );

    private CircleESP() {
    }

    public static void render(PoseStack poses, LivingEntity target, float radius, Color sideColor, Color lineColor, float alphaFactor) {
        boolean visible = mc.player.hasLineOfSight(target);
        float ticks = (float) (System.currentTimeMillis() % 1000000) * 0.004F;
        float alpha = 0.35F + 0.65F * ((Mth.sin(ticks * 1.8F) + 1.0F) * 0.5F) * alphaFactor;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        var cameraPosition = mc.getEntityRenderDispatcher().camera.position();
        double x = Mth.lerp(partialTick, target.xo, target.getX()) - cameraPosition.x;
        double y = Mth.lerp(partialTick, target.yo, target.getY()) - cameraPosition.y + Math.sin(ticks) + 1;
        double z = Mth.lerp(partialTick, target.zo, target.getZ()) - cameraPosition.z;

        poses.pushPose();
        poses.translate(x, y, z);
        BufferBuilder vertices = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLE_STRIP,
                DefaultVertexFormat.POSITION_COLOR
        );
        emitRing(vertices, poses.last().pose(), radius, sideColor, ticks, alpha);
        LAYER.apply(visible ? DEPTH_TESTED : THROUGH_WALLS).draw(vertices.buildOrThrow());
        poses.popPose();
    }

    private static RenderPipeline createPipeline(String path, CompareOp depthTest) {
        return RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("setsuna", path))
                .withDepthStencilState(new DepthStencilState(depthTest, false))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
                .build();
    }

    private static void emitRing(
            BufferBuilder vertices,
            Matrix4f matrix,
            float radius,
            Color color,
            float ticks,
            float alpha
    ) {
        float increment = ((float) Math.PI * 2) / (float) CIRCLE_SAMPLES;
        float lowerY = (float) (-Math.sin(ticks + 1) / 2.7F);
        float red = color.getAlpha() / 255.0F;
        float green = color.getGreen() / 255.0F;
        float blue = color.getBlue() / 255.0F;
        for (float angle = 0; angle <= Math.PI * 2; angle += increment) {
            float x = (float) (radius * Math.cos(angle));
            float z = (float) (radius * Math.sin(angle));
            vertices.addVertex(matrix, x, lowerY, z).setColor(red, green, blue, 0.0F);
            vertices.addVertex(matrix, x, 0, z).setColor(red, green, blue, 0.52F * alpha);
        }
    }
}
