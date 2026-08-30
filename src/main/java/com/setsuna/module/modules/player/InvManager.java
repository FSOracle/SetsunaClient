package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.player.InvHelper;
import com.setsuna.util.player.MoveUtils;
import com.setsuna.util.timer.TimerUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        return isItemUseful(stack, InventorySnapshot.capture());
    }

    private boolean isItemUseful(ItemStack stack, InventorySnapshot inventory) {
        if (stack.isEmpty()) return false;
        if (InvHelper.isGodItem(stack)) return true;
        if (stack.getDisplayName().getString().contains("点击使用")) return true;
        if (InvHelper.isArmor(stack)) {
            float protection = InvHelper.getProtection(stack);
            EquipmentSlot armorSlot = InvHelper.getArmorSlot(stack);
            if (inventory.currentArmorScore(armorSlot) >= protection) return false;
            float bestArmor = inventory.bestArmorScore(armorSlot);
            return !(protection < bestArmor);
        }
        if (InvHelper.isSword(stack)) return inventory.bestSword() == stack;
        if (InvHelper.isPickaxe(stack)) return inventory.bestPickaxe() == stack;
        if (stack.getItem() instanceof AxeItem && !InvHelper.isSharpnessAxe(stack))
            return inventory.bestAxe() == stack;
        if (stack.getItem() instanceof ShovelItem) return inventory.bestShovel() == stack;
        if (stack.getItem() instanceof CrossbowItem) return inventory.bestCrossbow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.isPunchBow(stack))
            return inventory.bestPunchBow() == stack;
        if (stack.getItem() instanceof BowItem && InvHelper.isPowerBow(stack))
            return inventory.bestPowerBow() == stack;
        if (stack.getItem() instanceof BowItem && inventory.count(Items.BOW) > 1) return false;
        if (stack.getItem() == Items.WATER_BUCKET && inventory.count(Items.WATER_BUCKET) > waterBucketCount.get())
            return false;
        if (stack.getItem() == Items.LAVA_BUCKET && inventory.count(Items.LAVA_BUCKET) > lavaBucketCount.get())
            return false;
        if (stack.getItem() instanceof FishingRodItem && inventory.count(Items.FISHING_ROD) > 1)
            return false;
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) && !keepProjectile.get())
            return false;
        if (stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return true;
        return !(stack.getItem() instanceof StandingAndWallBlockItem) && InvHelper.isCommonItemUseful(stack);
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
        List<Pair<BooleanSetting, IntSetting>> pairs = new ArrayList<>();
        if (!this.keepProjectile.get()) this.switchProjectile.set(false);
        pairs.add(Pair.of(this.switchSword, this.swordSlot));
        pairs.add(Pair.of(this.switchPickaxe, this.pickaxeSlot));
        pairs.add(Pair.of(this.switchAxe, this.axeSlot));
        pairs.add(Pair.of(this.switchBow, this.bowSlot));
        pairs.add(Pair.of(this.switchWaterBucket, this.waterBucketSlot));
        pairs.add(Pair.of(this.switchEnderPearl, this.enderPearlSlot));
        pairs.add(Pair.of(this.switchFireball, this.fireballSlot));
        if (!this.offhandItems.is(OffhandItemMode.GoldenApple))
            pairs.add(Pair.of(this.switchGoldenApple, this.goldenAppleSlot));
        if (!this.offhandItems.is(OffhandItemMode.Projectile))
            pairs.add(Pair.of(this.switchProjectile, this.projectileSlot));
        if (!this.offhandItems.is(OffhandItemMode.FishingRod)) pairs.add(Pair.of(this.switchRod, this.rodSlot));
        if (!this.offhandItems.is(OffhandItemMode.Block)) pairs.add(Pair.of(this.switchBlock, this.blockSlot));
        Set<Integer> usedSlot = new HashSet<>();
        for (Pair<BooleanSetting, IntSetting> pair : pairs) {
            if (pair.getKey().get()) {
                int targetSlot = pair.getValue().get() - 1;
                if (usedSlot.contains(targetSlot)) return false;
                usedSlot.add(targetSlot);
            }
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

        if (this.autoArmor.get()) {
            EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            for (int i = 0; i < armorSlots.length; i++) {
                ItemStack stack = InvHelper.getArmorStack(armorSlots[i]);
                if (InvHelper.isArmor(stack)) {
                    if (!stack.isEmpty() && timer.passedMillis(nextDelay) && InvHelper.getBestArmorScore(armorSlots[i]) > InvHelper.getProtection(stack)) {
                        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, 4 + (4 - i), 1, ContainerInput.THROW, mc.player);
                        this.inventoryOpen = true;
                        timer.reset();
                    }
                }
            }
            for (int ix = 0; ix < mc.player.getInventory().getNonEquipmentItems().size(); ix++) {
                ItemStack stack = InvHelper.getInventoryStack(ix);
                if (!stack.isEmpty() && InvHelper.isArmor(stack)) {
                    float currentItemScore = InvHelper.getProtection(stack);
                    boolean isBestItem = InvHelper.getBestArmorScore(InvHelper.getArmorSlot(stack)) == currentItemScore;
                    boolean isBetterItem = InvHelper.getCurrentArmorScore(InvHelper.getArmorSlot(stack)) < currentItemScore;
                    if (isBestItem && isBetterItem && timer.passedMillis(nextDelay)) {
                        if (ix < 9)
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, ix + 36, 0, ContainerInput.QUICK_MOVE, mc.player);
                        else
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, ix, 0, ContainerInput.QUICK_MOVE, mc.player);
                        this.inventoryOpen = true;
                        timer.reset();
                    }
                }
            }
        }

        if (this.clickOffHand && timer.passedMillis(nextDelay)) {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, 45, 0, ContainerInput.PICKUP, mc.player);
            this.inventoryOpen = true;
            this.clickOffHand = false;
            timer.reset();
        }

        if (this.offhandItems.is(OffhandItemMode.GoldenApple)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            Item offHandItem = offHand.getItem();

            int egapSlot = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gapSlot = InvHelper.getItemSlot(Items.GOLDEN_APPLE);

            if (offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                if (offHand.getCount() < offHand.getMaxStackSize() && egapSlot != -1) {
                    if (timer.passedMillis(nextDelay)) {
                        int targetSlot = egapSlot;
                        if (targetSlot < 9)
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, targetSlot + 36, 0, ContainerInput.PICKUP, mc.player);
                        else
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, targetSlot, 0, ContainerInput.PICKUP, mc.player);
                        this.inventoryOpen = true;
                        this.clickOffHand = true;
                        timer.reset();
                    }
                }
            } else if (offHandItem == Items.GOLDEN_APPLE) {
                if (egapSlot != -1) {
                    if (timer.passedMillis(nextDelay)) {
                        this.swapOffHand(egapSlot);
                        timer.reset();
                    }
                } else if (offHand.getCount() < offHand.getMaxStackSize() && gapSlot != -1) {
                    if (timer.passedMillis(nextDelay)) {
                        int targetSlot = gapSlot;
                        if (targetSlot < 9)
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, targetSlot + 36, 0, ContainerInput.PICKUP, mc.player);
                        else
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, targetSlot, 0, ContainerInput.PICKUP, mc.player);
                        this.inventoryOpen = true;
                        this.clickOffHand = true;
                        timer.reset();
                    }
                }
            } else {
                if (egapSlot != -1) {
                    if (timer.passedMillis(nextDelay)) {
                        this.swapOffHand(egapSlot);
                        timer.reset();
                    }
                } else if (gapSlot != -1) {
                    if (timer.passedMillis(nextDelay)) {
                        this.swapOffHand(gapSlot);
                        timer.reset();
                    }
                }
            }
        } else if (this.offhandItems.is(OffhandItemMode.Projectile)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            ItemStack bestProjectile = InvHelper.getBestProjectile();
            if (bestProjectile != null) {
                int slot = InvHelper.getItemStackSlot(bestProjectile);
                boolean shouldSwap = offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL || offHand.getCount() < bestProjectile.getCount();
                if (shouldSwap && slot != -1 && timer.passedMillis(nextDelay)) this.swapOffHand(slot);
            }
        } else if (this.offhandItems.is(OffhandItemMode.FishingRod)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            int slotx = InvHelper.getItemSlot(Items.FISHING_ROD);
            if (slotx != -1 && timer.passedMillis(nextDelay) && offHand.getItem() != Items.FISHING_ROD)
                this.swapOffHand(slotx);
        } else if (this.offhandItems.is(OffhandItemMode.Block)) {
            ItemStack offHand = InvHelper.getOffhandStack();
            ItemStack bestBlock = InvHelper.getBestBlock();
            if (bestBlock != null) {
                int slotx = InvHelper.getItemStackSlot(bestBlock);
                boolean shouldSwapx = !InvHelper.isValidStack(offHand) || offHand.getCount() < bestBlock.getCount();
                if (shouldSwapx && slotx != -1 && timer.passedMillis(nextDelay)) this.swapOffHand(slotx);
            }
        }

        if (this.switchGoldenApple.get() && !this.offhandItems.is(OffhandItemMode.GoldenApple)) {
            int targetSlotIdx = this.goldenAppleSlot.get() - 1;
            int egapSlot = InvHelper.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gapSlot = InvHelper.getItemSlot(Items.GOLDEN_APPLE);
            int bestGapSlot = (egapSlot != -1) ? egapSlot : gapSlot;
            if (bestGapSlot != -1) {
                ItemStack currentInSlot = InvHelper.getInventoryStack(targetSlotIdx);
                ItemStack bestGapItem = InvHelper.getInventoryStack(bestGapSlot);
                if (currentInSlot.getItem() != bestGapItem.getItem() || (currentInSlot.getCount() < bestGapItem.getCount() && currentInSlot.getItem() == bestGapItem.getItem())) {
                    this.swapItem(targetSlotIdx, bestGapItem);
                }
            }
        }

        if (this.switchBlock.get()) {
            int blockSlotIndex = this.blockSlot.get() - 1;
            ItemStack currentBlock = InvHelper.getInventoryStack(blockSlotIndex);
            ItemStack bestBlock = InvHelper.getBestBlock();
            if (bestBlock != null && (bestBlock.getCount() > currentBlock.getCount() || !InvHelper.isValidStack(currentBlock)) && !this.offhandItems.is(OffhandItemMode.Block)) {
                this.swapItem(blockSlotIndex, bestBlock);
            }
            if ((float) InvHelper.getBlockCountInInventory() > this.maxBlockSize.get()) {
                this.throwItem(InvHelper.getWorstBlock());
            }
        }

        if (this.switchSword.get()) {
            int slotIndex = this.swordSlot.get() - 1;
            ItemStack currentSword = InvHelper.getInventoryStack(slotIndex);
            ItemStack bestSword = InvHelper.getBestSword();
            ItemStack bestShapeAxe = InvHelper.getBestShapeAxe();
            if (InvHelper.getAxeDamage(bestShapeAxe) > InvHelper.getSwordDamage(bestSword))
                bestSword = bestShapeAxe;
            if (bestSword != null) {
                float currentDamage = InvHelper.isSword(currentSword) ? InvHelper.getSwordDamage(currentSword) : InvHelper.getAxeDamage(currentSword);
                float bestWeaponDamage = InvHelper.isSword(bestSword) ? InvHelper.getSwordDamage(bestSword) : InvHelper.getAxeDamage(bestSword);
                if (bestWeaponDamage > currentDamage) this.swapItem(slotIndex, bestSword);
            }
        }

        if (this.switchPickaxe.get()) {
            int slotIndex = this.pickaxeSlot.get() - 1;
            ItemStack bestPickaxe = InvHelper.getBestPickaxe();
            ItemStack currentPickaxe = InvHelper.getInventoryStack(slotIndex);
            if (InvHelper.isPickaxe(bestPickaxe) && (InvHelper.getToolScore(bestPickaxe) > InvHelper.getToolScore(currentPickaxe) || !InvHelper.isPickaxe(currentPickaxe)))
                this.swapItem(slotIndex, bestPickaxe);
        }

        if (this.switchAxe.get()) {
            int slotIndex = this.axeSlot.get() - 1;
            ItemStack bestAxe = InvHelper.getBestAxe();
            ItemStack currentAxe = InvHelper.getInventoryStack(slotIndex);
            if (bestAxe != null && bestAxe.getItem() instanceof AxeItem && (InvHelper.getToolScore(bestAxe) > InvHelper.getToolScore(currentAxe) || !(currentAxe.getItem() instanceof AxeItem)))
                this.swapItem(slotIndex, bestAxe);
        }

        if (this.switchRod.get() && !this.offhandItems.is(OffhandItemMode.FishingRod)) {
            int slotIndex = this.rodSlot.get() - 1;
            ItemStack bestRod = InvHelper.getFishingRod();
            ItemStack currentRod = InvHelper.getInventoryStack(slotIndex);
            if (!(currentRod.getItem() instanceof FishingRodItem)) this.swapItem(slotIndex, bestRod);
        }

        if (this.switchBow.get()) {
            int slotIndex = this.bowSlot.get() - 1;
            ItemStack currentBow = InvHelper.getInventoryStack(slotIndex);
            ItemStack bestBow;
            float bestScore, currentScore;
            if (this.preferBow.is(BowPriorityMode.Crossbow)) {
                bestBow = InvHelper.getBestCrossbow();
                bestScore = InvHelper.getCrossbowScore(bestBow);
                currentScore = InvHelper.getCrossbowScore(currentBow);
            } else if (this.preferBow.is(BowPriorityMode.PowerBow)) {
                bestBow = InvHelper.getBestPowerBow();
                bestScore = InvHelper.getPowerBowScore(bestBow);
                currentScore = InvHelper.getPowerBowScore(currentBow);
            } else {
                bestBow = InvHelper.getBestPunchBow();
                bestScore = InvHelper.getPunchBowScore(bestBow);
                currentScore = InvHelper.getPunchBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InvHelper.getBestCrossbow();
                bestScore = InvHelper.getCrossbowScore(bestBow);
                currentScore = InvHelper.getCrossbowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InvHelper.getBestPowerBow();
                bestScore = InvHelper.getPowerBowScore(bestBow);
                currentScore = InvHelper.getPowerBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InvHelper.getBestPunchBow();
                bestScore = InvHelper.getPunchBowScore(bestBow);
                currentScore = InvHelper.getPunchBowScore(currentBow);
            }
            if (bestBow != null && bestScore > currentScore) this.swapItem(slotIndex, bestBow);
            if ((float) InvHelper.getItemCount(Items.ARROW) > this.maxArrowSize.get())
                this.throwItem(InvHelper.getWorstArrow());
        }

        if (this.switchEnderPearl.get())
            this.swapItem(this.enderPearlSlot.get() - 1, Items.ENDER_PEARL);
        if (this.switchWaterBucket.get())
            this.swapItem(this.waterBucketSlot.get() - 1, Items.WATER_BUCKET);
        if (this.switchFireball.get())
            this.swapItem(this.fireballSlot.get() - 1, Items.FIRE_CHARGE);

        if (this.keepProjectile.get()) {
            if ((float) (InvHelper.getItemCount(Items.EGG) + InvHelper.getItemCount(Items.SNOWBALL)) > this.maxProjectileSize.get())
                this.throwItem(InvHelper.getWorstProjectile());
            if (this.switchProjectile.get() && !this.offhandItems.is(OffhandItemMode.Projectile)) {
                int pSlot = this.projectileSlot.get() - 1;
                if (InvHelper.getItemCount(Items.EGG) > 0) this.swapItem(pSlot, Items.EGG);
                else if (InvHelper.getItemCount(Items.SNOWBALL) > 0) this.swapItem(pSlot, Items.SNOWBALL);
            }
        }

        if (this.throwItems.get() && timer.passedMillis(nextDelay)) {
            InventorySnapshot inventory = InventorySnapshot.capture();
            List<Integer> slots = IntStream.range(0, inventory.size()).boxed().collect(Collectors.toList());
            Collections.shuffle(slots);
            for (Integer slotIdx : slots) {
                ItemStack stack = inventory.stackAt(slotIdx);
                if (!stack.isEmpty() && !this.isItemUseful(stack, inventory)) {
                    this.throwItem(slotIdx);
                    timer.reset();
                    return;
                }
            }
        }

    }

    private void swapOffHand(int slot) {
        if (slot < 9) {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot + 36, 40, ContainerInput.SWAP, mc.player);
        } else {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 40, ContainerInput.SWAP, mc.player);
        }
        this.inventoryOpen = true;
        timer.reset();
    }

    private void throwItem(ItemStack item) {
        int itemSlot = InvHelper.getItemStackSlot(item);
        if (itemSlot != -1) {
            this.throwItem(itemSlot);
        }
    }

    private void throwItem(int itemSlot) {
        int nextDelay = Math.max(minDelay.get(), (int) (this.delay.get() + random.nextGaussian() * 50));
        ItemStack item = InvHelper.getInventoryStack(itemSlot);
        if (InvHelper.isItemValid(item) && timer.passedMillis(nextDelay)) {
            if (itemSlot < 9) {
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, itemSlot + 36, 1, ContainerInput.THROW, mc.player);
            } else {
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, itemSlot, 1, ContainerInput.THROW, mc.player);
            }
            this.inventoryOpen = true;
            timer.reset();
        }
    }

    private void swapItem(int targetSlot, ItemStack bestItem) {
        ItemStack currentSlot = InvHelper.getInventoryStack(targetSlot);
        int nextDelay = Math.max(minDelay.get(), (int) (this.delay.get() + random.nextGaussian() * 50));
        if (InvHelper.isItemValid(currentSlot) && bestItem != currentSlot && timer.passedMillis(nextDelay)) {
            int bestItemSlot = InvHelper.getItemStackSlot(bestItem);
            if (bestItemSlot != -1) {
                if (bestItemSlot < 9) {
                    mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, bestItemSlot + 36, targetSlot, ContainerInput.SWAP, mc.player);
                } else {
                    mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, bestItemSlot, targetSlot, ContainerInput.SWAP, mc.player);
                }
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
                    if (bestItemSlot < 9) {
                        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, bestItemSlot + 36, targetSlot, ContainerInput.SWAP, mc.player);
                    } else {
                        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, bestItemSlot, targetSlot, ContainerInput.SWAP, mc.player);
                    }
                    this.inventoryOpen = true;
                    timer.reset();
                }
            }
        }
    }

    private static final class InventorySnapshot {
        private final List<ItemStack> items;
        private final Map<Item, Integer> counts;
        private final Map<Item, Integer> firstSlots;
        private final EnumMap<EquipmentSlot, Float> bestArmorScores;
        private final EnumMap<EquipmentSlot, Float> currentArmorScores;
        private final ItemStack bestSword;
        private final ItemStack bestPickaxe;
        private final ItemStack bestAxe;
        private final ItemStack bestShovel;
        private final ItemStack bestCrossbow;
        private final ItemStack bestPunchBow;
        private final ItemStack bestPowerBow;
        private final ItemStack bestBlock;
        private final ItemStack worstBlock;
        private final ItemStack worstProjectile;
        private final ItemStack worstArrow;
        private final int blockCount;

        private InventorySnapshot(List<ItemStack> items, Map<Item, Integer> counts, Map<Item, Integer> firstSlots,
                                  EnumMap<EquipmentSlot, Float> bestArmorScores,
                                  EnumMap<EquipmentSlot, Float> currentArmorScores,
                                  ItemStack bestSword, ItemStack bestPickaxe, ItemStack bestAxe,
                                  ItemStack bestShovel, ItemStack bestCrossbow, ItemStack bestPunchBow,
                                  ItemStack bestPowerBow, ItemStack bestBlock, ItemStack worstBlock,
                                  ItemStack worstProjectile, ItemStack worstArrow, int blockCount) {
            this.items = items;
            this.counts = counts;
            this.firstSlots = firstSlots;
            this.bestArmorScores = bestArmorScores;
            this.currentArmorScores = currentArmorScores;
            this.bestSword = bestSword;
            this.bestPickaxe = bestPickaxe;
            this.bestAxe = bestAxe;
            this.bestShovel = bestShovel;
            this.bestCrossbow = bestCrossbow;
            this.bestPunchBow = bestPunchBow;
            this.bestPowerBow = bestPowerBow;
            this.bestBlock = bestBlock;
            this.worstBlock = worstBlock;
            this.worstProjectile = worstProjectile;
            this.worstArrow = worstArrow;
            this.blockCount = blockCount;
        }

        static InventorySnapshot capture() {
            List<ItemStack> items = InvHelper.getAllItems();
            Map<Item, Integer> counts = new HashMap<>();
            Map<Item, Integer> firstSlots = new HashMap<>();
            EnumMap<EquipmentSlot, Float> bestArmorScores = new EnumMap<>(EquipmentSlot.class);
            EnumMap<EquipmentSlot, Float> currentArmorScores = new EnumMap<>(EquipmentSlot.class);

            ItemStack bestSword = null;
            float bestSwordScore = 0.0F;
            ItemStack bestPickaxe = null;
            float bestPickaxeScore = 0.0F;
            ItemStack bestAxe = null;
            float bestAxeScore = 0.0F;
            ItemStack bestShovel = null;
            float bestShovelScore = 0.0F;
            ItemStack bestCrossbow = null;
            float bestCrossbowScore = 0.0F;
            ItemStack bestPunchBow = null;
            float bestPunchBowScore = 0.0F;
            ItemStack bestPowerBow = null;
            float bestPowerBowScore = 0.0F;
            ItemStack bestBlock = null;
            int bestBlockCount = -1;
            ItemStack worstBlock = null;
            int worstBlockCount = Integer.MAX_VALUE;
            ItemStack worstProjectile = null;
            int worstProjectileCount = Integer.MAX_VALUE;
            ItemStack worstArrow = null;
            int worstArrowCount = Integer.MAX_VALUE;
            int blockCount = 0;

            for (int slot = 0; slot < items.size(); slot++) {
                ItemStack stack = items.get(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                Item item = stack.getItem();
                counts.merge(item, stack.getCount(), Integer::sum);
                firstSlots.putIfAbsent(item, slot);

                if (InvHelper.isArmor(stack)) {
                    EquipmentSlot armorSlot = InvHelper.getArmorSlot(stack);
                    if (armorSlot != null) {
                        float protection = InvHelper.getProtection(stack);
                        bestArmorScores.merge(armorSlot, protection, Math::max);
                    }
                }

                if (InvHelper.isSword(stack)) {
                    float score = InvHelper.getSwordDamage(stack);
                    if (score > bestSwordScore) {
                        bestSwordScore = score;
                        bestSword = stack;
                    }
                }

                if (InvHelper.isPickaxe(stack)) {
                    float score = InvHelper.getToolScore(stack);
                    if (score > bestPickaxeScore) {
                        bestPickaxeScore = score;
                        bestPickaxe = stack;
                    }
                }

                if (item instanceof AxeItem && !InvHelper.isSharpnessAxe(stack)) {
                    float score = InvHelper.getToolScore(stack);
                    if (score > bestAxeScore) {
                        bestAxeScore = score;
                        bestAxe = stack;
                    }
                }

                if (item instanceof ShovelItem) {
                    float score = InvHelper.getToolScore(stack);
                    if (score > bestShovelScore) {
                        bestShovelScore = score;
                        bestShovel = stack;
                    }
                }

                if (item instanceof CrossbowItem) {
                    float score = InvHelper.getCrossbowScore(stack);
                    if (score > bestCrossbowScore) {
                        bestCrossbowScore = score;
                        bestCrossbow = stack;
                    }
                }

                if (item instanceof BowItem && InvHelper.isPunchBow(stack)) {
                    float score = InvHelper.getPunchBowScore(stack);
                    if (score > bestPunchBowScore) {
                        bestPunchBowScore = score;
                        bestPunchBow = stack;
                    }
                }

                if (item instanceof BowItem && InvHelper.isPowerBow(stack)) {
                    float score = InvHelper.getPowerBowScore(stack);
                    if (score > bestPowerBowScore) {
                        bestPowerBowScore = score;
                        bestPowerBow = stack;
                    }
                }

                if (item instanceof BlockItem && InvHelper.isValidStack(stack) && InvHelper.isItemValid(stack)) {
                    int count = stack.getCount();
                    blockCount += count;
                    if (count > bestBlockCount) {
                        bestBlockCount = count;
                        bestBlock = stack;
                    }
                    if (count < worstBlockCount) {
                        worstBlockCount = count;
                        worstBlock = stack;
                    }
                }

                if (item == Items.EGG || item == Items.SNOWBALL) {
                    int count = stack.getCount();
                    if (count < worstProjectileCount) {
                        worstProjectileCount = count;
                        worstProjectile = stack;
                    }
                }

                if (item instanceof ArrowItem && InvHelper.isItemValid(stack)) {
                    int count = stack.getCount();
                    if (count < worstArrowCount) {
                        worstArrowCount = count;
                        worstArrow = stack;
                    }
                }
            }

            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
                currentArmorScores.put(slot, InvHelper.getProtection(InvHelper.getArmorStack(slot)));
                bestArmorScores.putIfAbsent(slot, 0.0F);
            }

            return new InventorySnapshot(items, counts, firstSlots, bestArmorScores, currentArmorScores,
                    bestSword, bestPickaxe, bestAxe, bestShovel, bestCrossbow, bestPunchBow, bestPowerBow,
                    bestBlock, worstBlock, worstProjectile, worstArrow, blockCount);
        }

        int size() {
            return items.size();
        }

        ItemStack stackAt(int slot) {
            if (slot < 0 || slot >= items.size()) {
                return ItemStack.EMPTY;
            }
            return items.get(slot);
        }

        int firstSlot(Item item) {
            return firstSlots.getOrDefault(item, -1);
        }

        int slotOf(ItemStack stack) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == stack) {
                    return i;
                }
            }
            return -1;
        }

        int count(Item item) {
            return counts.getOrDefault(item, 0);
        }

        float bestArmorScore(EquipmentSlot slot) {
            return bestArmorScores.getOrDefault(slot, 0.0F);
        }

        float currentArmorScore(EquipmentSlot slot) {
            return currentArmorScores.getOrDefault(slot, 0.0F);
        }

        ItemStack bestSword() {
            return bestSword;
        }

        ItemStack bestPickaxe() {
            return bestPickaxe;
        }

        ItemStack bestAxe() {
            return bestAxe;
        }

        ItemStack bestShovel() {
            return bestShovel;
        }

        ItemStack bestCrossbow() {
            return bestCrossbow;
        }

        ItemStack bestPunchBow() {
            return bestPunchBow;
        }

        ItemStack bestPowerBow() {
            return bestPowerBow;
        }

        ItemStack bestBlock() {
            return bestBlock;
        }

        ItemStack worstBlock() {
            return worstBlock;
        }

        ItemStack worstProjectile() {
            return worstProjectile;
        }

        ItemStack worstArrow() {
            return worstArrow;
        }

        int blockCount() {
            return blockCount;
        }
    }
}
