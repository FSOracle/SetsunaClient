package com.setsuna.script;

import com.setsuna.Setsuna;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.Category;
import com.setsuna.notification.NotificationManager;
import com.setsuna.notification.NotificationType;
import com.setsuna.util.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.util.Locale;

/** Builds the deliberately small globals table exposed to local scripts. */
final class LuaSandbox {

    private LuaSandbox() {
    }

    static Globals create(LuaScript script) {
        Globals globals = new Globals();
        LuaTable internalPackage = new LuaTable();
        internalPackage.set("loaded", new LuaTable());
        globals.set("package", internalPackage);
        globals.load(new BaseLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        globals.load(new JseMathLib());
        LoadState.install(globals);
        LuaC.install(globals);

        for (String forbidden : new String[]{"io", "os", "package", "debug", "luajava",
                "dofile", "loadfile", "require"}) {
            globals.set(forbidden, LuaValue.NIL);
        }

        LuaTable setsuna = new LuaTable();
        setsuna.set("version", LuaValue.valueOf(Setsuna.VERSION));
        setsuna.set("color", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() == 1) {
                    return LuaValue.valueOf(LuaApiSupport.color(args.arg1()));
                }
                int red = args.arg(1).checkint();
                int green = args.arg(2).checkint();
                int blue = args.arg(3).checkint();
                int alpha = args.arg(4).optint(255);
                return LuaValue.valueOf(LuaApiSupport.rgba(red, green, blue, alpha));
            }
        });
        setsuna.set("color_table", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaApiSupport.colorTable(LuaApiSupport.color(args.arg1()));
            }
        });
        setsuna.set("module", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable metadata = args.arg1().checktable();
                String name = metadata.get("name").optjstring(fileStem(script.fileName())).trim();
                String requestedId = metadata.get("id").optjstring(fileStem(script.fileName()));
                String id = StringUtil.slug(requestedId);
                if (id.isEmpty() || id.length() > 64) {
                    throw new LuaError("Module id must contain ASCII letters or numbers");
                }
                if (name.isEmpty() || name.length() > 96) {
                    throw new LuaError("Module name must contain 1..96 characters");
                }
                Category category;
                try {
                    category = Category.valueOf(metadata.get("category")
                            .optjstring("misc").trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException error) {
                    throw new LuaError("Unknown module category");
                }
                if (!category.guiVisible()) {
                    throw new LuaError("Lua modules cannot use the internal HUD category");
                }
                LuaModule module = new LuaModule(script, id, name, category);
                script.addModule(module);
                return module.api();
            }
        });
        globals.set("setsuna", setsuna);
        globals.set("client", clientApi());
        globals.set("player", playerApi());
        globals.set("world", LuaWorldApi.create());
        globals.set("input", LuaInputApi.create());
        globals.set("action", LuaActionApi.create(script));
        return globals;
    }

    private static LuaTable clientApi() {
        Minecraft mc = Setsuna.mc();
        LuaTable client = new LuaTable();
        client.set("notify", method(client, (args, first) -> {
            String message = args.arg(first).checkjstring();
            String title = args.arg(first + 1).optjstring("Lua");
            String typeName = args.arg(first + 2).optjstring("info");
            NotificationType type;
            try {
                type = NotificationType.valueOf(typeName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                type = NotificationType.INFO;
            }
            NotificationManager.INSTANCE.post(type, title, message);
            return LuaValue.NONE;
        }));
        client.set("username", method(client, (args, first) ->
                LuaValue.valueOf(mc.getUser().getName())));
        client.set("server", method(client, (args, first) -> {
            ServerData server = mc.getCurrentServer();
            return LuaValue.valueOf(server == null ? "singleplayer" : server.ip);
        }));
        LuaClientModuleApi.install(client);
        return client;
    }

    private static LuaTable playerApi() {
        Minecraft mc = Setsuna.mc();
        LuaTable player = new LuaTable();
        player.set("is_available", method(player, (args, first) ->
                LuaValue.valueOf(mc.player != null && mc.level != null)));
        player.set("x", method(player, (args, first) ->
                mc.player == null ? LuaValue.NIL : LuaValue.valueOf(mc.player.getX())));
        player.set("y", method(player, (args, first) ->
                mc.player == null ? LuaValue.NIL : LuaValue.valueOf(mc.player.getY())));
        player.set("z", method(player, (args, first) ->
                mc.player == null ? LuaValue.NIL : LuaValue.valueOf(mc.player.getZ())));
        player.set("health", method(player, (args, first) ->
                mc.player == null ? LuaValue.NIL : LuaValue.valueOf(mc.player.getHealth())));
        player.set("yaw", method(player, (args, first) ->
                mc.player == null ? LuaValue.NIL : LuaValue.valueOf(mc.player.getYRot())));
        player.set("pitch", method(player, (args, first) ->
                mc.player == null ? LuaValue.NIL : LuaValue.valueOf(mc.player.getXRot())));
        player.set("server_yaw", method(player, (args, first) ->
                mc.player == null ? LuaValue.NIL
                        : LuaValue.valueOf(RotationManager.INSTANCE.getYaw())));
        player.set("server_pitch", method(player, (args, first) ->
                mc.player == null ? LuaValue.NIL
                        : LuaValue.valueOf(RotationManager.INSTANCE.getPitch())));
        player.set("rotation", method(player, (args, first) -> {
            if (mc.player == null) return LuaValue.NIL;
            LuaTable result = new LuaTable();
            result.set("yaw", mc.player.getYRot());
            result.set("pitch", mc.player.getXRot());
            result.set("server_yaw", RotationManager.INSTANCE.getYaw());
            result.set("server_pitch", RotationManager.INSTANCE.getPitch());
            result.set("silent", LuaValue.valueOf(RotationManager.INSTANCE.isActive()));
            return result;
        }));
        player.set("entity", method(player, (args, first) ->
                LuaEntityApi.snapshot(mc.player)));
        return player;
    }

    private static VarArgFunction method(LuaTable receiver, MethodBody body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return body.invoke(args, args.arg1().raweq(receiver) ? 2 : 1);
            }
        };
    }

    private static String fileStem(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".lua")
                ? name.substring(0, name.length() - 4) : name;
    }

    @FunctionalInterface
    private interface MethodBody {
        LuaValue invoke(Varargs args, int first);
    }
}
