package com.setsuna.util.render.esp;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class BillboardQuad {

    private BillboardQuad() {
    }

    static BufferBuilder begin() {
        return Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR
        );
    }

    static Vec3 relativeEntityPosition(LivingEntity entity, Camera camera, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()) - camera.position().x,
                Mth.lerp(partialTick, entity.yOld, entity.getY()) - camera.position().y,
                Mth.lerp(partialTick, entity.zOld, entity.getZ()) - camera.position().z
        );
    }

    static void faceCamera(PoseStack poses, Camera camera) {
        poses.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
        poses.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
    }

    static void emitSolid(BufferBuilder vertices, Matrix4f transform, float radius, int color) {
        vertices.addVertex(transform, -radius, radius, 0.0F).setUv(0.0F, 1.0F).setColor(color);
        vertices.addVertex(transform, radius, radius, 0.0F).setUv(1.0F, 1.0F).setColor(color);
        vertices.addVertex(transform, radius, -radius, 0.0F).setUv(1.0F, 0.0F).setColor(color);
        vertices.addVertex(transform, -radius, -radius, 0.0F).setUv(0.0F, 0.0F).setColor(color);
    }

    static void emitGradient(
            BufferBuilder vertices,
            Matrix4f transform,
            float radius,
            int bottomLeft,
            int topLeft,
            int topRight,
            int bottomRight
    ) {
        vertices.addVertex(transform, -radius, -radius, 0.0F).setUv(0.0F, 0.0F).setColor(bottomLeft);
        vertices.addVertex(transform, -radius, radius, 0.0F).setUv(0.0F, 1.0F).setColor(topLeft);
        vertices.addVertex(transform, radius, radius, 0.0F).setUv(1.0F, 1.0F).setColor(topRight);
        vertices.addVertex(transform, radius, -radius, 0.0F).setUv(1.0F, 0.0F).setColor(bottomRight);
    }
}
