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
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.function.Function;

/**
 * Orbiting "firefly" trails around the KillAura target. Requires the
 * {@code setsuna:textures/particles/firefly.png} asset.
 */
public final class FireflyESP {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final int TRAIL_COUNT = 3;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "setsuna", "textures/particles/firefly.png");
    private static final RenderPipeline THROUGH_WALLS_PIPELINE = createPipeline(
            "pipeline/setsuna_firefly_nodepth", CompareOp.ALWAYS_PASS);
    private static final RenderPipeline VISIBLE_PIPELINE = createPipeline(
            "pipeline/setsuna_firefly", CompareOp.LESS_THAN_OR_EQUAL);
    private static final Function<RenderPipeline, RenderType> LAYERS = Util.memoize(FireflyESP::createLayer);

    private FireflyESP() {
    }

    public enum ColorMode {
        Solid,
        Blend,
        Rainbow
    }

    public static void render(
            PoseStack poses,
            LivingEntity target,
            int espLength,
            int factor,
            double shaking,
            double amplitude,
            Color color,
            ColorMode colorMode,
            Color secondColor,
            double colorMix,
            double colorSpeed,
            double rainbowSpeed,
            double rainbowSaturation,
            double rainbowBrightness
    ) {
        boolean visible = mc.player.hasLineOfSight(target);
        Camera camera = mc.gameRenderer.getMainCamera();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Orbit orbit = new Orbit(
                BillboardQuad.relativeEntityPosition(target, camera, partialTick),
                (float) (target.tickCount - 1) + partialTick,
                espLength,
                factor,
                shaking,
                amplitude,
                target.getBbWidth(),
                target.getBbHeight()
        );
        Palette palette = new Palette(
                colorMode,
                color,
                secondColor,
                (float) colorMix,
                (float) colorSpeed,
                (float) rainbowSpeed,
                (float) rainbowSaturation,
                (float) rainbowBrightness
        );
        BufferBuilder vertices = BillboardQuad.begin();

        for (int trail = 0; trail < TRAIL_COUNT; trail++) {
            emitTrail(poses, camera, vertices, orbit, palette, trail);
        }
        LAYERS.apply(visible ? VISIBLE_PIPELINE : THROUGH_WALLS_PIPELINE).draw(vertices.buildOrThrow());
    }

    private static void emitTrail(
            PoseStack poses,
            Camera camera,
            BufferBuilder vertices,
            Orbit orbit,
            Palette palette,
            int trail
    ) {
        for (int step = 0; step <= orbit.length(); step++) {
            double angle = orbit.angleAt(step, trail);
            double heightOffset = orbit.heightOffsetAt(step, trail);
            float progress = (float) step / (float) orbit.length();

            poses.pushPose();
            poses.translate(
                    orbit.origin().x + Math.cos(angle) * orbit.width(),
                    orbit.origin().y + orbit.height() * 0.5D + heightOffset,
                    orbit.origin().z + Math.sin(angle) * orbit.width()
            );
            BillboardQuad.faceCamera(poses, camera);
            float radius = Math.max(0.24F * progress, 0.2F);
            int color = palette.resolve(orbit.age(), step, trail, orbit.length()).getRGB();
            BillboardQuad.emitSolid(vertices, poses.last().pose(), radius, color);
            poses.popPose();
        }
    }

    private static RenderPipeline createPipeline(String path, CompareOp depthTest) {
        return RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("setsuna", path))
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .withDepthStencilState(new DepthStencilState(depthTest, false))
                .withCull(false)
                .build();
    }

    private static RenderType createLayer(RenderPipeline pipeline) {
        return RenderType.create("setsuna_firefly", RenderSetup.builder(pipeline)
                .withTexture("Sampler0", TEXTURE)
                .sortOnUpload()
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.MAIN_TARGET)
                .createRenderSetup());
    }

    private record Orbit(
            Vec3 origin,
            float age,
            int length,
            int factor,
            double shaking,
            double amplitude,
            float width,
            float height
    ) {
        double angleAt(int step, int trail) {
            return Math.toRadians(
                    (((float) step / 1.5F + age) * factor + trail * 120) % (factor * 360));
        }

        double heightOffsetAt(int step, int trail) {
            return Math.sin(Math.toRadians(age * 2.5F + step * (trail + 1)) * amplitude) / shaking;
        }
    }

    private record Palette(
            ColorMode mode,
            Color primary,
            Color secondary,
            float mix,
            float blendSpeed,
            float rainbowSpeed,
            float saturation,
            float brightness
    ) {
        Color resolve(float age, int step, int trail, int length) {
            float clampedMix = Mth.clamp(mix, 0.0F, 1.0F);
            float progress = length <= 0 ? 0.0F : (float) step / (float) length;
            return switch (mode) {
            case Blend -> {
                float wave = (Mth.sin(age * blendSpeed * 0.25F + progress * 6.2831855F + trail)
                        + 1.0F) * 0.5F;
                float blend = Mth.clamp(wave * clampedMix, 0.0F, 1.0F);
                yield ColorUtils.interpolateColor(primary, secondary, blend);
            }
            case Rainbow -> {
                float hue = Mth.frac(age * 0.01F * rainbowSpeed + progress + trail * 0.17F);
                Color rainbow = Color.getHSBColor(
                        hue,
                        Mth.clamp(saturation, 0.0F, 1.0F),
                        Mth.clamp(brightness, 0.0F, 1.0F)
                );
                yield new Color(rainbow.getRed(), rainbow.getGreen(), rainbow.getBlue(), primary.getAlpha());
            }
            case Solid -> primary;
            };
        }
    }
}
