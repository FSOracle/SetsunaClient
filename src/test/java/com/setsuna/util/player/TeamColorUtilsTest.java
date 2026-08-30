package com.setsuna.util.player;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamColorUtilsTest {

    @Test
    void readsComponentColorAndComparesRgbOnly() {
        Component blueName = Component.literal("Player").withStyle(ChatFormatting.BLUE);

        assertEquals(ChatFormatting.BLUE.getColor(), TeamColorUtils.getTextColor(blueName));
        assertNull(TeamColorUtils.getTextColor(null));
        assertTrue(TeamColorUtils.sameColor(0xFF336699, 0x00336699));
        assertFalse(TeamColorUtils.sameColor(0x00336699, 0x00996633));
        assertFalse(TeamColorUtils.sameColor(null, 0x00336699));
    }
}
