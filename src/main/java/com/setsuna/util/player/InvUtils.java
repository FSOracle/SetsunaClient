package com.setsuna.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Inventory lookup and temporary slot-selection operations. */
public final class InvUtils {

    private static final int OFFHAND_INVENTORY_SLOT = 40;
    private static final int OFFHAND_MENU_SLOT = 45;
    private static final int HOTBAR_MENU_OFFSET = 36;
    private static final Minecraft mc = Minecraft.getInstance();

    public static int[] invSlots;
    public static int previousSlot = -1;

    private InvUtils() {
    }

    public static boolean testInMainHand(Predicate<ItemStack> matcher) {
        return matcher.test(mc.player.getMainHandItem());
    }

    public static boolean testInOffHand(Predicate<ItemStack> matcher) {
        return matcher.test(mc.player.getOffhandItem());
    }

    public static FindItemResult findInHotbar(Item... acceptedItems) {
        return findInHotbar(matchesAny(acceptedItems));
    }

    public static FindItemResult findInHotbar(Predicate<ItemStack> matcher) {
        ItemStack offhand = mc.player.getOffhandItem();
        if (matcher.test(offhand)) {
            return resultFor(OFFHAND_INVENTORY_SLOT, offhand);
        }

        ItemStack mainHand = mc.player.getMainHandItem();
        if (matcher.test(mainHand)) {
            return resultFor(mc.player.getInventory().getSelectedSlot(), mainHand);
        }

        return scan(matcher, 0, 8);
    }

    public static FindItemResult find(Item... acceptedItems) {
        return find(matchesAny(acceptedItems));
    }

    public static FindItemResult find(Predicate<ItemStack> matcher) {
        return scan(matcher, 0, mc.player.getInventory().getContainerSize());
    }

    public static FindItemResult find(Predicate<ItemStack> matcher, int start, int end) {
        return scan(matcher, start, end);
    }

    public static void swap(int requestedSlot, boolean rememberCurrentSlot) {
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (requestedSlot == OFFHAND_INVENTORY_SLOT || requestedSlot == selectedSlot) {
            return;
        }

        if (rememberCurrentSlot && previousSlot == -1) {
            previousSlot = selectedSlot;
        } else if (!rememberCurrentSlot) {
            previousSlot = -1;
        }
        mc.player.getInventory().setSelectedSlot(requestedSlot);
    }

    public static void swapBack() {
        int slotToRestore = previousSlot;
        if (slotToRestore == -1) {
            return;
        }
        swap(slotToRestore, false);
        previousSlot = -1;
    }

    public static void invSwap(int inventorySlot) {
        int menuSlot = toMenuSlot(inventorySlot);
        int hotbarButton = mc.player.getInventory().getSelectedSlot();
        sendSwap(menuSlot, hotbarButton);
        invSlots = new int[]{menuSlot, hotbarButton};
    }

    public static void invSwapBack() {
        if (invSlots == null || invSlots.length < 2) {
            return;
        }
        sendSwap(invSlots[0], invSlots[1]);
    }

    private static FindItemResult scan(Predicate<ItemStack> matcher, int firstSlot, int lastSlot) {
        int firstMatch = -1;
        int itemCount = 0;
        int totalCapacity = 0;

        for (int inventorySlot = firstSlot; inventorySlot <= lastSlot; inventorySlot++) {
            ItemStack candidate = mc.player.getInventory().getItem(inventorySlot);
            if (!matcher.test(candidate)) {
                continue;
            }
            if (firstMatch == -1) {
                firstMatch = inventorySlot;
            }
            itemCount += candidate.getCount();
            totalCapacity += candidate.getMaxStackSize();
        }
        return new FindItemResult(firstMatch, itemCount, totalCapacity);
    }

    private static Predicate<ItemStack> matchesAny(Item[] acceptedItems) {
        return stack -> {
            for (Item accepted : acceptedItems) {
                if (stack.getItem() == accepted) return true;
            }
            return false;
        };
    }

    private static FindItemResult resultFor(int slot, ItemStack stack) {
        return new FindItemResult(slot, stack.getCount(), stack.getMaxStackSize());
    }

    private static int toMenuSlot(int inventorySlot) {
        if (inventorySlot < 9) {
            return inventorySlot + HOTBAR_MENU_OFFSET;
        }
        return inventorySlot == OFFHAND_INVENTORY_SLOT ? OFFHAND_MENU_SLOT : inventorySlot;
    }

    private static void sendSwap(int menuSlot, int hotbarButton) {
        mc.gameMode.handleContainerInput(
                mc.player.containerMenu.containerId,
                menuSlot,
                hotbarButton,
                ContainerInput.SWAP,
                mc.player
        );
    }
}
