package com.setsuna.script;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.Locale;

/** Shared validation and method binding for the primitive-only Lua API. */
final class LuaApiSupport {

    private LuaApiSupport() {
    }

    static VarArgFunction method(LuaTable receiver, MethodBody body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return body.invoke(args, args.arg1().raweq(receiver) ? 2 : 1);
            }
        };
    }

    static double finiteDouble(LuaValue value, String name) {
        double result = value.checkdouble();
        if (!Double.isFinite(result)) {
            throw new LuaError(name + " must be finite");
        }
        return result;
    }

    static float finiteFloat(LuaValue value, String name) {
        double result = finiteDouble(value, name);
        if (result < -Float.MAX_VALUE || result > Float.MAX_VALUE) {
            throw new LuaError(name + " is outside the supported range");
        }
        return (float) result;
    }

    static double boundedDouble(LuaValue value, String name, double min, double max) {
        return Math.max(min, Math.min(max, finiteDouble(value, name)));
    }

    static float boundedFloat(LuaValue value, String name, float min, float max) {
        return (float) boundedDouble(value, name, min, max);
    }

    static String boundedString(LuaValue value, String name, int maxLength) {
        String result = value.checkjstring();
        if (result.length() > maxLength) {
            throw new LuaError(name + " exceeds " + maxLength + " characters");
        }
        return result;
    }

    static int color(LuaValue value) {
        if (value.isnumber()) {
            long packed = value.checklong();
            if (packed < Integer.MIN_VALUE || packed > 0xFFFF_FFFFL) {
                throw new LuaError("Packed color must fit in 32 bits");
            }
            return (int) packed;
        }
        if (value.istable()) {
            LuaTable table = value.checktable();
            int red = channel(table, "r", 1, 255);
            int green = channel(table, "g", 2, 255);
            int blue = channel(table, "b", 3, 255);
            int alpha = channel(table, "a", 4, 255);
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
        String raw = value.checkjstring().trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("#")) raw = raw.substring(1);
        if (raw.startsWith("0x")) raw = raw.substring(2);
        if (raw.length() != 6 && raw.length() != 8) {
            throw new LuaError("Color must be #RRGGBB, #AARRGGBB, an integer, or an RGBA table");
        }
        try {
            long parsed = Long.parseUnsignedLong(raw, 16);
            if (raw.length() == 6) parsed |= 0xFF00_0000L;
            return (int) parsed;
        } catch (NumberFormatException error) {
            throw new LuaError("Invalid color: " + raw);
        }
    }

    static int optionalColor(LuaValue value, int fallback) {
        return value.isnil() ? fallback : color(value);
    }

    static float optionalBoundedFloat(
            LuaValue value, String name, float fallback, float min, float max) {
        return value.isnil() ? fallback : boundedFloat(value, name, min, max);
    }

    static LuaTable colorTable(int color) {
        LuaTable table = new LuaTable();
        table.set("a", LuaValue.valueOf((color >>> 24) & 0xFF));
        table.set("r", LuaValue.valueOf((color >>> 16) & 0xFF));
        table.set("g", LuaValue.valueOf((color >>> 8) & 0xFF));
        table.set("b", LuaValue.valueOf(color & 0xFF));
        return table;
    }

    static int rgba(int red, int green, int blue, int alpha) {
        return (clampChannel(alpha) << 24)
                | (clampChannel(red) << 16)
                | (clampChannel(green) << 8)
                | clampChannel(blue);
    }

    private static int channel(LuaTable table, String key, int index, int fallback) {
        LuaValue value = table.get(key);
        if (value.isnil()) value = table.get(index);
        return value.isnil() ? fallback : clampChannel(value.checkint());
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @FunctionalInterface
    interface MethodBody {
        Varargs invoke(Varargs args, int first);
    }
}
