package com.setsuna.accessor;

import net.minecraft.client.User;

/** Allows the alt manager to replace the active client session. */
public interface MinecraftSessionAccessor {

    void setsuna$setUser(User user);
}
