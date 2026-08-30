package com.setsuna.script;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.FontSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.util.StringUtil;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Primitive-only module and setting snapshots used by Lua-authored interfaces. */
final class LuaClientModuleApi {

    private LuaClientModuleApi() {
    }

    static void install(LuaTable client) {
        client.set("categories", LuaApiSupport.method(client, (args, first) -> {
            LuaExecutionGuard.requireRuntimeCallback();
            LuaTable result = new LuaTable();
            int index = 1;
            for (Category category : Category.values()) {
                if (!category.guiVisible()) continue;
                LuaTable snapshot = new LuaTable();
                snapshot.set("id", category.name().toLowerCase(Locale.ROOT));
                snapshot.set("name", category.displayName());
                result.set(index++, snapshot);
            }
            return result;
        }));
        client.set("modules", LuaApiSupport.method(client, (args, first) -> {
            LuaExecutionGuard.requireRuntimeCallback();
            String filter = args.arg(first).optjstring("all").trim().toLowerCase(Locale.ROOT);
            boolean includeHidden = args.arg(first + 1).optboolean(false);
            LuaTable result = new LuaTable();
            int index = 1;
            for (Module module : List.copyOf(ModuleManager.INSTANCE.modules())) {
                if (!matchesCategory(module, filter) || (!includeHidden && module.isHidden())) continue;
                result.set(index++, snapshot(module));
            }
            return result;
        }));
        client.set("module", LuaApiSupport.method(client, (args, first) -> {
            LuaExecutionGuard.requireRuntimeCallback();
            Module module = findModule(args.arg(first).checkjstring());
            return module == null ? LuaValue.NIL : snapshot(module);
        }));
    }

