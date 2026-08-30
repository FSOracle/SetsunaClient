package com.setsuna.mixin;

import com.setsuna.module.modules.render.NoRender;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.particle.FireworkParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkParticles.Starter.class)
public class FireworkParticlesStarterMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void setsuna$hideFireworkParticle(
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            IntList colors,
            IntList fadeColors,
            boolean trail,
            boolean twinkle,
            CallbackInfo ci
    ) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender.isEnabled() && noRender.fireworks.get()) {
            ci.cancel();
        }
    }
}
