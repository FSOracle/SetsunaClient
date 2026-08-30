package com.setsuna.setting;

import com.setsuna.setting.settings.BooleanSetting;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingPresentationTest {

    @Test
    void additionalVisibilityConditionsDoNotReplaceExistingConditions() {
        AtomicBoolean featureVisible = new AtomicBoolean(true);
        AtomicBoolean pageVisible = new AtomicBoolean(true);
        BooleanSetting setting = new BooleanSetting("Stable/ConfigName", false)
                .visibleWhen(featureVisible::get)
                .andVisibleWhen(pageVisible::get);

        assertTrue(setting.visible());
        pageVisible.set(false);
        assertFalse(setting.visible());
        pageVisible.set(true);
        featureVisible.set(false);
        assertFalse(setting.visible());
    }

    @Test
    void displayOverrideDoesNotChangeStableSettingName() {
        BooleanSetting setting = new BooleanSetting("Render/OutlineColor", false)
                .displayAs("Outline Color");

        assertEquals("Render/OutlineColor", setting.name());
        assertEquals("Outline Color", setting.displayName());
    }
}
