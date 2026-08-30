package com.setsuna.module.modules.misc;

import com.setsuna.Setsuna;
import com.setsuna.config.ConfigManager;
import com.setsuna.event.Listen;
import com.setsuna.event.events.MouseButtonEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.notification.NotificationManager;
import com.setsuna.notification.NotificationType;
import com.setsuna.util.player.ChatUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

/** Toggles the player under the crosshair in the friend list with middle click. */
public final class MiddleClickFriend extends Module {

    public static final MiddleClickFriend INSTANCE = new MiddleClickFriend();

    private MiddleClickFriend() {
        super("MiddleClickFriend", Category.MISC);
    }

    @Listen
    private void onMouseButton(MouseButtonEvent event) {
        if (event.action() != GLFW.GLFW_PRESS
                || event.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || mc.screen != null
                || noPlayer()
                || !(mc.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof Player player)
                || player == mc.player) {
            return;
        }

        String name = player.getGameProfile().name();
        if (name == null || name.isBlank()) {
            return;
        }

        boolean added;
        if (FriendManager.INSTANCE.isFriend(name)) {
            FriendManager.INSTANCE.remove(name);
            added = false;
        } else {
            FriendManager.INSTANCE.add(name);
            added = true;
        }

        String message = (added ? "Added " : "Removed ") + name
                + (added ? " as a friend." : " from friends.");
        ChatUtils.addChatMessage(message);
        NotificationManager.INSTANCE.post(
                added ? NotificationType.SUCCESS : NotificationType.INFO,
                "MiddleClickFriend",
                message);
        try {
            ConfigManager.INSTANCE.saveChecked();
        } catch (IOException | RuntimeException error) {
            Setsuna.LOGGER.error("Failed to save middle-click friend change", error);
        }
    }
}
