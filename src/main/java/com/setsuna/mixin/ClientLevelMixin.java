package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.setsuna.util.player.SkipTickUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public final class ClientLevelMixin {

    @WrapOperation(
            method = "tickNonPassenger",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void setsuna$skipLocalPlayerTick(Entity entity, Operation<Void> original) {
        if (entity == Minecraft.getInstance().player && SkipTickUtility.consumeSkipTick()) {
            return;
        }
        original.call(entity);
    }
}
