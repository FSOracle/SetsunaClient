package com.setsuna.util.player;

import com.setsuna.Setsuna;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-side chat helpers. Messages are added locally (never sent to the
 * server) with a {@code [Setsuna]} prefix, marshalled onto the client thread.
 */
public final class ChatUtils {

    public static final String PREFIX = "[Setsuna] ";

    private static final Minecraft mc = Minecraft.getInstance();

    private ChatUtils() {
    }

    public static void addChatMessage(String message) {
        addChatMessage(Component.literal(PREFIX + message));
    }

    public static void addChatMessage(Component message) {
        if (mc.gui == null) {
            Setsuna.LOGGER.info("[chat] {}", message.getString());
            return;
        }
        if (mc.isSameThread()) {
            mc.gui.getChat().addClientSystemMessage(message);
        } else {
            mc.execute(() -> mc.gui.getChat().addClientSystemMessage(message));
        }
    }
}
