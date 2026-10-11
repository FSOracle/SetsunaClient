package com.setsuna.cosmetic;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentVisualPriorityTest {
    @Test
    void noSuccessfulReplacementKeepsEveryCosmeticVisible() {
        // Original equipment, a disabled transmog, and a failed model load all resolve to this state.
        var priority = new EquipmentVisualPriority.Snapshot(Set.of(), HumanoidArm.RIGHT);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) assertTrue(priority.allowsArmorSlot(slot));
        assertTrue(priority.allowsHand(HumanoidArm.LEFT));
        assertTrue(priority.allowsHand(HumanoidArm.RIGHT));
    }

    @Test
    void armorReplacementHidesOnlyItsMatchingPart() {
        var helmet = new EquipmentVisualPriority.Snapshot(Set.of(EquipmentSlot.HEAD), HumanoidArm.RIGHT);
        assertFalse(helmet.allowsArmorSlot(EquipmentSlot.HEAD));
        assertTrue(helmet.allowsArmorSlot(EquipmentSlot.CHEST));
        assertTrue(helmet.allowsArmorSlot(EquipmentSlot.LEGS));
        assertTrue(helmet.allowsArmorSlot(EquipmentSlot.FEET));
        assertTrue(helmet.allowsHand(HumanoidArm.LEFT));
        assertTrue(helmet.allowsHand(HumanoidArm.RIGHT));

        var chest = new EquipmentVisualPriority.Snapshot(Set.of(EquipmentSlot.CHEST), HumanoidArm.RIGHT);
        assertFalse(chest.allowsArmorSlot(EquipmentSlot.CHEST));
        assertFalse(chest.allowsHand(HumanoidArm.LEFT));
        assertFalse(chest.allowsHand(HumanoidArm.RIGHT));
        assertTrue(chest.allowsArmorSlot(EquipmentSlot.HEAD));
        assertTrue(chest.allowsArmorSlot(EquipmentSlot.LEGS));
        assertTrue(chest.allowsArmorSlot(EquipmentSlot.FEET));
    }

    @Test
    void weaponReplacementTracksThePhysicalArmForBothHandednessSettings() {
        for (HumanoidArm mainArm : HumanoidArm.values()) {
            var main = new EquipmentVisualPriority.Snapshot(Set.of(EquipmentSlot.MAINHAND), mainArm);
            assertFalse(main.allowsHand(mainArm));
            assertTrue(main.allowsHand(mainArm.getOpposite()));
            assertTrue(main.allowsArmorSlot(EquipmentSlot.CHEST));
            var off = new EquipmentVisualPriority.Snapshot(Set.of(EquipmentSlot.OFFHAND), mainArm);
            assertTrue(off.allowsHand(mainArm));
            assertFalse(off.allowsHand(mainArm.getOpposite()));
        }
    }
}
