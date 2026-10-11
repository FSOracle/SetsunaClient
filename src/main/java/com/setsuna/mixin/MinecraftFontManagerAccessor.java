package com.setsuna.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the vanilla font manager for narrowly scoped glyph-atlas recovery. */
@Mixin(Minecraft.class)
public interface MinecraftFontManagerAccessor {

    @Accessor("fontManager")
    FontManager setsuna$getFontManager();

    @Mutable
    @Accessor("font")
    void setsuna$setFont(Font font);

    @Mutable
    @Accessor("fontFilterFishy")
    void setsuna$setFontFilterFishy(Font font);
}
