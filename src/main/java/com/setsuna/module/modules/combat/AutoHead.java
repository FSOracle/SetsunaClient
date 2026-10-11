package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.player.InvHelper;
import com.setsuna.util.player.InvUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Uses UHC player heads automatically, prioritizing golden heads at critical health. */
public final class AutoHead extends Module {

    public static final AutoHead INSTANCE = new AutoHead();

    private static final int FORCE_RETRY_TICKS = 2;
    private static final int HOTBAR_SIZE = 9;

    public final DoubleSetting health = add(new DoubleSetting("Health", 12.0, 1.0, 40.0, 0.5));
    public final DoubleSetting forceGoldenHealth = add(
            new DoubleSetting("Force Golden Health", 6.0, 0.0, 40.0, 0.5));
    public final IntSetting delay = add(new IntSetting("Delay", 10, 1, 40, 1));

    private int retryTicks;
    private int restoreSlot = -1;
    private int activeSlot = -1;
    private boolean headUseActive;

    private AutoHead() {
        super("Auto Head", Category.COMBAT);
    }

    @Listen(priority = Priority.HIGHEST)
    private void onTick(PlayerTickEvent.Pre event) {
        restoreSlotIfNeeded();
        refreshHeadUseState();
        if (retryTicks > 0) {
            retryTicks--;
        }
        if (noPlayer() || mc.gameMode == null || mc.screen != null || retryTicks > 0
                || isUsingHead() || mc.player.hasEffect(MobEffects.REGENERATION)
                || mc.player.isSpectator()) {
            return;
        }

        float totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        HeadMode mode = chooseMode(totalHealth, health.get(), forceGoldenHealth.get());
        if (mode == HeadMode.NONE) {
            return;
        }

        boolean goldenOnly = mode == HeadMode.GOLDEN_ONLY;
        ItemStack offhand = mc.player.getOffhandItem();
        int goldenSlot = findHeadSlot(true);
        int playerSlot = goldenOnly ? -1 : findHeadSlot(false);
        HeadLocation location = chooseHeadLocation(mode,
                InvHelper.isGoldenHead(offhand), goldenSlot >= 0,
                isOrdinaryHead(offhand), playerSlot >= 0);
        switch (location) {
            case OFFHAND_GOLDEN, OFFHAND_PLAYER -> useHead(InteractionHand.OFF_HAND, goldenOnly);
            case HOTBAR_GOLDEN -> useInventoryHead(goldenSlot, goldenOnly);
            case HOTBAR_PLAYER -> useInventoryHead(playerSlot, false);
            case NONE -> {
            }
        }
    }

    private void useInventoryHead(int slot, boolean forced) {
        if (slot < HOTBAR_SIZE) {
            useHotbarHead(slot, forced);
            return;
        }

        InvUtils.invSwap(slot);
        try {
            useHead(InteractionHand.MAIN_HAND, forced);
        } finally {
            InvUtils.invSwapBack();
        }
    }

    private void useHotbarHead(int slot, boolean forced) {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (slot != selected) {
            restoreSlot = selected;
            activeSlot = slot;
            mc.player.getInventory().setSelectedSlot(slot);
        }
        useHead(InteractionHand.MAIN_HAND, forced);
    }

    private void useHead(InteractionHand hand, boolean forced) {
        headUseActive = true;
        InteractionResult result = mc.gameMode.useItem(mc.player, hand);
        if (result.consumesAction()) {
            mc.player.swing(hand);
            mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
        }
        retryTicks = forced ? FORCE_RETRY_TICKS : delay.get();
    }

    /** Returns whether AutoHead currently owns an in-progress head use. */
    public boolean isUsingHead() {
        if (!isEnabled() || mc.player == null) {
            return false;
        }
        return headUseActive || isUsingHeadItem();
    }

    private void refreshHeadUseState() {
        if (headUseActive && (mc.player == null || !mc.player.isUsingItem())) {
            headUseActive = false;
        }
    }

    private boolean isUsingHeadItem() {
        return mc.player != null
                && mc.player.isUsingItem()
                && isHeadStack(mc.player.getUseItem());
    }

    private static boolean isHeadStack(ItemStack stack) {
        return InvHelper.isGoldenHead(stack) || isOrdinaryHead(stack);
    }

    private int findHeadSlot(boolean goldenOnly) {
        int slotsToSearch = goldenOnly
                ? HOTBAR_SIZE
                : mc.player.getInventory().getNonEquipmentItems().size();
        for (int slot = 0; slot < slotsToSearch; slot++) {
            if (matchingHead(mc.player.getInventory().getItem(slot), goldenOnly)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean matchingHead(ItemStack stack, boolean goldenOnly) {
        return goldenOnly ? InvHelper.isGoldenHead(stack) : isOrdinaryHead(stack);
    }

    private static boolean isOrdinaryHead(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && !InvHelper.isGoldenHead(stack)
                && isOrdinaryHeadName(stack.getHoverName().getString());
    }

    static boolean isOrdinaryHeadName(String itemName) {
        if (itemName == null) {
            return false;
        }

        String normalized = itemName.trim().toLowerCase(Locale.ROOT);
        boolean containsHead = normalized.contains("head") || normalized.contains("头");
        boolean goldenHead = normalized.contains("golden head")
                || normalized.contains("gold head")
                || normalized.contains("金头");
        return containsHead && !goldenHead;
    }

    static HeadMode chooseMode(float totalHealth, double triggerHealth, double forceHealth) {
        if (!Float.isFinite(totalHealth) || totalHealth > triggerHealth) {
            return HeadMode.NONE;
        }
        return totalHealth <= Math.min(triggerHealth, forceHealth)
                ? HeadMode.GOLDEN_ONLY
                : HeadMode.GOLDEN_FIRST;
    }

    static HeadLocation chooseHeadLocation(HeadMode mode, boolean offhandGolden,
                                           boolean hotbarGolden, boolean offhandPlayer,
                                           boolean hotbarPlayer) {
        if (mode == HeadMode.NONE) {
            return HeadLocation.NONE;
        }
        if (offhandGolden) {
            return HeadLocation.OFFHAND_GOLDEN;
        }
        if (hotbarGolden) {
            return HeadLocation.HOTBAR_GOLDEN;
        }
        if (mode == HeadMode.GOLDEN_ONLY) {
            return HeadLocation.NONE;
        }
        if (offhandPlayer) {
            return HeadLocation.OFFHAND_PLAYER;
        }
        return hotbarPlayer ? HeadLocation.HOTBAR_PLAYER : HeadLocation.NONE;
    }

    private void restoreSlotIfNeeded() {
        if (restoreSlot < 0 || mc.player == null) {
            return;
        }
        if (mc.player.getInventory().getSelectedSlot() == activeSlot) {
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        }
        restoreSlot = -1;
        activeSlot = -1;
    }

    @Override
    protected void onDisable() {
        restoreSlotIfNeeded();
        retryTicks = 0;
        headUseActive = false;
    }

    enum HeadMode {
        NONE,
        GOLDEN_FIRST,
        GOLDEN_ONLY
    }

    enum HeadLocation {
        NONE,
        OFFHAND_GOLDEN,
        HOTBAR_GOLDEN,
        OFFHAND_PLAYER,
        HOTBAR_PLAYER
    }
}
