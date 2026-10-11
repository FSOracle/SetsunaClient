package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.inventory.InventorySlotHighlight;
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
        if (stack.isEmpty()) return false;

        return switch (classify(stack)) {
            case SPECIAL -> true;
            case ARMOR -> beatsInventoryBest(
                    InvHelper.getProtection(stack),
                    InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack)));
            case SWORD -> beatsInventoryBest(
                    InvHelper.getSwordDamage(stack),
                    InvHelper.getBestSwordDamage());
            case PICKAXE -> beatsInventoryBest(
                    InvHelper.getToolScore(stack),
                    InvHelper.getBestPickaxeScore());
            case AXE -> beatsInventoryBest(
                    InvHelper.getToolScore(stack),
                    InvHelper.getBestAxeScore());
            case SHOVEL -> beatsInventoryBest(
                    InvHelper.getToolScore(stack),
                    InvHelper.getBestShovelScore());
            case CROSSBOW -> beatsInventoryBest(
                    InvHelper.getCrossbowScore(stack),
                    InvHelper.getBestCrossbowScore());
            case PUNCH_BOW -> beatsInventoryBest(
                    InvHelper.getPunchBowScore(stack),
                    InvHelper.getBestPunchBowScore());
            case POWER_BOW -> beatsInventoryBest(
                    InvHelper.getPowerBowScore(stack),
                    InvHelper.getBestPowerBowScore());
            case COMPASS -> !InvHelper.hasItem(stack.getItem());
            case WATER_BUCKET -> InvHelper.getItemCount(Items.WATER_BUCKET)
                    < InvManager.INSTANCE.waterBucketCount.get() && isOrdinarilyUseful(stack);
            case LAVA_BUCKET -> InvHelper.getItemCount(Items.LAVA_BUCKET)
                    < InvManager.INSTANCE.lavaBucketCount.get() && isOrdinarilyUseful(stack);
            case BLOCK -> belowBlockLimitOrUnmanaged(stack) && isOrdinarilyUseful(stack);
            case ARROW -> InvHelper.getItemCount(Items.ARROW) + stack.getCount()
                    < InvManager.INSTANCE.maxArrowSize.get() && isOrdinarilyUseful(stack);
            case FISHING_ROD -> InvHelper.getItemCount(Items.FISHING_ROD) < 1
                    && isOrdinarilyUseful(stack);
            case PROJECTILE -> belowProjectileLimit(stack) && isOrdinarilyUseful(stack);
            case COMMON -> isOrdinarilyUseful(stack);
        };
    }

    private static boolean isBestItemInContainer(AbstractContainerMenu menu, int containerSlots, ItemStack stack) {
        if (InvHelper.isGodItem(stack) || InvHelper.isSharpnessAxe(stack)) return true;

        GearKind gear = gearKind(stack);
        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack other = menu.getSlot(slot).getItem();
            if (outclasses(gear, stack, other)) return false;
        }
        return true;
    }

    private static ItemClass classify(ItemStack stack) {
        if (InvHelper.isGodItem(stack) || InvHelper.isSharpnessAxe(stack)) return ItemClass.SPECIAL;
        if (InvHelper.isArmor(stack)) return ItemClass.ARMOR;
        if (InvHelper.isSword(stack)) return ItemClass.SWORD;
        if (InvHelper.isPickaxe(stack)) return ItemClass.PICKAXE;

        Item item = stack.getItem();
        if (item instanceof AxeItem) return ItemClass.AXE;
        if (item instanceof ShovelItem) return ItemClass.SHOVEL;
        if (item instanceof CrossbowItem) return ItemClass.CROSSBOW;
        if (item instanceof BowItem && InvHelper.isPunchBow(stack)) return ItemClass.PUNCH_BOW;
        if (item instanceof BowItem && InvHelper.isPowerBow(stack)) return ItemClass.POWER_BOW;
        if (item == Items.COMPASS) return ItemClass.COMPASS;
        if (item == Items.WATER_BUCKET) return ItemClass.WATER_BUCKET;
        if (item == Items.LAVA_BUCKET) return ItemClass.LAVA_BUCKET;
        if (item instanceof BlockItem) return ItemClass.BLOCK;
        if (item == Items.ARROW) return ItemClass.ARROW;
        if (item instanceof FishingRodItem) return ItemClass.FISHING_ROD;
        if (item == Items.SNOWBALL || item == Items.EGG) return ItemClass.PROJECTILE;
        return ItemClass.COMMON;
    }

    private static boolean beatsInventoryBest(float candidate, float inventoryBest) {
        return !(candidate <= inventoryBest);
    }

    private static boolean belowBlockLimitOrUnmanaged(ItemStack stack) {
        return !InvHelper.isValidStack(stack)
                || InvHelper.getBlockCountInInventory() + stack.getCount()
                < InvManager.INSTANCE.maxBlockSize.get();
    }

    private static boolean belowProjectileLimit(ItemStack stack) {
        int held = InvHelper.getItemCount(Items.SNOWBALL) + InvHelper.getItemCount(Items.EGG);
        return held + stack.getCount() < InvManager.INSTANCE.maxProjectileSize.get()
                && InvManager.INSTANCE.keepProjectile.get();
    }

    private static boolean isOrdinarilyUseful(ItemStack stack) {
        return !(stack.getItem() instanceof StandingAndWallBlockItem)
                && InvHelper.isCommonItemUseful(stack);
    }

    private static GearKind gearKind(ItemStack stack) {
        if (InvHelper.isArmor(stack)) return GearKind.ARMOR;
        if (InvHelper.isSword(stack)) return GearKind.SWORD;
        if (InvHelper.isPickaxe(stack)) return GearKind.PICKAXE;
        if (stack.getItem() instanceof AxeItem) return GearKind.AXE;
        if (stack.getItem() instanceof ShovelItem) return GearKind.SHOVEL;
        return GearKind.NONE;
    }

    private static boolean outclasses(GearKind kind, ItemStack candidate, ItemStack other) {
        return switch (kind) {
            case ARMOR -> InvHelper.isArmor(other)
                    && InvHelper.getArmorSlot(candidate) == InvHelper.getArmorSlot(other)
                    && InvHelper.getProtection(other) > InvHelper.getProtection(candidate);
            case SWORD -> InvHelper.isSword(other)
                    && InvHelper.getSwordDamage(other) > InvHelper.getSwordDamage(candidate);
            case PICKAXE -> InvHelper.isPickaxe(other)
                    && InvHelper.getToolScore(other) > InvHelper.getToolScore(candidate);
            case AXE -> other.getItem() instanceof AxeItem
                    && InvHelper.getToolScore(other) > InvHelper.getToolScore(candidate);
            case SHOVEL -> other.getItem() instanceof ShovelItem
                    && InvHelper.getToolScore(other) > InvHelper.getToolScore(candidate);
            case NONE -> false;
        };
    }

    private enum ItemClass {
        SPECIAL,
        ARMOR,
        SWORD,
        PICKAXE,
        AXE,
        SHOVEL,
        CROSSBOW,
        PUNCH_BOW,
        POWER_BOW,
        COMPASS,
        WATER_BUCKET,
        LAVA_BUCKET,
        BLOCK,
        ARROW,
        FISHING_ROD,
        PROJECTILE,
        COMMON
    }

    private enum GearKind {
        ARMOR,
        SWORD,
        PICKAXE,
        AXE,
        SHOVEL,
        NONE
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
                InventorySlotHighlight.mark(menu, pSlotId);
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
