package com.setsuna.mixin;

import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the smoothed reload progress the vanilla overlay renders internally. */
@Mixin(LoadingOverlay.class)
public interface LoadingOverlayAccessor {

    @Accessor("currentProgress")
    float setsuna$getCurrentProgress();
}
