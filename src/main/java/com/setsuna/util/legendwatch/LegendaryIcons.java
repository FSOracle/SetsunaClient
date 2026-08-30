package com.setsuna.util.legendwatch;

import com.setsuna.module.modules.render.LegendWatch;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.Map;

public class LegendaryIcons {

    private static final FontDescription.Resource ICON_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("legendwatch", "icons"));
    private static final FontDescription.Resource TRANSPARENT_ICON_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("legendwatch", "transparent_icons"));

    private static final Map<String, String> LEGENDARY_MAP = Map.ofEntries(
            Map.entry("Emerald Blade", "\uE000"),
            Map.entry("Aiglos", "\uE001"),
            Map.entry("Armadillo Detonator", "\uE002"),
            Map.entry("Artemis Bow", "\uE003"),
            Map.entry("Beehive Blaster", "\uE004"),
            Map.entry("Crimson Chainsword", "\uE005"),
            Map.entry("Cloud Sword", "\uE006"),
            Map.entry("Corrupted Crossbow", "\uE007"),
            Map.entry("Death Note", "\uE008"),
            Map.entry("Reinforced Elytra", "\uE009"),
            Map.entry("Horn of Winter", "\uE00A"),
            Map.entry("Enderbow", "\uE00B"),
            Map.entry("Evoker Wand", "\uE00C"),
            Map.entry("Excalibur", "\uE00D"),
            Map.entry("Gerald the Sniffer", "\uE00E"),
            Map.entry("Ghastly Whistle", "\uE00F"),
            Map.entry("Golem Hammer", "\uE010"),
            Map.entry("Gruntilda", "\uE011"),
            Map.entry("Guardian Cannon", "\uE012"),
            Map.entry("Harpoon Launcher", "\uE013"),
            Map.entry("Phantom Longbow", "\uE014"),
            Map.entry("Hypnosis Staff", "\uE015"),
            Map.entry("Jim the Sorcerer", "\uE016"),
            Map.entry("Dragon Katana", "\uE017"),
            Map.entry("Kim the Transmuter", "\uE018"),
            Map.entry("Sculkweaver's Lantern", "\uE019"),
            Map.entry("Lich Staff", "\uE01A"),
            Map.entry("Magma Club", "\uE01B"),
            Map.entry("Midas Sword", "\uE01C"),
            Map.entry("Mjolnir", "\uE01D"),
            Map.entry("Poseidon's Trident", "\uE01E"),
            Map.entry("Pufferfish Cannon", "\uE01F"),
            Map.entry("Ravager Horn", "\uE020"),
            Map.entry("Reaper Scythe", "\uE021"),
            Map.entry("Ribbit Reel", "\uE022"),
            Map.entry("Shadow Blade", "\uE023"),
            Map.entry("Shrink Ray", "\uE024"),
            Map.entry("Sonic Crossbow", "\uE025"),
            Map.entry("Soul Gauntlet", "\uE026"),
            Map.entry("Villager Wand", "\uE027"),
            Map.entry("Void Staff", "\uE028"),
            Map.entry("War Pick", "\uE029"),
            Map.entry("Wither Sickles", "\uE02A"),
            Map.entry("Eagle Eye Bow", "\uE02B"),
            Map.entry("Freezing Chakram", "\uE02C"),
            Map.entry("Headhunter's Chestpiece", "\uE02D"),
            Map.entry("Magma Cannon", "\uE02E"),
            Map.entry("Chrono Sword", "\uE02F"),
            Map.entry("Sakura Tessen", "\uE030"),
            Map.entry("Elder Eye of Possession", "\uE031"),
            Map.entry("Dragon Sceptre", "\uE032"),
            Map.entry("Sceptre of Arachne", "\uE033"),
            Map.entry("Vampire Sabre", "\uE034")
    );

    public static Component getDisplay(String itemName) {
        String codepoint = LEGENDARY_MAP.get(itemName);
        if (codepoint != null && LegendWatch.INSTANCE.iconsEnabled()) {
            FontDescription.Resource font = LegendWatch.INSTANCE.transparentIconsEnabled() ? TRANSPARENT_ICON_FONT : ICON_FONT;
            return Component.literal(codepoint).withStyle(style -> style.withFont(font).withColor(0xFFFFFF));
        }
        return Component.literal(LegendaryNames.getDisplayName(itemName)).withStyle(ChatFormatting.GOLD);
    }

    public static String getCanonicalName(String itemName) {
        if (itemName == null) {
            return null;
        }
        String canonical = LegendaryNames.canonicalize(itemName);
        if (LEGENDARY_MAP.containsKey(canonical)) {
            return canonical;
        }
        for (String knownName : LEGENDARY_MAP.keySet()) {
            if (knownName.equalsIgnoreCase(canonical)) {
                return knownName;
            }
        }
        return null;
    }
}
