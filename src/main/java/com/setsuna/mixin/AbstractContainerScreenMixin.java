package com.setsuna.mixin;

import com.setsuna.module.modules.movement.InvMove;
import com.setsuna.ui.inventory.InventorySlotHighlight;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Legit-mode inventory action and close guards from the D:\1 implementation. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void setsuna$delayLegitInventoryClose(CallbackInfo ci) {
        if (InvMove.INSTANCE.interceptInventoryClose()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void setsuna$blockSlotActionWhileMoving(
            Slot slot,
            int slotId,
            int button,
            ContainerInput action,
            CallbackInfo ci) {
        if (InvMove.INSTANCE.blocksInventoryClick()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void setsuna$renderAutomatedSlotHighlights(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        InventorySlotHighlight.render((AbstractContainerScreen<?>) (Object) this, graphics);
    }
}
