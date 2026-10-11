package com.setsuna.test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

/** Initializes the vanilla component holders that Minecraft 26 binds after registry bootstrap. */
public final class MinecraftTestBootstrap {
    private MinecraftTestBootstrap() { }

    public static synchronized void initializeItemComponents() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (Items.AIR.builtInRegistryHolder().areComponentsBound()) return;
        var registries = VanillaRegistries.createLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
                .forEach(DataComponentInitializers.PendingComponents::apply);
    }
}
