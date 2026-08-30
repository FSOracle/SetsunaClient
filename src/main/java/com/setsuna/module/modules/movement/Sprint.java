package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;


public final class Sprint extends Module {

    public static final Sprint INSTANCE = new Sprint();

    private Sprint() {
        super("Sprint", Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        if (mc.options.keySprint.isDown()) mc.options.keySprint.setDown(false);
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        mc.options.keySprint.setDown(true);
    }
}
