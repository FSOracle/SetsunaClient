package com.setsuna.ui.clickgui;

final class FluxClickGuiLayout {

    static final float BASE_WIDTH = 395.0F;
    static final float BASE_HEIGHT = 280.0F;
    static final float MIN_WIDTH = 300.0F;
    static final float MIN_HEIGHT = 230.0F;
    static final float SCREEN_MARGIN = 8.0F;

    private FluxClickGuiLayout() {
    }

    static Bounds resolve(float screenWidth, float screenHeight,
                          float requestedX, float requestedY,
                          float requestedWidth, float requestedHeight) {
        float usableWidth = Math.max(1.0F, screenWidth - SCREEN_MARGIN * 2.0F);
        float usableHeight = Math.max(1.0F, screenHeight - SCREEN_MARGIN * 2.0F);
        float minimumWidth = Math.min(MIN_WIDTH, usableWidth);
        float minimumHeight = Math.min(MIN_HEIGHT, usableHeight);
        float width = clamp(finitePositive(requestedWidth) ? requestedWidth : BASE_WIDTH,
                minimumWidth, usableWidth);
        float height = clamp(finitePositive(requestedHeight) ? requestedHeight : BASE_HEIGHT,
                minimumHeight, usableHeight);
        float centeredX = (screenWidth - width) * 0.5F;
        float centeredY = (screenHeight - height) * 0.5F;
        float x = Float.isFinite(requestedX) ? requestedX : centeredX;
        float y = Float.isFinite(requestedY) ? requestedY : centeredY;
        x = clamp(x, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenWidth - SCREEN_MARGIN - width));
        y = clamp(y, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight - SCREEN_MARGIN - height));
        return new Bounds(x, y, width, height);
    }

    static int numericColumns(float contentWidth) {
        return contentWidth >= 245.0F ? 2 : 1;
    }

    static float clampScroll(float scroll, float contentHeight, float viewportHeight) {
        return clamp(scroll, 0.0F, Math.max(0.0F, contentHeight - viewportHeight));
    }

    static double snap(double raw, double minimum, double maximum, double step) {
        double safeStep = step > 0.0 && Double.isFinite(step) ? step : 0.01;
        double clamped = Math.max(minimum, Math.min(maximum, raw));
        double steps = Math.round((clamped - minimum) / safeStep);
        return Math.max(minimum, Math.min(maximum, minimum + steps * safeStep));
    }

    private static boolean finitePositive(float value) {
        return Float.isFinite(value) && value > 0.0F;
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (maximum < minimum) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Bounds(float x, float y, float width, float height) {
    }
}
