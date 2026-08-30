package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.setsuna.module.modules.render.Xray;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidRenderer.class)
public class FluidRendererMixin {

    @ModifyExpressionValue(method = "tesselate", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;scaleRGB(IF)I"))
    private int setsuna$hookXrayFluidOpacity(int color, BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState) {
        return Xray.INSTANCE.shouldApplyWallOpacity(blockState, pos) ? Xray.INSTANCE.applyWallAlpha(color) : color;
    }
}
