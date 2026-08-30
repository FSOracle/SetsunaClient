package com.setsuna.mixin;

import com.setsuna.module.modules.player.FastCraftModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects FastCraft rendering and click handling into the inventory screen.
 * <p>
 * Only activates on {@link InventoryScreen} (player inventory), not on
 * crafting tables or other {@link AbstractRecipeBookScreen} subclasses.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {

    @Unique
    private boolean setsuna$isInventoryScreen() {
        return (Object) this instanceof InventoryScreen;
    }

    @Unique
    private FastCraftModule setsuna$getFastCraft() {
        FastCraftModule module = FastCraftModule.INSTANCE;
        return module.isEnabled() ? module : null;
    }

    @Unique
    private int setsuna$getFastCraftLeftPos() {
        return ((AbstractContainerScreenAccessor) this).setsuna$getLeftPos();
    }

    @Unique
    private int setsuna$getFastCraftTopPos() {
        return ((AbstractContainerScreenAccessor) this).setsuna$getTopPos();
    }

    /**
     * Renders the FastCraft panel after all other screen content.
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void setsuna$renderFastCraftPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick, CallbackInfo ci) {
        if (!setsuna$isInventoryScreen()) return;
        FastCraftModule fastCraft = setsuna$getFastCraft();
        if (fastCraft == null) return;

        fastCraft.render(graphics, setsuna$getFastCraftLeftPos(), setsuna$getFastCraftTopPos(), mouseX, mouseY);
    }

    /**
     * Intercepts mouse clicks before the recipe book handles them.
     * If the click lands on the FastCraft panel, the event is consumed.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void setsuna$clickFastCraftPanel(MouseButtonEvent event, boolean doubleClick,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!setsuna$isInventoryScreen()) return;
        if (event.button() != 0) return; // Only left-click

        FastCraftModule fastCraft = setsuna$getFastCraft();
        if (fastCraft == null) return;

        if (fastCraft.mouseClicked(
                event.x(),
                event.y(),
                setsuna$getFastCraftLeftPos(),
                setsuna$getFastCraftTopPos())) {
            cir.setReturnValue(true);
        }
    }
}
