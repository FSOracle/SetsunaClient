package com.setsuna.module;

import com.setsuna.Setsuna;
import com.setsuna.event.EventBus;
import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyInputEvent;
import com.setsuna.event.events.MouseButtonEvent;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.module.modules.FontModule;
import com.setsuna.module.modules.render.FullBright;
import com.setsuna.module.modules.render.HoleESP;
import com.setsuna.module.modules.player.NetEaseMusicModule;
import com.setsuna.module.modules.player.FakePlayer;
import com.setsuna.module.modules.movement.NoJumpDelay;
import com.setsuna.module.modules.movement.NoFall;
import com.setsuna.module.modules.movement.NoSlow;
import com.setsuna.module.modules.movement.KeepSprint;
import com.setsuna.module.modules.render.HudEditorModule;
import com.setsuna.module.modules.movement.InvMove;
import com.setsuna.module.modules.movement.Sprint;
import com.setsuna.module.modules.movement.FlatElytraFly;
import com.setsuna.module.modules.combat.FakeLag;
import com.setsuna.module.modules.combat.Backtrack;
import com.setsuna.module.modules.combat.Criticals;
import com.setsuna.module.modules.combat.AutoHitCrystal;
import com.setsuna.module.modules.combat.AutoTotem;
import com.setsuna.module.modules.combat.AntiBot;
import com.setsuna.module.modules.combat.Burrow;
import com.setsuna.module.modules.combat.KillAura;
import com.setsuna.module.modules.combat.KillAuraPlus;
import com.setsuna.module.modules.combat.MaceAura;
import com.setsuna.module.modules.combat.SpearKill;
import com.setsuna.module.modules.combat.Surround;
import com.setsuna.module.modules.combat.ZealotCrystalPlus;
import com.setsuna.module.modules.misc.MiddleClickFriend;
import com.setsuna.module.modules.movement.Scaffold;
import com.setsuna.module.modules.movement.Speed;
import com.setsuna.module.modules.movement.MovementFix;
import com.setsuna.module.modules.movement.Velocity;
import com.setsuna.module.modules.player.AntiResourcePack;
import com.setsuna.module.modules.player.AntiWeb;
import com.setsuna.module.modules.player.AutoMLG;
import com.setsuna.module.modules.player.AutoTool;
import com.setsuna.module.modules.player.BedAura;
import com.setsuna.module.modules.player.FastCraftModule;
import com.setsuna.module.modules.player.GhostHand;
import com.setsuna.module.modules.player.InvManager;
import com.setsuna.module.modules.player.ChestStealer;
import com.setsuna.module.modules.player.FastBreak;
import com.setsuna.module.modules.player.PacketEat;
import com.setsuna.module.modules.render.BlockHighlight;
import com.setsuna.module.modules.render.CameraClip;
import com.setsuna.module.modules.render.Chams;
import com.setsuna.module.modules.render.Compass;
import com.setsuna.module.modules.render.ESP;
import com.setsuna.module.modules.render.ItemTag;
import com.setsuna.module.modules.render.KillEffect;
import com.setsuna.module.modules.render.DeltaForceStyle;
import com.setsuna.module.modules.render.LegendWatch;
import com.setsuna.module.modules.render.NameTags;
import com.setsuna.module.modules.render.NoRender;
import com.setsuna.module.modules.render.OreTracers;
import com.setsuna.module.modules.render.SpawnerFinder;
import com.setsuna.module.modules.render.TeamViewer;
import com.setsuna.module.modules.render.Tracers;
import com.setsuna.module.modules.render.UHCDetector;
import com.setsuna.module.modules.render.Xray;
import com.setsuna.ui.hud.BPSHUD;
import com.setsuna.ui.hud.CoordinatesHUD;
import com.setsuna.ui.hud.FPSHUD;
import com.setsuna.ui.hud.HUD;
import com.setsuna.ui.hud.HudFusionManager;
import com.setsuna.ui.hud.InventoryHUD;
import com.setsuna.ui.hud.KeybindOverlayHUD;
import com.setsuna.ui.hud.ModuleListHUD;
import com.setsuna.ui.hud.MusicLyricsHUD;
import com.setsuna.ui.hud.TargetHud;
import com.setsuna.ui.hud.Notifications;
import com.setsuna.ui.hud.PotionHUD;
import com.setsuna.ui.hud.RadarHUD;
import com.setsuna.ui.hud.ScaffoldBlockHUD;
import com.setsuna.ui.hud.ScoreboardHUD;
import com.setsuna.ui.hud.SessionInfoHUD;
import com.setsuna.ui.hud.WatermarkHUD;
import com.setsuna.util.client.InputBind;
import com.setsuna.util.network.BlinkManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns every {@link Module} instance: registers them, binds their i18n keys, and
 * dispatches key-bind presses. Registered once from {@link com.setsuna.SetsunaClient}.
 */
