package com.setsuna.event.events;

import com.setsuna.event.Event;

/** Fired at the start of each rendered frame, before the camera is prepared. */
public final class RenderFrameEvent extends Event {

    private final float partialTick;
    private final boolean renderLevel;

    public RenderFrameEvent(float partialTick, boolean renderLevel) {
        this.partialTick = partialTick;
        this.renderLevel = renderLevel;
    }

    public float partialTick() {
        return partialTick;
    }

    public boolean renderLevel() {
        return renderLevel;
    }
}
