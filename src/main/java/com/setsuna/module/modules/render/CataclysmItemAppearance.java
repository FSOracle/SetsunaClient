package com.setsuna.module.modules.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.render.CataclysmModelDefinition;
import com.setsuna.render.CataclysmRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class CataclysmItemAppearance implements SpecialModelRenderer<CataclysmItemModel.Frame> {
    interface Source { InputStream open(Identifier resource) throws IOException; }

    private static final Set<String> UNCENTERED = Set.of("black_steel_targe", "azure_sea_shield",
            "coral_spear", "coral_bardiche");
    private static final Set<String> ANIMATED = Set.of("cursed_bow", "wrath_of_the_desert",
            "meat_shredder", "laser_gatling");
    private static final Set<String> ENTITY_MATERIAL = Set.of("ceraunus", "gauntlet_of_guard",
            "gauntlet_of_bulwark", "gauntlet_of_maelstrom", "wither_assault_shoulder_weapon",
            "void_assault_shoulder_weapon", "void_forge", "brontes", "infernal_forge", "meat_shredder", "laser_gatling");
    final String id;
    final boolean flat;
    final boolean animated;
    final boolean blockLight;
    final Identifier texture;
    final List<Identifier> emissive;
    final CataclysmItemModel model;
    final JsonObject sourceItem;
    private final Map<String, Map<ItemDisplayContext, ItemTransform>> transforms = new HashMap<>();
    private final Vector3fc[] extents;

    static Identifier resource(String id, String filename) {
        return Identifier.fromNamespaceAndPath("setsuna", "cataclysm/items/" + id + "/" + filename);
    }

    CataclysmItemAppearance(String id, JsonObject metadata, Source source) throws IOException {
        this.id = id;
        this.flat = !metadata.has("mesh");
        this.animated = ANIMATED.contains(id);
        this.texture = resource(id, metadata.get("texture").getAsString());
        List<Identifier> layers = new ArrayList<>();
        for (JsonElement layer : metadata.getAsJsonArray("emissive")) layers.add(resource(id, layer.getAsString()));
        this.emissive = List.copyOf(layers);
        sourceItem = readJson(source.open(resource(id, "source_item.json")));
        blockLight = !sourceItem.has("gui_light") || !sourceItem.get("gui_light").getAsString().equals("front");
        transforms.put("", parseTransforms(sourceItem));
        if (metadata.has("variants")) {
            for (Map.Entry<String, JsonElement> entry : metadata.getAsJsonObject("variants").entrySet()) {
                JsonObject variant = readJson(source.open(resource(id, entry.getValue().getAsString())));
                transforms.put(entry.getKey(), parseTransforms(variant));
            }
        }
        // A missing layer must fall back to the real item, never erase it or show missing-texture purple.
        try (InputStream ignored = source.open(texture)) { }
        for (Identifier layer : emissive) try (InputStream ignored = source.open(layer)) { }
        if (flat) {
            model = null;
            extents = new Vector3fc[0];
        } else {
            byte[] meshBytes;
            try (InputStream stream = source.open(resource(id, "model.mesh.json"))) { meshBytes = stream.readAllBytes(); }
            JsonObject mesh = readJson(new ByteArrayInputStream(meshBytes));
            if (!hasSurface(mesh)) throw new IOException("Empty Cataclysm item geometry: " + id);
            var baked = CataclysmModelDefinition.parse(new ByteArrayInputStream(meshBytes)).bake();
            model = new CataclysmItemModel(id, baked, mesh);
            extents = captureExtents(new CataclysmItemModel.Frame(0, 0, false));
            if (extents.length == 0) throw new IOException("Missing Cataclysm item extents: " + id);
            for (Vector3fc point : extents) {
                if (!Float.isFinite(point.x()) || !Float.isFinite(point.y()) || !Float.isFinite(point.z())) {
                    throw new IOException("Non-finite Cataclysm item geometry: " + id);
                }
            }
        }
    }

    private static boolean hasSurface(JsonObject mesh) {
        for (JsonElement element : mesh.getAsJsonArray("bones")) {
            for (JsonElement cube : element.getAsJsonObject().getAsJsonArray("cubes")) {
                JsonArray values = cube.getAsJsonArray();
                if (values.size() != 12) continue;
                int dimensions = 0;
                for (int axis = 0; axis < 3; axis++) {
                    if (values.get(5 + axis).getAsFloat() + 2 * values.get(8 + axis).getAsFloat() > 0.00001F) dimensions++;
                }
                if (dimensions >= 2) return true;
            }
        }
        return false;
    }

    static JsonObject readJson(InputStream stream) throws IOException {
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("Invalid Cataclysm item resource", error);
        }
    }

    String variant(boolean using, float pull) {
        String result = "";
        if (!sourceItem.has("overrides")) return result;
        for (JsonElement element : sourceItem.getAsJsonArray("overrides")) {
            JsonObject override = element.getAsJsonObject();
            boolean matches = true;
            for (Map.Entry<String, JsonElement> predicate : override.getAsJsonObject("predicate").entrySet()) {
                String key = predicate.getKey();
                key = key.substring(key.indexOf(':') + 1);
                float actual = switch (key) {
                    case "blocking", "throwing", "pulling" -> using ? 1 : 0;
                    case "pull" -> pull;
                    default -> Float.NEGATIVE_INFINITY;
                };
                if (actual < predicate.getValue().getAsFloat()) matches = false;
            }
            if (matches && transforms.containsKey(override.get("model").getAsString())) {
                result = override.get("model").getAsString();
            }
        }
        return result;
    }

    ItemTransform transform(ItemDisplayContext context, String variant) {
        Map<ItemDisplayContext, ItemTransform> display = transforms.getOrDefault(variant, transforms.get(""));
        ItemTransform result = display.get(context);
        if (result == null && context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            result = display.get(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
        }
        if (result == null && context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            result = display.get(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        }
        return result == null ? ItemTransform.NO_TRANSFORM : result;
    }

    static Map<ItemDisplayContext, ItemTransform> parseTransforms(JsonObject source) {
        Map<ItemDisplayContext, ItemTransform> result = new EnumMap<>(ItemDisplayContext.class);
        if (!source.has("display")) return result;
        JsonObject display = source.getAsJsonObject("display");
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            if (!display.has(context.getSerializedName())) continue;
            JsonObject data = display.getAsJsonObject(context.getSerializedName());
            Vector3f rotation = vector(data, "rotation", 0);
            Vector3f translation = vector(data, "translation", 0).div(16);
            Vector3f scale = vector(data, "scale", 1);
            translation.set(Math.clamp(translation.x, -5, 5), Math.clamp(translation.y, -5, 5), Math.clamp(translation.z, -5, 5));
            scale.set(Math.clamp(scale.x, -4, 4), Math.clamp(scale.y, -4, 4), Math.clamp(scale.z, -4, 4));
            result.put(context, new ItemTransform(rotation, translation, scale));
        }
        return result;
    }

    private static Vector3f vector(JsonObject data, String key, float fallback) {
        if (!data.has(key)) return new Vector3f(fallback);
        JsonArray values = data.getAsJsonArray(key);
        if (values.size() != 3) throw new IllegalArgumentException("Expected a three-component " + key);
        Vector3f result = new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
        if (!result.isFinite()) throw new IllegalArgumentException("Non-finite item " + key);
        return result;
    }

    private void sourceTransform(PoseStack pose) {
        if (!UNCENTERED.contains(id)) pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(1, -1, -1);
    }

    @Override
    public void submit(CataclysmItemModel.Frame frame, PoseStack pose, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outline) {
        if (model == null) return;
        pose.pushPose();
        try {
            sourceTransform(pose);
            collector.submitModel(model, frame, pose, ENTITY_MATERIAL.contains(id)
                            ? RenderTypes.entityCutout(texture) : RenderTypes.armorCutoutNoCull(texture), light, overlay, -1,
                    null, outline, null);
            for (Identifier layer : emissive) {
                collector.submitModel(model, frame, pose, layer.getPath().endsWith("_ghost.png")
                                ? CataclysmRenderTypes.ghost(layer) : RenderTypes.eyes(layer),
                        0xF000F0, overlay, -1, null, outline, null);
            }
            if (foil) {
                collector.submitModel(model, frame, pose, RenderTypes.entityGlint(), light, overlay, -1,
                        null, outline, null);
            }
        } finally {
            pose.popPose();
        }
    }

    Vector3fc[] extents() { return extents; }

    Vector3fc[] extents(CataclysmItemModel.Frame frame) {
        return animated ? captureExtents(frame) : extents;
    }

    private Vector3fc[] captureExtents(CataclysmItemModel.Frame frame) {
        model.setupAnim(frame);
        List<Vector3fc> points = new ArrayList<>();
        PoseStack pose = new PoseStack();
        sourceTransform(pose);
        model.root().getExtentsForGui(pose, point -> points.add(new Vector3f(point)));
        return points.toArray(Vector3fc[]::new);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        for (Vector3fc point : extents) consumer.accept(point);
    }

    @Override
    public CataclysmItemModel.Frame extractArgument(ItemStack stack) {
        return new CataclysmItemModel.Frame(0, 0, false);
    }
}
