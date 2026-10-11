package com.setsuna.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.Setsuna;
import com.setsuna.module.modules.render.Cosmetics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;

import java.util.EnumMap;
import java.util.Map;

/** Uses the player's posed bones and the original Curios model subtrees for each wearable slot. */
public final class CataclysmAccessoryLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final boolean slim;
    private final Map<CataclysmAccessory, ModelPart> models = new EnumMap<>(CataclysmAccessory.class);

    public CataclysmAccessoryLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, boolean slim) {
        super(parent);
        this.slim = slim;
    }

    @Override
    public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                       AvatarRenderState state, float yRot, float xRot) {
        Cosmetics settings = Cosmetics.INSTANCE;
        if (!settings.shouldRender(state)) return;
        EquipmentVisualPriority.Snapshot visibility = EquipmentVisualPriority.resolve(state);
        poses.pushPose();
        try {
            PlayerModel player = getParentModel();
            player.root().translateAndRotate(poses);
            if (visibility.allowsArmorSlot(EquipmentSlot.CHEST)) {
                submitBody(settings.necklace.get().accessory, player.body, poses, collector, light);
            }
            if (visibility.allowsArmorSlot(EquipmentSlot.LEGS)) {
                submitBody(settings.waistAccessory.get().accessory, player.body, poses, collector, light);
                submitBody(settings.talisman.get().accessory, player.body, poses, collector, light);
            }
            if (visibility.allowsArmorSlot(EquipmentSlot.FEET)) {
                CataclysmAccessory footwear = settings.footwear.get().accessory;
                submitLimb(footwear, "left_leg", "left_boot", player.leftLeg, poses, collector, light);
                submitLimb(footwear, "right_leg", "right_boot", player.rightLeg, poses, collector, light);
            }
            for (HumanoidArm arm : HumanoidArm.values()) {
                if (visibility.allowsHand(arm)) {
                    CataclysmAccessory accessory = arm == state.mainArm
                            ? settings.mainHandAccessory.get().accessory : settings.offHandAccessory.get().accessory;
                    submitArm(accessory, arm, poses, collector, light);
                }
            }
        } finally {
            poses.popPose();
        }
    }

    /** Called after vanilla sets up its first-person arm, preserving that arm's exact pose. */
    public void submitFirstPerson(HumanoidArm arm, PoseStack poses, SubmitNodeCollector collector, int light) {
        var client = Setsuna.mc();
        Cosmetics settings = Cosmetics.INSTANCE;
        if (!settings.isEnabled() || client == null || client.player == null
                || client.player.isInvisible() || client.player.isSpectator()) return;
        boolean mainHand = arm == client.player.getMainArm();
        if (!EquipmentVisualPriority.allowsFirstPersonHand(arm)) return;
        CataclysmAccessory accessory = mainHand
                ? settings.mainHandAccessory.get().accessory : settings.offHandAccessory.get().accessory;
        submitArm(accessory, arm, poses, collector, light);
    }

    private void submitArm(CataclysmAccessory accessory, HumanoidArm arm, PoseStack poses,
                           SubmitNodeCollector collector, int light) {
        if (accessory == null) return;
        String side = arm == HumanoidArm.LEFT ? "left" : "right";
        ModelPart bone = arm == HumanoidArm.LEFT ? getParentModel().leftArm : getParentModel().rightArm;
        submitLimb(accessory, side + "_arm", side + "_" + accessory.child, bone, poses, collector, light);
    }

    private void submitBody(CataclysmAccessory accessory, ModelPart bone, PoseStack poses,
                            SubmitNodeCollector collector, int light) {
        if (accessory != null) submitLimb(accessory, accessory.bodyBone, accessory.child, bone, poses, collector, light);
    }

    private void submitLimb(CataclysmAccessory accessory, String boneName, String childName,
                            ModelPart posedBone, PoseStack poses, SubmitNodeCollector collector, int light) {
        if (accessory == null) return;
        ModelPart root = models.computeIfAbsent(accessory, style -> CataclysmAccessoryModels.bake(style, slim));
        ModelPart geometry = root.getChild(boneName).getChild(childName);
        poses.pushPose();
        try {
            // The child is in bone-local coordinates. Do not subtract the animated bone's pivot.
            posedBone.translateAndRotate(poses);
            collector.submitModelPart(geometry, poses, RenderTypes.entityCutout(accessory.texture()),
                    light, OverlayTexture.NO_OVERLAY, null);
            if (accessory.glowTexture() != null) {
                collector.submitModelPart(geometry, poses, RenderTypes.eyes(accessory.glowTexture()),
                        LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            }
        } finally {
            poses.popPose();
        }
    }
}
