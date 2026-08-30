package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.AttackEvent;
import com.setsuna.module.modules.movement.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void setsuna$beforeAttack(Player player, Entity target, CallbackInfo ci) {
        if (player == Minecraft.getInstance().player) {
            EventBus.INSTANCE.post(new AttackEvent(player, target));
        }
    }

    @ModifyExpressionValue(
            method = "ensureHasSentCarriedItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"))
    private int setsuna$scaffoldSilentSlot(int original) {
        return Scaffold.INSTANCE.modifyServerSelectedSlot(original);
    }
}
