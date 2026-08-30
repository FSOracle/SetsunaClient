package com.setsuna.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.setsuna.Setsuna;
import com.setsuna.command.CommandManager;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.setting.Setting;
import com.setsuna.ui.hud.EpsilonHudModule;
import com.setsuna.ui.hud.HUD;
import com.setsuna.ui.hud.ModuleListHUD;
import com.setsuna.ui.hud.MusicLyricsHUD;
import com.setsuna.ui.hud.Notifications;
import com.setsuna.ui.hud.TargetHud;
import com.setsuna.ui.hud.WatermarkHUD;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Owns the complete client configuration.
 * <p>
 * Each profile is stored in its own file at {@code <gameDir>/.setsuna/profiles/<name>.json},
 * so {@code .config save hoplite} writes {@code profiles/hoplite.json} and
 * {@code .config save default} writes {@code profiles/default.json}. A slim
 * {@code <gameDir>/.setsuna/config.json} keeps only the pointer to the active
 * profile, global friend list and command settings. Older single-file bundles are migrated
 * into this layout automatically on first load.
 */
public final class ConfigManager {

    public static final ConfigManager INSTANCE = new ConfigManager();
    public static final String DEFAULT_PROFILE = "default";

    private static final int SCHEMA_VERSION = 4;
    private static final int MAX_PROFILE_CODE_POINTS = 64;
    private static final String SCHEMA_VERSION_KEY = "schemaVersion";
    private static final String ACTIVE_PROFILE_KEY = "activeProfile";
    private static final String PROFILES_KEY = "profiles";
    private static final String MODULES_KEY = "modules";
    private static final String FRIENDS_KEY = "friends";
    private static final String COMMANDS_KEY = "commands";
    private static final String COMMAND_PREFIX_KEY = "prefix";
    private static final String COMMAND_HINT_COUNT_KEY = "hintCount";
    private static final String HUD_LAYOUT_VERSION_KEY = "hudLayoutVersion";
    private static final String LEGACY_HUD_LAYOUT_VERSION_KEY = "_hud_layout_version";
    private static final int HUD_LAYOUT_VERSION = 1;
    private static final String TEMP_FILE_PREFIX = ".config-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";
    private static final long STALE_TEMP_FILE_AGE_MILLIS = 10L * 60L * 1000L;
    private static final int FILE_OPERATION_ATTEMPTS = 6;
    private static final long FILE_OPERATION_RETRY_MILLIS = 25L;

    /** Characters that are illegal in Windows file names; stripped from profile names. */
    private static final Set<Character> ILLEGAL_NAME_CHARS =
            Set.of('<', '>', ':', '"', '/', '\\', '|', '?', '*');

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonObject profiles = new JsonObject();
    private String activeProfile = DEFAULT_PROFILE;
    private boolean loaded;
    private boolean writesBlocked;

    private ConfigManager() {
    }

    /** Loads the profiles and applies the active one. */
    public synchronized void load() {
        if (!loaded) {
            loadBundle();
            return;
        }
        applyProfile(activeProfile);
    }

    /** Rewrites the active profile file and the slim state file. */
    public synchronized void save() {
        try {
            saveChecked();
        } catch (Exception error) {
            Setsuna.LOGGER.error("Failed to save config to {}", profileFile(activeProfile), error);
        }
    }

    /** Same as {@link #save()}, but exposes failures to command/UI callers. */
    public synchronized void saveChecked() throws IOException {
        ensureLoaded();
        ensureWritable();
        JsonObject previousProfiles = profiles.deepCopy();
        try {
            refreshActiveProfile();
            writeProfile(activeProfile);
            writeState();
        } catch (IOException | RuntimeException error) {
            profiles = previousProfiles;
            throw error;
        }
    }

