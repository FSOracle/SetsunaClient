package com.setsuna.fabric.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.setsuna.module.modules.render.Xray;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DefaultFluidRenderer.class, remap = false)
public class SodiumFluidRendererMixin {

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;scaleRGB(IF)I"), require = 0)
    private int setsuna$hookXrayFluidOpacity(int color, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true, ordinal = 0) BlockPos pos, @Local(argsOnly = true) FluidState fluidState) {
        BlockState xrayState = state != null ? state : fluidState.createLegacyBlock();
        return Xray.INSTANCE.shouldApplyWallOpacity(xrayState, pos) ? Xray.INSTANCE.applyWallAlpha(color) : color;
    }
}
