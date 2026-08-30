package com.setsuna.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.setsuna.module.modules.render.Xray;
import com.setsuna.util.render.XraySectionCompilerHooks;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public class IndigoAltModelBlockRendererMixin {

    @Unique
    private boolean setsuna$xrayTargetBlock;

    @Unique
    private boolean setsuna$xrayWallBlock;

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void setsuna$beginXrayBlock(QuadEmitter output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState blockState, BlockStateModel model, long seed, CallbackInfo ci) {
        setsuna$xrayTargetBlock = false;
        setsuna$xrayWallBlock = false;

        Xray xray = Xray.INSTANCE;
        if (!xray.isRenderActive()) {
            return;
        }

        Boolean renderDecision = xray.getRenderDecision(blockState, pos);
        if (Boolean.FALSE.equals(renderDecision)) {
            ci.cancel();
            return;
        }

        setsuna$xrayTargetBlock = Boolean.TRUE.equals(renderDecision);
        setsuna$xrayWallBlock = renderDecision == null;
    }

    @WrapOperation(method = "tesselateBlock", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/aocalc/AoCalculator;prepare(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V"))
    private void setsuna$prepareXrayLight(AoCalculator aoCalculator, BlockAndTintGetter level, BlockState state, BlockPos pos, Operation<Void> original) {
        BlockAndTintGetter renderLevel = setsuna$xrayTargetBlock ? XraySectionCompilerHooks.targetBlockView(level, pos, state) : level;
        original.call(aoCalculator, renderLevel, state, pos);
    }

    @WrapOperation(method = "tesselateBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;emitQuads(Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/QuadEmitter;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Ljava/util/function/Predicate;)V", remap = true))
    private void setsuna$emitXrayQuads(BlockStateModel model, QuadEmitter output, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest, Operation<Void> original) {
        BlockAndTintGetter renderLevel = setsuna$xrayTargetBlock ? XraySectionCompilerHooks.targetBlockView(level, pos, state) : level;
        Predicate<Direction> renderCullTest = setsuna$xrayTargetBlock ? direction -> false : cullTest;

        if (setsuna$xrayWallBlock) {
            output.pushTransform(this::setsuna$applyXrayWallAlpha);
            try {
                original.call(model, output, renderLevel, pos, state, random, renderCullTest);
            } finally {
                output.popTransform();
            }
        } else {
            original.call(model, output, renderLevel, pos, state, random, renderCullTest);
        }
    }

    @WrapOperation(method = "transform", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AltModelBlockRendererImpl;shouldCullFace(Lnet/minecraft/core/Direction;)Z"))
    private boolean setsuna$keepXrayTargetFaces(AltModelBlockRendererImpl renderer, Direction direction, Operation<Boolean> original) {
        return !setsuna$xrayTargetBlock && original.call(renderer, direction);
    }

    @Unique
    private boolean setsuna$applyXrayWallAlpha(MutableQuadView quad) {
        for (int vertex = 0; vertex < 4; vertex++) {
            quad.color(vertex, Xray.INSTANCE.applyWallAlpha(quad.color(vertex)));
        }
        quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
        return true;
    }
}
