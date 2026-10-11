package com.setsuna.util.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4d;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldToScreenTest {

    @Test
    void retainsViewportOffsetsAndTopLeftOrigin() {
        int[] viewport = {20, 30, 800, 600};
        Vector4d bounds = WorldToScreen.projectEntity(viewport, new Matrix4f(),
                new AABB(-1, -1, 0, 1, 1, 0), Vec3.ZERO);

        assertNotNull(bounds);
        assertEquals(new Vector4d(20, -30, 820, 570), bounds);
    }

    @Test
    void keepsNearAndFarBoundariesAndRejectsClippedOrNonFinitePoints() {
        int[] viewport = {0, 0, 1920, 1080};
        Matrix4f identity = new Matrix4f();
        assertNotNull(WorldToScreen.projectEntity(viewport, identity,
                new AABB(-1, -1, -1, 1, 1, -1), Vec3.ZERO));
        assertNotNull(WorldToScreen.projectEntity(viewport, identity,
                new AABB(-1, -1, 1, 1, 1, 1), Vec3.ZERO));
        assertNull(WorldToScreen.projectEntity(viewport, identity,
                new AABB(-1, -1, 1.01, 1, 1, 2), Vec3.ZERO));
        assertNull(WorldToScreen.projectEntity(viewport, new Matrix4f().zero(),
                new AABB(-1, -1, -1, 1, 1, 1), Vec3.ZERO));
    }

    @Test
    void matchesPerspectiveProjectionAtCameraAndClippingBoundaries() {
        int[] viewport = {0, 0, 2560, 1440};
        Matrix4f perspective = new Matrix4f().perspective((float) Math.toRadians(70),
                16.0F / 9.0F, 0.05F, 256.0F);
        AABB[] boxes = {
                new AABB(-0.5, -1, -4, 0.5, 1, -3),
                new AABB(-0.5, -1, 3, 0.5, 1, 4),
                new AABB(-0.5, -1, -0.1, 0.5, 1, 0.1),
                new AABB(-0.5, -1, -0.05, 0.5, 1, -0.05),
                new AABB(-0.5, -1, -256, 0.5, 1, -256),
                new AABB(-0.5, -1, -300, 0.5, 1, -280)
        };
        for (AABB box : boxes) {
            assertMatchesLegacy(viewport, perspective, box, Vec3.ZERO);
        }
        assertNull(WorldToScreen.projectEntity(viewport, perspective, boxes[1], Vec3.ZERO));
    }

    @Test
    void preservesFloatRoundingNearWorldBorder() {
        int[] viewport = {0, 0, 1280, 720};
        Matrix4f perspective = new Matrix4f().perspective((float) Math.toRadians(90),
                16.0F / 9.0F, 0.05F, 1024.0F);
        for (double coordinate : new double[]{-29_999_999.75, -16_777_216.25,
                16_777_216.25, 29_999_999.75}) {
            Vec3 camera = new Vec3(coordinate, 64.125, coordinate);
            AABB box = new AABB(coordinate - 0.3, 64, coordinate - 8.3,
                    coordinate + 0.3, 65.8, coordinate - 7.7);
            assertMatchesLegacy(viewport, perspective, box, camera);
        }
    }

    @Test
    void matchesLegacyAcrossViewportCameraAndMatrixChanges() {
        Random random = new Random(0x5E75A);
        for (int index = 0; index < 1000; index++) {
            int[] viewport = {random.nextInt(200), random.nextInt(200),
                    1 + random.nextInt(3840), 1 + random.nextInt(2160)};
            Matrix4f matrix = new Matrix4f().perspective(
                            0.4F + random.nextFloat() * 2.0F,
                            viewport[2] / (float) viewport[3], 0.01F, 512.0F)
                    .rotateXYZ(random.nextFloat(), random.nextFloat(), random.nextFloat());
            double base = index % 2 == 0 ? 29_999_900.0 : 0;
            Vec3 camera = new Vec3(base + random.nextDouble() * 100,
                    random.nextDouble() * 320, base + random.nextDouble() * 100);
            double x = camera.x + random.nextDouble() * 100 - 50;
            double y = camera.y + random.nextDouble() * 100 - 50;
            double z = camera.z + random.nextDouble() * 100 - 50;
            AABB box = new AABB(x, y, z, x + 0.1 + random.nextDouble() * 3,
                    y + 0.1 + random.nextDouble() * 3, z + 0.1 + random.nextDouble() * 3);
            assertMatchesLegacy(viewport, matrix, box, camera);
        }
    }

    private static void assertMatchesLegacy(int[] viewport, Matrix4f matrix, AABB box, Vec3 camera) {
        Vector4d expected = legacyProjection(viewport, matrix, box, camera);
        Vector4d actual = WorldToScreen.projectEntity(viewport, matrix, box, camera);
        assertEquals(expected, actual, "Projection must be bit-for-bit unchanged");
    }

    /** Frozen pre-optimization projection used as the numerical regression oracle. */
    private static Vector4d legacyProjection(int[] viewport, Matrix4f matrix, AABB box, Vec3 camera) {
        Vector4f out = new Vector4f();
        Vector4d result = null;
        for (int i = 0; i < 8; i++) {
            Vector3f point = new Vector3f(
                    ((i & 1) == 0 ? (float) box.minX : (float) box.maxX) - (float) camera.x,
                    ((i & 2) == 0 ? (float) box.minY : (float) box.maxY) - (float) camera.y,
                    ((i & 4) == 0 ? (float) box.minZ : (float) box.maxZ) - (float) camera.z);
            matrix.project(point, viewport, out);
            out.y = viewport[3] - out.y;
            if (!Float.isFinite(out.x) || !Float.isFinite(out.y) || !Float.isFinite(out.z)
                    || out.z < 0.0F || out.z > 1.0F) continue;
            if (result == null) {
                result = new Vector4d(out.x, out.y, out.x, out.y);
            } else {
                result.x = Math.min(result.x, out.x);
                result.y = Math.min(result.y, out.y);
                result.z = Math.max(result.z, out.x);
                result.w = Math.max(result.w, out.y);
            }
        }
        return result;
    }
}
