package com.setsuna.util.render;

import com.setsuna.module.modules.render.Xray;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Xray render surgery: intercepts the section compiler's per-block quad output so
 * non-target blocks are dropped (or rendered as translucent walls) and target
 * blocks are drawn against an air-only neighbourhood. Kept as a static helper so
 * the mixin stays thin.
 */
public final class XraySectionCompilerHooks {

    private static final BlockQuadOutput EMPTY_OUTPUT = (x, y, z, quad, instance) -> {
    };

    private XraySectionCompilerHooks() {
    }

    public static void hookBlockOutput(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed, Operation<Void> original) {
        Xray xray = Xray.INSTANCE;
        if (!xray.isRenderActive()) {
            original.call(renderer, output, x, y, z, level, pos, state, model, seed);
            return;
        }

        Boolean renderDecision = xray.getRenderDecision(state, pos);
        if (Boolean.FALSE.equals(renderDecision)) {
            original.call(renderer, EMPTY_OUTPUT, x, y, z, level, pos, state, model, seed);
            return;
        }

        boolean renderTarget = Boolean.TRUE.equals(renderDecision);
        BlockAndTintGetter renderLevel = renderTarget ? targetBlockView(level, pos, state) : level;
        BlockQuadOutput xrayOutput = renderTarget ? output : new WallBlockQuadOutput(output, xray);
        original.call(renderer, xrayOutput, x, y, z, renderLevel, pos, state, model, seed);
    }

    private static void applyWallAlpha(QuadInstance instance, Xray xray) {
        for (int vertex = 0; vertex < 4; vertex++) {
            instance.setColor(vertex, xray.applyWallAlpha(instance.getColor(vertex)));
        }
    }

    public static BlockAndTintGetter targetBlockView(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return new XrayTargetBlockView(level, pos, state);
    }

    private static BakedQuad withLayer(BakedQuad quad, ChunkSectionLayer layer) {
        BakedQuad.MaterialInfo info = quad.materialInfo();
        BakedQuad.MaterialInfo xrayInfo = new BakedQuad.MaterialInfo(
                info.sprite(),
                layer,
                info.itemRenderType(),
                info.tintIndex(),
                info.shade(),
                info.lightEmission()
        );
        return new BakedQuad(
                quad.position0(),
                quad.position1(),
                quad.position2(),
                quad.position3(),
                quad.packedUV0(),
                quad.packedUV1(),
                quad.packedUV2(),
                quad.packedUV3(),
                quad.direction(),
                xrayInfo
        );
    }

    private static final class WallBlockQuadOutput implements BlockQuadOutput {
        private final BlockQuadOutput output;
        private final Xray xray;

        private WallBlockQuadOutput(BlockQuadOutput output, Xray xray) {
            this.output = output;
            this.xray = xray;
        }

        @Override
        public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            applyWallAlpha(instance, xray);
            output.put(x, y, z, withLayer(quad, ChunkSectionLayer.TRANSLUCENT), instance);
        }
    }

    private static final class XrayTargetBlockView implements BlockAndTintGetter {
        private final BlockAndTintGetter delegate;
        private final BlockPos targetPos;
        private final BlockState targetState;

        private XrayTargetBlockView(BlockAndTintGetter delegate, BlockPos targetPos, BlockState targetState) {
            this.delegate = delegate;
            this.targetPos = targetPos.immutable();
            this.targetState = targetState;
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return delegate.cardinalLighting();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return delegate.getBlockTint(pos, color);
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return pos.equals(targetPos) ? targetState : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return pos.equals(targetPos) ? targetState.getFluidState() : Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinY() {
            return delegate.getMinY();
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }
    }
}
