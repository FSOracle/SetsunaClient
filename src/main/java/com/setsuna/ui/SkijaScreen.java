package com.setsuna.ui;

import io.github.humbleui.skija.Canvas;

/** Screen contract rendered by the shared Skija framebuffer bridge. */
public interface SkijaScreen {

    void renderSkija(Canvas canvas);
}
