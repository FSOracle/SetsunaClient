package com.setsuna.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the {@code leftPos} and {@code topPos} fields from
 * {@link AbstractContainerScreen} so other mixins can read the container
 * background position without shadowing inherited fields.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int setsuna$getLeftPos();

    @Accessor("topPos")
    int setsuna$getTopPos();
}
