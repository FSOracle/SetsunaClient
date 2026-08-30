package com.setsuna.mixin;

import com.setsuna.ui.screen.MenuBackgroundSettings;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds shared background import controls to Minecraft's options screen. */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {

    @Unique
    private Button setsuna$resetBackground;

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void setsuna$addBackgroundControls(CallbackInfo ci) {
        int resetWidth = 68;
        int importWidth = 112;
        int gap = 4;
        int totalWidth = importWidth + gap + resetWidth;
        int x = Math.max(8, width - totalWidth - 8);
        int y = width < 500 ? 32 : 8;

        addRenderableWidget(Button.builder(Component.literal("IMPORT IMAGE"), button -> {
            if (MenuBackgroundSettings.importImage()) {
                setsuna$resetBackground.active = true;
            }
        }).bounds(x, y, importWidth, 20).build());

        setsuna$resetBackground = addRenderableWidget(Button.builder(Component.literal("RESET"), button -> {
            if (MenuBackgroundSettings.reset()) {
                button.active = false;
            }
        }).bounds(x + importWidth + gap, y, resetWidth, 20).build());
        setsuna$resetBackground.active = MenuBackgroundSettings.canReset();
    }
}
