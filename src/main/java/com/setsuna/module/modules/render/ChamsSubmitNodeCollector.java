package com.setsuna.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;

/** Rewrites every typed geometry submission made by a Flux Normal-mode layer. */
public final class ChamsSubmitNodeCollector extends ChamsOrderedSubmitNodeCollector
        implements SubmitNodeCollector {

    private final SubmitNodeCollector root;

    public ChamsSubmitNodeCollector(SubmitNodeCollector root, Chams chams) {
        super(root, chams);
        this.root = root;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return new ChamsOrderedSubmitNodeCollector(root.order(order), chams);
    }
}

class ChamsOrderedSubmitNodeCollector implements OrderedSubmitNodeCollector {

    protected final OrderedSubmitNodeCollector delegate;
    protected final Chams chams;

    ChamsOrderedSubmitNodeCollector(OrderedSubmitNodeCollector delegate, Chams chams) {
        this.delegate = delegate;
        this.chams = chams;
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        delegate.submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(PoseStack poseStack, Vec3 offset, int yOffset, Component text,
                              boolean discrete, int light, double distance, CameraRenderState camera) {
        delegate.submitNameTag(poseStack, offset, yOffset, text, discrete, light, distance, camera);
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text,
                           boolean dropShadow, Font.DisplayMode mode, int color, int backgroundColor,
                           int light, int outlineColor) {
        delegate.submitText(poseStack, x, y, text, dropShadow, mode, color, backgroundColor, light, outlineColor);
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf cameraOrientation) {
        delegate.submitFlame(poseStack, state, cameraOrientation);
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState state) {
        delegate.submitLeash(poseStack, state);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack,
                                RenderType renderType, int light, int overlay, int color,
                                TextureAtlasSprite sprite, int outlineColor,
                                ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        delegate.submitModel(model, state, poseStack, chams.getNormalRenderType(renderType),
                light, overlay, color, sprite, outlineColor, crumblingOverlay);
    }

    @Override
    public void submitModelPart(ModelPart part, PoseStack poseStack, RenderType renderType,
                                int light, int overlay, TextureAtlasSprite sprite,
                                boolean hasFoil, boolean translucent, int color,
                                ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
                                int outlineColor) {
        delegate.submitModelPart(part, poseStack, chams.getNormalRenderType(renderType),
                light, overlay, sprite, hasFoil, translucent, color, crumblingOverlay, outlineColor);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state) {
        delegate.submitMovingBlock(poseStack, state);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType,
                                 List<BlockStateModelPart> parts, int[] tints,
                                 int light, int overlay, int outlineColor) {
        delegate.submitBlockModel(poseStack, chams.getNormalRenderType(renderType),
                parts, tints, light, overlay, outlineColor);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
        delegate.submitBreakingBlockModel(poseStack, model, seed, progress);
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext,
                           int light, int overlay, int outlineColor, int[] tintLayers,
                           List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
        delegate.submitItem(poseStack, displayContext, light, overlay, outlineColor, tintLayers, quads, foilType);
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     SubmitNodeCollector.CustomGeometryRenderer renderer) {
        delegate.submitCustomGeometry(poseStack, chams.getNormalRenderType(renderType), renderer);
    }

    @Override
    public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) {
        delegate.submitParticleGroup(renderer);
    }
}
