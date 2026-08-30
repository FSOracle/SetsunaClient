package com.setsuna.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.setsuna.module.modules.movement.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow
    @Final
    public Player player;

    @ModifyExpressionValue(
            method = {"removeFromSelected", "tick", "getSelectedItem", "setSelectedItem"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Inventory;selected:I",
                    opcode = Opcodes.GETFIELD))
    private int setsuna$scaffoldSilentSlot(int original) {
        if (player == Minecraft.getInstance().player) {
            return Scaffold.INSTANCE.modifyServerSelectedSlot(original);
        }
        return original;
    }
}
