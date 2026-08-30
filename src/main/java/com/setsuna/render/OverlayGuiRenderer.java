package com.setsuna.render;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.List;

/** Independent vanilla GUI renderer used by the client HUD extraction pass. */
public final class OverlayGuiRenderer extends GuiRenderer {

    public OverlayGuiRenderer(GuiRenderState renderState,
                              MultiBufferSource.BufferSource bufferSource,
                              SubmitNodeCollector submitNodeCollector,
                              FeatureRenderDispatcher featureRenderDispatcher) {
        super(renderState, bufferSource, submitNodeCollector, featureRenderDispatcher, List.of());
    }
}
