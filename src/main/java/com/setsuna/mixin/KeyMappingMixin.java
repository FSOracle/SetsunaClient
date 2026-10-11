package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.setsuna.util.client.AttackKeyController;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mirrors LiquidBounce's simulated attack-key state for scheduled clicks. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean setsuna$simulateAttackKey(boolean original) {
        return AttackKeyController.modifyAttackKeyState(
                original,
                (KeyMapping) (Object) this
        );
    }
}
