package com.setsuna.module.modules.player;

import com.setsuna.util.legendwatch.LegendaryNames;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvManagerLegendaryProtectionTest {

    @Test
    void keepsLegendWatchItemsRegardlessOfDisplayColorOrAlias() {
        List<String> names = List.of(
                "Magma Club",
                "Vampire Sabre",
                "Puffenfish Cannon",
                "@War Pick",
                LegendaryNames.getDisplayName("Soul Gauntlet")
        );

        for (String name : names) {
            assertTrue(InvManager.isLegendWatchItemName(name), () -> name + " should be protected");
        }
        assertFalse(InvManager.isLegendWatchItemName("Ordinary Stick"));
    }
}
