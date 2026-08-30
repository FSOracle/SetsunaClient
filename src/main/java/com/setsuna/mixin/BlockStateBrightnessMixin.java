package com.setsuna.mixin;

import com.setsuna.module.modules.render.Xray;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateBrightnessMixin {

    @Inject(method = "getShadeBrightness", at = @At("RETURN"), cancellable = true)
    private void setsuna$hookXrayShadeBrightness(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (Xray.INSTANCE.isEnabled()) {
            cir.setReturnValue(1.0f);
        }
    }
}
