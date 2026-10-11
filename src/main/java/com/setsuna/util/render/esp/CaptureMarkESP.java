package com.setsuna.util.render.esp;

import com.setsuna.util.render.ColorUtils;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;

/** Billboarded capture marker used by combat target displays. */
public final class CaptureMarkESP {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("setsuna", "textures/particles/target.png");
    private static final RenderType LAYER = createLayer();

    private CaptureMarkESP() {
    }

    public static void render(
            PoseStack poses,
            LivingEntity target,
            double size,
            double rotationSpeed,
            double waveSpeed,
            Color firstColor,
            Color secondColor
    ) {
        double now = System.nanoTime() * 1.0E-9D;
        float angle = (float) (-((now * rotationSpeed * 60.0D) % 360.0D));
        Camera positionCamera = mc.getEntityRenderDispatcher().camera;
        Camera facingCamera = mc.gameRenderer.getMainCamera();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 relative = BillboardQuad.relativeEntityPosition(target, positionCamera, partialTick);
        float radius = (float) size * 0.5F;

        poses.pushPose();
        poses.translate(relative.x, relative.y + target.getBbHeight() * 0.5D, relative.z);
        BillboardQuad.faceCamera(poses, facingCamera);
        poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));

        Matrix4f transform = poses.last().pose();
        double[] phases = {0.0D, 0.25D, 0.5D, 0.75D};
        int[] colors = new int[phases.length];
        for (int index = 0; index < phases.length; index++) {
            colors[index] = waveColor((float) phases[index], waveSpeed, firstColor, secondColor, now).getRGB();
        }

        BufferBuilder vertices = BillboardQuad.begin();
        BillboardQuad.emitGradient(vertices, transform, radius, colors[0], colors[1], colors[2], colors[3]);
        LAYER.draw(vertices.buildOrThrow());
        poses.popPose();
    }

    private static Color waveColor(
            float progress,
            double speed,
            Color first,
            Color second,
            double time
    ) {
        float wave = (float) Math.sin(progress * Math.PI * 2.0D + time * speed);
        return ColorUtils.interpolateColor(first, second, (wave + 1.0F) * 0.5F);
    }

    private static RenderType createLayer() {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("setsuna", "pipeline/setsuna_target_icon"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .build();
        return RenderType.create("setsuna_target_icon", RenderSetup.builder(pipeline)
                .withTexture("Sampler0", TEXTURE)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.MAIN_TARGET)
                .createRenderSetup());
    }
}
