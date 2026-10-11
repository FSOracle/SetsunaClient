package com.setsuna.module.modules.combat.killaura;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

/** Matrix transforms from Nilore's OldHitting module. */
public final class NiloreBlockAnimation {

    public enum Mode {
        Vanilla,
        Leaked,
        Slide,
        NewPushDown
    }

    private NiloreBlockAnimation() {
    }

    public static void apply(
            PoseStack poseStack,
            float progress,
            HumanoidArm arm,
            float equipProgress,
            Mode mode,
            float size,
            float speed,
            float yOffset,
            float handX,
            float handY,
            float handZ) {
        float scaledProgress = progress * speed;
        poseStack.translate(0.0F, yOffset, 0.0F);
        switch (mode) {
            case Vanilla -> applyVanilla(poseStack, scaledProgress, arm, equipProgress, size);
            case Leaked -> applyLeaked(poseStack, scaledProgress, equipProgress, size);
            case Slide -> applySlide(poseStack, scaledProgress, size);
            case NewPushDown -> applyNewPushDown(
                    poseStack, scaledProgress, equipProgress, size, handX, handY, handZ);
        }
    }

    private static void applyVanilla(
            PoseStack stack, float progress, HumanoidArm arm, float equipProgress, float size) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        translate(stack, side * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
        translate(stack, side * -0.1414214F, 0.08F, 0.1414214F);
        rotate(stack, -102.25F, 1.0F, 0.0F, 0.0F);
        rotate(stack, side * 13.365F, 0.0F, 1.0F, 0.0F);
        rotate(stack, side * 78.05F, 0.0F, 0.0F, 1.0F);
        double sinSquared = Math.sin(progress * progress * Math.PI);
        double sinSqrt = Math.sin(Math.sqrt(progress) * Math.PI);
        rotate(stack, (float) (sinSquared * -20.0D), 0.0F, 1.0F, 0.0F);
        rotate(stack, (float) (sinSqrt * -20.0D), 0.0F, 0.0F, 1.0F);
        rotate(stack, (float) (sinSqrt * -80.0D), 1.0F, 0.0F, 0.0F);
        scale(stack, size);
    }

    private static void applyLeaked(PoseStack stack, float progress, float equipProgress, float size) {
        stack.translate(0.56F, -0.52F, -0.71999997F);
        rotate(stack, 45.0F, 0.0F, 1.0F, 0.0F);
        float sinSquared = Mth.sin(progress * progress * (float) Math.PI);
        float sinSqrt = Mth.sin(Mth.sqrt(progress) * (float) Math.PI);
        rotate(stack, sinSquared * -20.0F, 0.0F, 1.0F, 0.0F);
        rotate(stack, sinSqrt * -20.0F, 0.0F, 0.0F, 1.0F);
        rotate(stack, sinSqrt * -80.0F, 1.0F, 0.0F, 0.0F);
        stack.scale(0.4F * size, 0.4F * size, 0.4F * size);

        stack.translate(-0.5F, 0.2F, 0.0F);
        rotate(stack, 30.0F, 0.0F, 1.0F, 0.0F);
        rotate(stack, -80.0F, 1.0F, 0.0F, 0.0F);
        rotate(stack, 60.0F, 0.0F, 1.0F, 0.0F);
        float pulse = Mth.sin(Mth.sqrt(progress) * (float) Math.PI) / 8.0F;
        stack.translate(0.008D, 0.24D, 0.03D);
        stack.translate(-0.16D, -0.25D, 0.0D);
        float pulseScale = (0.8F + pulse) * size;
        stack.scale(pulseScale, pulseScale, pulseScale);
        rotate(stack, -Mth.sin(Mth.sqrt(progress) * (float) Math.PI) * 20.0F,
                0.0F, 1.2F, -0.8F);
        rotate(stack, -Mth.sin(Mth.sqrt(progress) * (float) Math.PI) * 30.0F,
                1.0F, 0.0F, 0.0F);
        stack.scale(2.4F * size, 2.4F * size, 2.4F * size);
        rotate(stack, -38.4F, 0.0F, 1.0F, 0.0F);
        scale(stack, size);
    }

    private static void applySlide(PoseStack stack, float progress, float size) {
        float swing = Mth.sin(Mth.sqrt(progress) * (float) Math.PI);
        translate(stack, 0.648F, -0.55F, -0.71999997F);
        rotate(stack, 77.0F, 0.0F, 1.0F, 0.0F);
        rotate(stack, -10.0F, 0.0F, 0.0F, 1.0F);
        rotate(stack, -80.0F, 1.0F, 0.0F, 0.0F);
        rotate(stack, -swing * 20.0F, 1.0F, 0.0F, 0.0F);
        stack.scale(1.2F * size, 1.2F * size, 1.2F * size);
        scale(stack, size);
    }

    private static void applyNewPushDown(
            PoseStack stack,
            float progress,
            float equipProgress,
            float size,
            float handX,
            float handY,
            float handZ) {
        translate(stack, handX - 0.08F, handY + 0.12F, handZ);
        float swing = Mth.sin(Mth.sqrt(progress) * (float) Math.PI);
        translate(stack, 0.56F, -0.52F + equipProgress / 1.4F * -0.6F, -0.71999997F);
        rotate(stack, 45.0F, 0.0F, 1.0F, 0.0F);
        stack.scale(0.4F, 0.4F, 0.4F);
        rotate(stack, -swing * 65.0F / 2.0F, swing / 2.0F, 1.0F, 4.0F);
        rotate(stack, -swing * 60.0F, 1.0F, swing / 3.0F, -0.0F);
        translate(stack, -0.5F, 0.2F, 0.0F);
        rotate(stack, 30.0F, 0.0F, 1.0F, 0.0F);
        rotate(stack, -80.0F, 1.0F, 0.0F, 0.0F);
        rotate(stack, 60.0F, 0.0F, 1.0F, 0.0F);
        scale(stack, size);
    }

    private static void translate(PoseStack stack, float x, float y, float z) {
        stack.translate(x, y, z);
    }

    private static void rotate(PoseStack stack, float degrees, float x, float y, float z) {
        stack.mulPose(new Quaternionf().rotationAxis(
                degrees * ((float) Math.PI / 180.0F), x, y, z));
    }

    private static void scale(PoseStack stack, float size) {
        stack.scale(size, size, size);
    }
}