    static Module findModule(String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.isEmpty()) return null;
        for (Module module : List.copyOf(ModuleManager.INSTANCE.modules())) {
            if (module.id().equalsIgnoreCase(id)) return module;
        }
        return null;
    }

    static LuaTable snapshot(Module module) {
        LuaTable result = new LuaTable();
        result.set("id", module.id());
        result.set("name", module.displayName());
        result.set("internal_name", module.name());
        result.set("category", module.category().name().toLowerCase(Locale.ROOT));
        result.set("category_name", module.category().displayName());
        result.set("enabled", LuaValue.valueOf(module.isEnabled()));
        result.set("toggleable", LuaValue.valueOf(module.isToggleable()));
        result.set("hidden", LuaValue.valueOf(module.isHidden()));
        result.set("keybind", module.keyBind());
        String info = module.getInfo();
        result.set("info", info == null ? LuaValue.NIL : LuaValue.valueOf(info));

        LuaTable settings = new LuaTable();
        int index = 1;
        for (Setting<?> setting : new ArrayList<>(module.settings())) {
            settings.set(index++, settingSnapshot(setting));
        }
        result.set("settings", settings);
        return result;
    }

    static boolean setModuleEnabled(String moduleId, boolean enabled) {
        Module module = findModule(moduleId);
        if (module == null || !module.isToggleable()) return false;
        module.setEnabled(enabled);
        return module.isEnabled() == enabled;
    }

    static boolean toggleModule(String moduleId) {
        Module module = findModule(moduleId);
        if (module == null) return false;
        if (module.isToggleable()) module.toggle();
        else module.trigger();
        return true;
    }

    static boolean setSetting(String moduleId, String settingId, LuaValue value) {
        Setting<?> setting = findSetting(moduleId, settingId);
        if (setting == null || setting instanceof ButtonSetting) return false;

        if (setting instanceof BooleanSetting typed) {
            typed.set(value.checkboolean());
        } else if (setting instanceof IntSetting typed) {
            typed.set(value.checkint());
        } else if (setting instanceof DoubleSetting typed) {
            typed.set(LuaApiSupport.finiteDouble(value, "setting value"));
        } else if (setting instanceof ColorSetting typed) {
            typed.set(new Color(LuaApiSupport.color(value), true));
        } else if (setting instanceof EnumSetting<?> typed) {
            setEnum(typed, value.checkjstring());
        } else if (setting instanceof KeybindSetting typed) {
            typed.set(keybind(value));
        } else if (setting instanceof StringSetting typed) {
            typed.set(LuaApiSupport.boundedString(value, "setting value", 4096));
        } else {
            return false;
        }
        return true;
    }

    static boolean cycleSetting(String moduleId, String settingId, int direction) {
        Setting<?> setting = findSetting(moduleId, settingId);
        if (setting == null) return false;
        int stepDirection = direction < 0 ? -1 : 1;
        if (setting instanceof BooleanSetting typed) {
            typed.toggle();
        } else if (setting instanceof IntSetting typed) {
            typed.set(typed.get() + typed.step() * stepDirection);
        } else if (setting instanceof DoubleSetting typed) {
            typed.set(typed.get() + typed.step() * stepDirection);
        } else if (setting instanceof EnumSetting<?> typed) {
            typed.cycle(stepDirection);
        } else if (setting instanceof FontSetting typed) {
            typed.cycle(stepDirection);
        } else if (setting instanceof ButtonSetting typed) {
            typed.press();
        } else {
            return false;
        }
        return true;
    }

    static boolean pressSetting(String moduleId, String settingId) {
        Setting<?> setting = findSetting(moduleId, settingId);
        if (!(setting instanceof ButtonSetting button)) return false;
        button.press();
        return true;
    }

    private static LuaTable settingSnapshot(Setting<?> setting) {
        LuaTable result = new LuaTable();
        result.set("id", setting.name());
        result.set("slug", StringUtil.slug(setting.name()));
        result.set("name", setting.displayName());
        result.set("visible", LuaValue.valueOf(setting.visible()));

        if (setting instanceof BooleanSetting typed) {
            baseValue(result, "boolean", LuaValue.valueOf(typed.get()),
                    LuaValue.valueOf(typed.defaultValue()));
        } else if (setting instanceof IntSetting typed) {
            baseValue(result, "integer", LuaValue.valueOf(typed.get()),
                    LuaValue.valueOf(typed.defaultValue()));
            range(result, typed.min(), typed.max(), typed.step());
        } else if (setting instanceof DoubleSetting typed) {
            baseValue(result, "number", LuaValue.valueOf(typed.get()),
                    LuaValue.valueOf(typed.defaultValue()));
            range(result, typed.min(), typed.max(), typed.step());
        } else if (setting instanceof ColorSetting typed) {
            baseValue(result, "color", LuaValue.valueOf(typed.argb()),
                    LuaValue.valueOf(typed.defaultValue().getRGB()));
            result.set("allow_alpha", LuaValue.valueOf(typed.allowAlpha()));
        } else if (setting instanceof EnumSetting<?> typed) {
            baseValue(result, "enum", LuaValue.valueOf(typed.get().name()),
                    LuaValue.valueOf(typed.defaultValue().name()));
            result.set("display_value", typed.displayValue());
            LuaTable options = new LuaTable();
            int index = 1;
            for (Enum<?> option : typed.values()) options.set(index++, option.name());
            result.set("options", options);
        } else if (setting instanceof FontSetting typed) {
            baseValue(result, "font", LuaValue.valueOf(typed.get()),
                    LuaValue.valueOf(typed.defaultValue()));
            result.set("options", strings(typed.options()));
        } else if (setting instanceof KeybindSetting typed) {
            baseValue(result, "keybind", LuaValue.valueOf(typed.get()),
                    LuaValue.valueOf(typed.defaultValue()));
        } else if (setting instanceof StringSetting typed) {
            baseValue(result, "string", LuaValue.valueOf(typed.get()),
                    LuaValue.valueOf(typed.defaultValue()));
        } else if (setting instanceof ButtonSetting) {
            result.set("type", "button");
            result.set("value", LuaValue.NIL);
            result.set("default", LuaValue.NIL);
        } else {
            result.set("type", "unknown");
            result.set("value", LuaValue.NIL);
            result.set("default", LuaValue.NIL);
        }
        return result;
    }

    private static boolean matchesCategory(Module module, String filter) {
        if (filter.isEmpty() || filter.equals("all")) return module.category().guiVisible();
        if (filter.equals("*") || filter.equals("all_internal")) return true;
        return module.category().name().equalsIgnoreCase(filter);
    }

    private static Setting<?> findSetting(String moduleId, String rawSettingId) {
        Module module = findModule(moduleId);
        if (module == null || rawSettingId == null) return null;
        String requested = rawSettingId.trim();
        String slug = StringUtil.slug(requested);
        for (Setting<?> setting : new ArrayList<>(module.settings())) {
            if (setting.name().equalsIgnoreCase(requested)
                    || setting.displayName().equalsIgnoreCase(requested)
                    || StringUtil.slug(setting.name()).equals(slug)) {
                return setting;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnum(EnumSetting<?> setting, String requested) {
        for (Enum<?> option : setting.values()) {
            if (option.name().equalsIgnoreCase(requested)) {
                ((Setting) setting).set(option);
                return;
            }
        }
        throw new LuaError("Unknown enum setting value: " + requested);
    }

    private static int keybind(LuaValue value) {
        if (value.isnumber()) return value.checkint();
        String name = value.checkjstring().trim();
        if (name.equalsIgnoreCase("none") || name.equalsIgnoreCase("unbound")) {
            return KeybindSetting.NONE;
        }
        return LuaInputApi.keyCode(value);
    }

    private static void baseValue(
            LuaTable result,
            String type,
            LuaValue value,
            LuaValue defaultValue) {
        result.set("type", type);
        result.set("value", value);
        result.set("default", defaultValue);
    }

    private static void range(LuaTable result, double min, double max, double step) {
        result.set("min", min);
        result.set("max", max);
        result.set("step", step);
    }

    private static LuaTable strings(List<String> values) {
        LuaTable result = new LuaTable();
        int index = 1;
        for (String value : values) result.set(index++, value);
        return result;
    }
}
