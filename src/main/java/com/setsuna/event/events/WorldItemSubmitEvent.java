package com.setsuna.event.events;

import com.mojang.blaze3d.vertex.PoseStack;
import com.setsuna.event.Event;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Fired while world item models can still be submitted to the current frame.
 */
public final class WorldItemSubmitEvent extends Event {

    private final PoseStack poseStack;
    private final SubmitNodeCollector collector;
    private final Vec3 cameraPosition;

    public WorldItemSubmitEvent(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPosition) {
        this.poseStack = poseStack;
        this.collector = collector;
        this.cameraPosition = cameraPosition;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public SubmitNodeCollector getCollector() {
        return collector;
    }

    public Vec3 getCameraPosition() {
        return cameraPosition;
    }
}
