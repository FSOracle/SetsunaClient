package com.setsuna.mixin;

import com.setsuna.module.modules.render.CataclysmItemRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Shadow @Final private ModelManager modelManager;

    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void setsuna$cataclysmItemAppearance(ItemStackRenderState state, ItemStack stack,
                                              ItemDisplayContext context, Level level, ItemOwner owner,
                                              int seed, CallbackInfo ci) {
        if (CataclysmItemRenderer.append(state, stack, context, level, owner, seed,
                (ItemModelResolver) (Object) this, modelManager)) ci.cancel();
    }
}
