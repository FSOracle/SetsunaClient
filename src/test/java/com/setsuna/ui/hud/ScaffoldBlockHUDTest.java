package com.setsuna.ui.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldBlockHUDTest {
    @Test
    void keepsSharedHudRenderingEnabledForPanelAndCount() {
        assertTrue(ScaffoldBlockHUD.INSTANCE.usesSharedOpacityLayer());
    }
}
