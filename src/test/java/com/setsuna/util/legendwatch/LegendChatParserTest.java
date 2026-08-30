package com.setsuna.util.legendwatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LegendChatParserTest {

    @BeforeEach
    @AfterEach
    void clearCrafts() {
        CraftTracker.onMatchReset();
    }

    @Test
    void detectsFullyChineseCraftAnnouncement() {
        LegendChatParser.handleMessage(
                "[MVP+] santaorclos 已制作出传奇武器：冰霜法杖！此传奇物品无法再次制作！");

        assertCraft("santaorclos", "Lich Staff");
    }

    @Test
    void detectsTheDisplayedChineseCraftAnnouncement() {
        LegendChatParser.handleMessage(
                "santaorclos 制作了 冰霜法杖！这件传奇武器无法被其他玩家再次制作！");

        assertCraft("santaorclos", "Lich Staff");
    }

    @Test
    void detectsCompactChineseCraftAnnouncement() {
        LegendChatParser.handleMessage("santaorclos制作了灵魂手套！这件传奇物品不能再次制作！");

        assertCraft("santaorclos", "Soul Gauntlet");
    }

    @Test
    void detectsChineseNameInsideEnglishAnnouncement() {
        LegendChatParser.handleMessage(
                "santaorclos has crafted the 冰霜法杖! This legendary cannot be crafted again!");

        assertCraft("santaorclos", "Lich Staff");
    }

    @Test
    void keepsEnglishCraftDetectionWorking() {
        LegendChatParser.handleMessage(
                "santaorclos has crafted the Lich Staff! This legendary cannot be crafted again!");

        assertCraft("santaorclos", "Lich Staff");
    }

    @Test
    void ignoresOrdinaryChinesePlayerChat() {
        LegendChatParser.handleMessage("santaorclos：我刚刚制作了冰霜法杖！");

        assertFalse(CraftTracker.getCrafts("santaorclos").stream().findAny().isPresent());
    }

    private static void assertCraft(String username, String itemName) {
        List<LegendaryInfo> crafts = CraftTracker.getCrafts(username);
        assertEquals(1, crafts.size());
        assertEquals(itemName, crafts.getFirst().itemName());
        assertFalse(crafts.getFirst().predicted());
    }
}
