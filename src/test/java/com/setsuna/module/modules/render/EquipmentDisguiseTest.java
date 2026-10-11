package com.setsuna.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.test.MinecraftTestBootstrap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentDisguiseTest {
    private static final String ROOT = "assets/setsuna/cataclysm/armor/";

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initializeItemComponents();
    }

    @Test
    void actualElytraUsesTheSameChestReplacementEntryPointAsArmor() {
        // In 26.1.2 both layer entry points use chestEquipment. The humanoid
        // layer runs first even when the asset has only a WINGS texture layer.
        assertTrue(HumanoidArmorLayer.shouldRender(new ItemStack(Items.ELYTRA), EquipmentSlot.CHEST));
        assertFalse(HumanoidArmorLayer.shouldRender(new ItemStack(Items.ELYTRA), EquipmentSlot.HEAD));
    }

    @Test
    void bundledMeshesHaveTheExpectedBodyParts() throws Exception {
        assertPart("ignitium/ignitium_armor.mesh.json", EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.FEET);
        assertPart("ignitium/ignitium_armor_legs.mesh.json", EquipmentSlot.LEGS);
        assertPart("ignitium/ignitium_elytra.mesh.json", EquipmentSlot.CHEST);
        assertPart("cursium/cursium_armor.mesh.json", EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET);
        assertPart("cursium/cursium_armor_legs.mesh.json", EquipmentSlot.LEGS);
        assertPart("bone_reptile/bone_reptile_armor.mesh.json", EquipmentSlot.HEAD, EquipmentSlot.CHEST);
        assertPart("monstrous_helm/monstrous_helm.mesh.json", EquipmentSlot.HEAD);
        assertPart("bloom_stone_pauldrons/bloom_stone_pauldrons.mesh.json", EquipmentSlot.CHEST);
    }

    @Test
    void completeOriginalGeometryIncludesInheritedCubesAndBothLegDetails() throws Exception {
        assertCubes("ignitium/ignitium_armor.mesh.json", 10, 1, 3, 4, 4, 1, 1);
        assertCubes("cursium/cursium_armor.mesh.json", 9, 1, 2, 4, 4, 3, 3);
        assertCubes("bone_reptile/bone_reptile_armor.mesh.json", 10, 1, 4, 4, 4, 1, 1);
        assertCubes("monstrous_helm/monstrous_helm.mesh.json", 6, 1, 1, 1, 1, 1, 1);
        assertCubes("bloom_stone_pauldrons/bloom_stone_pauldrons.mesh.json", 1, 1, 12, 7, 2, 1, 1);
        assertTrue(CataclysmArmorMesh.bones(EquipmentSlot.LEGS).contains(CataclysmArmorMesh.Bone.BODY));
        assertEquals(CataclysmArmorMesh.bones(EquipmentSlot.FEET),
                List.of(CataclysmArmorMesh.Bone.LEFT_LEG, CataclysmArmorMesh.Bone.RIGHT_LEG));
    }

    @Test
    void bootsUseOuterTextureAndLeggingsHaveOriginalSmallerDeformation() throws Exception {
        for (EquipmentDisguise.Style style : List.of(EquipmentDisguise.Style.IGNITIUM,
                EquipmentDisguise.Style.CURSIUM)) {
            assertEquals(EquipmentDisguise.texture(style, EquipmentSlot.CHEST),
                    EquipmentDisguise.texture(style, EquipmentSlot.FEET));
            assertTrue(EquipmentDisguise.texture(style, EquipmentSlot.LEGS).getPath().endsWith("_legs.png"));
        }
        CataclysmArmorMesh outer = mesh("ignitium/ignitium_armor.mesh.json");
        CataclysmArmorMesh inner = mesh("ignitium/ignitium_armor_legs.mesh.json");
        assertEquals(2.6F, maximumX(outer.part(CataclysmArmorMesh.Bone.LEFT_LEG)), 0.0001F);
        assertEquals(2.2F, maximumX(inner.part(CataclysmArmorMesh.Bone.LEFT_LEG)), 0.0001F);
    }

    @Test
    void posedBonesKeepCrouchingTranslationAndSubmissionsDoNotMutateGeometry() throws Exception {
        CataclysmArmorMesh mesh = mesh("ignitium/ignitium_armor.mesh.json");
        HumanoidModel<HumanoidRenderState> posed = new HumanoidModel<>(LayerDefinition.create(
                HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 64).bakeRoot());
        List<Matrix4f> submitted = new ArrayList<>();
        OrderedSubmitNodeCollector collector = (OrderedSubmitNodeCollector) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{OrderedSubmitNodeCollector.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("submitModelPart")) {
                        submitted.add(new Matrix4f(((PoseStack) args[1]).last().pose()));
                    }
                    return null;
                });
        posed.head.y = 4.2F;
        mesh.submit(EquipmentSlot.HEAD, posed, new HumanoidRenderState(), new PoseStack(),
                collector, null, 0, false);
        posed.head.y = 0;
        mesh.submit(EquipmentSlot.HEAD, posed, new HumanoidRenderState(), new PoseStack(),
                collector, null, 0, false);
        assertEquals(4.2F / 16, submitted.getFirst().m31(), 0.0001F);
        assertEquals(0, submitted.getLast().m31(), 0.0001F);
        assertEquals(0, mesh.part(CataclysmArmorMesh.Bone.HEAD).y);
        assertEquals(0, mesh.part(CataclysmArmorMesh.Bone.HEAD).xRot);
    }

    @Test
    void elytraHasSeparateWingPartsForAnimatedSubmission() throws Exception {
        CataclysmArmorMesh mesh = mesh("ignitium/ignitium_elytra.mesh.json");
        assertTrue(mesh.hasWings());
        assertFalse(mesh.part(CataclysmArmorMesh.Bone.BODY).getChild("left_wing").visible);
        assertFalse(mesh.part(CataclysmArmorMesh.Bone.BODY).getChild("right_wing").visible);
    }

    @Test
    void everyOfferedAppearanceHasItsTexturesAndInventoryIcon() throws Exception {
        String[] files = {
                "ignitium/armor_ignitium_armor.png",
                "ignitium/armor_ignitium_armor_legs.png",
                "ignitium/armor_ignitium_elytra_chestplate.png",
                "ignitium/item_ignitium_helmet.png",
                "ignitium/item_ignitium_chestplate.png",
                "ignitium/item_ignitium_leggings.png",
                "ignitium/item_ignitium_boots.png",
                "ignitium/item_ignitium_elytra_chestplate.png",
                "cursium/cursium_armor.png", "cursium/cursium_armor_legs.png",
                "cursium/cursium_armor_ghost.png", "cursium/cursium_helmet.png",
                "cursium/cursium_chestplate.png", "cursium/cursium_leggings.png",
                "cursium/cursium_boots.png",
                "bone_reptile/bone_reptile_armor.png", "bone_reptile/bone_reptile_helmet.png",
                "bone_reptile/bone_reptile_chestplate.png",
                "monstrous_helm/armor_monstrous_helm.png", "monstrous_helm/item_monstrous_helm.png",
                "bloom_stone_pauldrons/armor_bloom_stone_pauldrons.png",
                "bloom_stone_pauldrons/item_bloom_stone_pauldrons.png",
                "../textures/entity/equipment/humanoid/cataclysm/ignitium.png",
                "../textures/entity/equipment/humanoid_leggings/cataclysm/ignitium.png",
                "../textures/entity/equipment/humanoid/cataclysm/ignitium_elytra.png",
                "../textures/entity/equipment/humanoid/cataclysm/cursium.png",
                "../textures/entity/equipment/humanoid_leggings/cataclysm/cursium.png",
                "../textures/entity/equipment/humanoid/cataclysm/bone_reptile.png",
                "../textures/entity/equipment/humanoid/cataclysm/monstrous_helm.png",
                "../textures/entity/equipment/humanoid/cataclysm/bloom_stone_pauldrons.png"
        };
        for (String file : files) {
            try (InputStream ignored = resource(file)) {
                assertNotNull(ignored, file);
            }
        }
    }

    @Test
    void eachArmorSlotHasAnIndependentChoice() {
        EquipmentDisguise module = EquipmentDisguise.INSTANCE;
        var previousHelmet = module.helmet.get();
        var previousChestplate = module.chestplate.get();
        try {
            module.helmet.set(EquipmentDisguise.HeadStyle.MONSTROUS_HELM);
            module.chestplate.set(EquipmentDisguise.ChestStyle.IGNITIUM_ELYTRA);
            assertEquals(EquipmentDisguise.Style.MONSTROUS_HELM, module.selected(EquipmentSlot.HEAD));
            assertEquals(EquipmentDisguise.Style.IGNITIUM_ELYTRA, module.selected(EquipmentSlot.CHEST));
            assertTrue(module.helmet.isSlider());
            assertTrue(module.chestplate.isSlider());
        } finally {
            module.helmet.set(previousHelmet);
            module.chestplate.set(previousChestplate);
        }
    }

    private static void assertPart(String file, EquipmentSlot... slots) throws Exception {
        try (InputStream stream = resource(file)) {
            CataclysmArmorMesh mesh = CataclysmArmorMesh.parse(stream);
            for (EquipmentSlot slot : slots) assertTrue(mesh.has(slot), file + " missing " + slot);
        }
    }

    private static CataclysmArmorMesh mesh(String file) throws Exception {
        return CataclysmArmorMesh.parse(resource(file));
    }

    private static void assertCubes(String file, int... expected) throws Exception {
        CataclysmArmorMesh mesh = mesh(file);
        for (CataclysmArmorMesh.Bone bone : CataclysmArmorMesh.Bone.values()) {
            int[] count = {0};
            mesh.part(bone).visit(new PoseStack(), (pose, path, index, cube) -> count[0]++);
            assertEquals(expected[bone.ordinal()], count[0], file + ": " + bone);
        }
    }

    private static float maximumX(ModelPart part) {
        float[] maximum = {Float.NEGATIVE_INFINITY};
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            for (var polygon : cube.polygons) for (var vertex : polygon.vertices()) {
                maximum[0] = Math.max(maximum[0], vertex.x());
            }
        });
        return maximum[0];
    }

    private static InputStream resource(String file) {
        String path = file.startsWith("../")
                ? "assets/setsuna/" + file.substring(3)
                : ROOT + file;
        InputStream stream = EquipmentDisguiseTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, file);
        return stream;
    }
}
