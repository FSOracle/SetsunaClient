package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.setsuna.module.modules.render.Xray;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    @WrapOperation(method = "shouldRenderFace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private boolean setsuna$hookXrayShouldRenderFace(BlockState state, BlockState neighborState, Direction direction, Operation<Boolean> original, BlockAndTintGetter level, BlockState originalState, Direction originalDirection, BlockPos neighborPos) {
        Boolean decision = Xray.INSTANCE.getRenderDecision(state, neighborPos.relative(direction.getOpposite()));
        return decision != null ? decision : original.call(state, neighborState, direction);
    }
}
