package com.setsuna.module.modules.movement;

import com.setsuna.module.Category;
import com.setsuna.module.Module;

/** Removes the local player's vanilla jump input cooldown. */
public final class NoJumpDelay extends Module {

    public static final NoJumpDelay INSTANCE = new NoJumpDelay();

    private NoJumpDelay() {
        super("No Jump Delay", Category.MOVEMENT);
    }
}
