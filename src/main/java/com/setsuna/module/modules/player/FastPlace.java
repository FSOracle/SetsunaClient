package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.MouseButtonEvent;
import com.setsuna.event.events.UseCooldownEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.IntSetting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileItem;
import org.lwjgl.glfw.GLFW;

/** Reduces vanilla's right-click use cooldown for blocks and projectiles. */
public final class FastPlace extends Module {

    public static final FastPlace INSTANCE = new FastPlace();

    private final IntSetting cooldown = add(new IntSetting("Cooldown", 0, 0, 4, 1));
    private final BooleanSetting blocks = add(new BooleanSetting("Blocks", true));
    private final BooleanSetting projectiles = add(new BooleanSetting("Projectiles", true));
    private final IntSetting startDelay = add(new IntSetting("Start Delay", 0, 0, 1000, 50));

    private long lastUsePressMs;

    private FastPlace() {
        super("Fast Place", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        lastUsePressMs = 0L;
    }

    @Listen
    private void onMouseButton(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.action() == GLFW.GLFW_PRESS) {
            lastUsePressMs = System.currentTimeMillis();
        }
    }

    @Listen
    private void onUseCooldown(UseCooldownEvent event) {
        if (noPlayer() || !matchesHeldItem() || !hasPassedStartDelay()) {
            return;
        }

        event.setCooldown(cooldown.get());
    }

    private boolean matchesHeldItem() {
        return matches(mc.player.getMainHandItem().getItem())
                || matches(mc.player.getOffhandItem().getItem());
    }

    private boolean matches(Item item) {
        return blocks.get() && item instanceof BlockItem
                || projectiles.get() && item instanceof ProjectileItem;
    }

    private boolean hasPassedStartDelay() {
        int delay = startDelay.get();
        return delay <= 0 || lastUsePressMs == 0L || System.currentTimeMillis() - lastUsePressMs >= delay;
    }
}
