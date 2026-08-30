package com.setsuna.event.events;

import com.setsuna.event.Event;
import io.github.humbleui.skija.Canvas;

/**
 * Fired once per frame with the shared Skija {@link Canvas}, already scaled to
 * GUI-scaled coordinates (top-left origin), so modules can draw 2D overlays
 * (nametags, item tags, HUD) with the same primitives the ClickGUI uses.
 */
public final class Render2DEvent extends Event {

    private final Canvas canvas;
    private final float width;
    private final float height;
    private final double guiScale;

    public Render2DEvent(Canvas canvas, float width, float height, double guiScale) {
        this.canvas = canvas;
        this.width = width;
        this.height = height;
        this.guiScale = guiScale;
    }

    public Canvas canvas() {
        return canvas;
    }

    /** GUI-scaled width of the drawing surface. */
    public float width() {
        return width;
    }

    /** GUI-scaled height of the drawing surface. */
    public float height() {
        return height;
    }

    /** The current GUI scale factor, for converting raw pixel projections. */
    public double guiScale() {
        return guiScale;
    }
}
