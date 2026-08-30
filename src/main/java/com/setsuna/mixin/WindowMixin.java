package com.setsuna.mixin;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import com.setsuna.Setsuna;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Replaces the vanilla window icon with the Setsuna client icon.
 * <p>
 * The icon PNGs ship under {@code assets/setsuna/textures/icons/}; when either
 * resource is missing we fall back to the vanilla icons so the window still
 * gets a valid icon.
 */
@Mixin(Window.class)
public class WindowMixin {

    @Redirect(
            method = "setIcon",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/IconSet;getStandardIcons(Lnet/minecraft/server/packs/PackResources;)Ljava/util/List;"))
    private List<IoSupplier<InputStream>> setsuna$icons(IconSet iconSet, PackResources resources) throws IOException {
        IoSupplier<InputStream> icon16 = () -> Setsuna.class.getResourceAsStream("/assets/setsuna/textures/icons/icon_16x16.png");
        IoSupplier<InputStream> icon32 = () -> Setsuna.class.getResourceAsStream("/assets/setsuna/textures/icons/icon_32x32.png");
        try (InputStream stream16 = icon16.get(); InputStream stream32 = icon32.get()) {
            if (stream16 != null && stream32 != null) {
                Setsuna.LOGGER.info("Applying {} window icon.", Setsuna.NAME);
                return List.of(icon16, icon32);
            }
        }

        Setsuna.LOGGER.warn("{} window icon resources are missing, falling back to vanilla icons.", Setsuna.NAME);
        return iconSet.getStandardIcons(resources);
    }
}
