package com.setsuna.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CataclysmAccessoryModelsTest {
    // Counts from the original CuriosModel layer definitions, excluding transparent humanoid skin cubes.
    private static final Map<CataclysmAccessory, Integer> CUBES = Map.of(
            CataclysmAccessory.BELT_OF_BEGINNER, 3,
            CataclysmAccessory.BELT_OF_MONSTROSITY, 7,
            CataclysmAccessory.BERSERKER_SOUL_AMULET, 3,
            CataclysmAccessory.BLAZING_GRIPS, 4,
            CataclysmAccessory.CHITIN_CLAW, 6,
            CataclysmAccessory.SANDSTORM_IN_A_BOTTLE, 13,
            CataclysmAccessory.STICKY_GLOVES, 4,
            CataclysmAccessory.STURDY_BOOTS, 12,
            CataclysmAccessory.UNBREAKABLE_SKULL, 12,
            CataclysmAccessory.VITALITY_ANKH, 3);

    @Test
    void retainsEveryOriginalCubeAndPlaneForBothPlayerArmTypes() {
        for (boolean slim : new boolean[]{false, true}) {
            for (CataclysmAccessory accessory : CataclysmAccessory.values()) {
                ModelPart root = CataclysmAccessoryModels.bake(accessory, slim);
                AtomicInteger count = new AtomicInteger();
                for (ModelPart child : wearableParts(accessory, root)) {
                    child.visit(new PoseStack(), (pose, path, index, cube) -> count.incrementAndGet());
                }
                assertEquals(CUBES.get(accessory).intValue(), count.get(), accessory + " slim=" + slim);
            }
        }
    }

    @Test
    void keepsTheOriginalSlimArmOffsetsAndBootInflation() {
        ModelPart wideGrip = CataclysmAccessoryModels.bake(CataclysmAccessory.BLAZING_GRIPS, false)
                .getChild("right_arm").getChild("right_gauntlet");
        ModelPart slimGrip = CataclysmAccessoryModels.bake(CataclysmAccessory.BLAZING_GRIPS, true)
                .getChild("right_arm").getChild("right_gauntlet");
        assertEquals(-1.0F, wideGrip.x);
        assertEquals(0.0F, slimGrip.x, 0.0001F);
        ModelPart wideGlove = CataclysmAccessoryModels.bake(CataclysmAccessory.STICKY_GLOVES, false)
                .getChild("left_arm").getChild("left_glove");
        ModelPart slimGlove = CataclysmAccessoryModels.bake(CataclysmAccessory.STICKY_GLOVES, true)
                .getChild("left_arm").getChild("left_glove");
        assertEquals(0.0F, wideGlove.x, 0.0001F);
        assertEquals(-1.0F, slimGlove.x);
        ModelPart boot = CataclysmAccessoryModels.bake(CataclysmAccessory.STURDY_BOOTS, false)
                .getChild("right_leg").getChild("right_boot");
        assertEquals(12.0F, boot.y);
        float[] firstCubeMaximumX = {Float.NEGATIVE_INFINITY};
        boot.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (index != 0) return;
            for (var polygon : cube.polygons) for (var vertex : polygon.vertices()) {
                firstCubeMaximumX[0] = Math.max(firstCubeMaximumX[0], vertex.x());
            }
        });
        assertEquals(2.57F, firstCubeMaximumX[0], 0.0001F,
                "Boots keep the registered 0.3 inflation plus the original 0.27 cube extension");
    }

    @Test
    void packagesTheNativeWearableTexturesAndBothEmissiveLayers() throws Exception {
        int glowCount = 0;
        for (CataclysmAccessory accessory : CataclysmAccessory.values()) {
            assertTexture(accessory.texture().getPath());
            if (accessory.glowTexture() != null) {
                assertTexture(accessory.glowTexture().getPath());
                glowCount++;
            }
        }
        assertEquals(2, glowCount);
        assertTrue(CataclysmAccessory.UNBREAKABLE_SKULL.texture().getPath().endsWith("unbreakable_skull2.png"),
                "The original renderer uses the second skull texture, not the obsolete first texture");
    }

    private static ModelPart[] wearableParts(CataclysmAccessory accessory, ModelPart root) {
        if (accessory.bodyBone != null) return new ModelPart[]{root.getChild(accessory.bodyBone).getChild(accessory.child)};
        String limb = accessory == CataclysmAccessory.STURDY_BOOTS ? "leg" : "arm";
        return new ModelPart[]{root.getChild("left_" + limb).getChild("left_" + accessory.child),
                root.getChild("right_" + limb).getChild("right_" + accessory.child)};
    }

    private void assertTexture(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/setsuna/" + path)) {
            assertNotNull(stream, path);
            var image = ImageIO.read(stream);
            assertNotNull(image, path);
            assertEquals(128, image.getWidth(), path);
            assertEquals(128, image.getHeight(), path);
        }
    }
}
