package com.setsuna.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {

    @Accessor("wasSprinting")
    boolean setsuna$wasSprinting();

    @Invoker("sendPosition")
    void setsuna$sendPosition();
}
