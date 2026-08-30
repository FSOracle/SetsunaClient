package com.setsuna.mixin;

import com.setsuna.ui.screen.MainMenuScreen;
import com.setsuna.ui.screen.TitleScreenMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Setsuna home-screen entry without requiring an account gate. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void setsuna$replaceTitle(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (TitleScreenMode.isVanilla()) {
            int buttonWidth = Math.max(104, Math.min(138, Math.round(width * 0.16F)));
            int buttonHeight = 27;
            int buttonX = Math.max(8, width - 28 - buttonWidth);
            int buttonY = Math.max(8, height - buttonHeight - 14);
            addRenderableWidget(Button.builder(Component.literal("SETSUNA UI"), button -> {
                TitleScreenMode.useSetsuna();
                client.setScreen(new MainMenuScreen());
            }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());
            return;
        }
        client.setScreen(new MainMenuScreen());
    }
}
