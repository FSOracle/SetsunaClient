package com.setsuna.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.Setsuna;
import com.setsuna.module.modules.render.Cosmetics;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

import java.io.IOException;

/** Runs inside the player's posed model, before its body transform is popped. */
public final class CosmeticHeadwearLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier BAMBOO_DOULI = Identifier.fromNamespaceAndPath(
            Setsuna.MOD_ID, "cosmetics/bamboo_douli.json");

    // Entity renderers and their layers are recreated when resources reload.
    private CosmeticMesh mesh;
    private boolean loadAttempted;

    public CosmeticHeadwearLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
                       AvatarRenderState state, float yRot, float xRot) {
        if (!Cosmetics.INSTANCE.wearsHeadwear(state)
                || !EquipmentVisualPriority.allowsCosmetic(state, EquipmentSlot.HEAD)) return;
        CosmeticMesh current = model();
        if (current == null) return;
        poseStack.pushPose();
        try {
            // Vanilla has already called setupAnim(state) for this layer pass.
            // This includes head yaw/pitch, crouching, swimming and riding poses.
            PlayerModel playerModel = getParentModel();
            playerModel.root().translateAndRotate(poseStack);
            playerModel.translateToHead(poseStack);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(current.texture()),
                    (pose, output) -> current.render(pose, output, light));
        } finally {
            poseStack.popPose();
        }
    }

    private CosmeticMesh model() {
        if (!loadAttempted) {
            loadAttempted = true;
            try {
                var resource = Setsuna.mc().getResourceManager().getResourceOrThrow(BAMBOO_DOULI);
                mesh = CosmeticMesh.parse(resource.open());
            } catch (IOException | RuntimeException error) {
                Setsuna.LOGGER.warn("Could not load headwear {}", BAMBOO_DOULI, error);
            }
        }
        return mesh;
    }
}
