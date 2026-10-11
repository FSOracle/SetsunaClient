package com.setsuna.module.modules.render;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CosmeticsSettingsTest {
    @Test
    void independentCataclysmSlotsSurviveConfigReloadAlongsideTheDouli() {
        Cosmetics cosmetics = Cosmetics.INSTANCE;
        JsonObject previous = cosmetics.toJson();
        try {
            cosmetics.headwear.set(Cosmetics.Headwear.BAMBOO_DOULI);
            cosmetics.necklace.set(Cosmetics.Necklace.BERSERKER_SOUL_AMULET);
            cosmetics.mainHandAccessory.set(Cosmetics.HandAccessory.BLAZING_GRIPS);
            cosmetics.offHandAccessory.set(Cosmetics.HandAccessory.CHITIN_CLAW);
            cosmetics.waistAccessory.set(Cosmetics.WaistAccessory.SANDSTORM_IN_A_BOTTLE);
            cosmetics.talisman.set(Cosmetics.Talisman.UNBREAKABLE_SKULL);
            cosmetics.footwear.set(Cosmetics.Footwear.STURDY_BOOTS);
            JsonObject saved = cosmetics.toJson();
            cosmetics.mainHandAccessory.set(Cosmetics.HandAccessory.NONE);
            cosmetics.offHandAccessory.set(Cosmetics.HandAccessory.NONE);
            cosmetics.fromJson(saved);
            assertEquals(Cosmetics.Headwear.BAMBOO_DOULI, cosmetics.headwear.get());
            assertEquals(Cosmetics.Necklace.BERSERKER_SOUL_AMULET, cosmetics.necklace.get());
            assertEquals(Cosmetics.HandAccessory.BLAZING_GRIPS, cosmetics.mainHandAccessory.get());
            assertEquals(Cosmetics.HandAccessory.CHITIN_CLAW, cosmetics.offHandAccessory.get());
            assertEquals(Cosmetics.WaistAccessory.SANDSTORM_IN_A_BOTTLE, cosmetics.waistAccessory.get());
            assertEquals(Cosmetics.Talisman.UNBREAKABLE_SKULL, cosmetics.talisman.get());
            assertEquals(Cosmetics.Footwear.STURDY_BOOTS, cosmetics.footwear.get());
        } finally {
            cosmetics.fromJson(previous);
        }
    }

    @Test
    void headwearChoiceSurvivesTheExistingConfigRoundTrip() {
        Cosmetics cosmetics = Cosmetics.INSTANCE;
        Cosmetics.Headwear previous = cosmetics.headwear.get();
        try {
            cosmetics.headwear.set(Cosmetics.Headwear.BAMBOO_DOULI);
            JsonObject saved = cosmetics.toJson();
            assertEquals("BAMBOO_DOULI", saved.getAsJsonObject("settings").get("Headwear").getAsString());
            cosmetics.headwear.set(Cosmetics.Headwear.NONE);
            cosmetics.fromJson(saved);
            assertEquals(Cosmetics.Headwear.BAMBOO_DOULI, cosmetics.headwear.get());
            cosmetics.headwear.fromJson(new com.google.gson.JsonPrimitive("UNKNOWN_FUTURE_STYLE"));
            assertEquals(Cosmetics.Headwear.BAMBOO_DOULI, cosmetics.headwear.get());
        } finally {
            cosmetics.headwear.set(previous);
        }
    }
}
