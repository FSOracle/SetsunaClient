package com.setsuna.fabric.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.setsuna.module.modules.render.Xray;
import com.setsuna.util.render.XraySectionCompilerHooks;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererMixin extends AbstractBlockRenderContext {

    @Unique
    private BlockAndTintGetter setsuna$originalLevel;

    @Inject(method = "renderModel", at = @At("HEAD"))
    private void setsuna$hookXrayTargetBlockViewHead(CallbackInfo ci, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true, ordinal = 0) BlockPos pos) {
        Boolean decision = Xray.INSTANCE.getSodiumRenderDecision(state, pos);
        if (Boolean.TRUE.equals(decision)) {
            setsuna$originalLevel = this.level;
            this.level = XraySectionCompilerHooks.targetBlockView(this.level, pos, state);
        }
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void setsuna$hookXrayTargetBlockViewReturn(CallbackInfo ci) {
        if (setsuna$originalLevel != null) {
            this.level = setsuna$originalLevel;
            setsuna$originalLevel = null;
        }
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"))
    private void setsuna$hookXrayBufferQuadOpacity(MutableQuadViewImpl quad, float[] brightness, Material material, CallbackInfo ci) {
        if (!Xray.INSTANCE.shouldApplyWallOpacity(this.state, this.pos)) {
            return;
        }

        for (int vertex = 0; vertex < 4; vertex++) {
            quad.setColor(vertex, Xray.INSTANCE.applyWallAlpha(quad.baseColor(vertex)));
        }
    }

    @ModifyExpressionValue(method = "processQuad", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/DefaultMaterials;forChunkLayer(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;"), require = 0)
    private Material setsuna$hookXrayMaterial(Material material) {
        return Xray.INSTANCE.shouldRenderTranslucentWall(this.state, this.pos) ? DefaultMaterials.TRANSLUCENT : material;
    }
}
