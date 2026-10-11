package com.setsuna.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Immutable quad export from the editable Blockbench model, in head-local coordinates. */
final class CosmeticMesh {
    record Vertex(float x, float y, float z, float u, float v) { }
    record Normal(float x, float y, float z) { }
    record Quad(List<Vertex> vertices, Normal normal) { }

    private final Identifier texture;
    private final List<Quad> quads;

    private CosmeticMesh(Identifier texture, List<Quad> quads) {
        this.texture = texture;
        this.quads = List.copyOf(quads);
    }

    static CosmeticMesh parse(InputStream stream) throws IOException {
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("format_version").getAsInt() != 1) {
                throw new IOException("Unsupported cosmetic mesh format");
            }
            Identifier texture = Identifier.tryParse(root.get("texture").getAsString());
            if (texture == null) throw new IOException("Invalid cosmetic texture identifier");
            JsonArray faces = root.getAsJsonArray("quads");
            if (faces.isEmpty() || faces.size() > 32768) throw new IOException("Invalid cosmetic face count");
            List<Quad> quads = new ArrayList<>(faces.size());
            for (JsonElement element : faces) {
                JsonObject face = element.getAsJsonObject();
                JsonArray vertices = face.getAsJsonArray("vertices");
                if (vertices.size() != 4) throw new IOException("Cosmetic faces must have four vertices");
                List<Vertex> converted = new ArrayList<>(4);
                for (JsonElement item : vertices) {
                    JsonArray values = item.getAsJsonArray();
                    if (values.size() != 5) throw new IOException("Expected cosmetic position and UV");
                    // Blockbench: +Y up, +Z forward, one pixel = 1/16 block.
                    // Minecraft head: +Y down, -Z forward. This rotation preserves winding.
                    converted.add(new Vertex(number(values, 0) / 16.0F,
                            -number(values, 1) / 16.0F, -number(values, 2) / 16.0F,
                            number(values, 3), number(values, 4)));
                }
                JsonArray normal = face.getAsJsonArray("normal");
                if (normal.size() != 3) throw new IOException("Expected cosmetic face normal");
                float nx = number(normal, 0), ny = -number(normal, 1), nz = -number(normal, 2);
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (!Float.isFinite(length) || length < 0.00001F) {
                    throw new IOException("Invalid cosmetic face normal");
                }
                quads.add(new Quad(List.copyOf(converted), new Normal(nx / length, ny / length, nz / length)));
            }
            return new CosmeticMesh(texture, quads);
        } catch (RuntimeException error) {
            throw new IOException("Invalid cosmetic mesh", error);
        }
    }

    private static float number(JsonArray values, int index) throws IOException {
        float value = values.get(index).getAsFloat();
        if (!Float.isFinite(value)) throw new IOException("Non-finite cosmetic coordinate");
        return value;
    }

    Identifier texture() {
        return texture;
    }

    List<Quad> quads() {
        return quads;
    }

    void render(PoseStack.Pose pose, VertexConsumer output, int light) {
        for (Quad quad : quads) {
            Normal normal = quad.normal;
            for (Vertex vertex : quad.vertices) {
                output.addVertex(pose, vertex.x, vertex.y, vertex.z)
                        .setColor(0xFFFFFFFF).setUv(vertex.u, vertex.v)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, normal.x, normal.y, normal.z);
            }
        }
    }
}
