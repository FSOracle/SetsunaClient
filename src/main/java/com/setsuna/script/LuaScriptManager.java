package com.setsuna.script;

import com.setsuna.Setsuna;
import com.setsuna.config.ConfigManager;
import com.setsuna.module.ModuleManager;
import org.luaj.vm2.Globals;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Owns script discovery, sandbox creation, reloads and dynamic module teardown. */
public final class LuaScriptManager {

    public static final LuaScriptManager INSTANCE = new LuaScriptManager();

    private static final long MAX_SCRIPT_BYTES = 1024L * 1024L;
    private static final int MAX_ERRORS = 100;
    private static final String EXAMPLE = """
            local module = setsuna.module({
                id = "lua_visual_example",
                name = "Lua 视觉示例",
                category = "render"
            })

            local showBoxes = module:boolean("show_boxes", true, "显示实体方框")
            local range = module:integer("range", 24, 4, 64, 1, "扫描范围")
            local accent = setsuna.color("#58DCE5")
            local fill = setsuna.color("#2458DCE5")
            local background = setsuna.color("#D914191E")
            local ticks = 0
            local nearby = {}

            module:on("enable", function()
                client:notify("视觉示例已启用", "Lua", "success")
            end)

            module:on("tick", function()
                ticks = ticks + 1
                if ticks % 10 == 0 and player:is_available() then
                    nearby = world:entities(range:get(), "living", 64)
                end
            end)

            module:on("render2d", function(self, draw)
                if not player:is_available() then return end
                local label = string.format("Lua HUD  |  XYZ %.1f  %.1f  %.1f",
                    player:x(), player:y(), player:z())
                local width = draw:text_width(label, 14) + 24
                draw:rounded_rect(12, 12, width, 38, 5, background)
                draw:outline_rect(12, 12, width, 38, accent, 1, 5)
                draw:text(label, 24, 21, accent, 14, true)
            end)

            module:on("render3d", function(self, draw)
                if not showBoxes:get() then return end
                for _, entity in ipairs(nearby) do
                    draw:entity_box(entity.id, fill, accent, 1.5)
                end
            end)
            """;

    private final List<LuaScript> scripts = new ArrayList<>();
    private final List<LuaScriptError> errors = new ArrayList<>();

    private LuaScriptManager() {
    }

    static String exampleSource() {
        return EXAMPLE;
    }

    /** Loads every enabled {@code *.lua} file. Individual failures do not block other files. */
    public synchronized int loadAll() {
        errors.clear();
        Path directory;
        try {
            directory = scriptsDirectory();
            createExample(directory);
        } catch (IOException error) {
            recordError(null, "discovery", error);
            return 0;
        }

        List<Path> files;
        try (Stream<Path> entries = Files.list(directory)) {
            files = entries.filter(Files::isRegularFile)
                    .filter(LuaScriptManager::isLuaFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException error) {
            recordError(directory, "discovery", error);
            return 0;
        }

        for (Path file : files) load(file);
        Setsuna.LOGGER.info("Loaded {} Lua scripts with {} modules ({} errors).",
                scripts.size(), moduleCount(), errors.size());
        return moduleCount();
    }

    /** Saves dynamic state, replaces all scripts, then reapplies the active profile. */
    public synchronized int reload() throws IOException {
        ConfigManager.INSTANCE.saveChecked();
        unloadAll();
        int count = loadAll();
        ConfigManager.INSTANCE.load();
        return count;
    }

    public synchronized void unloadAll() {
        for (int scriptIndex = scripts.size() - 1; scriptIndex >= 0; scriptIndex--) {
            LuaScript script = scripts.get(scriptIndex);
            List<LuaModule> modules = script.modules();
            for (int moduleIndex = modules.size() - 1; moduleIndex >= 0; moduleIndex--) {
                ModuleManager.INSTANCE.unregisterDynamic(modules.get(moduleIndex));
            }
            script.close();
        }
        scripts.clear();
    }

    public synchronized List<String> loadedScripts() {
        return scripts.stream().map(script -> script.fileName() + " ("
                + script.modules().size() + " modules)").toList();
    }

    public synchronized List<LuaScriptError> errors() {
        return List.copyOf(errors);
    }

    public Path scriptsDirectory() throws IOException {
        Path directory = ConfigManager.INSTANCE.configDirectory().resolve("scripts");
        Files.createDirectories(directory);
        return directory;
    }

    public void openScriptsDirectory() throws IOException {
        Path directory = scriptsDirectory();
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("Desktop integration is unavailable");
        }
        Desktop.getDesktop().open(directory.toFile());
    }

    synchronized void recordCallbackError(Path file, String moduleId, String event, Throwable error) {
        recordError(file, moduleId + "." + event, error);
    }

    private void load(Path file) {
        try {
            if (Files.size(file) > MAX_SCRIPT_BYTES) {
                throw new IOException("Script exceeds the 1 MiB size limit");
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            LuaScript script = new LuaScript(file);
            Globals globals = LuaSandbox.create(script);
            script.globals(globals);
            globals.load(source, "@" + file.getFileName()).call();
            if (script.modules().isEmpty()) {
                throw new IllegalArgumentException("Script did not declare a module");
            }
            List<LuaModule> registered = new ArrayList<>();
            try {
                for (LuaModule module : script.modules()) {
                    ModuleManager.INSTANCE.registerDynamic(module);
                    registered.add(module);
                }
            } catch (Throwable error) {
                for (int index = registered.size() - 1; index >= 0; index--) {
                    ModuleManager.INSTANCE.unregisterDynamic(registered.get(index));
                }
                throw error;
            }
            scripts.add(script);
        } catch (Throwable error) {
            recordError(file, "load", error);
        }
    }

    private int moduleCount() {
        return scripts.stream().mapToInt(script -> script.modules().size()).sum();
    }

    private void recordError(Path file, String phase, Throwable error) {
        String message = safeMessage(error);
        errors.add(new LuaScriptError(file, phase, message, Instant.now()));
        while (errors.size() > MAX_ERRORS) errors.removeFirst();
        Setsuna.LOGGER.error("Lua script failure in {} during {}: {}", file, phase, message, error);
    }

    private static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName() + ": "
                + (message == null || message.isBlank() ? "unknown error" : message);
    }

    private static boolean isLuaFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lua");
    }

    private static void createExample(Path directory) throws IOException {
        Path example = directory.resolve("example.lua.disabled");
        if (!Files.exists(example)) {
            Files.writeString(example, EXAMPLE, StandardCharsets.UTF_8);
        }
    }
}
