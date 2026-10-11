package com.setsuna.ui.screen;

/** Retains the original dialog proportions while fitting smaller game windows. */
record ChangelogLayout(float width, float height, float scale, UiControls.Box panel) {
    static ChangelogLayout forViewport(float viewportWidth, float viewportHeight) {
        float scale = Math.min(1, Math.min(Math.max(1, viewportWidth) / 384,
                Math.max(1, viewportHeight) / 324));
        float width = Math.max(1, viewportWidth) / scale;
        float height = Math.max(1, viewportHeight) / scale;
        float panelWidth = Math.min(760, Math.max(320, width - 64));
        float panelHeight = Math.min(560, Math.max(260, height - 64));
        return new ChangelogLayout(width, height, scale, new UiControls.Box(
                Math.round((width - panelWidth) * .5F), Math.round((height - panelHeight) * .5F),
                panelWidth, panelHeight));
    }

    double logicalX(double x) { return x / scale; }
    double logicalY(double y, float slide) { return y / scale - slide; }
}
