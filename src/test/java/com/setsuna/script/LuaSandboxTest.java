package com.setsuna.script;

import com.setsuna.setting.settings.IntSetting;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaSandboxTest {

    @Test
    void dangerousLibrariesAreNotExposed() {
        Globals globals = LuaSandbox.create(new LuaScript(Path.of("sandbox.lua")));

        for (String name : new String[]{"io", "os", "package", "debug", "luajava",
                "dofile", "loadfile", "require"}) {
            assertTrue(globals.get(name).isnil(), name + " must not be exposed");
        }
    }

    @Test
    void scriptCanDeclareModuleAndClampedSetting() {
        LuaScript script = new LuaScript(Path.of("example.lua"));
        Globals globals = LuaSandbox.create(script);

        globals.load("""
                local module = setsuna.module({
                    id = "test module",
                    name = "测试模块",
                    category = "misc"
                })
                local amount = module:integer("amount", 3, 1, 10, 1, "数量")
                amount:set(99)
                result = amount:get()
                module:on("tick", function() end)
                """, "@example.lua").call();

        assertEquals(1, script.modules().size());
        LuaModule module = script.modules().getFirst();
        assertEquals("test_module", module.id());
        assertEquals(10, globals.get("result").checkint());
        assertEquals(10, ((IntSetting) module.settings().getFirst()).get());
        assertEquals("数量", module.settings().getFirst().displayName());
    }

    @Test
    void settingsCanBeReadAndResetFromLua() {
        LuaScript script = new LuaScript(Path.of("reset.lua"));
        Globals globals = LuaSandbox.create(script);

        globals.load("""
                local module = setsuna.module({ id = "reset", name = "Reset" })
                local enabled = module:boolean("enabled option", true)
                enabled:set(false)
                enabled:reset()
                result = enabled:get()
                """, "@reset.lua").call();

        assertEquals(LuaValue.TRUE, globals.get("result"));
    }

    @Test
    void allV2CallbacksCanBeRegistered() {
        LuaScript script = new LuaScript(Path.of("events.lua"));
        Globals globals = LuaSandbox.create(script);

        globals.load("""
                local module = setsuna.module({ id = "events", name = "Events" })
                local names = {
                    "enable", "disable", "tick", "render2d", "render3d",
                    "key", "char", "mouse", "mouse_scroll",
                    "input", "move", "attack"
                }
                for _, name in ipairs(names) do
                    module:on(name, function() end)
                end
                """, "@events.lua").call();

        assertEquals(1, script.modules().size());
    }

    @Test
    void bundledVisualExampleLoadsAndDeclaresSettings() {
        LuaScript script = new LuaScript(Path.of("example.lua"));
        Globals globals = LuaSandbox.create(script);

        globals.load(LuaScriptManager.exampleSource(), "@example.lua").call();

        assertEquals(1, script.modules().size());
        assertEquals("lua_visual_example", script.modules().getFirst().id());
        assertEquals(2, script.modules().getFirst().settings().size());
    }

    @Test
    void v2GlobalsExposeOnlyLuaValues() {
        Globals globals = LuaSandbox.create(new LuaScript(Path.of("api.lua")));

        assertTrue(globals.get("world").istable());
        assertTrue(globals.get("input").istable());
        assertTrue(globals.get("action").istable());
        assertTrue(globals.get("setsuna").get("color").isfunction());
        assertTrue(globals.get("world").get("entities").isfunction());
        assertTrue(globals.get("action").get("attack").isfunction());
        assertTrue(globals.get("input").get("cursor").isfunction());
        assertTrue(globals.get("client").get("modules").isfunction());
        assertTrue(globals.get("client").get("categories").isfunction());
        assertTrue(globals.get("action").get("rotate_silent").isfunction());
        assertTrue(globals.get("action").get("send_rotation").isfunction());
        assertTrue(globals.get("action").get("module_toggle").isfunction());
        assertTrue(globals.get("action").get("setting_set").isfunction());
    }

    @Test
    void colorHelperAcceptsHexAndRgbaTables() {
        Globals globals = LuaSandbox.create(new LuaScript(Path.of("color.lua")));

        globals.load("""
                hex = setsuna.color("#80402010")
                rgb = setsuna.color("#ff0080")
                rgba = setsuna.color({ r = 1, g = 2, b = 3, a = 4 })
                channels = setsuna.color_table(rgba)
                """, "@color.lua").call();

        assertEquals(0x80402010, globals.get("hex").checkint());
        assertEquals(0xFFFF0080, globals.get("rgb").checkint());
        assertEquals(0x04010203, globals.get("rgba").checkint());
        LuaTable channels = globals.get("channels").checktable();
        assertEquals(4, channels.get("a").checkint());
        assertEquals(3, channels.get("b").checkint());
    }

    @Test
    void packedColorsMustFitIn32Bits() {
        Globals globals = LuaSandbox.create(new LuaScript(Path.of("bad-color.lua")));

        assertThrows(LuaError.class,
                () -> globals.load("setsuna.color(4294967296)", "@bad-color.lua").call());
    }

    @Test
    void runtimeApisRejectTopLevelCallsBeforeTouchingMinecraft() {
        Globals globals = LuaSandbox.create(new LuaScript(Path.of("guard.lua")));

        assertThrows(LuaError.class, () -> globals.load("action:jump()", "@guard.lua").call());
        assertThrows(LuaError.class, () -> globals.load("world:block(0, 0, 0)", "@guard.lua").call());
        assertThrows(LuaError.class, () -> globals.load("input:is_down('space')", "@guard.lua").call());
        assertThrows(LuaError.class, () -> globals.load("input:cursor()", "@guard.lua").call());
        assertThrows(LuaError.class, () -> globals.load("client:modules()", "@guard.lua").call());
    }

    @Test
    void invalidSettingStepsAreRejected() {
        Globals globals = LuaSandbox.create(new LuaScript(Path.of("invalid-setting.lua")));

        assertThrows(LuaError.class, () -> globals.load("""
                local module = setsuna.module({ id = "invalid", name = "Invalid" })
                module:integer("bad", 1, 0, 10, 0)
                """, "@invalid-setting.lua").call());
    }
}
