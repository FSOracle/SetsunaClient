package com.setsuna.module.modules.render;

import com.setsuna.ui.hud.TargetHud;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class NameTagsHealthDetectionTest {

    @Test
    void exposesAnIndependentHealthDetectionSettingWithHeypixelMode() {
        var setting = NameTags.INSTANCE.healthDetection;
        try {
            assertEquals(TargetHud.HealthDetection.Hoplite, setting.get());
            assertArrayEquals(TargetHud.HealthDetection.values(), setting.values());
            assertNotSame(TargetHud.INSTANCE.healthDetection, setting);

            setting.set(TargetHud.HealthDetection.Heypixel);
            assertEquals(TargetHud.HealthDetection.Heypixel, setting.get());
        } finally {
            setting.reset();
        }
    }
}
