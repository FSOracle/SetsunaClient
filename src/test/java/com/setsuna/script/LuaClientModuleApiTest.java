package com.setsuna.script;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaClientModuleApiTest {

    @Test
    void moduleSnapshotContainsPrimitiveSettingMetadata() {
        LuaTable module = LuaClientModuleApi.snapshot(new SnapshotModule());

        assertEquals("snapshot_module", module.get("id").checkjstring());
        assertEquals("misc", module.get("category").checkjstring());
        assertFalse(module.get("enabled").checkboolean());

        LuaTable settings = module.get("settings").checktable();
        assertEquals("boolean", settings.get(1).get("type").checkjstring());
        assertTrue(settings.get(1).get("value").checkboolean());
        assertEquals("integer", settings.get(2).get("type").checkjstring());
        assertEquals(1, settings.get(2).get("min").checkint());
        assertEquals(10, settings.get(2).get("max").checkint());
        assertEquals("number", settings.get(3).get("type").checkjstring());
        assertEquals(0.25, settings.get(3).get("step").checkdouble());
        assertEquals("enum", settings.get(4).get("type").checkjstring());
        assertEquals("FIRST", settings.get(4).get("value").checkjstring());
        assertEquals(2, settings.get(4).get("options").length());
        assertEquals("color", settings.get(5).get("type").checkjstring());
        assertTrue(settings.get(5).get("allow_alpha").checkboolean());
    }

    private static final class SnapshotModule extends Module {

        private SnapshotModule() {
            super("Snapshot Module", Category.MISC);
            add(new BooleanSetting("Enabled Option", true));
            add(new IntSetting("Amount", 4, 1, 10, 2));
            add(new DoubleSetting("Scale", 1.5, 0.5, 3.0, 0.25));
            add(new EnumSetting<>("Mode", Mode.FIRST));
            add(new ColorSetting("Tint", new Color(10, 20, 30, 40), true));
        }
    }

    private enum Mode {
        FIRST,
        SECOND
    }
}
