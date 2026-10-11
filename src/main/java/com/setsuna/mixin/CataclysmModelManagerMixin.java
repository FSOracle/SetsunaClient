package com.setsuna.mixin;

import com.setsuna.module.modules.render.CataclysmItemRenderer;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
public abstract class CataclysmModelManagerMixin {
    @Inject(method = "apply", at = @At("TAIL"))
    private void setsuna$reloadItemAppearances(CallbackInfo ci) {
        CataclysmItemRenderer.clearCache();
    }
}
