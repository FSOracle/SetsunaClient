package com.setsuna.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.function.Function;

/** Cataclysm's original ghost material: unlit energy shader with ordinary alpha blending. */
public final class CataclysmRenderTypes {
    private static final RenderPipeline GHOST_PIPELINE = createGhostPipeline();
    private static final Function<Identifier, RenderType> GHOST = Util.memoize(texture ->
            RenderType.create("setsuna_cataclysm_ghost", RenderSetup.builder(GHOST_PIPELINE)
                    .withTexture("Sampler0", texture).useLightmap().useOverlay()
                    .affectsCrumbling().sortOnUpload().bufferSize(256).createRenderSetup()));

    private CataclysmRenderTypes() { }

    public static RenderType ghost(Identifier texture) {
        return GHOST.apply(texture);
    }

    private static RenderPipeline createGhostPipeline() {
        RenderPipeline original = RenderPipelines.ENERGY_SWIRL;
        RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(
                Optional.of(original.getVertexShader()), Optional.of(original.getFragmentShader()),
                Optional.of(original.getShaderDefines()), Optional.of(original.getSamplers()),
                Optional.of(original.getUniforms()), Optional.of(original.getColorTargetState()),
                Optional.of(original.getDepthStencilState()), Optional.of(original.getPolygonMode()),
                Optional.of(original.isCull()), Optional.of(original.getVertexFormat()),
                Optional.of(original.getVertexFormatMode()));
        return RenderPipelines.register(RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("setsuna", "pipeline/cataclysm_ghost"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(DepthStencilState.DEFAULT).withCull(false).build());
    }
}