public final class ModuleManager {

    public static final ModuleManager INSTANCE = new ModuleManager();

    private final Minecraft mc = Setsuna.mc();
    private final List<Module> modules = new ArrayList<>();
    private final Map<Module, SmartKeyboardState> smartKeyboardStates = new IdentityHashMap<>();
    private final Map<Module, SmartMouseState> smartMouseStates = new IdentityHashMap<>();
    private boolean initialized;

    private static final long SMART_MOUSE_HOLD_THRESHOLD_NANOS = 200_000_000L;

    private enum SmartKeyboardState {
        PENDING_ENABLED,
        PENDING_DISABLED,
        HOLDING
    }

    private record SmartMouseState(boolean previouslyEnabled, long pressTimeNanos) {
    }

    private ModuleManager() {
    }

    /** Instantiates and registers all built-in modules, then listens for keybinds. */
    public void init() {
        if (initialized) {
            return;
        }
        register(ClickGui.INSTANCE);
        register(FontModule.INSTANCE);
        register(HudEditorModule.INSTANCE);
        register(NetEaseMusicModule.INSTANCE);
        register(AntiBot.INSTANCE);
        register(KillAura.INSTANCE);
        register(KillAuraPlus.INSTANCE);
        register(AutoHitCrystal.INSTANCE);
        register(AutoTotem.INSTANCE);
        register(Burrow.INSTANCE);
        register(MaceAura.INSTANCE);
        register(SpearKill.INSTANCE);
        register(Surround.INSTANCE);
        register(ZealotCrystalPlus.INSTANCE);
        register(Criticals.INSTANCE);
        register(FakeLag.INSTANCE);
        register(Backtrack.INSTANCE);
        register(MiddleClickFriend.INSTANCE);
        register(Sprint.INSTANCE);
        register(InvMove.INSTANCE);
        register(NoFall.INSTANCE);
        register(NoSlow.INSTANCE);
        register(KeepSprint.INSTANCE);
        register(FullBright.INSTANCE);
        register(Speed.INSTANCE);
        register(FlatElytraFly.INSTANCE);
        register(MovementFix.INSTANCE);
        register(BlockHighlight.INSTANCE);
        register(CameraClip.INSTANCE);
        register(Compass.INSTANCE);
        register(DeltaForceStyle.INSTANCE);
        register(Tracers.INSTANCE);
        register(ItemTag.INSTANCE);
        register(KillEffect.INSTANCE);
        register(ESP.INSTANCE);
        register(OreTracers.INSTANCE);
        register(Xray.INSTANCE);
        register(Chams.INSTANCE);
        register(UHCDetector.INSTANCE);
        register(SpawnerFinder.INSTANCE);
        register(HoleESP.INSTANCE);
        register(NameTags.INSTANCE);
        register(NoRender.INSTANCE);
        register(LegendWatch.INSTANCE);
        register(Scaffold.INSTANCE);
        register(Velocity.INSTANCE);
        register(AntiResourcePack.INSTANCE);
        register(AntiWeb.INSTANCE);
        register(AutoMLG.INSTANCE);
        register(AutoTool.INSTANCE);
        register(BedAura.INSTANCE);
        register(GhostHand.INSTANCE);
        register(InvManager.INSTANCE);
        register(NoJumpDelay.INSTANCE);
        register(FastCraftModule.INSTANCE);
        register(ChestStealer.INSTANCE);
        register(FastBreak.INSTANCE);
        register(PacketEat.INSTANCE);
        register(FakePlayer.INSTANCE);
        register(WatermarkHUD.INSTANCE);
        register(ModuleListHUD.INSTANCE);
        register(FPSHUD.INSTANCE);
        register(BPSHUD.INSTANCE);
        register(CoordinatesHUD.INSTANCE);
        register(InventoryHUD.INSTANCE);
        register(KeybindOverlayHUD.INSTANCE);
        register(PotionHUD.INSTANCE);
        register(RadarHUD.INSTANCE);
        register(TeamViewer.INSTANCE);
        register(ScaffoldBlockHUD.INSTANCE);
        register(ScoreboardHUD.INSTANCE);
        register(MusicLyricsHUD.INSTANCE);
        register(TargetHud.INSTANCE);
        register(SessionInfoHUD.INSTANCE);
        register(Notifications.INSTANCE);
        register(HUD.INSTANCE);
        EventBus.INSTANCE.subscribe(HudFusionManager.INSTANCE);
        EventBus.INSTANCE.subscribe(BlinkManager.INSTANCE);
        EventBus.INSTANCE.subscribe(this);
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /** Disables all active behavior when the authentication lease closes. */
    public void disableAll() {
        smartKeyboardStates.clear();
        smartMouseStates.clear();
        for (Module module : modules) {
            module.setEnabled(false);
        }
    }

    private void register(Module module) {
        if (modules.stream().anyMatch(existing -> existing.id().equals(module.id()))) {
            throw new IllegalStateException("Duplicate module id: " + module.id());
        }
        modules.add(module);
        module.bindI18n();
        module.captureConfigDefaults();
    }

    /** Registers a runtime-owned module such as one declared by a Lua script. */
    public synchronized void registerDynamic(Module module) {
        if (!initialized) {
            throw new IllegalStateException("Module manager is not initialized");
        }
        register(module);
    }

    /** Removes one runtime-owned module after stopping all of its active behavior. */
    public synchronized void unregisterDynamic(Module module) {
        if (!modules.contains(module)) {
            return;
        }
        module.setEnabled(false);
        smartKeyboardStates.remove(module);
        smartMouseStates.remove(module);
        modules.remove(module);
    }

    public List<Module> modules() {
        return modules;
    }

    /** All modules in a category, in registration order. */
    public List<Module> modulesIn(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.category() == category) {
                result.add(module);
            }
        }
        return result;
    }

    @Listen
    private void onKey(KeyInputEvent event) {
        if (event.key() == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }

        switch (event.action()) {
            case GLFW.GLFW_PRESS -> {
                if (mc.screen != null || mc.options.keyDebugModifier.isDown()) {
                    return;
                }
                for (Module module : modules) {
                    InputBind bind = module.bind();
                    if (!bind.matchesKey(event.key()) || !bind.matchesModifiers(event.modifiers())) {
                        continue;
                    }
                    handleKeyboardPress(module, bind);
                }
            }
            case GLFW.GLFW_REPEAT -> {
                for (Module module : modules) {
                    InputBind bind = module.bind();
                    if (bind.action() == InputBind.BindAction.SMART
                            && bind.matchesKey(event.key())
                            && smartKeyboardStates.containsKey(module)) {
                        smartKeyboardStates.put(module, SmartKeyboardState.HOLDING);
                    }
                }
            }
            case GLFW.GLFW_RELEASE -> {
                for (Module module : modules) {
                    InputBind bind = module.bind();
                    if (!module.isToggleable() || !bind.isAffectedByKeyRelease(event.key())) {
                        continue;
                    }
                    switch (bind.action()) {
                        case HOLD -> module.setEnabled(false);
                        case SMART -> {
                            SmartKeyboardState state = smartKeyboardStates.remove(module);
                            if (state != null) {
                                module.setEnabled(state == SmartKeyboardState.PENDING_DISABLED);
                            }
                        }
                        case TOGGLE -> {
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    private void handleKeyboardPress(Module module, InputBind bind) {
        if (!module.isToggleable()) {
            module.trigger();
            return;
        }
        switch (bind.action()) {
            case TOGGLE -> module.toggle();
            case HOLD -> module.setEnabled(true);
            case SMART -> {
                smartKeyboardStates.put(module, module.isEnabled()
                        ? SmartKeyboardState.PENDING_ENABLED
                        : SmartKeyboardState.PENDING_DISABLED);
                module.setEnabled(true);
            }
        }
    }

    @Listen
    private void onMouse(MouseButtonEvent event) {
        switch (event.action()) {
            case GLFW.GLFW_PRESS -> {
                if (mc.screen != null) {
                    return;
                }
                for (Module module : modules) {
                    InputBind bind = module.bind();
                    if (!bind.matchesMouse(event.button()) || !bind.matchesModifiers(event.modifiers())) {
                        continue;
                    }
                    if (!module.isToggleable()) {
                        module.trigger();
                        continue;
                    }
                    switch (bind.action()) {
                        case TOGGLE -> module.toggle();
                        case HOLD -> module.setEnabled(true);
                        case SMART -> {
                            smartMouseStates.put(module,
                                    new SmartMouseState(module.isEnabled(), System.nanoTime()));
                            module.setEnabled(true);
                        }
                    }
                }
            }
            case GLFW.GLFW_RELEASE -> {
                for (Module module : modules) {
                    InputBind bind = module.bind();
                    if (!module.isToggleable() || !bind.matchesMouse(event.button())) {
                        continue;
                    }
                    switch (bind.action()) {
                        case HOLD -> module.setEnabled(false);
                        case SMART -> {
                            SmartMouseState state = smartMouseStates.remove(module);
                            if (state == null) {
                                continue;
                            }
                            boolean held = System.nanoTime() - state.pressTimeNanos()
                                    >= SMART_MOUSE_HOLD_THRESHOLD_NANOS;
                            module.setEnabled(held ? false : !state.previouslyEnabled());
                        }
                        case TOGGLE -> {
                        }
                    }
                }
            }
            default -> {
            }
        }
    }
}
