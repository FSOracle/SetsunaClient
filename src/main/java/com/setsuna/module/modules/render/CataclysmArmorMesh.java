package com.setsuna.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.render.CataclysmModelDefinition;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Quaternionf;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;

/** Native cube geometry with immutable local poses for deferred rendering. */
final class CataclysmArmorMesh {
    enum Bone { HEAD, HAT, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG }

    private final EnumMap<Bone, ModelPart> parts = new EnumMap<>(Bone.class);
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    private CataclysmArmorMesh(CataclysmModelDefinition definition) {
        ModelPart root = definition.bakeRoot().getChild("root");
        for (Bone bone : Bone.values()) {
            ModelPart part = root.getChild(bone.name().toLowerCase(java.util.Locale.ROOT));
            // The posed vanilla bone supplies its entire current transform. The
            // source mesh remains in that bone's local space, including children.
            part.loadPose(PartPose.ZERO);
            parts.put(bone, part);
        }
        ModelPart body = parts.get(Bone.BODY);
        if (body.hasChild("left_wing")) {
            // Hide these only in the static body tree. Separate immutable wing
            // copies receive their animated transform through the submitted pose.
            body.getChild("left_wing").visible = false;
            body.getChild("right_wing").visible = false;
            ModelPart wingBody = definition.bakeRoot().getChild("root").getChild("body");
            leftWing = wingBody.getChild("left_wing");
            rightWing = wingBody.getChild("right_wing");
            leftWing.loadPose(PartPose.ZERO);
            rightWing.loadPose(PartPose.ZERO);
        } else {
            leftWing = rightWing = null;
        }
    }

    static CataclysmArmorMesh parse(InputStream stream) throws IOException {
        return new CataclysmArmorMesh(CataclysmModelDefinition.parse(stream));
    }

    boolean has(EquipmentSlot slot) {
        return !bones(slot).isEmpty() && bones(slot).stream()
                .allMatch(bone -> parts.containsKey(bone) && !parts.get(bone).isEmpty());
    }

    boolean hasWings() {
        return leftWing != null && rightWing != null;
    }

    ModelPart part(Bone bone) {
        return parts.get(bone);
    }

    void submit(EquipmentSlot slot, HumanoidModel<?> model, HumanoidRenderState state,
                PoseStack poseStack, OrderedSubmitNodeCollector collector,
                RenderType renderType, int light, boolean foil) {
        poseStack.pushPose();
        try {
            model.root().translateAndRotate(poseStack);
            for (Bone bone : bones(slot)) {
                poseStack.pushPose();
                try {
                    posedPart(model, bone).translateAndRotate(poseStack);
                    submitPart(parts.get(bone), poseStack, collector, renderType, light, foil, state.outlineColor);
                    if (bone == Bone.BODY && slot == EquipmentSlot.CHEST && hasWings()) {
                        submitWing(leftWing, true, state, poseStack, collector, renderType, light, foil);
                        submitWing(rightWing, false, state, poseStack, collector, renderType, light, foil);
                    }
                } finally {
                    poseStack.popPose();
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void submitWing(ModelPart wing, boolean left, HumanoidRenderState state,
                                   PoseStack poses, OrderedSubmitNodeCollector collector,
                                   RenderType renderType, int light, boolean foil) {
        poses.pushPose();
        try {
            // Cataclysm's original pivots differ from vanilla elytra (6/-5, 0, 1.5).
            // Modern render state already supplies the interpolated flight angles.
            poses.translate((left ? 6.0F : -5.0F) / 16.0F,
                    (state.isCrouching ? 3.0F : 0.0F) / 16.0F, 1.5F / 16.0F);
            float sign = left ? 1.0F : -1.0F;
            poses.mulPose(new Quaternionf().rotationZYX(sign * state.elytraRotZ,
                    sign * state.elytraRotY, state.elytraRotX));
            submitPart(wing, poses, collector, renderType, light, foil, state.outlineColor);
        } finally {
            poses.popPose();
        }
    }

    private static void submitPart(ModelPart part, PoseStack poses, OrderedSubmitNodeCollector collector,
                                   RenderType renderType, int light, boolean foil, int outline) {
        collector.submitModelPart(part, poses, renderType, light, OverlayTexture.NO_OVERLAY,
                null, false, foil, 0xFFFFFFFF, null, outline);
    }

    static List<Bone> bones(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> List.of(Bone.HEAD, Bone.HAT);
            case CHEST -> List.of(Bone.BODY, Bone.LEFT_ARM, Bone.RIGHT_ARM);
            case LEGS -> List.of(Bone.BODY, Bone.LEFT_LEG, Bone.RIGHT_LEG);
            case FEET -> List.of(Bone.LEFT_LEG, Bone.RIGHT_LEG);
            default -> List.of();
        };
    }

    private static ModelPart posedPart(HumanoidModel<?> model, Bone bone) {
        return switch (bone) {
            case HEAD, HAT -> model.head;
            case BODY -> model.body;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
        };
    }
}
