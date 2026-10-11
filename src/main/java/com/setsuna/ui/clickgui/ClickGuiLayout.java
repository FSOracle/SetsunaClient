package com.setsuna.ui.clickgui;

/** Responsive geometry shared by ClickGUI rendering and hit testing. */
record ClickGuiLayout(
        float x,
        float y,
        float width,
        float height,
        float railWidth,
        float moduleWidth) {

    static final float OUTER_MARGIN = 8.0F;

    static ClickGuiLayout forViewport(int screenWidth, int screenHeight) {
        ClickGuiLayout base = of(Math.max(420, screenWidth), Math.max(320, screenHeight), 1.0F);
        return new ClickGuiLayout((screenWidth - base.width) * 0.5F,
                (screenHeight - base.height) * 0.5F, base.width, base.height, base.railWidth, base.moduleWidth);
    }

    float fittedScale(int screenWidth, int screenHeight, float requestedScale) {
        return Math.max(0.01F, Math.min(requestedScale,
                Math.min(Math.max(1.0F, screenWidth - 16.0F) / width,
                        Math.max(1.0F, screenHeight - 16.0F) / height)));
    }

    static ClickGuiLayout of(int screenWidth, int screenHeight, float guiScale) {
        float margin = Math.min(OUTER_MARGIN, Math.max(4.0F, Math.min(screenWidth, screenHeight) * 0.025F));
        float availableWidth = Math.max(1.0F, screenWidth - margin * 2.0F);
        float availableHeight = Math.max(1.0F, screenHeight - margin * 2.0F);
        float scale = clamp(guiScale, 0.65F, 1.25F);
        float width = Math.min(availableWidth, 580.0F * scale);
        float height = Math.min(availableHeight, 370.0F * scale);

        float railMinimum = Math.min(46.0F, width * 0.14F);
        float railMaximum = Math.min(56.0F, width);
        float moduleMinimum = Math.min(112.0F, width * 0.31F);
        float detailMinimum = Math.min(150.0F, width * 0.49F);
        float railWidth = clamp(width * 0.09F, railMinimum, railMaximum);
        float moduleWidth = clamp(width * 0.28F, moduleMinimum, Math.min(210.0F, width - railWidth));
        if (width - railWidth - moduleWidth < detailMinimum) {
            moduleWidth = Math.max(1.0F, width - railWidth - detailMinimum);
        }

        return new ClickGuiLayout(
                (screenWidth - width) * 0.5F,
                (screenHeight - height) * 0.5F,
                width,
                height,
                railWidth,
                moduleWidth);
    }

    float moduleX() {
        return x + railWidth;
    }

    float headerHeight() {
        return Math.min(height, clamp(height * 0.085F, 34.0F, 44.0F));
    }

    float bodyY() {
        return y + headerHeight();
    }

    float bodyHeight() {
        return Math.max(0.0F, height - headerHeight());
    }

    float detailX() {
        return moduleX() + moduleWidth;
    }

    float detailWidth() {
        return width - railWidth - moduleWidth;
    }

    float moduleListY() {
        return y + moduleListOffset();
    }

    float moduleListHeight() {
        return Math.max(0.0F, tabsY() - moduleListY() - 8.0F);
    }

    float settingsY() {
        return y + detailHeaderHeight();
    }

    float settingsHeight() {
        return Math.max(0.0F, height - detailHeaderHeight());
    }

    float categoryTop() {
        return Math.min(height, headerHeight() + Math.min(16.0F, Math.max(7.0F, bodyHeight() * 0.06F)));
    }

    float contentX() {
        return x + 12.0F;
    }

    float contentWidth() {
        return Math.max(1.0F, width - 24.0F);
    }

    float tabsHeight() {
        return Math.min(42.0F, Math.max(32.0F, height * 0.12F));
    }

    float tabsY() {
        return y + height - tabsHeight() - 8.0F;
    }

    float panelContentX() {
        return x + 12.0F;
    }

    float panelContentWidth() {
        return Math.max(1.0F, width - 24.0F);
    }

    float moduleListOffset() {
        return Math.min(height, headerHeight() + 32.0F);
    }

    int moduleColumns() {
        return panelContentWidth() >= 450.0F ? 3 : panelContentWidth() >= 280.0F ? 2 : 1;
    }

    float moduleCardWidth() {
        return Math.max(1.0F, (panelContentWidth() - 8.0F * (moduleColumns() - 1)) / moduleColumns());
    }

    float moduleCardX(int index) {
        return panelContentX() + (index % moduleColumns()) * (moduleCardWidth() + 8.0F);
    }

    float moduleCardY(int index, float scroll) {
        return moduleListY() + (index / moduleColumns()) * 58.0F - scroll;
    }

    float moduleContentHeight(int count) {
        return count == 0 ? 0.0F : ((count + moduleColumns() - 1) / moduleColumns()) * 58.0F - 8.0F;
    }

    float detailHeaderHeight() {
        return Math.min(height, headerHeight() + inspectorHeaderHeight());
    }

    float inspectorHeaderHeight() {
        return Math.min(bodyHeight(), clamp(bodyHeight() * 0.31F, 68.0F, 88.0F));
    }

    boolean contains(double mouseX, double mouseY) {
        return contains(mouseX, mouseY, x, y, width, height);
    }

    static boolean contains(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
