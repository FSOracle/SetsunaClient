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

    private CircleESP() {
    }

    private static final RenderPipeline TRIANGLE_STRIP_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("setsuna", "pipeline/triangle_strip"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .build();

    private static final RenderPipeline TRIANGLE_STRIP_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("setsuna", "pipeline/triangle_strip"))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .build();

    private static final Function<RenderPipeline, RenderType> TRIANGLE_STRIP = Util.memoize(
            renderPipeline -> RenderType.create("setsuna_triangle_strip", RenderSetup.builder(renderPipeline)
                    .createRenderSetup())
    );

    public static void render(PoseStack poseStack, LivingEntity target, float radius, Color sideColor, Color lineColor, float alphaFactor) {
        boolean canSee = mc.player.hasLineOfSight(target);

        float ticks = (float) (System.currentTimeMillis() % 1000000) * 0.004f;
        float alpha = 0.35f + 0.65f * ((Mth.sin(ticks * 1.8f) + 1.0f) * 0.5f) * alphaFactor;

        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        double x = Mth.lerp(tickDelta, target.xo, target.getX()) - mc.getEntityRenderDispatcher().camera.position().x;
        double y = Mth.lerp(tickDelta, target.yo, target.getY()) - mc.getEntityRenderDispatcher().camera.position().y + Math.sin(ticks) + 1;
        double z = Mth.lerp(tickDelta, target.zo, target.getZ()) - mc.getEntityRenderDispatcher().camera.position().z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        BufferBuilder triBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        for (float i = 0; i <= (Math.PI * 2); i += ((float) Math.PI * 2) / 64.F) {
            float vecX = (float) (radius * Math.cos(i));
            float vecZ = (float) (radius * Math.sin(i));

            triBuffer.addVertex(matrix, vecX, (float) (-Math.sin(ticks + 1) / 2.7f), vecZ).setColor(sideColor.getAlpha() / 255.0f, sideColor.getGreen() / 255.0f, sideColor.getBlue() / 255.0f, 0.0f);
            triBuffer.addVertex(matrix, vecX, 0, vecZ).setColor(sideColor.getAlpha() / 255.0f, sideColor.getGreen() / 255.0f, sideColor.getBlue() / 255.0f, 0.52f * alpha);
        }

        TRIANGLE_STRIP.apply(canSee ? TRIANGLE_STRIP_PIPELINE : TRIANGLE_STRIP_NO_DEPTH_PIPELINE).draw(triBuffer.buildOrThrow());

        poseStack.popPose();
    }
}
