package com.setsuna.mixin;

import com.setsuna.ui.screen.VanillaButtonOverlay;
import com.setsuna.ui.screen.VanillaScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class ThemedButtonMixin extends AbstractWidget {

    protected ThemedButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void setsuna$drawThemedButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        if (!VanillaScreenTheme.canReplaceVanillaButtons(Minecraft.getInstance().screen)) {
            return;
        }
        VanillaButtonOverlay.add(getX(), getY(), width, height, getMessage().getString(),
                isHoveredOrFocused(), active, getAlpha());
        handleCursor(graphics);
        ci.cancel();
    }
}
