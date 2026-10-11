package com.setsuna.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Original model-pixel cubes, UVs and local bone poses, baked by Minecraft itself. */
public final class CataclysmModelDefinition {
    public record Baked(ModelPart root, Map<String, ModelPart> bones) { }

    private final JsonObject data;

    private CataclysmModelDefinition(JsonObject data) {
        this.data = data;
    }

    public static CataclysmModelDefinition parse(InputStream input) throws IOException {
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            int width = data.get("texture_width").getAsInt();
            int height = data.get("texture_height").getAsInt();
            if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                    || data.getAsJsonArray("bones").isEmpty()) {
                throw new IOException("Invalid Cataclysm model dimensions or empty skeleton");
            }
            return new CataclysmModelDefinition(data);
        } catch (RuntimeException error) {
            throw new IOException("Invalid Cataclysm cube model", error);
        }
    }

    public ModelPart bakeRoot() {
        return bake().root();
    }

    public Baked bake() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, JsonObject> nodes = new LinkedHashMap<>();
        for (JsonElement element : data.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String id = bone.get("id").getAsString();
            if (nodes.put(id, bone) != null) throw new IllegalArgumentException("Duplicate bone " + id);
        }
        Map<String, PartDefinition> parts = new HashMap<>();
        for (String id : nodes.keySet()) addBone(id, nodes, parts, mesh.getRoot(), new HashSet<>());
        ModelPart root = LayerDefinition.create(mesh, data.get("texture_width").getAsInt(),
                data.get("texture_height").getAsInt()).bakeRoot();
        Map<String, ModelPart> bones = new HashMap<>();
        for (String id : nodes.keySet()) findBone(id, nodes, bones, root);
        return new Baked(root, Map.copyOf(bones));
    }

    private static PartDefinition addBone(String id, Map<String, JsonObject> nodes,
                                          Map<String, PartDefinition> parts, PartDefinition root,
                                          Set<String> ancestors) {
        if (parts.containsKey(id)) return parts.get(id);
        JsonObject bone = nodes.get(id);
        if (bone == null || !ancestors.add(id)) throw new IllegalArgumentException("Invalid bone parent " + id);
        String parentId = parent(bone);
        PartDefinition parent = parentId == null ? root : addBone(parentId, nodes, parts, root, ancestors);
        CubeListBuilder builder = CubeListBuilder.create();
        for (JsonElement element : bone.getAsJsonArray("cubes")) {
            JsonArray c = element.getAsJsonArray();
            if (c.size() != 12) throw new IllegalArgumentException("Invalid cube in " + id);
            builder.texOffs(c.get(0).getAsInt(), c.get(1).getAsInt()).mirror(c.get(11).getAsBoolean())
                    .addBox(number(c, 2), number(c, 3), number(c, 4), number(c, 5), number(c, 6),
                            number(c, 7), new CubeDeformation(number(c, 8), number(c, 9), number(c, 10)));
        }
        JsonArray pivot = bone.getAsJsonArray("pivot"), rotation = bone.getAsJsonArray("rotation");
        String name = bone.get("name").getAsString();
        if (parent.getChildren().stream().anyMatch(entry -> entry.getKey().equals(name))) {
            throw new IllegalArgumentException("Duplicate child name " + name);
        }
        PartDefinition result = parent.addOrReplaceChild(name, builder, PartPose.offsetAndRotation(
                number(pivot, 0), number(pivot, 1), number(pivot, 2),
                number(rotation, 0), number(rotation, 1), number(rotation, 2)));
        parts.put(id, result);
        ancestors.remove(id);
        return result;
    }

    private static ModelPart findBone(String id, Map<String, JsonObject> nodes,
                                       Map<String, ModelPart> parts, ModelPart root) {
        if (parts.containsKey(id)) return parts.get(id);
        JsonObject bone = nodes.get(id);
        String parentId = parent(bone);
        ModelPart parent = parentId == null ? root : findBone(parentId, nodes, parts, root);
        ModelPart result = parent.getChild(bone.get("name").getAsString());
        parts.put(id, result);
        return result;
    }

    private static String parent(JsonObject bone) {
        JsonElement parent = bone.get("parent");
        return parent == null || parent.isJsonNull() ? null : parent.getAsString();
    }

    private static float number(JsonArray values, int index) {
        float value = values.get(index).getAsFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite model coordinate");
        return value;
    }
}
