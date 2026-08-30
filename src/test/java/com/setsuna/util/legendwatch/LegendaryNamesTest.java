package com.setsuna.util.legendwatch;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LegendaryNamesTest {

    @Test
    void localizesEveryRequestedLegendaryName() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Magma Club", "熔岩巨棒");
        expected.put("Pufferfish Cannon", "河豚大炮");
        expected.put("War Pick", "战争之镐");
        expected.put("Lich Staff", "冰霜法杖");
        expected.put("Ravager Horn", "灾厄号角");
        expected.put("Hypnosis Staff", "催眠权杖");
        expected.put("Wither Sickles", "凋零双镰");
        expected.put("Cloud Sword", "轻云之剑");
        expected.put("Excalibur", "王者之剑");
        expected.put("Kim the Transmuter", "转化师老金");
        expected.put("Chrono Sword", "时空之剑");
        expected.put("Sceptre of Arachne", "阿拉赫涅权杖");
        expected.put("Crimson Chainsword", "猩红链剑");
        expected.put("Gerald the Sniffer", "嗅探兽 - 杰拉德");
        expected.put("Shrink Ray", "收缩射线");
        expected.put("Sculkweaver's Lantern", "幽匿灵灯");
        expected.put("Soul Gauntlet", "灵魂手套");
        expected.put("Magma Cannon", "岩浆大炮");
        expected.put("Dragon Sceptre", "巨龙权杖");

        expected.forEach((english, chinese) -> assertEquals(chinese, LegendaryNames.getDisplayName(english)));
    }

    @Test
    void supportsProvidedSpellingVariants() {
        assertEquals("河豚大炮", LegendaryNames.getDisplayName("Puffenfish Cannon"));
        assertEquals("战争之镐", LegendaryNames.getDisplayName("War pick"));
        assertEquals("王者之剑", LegendaryNames.getDisplayName("Exoalibur"));
        assertEquals("转化师老金", LegendaryNames.getDisplayName("Kim the Transmuten"));
        assertEquals("猩红链剑", LegendaryNames.getDisplayName("Crimson Chainsuord"));
        assertEquals("灵魂手套", LegendaryNames.getDisplayName("SoulGauntlet"));
    }

    @Test
    void resolvesChineseDisplayNamesBackToCanonicalNames() {
        assertEquals("Lich Staff", LegendaryNames.canonicalize("冰霜法杖"));
        assertEquals("Gerald the Sniffer", LegendaryNames.canonicalize("嗅探兽 - 杰拉德"));
        assertEquals("Soul Gauntlet", LegendaryNames.canonicalize("灵魂手套"));
    }

    @Test
    void localizesNamesInsideSentencesWithoutTouchingUnknownText() {
        assertEquals(
                "LuwrFans 制作了 嗅探兽 - 杰拉德！",
                LegendaryNames.localizeText("LuwrFans 制作了 Gerald the Sniffer！"));
        assertEquals(
                "制作了嗅探兽 - 杰拉德！",
                LegendaryNames.localizeText("制作了Gerald the Sniffer！"));
        assertEquals("幽匿灵灯", LegendaryNames.localizeText("Sculkweaver’s Lantern"));
        assertEquals("Not a legendary", LegendaryNames.localizeText("Not a legendary"));
    }

    @Test
    void chatLocalizationPreservesStyleAndClickAction() {
        Style itemStyle = Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.RunCommand("/quickcraft legendary_sniffer"));
        Component original = Component.literal("crafted ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Gerald the ").withStyle(itemStyle))
                .append(Component.literal("Sniffer").withStyle(itemStyle));

        Component localized = LegendaryChatLocalizer.localize(original);

        assertEquals("crafted 嗅探兽 - 杰拉德", localized.getString());
        AtomicReference<Style> translatedStyle = new AtomicReference<>();
        localized.<Void>visit((style, text) -> {
            if (text.contains("嗅探兽")) {
                translatedStyle.set(style);
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        assertEquals(itemStyle, translatedStyle.get());

        Component untouched = Component.literal("ordinary chat");
        assertSame(untouched, LegendaryChatLocalizer.localize(untouched));
    }

    @Test
    void localizesTheWholeEnglishCraftAnnouncement() {
        Component original = Component.literal("santaorclos ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("has crafted the ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("Lich Staff").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("! This legendary cannot be crafted again!")
                        .withStyle(ChatFormatting.YELLOW));

        Component localized = LegendaryChatLocalizer.localize(original);

        assertEquals(
                "santaorclos 制作了 冰霜法杖！这件传奇武器无法被其他玩家再次制作！",
                localized.getString());
    }
}
