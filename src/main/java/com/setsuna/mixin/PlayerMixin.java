package com.setsuna.mixin;

import com.setsuna.event.EventBus;
import com.setsuna.event.events.AttackSlowDownEvent;
import com.setsuna.event.events.AttackYawEvent;
import com.setsuna.module.modules.movement.Scaffold;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

    @ModifyExpressionValue(
            method = {"causeExtraKnockback", "doSweepAttack"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float setsuna$modifyAttackYaw(float original) {
        AttackYawEvent event = EventBus.INSTANCE.post(new AttackYawEvent(original));
        return event.getYaw();
    }

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"), cancellable = true)
    private void setsuna$onCauseExtraKnockback(Entity entity, float knockbackAmount, Vec3 oldMovement, CallbackInfo ci) {
        AttackSlowDownEvent event = EventBus.INSTANCE.post(new AttackSlowDownEvent(entity, knockbackAmount));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "isStayingOnGroundSurface", at = @At("RETURN"), cancellable = true)
    private void setsuna$scaffoldSafeWalk(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (!cir.getReturnValue() && (Object) this == mc.player && Scaffold.INSTANCE.shouldSafeWalk()) {
            cir.setReturnValue(true);
        }
    }
}
