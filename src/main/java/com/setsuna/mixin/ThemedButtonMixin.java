package com.setsuna.mixin;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({AbstractButton.class})
public abstract class ThemedButtonMixin extends AbstractWidget {
   protected ThemedButtonMixin(int x, int y, int width, int height, Component message) {
      super(x, y, width, height, message);
   }

}
