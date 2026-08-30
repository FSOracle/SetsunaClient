package com.setsuna.mixin;

import com.setsuna.module.modules.player.FastBreak;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reports successful client-predicted block breaks to FastBreak. */
@Mixin(MultiPlayerGameMode.class)
public class FastBreakMixin {

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void setsuna$packetStartDestroyBlock(BlockPos pos, Direction direction,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (FastBreak.INSTANCE.handleStartDestroyBlock(pos, direction)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void setsuna$afterDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            FastBreak.INSTANCE.onBlockDestroyed(pos);
        }
    }
}
