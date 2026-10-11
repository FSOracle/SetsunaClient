package com.setsuna.event.events;

import com.setsuna.event.Event;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Fired once per frame after the level has been rendered, carrying a
 * {@link PoseStack} already multiplied by the camera's model-view matrix.
 * Modules draw world-space geometry (boxes, tracers) against it.
 */
public final class Render3DEvent extends Event {

    private final PoseStack worldPose;

    public Render3DEvent(PoseStack poseStack) {
        worldPose = poseStack;
    }

    public PoseStack getPoseStack() {
        PoseStack current = worldPose;
        return current;
    }
}
