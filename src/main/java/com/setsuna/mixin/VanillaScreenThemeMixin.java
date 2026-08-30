package com.setsuna.mixin;

import com.setsuna.ui.screen.VanillaScreenTheme;
import com.setsuna.ui.screen.VanillaButtonOverlay;
import com.setsuna.render.SkijaRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class VanillaScreenThemeMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
    private void setsuna$beginThemedFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        if (VanillaScreenTheme.applies((Screen) (Object) this)) {
            VanillaButtonOverlay.beginFrame();
            VanillaScreenTheme.beginFrame((Screen) (Object) this);
        }
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void setsuna$finishThemedFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        if (VanillaScreenTheme.applies((Screen) (Object) this)) {
            VanillaButtonOverlay.endFrame();
            VanillaScreenTheme.drawTransition(graphics);
        }
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void setsuna$drawThemedBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick, CallbackInfo ci) {
        if (!VanillaScreenTheme.applies((Screen) (Object) this)) {
            return;
        }
        if (SkijaRenderer.hasFailed()) {
            VanillaScreenTheme.drawFallback(graphics);
        }
        ci.cancel();
    }
}
