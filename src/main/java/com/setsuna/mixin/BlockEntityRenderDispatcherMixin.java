package com.setsuna.mixin;

import com.setsuna.module.modules.render.Xray;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void setsuna$hookXrayHideBlockEntities(E blockEntity, float partialTicks, ModelFeatureRenderer.CrumblingOverlay breakProgress, CallbackInfoReturnable<S> cir) {
        if (Xray.INSTANCE.shouldHideBlockEntity(blockEntity.getBlockState(), blockEntity.getBlockPos())) {
            cir.setReturnValue(null);
        }
    }
}
