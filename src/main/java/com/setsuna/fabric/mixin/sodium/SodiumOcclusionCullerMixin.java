package com.setsuna.fabric.mixin.sodium;

import com.setsuna.module.modules.render.Xray;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = OcclusionCuller.class, remap = false)
public class SodiumOcclusionCullerMixin {

    @ModifyVariable(method = "findVisible", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean setsuna$hookXrayDisableOcclusionCulling(boolean useOcclusionCulling) {
        return Xray.INSTANCE.shouldDisableChunkOcclusion() ? false : useOcclusionCulling;
    }
}
