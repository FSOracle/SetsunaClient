package com.setsuna.cosmetic;

import net.minecraft.resources.Identifier;

import java.util.Locale;

/** Wearable models present in Cataclysm 3.31, with their original material selections. */
public enum CataclysmAccessory {
    BELT_OF_BEGINNER("body", "belt"),
    BELT_OF_MONSTROSITY("body", "belt"),
    BERSERKER_SOUL_AMULET("body", "soul_trimmed_amulet"),
    BLAZING_GRIPS(null, "gauntlet"),
    CHITIN_CLAW(null, "gauntlet"),
    SANDSTORM_IN_A_BOTTLE("body", "root"),
    STICKY_GLOVES(null, "glove"),
    STURDY_BOOTS(null, "boot"),
    UNBREAKABLE_SKULL("body", "belt"),
    VITALITY_ANKH("body", "ankh");

    final String bodyBone;
    final String child;

    CataclysmAccessory(String bodyBone, String child) {
        this.bodyBone = bodyBone;
        this.child = child;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Identifier texture() {
        String file = this == UNBREAKABLE_SKULL ? "unbreakable_skull2" : id();
        return Identifier.fromNamespaceAndPath("setsuna", "textures/cosmetics/cataclysm/" + file + ".png");
    }

    public Identifier glowTexture() {
        if (this != BLAZING_GRIPS && this != BERSERKER_SOUL_AMULET) return null;
        return Identifier.fromNamespaceAndPath("setsuna", "textures/cosmetics/cataclysm/" + id() + "_layer.png");
    }
}
