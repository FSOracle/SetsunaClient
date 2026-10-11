package com.setsuna.module.modules.render;

import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.EnumSetting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/** Independent appearance choices for each real item category. Never changes an inventory stack. */
public final class CataclysmItemDisguises {
    public static final CataclysmItemDisguises INSTANCE = new CataclysmItemDisguises();

    public enum SwordStyle { NONE, BLACK_STEEL_SWORD, ATHAME, KHOPESH, ZWEIENDER, FINAL_FRACTAL,
        THE_INCINERATOR, SOUL_RENDER, THE_IMMOLATOR, MEAT_SHREDDER, TIDAL_CLAWS,
        GAUNTLET_OF_GUARD, GAUNTLET_OF_BULWARK, GAUNTLET_OF_MAELSTROM, VOID_JAW }
    public enum AxeStyle { NONE, BLACK_STEEL_AXE, CORAL_BARDICHE, SOUL_RENDER }
    public enum PickaxeStyle { NONE, BLACK_STEEL_PICKAXE, INFERNAL_FORGE, VOID_FORGE }
    public enum ShovelStyle { NONE, BLACK_STEEL_SHOVEL }
    public enum HoeStyle { NONE, BLACK_STEEL_HOE }
    public enum MaceStyle { NONE, INFERNAL_FORGE, VOID_FORGE, BRONTES, THE_ANNIHILATOR }
    public enum BowStyle { NONE, CURSED_BOW, WRATH_OF_THE_DESERT }
    public enum CrossbowStyle { NONE, LASER_GATLING, WITHER_ASSAULT_SHOULDER_WEAPON, VOID_ASSAULT_SHOULDER_WEAPON }
    public enum ShieldStyle { NONE, BULWARK_OF_THE_FLAME, BLACK_STEEL_TARGE, AZURE_SEA_SHIELD }
    public enum TridentStyle { NONE, CORAL_SPEAR, ANCIENT_SPEAR, ASTRAPE, CERAUNUS, THE_IMMOLATOR }
    public enum ArrowStyle { NONE, VOID_SCATTER_ARROW }

    public final EnumSetting<SwordStyle> sword = new EnumSetting<>("Sword", SwordStyle.NONE).asSlider();
    public final EnumSetting<AxeStyle> axe = new EnumSetting<>("Axe", AxeStyle.NONE).asSlider();
    public final EnumSetting<PickaxeStyle> pickaxe = new EnumSetting<>("Pickaxe", PickaxeStyle.NONE).asSlider();
    public final EnumSetting<ShovelStyle> shovel = new EnumSetting<>("Shovel", ShovelStyle.NONE).asSlider();
    public final EnumSetting<HoeStyle> hoe = new EnumSetting<>("Hoe", HoeStyle.NONE).asSlider();
    public final EnumSetting<MaceStyle> mace = new EnumSetting<>("Mace", MaceStyle.NONE).asSlider();
    public final EnumSetting<BowStyle> bow = new EnumSetting<>("Bow", BowStyle.NONE).asSlider();
    public final EnumSetting<CrossbowStyle> crossbow = new EnumSetting<>("Crossbow", CrossbowStyle.NONE).asSlider();
    public final EnumSetting<ShieldStyle> shield = new EnumSetting<>("Shield", ShieldStyle.NONE).asSlider();
    public final EnumSetting<TridentStyle> trident = new EnumSetting<>("Trident", TridentStyle.NONE).asSlider();
    public final EnumSetting<ArrowStyle> arrow = new EnumSetting<>("Arrow", ArrowStyle.NONE).asSlider();

    public List<Setting<?>> settings() {
        return List.of(sword, axe, pickaxe, shovel, hoe, mace, bow, crossbow, shield, trident, arrow);
    }

    public String selected(ItemStack stack) {
        EnumSetting<?> choice = settingFor(stack);
        if (choice == null || choice.get().name().equals("NONE")) return null;
        return choice.get().name().toLowerCase(Locale.ROOT);
    }

    public EnumSetting<?> settingFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.SHIELD)) return shield;
        if (stack.is(Items.BOW)) return bow;
        if (stack.is(Items.CROSSBOW)) return crossbow;
        if (stack.is(Items.TRIDENT)) return trident;
        if (stack.is(Items.MACE)) return mace;
        if (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) return arrow;
        // Tags cover modded tools; the vanilla suffix fallback also works before tags load in a menu.
        String vanilla = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")
                ? BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() : "";
        if (stack.is(ItemTags.SWORDS) || vanilla.endsWith("_sword")) return sword;
        if (stack.is(ItemTags.PICKAXES) || vanilla.endsWith("_pickaxe")) return pickaxe;
        if (stack.is(ItemTags.AXES) || vanilla.endsWith("_axe")) return axe;
        if (stack.is(ItemTags.SHOVELS) || vanilla.endsWith("_shovel")) return shovel;
        if (stack.is(ItemTags.HOES) || vanilla.endsWith("_hoe")) return hoe;
        return null;
    }
}
