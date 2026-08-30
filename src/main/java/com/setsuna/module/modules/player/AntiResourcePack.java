package com.setsuna.module.modules.player;

import com.setsuna.Setsuna;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

import java.util.function.Consumer;

/** Spoofs a successful server resource-pack load without downloading the pack. */
public final class AntiResourcePack extends Module {

    public static final AntiResourcePack INSTANCE = new AntiResourcePack();

    private AntiResourcePack() {
        super("AntiResourcePack", Category.PLAYER);
    }

    public boolean handlePush(ClientboundResourcePackPushPacket packet,
                              Consumer<ServerboundResourcePackPacket> sender) {
        if (!isEnabled()) {
            return false;
        }

        sender.accept(new ServerboundResourcePackPacket(
                packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
        sender.accept(new ServerboundResourcePackPacket(
                packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        Setsuna.LOGGER.info("Blocked server resource pack: {}", packet.id());
        return true;
    }
}
