package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.util.client.ViewBobbingSuppressor;
import com.setsuna.util.render.Render3DUtils;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;

/**
 * Draws lines from the crosshair to living entities, coloured by category and
 * friend status. Rendered in-world against the camera-relative pose.
 */
public final class Tracers extends Module {

    public static final Tracers INSTANCE = new Tracers();

    public enum Target {
        PLAYERS,
        MOBS,
        ANIMALS,
        ALL
    }

    public final EnumSetting<Target> target = add(new EnumSetting<>("TargetHUD", Target.PLAYERS));
    public final DoubleSetting range = add(new DoubleSetting("Range", 128.0, 4.0, 512.0, 1.0));
    public final DoubleSetting lineWidth = add(new DoubleSetting("Line Width", 1.5, 0.5, 5.0, 0.5));
    public final ColorSetting playerColor = add(new ColorSetting("Player Color", new Color(100, 200, 255, 200)));
    public final ColorSetting friendColor = add(new ColorSetting("Friend Color", new Color(80, 255, 80, 200)));
    public final ColorSetting mobColor = add(new ColorSetting("Mob Color", new Color(255, 80, 80, 200)));
    public final ColorSetting animalColor = add(new ColorSetting("Animal Color", new Color(255, 220, 80, 200)));
    public final BooleanSetting rainbow = add(new BooleanSetting("Rainbow", false));

    private Tracers() {
        super("Tracers", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        ViewBobbingSuppressor.release("tracers");
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer()) {
            return;
        }
        ViewBobbingSuppressor.acquire("tracers");

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double maxDistSq = range.get() * range.get();

        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        Vector3f eyeVector = eyeVector(camera.xRot(), camera.yRot());

        PoseStack.Pose entry = event.getPoseStack().last();
        Matrix4f matrix = entry.pose();
        float thickness = lineWidth.get().floatValue();

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) {
                continue;
            }
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!living.isAlive() || living.isDeadOrDying()) {
                continue;
            }
            if (!shouldTarget(entity)) {
                continue;
            }
            if (mc.player.distanceToSqr(entity) > maxDistSq) {
                continue;
            }

            Color color = resolveColor(entity);

            float x = (float) (Mth.lerp(partialTick, entity.xOld, entity.getX()) - cameraPos.x);
            float y = (float) (Mth.lerp(partialTick, entity.yOld, entity.getY()) - cameraPos.y);
            float z = (float) (Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cameraPos.z);
            float topY = y + entity.getBbHeight();

            addLine(buffer, matrix, entry, eyeVector.x, eyeVector.y, eyeVector.z, x, y, z, color, thickness);
            addLine(buffer, matrix, entry, x, y, z, x, topY, z, color, thickness);
        }

        MeshData meshData = buffer.build();
        if (meshData != null) {
            Render3DUtils.LINES.draw(meshData);
        }
    }

    private Vector3f eyeVector(float xRot, float yRot) {
        Vector3f vector = new Vector3f(0.0f, 0.0f, 1.0f);
        vector.rotateX((float) Math.toRadians(-xRot));
        vector.rotateY((float) Math.toRadians(-yRot));
        return vector;
    }

    private void addLine(BufferBuilder buffer, Matrix4f matrix, PoseStack.Pose entry,
                         float x1, float y1, float z1, float x2, float y2, float z2,
                         Color color, float thickness) {
        Vector3f normal = new Vector3f(x2 - x1, y2 - y1, z2 - z1).normalize();
        buffer.addVertex(matrix, x1, y1, z1)
                .setColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .setNormal(entry, normal.x, normal.y, normal.z)
                .setLineWidth(thickness);
        buffer.addVertex(matrix, x2, y2, z2)
                .setColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .setNormal(entry, normal.x, normal.y, normal.z)
                .setLineWidth(thickness);
    }

    private boolean shouldTarget(Entity entity) {
        return switch (target.get()) {
            case PLAYERS -> entity instanceof Player;
            case MOBS -> entity instanceof Monster;
            case ANIMALS -> entity instanceof Animal;
            case ALL -> entity instanceof LivingEntity;
        };
    }

    private Color resolveColor(Entity entity) {
        if (rainbow.get()) {
            float hue = (System.currentTimeMillis() % 3000L) / 3000.0f;
            return Color.getHSBColor(hue, 0.9f, 1.0f);
        }
        if (entity instanceof Player player && FriendManager.INSTANCE.isFriend(player)) {
            return friendColor.get();
        }
        if (entity instanceof Monster) {
            return mobColor.get();
        }
        if (entity instanceof Animal) {
            return animalColor.get();
        }
        return playerColor.get();
    }
}
