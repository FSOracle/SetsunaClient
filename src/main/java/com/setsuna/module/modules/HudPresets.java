package com.setsuna.module.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.setsuna.Setsuna;
import com.setsuna.config.ConfigManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.notification.NotificationManager;
import com.setsuna.notification.NotificationType;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.ui.hud.HUDModuel;
import com.setsuna.ui.hud.HUD;
import com.setsuna.ui.hud.HudFusionManager;
import com.setsuna.ui.screen.HudPresetScreen;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Applies official HUD layouts and owns user HUD profiles, always excluding colors. */
public final class HudPresets extends Module {

    public static final HudPresets INSTANCE = new HudPresets();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MODULES_KEY = "modules";
    private static final String FUSION_KEY = "fusion";
    private static final Set<Character> ILLEGAL_NAME_CHARS =
            Set.of('<', '>', ':', '"', '/', '\\', '|', '?', '*');

    public enum OfficialPreset { ONE, TWO, THREE }

    public record PreviewItem(String moduleId, String displayName, float x, float y,
                              float width, float height, boolean fused) {
    }

    public record PreviewData(List<PreviewItem> items) {
        public static final PreviewData EMPTY = new PreviewData(List.of());
    }

    private HudPresets() {
        super("HUD Presets", Category.CLIENT);
        setToggleable(false);
    }

    @Override
    protected void onTrigger() {
        mc.setScreen(new HudPresetScreen(mc.screen));
    }

    public boolean applyOfficial(OfficialPreset preset) {
        String resource = "/assets/setsuna/presets/hud/official-"
                + preset.name().toLowerCase(Locale.ROOT) + ".json";
        try (InputStream stream = HudPresets.class.getResourceAsStream(resource)) {
            if (stream == null) {
                notify(NotificationType.ERROR, "HUD preset resource is missing");
                return false;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return applyRoot(JsonParser.parseReader(reader).getAsJsonObject(),
                        "Official " + preset.name());
            }
        } catch (Exception error) {
            Setsuna.LOGGER.error("Failed to apply HUD preset {}", preset, error);
            notify(NotificationType.ERROR, "Could not apply HUD preset");
            return false;
        }
    }

