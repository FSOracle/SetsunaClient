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
import com.viaversion.setsunavia.protocoltranslator.ProtocolTranslator;
import tritium.ncm.music.CloudMusic;

import java.util.concurrent.CompletableFuture;

/** Starts and stops the client feature runtime without an account gate. */
public final class FeatureRuntime {

    public static final FeatureRuntime INSTANCE = new FeatureRuntime();

    private boolean active;
    private boolean musicStarted;
    private int musicGeneration;

    private FeatureRuntime() {
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void activate() {
        if (active) return;
        try {
            active = true;
            AltManager.INSTANCE.load();
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
                CloudMusic.initNCM();
                synchronized (FeatureRuntime.this) {
                    if (generation != musicGeneration || !active) {
                        CloudMusic.onStop();
                    }
                }
            });
            Setsuna.LOGGER.info("Loaded {} modules.", ModuleManager.INSTANCE.modules().size());
        } catch (Throwable error) {
            if (ModuleManager.INSTANCE.isInitialized()) {
                LuaScriptManager.INSTANCE.unloadAll();
            }
            active = false;
            Setsuna.LOGGER.error("Feature runtime initialization failed", error);
        }
    }

    public synchronized void deactivate() {
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
        ProtocolTranslator.setTargetVersion(ProtocolTranslator.NATIVE_VERSION);
    }

    public synchronized void shutdown() {
        ConfigManager.INSTANCE.save();
        if (ModuleManager.INSTANCE.isInitialized()) {
            LuaScriptManager.INSTANCE.unloadAll();
        }
        if (musicStarted) {
            musicGeneration++;
            CloudMusic.onStop();
            musicStarted = false;
        }
        active = false;
        SkijaRenderer.close();
    }
}
