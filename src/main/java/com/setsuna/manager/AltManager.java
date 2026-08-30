package com.setsuna.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.setsuna.Setsuna;
import com.setsuna.accessor.MinecraftSessionAccessor;
import com.setsuna.util.alt.Alt;
import com.setsuna.util.alt.MicrosoftAuthService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Stores alternate accounts and replaces the active Minecraft session. */
public final class AltManager {

    public static final AltManager INSTANCE = new AltManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final MicrosoftAuthService microsoftAuthService = new MicrosoftAuthService();
    private final List<Alt> alts = new ArrayList<>();

    private Alt lastAlt;
    private boolean loaded;

    private AltManager() {
    }

    public synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        alts.clear();

        Path file = altFile();
        if (!Files.exists(file)) {
            return;
        }

        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
            for (JsonElement element : array) {
                Alt.CODEC.parse(JsonOps.INSTANCE, element)
                        .result()
                        .filter(alt -> !alt.getUsername().isBlank())
                        .ifPresent(this::addLoadedAlt);
            }
        } catch (Exception error) {
            Setsuna.LOGGER.error("Failed to load alternate accounts from {}", file, error);
        }
    }

    public synchronized List<Alt> getAlts() {
        load();
        return List.copyOf(alts);
    }

    public synchronized Optional<Alt> getLastAlt() {
        return Optional.ofNullable(lastAlt);
    }

    public synchronized boolean addOfflineAlt(String username) {
        load();
        String name = normalizeName(username);
        if (name.isBlank()) {
            return false;
        }
        if (alts.stream().anyMatch(alt -> alt.getUsername().equalsIgnoreCase(name))) {
            return false;
        }

        alts.add(new Alt(name));
        sortAlts();
        save();
        return true;
    }

    public synchronized void removeAlt(Alt alt) {
        load();
        if (alt == null) {
            return;
        }
        alts.removeIf(existing -> existing.getUsername().equalsIgnoreCase(alt.getUsername()));
        if (lastAlt != null && lastAlt.getUsername().equalsIgnoreCase(alt.getUsername())) {
            lastAlt = null;
        }
        save();
    }

    public void loginWithRefresh(Alt alt, MicrosoftAuthService.LoginCallback callback) {
        loginWithRefresh(alt, callback, true);
    }

    public void loginWithRefresh(
            Alt alt,
            MicrosoftAuthService.LoginCallback callback,
            boolean loginAfterSuccess
    ) {
        MicrosoftAuthService.LoginCallback listener = callback == null
                ? MicrosoftAuthService.LoginCallback.NOOP
                : callback;
        if (alt == null) {
            listener.onFailed(new IOException("Account is missing"));
            return;
        }

        if (alt.canRefresh() && (alt.isExpired() || alt.getAccessToken().isBlank())) {
            microsoftAuthService.refresh(alt, new MicrosoftAuthService.LoginCallback() {
                @Override
                public void setStatus(String status) {
                    listener.setStatus(status);
                }

                @Override
                public void onSucceed(Alt refreshedAlt) {
                    saveMicrosoftAlt(refreshedAlt);
                    if (loginAfterSuccess) {
                        login(refreshedAlt);
                    }
                    listener.onSucceed(refreshedAlt);
                }

                @Override
                public void onFailed(Exception error) {
                    listener.onFailed(error);
                }
            });
            return;
        }

        if (alt.isMicrosoft() && alt.isExpired()) {
            listener.onFailed(new IOException("Minecraft token expired. Add a fresh token."));
            return;
        }

        if (loginAfterSuccess) {
            login(alt);
        }
        listener.onSucceed(alt);
    }

    public void startMicrosoftDeviceLogin(MicrosoftAuthService.LoginCallback callback) {
        startMicrosoftDeviceLogin(callback, true);
    }

    public void startMicrosoftDeviceLogin(
            MicrosoftAuthService.LoginCallback callback,
            boolean loginAfterSuccess
    ) {
        MicrosoftAuthService.LoginCallback listener = callback == null
                ? MicrosoftAuthService.LoginCallback.NOOP
                : callback;
        microsoftAuthService.loginDeviceCode(new MicrosoftAuthService.LoginCallback() {
            @Override
            public void setStatus(String status) {
                listener.setStatus(status);
            }

            @Override
            public void onSucceed(Alt alt) {
                saveMicrosoftAlt(alt);
                if (loginAfterSuccess) {
                    login(alt);
                }
                listener.onSucceed(alt);
            }

            @Override
            public void onFailed(Exception error) {
                listener.onFailed(error);
            }
        });
    }

    public void loginWithMinecraftToken(
            String minecraftAccessToken,
            MicrosoftAuthService.LoginCallback callback
    ) {
        loginWithMinecraftToken(minecraftAccessToken, callback, true);
    }

    public void loginWithMinecraftToken(
            String minecraftAccessToken,
            MicrosoftAuthService.LoginCallback callback,
            boolean loginAfterSuccess
    ) {
        MicrosoftAuthService.LoginCallback listener = callback == null
                ? MicrosoftAuthService.LoginCallback.NOOP
                : callback;
        microsoftAuthService.loginMinecraftToken(minecraftAccessToken, new MicrosoftAuthService.LoginCallback() {
            @Override
            public void setStatus(String status) {
                listener.setStatus(status);
            }

            @Override
            public void onSucceed(Alt alt) {
                saveMicrosoftAlt(alt);
                if (loginAfterSuccess) {
                    login(alt);
                }
                listener.onSucceed(alt);
            }

            @Override
            public void onFailed(Exception error) {
                listener.onFailed(error);
            }
        });
    }

    public synchronized void login(Alt alt) {
        if (alt == null || alt.getUsername().isBlank()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Optional<String> xuid = alt.getXuid().isBlank()
                ? Optional.empty()
                : Optional.of(alt.getXuid());
        User user = new User(
                alt.getUsername(),
                alt.getProfileId(),
                alt.getAccessToken(),
                xuid,
                Optional.empty()
        );
        ((MinecraftSessionAccessor) minecraft).setsuna$setUser(user);
        lastAlt = alt;
        save();
    }

    public synchronized String getCurrentUsername() {
        return Minecraft.getInstance().getUser().getName();
    }

    private void addLoadedAlt(Alt alt) {
        if (alts.stream().noneMatch(existing -> existing.getUsername().equalsIgnoreCase(alt.getUsername()))) {
            alts.add(alt);
        }
        sortAlts();
    }

    private synchronized void saveMicrosoftAlt(Alt alt) {
        load();
        alts.removeIf(existing -> existing.getUsername().equalsIgnoreCase(alt.getUsername())
                || (!existing.getUserUUID().isBlank()
                && existing.getUserUUID().equalsIgnoreCase(alt.getUserUUID())));
        alts.add(alt);
        sortAlts();
        lastAlt = alt;
        save();
    }

    private void sortAlts() {
        alts.sort(Comparator.comparing(Alt::getUsername, String.CASE_INSENSITIVE_ORDER));
    }

    private void save() {
        Path file = altFile();
        try {
            Files.createDirectories(file.getParent());
            JsonArray array = new JsonArray();
            for (Alt alt : alts) {
                Alt.CODEC.encodeStart(JsonOps.INSTANCE, alt).result().ifPresent(array::add);
            }
            Files.writeString(
                    file,
                    gson.toJson(array),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException error) {
            Setsuna.LOGGER.error("Failed to save alternate accounts to {}", file, error);
        }
    }

    private Path altFile() {
        return Setsuna.mc().gameDirectory.toPath().resolve("." + Setsuna.MOD_ID).resolve("alts.json");
    }

    private static String normalizeName(String username) {
        String name = username == null ? "" : username.trim();
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        return name.replaceAll("[^A-Za-z0-9_]", "");
    }
}
