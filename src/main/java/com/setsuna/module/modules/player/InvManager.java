package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.inventory.InventorySlotHighlight;
import com.setsuna.util.legendwatch.LegendaryIcons;
import com.setsuna.util.player.InvHelper;
import com.setsuna.util.player.MoveUtils;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.timer.TimerUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class InvManager extends Module {

    public static final InvManager INSTANCE = new InvManager();

    private enum OffhandItemMode {
        None,
        GoldenApple,
        Projectile,
        FishingRod,
        Block
    }

    private enum BowPriorityMode {
        Crossbow,
        PowerBow,
        PunchBow
    }

    private final IntSetting minDelay = add(new IntSetting("Min Delay", 50, 0, 1000, 50));
    private final IntSetting delay = add(new IntSetting("Delay", 50, 0, 1000, 50));
    private final EnumSetting<OffhandItemMode> offhandItems = add(new EnumSetting<>("Offhand Items", OffhandItemMode.None));
    private final BooleanSetting autoArmor = add(new BooleanSetting("Auto Armor", true));
    private final BooleanSetting inventoryOnly = add(new BooleanSetting("Inventory Only", true));
    private final BooleanSetting switchSword = add(new BooleanSetting("Switch Sword", true));
    private final IntSetting swordSlot = add(new IntSetting("Sword Slot", 1, 1, 9, 1).visibleWhen(switchSword::get));
    private final BooleanSetting switchBlock = add(new BooleanSetting("Switch Block", true).visibleWhen( () -> !offhandItems.is(OffhandItemMode.Block)));
    private final IntSetting blockSlot = add(new IntSetting("Block Slot", 2, 1, 9, 1).visibleWhen( () -> switchBlock.get() && !offhandItems.is(OffhandItemMode.Block)));
    public final IntSetting maxBlockSize = add(new IntSetting("Max Block Size", 256, 64, 512, 64).visibleWhen(switchBlock::get));
    private final BooleanSetting switchPickaxe = add(new BooleanSetting("Switch Pickaxe", true));
    private final IntSetting pickaxeSlot = add(new IntSetting("Pickaxe Slot", 3, 1, 9, 1).visibleWhen(switchPickaxe::get));
    private final BooleanSetting switchAxe = add(new BooleanSetting("Switch Axe", true));
    private final IntSetting axeSlot = add(new IntSetting("Axe Slot", 4, 1, 9, 1).visibleWhen(switchAxe::get));
    private final BooleanSetting switchBow = add(new BooleanSetting("Switch Bow or Crossbow", true));
    private final IntSetting bowSlot = add(new IntSetting("Bow Slot", 5, 1, 9, 1).visibleWhen(switchBow::get));
    private final EnumSetting<BowPriorityMode> preferBow = add(new EnumSetting<>("Bow Priority", BowPriorityMode.Crossbow).visibleWhen(switchBow::get));
    public final IntSetting maxArrowSize = add(new IntSetting("Max Arrow Size", 256, 64, 512, 64).visibleWhen(switchBow::get));
    private final BooleanSetting switchWaterBucket = add(new BooleanSetting("Switch Water Bucket", true));
    private final IntSetting waterBucketSlot = add(new IntSetting("Water Bucket Slot", 6, 1, 9, 1).visibleWhen(switchWaterBucket::get));
    private final BooleanSetting switchEnderPearl = add(new BooleanSetting("Switch Ender Pearl", true));
    private final IntSetting enderPearlSlot = add(new IntSetting("Ender Pearl Slot", 7, 1, 9, 1).visibleWhen(switchEnderPearl::get));
    private final BooleanSetting switchFireball = add(new BooleanSetting("Switch Fireball", true));
    private final IntSetting fireballSlot = add(new IntSetting("Fireball Slot", 8, 1, 9, 1).visibleWhen(switchFireball::get));
    private final BooleanSetting switchGoldenApple = add(new BooleanSetting("Switch Golden Apple", true).visibleWhen( () -> !offhandItems.is(OffhandItemMode.GoldenApple)));
    private final IntSetting goldenAppleSlot = add(new IntSetting("Golden Apple Slot", 9, 1, 9, 1).visibleWhen( () -> switchGoldenApple.get() && !offhandItems.is(OffhandItemMode.GoldenApple)));
    private final BooleanSetting throwItems = add(new BooleanSetting("Throw Items", true));
    public final IntSetting waterBucketCount = add(new IntSetting("Keep Water Buckets", 1, 0, 5, 1).visibleWhen(throwItems::get));
    public final IntSetting lavaBucketCount = add(new IntSetting("Keep Lava Buckets", 1, 0, 5, 1).visibleWhen(throwItems::get));
    public final BooleanSetting keepProjectile = add(new BooleanSetting("Keep Eggs & Snowballs", true));
    private final BooleanSetting switchProjectile = add(new BooleanSetting("Switch Eggs & Snowballs", false).visibleWhen( () -> keepProjectile.get() && !offhandItems.is(OffhandItemMode.Projectile)));
    private final IntSetting projectileSlot = add(new IntSetting("Eggs & Snowballs Slot", 9, 1, 9, 1).visibleWhen( () -> switchProjectile.get() && keepProjectile.get() && !offhandItems.is(OffhandItemMode.Projectile)));
    public final IntSetting maxProjectileSize = add(new IntSetting("Max Eggs & Snowballs Size", 64, 16, 256, 16).visibleWhen(keepProjectile::get));
    private final BooleanSetting switchRod = add(new BooleanSetting("Switch Rod", false).visibleWhen( () -> !offhandItems.is(OffhandItemMode.FishingRod)));
    private final IntSetting rodSlot = add(new IntSetting("Rod Slot", 9, 1, 9, 1).visibleWhen( () -> switchRod.get() && !offhandItems.is(OffhandItemMode.FishingRod)));

    private static final TimerUtils timer = new TimerUtils();
    private static final Random random = new Random();

    private int noMoveTicks = 0;
    private boolean clickOffHand = false;
    private boolean inventoryOpen = false;

    private InvManager() {
        super("Inv Manager", Category.PLAYER);
    }

    public boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isProtectedItem(stack)) return true;
        if (InvHelper.isGodItem(stack)) return true;
        if (stack.getDisplayName().getString().contains("点击使用")) return true;
        if (InvHelper.isArmor(stack)) {
            return isUsefulArmor(stack);
        }
        Boolean primaryTool = primaryToolUseful(stack);
        if (primaryTool != null) return primaryTool;
        if (isBowOrDuplicate(stack)) return false;
        if (exceedsKeepLimit(stack)) return false;
        Item item = stack.getItem();
        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) return true;
        return !(item instanceof StandingAndWallBlockItem) && InvHelper.isCommonItemUseful(stack);
    }

    private boolean isUsefulArmor(ItemStack stack) {
        EquipmentSlot slot = InvHelper.getArmorSlot(stack);
        float protection = InvHelper.getProtection(stack);
        if (InvHelper.getCurrentArmorScore(slot) >= protection) return false;
        return !(protection < InvHelper.getBestArmorScore(slot));
    }

    private Boolean primaryToolUseful(ItemStack stack) {
        Item item = stack.getItem();
        if (InvHelper.isSword(stack)) return InvHelper.getBestSword() == stack;
        if (InvHelper.isPickaxe(stack)) return InvHelper.getBestPickaxe() == stack;
        if (item instanceof AxeItem && !InvHelper.isSharpnessAxe(stack)) return InvHelper.getBestAxe() == stack;
        if (item instanceof ShovelItem) return InvHelper.getBestShovel() == stack;
        if (item instanceof CrossbowItem) return InvHelper.getBestCrossbow() == stack;
        if (item instanceof BowItem && InvHelper.isPunchBow(stack)) return InvHelper.getBestPunchBow() == stack;
        if (item instanceof BowItem && InvHelper.isPowerBow(stack)) return InvHelper.getBestPowerBow() == stack;
        return null;
    }

    private boolean isBowOrDuplicate(ItemStack stack) {
        return stack.getItem() instanceof BowItem && InvHelper.getItemCount(Items.BOW) > 1;
    }

    private boolean exceedsKeepLimit(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.WATER_BUCKET) return InvHelper.getItemCount(item) > waterBucketCount.get();
        if (item == Items.LAVA_BUCKET) return InvHelper.getItemCount(item) > lavaBucketCount.get();
        if (item instanceof FishingRodItem) return InvHelper.getItemCount(Items.FISHING_ROD) > 1;
        return (item == Items.SNOWBALL || item == Items.EGG) && !keepProjectile.get();
    }

    private static boolean isProtectedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == Blocks.ENCHANTING_TABLE) {
            return true;
        }

        if (isLegendWatchItemName(stack.getHoverName().getString())) {
            return true;
        }

        Integer nameColor = TeamColorUtils.getTextColor(stack.getHoverName());
        return nameColor != null && (nameColor & 0xFFFFFF) == (ChatFormatting.GOLD.getColor() & 0xFFFFFF);
    }

    static boolean isLegendWatchItemName(String itemName) {
        return LegendaryIcons.isLegendaryName(itemName);
    }

    @Listen
    private void onPacketSend(PacketEvent.Send event) {
        if (noPlayer() || mc.getConnection() == null) return;
        if (event.getPacket() instanceof ServerboundContainerClosePacket) this.inventoryOpen = false;
        if (this.inventoryOpen && !this.inventoryOnly.get()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
                if (MoveUtils.isMoving()) {
                    mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                }
            } else if (event.getPacket() instanceof ServerboundUseItemOnPacket || event.getPacket() instanceof ServerboundUseItemPacket || event.getPacket() instanceof ServerboundInteractPacket || event.getPacket() instanceof ServerboundPlayerActionPacket) {
                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
            }
        }
    }

    private boolean checkConfig() {
        if (!this.keepProjectile.get()) this.switchProjectile.set(false);

        List<SlotAssignment> assignments = new ArrayList<>(List.of(
                new SlotAssignment(switchSword, swordSlot),
                new SlotAssignment(switchPickaxe, pickaxeSlot),
                new SlotAssignment(switchAxe, axeSlot),
                new SlotAssignment(switchBow, bowSlot),
                new SlotAssignment(switchWaterBucket, waterBucketSlot),
                new SlotAssignment(switchEnderPearl, enderPearlSlot),
                new SlotAssignment(switchFireball, fireballSlot)
        ));
        if (!this.offhandItems.is(OffhandItemMode.GoldenApple))
            assignments.add(new SlotAssignment(this.switchGoldenApple, this.goldenAppleSlot));
        if (!this.offhandItems.is(OffhandItemMode.Projectile))
            assignments.add(new SlotAssignment(this.switchProjectile, this.projectileSlot));
        if (!this.offhandItems.is(OffhandItemMode.FishingRod))
            assignments.add(new SlotAssignment(this.switchRod, this.rodSlot));
        if (!this.offhandItems.is(OffhandItemMode.Block))
            assignments.add(new SlotAssignment(this.switchBlock, this.blockSlot));

        Set<Integer> occupied = new HashSet<>();
        for (SlotAssignment assignment : assignments) {
            if (assignment.enabled().get() && !occupied.add(assignment.slot().get() - 1)) return false;
        }
        return true;
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer() || mc.gameMode == null) return;
        if (InvHelper.shouldDisableFeatures()) return;
        if (MoveUtils.isMoving()) this.noMoveTicks = 0;
        else this.noMoveTicks++;
        boolean allowMove = !this.inventoryOnly.get();
        if (ChestStealer.INSTANCE.isWorking() || (this.inventoryOnly.get() ? !(mc.screen instanceof InventoryScreen) : (!allowMove && this.noMoveTicks <= 1))) {
            this.clickOffHand = false;
            return;
        }
        if (mc.screen instanceof AbstractContainerScreen container && container.getMenu().containerId != mc.player.inventoryMenu.containerId)
            return;
        int nextDelay = Math.max(minDelay.get(), (int) (this.delay.get() + random.nextGaussian() * 50));

        if (this.autoArmor.get()) manageArmor(nextDelay);

        if (this.clickOffHand && timer.passedMillis(nextDelay)) {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, 45, 0, ContainerInput.PICKUP, mc.player);
            this.inventoryOpen = true;
            this.clickOffHand = false;
            timer.reset();
        }

        manageOffhand(nextDelay);

        manageGoldenAppleSlot();
        manageBlockSlot();
        manageWeaponSlot();
        manageToolSlots();
        manageRodSlot();

        manageBowSlot();
        manageSimpleSlots();
        manageProjectiles();
        discardUnuseful(nextDelay);

    }

    private void manageArmor(int nextDelay) {
        EquipmentSlot[] slots = {
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD
        };
        for (int index = 0; index < slots.length; index++) {
            EquipmentSlot slot = slots[index];
            ItemStack equipped = InvHelper.getArmorStack(slot);
            if (InvHelper.isArmor(equipped)
                    && !equipped.isEmpty()
                    && timer.passedMillis(nextDelay)
                    && InvHelper.getBestArmorScore(slot) > InvHelper.getProtection(equipped)) {
                sendContainerInput(4 + (4 - index), 1, ContainerInput.THROW);
                inventoryOpen = true;
                timer.reset();
            }
        }

        for (int inventorySlot = 0;
             inventorySlot < mc.player.getInventory().getNonEquipmentItems().size();
             inventorySlot++) {
            ItemStack candidate = InvHelper.getInventoryStack(inventorySlot);
            if (candidate.isEmpty() || !InvHelper.isArmor(candidate)) continue;

            float candidateScore = InvHelper.getProtection(candidate);
            EquipmentSlot armorSlot = InvHelper.getArmorSlot(candidate);
            boolean best = InvHelper.getBestArmorScore(armorSlot) == candidateScore;
            boolean upgrade = InvHelper.getCurrentArmorScore(armorSlot) < candidateScore;
            if (best && upgrade && timer.passedMillis(nextDelay)) {
                sendContainerInput(menuSlot(inventorySlot), 0, ContainerInput.QUICK_MOVE);
                inventoryOpen = true;
                timer.reset();
            }
        }
    }

    private void manageOffhand(int nextDelay) {
        switch (offhandItems.get()) {
            case GoldenApple -> manageGoldenAppleOffhand(nextDelay);
            case Projectile -> manageProjectileOffhand(nextDelay);
            case FishingRod -> manageRodOffhand(nextDelay);
            case Block -> manageBlockOffhand(nextDelay);
            case None -> {
            }
        }
    }

    private void manageGoldenAppleOffhand(int nextDelay) {
        ItemStack offhand = InvHelper.getOffhandStack();
        Item held = offhand.getItem();
        int enchantedSlot = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
        int regularSlot = InvHelper.getItemSlot(Items.GOLDEN_APPLE);

        if (held == Items.ENCHANTED_GOLDEN_APPLE) {
            if (offhand.getCount() < offhand.getMaxStackSize()
                    && enchantedSlot != -1
                    && timer.passedMillis(nextDelay)) {
                startOffhandPickup(enchantedSlot);
            }
            return;
        }

        if (held == Items.GOLDEN_APPLE) {
            if (enchantedSlot != -1) {
                if (timer.passedMillis(nextDelay)) {
                    swapOffHand(enchantedSlot);
                    timer.reset();
                }
            } else if (offhand.getCount() < offhand.getMaxStackSize()
                    && regularSlot != -1
                    && timer.passedMillis(nextDelay)) {
                startOffhandPickup(regularSlot);
            }
            return;
        }

        int replacement = enchantedSlot != -1 ? enchantedSlot : regularSlot;
        if (replacement != -1 && timer.passedMillis(nextDelay)) {
            swapOffHand(replacement);
            timer.reset();
        }
    }

    private void startOffhandPickup(int inventorySlot) {
        sendContainerInput(menuSlot(inventorySlot), 0, ContainerInput.PICKUP);
        inventoryOpen = true;
        clickOffHand = true;
        timer.reset();
    }

    private void manageProjectileOffhand(int nextDelay) {
        ItemStack offhand = InvHelper.getOffhandStack();
        ItemStack best = InvHelper.getBestProjectile();
        if (best == null) return;

        int slot = InvHelper.getItemStackSlot(best);
        boolean replace = offhand.getItem() != Items.EGG && offhand.getItem() != Items.SNOWBALL
                || offhand.getCount() < best.getCount();
        if (replace && slot != -1 && timer.passedMillis(nextDelay)) swapOffHand(slot);
    }

    private void manageRodOffhand(int nextDelay) {
        ItemStack offhand = InvHelper.getOffhandStack();
        int slot = InvHelper.getItemSlot(Items.FISHING_ROD);
        if (slot != -1 && timer.passedMillis(nextDelay) && offhand.getItem() != Items.FISHING_ROD) {
            swapOffHand(slot);
        }
    }

    private void manageBlockOffhand(int nextDelay) {
        ItemStack offhand = InvHelper.getOffhandStack();
        ItemStack best = InvHelper.getBestBlock();
        if (best == null) return;

        int slot = InvHelper.getItemStackSlot(best);
        boolean replace = !InvHelper.isValidStack(offhand) || offhand.getCount() < best.getCount();
        if (replace && slot != -1 && timer.passedMillis(nextDelay)) swapOffHand(slot);
    }

    private void manageGoldenAppleSlot() {
        if (!switchGoldenApple.get() || offhandItems.is(OffhandItemMode.GoldenApple)) return;

        int target = goldenAppleSlot.get() - 1;
        int enchanted = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
        int regular = InvHelper.getItemSlot(Items.GOLDEN_APPLE);
        int source = enchanted != -1 ? enchanted : regular;
        if (source == -1) return;

        ItemStack current = InvHelper.getInventoryStack(target);
        ItemStack best = InvHelper.getInventoryStack(source);
        if (current.getItem() != best.getItem()
                || current.getCount() < best.getCount() && current.getItem() == best.getItem()) {
            swapItem(target, best);
        }
    }

    private void manageBlockSlot() {
        if (!switchBlock.get()) return;

        int target = blockSlot.get() - 1;
        ItemStack current = InvHelper.getInventoryStack(target);
        ItemStack best = InvHelper.getBestBlock();
        if (best != null
                && (best.getCount() > current.getCount() || !InvHelper.isValidStack(current))
                && !offhandItems.is(OffhandItemMode.Block)) {
            swapItem(target, best);
        }
        if ((float) InvHelper.getBlockCountInInventory() > maxBlockSize.get()) {
            throwItem(InvHelper.getWorstBlock());
        }
    }

    private void manageWeaponSlot() {
        if (!switchSword.get()) return;

        int target = swordSlot.get() - 1;
        ItemStack current = InvHelper.getInventoryStack(target);
        ItemStack best = InvHelper.getBestSword();
        ItemStack sharpAxe = InvHelper.getBestShapeAxe();
        if (InvHelper.getAxeDamage(sharpAxe) > InvHelper.getSwordDamage(best)) best = sharpAxe;
        if (best == null) return;

        float currentDamage = InvHelper.isSword(current)
                ? InvHelper.getSwordDamage(current)
                : InvHelper.getAxeDamage(current);
        float bestDamage = InvHelper.isSword(best)
                ? InvHelper.getSwordDamage(best)
                : InvHelper.getAxeDamage(best);
        if (bestDamage > currentDamage) swapItem(target, best);
    }

    private void manageToolSlots() {
        if (switchPickaxe.get()) {
            int target = pickaxeSlot.get() - 1;
            ItemStack best = InvHelper.getBestPickaxe();
            ItemStack current = InvHelper.getInventoryStack(target);
            if (InvHelper.isPickaxe(best)
                    && (InvHelper.getToolScore(best) > InvHelper.getToolScore(current)
                    || !InvHelper.isPickaxe(current))) {
                swapItem(target, best);
            }
        }

        if (switchAxe.get()) {
            int target = axeSlot.get() - 1;
            ItemStack best = InvHelper.getBestAxe();
            ItemStack current = InvHelper.getInventoryStack(target);
            if (best != null && best.getItem() instanceof AxeItem
                    && (InvHelper.getToolScore(best) > InvHelper.getToolScore(current)
                    || !(current.getItem() instanceof AxeItem))) {
                swapItem(target, best);
            }
        }
    }

    private void manageRodSlot() {
        if (!switchRod.get() || offhandItems.is(OffhandItemMode.FishingRod)) return;

        int target = rodSlot.get() - 1;
        ItemStack best = InvHelper.getFishingRod();
        ItemStack current = InvHelper.getInventoryStack(target);
        if (!(current.getItem() instanceof FishingRodItem)) swapItem(target, best);
    }

    private void manageBowSlot() {
        if (!switchBow.get()) return;

        int target = bowSlot.get() - 1;
        ItemStack current = InvHelper.getInventoryStack(target);
        BowChoice choice = bowChoice(preferBow.get(), current);
        if (choice.best() == null) choice = bowChoice(BowPriorityMode.Crossbow, current);
        if (choice.best() == null) choice = bowChoice(BowPriorityMode.PowerBow, current);
        if (choice.best() == null) choice = bowChoice(BowPriorityMode.PunchBow, current);

        if (choice.best() != null && choice.bestScore() > choice.currentScore()) {
            swapItem(target, choice.best());
        }
        if ((float) InvHelper.getItemCount(Items.ARROW) > maxArrowSize.get()) {
            throwItem(InvHelper.getWorstArrow());
        }
    }

    private BowChoice bowChoice(BowPriorityMode mode, ItemStack current) {
        return switch (mode) {
            case Crossbow -> {
                ItemStack best = InvHelper.getBestCrossbow();
                yield new BowChoice(best, InvHelper.getCrossbowScore(best), InvHelper.getCrossbowScore(current));
            }
            case PowerBow -> {
                ItemStack best = InvHelper.getBestPowerBow();
                yield new BowChoice(best, InvHelper.getPowerBowScore(best), InvHelper.getPowerBowScore(current));
            }
            case PunchBow -> {
                ItemStack best = InvHelper.getBestPunchBow();
                yield new BowChoice(best, InvHelper.getPunchBowScore(best), InvHelper.getPunchBowScore(current));
            }
        };
    }

    private void manageSimpleSlots() {
        if (switchEnderPearl.get()) swapItem(enderPearlSlot.get() - 1, Items.ENDER_PEARL);
        if (switchWaterBucket.get()) swapItem(waterBucketSlot.get() - 1, Items.WATER_BUCKET);
        if (switchFireball.get()) swapItem(fireballSlot.get() - 1, Items.FIRE_CHARGE);
    }

    private void manageProjectiles() {
        if (!keepProjectile.get()) return;

        if ((float) (InvHelper.getItemCount(Items.EGG) + InvHelper.getItemCount(Items.SNOWBALL))
                > maxProjectileSize.get()) {
            throwItem(InvHelper.getWorstProjectile());
        }
        if (switchProjectile.get() && !offhandItems.is(OffhandItemMode.Projectile)) {
            int target = projectileSlot.get() - 1;
            if (InvHelper.getItemCount(Items.EGG) > 0) swapItem(target, Items.EGG);
            else if (InvHelper.getItemCount(Items.SNOWBALL) > 0) swapItem(target, Items.SNOWBALL);
        }
    }

    private void discardUnuseful(int nextDelay) {
        if (!throwItems.get()) return;

        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < mc.player.getInventory().getNonEquipmentItems().size(); slot++) {
            slots.add(slot);
        }
        Collections.shuffle(slots);
        for (int slot : slots) {
            ItemStack stack = InvHelper.getInventoryStack(slot);
            if (!stack.isEmpty() && !isItemUseful(stack) && timer.passedMillis(nextDelay)) {
                throwItem(stack);
                timer.reset();
                return;
            }
        }
    }

    private record BowChoice(ItemStack best, float bestScore, float currentScore) {
    }

    private record SlotAssignment(BooleanSetting enabled, IntSetting slot) {
    }

    private void swapOffHand(int slot) {
        sendContainerInput(menuSlot(slot), 40, ContainerInput.SWAP);
        this.inventoryOpen = true;
        timer.reset();
    }

    private void throwItem(ItemStack item) {
        int nextDelay = Math.max(minDelay.get(), (int) (this.delay.get() + random.nextGaussian() * 50));
        if (item != null && !isProtectedItem(item) && InvHelper.isItemValid(item) && timer.passedMillis(nextDelay)) {
            int itemSlot = InvHelper.getItemStackSlot(item);
            if (itemSlot != -1) {
                sendContainerInput(menuSlot(itemSlot), 1, ContainerInput.THROW);
                this.inventoryOpen = true;
                timer.reset();
            }
        }
    }

    private void swapItem(int targetSlot, ItemStack bestItem) {
        ItemStack currentSlot = InvHelper.getInventoryStack(targetSlot);
        int nextDelay = Math.max(minDelay.get(), (int) (this.delay.get() + random.nextGaussian() * 50));
        if (InvHelper.isItemValid(currentSlot) && bestItem != currentSlot && timer.passedMillis(nextDelay)) {
            int bestItemSlot = InvHelper.getItemStackSlot(bestItem);
            if (bestItemSlot != -1) {
                sendContainerInput(menuSlot(bestItemSlot), targetSlot, ContainerInput.SWAP);
                this.inventoryOpen = true;
                timer.reset();
            }
        }
    }

    private void swapItem(int targetSlot, Item item) {
        ItemStack currentSlot = InvHelper.getInventoryStack(targetSlot);
        int nextDelay = Math.max(minDelay.get(), (int) (this.delay.get() + random.nextGaussian() * 50));
        if (InvHelper.isItemValid(currentSlot) && timer.passedMillis(nextDelay)) {
            int bestItemSlot = InvHelper.getItemSlot(item);
            if (bestItemSlot != -1) {
                ItemStack bestItemStack = InvHelper.getInventoryStack(bestItemSlot);
                if (currentSlot.getItem() != item || currentSlot.getItem() == item && currentSlot.getCount() < bestItemStack.getCount()) {
                    sendContainerInput(menuSlot(bestItemSlot), targetSlot, ContainerInput.SWAP);
                    this.inventoryOpen = true;
                    timer.reset();
                }
            }
        }
    }

    private static int menuSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private void sendContainerInput(int slot, int button, ContainerInput input) {
        mc.gameMode.handleContainerInput(
                mc.player.inventoryMenu.containerId,
                slot,
                button,
                input,
                mc.player
        );
        InventorySlotHighlight.mark(mc.player.inventoryMenu, slot);
        if (input == ContainerInput.SWAP && button >= 0 && button < 9) {
            InventorySlotHighlight.mark(mc.player.inventoryMenu, menuSlot(button));
        } else if (input == ContainerInput.SWAP && button == 40) {
            InventorySlotHighlight.mark(mc.player.inventoryMenu, 45);
        }
    }

}
