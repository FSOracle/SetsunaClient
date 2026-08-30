package com.setsuna.module.modules.player;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.setsuna.util.legendwatch.LegendaryNames;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The Hoplite quick-craft catalog used by OpenClap.
 *
 * <p>These are server-side items, so they do not have entries in Minecraft's
 * item registry.  The base item and custom model data reproduce the stacks
 * sent by the original client and let the server resource pack render them.
 */
final class FastCraftCatalog {

    private static final String GOLDEN_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2JiNjEyZWI0OTVlZGUyYzVjYTUxNzhkMmQxZWNmMWNhNWEyNTVkMjVkZmMzYzI1NGJjNDdmNjg0ODc5MWQ4In19fQ==";

    private static final ResolvableProfile GOLDEN_HEAD_PROFILE = createGoldenHeadProfile();

    private static final List<Definition> ENTRIES = List.of(
            entry("Golden Head", "golden_head", Items.PLAYER_HEAD, 0.0F),
            entry("Light Apple", "light_apple", Items.GOLDEN_APPLE, 1.0F),
            entry("Suspicious Stew", "suspicious_stew", Items.SUSPICIOUS_STEW, 8.0F),
            entry("Totem", "totem", Items.TOTEM_OF_UNDYING, 0.0F),
            entry("Panacea Potion", "panacea", Items.POTION, 1.0F),
            entry("Super Smelter's Pickaxe", "infernal_pickaxe", Items.DIAMOND_PICKAXE, 4.0F),
            entry("Smelter's Pickaxe", "smelters_pickaxe", Items.IRON_PICKAXE, 1.0F),
            entry("Explosive Pickaxe", "explosive_pickaxe", Items.DIAMOND_PICKAXE, 2.0F),
            entry("Lumberjack's Axe", "lumberjacks_axe", Items.IRON_AXE, 1.0F),
            entry("Nether Reactor Core", "nether_reactor", Items.BARRIER, 1.0F),
            entry("Light Anvil", "light_anvil", Items.ANVIL, 0.0F),
            entry("Ares Blessing", "ares_blessing", Items.PAPER, 66.0F),
            entry("Tracker Pack", "wishbone", Items.BONE, 0.0F),
            entry("Ender Dog Bone", "ender_wishbone", Items.BONE, 9.0F),
            entry("Bundled Arrow", "bundled_arrows", Items.ARROW, 0.0F),
            entry("Bundled", "bundled_item", Items.BUNDLE, 0.0F),
            entry("Light Netherite Sword", "light_netherite_sword", Items.NETHERITE_SWORD, 2.0F),
            entry("Trident", "trident", Items.TRIDENT, 0.0F),
            entry("ShortSword", "quick_sword", Items.IRON_SWORD, 2.0F),
            entry("ShortBow", "quick_bow", Items.BOW, 4.0F),
            entry("Barbed Rod", "barbed_rod", Items.FISHING_ROD, 4.0F),
            entry("Cactus Chestplate", "cactus_chestplate", Items.LEATHER_CHESTPLATE, 1.0F),
            entry("Bandit Leggings", "bandit_leggings", Items.LEATHER_LEGGINGS, 4.0F),
            entry("Skeleton Leggings", "skeleton_leggings", Items.LEATHER_LEGGINGS, 6.0F),
            entry("Axolotl Boots", "axolotl_boots", Items.LEATHER_BOOTS, 5.0F),
            entry("Gerald the Sniffer", "legendary_sniffer", Items.SNIFFER_SPAWN_EGG, 1.0F),
            entry("Magma Club", "magma_club", Items.IRON_SWORD, 3.0F),
            entry("Aiglos", "aiglos", Items.BOW, 7.0F),
            entry("Reinforced Elytra", "reinforced_elytra", Items.ELYTRA, 1.0F),
            entry("Wither Sickles", "wither_sickles", Items.STONE_HOE, 3.0F),
            entry("Shrink Ray", "shrink_ray", Items.BOW, 12.0F),
            entry("Excalibur", "excalibur", Items.NETHERITE_SWORD, 3.0F),
            entry("Evoker Wand", "evoker_wand", Items.STONE_SWORD, 7.0F),
            entry("Sculkweaver's Lantern", "sculkweavers_lantern", Items.FISHING_ROD, 5.0F),
            entry("Crimson Chainsword", "crimson_chainsword", Items.DIAMOND_SWORD, 8.0F),
            entry("Armadillo Detonator", "armadillo_detonator", Items.CROSSBOW, 5.0F),
            entry("Soul Gauntlet", "soul_gauntlet", Items.BLAZE_ROD, 17.0F),
            entry("Phantom Longbow", "phantom_bow", Items.BOW, 22.0F),
            entry("Ribbit Reel", "ribbit_reel", Items.BOW, 16.0F),
            entry("Magma Cannon", "magma_cannon", Items.BLAZE_ROD, 24.0F),
            entry("Villager Wand", "villager_wand", Items.STONE_SWORD, 11.0F),
            entry("Ghastly Whistle", "ghastly_whistle", Items.BLAZE_ROD, 18.0F),
            entry("Expert Evoker", "expert_evoker", Items.IRON_INGOT, 1.0F),
            entry("Freezing Chakram", "freezing_chakram", Items.DIAMOND_SWORD, 13.0F),
            entry("Kim the Transmuter", "kim_the_transmuter", Items.IRON_INGOT, 3.0F),
            entry("Gruntilda", "gruntilda", Items.IRON_INGOT, 4.0F),
            entry("@Revival Star", "revival_star", Items.PAPER, 70.0F),
            entry("Mjolnir", "mjolnir", Items.STONE_AXE, 1.0F),
            entry("Golem Hammer", "golem_hammer", Items.STONE_AXE, 3.0F),
            entry("Lich Staff", "lich_staff", Items.STONE_SWORD, 2.0F),
            entry("Void Staff", "void_staff", Items.STONE_SWORD, 4.0F),
            entry("Hypnosis Staff", "hypnosis_staff", Items.STONE_SWORD, 6.0F),
            entry("Reaper Scythe", "reaper_scythe", Items.STONE_HOE, 2.0F),
            entry("Pufferfish Cannon", "pufferfish_cannon", Items.WOODEN_SHOVEL, 1.0F),
            entry("Chrono Sword", "chrono_sword", Items.IRON_SWORD, 10.0F),
            entry("Midas Sword", "midas_sword", Items.DIAMOND_SWORD, 1.0F),
            entry("Dragon Katana", "dragon_katana", Items.DIAMOND_SWORD, 2.0F),
            entry("@Emerald Blade", "emerald_blade", Items.DIAMOND_SWORD, 3.0F),
            entry("Shadow Blade", "shadow_blade", Items.DIAMOND_SWORD, 4.0F),
            entry("Cloud Sword", "cloud_sword", Items.DIAMOND_SWORD, 7.0F),
            entry("@War Pick", "war_pick", Items.DIAMOND_PICKAXE, 1.0F),
            entry("Enderbow", "enderbow", Items.BOW, 1.0F),
            entry("Artemis Bow", "artemis_bow", Items.BOW, 3.0F),
            entry("Poseidon's Trident", "poseidons_trident", Items.BOW, 8.0F),
            entry("Beehive Blaster", "beehive_blaster", Items.BOW, 9.0F),
            entry("Guardian Cannon", "guardian_cannon", Items.BOW, 10.0F),
            entry("Harpoon Launcher", "harpoon_launcher", Items.BOW, 11.0F),
            entry("Eagle Eye Bow", "eagle_eye_bow", Items.BOW, 28.0F),
            entry("Sonic Crossbow", "sonic_crossbow", Items.CROSSBOW, 1.0F),
            entry("@Flaming Crossbow", "flaming_crossbow", Items.CROSSBOW, 3.0F),
            entry("Corrupted Crossbow", "corrupted_crossbow", Items.CROSSBOW, 4.0F),
            entry("Horn of Winter", "horn_of_winter", Items.BLAZE_ROD, 9.0F),
            entry("Ravager Horn", "ravager_horn", Items.GOAT_HORN, 1.0F),
            entry("@Headhunter's Chestpiece", "headhunters_chestpiece", Items.DIAMOND_CHESTPLATE, 1.0F),
            entry("Rigged Saddle", "saddle", Items.SADDLE, 1.0F)
    );

    private FastCraftCatalog() {
    }

    static List<Definition> entries() {
        return ENTRIES;
    }

    static ItemStack createStack(Definition definition) {
        ItemStack stack = new ItemStack(definition.baseItem());
        if ("golden_head".equals(definition.id())) {
            stack.set(DataComponents.PROFILE, GOLDEN_HEAD_PROFILE);
        } else {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    List.of(definition.modelData()), List.of(), List.of(), List.of()));
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(LegendaryNames.getDisplayName(definition.displayName()))
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
        return stack;
    }

    private static Definition entry(String displayName, String id, Item baseItem, float modelData) {
        return new Definition(displayName, id, baseItem, modelData);
    }

    private static ResolvableProfile createGoldenHeadProfile() {
        ImmutableMultimap<String, Property> properties = ImmutableMultimap.of(
                "textures", new Property("textures", GOLDEN_HEAD_TEXTURE));
        PropertyMap propertyMap = new PropertyMap(properties);
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes("golden_head".getBytes(StandardCharsets.UTF_8)),
                "golden_head",
                propertyMap);
        return ResolvableProfile.createResolved(profile);
    }

    record Definition(String displayName, String id, Item baseItem, float modelData) {
    }
}
