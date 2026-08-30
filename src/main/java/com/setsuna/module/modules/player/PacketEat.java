package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/** Keeps the server-side use action active for food that may always be eaten. */
public final class PacketEat extends Module {

    public static final PacketEat INSTANCE = new PacketEat();

    private ItemStack activeFood = ItemStack.EMPTY;

    private PacketEat() {
        super("Packet Eat", Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        activeFood = ItemStack.EMPTY;
    }

    @Listen
    private void onPostTick(PlayerTickEvent.Post event) {
        if (noPlayer()) {
            activeFood = ItemStack.EMPTY;
        } else if (mc.player.isUsingItem()) {
            activeFood = mc.player.getUseItem();
        }
    }

    @Listen
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundPlayerActionPacket packet)
                || packet.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                || activeFood.isEmpty()) {
            return;
        }

        FoodProperties food = activeFood.get(DataComponents.FOOD);
        if (food != null && food.canAlwaysEat()) {
            event.cancel();
        }
    }
}
