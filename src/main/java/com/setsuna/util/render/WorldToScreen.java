package com.setsuna.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4d;
import org.joml.Vector4f;

/**
 * Projects world-space positions into screen space. Returned coordinates are in
 * raw window pixels (top-left origin); divide by {@code mc.getWindow().getGuiScale()}
 * to convert into the GUI-scaled space that the Skija overlay draws in.
 */
public final class WorldToScreen {

    private static final Minecraft mc = Minecraft.getInstance();

    private WorldToScreen() {
    }

    public static Vector3f getWorldPositionToScreen(Vec3 pos) {
        final Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();

        CameraRenderState cameraState = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Matrix4f viewProjectionMatrix = new Matrix4f(cameraState.projectionMatrix).mul(cameraState.viewRotationMatrix);

        final int[] viewport = new int[]{0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()};
        final Vector3f out = new Vector3f();

        viewProjectionMatrix.project((float) (pos.x - cameraPos.x),
                (float) (pos.y - cameraPos.y), (float) (pos.z - cameraPos.z), viewport, out);
        out.y = viewport[3] - out.y;

        return out;
    }

    public static Vector4d getEntityPositionsOn2D(LivingEntity target, float tickDelta) {
        final Vec3 position = interpolate(target, tickDelta);
        final float width = target.getBbWidth() / 2f;
        final float height = target.getBbHeight() + (target.isCrouching() ? 0.1f : 0.2f);

        final AABB boundingBox = new AABB(
                position.x - width, position.y, position.z - width,
                position.x + width, position.y + height, position.z + width
        );

        return projectAbsoluteAABBOn2D(boundingBox);
    }

    public static Vector4d projectAbsoluteAABBOn2D(AABB absoluteBoundingBox) {
        final int[] viewport = new int[]{0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()};
        CameraRenderState cameraState = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Matrix4f viewProjectionMatrix = new Matrix4f(cameraState.projectionMatrix).mul(cameraState.viewRotationMatrix);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();

        final Vector4d projection = projectEntity(viewport, viewProjectionMatrix, absoluteBoundingBox, cameraPos);
        if (projection == null) return null;

        double guiScale = mc.getWindow().getGuiScale();
        projection.x /= guiScale;
        projection.y /= guiScale;
        projection.z /= guiScale;
        projection.w /= guiScale;

        return projection;
    }

    public static Vector4d projectEntity(final int[] viewport, final Matrix4f matrix, final AABB absoluteBoundingBox, final Vec3 cameraPos) {
        final Vector4f out = new Vector4f();
        Vector4d result = null;

        // Keep the existing float-before-subtraction rounding at large world coordinates.
        final float minX = (float) absoluteBoundingBox.minX - (float) cameraPos.x;
        final float minY = (float) absoluteBoundingBox.minY - (float) cameraPos.y;
        final float minZ = (float) absoluteBoundingBox.minZ - (float) cameraPos.z;
        final float maxX = (float) absoluteBoundingBox.maxX - (float) cameraPos.x;
        final float maxY = (float) absoluteBoundingBox.maxY - (float) cameraPos.y;
        final float maxZ = (float) absoluteBoundingBox.maxZ - (float) cameraPos.z;

        for (int i = 0; i < 8; i++) {
            matrix.project((i & 1) == 0 ? minX : maxX,
                    (i & 2) == 0 ? minY : maxY, (i & 4) == 0 ? minZ : maxZ, viewport, out);
            out.y = viewport[3] - out.y;

            if (!Float.isFinite(out.x) || !Float.isFinite(out.y) || !Float.isFinite(out.z)) {
                continue;
            }
            if (out.z < 0.0f || out.z > 1.0f) {
                continue;
            }

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

    public static Vector4d projectEntity(final int[] viewport, final Matrix4f matrix, final AABB absoluteBoundingBox) {
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
        return projectEntity(viewport, matrix, absoluteBoundingBox, cameraPos);
    }

    public static Vec3 interpolate(LivingEntity entity, float tickDelta) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }
}