    public PreviewData previewOfficial(OfficialPreset preset) {
        String resource = "/assets/setsuna/presets/hud/official-"
                + preset.name().toLowerCase(Locale.ROOT) + ".json";
        try (InputStream stream = HudPresets.class.getResourceAsStream(resource)) {
            if (stream == null) return PreviewData.EMPTY;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return preview(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (Exception error) {
            Setsuna.LOGGER.warn("Failed to preview official HUD preset {}", preset, error);
            return PreviewData.EMPTY;
        }
    }

    public List<String> personalPresets() {
        try (Stream<Path> files = Files.list(personalDirectory())) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (Exception error) {
            Setsuna.LOGGER.warn("Failed to list personal HUD presets", error);
            return List.of();
        }
    }

    public boolean savePersonal(String rawName) {
        String name = sanitizeName(rawName);
        if (name.isBlank()) {
            notify(NotificationType.ERROR, "Enter a preset name");
            return false;
        }
        try {
            Files.writeString(personalFile(name), GSON.toJson(snapshot()), StandardCharsets.UTF_8);
            notify(NotificationType.SUCCESS, "Saved HUD preset " + name);
            return true;
        } catch (Exception error) {
            Setsuna.LOGGER.error("Failed to save personal HUD preset {}", name, error);
            notify(NotificationType.ERROR, "Could not save HUD preset");
            return false;
        }
    }

    public boolean loadPersonal(String rawName) {
        String name = sanitizeName(rawName);
        try (Reader reader = Files.newBufferedReader(personalFile(name), StandardCharsets.UTF_8)) {
            return applyRoot(JsonParser.parseReader(reader).getAsJsonObject(), name);
        } catch (Exception error) {
            Setsuna.LOGGER.error("Failed to load personal HUD preset {}", name, error);
            notify(NotificationType.ERROR, "Could not load HUD preset");
            return false;
        }
    }

    public PreviewData previewPersonal(String rawName) {
        String name = sanitizeName(rawName);
        if (name.isBlank()) return PreviewData.EMPTY;
        try (Reader reader = Files.newBufferedReader(personalFile(name), StandardCharsets.UTF_8)) {
            return preview(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception error) {
            Setsuna.LOGGER.warn("Failed to preview personal HUD preset {}", name, error);
            return PreviewData.EMPTY;
        }
    }

    public boolean deletePersonal(String rawName) {
        String name = sanitizeName(rawName);
        if (name.isBlank()) return false;
        try {
            boolean deleted = Files.deleteIfExists(personalFile(name));
            notify(deleted ? NotificationType.SUCCESS : NotificationType.INFO,
                    deleted ? "Deleted HUD preset " + name : "HUD preset was not found");
            return deleted;
        } catch (Exception error) {
            Setsuna.LOGGER.error("Failed to delete personal HUD preset {}", name, error);
            notify(NotificationType.ERROR, "Could not delete HUD preset");
            return false;
        }
    }

    public Path personalDirectory() throws IOException {
        Path directory = ConfigManager.INSTANCE.configDirectory()
                .resolve("visual-presets").resolve("hud-personal");
        Files.createDirectories(directory);
        return directory;
    }

    public String sanitizeName(String rawName) {
        String normalized = Normalizer.normalize(rawName == null ? "" : rawName.trim(),
                Normalizer.Form.NFKC);
        StringBuilder safe = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            if (safe.codePointCount(0, safe.length()) >= 48 || Character.isISOControl(codePoint)
                    || codePoint <= Character.MAX_VALUE
                    && ILLEGAL_NAME_CHARS.contains((char) codePoint)) return;
            safe.appendCodePoint(codePoint);
        });
        String result = safe.toString().trim();
        while (result.endsWith(".")) result = result.substring(0, result.length() - 1).trim();
        return result;
    }

    private JsonObject snapshot() {
        JsonObject modules = new JsonObject();
        for (Module module : ModuleManager.INSTANCE.modules()) {
            if (isHudModule(module)) modules.add(module.id(), withoutColors(module, module.toJson()));
        }
        JsonArray fusion = new JsonArray();
        float width = mc.getWindow().getGuiScaledWidth();
        float height = mc.getWindow().getGuiScaledHeight();
        HudFusionManager.capturePresetAttachments(width, height).forEach(fusion::add);
        JsonObject root = new JsonObject();
        root.add(MODULES_KEY, modules);
        root.add(FUSION_KEY, fusion);
        return root;
    }

    private boolean applyRoot(JsonObject root, String label) {
        JsonObject modules = root.has(MODULES_KEY) && root.get(MODULES_KEY).isJsonObject()
                ? root.getAsJsonObject(MODULES_KEY) : new JsonObject();
        if (modules.isEmpty()) {
            notify(NotificationType.INFO, "HUD preset is empty");
            return false;
        }
        int applied = 0;
        for (Module module : ModuleManager.INSTANCE.modules()) {
            if (!isHudModule(module)) continue;
            JsonElement value = modules.get(module.id());
            if (value == null || !value.isJsonObject()) continue;
            module.fromJson(withoutColors(module, value.getAsJsonObject()));
            applied++;
        }
        HUD.INSTANCE.reconcileAfterConfigLoad(true);
        List<String> fusion = new ArrayList<>();
        if (root.has(FUSION_KEY) && root.get(FUSION_KEY).isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray(FUSION_KEY)) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    fusion.add(element.getAsString());
                }
            }
        }
        HudFusionManager.restorePresetAttachments(fusion);
        notify(NotificationType.SUCCESS, "Applied " + label + " to " + applied + " HUD modules");
        return true;
    }

    private static PreviewData preview(JsonObject root) {
        if (root == null || !root.has(MODULES_KEY) || !root.get(MODULES_KEY).isJsonObject()) {
            return PreviewData.EMPTY;
        }
        Set<String> fused = new HashSet<>();
        if (root.has(FUSION_KEY) && root.get(FUSION_KEY).isJsonArray()) {
            for (JsonElement value : root.getAsJsonArray(FUSION_KEY)) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    fused.add(value.getAsString());
                }
            }
        }
        JsonObject modules = root.getAsJsonObject(MODULES_KEY);
        List<PreviewItem> items = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        float screenWidth = Math.max(1.0F, minecraft.getWindow().getGuiScaledWidth());
        float screenHeight = Math.max(1.0F, minecraft.getWindow().getGuiScaledHeight());
        for (Module module : ModuleManager.INSTANCE.modules()) {
            if (!(module instanceof HUDModuel hud)) continue;
            JsonElement value = modules.get(module.id());
            if (value == null || !value.isJsonObject()) continue;
            JsonObject object = value.getAsJsonObject();
            if (!readBoolean(object, "enabled", false)) continue;
            JsonObject settings = object.has("settings") && object.get("settings").isJsonObject()
                    ? object.getAsJsonObject("settings") : new JsonObject();
            float x = readNumber(settings, "X Position", 0.0F) / 1000.0F;
            float y = readNumber(settings, "Y Position", 0.0F) / 1000.0F;
            float scale = readNumber(settings, "Scale", 1.0F);
            if (scale > 10.0F) scale /= 100.0F;
            scale = Math.max(0.1F, Math.min(4.0F, scale));
            float previewWidth = readNumber(settings, "Width", hud.previewBaseWidth()) * scale;
            float previewHeight = readNumber(settings, "Height", hud.previewBaseHeight()) * scale;
            if ("music-lyrics-hud".equals(module.id())
                    && readBoolean(settings, "Music Player", false)) {
                previewHeight = 74.0F * scale;
            }
            items.add(new PreviewItem(module.id(), hud.editorLabel(), clamp01(x), clamp01(y),
                    clampSize(previewWidth / screenWidth), clampSize(previewHeight / screenHeight),
                    fused.contains(module.id())));
        }
        return new PreviewData(mergeFusion(items));
    }

    private static List<PreviewItem> mergeFusion(List<PreviewItem> source) {
        List<PreviewItem> members = source.stream()
                .filter(item -> item.fused() || "watermark_hud".equals(item.moduleId()))
                .toList();
        boolean hasAttachedMember = members.stream().anyMatch(PreviewItem::fused);
        if (!hasAttachedMember) return List.copyOf(source);

        PreviewItem anchor = members.stream()
                .filter(item -> "watermark_hud".equals(item.moduleId()))
                .findFirst().orElse(members.getFirst());
        float width = 0.0F;
        float height = 0.0F;
        int attached = 0;
        for (PreviewItem member : members) {
            width += member.width();
            height = Math.max(height, member.height());
            if (member.fused()) attached++;
        }
        PreviewItem group = new PreviewItem("fused_hud", "Watermark + " + attached,
                anchor.x(), anchor.y(), clampSize(width), clampSize(height), true);
        List<PreviewItem> merged = new ArrayList<>();
        merged.add(group);
        for (PreviewItem item : source) {
            if (!members.contains(item)) merged.add(item);
        }
        return List.copyOf(merged);
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) return fallback;
        try {
            return value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float readNumber(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return value.getAsFloat();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float clampSize(float value) {
        return Math.max(0.008F, Math.min(0.82F, value));
    }

    private Path personalFile(String name) throws IOException {
        return personalDirectory().resolve(name + ".json");
    }

    private static JsonObject withoutColors(Module module, JsonObject source) {
        JsonObject filtered = source.deepCopy();
        if (!filtered.has("settings") || !filtered.get("settings").isJsonObject()) return filtered;
        JsonObject settings = filtered.getAsJsonObject("settings");
        for (Setting<?> setting : module.settings()) {
            if (setting instanceof ColorSetting) settings.remove(setting.name());
        }
        return filtered;
    }

    private static boolean isHudModule(Module module) {
        return module == HUD.INSTANCE || module instanceof HUDModuel;
    }

    private static void notify(NotificationType type, String message) {
        NotificationManager.INSTANCE.post(type, "HUD Presets", message);
    }
}
