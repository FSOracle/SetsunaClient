package com.setsuna.script;

import com.setsuna.event.Listen;
import com.setsuna.event.events.AttackEvent;
import com.setsuna.event.events.CharInputEvent;
import com.setsuna.event.events.KeyInputEvent;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.MouseButtonEvent;
import com.setsuna.event.events.MouseScrollEvent;
import com.setsuna.event.events.MoveEvent;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.StringSetting;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A normal Setsuna module whose behavior and settings are declared by Lua. */
public final class LuaModule extends Module {

    private static final Set<String> EVENTS = Set.of(
            "enable", "disable", "tick", "render2d", "render3d",
            "key", "char", "mouse", "mouse_scroll", "input", "move", "attack");

    private final String moduleId;
    private final LuaScript script;
    private final Map<String, LuaValue> callbacks = new HashMap<>();
    private final LuaTable api = new LuaTable();

    LuaModule(LuaScript script, String moduleId, String name, Category category) {
        super(name, category);
        this.script = script;
        this.moduleId = moduleId;
        installApi();
    }

    @Override
    public String id() {
        return moduleId;
    }

    LuaTable api() {
        return api;
    }

    private void installApi() {
        api.set("id", LuaValue.valueOf(moduleId));
        api.set("on", method((args, first) -> {
            String event = args.arg(first).checkjstring().toLowerCase(java.util.Locale.ROOT);
            if (!EVENTS.contains(event)) {
                throw new LuaError("Unknown module event: " + event);
            }
            callbacks.put(event, args.arg(first + 1).checkfunction());
            return api;
        }));
        api.set("boolean", method((args, first) -> {
            String name = settingName(args.arg(first));
            boolean value = args.arg(first + 1).checkboolean();
            return settingHandle(add(display(new BooleanSetting(name, value), args.arg(first + 2))));
        }));
        api.set("integer", method((args, first) -> {
            String name = settingName(args.arg(first));
            int value = args.arg(first + 1).checkint();
            int min = args.arg(first + 2).checkint();
            int max = args.arg(first + 3).checkint();
            int step = args.arg(first + 4).optint(1);
            if (max < min) throw new LuaError("integer max must be >= min");
            if (step <= 0) throw new LuaError("integer step must be positive");
            value = Math.max(min, Math.min(max, value));
            return settingHandle(add(display(new IntSetting(name, value, min, max, step),
                    args.arg(first + 5))));
        }));
        api.set("number", method((args, first) -> {
            String name = settingName(args.arg(first));
            double value = args.arg(first + 1).checkdouble();
            double min = args.arg(first + 2).checkdouble();
            double max = args.arg(first + 3).checkdouble();
            double step = args.arg(first + 4).optdouble(0.1D);
            if (!Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max)
                    || !Double.isFinite(step) || max < min || step <= 0.0D) {
                throw new LuaError("number default/range/step must be finite with max >= min and step > 0");
            }
            value = Math.max(min, Math.min(max, value));
            return settingHandle(add(display(new DoubleSetting(name, value, min, max, step),
                    args.arg(first + 5))));
        }));
        api.set("string", method((args, first) -> {
            String name = settingName(args.arg(first));
            String value = LuaApiSupport.boundedString(args.arg(first + 1), "string default", 4096);
            return settingHandle(add(display(new StringSetting(name, value), args.arg(first + 2))));
        }));
    }

    private <T extends Setting<?>> T display(T setting, LuaValue label) {
        if (!label.isnil()) setting.displayAs(label.checkjstring());
        return setting;
    }

    private String settingName(LuaValue value) {
        String name = value.checkjstring().trim();
        if (name.isEmpty() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) {
            throw new LuaError("Setting name must contain 1..64 printable characters");
        }
        if (settings().stream().anyMatch(setting -> setting.name().equals(name))) {
            throw new LuaError("Duplicate setting name: " + name);
        }
        return name;
    }

    private LuaTable settingHandle(Setting<?> setting) {
        LuaTable handle = new LuaTable();
        handle.set("get", method(handle, (args, first) -> toLuaValue(setting.get())));
        handle.set("set", method(handle, (args, first) -> {
            LuaValue value = args.arg(first);
            if (setting instanceof BooleanSetting typed) typed.set(value.checkboolean());
            else if (setting instanceof IntSetting typed) typed.set(value.checkint());
            else if (setting instanceof DoubleSetting typed) {
                typed.set(LuaApiSupport.finiteDouble(value, "setting value"));
            } else if (setting instanceof StringSetting typed) {
                typed.set(LuaApiSupport.boundedString(value, "setting value", 4096));
            }
            return toLuaValue(setting.get());
        }));
        handle.set("reset", method(handle, (args, first) -> {
            setting.reset();
            return toLuaValue(setting.get());
        }));
        return handle;
    }

    private static LuaValue toLuaValue(Object value) {
        if (value instanceof Boolean bool) return LuaValue.valueOf(bool);
        if (value instanceof Integer integer) return LuaValue.valueOf(integer);
        if (value instanceof Number number) return LuaValue.valueOf(number.doubleValue());
        return LuaValue.valueOf(String.valueOf(value));
    }

    private VarArgFunction method(MethodBody body) {
        return method(api, body);
    }

    private static VarArgFunction method(LuaTable receiver, MethodBody body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int first = args.arg1().raweq(receiver) ? 2 : 1;
                return body.invoke(args, first);
            }
        };
    }

    @Override
    protected void onEnable() {
        invoke("enable", null, true, null);
    }

    @Override
    protected void onDisable() {
        try {
            invoke("disable", null, false, null);
        } finally {
            LuaActionApi.releaseOwnedState(this);
        }
    }

    @Listen
    private void onTick(TickEvent.Post event) {
        invoke("tick", null, true, null);
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        invoke("render2d", new LuaRender2DContext(event).api(), true, null);
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        invoke("render3d", new LuaRender3DContext(event).api(), true, null);
    }

    @Listen
    private void onKey(KeyInputEvent event) {
        LuaTable context = new LuaTable();
        context.set("key", LuaValue.valueOf(event.key()));
        context.set("scancode", LuaValue.valueOf(event.scancode()));
        context.set("action", LuaValue.valueOf(event.action()));
        context.set("modifiers", LuaValue.valueOf(event.modifiers()));
        context.set("state", LuaValue.valueOf(inputState(event.action())));
        context.set("pressed", LuaValue.valueOf(event.action() == GLFW.GLFW_PRESS));
        context.set("released", LuaValue.valueOf(event.action() == GLFW.GLFW_RELEASE));
        context.set("repeat", LuaValue.valueOf(event.action() == GLFW.GLFW_REPEAT));
        context.set("cancel", LuaValue.FALSE);
        invoke("key", context, true, () -> {
            if (context.get("cancel").optboolean(false)) event.cancel();
        });
    }

    @Listen
    private void onMouse(MouseButtonEvent event) {
        LuaTable context = new LuaTable();
        context.set("button", LuaValue.valueOf(event.button()));
        context.set("action", LuaValue.valueOf(event.action()));
        context.set("modifiers", LuaValue.valueOf(event.modifiers()));
        context.set("state", LuaValue.valueOf(inputState(event.action())));
        context.set("pressed", LuaValue.valueOf(event.action() == GLFW.GLFW_PRESS));
        context.set("released", LuaValue.valueOf(event.action() == GLFW.GLFW_RELEASE));
        context.set("x", LuaValue.valueOf(event.x()));
        context.set("y", LuaValue.valueOf(event.y()));
        context.set("raw_x", LuaValue.valueOf(event.rawX()));
        context.set("raw_y", LuaValue.valueOf(event.rawY()));
        context.set("cancel", LuaValue.FALSE);
        invoke("mouse", context, true, () -> {
            if (context.get("cancel").optboolean(false)) event.cancel();
        });
    }

    @Listen
    private void onMouseScroll(MouseScrollEvent event) {
        LuaTable context = new LuaTable();
        context.set("horizontal", LuaValue.valueOf(event.horizontal()));
        context.set("vertical", LuaValue.valueOf(event.vertical()));
        context.set("x", LuaValue.valueOf(event.x()));
        context.set("y", LuaValue.valueOf(event.y()));
        context.set("raw_x", LuaValue.valueOf(event.rawX()));
        context.set("raw_y", LuaValue.valueOf(event.rawY()));
        context.set("cancel", LuaValue.FALSE);
        invoke("mouse_scroll", context, true, () -> {
            if (context.get("cancel").optboolean(false)) event.cancel();
        });
    }

    @Listen
    private void onChar(CharInputEvent event) {
        LuaTable context = new LuaTable();
        context.set("codepoint", LuaValue.valueOf(event.codepoint()));
        context.set("text", LuaValue.valueOf(event.text()));
        context.set("allowed", LuaValue.valueOf(event.allowedChatCharacter()));
        context.set("cancel", LuaValue.FALSE);
        invoke("char", context, true, () -> {
            if (context.get("cancel").optboolean(false)) event.cancel();
        });
    }

    @Listen
    private void onInput(KeyboardInputEvent event) {
        LuaTable context = new LuaTable();
        context.set("forward", LuaValue.valueOf(event.getForward()));
        context.set("strafe", LuaValue.valueOf(event.getStrafe()));
        context.set("original_forward", LuaValue.valueOf(event.getOriginalForward()));
        context.set("original_strafe", LuaValue.valueOf(event.getOriginalStrafe()));
        context.set("jump", LuaValue.valueOf(event.isJump()));
        context.set("sneak", LuaValue.valueOf(event.isSneak()));
        context.set("sprint", LuaValue.valueOf(event.isSprint()));
        invoke("input", context, true, () -> {
            event.setForward(LuaApiSupport.boundedFloat(
                    context.get("forward"), "input.forward", -1.0F, 1.0F));
            event.setStrafe(LuaApiSupport.boundedFloat(
                    context.get("strafe"), "input.strafe", -1.0F, 1.0F));
            event.setJump(context.get("jump").checkboolean());
            event.setSneak(context.get("sneak").checkboolean());
            event.setSprint(context.get("sprint").checkboolean());
        });
    }

    @Listen
    private void onMove(MoveEvent event) {
        LuaTable context = new LuaTable();
        context.set("x", LuaValue.valueOf(event.getX()));
        context.set("y", LuaValue.valueOf(event.getY()));
        context.set("z", LuaValue.valueOf(event.getZ()));
        context.set("cancel", LuaValue.FALSE);
        invoke("move", context, true, () -> {
            double x = LuaApiSupport.finiteDouble(context.get("x"), "move.x");
            double y = LuaApiSupport.finiteDouble(context.get("y"), "move.y");
            double z = LuaApiSupport.finiteDouble(context.get("z"), "move.z");
            boolean cancelled = context.get("cancel").optboolean(false);
            boolean changed = Double.compare(x, event.getX()) != 0
                    || Double.compare(y, event.getY()) != 0
                    || Double.compare(z, event.getZ()) != 0;
            if (cancelled) {
                event.setX(0.0);
                event.setY(0.0);
                event.setZ(0.0);
            } else {
                event.setX(x);
                event.setY(y);
                event.setZ(z);
            }
            if (cancelled || changed) event.cancel();
        });
    }

    @Listen
    private void onAttack(AttackEvent event) {
        invoke("attack", LuaEntityApi.snapshot(event.getTarget()), true, null);
    }

    private void invoke(String event, LuaValue context, boolean disableOnFailure, Runnable after) {
        LuaValue callback = callbacks.get(event);
        if (callback == null) return;
        try (LuaExecutionGuard.Scope ignored = LuaExecutionGuard.enter(event, this)) {
            if (context == null) callback.call(api);
            else callback.invoke(LuaValue.varargsOf(new LuaValue[]{api, context}));
            if (after != null) after.run();
        } catch (Throwable error) {
            LuaScriptManager.INSTANCE.recordCallbackError(script.file(), moduleId, event, error);
            if (disableOnFailure) setEnabled(false);
        }
    }

    private static String inputState(int action) {
        return switch (action) {
            case GLFW.GLFW_PRESS -> "press";
            case GLFW.GLFW_RELEASE -> "release";
            case GLFW.GLFW_REPEAT -> "repeat";
            default -> "unknown";
        };
    }

    @FunctionalInterface
    private interface MethodBody {
        LuaValue invoke(Varargs args, int first);
    }
}
