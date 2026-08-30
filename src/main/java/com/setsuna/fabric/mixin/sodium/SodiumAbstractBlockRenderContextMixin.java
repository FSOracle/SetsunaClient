package com.setsuna.fabric.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.setsuna.module.modules.render.Xray;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public class SodiumAbstractBlockRenderContextMixin {

    @Shadow
    protected BlockState state;

    @Shadow
    protected BlockPos pos;

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void setsuna$hookXrayShouldDrawSide(Direction face, CallbackInfoReturnable<Boolean> cir) {
        Boolean decision = Xray.INSTANCE.getSodiumRenderDecision(this.state, this.pos);
        if (decision != null) {
            cir.setReturnValue(decision);
        }
    }

    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true)
    private void setsuna$hookXrayIsFaceCulled(Direction face, CallbackInfoReturnable<Boolean> cir) {
        Boolean decision = Xray.INSTANCE.getSodiumRenderDecision(this.state, this.pos);
        if (decision != null) {
            cir.setReturnValue(!decision);
        }
    }

    @ModifyExpressionValue(method = "renderQuad", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;setRenderType(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;"), require = 0)
    private MutableQuadViewImpl setsuna$hookXrayRenderType(MutableQuadViewImpl quad) {
        return Xray.INSTANCE.shouldRenderTranslucentWall(this.state, this.pos) ? quad.setRenderType(ChunkSectionLayer.TRANSLUCENT) : quad;
    }
}
