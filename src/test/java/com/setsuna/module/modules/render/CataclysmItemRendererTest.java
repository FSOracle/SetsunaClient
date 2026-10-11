package com.setsuna.module.modules.render;

import com.google.gson.JsonObject;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.test.MinecraftTestBootstrap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import static org.junit.jupiter.api.Assertions.*;

class CataclysmItemRendererTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initializeItemComponents();
    }

    @Test
    void everySelectableAppearanceLoadsItsNativeMeshTexturesAndDisplayTransforms() throws Exception {
        Map<String, JsonObject> catalog = catalog();
        Set<String> choices = new HashSet<>();
        CataclysmItemDisguises options = new CataclysmItemDisguises();
        for (var setting : options.settings()) {
            assertTrue(((EnumSetting<?>) setting).isSlider());
            for (Enum<?> value : ((EnumSetting<?>) setting).values()) {
                if (!value.name().equals("NONE")) choices.add(value.name().toLowerCase(Locale.ROOT));
            }
        }
        assertEquals(catalog.keySet(), choices, "Every imported weapon/tool must be reachable");
        int meshes = 0, flat = 0;
        for (String id : choices) {
            CataclysmItemAppearance appearance = load(id, catalog);
            if (appearance.flat) {
                flat++;
                try (InputStream ignored = resource(Identifier.fromNamespaceAndPath("setsuna", "items/cataclysm/" + id + ".json"))) { }
            } else {
                meshes++;
                assertNotNull(appearance.model);
                assertTrue(appearance.extents().length > 0, id);
                for (var extent : appearance.extents()) {
                    assertTrue(Float.isFinite(extent.x()) && Float.isFinite(extent.y()) && Float.isFinite(extent.z()), id);
                }
                assertNotSame(net.minecraft.client.resources.model.cuboid.ItemTransform.NO_TRANSFORM,
                        appearance.transform(ItemDisplayContext.GUI, ""), id + " GUI transform");
            }
        }
        assertEquals(25, meshes);
        assertEquals(11, flat);
    }

    @Test
    void categoryChoicesAreIndependentAndDoNotTurnBlocksIntoWeapons() {
        CataclysmItemDisguises options = new CataclysmItemDisguises();
        options.sword.set(CataclysmItemDisguises.SwordStyle.THE_INCINERATOR);
        options.pickaxe.set(CataclysmItemDisguises.PickaxeStyle.VOID_FORGE);
        options.axe.set(CataclysmItemDisguises.AxeStyle.BLACK_STEEL_AXE);
        assertEquals("the_incinerator", options.selected(new ItemStack(Items.DIAMOND_SWORD)));
        assertEquals("void_forge", options.selected(new ItemStack(Items.NETHERITE_PICKAXE)));
        assertEquals("black_steel_axe", options.selected(new ItemStack(Items.IRON_AXE)));
        assertNull(options.selected(new ItemStack(Items.DIAMOND_SHOVEL)));
        assertNull(options.selected(new ItemStack(Items.STONE)));
        assertNull(options.selected(ItemStack.EMPTY));
    }

    @Test
    void renderedFlatStackIsACopyAndOriginalDataIsUnchanged() {
        ItemStack original = new ItemStack(Items.DIAMOND_SWORD);
        original.setDamageValue(87);
        var originalComponents = original.immutableComponents();
        Identifier appearance = Identifier.fromNamespaceAndPath("setsuna", "cataclysm/zweiender");
        ItemStack visual = CataclysmItemRenderer.visualCopy(original, appearance);
        assertNotSame(original, visual);
        assertEquals(appearance, visual.get(DataComponents.ITEM_MODEL));
        assertEquals(originalComponents, original.immutableComponents());
        assertEquals(87, visual.getDamageValue());
        assertNotEquals(appearance, original.get(DataComponents.ITEM_MODEL));
    }

    @Test
    void remotePlayersContainersAndGroundItemsNeverReceiveLocalAppearances() {
        Object local = new Object(), remote = new Object();
        for (ItemDisplayContext hand : new ItemDisplayContext[]{ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND}) {
            assertTrue(CataclysmItemRenderer.allowedScope(hand, local, local, false));
            assertFalse(CataclysmItemRenderer.allowedScope(hand, remote, local, true));
            assertFalse(CataclysmItemRenderer.allowedScope(hand, null, local, true));
        }
        assertTrue(CataclysmItemRenderer.allowedScope(ItemDisplayContext.GUI, local, local, true));
        assertTrue(CataclysmItemRenderer.allowedScope(ItemDisplayContext.GUI, null, local, true));
        assertFalse(CataclysmItemRenderer.allowedScope(ItemDisplayContext.GUI, local, local, false));
        assertFalse(CataclysmItemRenderer.allowedScope(ItemDisplayContext.GUI, remote, local, true));
        assertFalse(CataclysmItemRenderer.allowedScope(ItemDisplayContext.GUI, null, null, true));
        assertFalse(CataclysmItemRenderer.allowedScope(ItemDisplayContext.GROUND, local, local, true));
        assertFalse(CataclysmItemRenderer.allowedScope(ItemDisplayContext.FIXED, local, local, true));
    }

    @Test
    void originalHandedTransformsAndUseOverridesArePreserved() throws Exception {
        Map<String, JsonObject> catalog = catalog();
        var sword = load("the_incinerator", catalog);
        var gui = sword.transform(ItemDisplayContext.GUI, "");
        assertEquals(0.325F, gui.scale().x(), 0.0001F);
        assertEquals(-2.5F / 16, gui.translation().x(), 0.0001F);
        assertEquals(-90, gui.rotation().x());
        assertSame(sword.transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ""),
                sword.transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ""));
        var shield = load("bulwark_of_the_flame", catalog);
        String blocking = shield.variant(true, 0);
        assertTrue(blocking.endsWith("_blocking"));
        assertEquals("", shield.variant(false, 0));
        assertNotEquals(shield.transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ""),
                shield.transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, blocking));
        var spear = load("coral_spear", catalog);
        assertTrue(spear.variant(true, 0).endsWith("_throwing"));
        assertNotEquals(spear.transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ""),
                spear.transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ""));
    }

    @Test
    void bowArrowsHideAtRestAndEachDeferredPoseResetsItsAnimation() throws Exception {
        Map<String, JsonObject> catalog = catalog();
        for (String id : new String[]{"cursed_bow", "wrath_of_the_desert"}) {
            var model = load(id, catalog).model;
            var idle = new CataclysmItemModel.Frame(20, 0, false);
            var pulled = new CataclysmItemModel.Frame(35, 0.75F, true);
            model.setupAnim(idle);
            assertFalse(model.part("arrow_pivot1").getChild("setsuna_geometry").visible);
            float idleArm = model.part("arm1").xRot;
            model.setupAnim(pulled);
            float pulledArm = model.part("arm1").xRot;
            assertNotEquals(idleArm, pulledArm);
            assertEquals(0.9F, model.part("arrow_pivot1").getChild("setsuna_geometry").xScale, 0.0001F);
            assertEquals(1, model.part("arrow_pivot1").xScale, "Citadel scale must not move child pivots");
            model.setupAnim(idle);
            assertEquals(idleArm, model.part("arm1").xRot);
            assertFalse(model.part("arrow_pivot1").getChild("setsuna_geometry").visible);
            model.setupAnim(pulled);
            assertEquals(pulledArm, model.part("arm1").xRot);
        }
    }

    @Test
    void missingTexturesFailBeforeCreatingAReplacement() throws Exception {
        JsonObject metadata = catalog().get("the_incinerator");
        assertThrows(IOException.class, () -> new CataclysmItemAppearance("the_incinerator", metadata, id -> {
            if (id.getPath().endsWith(".png")) throw new IOException("Test missing texture");
            return resource(id);
        }));
    }

    @Test
    void emptyOrDegenerateMeshesFailBeforeReplacingTheOriginalItem() throws Exception {
        JsonObject metadata = catalog().get("the_incinerator");
        for (String cubes : new String[]{"[]", "[[0,0,0,0,0,0,0,0,0,0,0,false]]"}) {
            String emptyMesh = "{\"texture_width\":64,\"texture_height\":64,\"bones\":["
                    + "{\"id\":\"b0\",\"name\":\"root\",\"parent\":null,\"pivot\":[0,0,0],"
                    + "\"rotation\":[0,0,0],\"cubes\":" + cubes + "}]}";
            assertThrows(IOException.class, () -> new CataclysmItemAppearance("the_incinerator", metadata, id -> {
                if (id.getPath().endsWith("model.mesh.json")) {
                    return new ByteArrayInputStream(emptyMesh.getBytes(StandardCharsets.UTF_8));
                }
                return resource(id);
            }));
        }
    }

    @Test
    void chargedBowBoundsCaptureTheCurrentGeometryAndRemainStableForDeferredGuiRendering() throws Exception {
        Map<String, JsonObject> catalog = catalog();
        for (String id : new String[]{"cursed_bow", "wrath_of_the_desert"}) {
            var appearance = load(id, catalog);
            var idle = new CataclysmItemModel.Frame(0, 0, false);
            var drawn = new CataclysmItemModel.Frame(10, 1, true);
            double[] idleBounds = guiBounds(appearance, appearance.extents(idle));
            var captured = appearance.extents(drawn);
            double[] capturedBounds = guiBounds(appearance, captured);
            assertTrue(capturedBounds[0] < idleBounds[0] - 0.01 || capturedBounds[2] > idleBounds[2] + 0.01
                    || capturedBounds[1] < idleBounds[1] - 0.01 || capturedBounds[3] > idleBounds[3] + 0.01,
                    id + " drawn arrows must expand the GUI bounds");
            // A later inventory slot may reset the shared model to idle before the earlier slot draws.
            appearance.extents(idle);
            assertArrayEquals(capturedBounds, guiBounds(appearance, captured), 0.00001,
                    id + " bounds must not depend on the shared model's latest pose");
            assertArrayEquals(capturedBounds, guiBounds(appearance, appearance.extents(drawn)), 0.00001);
        }
    }

    private static double[] guiBounds(CataclysmItemAppearance appearance, Vector3fc[] points) {
        PoseStack transform = new PoseStack();
        appearance.transform(ItemDisplayContext.GUI, "").apply(false, transform.last());
        double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Vector3fc point : points) {
            Vector3f transformed = transform.last().pose().transformPosition(new Vector3f(point));
            bounds[0] = Math.min(bounds[0], transformed.x);
            bounds[1] = Math.min(bounds[1], transformed.y);
            bounds[2] = Math.max(bounds[2], transformed.x);
            bounds[3] = Math.max(bounds[3], transformed.y);
        }
        return bounds;
    }

    private static CataclysmItemAppearance load(String id, Map<String, JsonObject> catalog) throws IOException {
        return new CataclysmItemAppearance(id, catalog.get(id), CataclysmItemRendererTest::resource);
    }

    private static Map<String, JsonObject> catalog() throws IOException {
        var manifest = CataclysmItemAppearance.readJson(resource(Identifier.fromNamespaceAndPath("setsuna", "cataclysm/items/manifest.json")));
        Map<String, JsonObject> result = new HashMap<>();
        manifest.getAsJsonArray("items").forEach(item -> {
            JsonObject metadata = item.getAsJsonObject();
            result.put(metadata.get("id").getAsString(), metadata);
        });
        return result;
    }

    private static InputStream resource(Identifier id) throws IOException {
        InputStream stream = CataclysmItemRendererTest.class.getClassLoader()
                .getResourceAsStream("assets/" + id.getNamespace() + "/" + id.getPath());
        if (stream == null) throw new IOException("Missing " + id);
        return stream;
    }
}
