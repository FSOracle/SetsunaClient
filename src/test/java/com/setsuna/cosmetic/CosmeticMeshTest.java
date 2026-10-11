package com.setsuna.cosmetic;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticMeshTest {
    private static final String QUAD = """
            {"format_version":1,"texture":"setsuna:textures/cosmetics/bamboo_douli.png",
             "quads":[{"vertices":[[16,8,4,0,0],[0,8,4,1,0],[0,8,0,1,1],[16,8,0,0,1]],
                       "normal":[0,1,0]}]}
            """;

    @Test
    void convertsBlockbenchPixelsIntoThePlayerHeadCoordinateSystem() throws Exception {
        CosmeticMesh mesh = parse(QUAD);
        CosmeticMesh.Quad face = mesh.quads().getFirst();
        assertEquals(new CosmeticMesh.Vertex(1, -0.5F, -0.25F, 0, 0), face.vertices().getFirst());
        assertEquals(new CosmeticMesh.Normal(0, -1, -0.0F), face.normal());
        assertEquals("setsuna:textures/cosmetics/bamboo_douli.png", mesh.texture().toString());
        assertThrows(UnsupportedOperationException.class, () -> mesh.quads().clear());
        assertThrows(UnsupportedOperationException.class, () -> face.vertices().clear());
    }

    @Test
    void rejectsUnsupportedOrBrokenExports() {
        assertThrows(IOException.class, () -> parse(QUAD.replace("\"format_version\":1", "\"format_version\":2")));
        assertThrows(IOException.class, () -> parse(QUAD.replace("[0,1,0]", "[0,0,0]")));
        assertThrows(IOException.class, () -> parse(QUAD.replace("[16,8,4,0,0]", "[16,8,4,0]")));
        assertThrows(IOException.class, () -> parse(QUAD.replace("[16,8,4,0,0]", "[1e999,8,4,0,0]")));
        assertThrows(IOException.class, () -> parse("{}"));
    }

    @Test
    void bundledModelAndTextureAreUsableByTheClient() throws Exception {
        try (var source = getClass().getResourceAsStream("/assets/setsuna/cosmetics/bamboo_douli.json")) {
            assertNotNull(source, "The exported headwear mesh must be packaged in the client");
            CosmeticMesh mesh = CosmeticMesh.parse(source);
            assertTrue(mesh.quads().size() >= 32, "The model should contain the cone and its detail meshes");
            try (var texture = getClass().getResourceAsStream("/assets/" + mesh.texture().getNamespace()
                    + "/" + mesh.texture().getPath())) {
                assertNotNull(texture, "The mesh must reference a packaged texture");
                assertArrayEquals(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}, texture.readNBytes(8));
            }
            float minY = Float.POSITIVE_INFINITY;
            for (CosmeticMesh.Quad quad : mesh.quads()) {
                CosmeticMesh.Normal normal = quad.normal();
                assertEquals(1.0F, normal.x() * normal.x() + normal.y() * normal.y()
                        + normal.z() * normal.z(), 0.0001F);
                for (CosmeticMesh.Vertex vertex : quad.vertices()) {
                    assertTrue(vertex.u() >= 0 && vertex.u() <= 1);
                    assertTrue(vertex.v() >= 0 && vertex.v() <= 1);
                    minY = Math.min(minY, vertex.y());
                }
            }
            assertTrue(minY < -0.5F, "The crown must sit above the eight-pixel player head");
        }
    }

    private CosmeticMesh parse(String json) throws IOException {
        return CosmeticMesh.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
