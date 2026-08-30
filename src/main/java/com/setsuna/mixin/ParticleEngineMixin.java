package com.setsuna.mixin;

import com.setsuna.module.modules.render.NoRender;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void setsuna$filterParticle(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfoReturnable<Particle> cir
    ) {
        if (setsuna$shouldCancel(options)) {
            cir.setReturnValue(null);
            cir.cancel();
        }
    }

    @Inject(
            method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void setsuna$filterTrackingEmitter(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
        if (setsuna$shouldCancel(options)) {
            ci.cancel();
        }
    }

    private static boolean setsuna$shouldCancel(ParticleOptions options) {
        NoRender noRender = NoRender.INSTANCE;
        if (!noRender.isEnabled() || options == null) {
            return false;
        }
        if (noRender.explosions.get()
                && (options.getType() == ParticleTypes.EXPLOSION
                || options.getType() == ParticleTypes.EXPLOSION_EMITTER
                || options.getType() == ParticleTypes.POOF
                || options.getType() == ParticleTypes.SMOKE
                || options.getType() == ParticleTypes.LARGE_SMOKE
                || options.getType() == ParticleTypes.CLOUD)) {
            return true;
        }
        if (noRender.potionParticles.get()
                && (options.getType() == ParticleTypes.EFFECT
                || options.getType() == ParticleTypes.ENTITY_EFFECT
                || options.getType() == ParticleTypes.INSTANT_EFFECT
                || options.getType() == ParticleTypes.SPLASH)) {
            return true;
        }
        if (noRender.fireworks.get()
                && (options.getType() == ParticleTypes.FIREWORK || options.getType() == ParticleTypes.FLASH)) {
            return true;
        }
        if (noRender.portal.get()
                && (options.getType() == ParticleTypes.PORTAL || options.getType() == ParticleTypes.REVERSE_PORTAL)) {
            return true;
        }
        return noRender.totems.get() && options.getType() == ParticleTypes.TOTEM_OF_UNDYING;
    }
}
