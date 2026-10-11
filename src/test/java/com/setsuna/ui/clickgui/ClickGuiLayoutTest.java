package com.setsuna.ui.clickgui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClickGuiLayoutTest {
    @Test
    void fitsThePanelAndKeepsUsableContentAcrossWindowSizesAndScales() {
        int[][] viewports = {{320, 180}, {427, 240}, {640, 360}, {960, 540}, {1720, 720}};
        for (int[] viewport : viewports) {
            ClickGuiLayout layout = ClickGuiLayout.forViewport(viewport[0], viewport[1]);
            assertTrue(layout.moduleListHeight() >= 100.0F);
            assertTrue(layout.tabsY() - layout.settingsY() - 36.0F >= 80.0F);
            for (float scale : new float[]{0.65F, 1.0F, 1.25F}) {
                float fitted = layout.fittedScale(viewport[0], viewport[1], scale);
                assertTrue(fitted > 0.0F && fitted <= scale);
                assertTrue(layout.width() * fitted <= viewport[0] - 15.9F);
                assertTrue(layout.height() * fitted <= viewport[1] - 15.9F);
            }
        }
    }

    @Test
    void gridRowsShareTheSameBoundsForRenderingAndHitTesting() {
        for (int width : new int[]{320, 420, 640, 960}) {
            ClickGuiLayout layout = ClickGuiLayout.forViewport(width, 540);
            float scroll = 25.0F;
            for (int index = 0; index < 50; index++) {
                float x = layout.moduleCardX(index);
                float y = layout.moduleCardY(index, scroll);
                assertTrue(x >= layout.panelContentX());
                assertTrue(x + layout.moduleCardWidth() <= layout.panelContentX() + layout.panelContentWidth() + 0.01F);
                assertTrue(ClickGuiLayout.contains(x + 1, y + 1, x, y, layout.moduleCardWidth(), 50.0F));
                assertFalse(ClickGuiLayout.contains(x + 1, y + 54, x, y, layout.moduleCardWidth(), 50.0F));
            }
            assertEquals(layout.moduleListY(), layout.moduleCardY(0, 0));
            assertEquals(0, layout.moduleContentHeight(0));
            assertEquals(50, layout.moduleContentHeight(layout.moduleColumns()));
            assertEquals(108, layout.moduleContentHeight(layout.moduleColumns() + 1));
        }
    }

    @Test
    void keepsCenteredGeometryWhenTheVirtualCanvasNeedsScaling() {
        ClickGuiLayout layout = ClickGuiLayout.forViewport(320, 180);
        assertEquals(160, layout.x() + layout.width() * 0.5F, 0.01F);
        assertEquals(90, layout.y() + layout.height() * 0.5F, 0.01F);
        assertEquals(2, layout.moduleColumns());
        assertEquals(3, ClickGuiLayout.forViewport(960, 540).moduleColumns());
    }
}
