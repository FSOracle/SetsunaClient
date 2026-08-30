package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.setsuna.module.modules.movement.Scaffold;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientInput.class)
public abstract class ClientInputMixin {

    @Shadow
    protected Vec2 moveVector;

    @ModifyReturnValue(method = "hasForwardImpulse", at = @At("RETURN"))
    private boolean setsuna$scaffoldOmnidirectionalSprint(boolean original) {
        if (Scaffold.INSTANCE.shouldSprintOmnidirectionally()) {
            return Math.abs(moveVector.x) > 1.0E-5F || Math.abs(moveVector.y) > 1.0E-5F;
        }
        return original;
    }
}
