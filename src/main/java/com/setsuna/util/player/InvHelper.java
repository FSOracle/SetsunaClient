package com.setsuna.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class InvHelper {

    private static final int HOTBAR_SIZE = 9;
    private static final String GOLDEN_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2JiNjEyZWI0OTVlZGUyYzVjYTUxNzhkMmQxZWNmMWNhNWEyNTVkMjVkZmMzYzI1NGJjNDdmNjg0ODc5MWQ4In19fQ==";

    public static final List<Block> blacklistedBlocks = Arrays.asList(
            Blocks.AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ENCHANTING_TABLE,
            Blocks.GLASS_PANE,
            Blocks.GLASS_PANE,
            Blocks.IRON_BARS,
            Blocks.SNOW,
            Blocks.COAL_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.TORCH,
            Blocks.ANVIL,
            Blocks.TRAPPED_CHEST,
            Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX,
            Blocks.TNT,
            Blocks.GOLD_ORE,
            Blocks.IRON_ORE,
            Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.STONE_BUTTON,
            Blocks.LEVER,
            Blocks.TALL_GRASS,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL,
            Blocks.CORNFLOWER,
            Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM,
            Blocks.VINE,
            Blocks.SUNFLOWER,
            Blocks.LADDER,
            Blocks.FURNACE,
            Blocks.SAND,
            Blocks.CACTUS,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.CRAFTING_TABLE,
            Blocks.COBWEB,
            Blocks.PUMPKIN,
            Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH,
            Blocks.FLOWER_POT
    );

    private static final Set<Item> SWORDS = Set.of(
            Items.WOODEN_SWORD,
            Items.STONE_SWORD,
            Items.COPPER_SWORD,
            Items.IRON_SWORD,
            Items.GOLDEN_SWORD,
            Items.DIAMOND_SWORD,
            Items.NETHERITE_SWORD
    );
    private static final Set<Item> PICKAXES = Set.of(
            Items.WOODEN_PICKAXE,
            Items.STONE_PICKAXE,
            Items.COPPER_PICKAXE,
            Items.IRON_PICKAXE,
            Items.GOLDEN_PICKAXE,
            Items.DIAMOND_PICKAXE,
            Items.NETHERITE_PICKAXE
    );
    private static final Minecraft mc = Minecraft.getInstance();

    private static int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantmentKey) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (entry.getKey().is(enchantmentKey)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }

    public static boolean shouldDisableFeatures() {
        for (ItemStack item : inventorySnapshot()) {
            if (!item.isEmpty() && isMenuActionName(item.getDisplayName().getString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isGoldenHead(ItemStack stack) {
        if (stack == null || !stack.is(Items.PLAYER_HEAD)) {
            return false;
        }

        String name = stack.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
        if (name.contains("golden head") || name.contains("gold head") || name.contains("金头")) {
            return true;
        }

        var profile = stack.get(DataComponents.PROFILE);
        if (profile == null) {
            return false;
        }
        String profileName = profile.name().orElse("").replace('_', ' ');
        if (profileName.equalsIgnoreCase("golden head") || profileName.equalsIgnoreCase("gold head")) {
            return true;
        }
        for (var property : profile.partialProfile().properties().get("textures")) {
            if (GOLDEN_HEAD_TEXTURE.equals(property.value())) {
                return true;
            }
        }

        return false;
    }

    public static boolean isArmor(ItemStack stack) {
        return getArmorSlot(stack) != null;
    }

    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return null;
        }

        EquipmentSlot slot = equippable.slot();
        return slot.isArmor() && slot != EquipmentSlot.BODY ? slot : null;
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && SWORDS.contains(stack.getItem());
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && PICKAXES.contains(stack.getItem());
    }

    public static ItemStack getInventoryStack(int slot) {
        if (mc.player == null || slot < 0 || slot >= mc.player.getInventory().getNonEquipmentItems().size()) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory().getNonEquipmentItems().get(slot);
    }

    public static ItemStack getArmorStack(EquipmentSlot slot) {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory().getItem(slot.getIndex(36));
    }

    public static ItemStack getOffhandStack() {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory().getItem(Inventory.SLOT_OFFHAND);
    }

    public static boolean isSharpnessAxe(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof AxeItem
                && getEnchantmentLevel(stack, Enchantments.SHARPNESS) >= 8
                && getEnchantmentLevel(stack, Enchantments.SHARPNESS) < 50;
    }

    public static boolean isGodAxe(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.GOLDEN_AXE
                && getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100;
    }

    public static boolean isEnchantedGApple(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    public static boolean isEndCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL;
    }

    public static boolean isKBBall(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.SLIME_BALL
                && getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1;
    }

    public static boolean isKBStick(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.STICK
                && getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1;
    }

    public static int findEmptyInventory() {
        return mc.player == null ? -1 : findSlot(
                HOTBAR_SIZE,
                mc.player.getInventory().getNonEquipmentItems().size(),
                ItemStack::isEmpty
        );
    }

    public static int findEmptySlot() {
        return mc.player == null ? -1 : findSlot(0, HOTBAR_SIZE, ItemStack::isEmpty);
    }

    public static Integer findItemHotbar(Item item) {
        if (mc.player == null) return null;
        int slot = findSlot(0, HOTBAR_SIZE, stack -> stack.getItem() == item);
        return slot < 0 ? null : slot;
    }

    public static int getPunchLevel(ItemStack stack) {
        return getEnchantmentLevel(stack, Enchantments.PUNCH);
    }

    public static int getPowerLevel(ItemStack stack) {
        return getEnchantmentLevel(stack, Enchantments.POWER);
    }

    public static List<ItemStack> getAllItems() {
        if (mc.player == null) {
            return new ArrayList<>();
        }

        return inventorySnapshot();
    }

    public static float getBestArmorScore(EquipmentSlot slot) {
        return maxScore(item -> !item.isEmpty() && getArmorSlot(item) == slot, InvHelper::getProtection);
    }

    public static float getCurrentArmorScore(EquipmentSlot slot) {
        return getProtection(getArmorStack(slot));
    }

    public static float getBestSwordDamage() {
        return maxScore(InvHelper::isSword, InvHelper::getSwordDamage);
    }

    public static ItemStack getBestSword() {
        return highest(InvHelper::isSword, stack -> (int) (getSwordDamage(stack) * 100.0F));
    }

    public static int getItemStackSlot(ItemStack stack) {
        return stack == null || mc.player == null
                ? -1
                : findContainerSlot(0, mc.player.getInventory().getContainerSize(), candidate -> candidate == stack);
    }

    public static boolean isItemValid(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        if (stack.getItem() instanceof PlayerHeadItem) {
            return false;
        }

        return !containsAny(
                stack.getDisplayName().getString(),
                "Click", "Right", "点击", "Teleport", "使用", "传送", "再来"
        );
    }

    public static int getItemSlot(Item item) {
        return mc.player == null
                ? -1
                : findSlot(0, mc.player.getInventory().getNonEquipmentItems().size(), stack -> stack.getItem() == item);
    }

    public static ItemStack getBestProjectile() {
        return highest(
                item -> !item.isEmpty()
                        && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL)
                        && isItemValid(item),
                ItemStack::getCount
        );
    }

    public static ItemStack getFishingRod() {
        return first(item -> !item.isEmpty() && item.getItem() instanceof FishingRodItem && isItemValid(item));
    }

    public static int getBlockCountInInventory() {
        int count = 0;
        for (ItemStack item : inventorySnapshot()) {
            if (!item.isEmpty() && item.getItem() instanceof BlockItem && isValidStack(item) && isItemValid(item)) {
                count += item.getCount();
            }
        }
        return count;
    }

    public static ItemStack getWorstProjectile() {
        return lowest(item -> !item.isEmpty()
                && (item.getItem() == Items.EGG || item.getItem() == Items.SNOWBALL), ItemStack::getCount);
    }

    public static ItemStack getWorstArrow() {
        return lowest(item -> !item.isEmpty() && item.getItem() instanceof ArrowItem && isItemValid(item), ItemStack::getCount);
    }

    public static ItemStack getWorstBlock() {
        return lowest(item -> !item.isEmpty()
                && item.getItem() instanceof BlockItem && isValidStack(item) && isItemValid(item), ItemStack::getCount);
    }

    public static ItemStack getBestBlock() {
        return highest(item -> !item.isEmpty()
                && item.getItem() instanceof BlockItem && isValidStack(item) && isItemValid(item), ItemStack::getCount);
    }

    public static float getBestPickaxeScore() {
        return maxScore(item -> isPickaxe(item) && isItemValid(item), InvHelper::getToolScore);
    }

    public static ItemStack getBestPickaxe() {
        return highest(item -> isPickaxe(item) && isItemValid(item), stack -> (int) (getToolScore(stack) * 100.0F));
    }

    public static float getBestAxeScore() {
        return maxScore(item -> !item.isEmpty() && item.getItem() instanceof AxeItem
                && !isSharpnessAxe(item) && isItemValid(item), InvHelper::getToolScore);
    }

    public static ItemStack getBestAxe() {
        return highest(item -> !item.isEmpty() && item.getItem() instanceof AxeItem
                && !isSharpnessAxe(item) && isItemValid(item), stack -> (int) (getToolScore(stack) * 100.0F));
    }

    public static ItemStack getBestShapeAxe() {
        return highest(item -> !item.isEmpty() && item.getItem() instanceof AxeItem
                && isSharpnessAxe(item) && isItemValid(item) && !isGodAxe(item),
                stack -> (int) (getAxeDamage(stack) * 100.0F));
    }

    public static float getBestShovelScore() {
        return maxScore(item -> !item.isEmpty() && item.getItem() instanceof ShovelItem && isItemValid(item), InvHelper::getToolScore);
    }

    public static ItemStack getBestShovel() {
        return highest(item -> !item.isEmpty() && item.getItem() instanceof ShovelItem && isItemValid(item),
                stack -> (int) (getToolScore(stack) * 100.0F));
    }

    public static float getBestCrossbowScore() {
        return maxScore(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item), InvHelper::getCrossbowScore);
    }

    public static ItemStack getBestCrossbow() {
        return highest(item -> !item.isEmpty() && item.getItem() instanceof CrossbowItem && isItemValid(item),
                stack -> (int) (getCrossbowScore(stack) * 100.0F));
    }

    public static float getBestPunchBowScore() {
        return maxScore(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item), InvHelper::getPunchBowScore);
    }

    public static ItemStack getBestPunchBow() {
        return highest(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item),
                stack -> (int) (getPunchBowScore(stack) * 100.0F));
    }

    public static float getBestPowerBowScore() {
        return maxScore(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item), InvHelper::getPowerBowScore);
    }

    public static ItemStack getBestPowerBow() {
        return highest(item -> !item.isEmpty() && item.getItem() instanceof BowItem && isItemValid(item),
                stack -> (int) (getPowerBowScore(stack) * 100.0F));
    }

    public static boolean isPunchBow(ItemStack stack) {
        return getPunchBowScore(stack) > 10.0F && isItemValid(stack);
    }

    public static boolean isPowerBow(ItemStack stack) {
        return getPowerBowScore(stack) > 10.0F && isItemValid(stack);
    }

    public static boolean hasItem(Item item) {
        for (ItemStack stack : inventorySnapshot()) {
            if (!stack.isEmpty() && stack.getItem() == item) return true;
        }
        return false;
    }

    public static int getItemCount(Item item) {
        int total = 0;
        for (ItemStack stack : inventorySnapshot()) {
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    private static ArrayList<ItemStack> inventorySnapshot() {
        ArrayList<ItemStack> snapshot = new ArrayList<>();
        if (mc.player == null) return snapshot;

        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            snapshot.add(mc.player.getInventory().getItem(slot));
        }
        return snapshot;
    }

    private static int findSlot(int first, int endExclusive, Predicate<ItemStack> accepts) {
        for (int slot = first; slot < endExclusive; slot++) {
            if (accepts.test(getInventoryStack(slot))) return slot;
        }
        return -1;
    }

    private static int findContainerSlot(int first, int endExclusive, Predicate<ItemStack> accepts) {
        for (int slot = first; slot < endExclusive; slot++) {
            if (accepts.test(mc.player.getInventory().getItem(slot))) return slot;
        }
        return -1;
    }

    private static boolean isMenuActionName(String name) {
        return containsAny(name, "Click", "点击", "Right", "Teleport", "离开游戏", "选择", "再来");
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) return true;
        }
        return false;
    }

    private static ItemStack first(Predicate<ItemStack> accepts) {
        for (ItemStack stack : inventorySnapshot()) {
            if (accepts.test(stack)) return stack;
        }
        return null;
    }

    private static ItemStack highest(Predicate<ItemStack> accepts, ToIntFunction<ItemStack> score) {
        return extremum(accepts, score, true);
    }

    private static ItemStack lowest(Predicate<ItemStack> accepts, ToIntFunction<ItemStack> score) {
        return extremum(accepts, score, false);
    }

    private static ItemStack extremum(
            Predicate<ItemStack> accepts,
            ToIntFunction<ItemStack> score,
            boolean selectHighest
    ) {
        ItemStack selected = null;
        int selectedScore = 0;
        for (ItemStack stack : inventorySnapshot()) {
            if (!accepts.test(stack)) continue;

            int currentScore = score.applyAsInt(stack);
            boolean better = selected == null
                    || (selectHighest ? currentScore > selectedScore : currentScore < selectedScore);
            if (better) {
                selected = stack;
                selectedScore = currentScore;
            }
        }
        return selected;
    }

    private static float maxScore(Predicate<ItemStack> accepts, Function<ItemStack, Float> score) {
        boolean found = false;
        float maximum = 0.0F;
        for (ItemStack stack : inventorySnapshot()) {
            if (!accepts.test(stack)) continue;

            float current = score.apply(stack);
            if (!found || Float.compare(current, maximum) > 0) {
                found = true;
                maximum = current;
            }
        }
        return found ? maximum : 0.0F;
    }

    public static float getPunchBowScore(ItemStack stack) {
        return bowScore(stack, true);
    }

    public static float getPowerBowScore(ItemStack stack) {
        return bowScore(stack, false);
    }

    public static float getToolScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isGodItem(stack) || isSharpnessAxe(stack)) {
            return 0.0F;
        }

        Float valence = toolBaseSpeed(stack);
        if (valence == null) return 0.0F;

        int efficiency = getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0) {
            valence += efficiency * 0.0075F;
        }

        return valence;
    }

    private static float getMainhandAttackDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }

        final float[] damage = {0.0F};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                damage[0] += getAttributeModifierAmount(modifier);
            }
        });
        return damage[0];
    }

    private static float getSharpnessBonus(ItemStack stack) {
        int level = getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        return level > 0 ? 0.5F * level + 0.5F : 0.0F;
    }

    private static float getAttributeModifierAmount(AttributeModifier modifier) {
        return (float) modifier.amount();
    }

    public static float getAxeDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof AxeItem)) {
            return 0.0F;
        }

        return getMainhandAttackDamage(stack) + getSharpnessBonus(stack);
    }

    public static float getSwordDamage(ItemStack stack) {
        if (!isSword(stack)) {
            return 0.0F;
        }

        return getMainhandAttackDamage(stack) + getSharpnessBonus(stack);
    }

    public static float getProtection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }

        EquipmentSlot slot = getArmorSlot(stack);
        if (slot == null) {
            return 0.0F;
        }

        final ArmorTotals totals = new ArmorTotals();
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR)) {
                totals.armor += (float) modifier.amount();
            } else if (attribute.is(Attributes.ARMOR_TOUGHNESS)) {
                totals.toughness += (float) modifier.amount();
            }
        });

        return totals.armor * 100.0F
                + totals.toughness * 10.0F
                + getEnchantmentLevel(stack, Enchantments.PROTECTION);
    }

    public static float getCrossbowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof CrossbowItem)) {
            return 0.0F;
        }

        int score = getEnchantmentLevel(stack, Enchantments.QUICK_CHARGE);
        score += getEnchantmentLevel(stack, Enchantments.MULTISHOT);
        return score + getEnchantmentLevel(stack, Enchantments.PIERCING);
    }

    public static boolean isGodItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.GOLDEN_AXE && getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 100) return true;
        if (item == Items.SLIME_BALL && getEnchantmentLevel(stack, Enchantments.KNOCKBACK) > 1) return true;
        return item == Items.TOTEM_OF_UNDYING || item == Items.END_CRYSTAL;
    }

    public static boolean isCommonItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        Item item = stack.getItem();
        if (item instanceof BlockItem block) {
            return block.getBlock() != Blocks.ENCHANTING_TABLE && block.getBlock() != Blocks.COBWEB;
        }

        return !isDiscardedCommonItem(item);
    }

    public static boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem) || stack.getCount() <= 1) {
            return false;
        }

        if (!isItemValid(stack)) {
            return false;
        }

        if (containsAny(stack.getDisplayName().getString(), "Click", "点击")) {
            return false;
        }

        if (stack.getItem() instanceof StandingAndWallBlockItem) {
            return false;
        }

        Block block = ((BlockItem) stack.getItem()).getBlock();
        return !isPlantBlock(block) && !(block instanceof SlabBlock) && !blacklistedBlocks.contains(block);
    }

    private static float bowScore(ItemStack stack, boolean punchFirst) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem)) return 0.0F;

        float score = 10.0F;
        if (punchFirst) {
            score += getEnchantmentLevel(stack, Enchantments.PUNCH);
            score += getEnchantmentLevel(stack, Enchantments.INFINITY);
            score += getEnchantmentLevel(stack, Enchantments.FLAME);
            score += getEnchantmentLevel(stack, Enchantments.POWER) / 10.0F;
        } else {
            score += getEnchantmentLevel(stack, Enchantments.PUNCH) / 10.0F;
            score += getEnchantmentLevel(stack, Enchantments.INFINITY);
            score += getEnchantmentLevel(stack, Enchantments.FLAME);
            score += getEnchantmentLevel(stack, Enchantments.POWER);
        }
        return score + (float) stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    private static Float toolBaseSpeed(ItemStack stack) {
        if (isPickaxe(stack)) return stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
        if (stack.getItem() instanceof AxeItem) return stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
        if (stack.getItem() instanceof ShovelItem) return stack.getDestroySpeed(Blocks.DIRT.defaultBlockState());
        return null;
    }

    private static boolean isDiscardedCommonItem(Item item) {
        return item instanceof WritableBookItem
                || item instanceof WrittenBookItem
                || item instanceof KnowledgeBookItem
                || item instanceof ExperienceBottleItem
                || item instanceof FireworkRocketItem
                || item == Items.WHEAT_SEEDS
                || item == Items.BEETROOT_SEEDS
                || item == Items.MELON_SEEDS
                || item == Items.PUMPKIN_SEEDS
                || item == Items.FLINT_AND_STEEL;
    }

    private static boolean isPlantBlock(Block block) {
        return block instanceof FlowerBlock
                || block instanceof BushBlock
                || block instanceof NetherFungusBlock
                || block instanceof CropBlock;
    }

    private static final class ArmorTotals {
        private float armor;
        private float toughness;
    }

}
