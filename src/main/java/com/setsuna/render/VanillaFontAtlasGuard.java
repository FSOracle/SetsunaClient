package com.setsuna.render;

import com.setsuna.Setsuna;
import com.setsuna.mixin.MinecraftFontManagerAccessor;
import net.minecraft.client.Minecraft;

/** Rebuilds vanilla glyph textures after a renderer or client-font transition. */
public final class VanillaFontAtlasGuard {

    private static boolean refreshQueued;

    private VanillaFontAtlasGuard() {
    }

    public static synchronized void requestRefresh() {
        if (refreshQueued) return;
        refreshQueued = true;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            synchronized (VanillaFontAtlasGuard.class) {
                refreshQueued = false;
            }
            try {
                MinecraftFontManagerAccessor accessor =
                        (MinecraftFontManagerAccessor) (Object) minecraft;
                var fontManager = accessor.setsuna$getFontManager();
                fontManager.updateOptions(minecraft.options);
                accessor.setsuna$setFont(fontManager.createFont());
                accessor.setsuna$setFontFilterFishy(fontManager.createFontFilterFishy());
            } catch (Throwable error) {
                Setsuna.LOGGER.debug("Unable to refresh the vanilla glyph atlas", error);
            }
        });
    }
}
