package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.module.modules.movement.InvMove;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires {@link KeyboardInputEvent} after raw key state is resolved, then rebuilds
 * the player's {@link Input} and move vector from the (possibly modified) values.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean setsuna$inventoryMoveInput(KeyMapping mapping, Operation<Boolean> original) {
        return original.call(mapping) || InvMove.INSTANCE.suppliesInput(mapping);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void setsuna$onInputTick(CallbackInfo ci) {
        Input original = this.keyPresses;
        KeyboardInputEvent event = EventBus.INSTANCE.post(new KeyboardInputEvent(
                original.forward(), original.backward(), original.left(), original.right(),
                original.jump(), original.shift(), original.sprint()));
        this.keyPresses = event.toInput();
        this.moveVector = new Vec2(event.getStrafe(), event.getForward()).normalized();
    }
}
