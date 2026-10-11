package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.ModuleManager;
import io.github.humbleui.types.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudFusionPerformanceTest {
    @Test
    void standaloneHudReusesFrameDecisionWhileKeepingUpdatedBounds() throws Exception {
        CountingHud hud = new CountingHud();
        ModuleManager.INSTANCE.modules().add(hud);
        try {
            Render2DEvent event = new Render2DEvent(null, 800, 600, 1.0);
            var prepare = HudFusionManager.class.getDeclaredMethod("renderFusedGroups", Render2DEvent.class);
            prepare.setAccessible(true);
            prepare.invoke(HudFusionManager.INSTANCE, event);
            hud.visits = 0;
            // HUD text/layout can change the width after the frame's fusion pass.
            hud.resize(120, 30);
            Rect expected = Rect.makeXYWH(hud.hudX(800), hud.hudY(600), 120, 30);
            for (int i = 0; i < 100; i++) {
                assertEquals(expected, HudFusionManager.contentBounds(hud, event));
            }
            assertEquals(0, hud.visits, "Standalone HUD lookups must not traverse all fusion candidates again");
        } finally {
            ModuleManager.INSTANCE.modules().remove(hud);
            HudFusionManager.resetAttachmentMemory();
        }
    }

    @Test
    void unpreparedPreviewStillResolvesLayout() {
        CountingHud hud = new CountingHud();
        ModuleManager.INSTANCE.modules().add(hud);
        HudFusionManager.resetAttachmentMemory();
        try {
            Render2DEvent preview = new Render2DEvent(null, 640, 480, 1.0);
            assertEquals(Rect.makeXYWH(hud.hudX(640), hud.hudY(480), 60, 20),
                    HudFusionManager.contentBounds(hud, preview));
            assertTrue(hud.visits > 0, "A preview without a prepared frame must still resolve fusion membership");
        } finally {
            ModuleManager.INSTANCE.modules().remove(hud);
            HudFusionManager.resetAttachmentMemory();
        }
    }

    private static final class CountingHud extends HUDModuel {
        private int visits;

        private CountingHud() {
            super("Fusion Performance Probe", 100, 100, 60, 20);
        }

        @Override
        public boolean isEnabled() {
            visits++;
            return true;
        }

        private void resize(float width, float height) {
            updateBounds(width, height);
        }
    }
}
