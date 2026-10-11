package com.setsuna.cosmetic;

import com.setsuna.Setsuna;
import com.setsuna.accessor.EntityRenderStateAccessor;
import com.setsuna.module.modules.render.CataclysmItemRenderer;
import com.setsuna.module.modules.render.EquipmentDisguise;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;

import java.util.EnumSet;
import java.util.Set;

/** Only a usable transmog suppresses cosmetics. Original equipment can be worn with cosmetics. */
public final class EquipmentVisualPriority {
    private EquipmentVisualPriority() { }

    public static boolean allowsCosmetic(HumanoidRenderState state, EquipmentSlot slot) {
        var stack = switch (slot) {
            case HEAD -> state.headEquipment;
            case CHEST -> state.chestEquipment;
            case LEGS -> state.legsEquipment;
            case FEET -> state.feetEquipment;
            default -> throw new IllegalArgumentException("Expected an armor slot: " + slot);
        };
        return !EquipmentDisguise.INSTANCE.replacesArmor(state, stack, slot);
    }

    public static Snapshot resolve(HumanoidRenderState state) {
        EnumSet<EquipmentSlot> replaced = EnumSet.noneOf(EquipmentSlot.class);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!allowsCosmetic(state, slot)) replaced.add(slot);
        }
        var owner = state instanceof EntityRenderStateAccessor accessor ? accessor.setsuna$getEntity() : null;
        for (HumanoidArm arm : HumanoidArm.values()) {
            var stack = arm == HumanoidArm.LEFT ? state.leftHandItemStack : state.rightHandItemStack;
            if (CataclysmItemRenderer.replacesHeldItem(stack, owner)) {
                replaced.add(arm == state.mainArm ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        return new Snapshot(replaced, state.mainArm);
    }

    public static boolean allowsFirstPersonHand(HumanoidArm arm) {
        var player = Setsuna.mc().player;
        if (player == null) return false;
        var held = arm == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
        return !EquipmentDisguise.INSTANCE.replacesLocalArmor(EquipmentSlot.CHEST)
                && !CataclysmItemRenderer.replacesHeldItem(held, player);
    }

    public record Snapshot(Set<EquipmentSlot> replaced, HumanoidArm mainArm) {
        public Snapshot {
            replaced = Set.copyOf(replaced);
        }

        public boolean allowsArmorSlot(EquipmentSlot slot) {
            return !replaced.contains(slot);
        }

        public boolean allowsHand(HumanoidArm arm) {
            EquipmentSlot hand = arm == mainArm ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            return !replaced.contains(EquipmentSlot.CHEST) && !replaced.contains(hand);
        }
    }
}
