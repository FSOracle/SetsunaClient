package com.setsuna.event.events;

import com.setsuna.event.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD extraction pass backed by Minecraft's native GUI renderer.
 *
 * <p>Use this event for content that needs vanilla render state, such as item
 * models and resource-pack textures.</p>
 */
public final class VanillaHudRenderEvent extends Event {

    private final GuiGraphicsExtractor graphics;
    private final DeltaTracker deltaTracker;
    private final float width;
    private final float height;

    public VanillaHudRenderEvent(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                 float width, float height) {
        this.graphics = graphics;
        this.deltaTracker = deltaTracker;
        this.width = width;
        this.height = height;
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    public DeltaTracker deltaTracker() {
        return deltaTracker;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }
}
