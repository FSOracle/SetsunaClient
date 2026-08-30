package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.player.InvHelper;
import com.setsuna.util.timer.TimerUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChestStealer extends Module {

    public static final ChestStealer INSTANCE = new ChestStealer();

    private ChestStealer() {
        super("ChestStealer", Category.PLAYER);
    }

    private final IntSetting minDelay = add(new IntSetting("Min Delay", 50, 0, 1000, 50));
    private final IntSetting delay = add(new IntSetting("Delay", 50, 0, 1000, 50));
    private final IntSetting delayDeviation = add(new IntSetting("Delay Deviation", 35, 0, 500, 5).visibleWhen(() -> delay.get() > 0));
    private final BooleanSetting autoCloseChest = add(new BooleanSetting("Close Delay", true));
    private final IntSetting closeDelayValue = add(new IntSetting("Close Delay Value", 150, 0, 1000, 1).visibleWhen(autoCloseChest::get));
    private final IntSetting closeDelayDeviation = add(new IntSetting("Close Delay Deviation", 40, 0, 500, 5).visibleWhen(autoCloseChest::get));
    private final BooleanSetting pickEnderChest = add(new BooleanSetting("Ender Chest", false));

    private Screen lastTickScreen;
    private int nextClickDelay;
    private int nextCloseDelay;
    private boolean waitingToClose;

    private static final TimerUtils timer = new TimerUtils();
    private static final Random random = new Random();

    public boolean isWorking() {
        return !timer.hasDelayed(3);
    }

    public static boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else if (InvHelper.isGodItem(stack) || InvHelper.isSharpnessAxe(stack)) {
            return true;
        } else if (InvHelper.isArmor(stack)) {
            float protection = InvHelper.getProtection(stack);
            float bestArmor = InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack));
            return !(protection <= bestArmor);
        } else if (InvHelper.isSword(stack)) {
            float damage = InvHelper.getSwordDamage(stack);
            float bestDamage = InvHelper.getBestSwordDamage();
            return !(damage <= bestDamage);
        } else if (InvHelper.isPickaxe(stack)) {
            float score = InvHelper.getToolScore(stack);
            float bestScore = InvHelper.getBestPickaxeScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof AxeItem) {
            float score = InvHelper.getToolScore(stack);
            float bestScore = InvHelper.getBestAxeScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof ShovelItem) {
            float score = InvHelper.getToolScore(stack);
            float bestScore = InvHelper.getBestShovelScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof CrossbowItem) {
            float score = InvHelper.getCrossbowScore(stack);
            float bestScore = InvHelper.getBestCrossbowScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof BowItem && InvHelper.isPunchBow(stack)) {
            float score = InvHelper.getPunchBowScore(stack);
            float bestScore = InvHelper.getBestPunchBowScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof BowItem && InvHelper.isPowerBow(stack)) {
            float score = InvHelper.getPowerBowScore(stack);
            float bestScore = InvHelper.getBestPowerBowScore();
            return !(score <= bestScore);
        } else if (stack.getItem() == Items.COMPASS) {
            return !InvHelper.hasItem(stack.getItem());
        } else if (stack.getItem() == Items.WATER_BUCKET && InvHelper.getItemCount(Items.WATER_BUCKET) >= InvManager.INSTANCE.waterBucketCount.get()) {
            return false;
        } else if (stack.getItem() == Items.LAVA_BUCKET && InvHelper.getItemCount(Items.LAVA_BUCKET) >= InvManager.INSTANCE.lavaBucketCount.get()) {
            return false;
        } else if (stack.getItem() instanceof BlockItem
                && InvHelper.isValidStack(stack)
                && InvHelper.getBlockCountInInventory() + stack.getCount() >= InvManager.INSTANCE.maxBlockSize.get()) {
            return false;
        } else if (stack.getItem() == Items.ARROW && InvHelper.getItemCount(Items.ARROW) + stack.getCount() >= InvManager.INSTANCE.maxArrowSize.get()) {
            return false;
        } else if (stack.getItem() instanceof FishingRodItem && InvHelper.getItemCount(Items.FISHING_ROD) >= 1) {
            return false;
        } else if (stack.getItem() != Items.SNOWBALL && stack.getItem() != Items.EGG
                || InvHelper.getItemCount(Items.SNOWBALL) + InvHelper.getItemCount(Items.EGG) + stack.getCount() < InvManager.INSTANCE.maxProjectileSize.get()
                && InvManager.INSTANCE.keepProjectile.get()
        ) {
            return !(stack.getItem() instanceof StandingAndWallBlockItem) && InvHelper.isCommonItemUseful(stack);
        } else {
            return false;
        }
    }

    private static boolean isBestItemInContainer(AbstractContainerMenu menu, int containerSlots, ItemStack stack) {
        if (!InvHelper.isGodItem(stack) && !InvHelper.isSharpnessAxe(stack)) {
            for (int i = 0; i < containerSlots; i++) {
                ItemStack checkStack = menu.getSlot(i).getItem();
                if (InvHelper.isArmor(stack) && InvHelper.isArmor(checkStack)) {
                    if (InvHelper.getArmorSlot(stack) == InvHelper.getArmorSlot(checkStack)
                            && InvHelper.getProtection(checkStack) > InvHelper.getProtection(stack)) {
                        return false;
                    }
                } else if (InvHelper.isSword(stack) && InvHelper.isSword(checkStack)) {
                    if (InvHelper.getSwordDamage(checkStack) > InvHelper.getSwordDamage(stack)) {
                        return false;
                    }
                } else if (InvHelper.isPickaxe(stack) && InvHelper.isPickaxe(checkStack)) {
                    if (InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof AxeItem && checkStack.getItem() instanceof AxeItem) {
                    if (InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof ShovelItem
                        && checkStack.getItem() instanceof ShovelItem
                        && InvHelper.getToolScore(checkStack) > InvHelper.getToolScore(stack)) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        Screen currentScreen = mc.screen;
        if (!(currentScreen instanceof AbstractContainerScreen<?> container)) {
            this.lastTickScreen = currentScreen;
            this.waitingToClose = false;
            return;
        }

        if (noPlayer() || mc.gameMode == null) {
            this.lastTickScreen = currentScreen;
            return;
        }

        AbstractContainerMenu menu = container.getMenu();
        int containerSlots = getContainerSlotCount(menu);
        if (containerSlots <= 0) {
            this.lastTickScreen = currentScreen;
            return;
        }

        if (currentScreen != this.lastTickScreen) {
            resetContainerTiming();
            this.lastTickScreen = currentScreen;
            return;
        }

        if (this.isContainerEmpty(menu, containerSlots)) {
            handleEmptyContainer();
        } else {
            this.waitingToClose = false;
            takeUsefulItems(menu, containerSlots);
        }

        this.lastTickScreen = currentScreen;
    }

    private void handleEmptyContainer() {
        if (!autoCloseChest.get()) {
            return;
        }
        if (!waitingToClose) {
            nextCloseDelay = sampleCloseDelay();
            waitingToClose = true;
        }
        if (timer.passedMillis(nextCloseDelay)) {
            mc.player.closeContainer();
            resetContainerTiming();
        }
    }

    private void takeUsefulItems(AbstractContainerMenu menu, int containerSlots) {
        if (!timer.passedMillis(nextClickDelay)) {
            return;
        }

        boolean movedItem = false;
        List<Integer> slots = IntStream.range(0, containerSlots).boxed().collect(Collectors.toList());
        Collections.shuffle(slots, random);

        for (Integer pSlotId : slots) {
            ItemStack stack = menu.getSlot(pSlotId).getItem();
            if (isItemUseful(stack) && isBestItemInContainer(menu, containerSlots, stack)) {
                mc.gameMode.handleContainerInput(menu.containerId, pSlotId, 0, ContainerInput.QUICK_MOVE, mc.player);
                movedItem = true;
                if (nextClickDelay > 0) {
                    resetContainerTiming();
                    return;
                }
            }
        }

        if (movedItem) {
            resetContainerTiming();
        }
    }

    private void resetContainerTiming() {
        timer.reset();
        nextClickDelay = sampleClickDelay();
        waitingToClose = false;
    }

    private int sampleClickDelay() {
        return sampleGaussianDelay(delay.get(), delayDeviation.get(), minDelay.get());
    }

    private int sampleCloseDelay() {
        return sampleGaussianDelay(closeDelayValue.get(), closeDelayDeviation.get(), 0);
    }

    private static int sampleGaussianDelay(int mean, int deviation, int min) {
        if (mean <= 0) {
            return 0;
        }

        int lowerBound = Math.max(0, min);
        int center = Math.max(mean, lowerBound);
        int sigma = Math.max(0, deviation);
        if (sigma <= 0) {
            return center;
        }

        int upperBound = Math.max(center, center + sigma * 3);
        for (int i = 0; i < 8; i++) {
            int sample = (int) Math.round(center + random.nextGaussian() * sigma);
            if (sample >= lowerBound && sample <= upperBound) {
                return sample;
            }
        }

        int sample = (int) Math.round(center + random.nextGaussian() * sigma);
        return Math.max(lowerBound, Math.min(upperBound, sample));
    }

    private int getContainerSlotCount(AbstractContainerMenu menu) {
        if (mc.player != null && menu == mc.player.inventoryMenu) {
            return 0;
        }

        int slots = menu.slots.size();
        return slots > 36 ? slots - 36 : 0;
    }

    private boolean isContainerEmpty(AbstractContainerMenu menu, int containerSlots) {
        for (int i = 0; i < containerSlots; i++) {
            ItemStack item = menu.getSlot(i).getItem();
            if (!item.isEmpty() && isItemUseful(item) && isBestItemInContainer(menu, containerSlots, item)) {
                return false;
            }
        }

        return true;
    }

}