    public synchronized List<String> listProfiles() {
        ensureLoaded();
        return profiles.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public synchronized String currentProfile() {
        ensureLoaded();
        return activeProfile;
    }

    /** Rescans profile files without applying one or changing the current runtime state. */
    public synchronized int refreshProfiles() {
        ensureLoaded();
        JsonObject previousProfiles = profiles;
        JsonObject runtimeSnapshot = snapshotProfile();

        profiles = new JsonObject();
        loadProfileFiles();

        if (findProfileName(activeProfile) == null) {
            profiles.add(activeProfile, runtimeSnapshot);
        }
        if (findProfileName(DEFAULT_PROFILE) == null) {
            JsonElement previousDefault = previousProfiles.get(DEFAULT_PROFILE);
            profiles.add(DEFAULT_PROFILE, previousDefault != null && previousDefault.isJsonObject()
                    ? previousDefault.deepCopy() : runtimeSnapshot.deepCopy());
        }
        return profiles.size();
    }

    /** Returns the client config directory, creating it when needed. */
    public synchronized Path configDirectory() throws IOException {
        Path directory = configDir();
        Files.createDirectories(directory);
        return directory;
    }

    /** Saves the current runtime state to its own {@code profiles/<name>.json} file. */
    public synchronized String saveProfile(String rawName) throws IOException {
        ensureLoaded();
        ensureWritable();
        String requested = sanitizeProfileName(rawName);
        String existing = findProfileName(requested);
        String name = existing != null ? existing
                : DEFAULT_PROFILE.equalsIgnoreCase(requested) ? DEFAULT_PROFILE : requested;
        JsonObject previousProfiles = profiles.deepCopy();
        String previousActive = activeProfile;
        try {
            profiles.add(name, snapshotProfile());
            activeProfile = name;
            writeProfile(name);
            writeState();
            return name;
        } catch (IOException | RuntimeException error) {
            profiles = previousProfiles;
            activeProfile = previousActive;
            throw error;
        }
    }

    /** Applies a stored profile and records it as the next startup profile. */
    public synchronized boolean loadProfile(String rawName) throws IOException {
        ensureLoaded();
        ensureWritable();
        String name = findProfileName(sanitizeProfileName(rawName));
        if (name == null) {
            return false;
        }
        JsonElement profile = profiles.get(name);
        if (profile == null || !profile.isJsonObject()) {
            return false;
        }
        JsonObject previousProfiles = profiles.deepCopy();
        String previousActive = activeProfile;
        JsonObject previousRuntime = snapshotProfile();
        try {
            activeProfile = name;
            applyProfile(name);
            refreshActiveProfile();
            writeProfile(activeProfile);
            writeState();
            return true;
        } catch (IOException | RuntimeException error) {
            profiles = previousProfiles;
            activeProfile = previousActive;
            restoreRuntime(previousRuntime);
            throw error;
        }
    }

    /** Deletes a profile file. The built-in default profile is retained. */
    public synchronized boolean deleteProfile(String rawName) throws IOException {
        ensureLoaded();
        ensureWritable();
        String name = findProfileName(sanitizeProfileName(rawName));
        if (name == null || DEFAULT_PROFILE.equals(name)) {
            return false;
        }

        JsonObject previousProfiles = profiles.deepCopy();
        String previousActive = activeProfile;
        JsonObject previousRuntime = snapshotProfile();
        try {
            profiles.remove(name);
            if (activeProfile.equalsIgnoreCase(name)) {
                ensureDefaultProfile();
                activeProfile = DEFAULT_PROFILE;
                applyProfile(activeProfile);
                refreshActiveProfile();
                writeProfile(activeProfile);
            }
            writeState();
            deleteProfileFile(name);
            return true;
        } catch (IOException | RuntimeException error) {
            profiles = previousProfiles;
            activeProfile = previousActive;
            restoreRuntime(previousRuntime);
            throw error;
        }
    }

    private void loadBundle() {
        cleanupStaleTemporaryFiles(configDir());
        cleanupStaleTemporaryFiles(profilesDir());
        Path state = stateFile();
        boolean rewrite = false;
        profiles = new JsonObject();

        try {
            if (Files.isRegularFile(state)) {
                JsonObject root = readObject(state);
                if (root.has(PROFILES_KEY) && root.get(PROFILES_KEY).isJsonObject()) {
                    // Legacy single-file bundle: split every embedded profile into its own file.
                    importBundled(root);
                    rewrite = true;
                } else {
                    importState(root);
                }
            } else {
                activeProfile = DEFAULT_PROFILE;
                FriendManager.INSTANCE.clear();
                loadCommandSettings(null);
            }

            loadProfileFiles();
            ensureDefaultProfile();

            String resolvedActive = findProfileName(activeProfile);
            if (resolvedActive == null) {
                activeProfile = DEFAULT_PROFILE;
                rewrite = true;
            } else {
                activeProfile = resolvedActive;
            }

            loaded = true;
            applyProfile(activeProfile);
            rewrite |= refreshActiveProfile();

            if (rewrite) {
                try {
                    writeAllProfiles();
                    writeState();
                } catch (IOException error) {
                    Setsuna.LOGGER.error("Loaded config but could not persist it to {}", profilesDir(), error);
                }
            }
        } catch (Exception error) {
            profiles = new JsonObject();
            activeProfile = DEFAULT_PROFILE;
            loaded = true;
            writesBlocked = Files.isRegularFile(state);
            FriendManager.INSTANCE.clear();
            loadCommandSettings(null);
            ensureDefaultProfile();
            Setsuna.LOGGER.error("Failed to load config from {}", state, error);
        }
    }

    /** Reads the slim state file: schema, active profile pointer and friends. */
    private void importState(JsonObject root) throws IOException {
        int version = readInt(root, SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        if (version > SCHEMA_VERSION) {
            throw new IOException("Unsupported config schema " + version
                    + " (maximum " + SCHEMA_VERSION + ")");
        }
        activeProfile = safeSanitize(readString(root, ACTIVE_PROFILE_KEY, DEFAULT_PROFILE));
        loadGlobalFriends(root.get(FRIENDS_KEY));
        loadCommandSettings(root.get(COMMANDS_KEY));
    }

    /** Splits an old bundled root into in-memory profiles for migration. */
    private void importBundled(JsonObject root) {
        JsonObject bundled = root.getAsJsonObject(PROFILES_KEY);
        for (Map.Entry<String, JsonElement> entry : bundled.entrySet()) {
            if (entry.getKey().isBlank() || !entry.getValue().isJsonObject()) {
                continue;
            }
            String name;
            try {
                name = sanitizeProfileName(entry.getKey());
            } catch (RuntimeException error) {
                Setsuna.LOGGER.warn("Skipping bundled profile with invalid name {}", entry.getKey());
                continue;
            }
            if (findProfileName(name) != null) {
                name = uniqueProfileName(name + " legacy");
            }
            profiles.add(name, normalizeProfile(entry.getValue().getAsJsonObject()));
        }
        activeProfile = safeSanitize(readString(root, ACTIVE_PROFILE_KEY, DEFAULT_PROFILE));
        loadGlobalFriends(root.get(FRIENDS_KEY));
        loadCommandSettings(root.get(COMMANDS_KEY));
    }

    /** Loads every {@code profiles/*.json} file not already present in memory. */
    private void loadProfileFiles() {
        Path directory = profilesDir();
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            for (Path file : files) {
                String filename = file.getFileName().toString();
                String stem = filename.substring(0, filename.length() - 5);
                String name;
                try {
                    name = sanitizeProfileName(stem);
                } catch (RuntimeException error) {
                    Setsuna.LOGGER.warn("Skipping profile file with invalid name {}", file);
                    continue;
                }
                if (findProfileName(name) != null) {
                    continue;
                }
                try {
                    profiles.add(name, normalizeProfile(readObject(file)));
                } catch (IOException error) {
                    Setsuna.LOGGER.warn("Skipping invalid profile file {}", file, error);
                }
            }
        } catch (IOException error) {
            Setsuna.LOGGER.warn("Failed to inspect profiles directory {}", directory, error);
        }
    }

    private void applyProfile(String name) {
        JsonElement element = profiles.get(name);
        if (element == null || !element.isJsonObject()) {
            return;
        }

        JsonObject profile = normalizeProfile(element.getAsJsonObject());
        profiles.add(name, profile);
        JsonObject modules = profile.getAsJsonObject(MODULES_KEY);

        for (Module module : ModuleManager.INSTANCE.modules()) {
            module.resetConfig();
        }
        migrateLegacyHud(modules);
        migrateClickGuiMode(modules);
        migrateHealthDetection(modules);

        for (Module module : ModuleManager.INSTANCE.modules()) {
            JsonElement moduleElement = moduleConfig(modules, module.id());
            if (moduleElement == null || !moduleElement.isJsonObject()) {
                continue;
            }
            try {
                module.fromJson(moduleElement.getAsJsonObject());
            } catch (RuntimeException error) {
                Setsuna.LOGGER.warn("Ignoring invalid config for module {} in profile {}",
                        module.id(), name, error);
            }
        }

        reconcileHudController(modules);
        migrateHudLayout(profile, modules);
    }

    private JsonObject snapshotProfile() {
        JsonObject modules = new JsonObject();
        JsonObject profile = new JsonObject();
        JsonElement current = profiles.get(activeProfile);
        if (current != null && current.isJsonObject()) {
            JsonObject currentProfile = normalizeProfile(current.getAsJsonObject());
            profile = currentProfile.deepCopy();
            modules = currentProfile.getAsJsonObject(MODULES_KEY).deepCopy();
        }
        modules.remove(LEGACY_HUD_LAYOUT_VERSION_KEY);
        modules.remove("client_hud");
        removeLegacyModuleKeys(modules);
        for (Module module : ModuleManager.INSTANCE.modules()) {
            JsonObject serialized = module.toJson();
            JsonElement previous = modules.get(module.id());
            modules.add(module.id(), previous != null && previous.isJsonObject()
                    ? mergeObjects(previous.getAsJsonObject(), serialized)
                    : serialized);
        }

        profile.addProperty(HUD_LAYOUT_VERSION_KEY, HUD_LAYOUT_VERSION);
        profile.add(MODULES_KEY, modules);
        profile.remove(FRIENDS_KEY);
        return profile;
    }

    private boolean refreshActiveProfile() {
        JsonObject snapshot = snapshotProfile();
        JsonElement previous = profiles.get(activeProfile);
        boolean changed = previous == null || !snapshot.equals(previous);
        profiles.add(activeProfile, snapshot);
        return changed;
    }

    private void restoreRuntime(JsonObject runtimeSnapshot) {
        JsonObject storedProfiles = profiles;
        JsonObject temporaryProfiles = profiles.deepCopy();
        temporaryProfiles.add(activeProfile, runtimeSnapshot.deepCopy());
        profiles = temporaryProfiles;
        try {
            applyProfile(activeProfile);
        } finally {
            profiles = storedProfiles;
        }
    }

    private static JsonObject mergeObjects(JsonObject base, JsonObject update) {
        JsonObject merged = base.deepCopy();
        for (Map.Entry<String, JsonElement> entry : update.entrySet()) {
            JsonElement existing = merged.get(entry.getKey());
            JsonElement replacement = entry.getValue();
            if (existing != null && existing.isJsonObject() && replacement.isJsonObject()) {
                merged.add(entry.getKey(), mergeObjects(
                        existing.getAsJsonObject(), replacement.getAsJsonObject()));
            } else {
                merged.add(entry.getKey(), replacement.deepCopy());
            }
        }
        return merged;
    }

    private JsonObject normalizeProfile(JsonObject source) {
        JsonElement modules = source.get(MODULES_KEY);
        if (modules != null && modules.isJsonObject()) {
            JsonObject normalized = source.deepCopy();
            if (!normalized.has(HUD_LAYOUT_VERSION_KEY)) {
                normalized.addProperty(HUD_LAYOUT_VERSION_KEY,
                        readInt(modules.getAsJsonObject(), LEGACY_HUD_LAYOUT_VERSION_KEY, 0));
            }
            return normalized;
        }
        return legacyProfile(source);
    }

    private static JsonElement moduleConfig(JsonObject modules, String moduleId) {
        JsonElement current = modules.get(moduleId);
        if (current != null && current.isJsonObject()) {
            return current;
        }
        String legacyKey = legacyModuleKey(moduleId);
        return legacyKey == null ? current : modules.get(legacyKey);
    }

    private static String legacyModuleKey(String moduleId) {
        return switch (moduleId) {
            case "cheststealer" -> "stealer";
            default -> null;
        };
    }

    private static void removeLegacyModuleKeys(JsonObject modules) {
        modules.remove("stealer");
    }

    private JsonObject legacyProfile(JsonObject modules) {
        JsonObject profile = new JsonObject();
        profile.addProperty(HUD_LAYOUT_VERSION_KEY,
                readInt(modules, LEGACY_HUD_LAYOUT_VERSION_KEY, 0));
        profile.add(MODULES_KEY, modules.deepCopy());
        return profile;
    }

    /** Writes the slim state file: schema, active profile pointer and friends. */
    private void writeState() throws IOException {
        ensureWritable();
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        root.addProperty(ACTIVE_PROFILE_KEY, activeProfile);
        JsonArray friends = new JsonArray();
        FriendManager.INSTANCE.entries().stream()
                .sorted(Comparator.comparing(FriendManager.Friend::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(friend -> {
                    JsonObject object = new JsonObject();
                    object.addProperty("name", friend.name());
                    if (friend.alias() != null && !friend.alias().isBlank()) {
                        object.addProperty("alias", friend.alias());
                    }
                    friends.add(object);
                });
        root.add(FRIENDS_KEY, friends);
        JsonObject commands = new JsonObject();
        commands.addProperty(COMMAND_PREFIX_KEY, CommandManager.INSTANCE.prefix());
        commands.addProperty(COMMAND_HINT_COUNT_KEY, CommandManager.INSTANCE.hintCount());
        root.add(COMMANDS_KEY, commands);
        writeAtomically(stateFile(), gson.toJson(root));
    }

    /** Writes a single profile to its own {@code profiles/<name>.json} file. */
    private void writeProfile(String name) throws IOException {
        ensureWritable();
        JsonElement profile = profiles.get(name);
        if (profile == null || !profile.isJsonObject()) {
            return;
        }
        writeAtomically(profileFile(name), gson.toJson(profile));
    }

    private void writeAllProfiles() throws IOException {
        for (String name : profiles.keySet()) {
            writeProfile(name);
        }
    }

    private void deleteProfileFile(String name) {
        try {
            Files.deleteIfExists(profileFile(name));
        } catch (IOException error) {
            Setsuna.LOGGER.warn("Removed profile {} from memory but could not delete its file", name, error);
        }
    }

    private static JsonObject readObject(Path file) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException error) {
            throw new IOException("Invalid JSON in " + file, error);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("Config root must be a JSON object: " + file);
        }
        return parsed.getAsJsonObject();
    }

    private static void writeAtomically(Path target, String json) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
        try {
            ByteBuffer contents = StandardCharsets.UTF_8.encode(json);
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                while (contents.hasRemaining()) {
                    channel.write(contents);
                }
                channel.force(true);
            }
            moveReplacingWithRetry(temporary, target);
        } finally {
            try {
                deleteWithRetry(temporary);
            } catch (IOException cleanupError) {
                Setsuna.LOGGER.warn("Could not remove temporary config file {}", temporary, cleanupError);
            }
        }
    }

    private static void moveReplacingWithRetry(Path source, Path target) throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt < FILE_OPERATION_ATTEMPTS; attempt++) {
            try {
                try {
                    Files.move(source, target,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException error) {
                lastError = error;
                pauseBeforeRetry(attempt);
            }
        }
        throw lastError;
    }

    private static void deleteWithRetry(Path file) throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt < FILE_OPERATION_ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(file);
                return;
            } catch (IOException error) {
                lastError = error;
                pauseBeforeRetry(attempt);
            }
        }
        throw lastError;
    }

    private static void pauseBeforeRetry(int attempt) throws IOException {
        if (attempt + 1 >= FILE_OPERATION_ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(FILE_OPERATION_RETRY_MILLIS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while retrying a config file operation", error);
        }
    }

    private static void cleanupStaleTemporaryFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - STALE_TEMP_FILE_AGE_MILLIS;
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.getFileName().toString();
                        return name.startsWith(TEMP_FILE_PREFIX) && name.endsWith(TEMP_FILE_SUFFIX);
                    })
                    .forEach(file -> {
                        try {
                            if (Files.getLastModifiedTime(file).toMillis() < cutoff) {
                                Files.deleteIfExists(file);
                            }
                        } catch (IOException error) {
                            Setsuna.LOGGER.debug("Could not remove stale temporary config file {}", file, error);
                        }
                    });
        } catch (IOException error) {
            Setsuna.LOGGER.debug("Could not inspect temporary config files in {}", directory, error);
        }
    }

    private void ensureLoaded() {
        if (!loaded) {
            loadBundle();
        }
    }

    private void ensureWritable() throws IOException {
        if (writesBlocked) {
            throw new IOException("Refusing to overwrite an unreadable or newer config file");
        }
    }

    private void ensureDefaultProfile() {
        String existing = findProfileName(DEFAULT_PROFILE);
        if (existing == null) {
            profiles.add(DEFAULT_PROFILE, snapshotProfile());
        } else if (!DEFAULT_PROFILE.equals(existing)) {
            JsonElement profile = profiles.remove(existing);
            profiles.add(DEFAULT_PROFILE, profile);
        }
    }

    private String findProfileName(String requested) {
        if (requested == null) {
            return null;
        }
        for (String name : profiles.keySet()) {
            if (name.equalsIgnoreCase(requested) && profiles.get(name).isJsonObject()) {
                return name;
            }
        }
        return null;
    }

    private String uniqueProfileName(String base) {
        if (findProfileName(base) == null) {
            return base;
        }
        for (int index = 2; ; index++) {
            String candidate = base + " " + index;
            if (findProfileName(candidate) == null) {
                return candidate;
            }
        }
    }

    private Path configDir() {
        return Setsuna.mc().gameDirectory.toPath().resolve(Setsuna.MOD_ID);
    }

    private Path stateFile() {
        return configDir().resolve("config.json");
    }

    private Path profilesDir() {
        return configDir().resolve("profiles");
    }

    private Path profileFile(String name) {
        return profilesDir().resolve(name + ".json");
    }

    private static String safeSanitize(String rawName) {
        try {
            return sanitizeProfileName(rawName);
        } catch (RuntimeException ignored) {
            return DEFAULT_PROFILE;
        }
    }

    private static String sanitizeProfileName(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("Config name cannot be null");
        }
        String normalized = Normalizer.normalize(rawName, Normalizer.Form.NFKC).trim();
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - 5).trim();
        }

        StringBuilder result = new StringBuilder();
        int count = 0;
        for (int offset = 0; offset < normalized.length() && count < MAX_PROFILE_CODE_POINTS; ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) {
                continue;
            }
            if (codePoint <= Character.MAX_VALUE && ILLEGAL_NAME_CHARS.contains((char) codePoint)) {
                continue;
            }
            result.appendCodePoint(codePoint);
            count++;
        }

        // Windows forbids trailing dots and spaces in file names.
        int end = result.length();
        while (end > 0 && (result.charAt(end - 1) == '.' || result.charAt(end - 1) == ' ')) {
            end--;
        }
        String name = result.substring(0, end).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Config name cannot be empty");
        }
        if (isReservedDeviceName(name)) {
            name = name + "_";
        }
        return name;
    }

    private static boolean isReservedDeviceName(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        int dot = upper.indexOf('.');
        String base = dot >= 0 ? upper.substring(0, dot) : upper;
        if (base.equals("CON") || base.equals("PRN") || base.equals("AUX") || base.equals("NUL")) {
            return true;
        }
        return (base.startsWith("COM") || base.startsWith("LPT"))
                && base.length() == 4
                && base.charAt(3) >= '1' && base.charAt(3) <= '9';
    }

    private static String readString(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        String result = value.getAsString();
        return result.isBlank() ? fallback : result;
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean loadGlobalFriends(JsonElement element) {
        FriendManager.INSTANCE.clear();
        if (element == null || !element.isJsonArray()) {
            return false;
        }
        for (JsonElement friend : element.getAsJsonArray()) {
            if (friend.isJsonPrimitive() && friend.getAsJsonPrimitive().isString()) {
                FriendManager.INSTANCE.add(friend.getAsString());
                continue;
            }
            if (!friend.isJsonObject()) {
                continue;
            }
            JsonObject object = friend.getAsJsonObject();
            String name = readString(object, "name", "");
            if (!FriendManager.INSTANCE.add(name)) {
                continue;
            }
            String alias = readString(object, "alias", "");
            if (!alias.isBlank()) {
                FriendManager.INSTANCE.setAlias(name, alias);
            }
        }
        return true;
    }

    private static void loadCommandSettings(JsonElement element) {
        CommandManager manager = CommandManager.INSTANCE;
        manager.setPrefix(CommandManager.DEFAULT_PREFIX);
        manager.setHintCount(CommandManager.DEFAULT_HINT_COUNT);
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject commands = element.getAsJsonObject();
        String prefix = readString(commands, COMMAND_PREFIX_KEY, CommandManager.DEFAULT_PREFIX);
        try {
            manager.setPrefix(prefix);
        } catch (IllegalArgumentException error) {
            Setsuna.LOGGER.warn("Ignoring invalid command prefix in config", error);
        }
        manager.setHintCount(readInt(
                commands, COMMAND_HINT_COUNT_KEY, CommandManager.DEFAULT_HINT_COUNT));
    }

    private static void migrateLegacyHud(JsonObject modules) {
        JsonElement legacyElement = modules.get("client_hud");
        if (legacyElement == null || !legacyElement.isJsonObject()) return;
        JsonObject legacy = legacyElement.getAsJsonObject();
        JsonObject settings = legacy.has("settings") && legacy.get("settings").isJsonObject()
                ? legacy.getAsJsonObject("settings") : new JsonObject();
        boolean masterEnabled = readBoolean(legacy, "enabled", true);

        if (!modules.has(WatermarkHUD.INSTANCE.id())) {
            applyLegacy(settings, "Watermark X", WatermarkHUD.INSTANCE.xPosition);
            applyLegacy(settings, "Watermark Y", WatermarkHUD.INSTANCE.yPosition);
            WatermarkHUD.INSTANCE.setEnabled(masterEnabled && legacyBoolean(settings, "Watermark", true));
        }
        if (!modules.has(ModuleListHUD.INSTANCE.id())) {
            applyLegacy(settings, "Array List X", ModuleListHUD.INSTANCE.xPosition);
            applyLegacy(settings, "Array List Y", ModuleListHUD.INSTANCE.yPosition);
            ModuleListHUD.INSTANCE.setEnabled(masterEnabled && legacyBoolean(settings, "Array List", true));
        }
        if (!modules.has(MusicLyricsHUD.INSTANCE.id())) {
            applyLegacy(settings, "Music X", MusicLyricsHUD.INSTANCE.xPosition);
            applyLegacy(settings, "Music Y", MusicLyricsHUD.INSTANCE.yPosition);
        }
    }

    private static void migrateHealthDetection(JsonObject modules) {
        JsonElement clickGuiElement = moduleConfig(modules, ClickGui.INSTANCE.id());
        if (clickGuiElement == null || !clickGuiElement.isJsonObject()) return;
        JsonObject clickGui = clickGuiElement.getAsJsonObject();
        if (!clickGui.has("settings") || !clickGui.get("settings").isJsonObject()) return;

        String settingName = TargetHud.INSTANCE.healthDetection.name();
        JsonElement legacyValue = clickGui.getAsJsonObject("settings").remove(settingName);
        if (legacyValue == null) return;

        JsonElement targetElement = moduleConfig(modules, TargetHud.INSTANCE.id());
        JsonObject target;
        if (targetElement != null && targetElement.isJsonObject()) {
            target = targetElement.getAsJsonObject();
        } else {
            target = new JsonObject();
            modules.add(TargetHud.INSTANCE.id(), target);
        }
        JsonObject targetSettings;
        if (target.has("settings") && target.get("settings").isJsonObject()) {
            targetSettings = target.getAsJsonObject("settings");
        } else {
            targetSettings = new JsonObject();
            target.add("settings", targetSettings);
        }
        if (!targetSettings.has(settingName)) {
            targetSettings.add(settingName, legacyValue.deepCopy());
        }
    }

    private static void migrateClickGuiMode(JsonObject modules) {
        JsonElement clickGuiElement = moduleConfig(modules, ClickGui.INSTANCE.id());
        if (clickGuiElement == null || !clickGuiElement.isJsonObject()) return;
        JsonObject clickGui = clickGuiElement.getAsJsonObject();
        if (!clickGui.has("settings") || !clickGui.get("settings").isJsonObject()) return;
        JsonObject settings = clickGui.getAsJsonObject("settings");
        JsonElement mode = settings.get("Mode");
        if (mode != null && mode.isJsonPrimitive()
                && ("Modern".equalsIgnoreCase(mode.getAsString())
                || "Panel".equalsIgnoreCase(mode.getAsString())
                || "Flux".equalsIgnoreCase(mode.getAsString()))) {
            settings.addProperty("Mode", "Pop");
        }
    }

    private static void reconcileHudController(JsonObject modules) {
        JsonElement controller = modules.get(HUD.INSTANCE.id());
        HUD.INSTANCE.reconcileAfterConfigLoad(controller != null && controller.isJsonObject());
    }

    private static void migrateHudLayout(JsonObject profile, JsonObject modules) {
        int version = readInt(profile, HUD_LAYOUT_VERSION_KEY,
                readInt(modules, LEGACY_HUD_LAYOUT_VERSION_KEY, 0));
        if (version >= HUD_LAYOUT_VERSION) return;
        for (Module module : ModuleManager.INSTANCE.modules()) {
            if (module instanceof EpsilonHudModule hud) {
                hud.resetPosition();
            }
        }
        Notifications.INSTANCE.position.reset();
        profile.addProperty(HUD_LAYOUT_VERSION_KEY, HUD_LAYOUT_VERSION);
    }

    private static void applyLegacy(JsonObject settings, String name, Setting<?> target) {
        JsonElement value = settings.get(name);
        if (value == null) return;
        try {
            target.fromJson(value);
        } catch (RuntimeException ignored) {
            // A malformed legacy field must not prevent the rest of the config from loading.
        }
    }

    private static boolean legacyBoolean(JsonObject settings, String name, boolean fallback) {
        return readBoolean(settings, name, fallback);
    }

    private static boolean readBoolean(JsonObject object, String name, boolean fallback) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        try {
            return value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
