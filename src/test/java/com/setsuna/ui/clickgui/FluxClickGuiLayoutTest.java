package com.setsuna.ui.clickgui;

import com.setsuna.module.modules.ClickGui;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FluxClickGuiLayoutTest {

    @Test
    void defaultsToTheFluxWindowSizeAndCentersIt() {
        FluxClickGuiLayout.Bounds bounds = FluxClickGuiLayout.resolve(
                800.0F, 600.0F, Float.NaN, Float.NaN, Float.NaN, Float.NaN);

        assertEquals(202.5F, bounds.x());
        assertEquals(160.0F, bounds.y());
        assertEquals(395.0F, bounds.width());
        assertEquals(280.0F, bounds.height());
    }

    @Test
    void fitsSmallScreensAndKeepsTheWindowInsideTheMargin() {
        FluxClickGuiLayout.Bounds bounds = FluxClickGuiLayout.resolve(
                280.0F, 210.0F, -100.0F, 500.0F, 500.0F, 500.0F);

        assertEquals(8.0F, bounds.x());
        assertEquals(8.0F, bounds.y());
        assertEquals(264.0F, bounds.width());
        assertEquals(194.0F, bounds.height());
    }

    @Test
    void clampsDraggedWindowsToTheVisibleScreen() {
        FluxClickGuiLayout.Bounds bounds = FluxClickGuiLayout.resolve(
                800.0F, 600.0F, 900.0F, 900.0F, 395.0F, 280.0F);

        assertEquals(397.0F, bounds.x());
        assertEquals(312.0F, bounds.y());
    }

    @Test
    void switchesNumericSettingsToTwoColumnsAtTheFluxThreshold() {
        assertEquals(1, FluxClickGuiLayout.numericColumns(244.99F));
        assertEquals(2, FluxClickGuiLayout.numericColumns(245.0F));
    }

    @Test
    void clampsScrollingToTheScrollableRange() {
        assertEquals(0.0F, FluxClickGuiLayout.clampScroll(-20.0F, 400.0F, 200.0F));
        assertEquals(70.0F, FluxClickGuiLayout.clampScroll(70.0F, 400.0F, 200.0F));
        assertEquals(200.0F, FluxClickGuiLayout.clampScroll(500.0F, 400.0F, 200.0F));
        assertEquals(0.0F, FluxClickGuiLayout.clampScroll(20.0F, 100.0F, 200.0F));
    }

    @Test
    void snapsNumericValuesToTheirConfiguredStepAndRange() {
        assertEquals(0.3D, FluxClickGuiLayout.snap(0.26D, 0.0D, 1.0D, 0.1D), 1.0E-9D);
        assertEquals(0.0D, FluxClickGuiLayout.snap(-4.0D, 0.0D, 1.0D, 0.1D), 1.0E-9D);
        assertEquals(1.0D, FluxClickGuiLayout.snap(4.0D, 0.0D, 1.0D, 0.1D), 1.0E-9D);
    }

    @Test
    void exposesFluxAsAClickGuiMode() {
        assertEquals(ClickGui.Mode.Flux, ClickGui.Mode.valueOf("Flux"));
    }
}
