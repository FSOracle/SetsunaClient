package com.setsuna.mixin;

import com.setsuna.event.EventBus;
import com.setsuna.event.events.Render3DEvent;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires {@link Render3DEvent} at the end of {@code renderLevel} with a
 * {@link PoseStack} pre-multiplied by the camera's model-view matrix, so modules
 * can draw world-space geometry in the same frame the level was drawn.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void setsuna$onPostRenderLevel(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker,
                                         boolean renderOutline, CameraRenderState cameraState, Matrix4fc modelViewMatrix,
                                         GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky,
                                         ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci) {
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        EventBus.INSTANCE.post(new Render3DEvent(poseStack));
    }
}
