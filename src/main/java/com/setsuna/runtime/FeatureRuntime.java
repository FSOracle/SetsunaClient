package com.setsuna.runtime;

import com.setsuna.Setsuna;
import com.setsuna.command.CommandManager;
import com.setsuna.config.ConfigManager;
import com.setsuna.manager.AltManager;
import com.setsuna.manager.HealthManager;
import com.setsuna.manager.RotationManager;
import com.setsuna.manager.target.TargetManager;
import com.setsuna.module.ModuleManager;
import com.setsuna.notification.NotificationManager;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.script.LuaScriptManager;
import tritium.ncm.music.CloudMusic;

import java.util.concurrent.CompletableFuture;

/** Starts local client features with Minecraft and saves them at shutdown. */
public final class FeatureRuntime {

    public static final FeatureRuntime INSTANCE = new FeatureRuntime();

    private volatile boolean active;
    private boolean musicStarted;
    private int musicGeneration;

    private FeatureRuntime() {
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void activate() {
        if (active) {
            return;
        }
        try {
            // Config restoration may enable modules while initialization is running.
            active = true;
            AltManager.INSTANCE.load();
            // Subscribe stateful managers before configs enable packet-driven modules.
            RotationManager.INSTANCE.getClass();
            HealthManager.INSTANCE.getClass();
            TargetManager.INSTANCE.getClass();
            ModuleManager.INSTANCE.init();
            LuaScriptManager.INSTANCE.loadAll();
            ConfigManager.INSTANCE.load();
            CommandManager.INSTANCE.init();
            NotificationManager.INSTANCE.start();
            musicStarted = true;
            int generation = ++musicGeneration;
            CompletableFuture.runAsync(() -> {
                if (isActive()) {
                    CloudMusic.initNCM();
                }
                synchronized (FeatureRuntime.this) {
                    if (generation != musicGeneration || !active) {
                        CloudMusic.onStop();
                    }
                }
            }).exceptionally(error -> {
                Setsuna.LOGGER.error("Failed to initialize online music", error);
                return null;
            });
            Setsuna.LOGGER.info("Client runtime loaded {} modules.",
                    ModuleManager.INSTANCE.modules().size());
        } catch (Throwable error) {
            stopFeatures();
            throw new IllegalStateException("Failed to initialize the client runtime", error);
        }
    }

    private void stopFeatures() {
        if (ModuleManager.INSTANCE.isInitialized()) {
            ModuleManager.INSTANCE.disableAll();
            LuaScriptManager.INSTANCE.unloadAll();
        }
        NotificationManager.INSTANCE.clear();
        if (musicStarted) {
            musicGeneration++;
            CloudMusic.onStop();
            musicStarted = false;
        }
        active = false;
    }

    public synchronized void shutdown() {
        try {
            if (active) {
                ConfigManager.INSTANCE.save();
            }
        } finally {
            try {
                stopFeatures();
            } finally {
                SkijaRenderer.close();
            }
        }
    }
}
