package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.util.player.InvHelper;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class AutoTotem extends Module {

    public static final AutoTotem INSTANCE = new AutoTotem();

    private final BooleanSetting strict = add(new BooleanSetting("Strict", true));
    private final DoubleSetting health = add(new DoubleSetting("Health", 16.0, 0.0, 36.0, 0.5));
    private final BooleanSetting checkGapple = add(new BooleanSetting("Check Gapple", true));

    private AutoTotem() {
        super("Auto Totem", Category.COMBAT);
    }

    @Override
    public String getInfo() {
        return noPlayer() ? null : Integer.toString(InvHelper.getItemCount(Items.TOTEM_OF_UNDYING));
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer() || mc.gameMode == null || !shouldHoldTotem()) {
            return;
        }
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        int slot = InvHelper.getItemSlot(Items.TOTEM_OF_UNDYING);
        if (slot >= 0) {
            moveItemToOffhand(slot);
        }
    }

    private boolean shouldHoldTotem() {
        float totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (totalHealth <= health.get()) {
            return true;
        }
        if (mc.player.getOffhandItem().isEmpty() || mc.player.getY() < -64.0) {
            return true;
        }
        if (!checkGapple.get()) {
            return false;
        }

        Item mainHandItem = mc.player.getMainHandItem().getItem();
        return mainHandItem == Items.GOLDEN_APPLE || mainHandItem == Items.ENCHANTED_GOLDEN_APPLE;
    }

    private void moveItemToOffhand(int inventorySlot) {
        int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        int containerId = mc.player.inventoryMenu.containerId;

        if (!strict.get()) {
            mc.gameMode.handleContainerInput(
                    containerId, containerSlot, 40, ContainerInput.SWAP, mc.player);
            return;
        }

        mc.gameMode.handleContainerInput(
                containerId, containerSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(
                containerId, 45, 0, ContainerInput.PICKUP, mc.player);
        if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
            mc.gameMode.handleContainerInput(
                    containerId, containerSlot, 0, ContainerInput.PICKUP, mc.player);
        }
    }
}
