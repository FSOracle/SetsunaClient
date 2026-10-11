package com.setsuna.module;

import com.setsuna.Setsuna;
import com.setsuna.event.EventBus;
import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyInputEvent;
import com.setsuna.event.events.MouseButtonEvent;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.module.modules.ColorPresets;
import com.setsuna.module.modules.FontModule;
import com.setsuna.module.modules.HudPresets;
import com.setsuna.module.modules.combat.AimAssist;
import com.setsuna.module.modules.combat.AntiBot;
import com.setsuna.module.modules.combat.AutoClicker;
import com.setsuna.module.modules.combat.AutoHead;
import com.setsuna.module.modules.combat.Backtrack;
import com.setsuna.module.modules.combat.Burrow;
import com.setsuna.module.modules.combat.Criticals;
import com.setsuna.module.modules.combat.FakeLag;
import com.setsuna.module.modules.combat.Hitbox;
import com.setsuna.module.modules.combat.KillAura;
import com.setsuna.module.modules.combat.Reach;
import com.setsuna.module.modules.combat.KillAuraPlus;
import com.setsuna.module.modules.combat.SilentAura;
import com.setsuna.module.modules.combat.Surround;
import com.setsuna.module.modules.combat.TriggerBot;
import com.setsuna.module.modules.combat.WTap;
import com.setsuna.module.modules.misc.Disabler;
import com.setsuna.module.modules.misc.MiddleClickFriend;
import com.setsuna.module.modules.movement.FlatElytraFly;
import com.setsuna.module.modules.movement.InvMove;
import com.setsuna.module.modules.movement.KeepSprint;
import com.setsuna.module.modules.movement.MovementFix;
import com.setsuna.module.modules.movement.NoFall;
import com.setsuna.module.modules.movement.NoJumpDelay;
import com.setsuna.module.modules.movement.NoSlow;
import com.setsuna.module.modules.movement.Scaffold;
import com.setsuna.module.modules.movement.Speed;
import com.setsuna.module.modules.movement.Sprint;
import com.setsuna.module.modules.movement.Velocity;
import com.setsuna.module.modules.player.AntiResourcePack;
import com.setsuna.module.modules.player.AntiWeb;
import com.setsuna.module.modules.player.AutoMLG;
import com.setsuna.module.modules.player.AutoTool;
import com.setsuna.module.modules.player.BedAura;
import com.setsuna.module.modules.player.ChestStealer;
import com.setsuna.module.modules.player.FakePlayer;
import com.setsuna.module.modules.player.FastBreak;
import com.setsuna.module.modules.player.FastCraftModule;
import com.setsuna.module.modules.player.FastPlace;
import com.setsuna.module.modules.player.GhostHand;
import com.setsuna.module.modules.player.InvManager;
import com.setsuna.module.modules.player.NetEaseMusicModule;
import com.setsuna.module.modules.player.PacketEat;
import com.setsuna.module.modules.render.Basketball;
import com.setsuna.module.modules.render.Billiards;
import com.setsuna.module.modules.render.BlockHighlight;
import com.setsuna.module.modules.render.CameraClip;
import com.setsuna.module.modules.render.Chams;
import com.setsuna.module.modules.render.ChestESP;
import com.setsuna.module.modules.render.Compass;
import com.setsuna.module.modules.render.DeltaForceStyle;
import com.setsuna.module.modules.render.ArmorTrimOverride;
import com.setsuna.module.modules.render.ESP;
import com.setsuna.module.modules.render.EquipmentDisguise;
import com.setsuna.module.modules.render.LocalSkinCape;
import com.setsuna.module.modules.render.Football;
import com.setsuna.module.modules.render.FullBright;
import com.setsuna.module.modules.render.HitEffect;
import com.setsuna.module.modules.render.HudEditorModule;
import com.setsuna.module.modules.render.ItemTag;
import com.setsuna.module.modules.render.KillEffect;
import com.setsuna.module.modules.render.LegendWatch;
import com.setsuna.module.modules.render.MCS;
import com.setsuna.module.modules.render.NameTags;
import com.setsuna.module.modules.render.NoRender;
import com.setsuna.module.modules.render.OreTracers;
import com.setsuna.module.modules.render.ProjectileTrajectory;
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
import com.setsuna.ui.hud.Notifications;
import com.setsuna.ui.hud.PigHUD;
import com.setsuna.ui.hud.PotionHUD;
import com.setsuna.ui.hud.RadarHUD;
import com.setsuna.ui.hud.ScaffoldBlockHUD;
import com.setsuna.ui.hud.ScoreboardHUD;
import com.setsuna.ui.hud.SessionInfoHUD;
import com.setsuna.ui.hud.TargetHud;
import com.setsuna.ui.hud.WatermarkHUD;
import com.setsuna.util.client.InputBind;
import com.setsuna.util.network.BlinkManager;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class ModuleManager {
   public static final ModuleManager INSTANCE = new ModuleManager();
   private final Minecraft mc = Setsuna.mc();
   private final List<Module> modules = new ArrayList();
   private final Map<Module, SmartKeyboardState> smartKeyboardStates = new IdentityHashMap();
   private final Map<Module, SmartMouseState> smartMouseStates = new IdentityHashMap();
   private boolean initialized;
   private static final long SMART_MOUSE_HOLD_THRESHOLD_NANOS = 200000000L;

   private ModuleManager() {
   }

   public void init() {
      if (!this.initialized) {
         this.modules.clear();
         this.smartKeyboardStates.clear();
         this.smartMouseStates.clear();
         this.register(ClickGui.INSTANCE);
         this.register(HudPresets.INSTANCE);
         this.register(ColorPresets.INSTANCE);
         this.register(FontModule.INSTANCE);
         this.register(HudEditorModule.INSTANCE);
         this.register(NetEaseMusicModule.INSTANCE);
         this.register(AntiBot.INSTANCE);
         this.register(KillAura.INSTANCE);
         this.register(KillAuraPlus.INSTANCE);
         this.register(AutoHead.INSTANCE);
         this.register(Burrow.INSTANCE);
         this.register(Surround.INSTANCE);
         this.register(Criticals.INSTANCE);
         this.register(AimAssist.INSTANCE);
         this.register(AutoClicker.INSTANCE);
         this.register(Hitbox.INSTANCE);
         this.register(Reach.INSTANCE);
         this.register(SilentAura.INSTANCE);
         this.register(TriggerBot.INSTANCE);
         this.register(WTap.INSTANCE);
         this.register(FakeLag.INSTANCE);
         this.register(Backtrack.INSTANCE);
         this.register(MiddleClickFriend.INSTANCE);
         this.register(Disabler.INSTANCE);
         this.register(Sprint.INSTANCE);
         this.register(InvMove.INSTANCE);
         this.register(NoFall.INSTANCE);
         this.register(NoSlow.INSTANCE);
         this.register(KeepSprint.INSTANCE);
         this.register(FullBright.INSTANCE);
         this.register(Speed.INSTANCE);
         this.register(FlatElytraFly.INSTANCE);
         this.register(MovementFix.INSTANCE);
         this.register(BlockHighlight.INSTANCE);
         this.register(CameraClip.INSTANCE);
         this.register(Compass.INSTANCE);
         this.register(DeltaForceStyle.INSTANCE);
         this.register(Football.INSTANCE);
         this.register(Basketball.INSTANCE);
         this.register(Billiards.INSTANCE);
         this.register(MCS.INSTANCE);
         this.register(Tracers.INSTANCE);
         this.register(ItemTag.INSTANCE);
         this.register(KillEffect.INSTANCE);
         this.register(HitEffect.INSTANCE);
         this.register(ESP.INSTANCE);
         this.register(ArmorTrimOverride.INSTANCE);
         this.register(EquipmentDisguise.INSTANCE);
         this.register(LocalSkinCape.INSTANCE);
         this.register(ChestESP.INSTANCE);
         this.register(OreTracers.INSTANCE);
         this.register(Xray.INSTANCE);
         this.register(Chams.INSTANCE);
         this.register(UHCDetector.INSTANCE);
         this.register(SpawnerFinder.INSTANCE);
         this.register(NameTags.INSTANCE);
         this.register(ProjectileTrajectory.INSTANCE);
         this.register(NoRender.INSTANCE);
         this.register(LegendWatch.INSTANCE);
         this.register(Scaffold.INSTANCE);
         this.register(Velocity.INSTANCE);
         this.register(AntiResourcePack.INSTANCE);
         this.register(AntiWeb.INSTANCE);
         this.register(AutoMLG.INSTANCE);
         this.register(AutoTool.INSTANCE);
         this.register(BedAura.INSTANCE);
         this.register(GhostHand.INSTANCE);
         this.register(InvManager.INSTANCE);
         this.register(NoJumpDelay.INSTANCE);
         this.register(FastCraftModule.INSTANCE);
         this.register(ChestStealer.INSTANCE);
         this.register(FastBreak.INSTANCE);
         this.register(FastPlace.INSTANCE);
         this.register(PacketEat.INSTANCE);
         this.register(FakePlayer.INSTANCE);
         this.register(WatermarkHUD.INSTANCE);
         this.register(ModuleListHUD.INSTANCE);
         this.register(FPSHUD.INSTANCE);
         this.register(BPSHUD.INSTANCE);
         this.register(CoordinatesHUD.INSTANCE);
         this.register(InventoryHUD.INSTANCE);
         this.register(KeybindOverlayHUD.INSTANCE);
         this.register(PotionHUD.INSTANCE);
         this.register(PigHUD.INSTANCE);
         this.register(RadarHUD.INSTANCE);
         this.register(TeamViewer.INSTANCE);
         this.register(ScaffoldBlockHUD.INSTANCE);
         this.register(ScoreboardHUD.INSTANCE);
         this.register(MusicLyricsHUD.INSTANCE);
         this.register(TargetHud.INSTANCE);
         this.register(SessionInfoHUD.INSTANCE);
         this.register(Notifications.INSTANCE);
         this.register(HUD.INSTANCE);
         EventBus.INSTANCE.subscribe(HudFusionManager.INSTANCE);
         EventBus.INSTANCE.subscribe(BlinkManager.INSTANCE);
         EventBus.INSTANCE.subscribe(this);
         this.initialized = true;
      }
   }

   public boolean isInitialized() {
      return this.initialized;
   }

   public void disableAll() {
      this.smartKeyboardStates.clear();
      this.smartMouseStates.clear();

      for(Module module : this.modules) {
         module.setEnabled(false);
      }

   }

   private void register(Module module) {
      if (this.modules.stream().anyMatch((existing) -> existing.id().equals(module.id()))) {
         throw new IllegalStateException("Duplicate module id: " + module.id());
      } else {
         this.modules.add(module);
         module.bindI18n();
         module.captureConfigDefaults();
      }
   }

   public synchronized void registerDynamic(Module module) {
      if (!this.initialized) {
         throw new IllegalStateException("Module manager is not initialized");
      } else {
         this.register(module);
      }
   }

   public synchronized void unregisterDynamic(Module module) {
      if (this.modules.contains(module)) {
         module.setEnabled(false);
         this.smartKeyboardStates.remove(module);
         this.smartMouseStates.remove(module);
         this.modules.remove(module);
      }
   }

   public List<Module> modules() {
      return this.modules;
   }

   public List<Module> modulesIn(Category category) {
      List<Module> result = new ArrayList();

      for(Module module : this.modules) {
         if (module.category() == category) {
            result.add(module);
         }
      }

      return result;
   }

   @Listen
   private void onKey(KeyInputEvent event) {
      if (event.key() != -1) {
         switch (event.action()) {
            case 0:
               for(Module module : this.modules) {
                  InputBind bind = module.bind();
                  if (module.isToggleable() && bind.isAffectedByKeyRelease(event.key())) {
                     switch (bind.action()) {
                        case HOLD:
                           module.setEnabled(false);
                           break;
                        case SMART:
                           SmartKeyboardState state = (SmartKeyboardState)this.smartKeyboardStates.remove(module);
                           if (state != null) {
                              module.setEnabled(state == ModuleManager.SmartKeyboardState.PENDING_DISABLED);
                           }
                        case TOGGLE:
                     }
                  }
               }
               break;
            case 1:
               if (this.mc.screen != null || this.mc.options.keyDebugModifier.isDown()) {
                  return;
               }

               for(Module module : this.modules) {
                  InputBind bind = module.bind();
                  if (bind.matchesKey(event.key()) && bind.matchesModifiers(event.modifiers())) {
                     this.handleKeyboardPress(module, bind);
                  }
               }
               break;
            case 2:
               for(Module module : this.modules) {
                  InputBind bind = module.bind();
                  if (bind.action() == InputBind.BindAction.SMART && bind.matchesKey(event.key()) && this.smartKeyboardStates.containsKey(module)) {
                     this.smartKeyboardStates.put(module, ModuleManager.SmartKeyboardState.HOLDING);
                  }
               }
         }

      }
   }

   private void handleKeyboardPress(Module module, InputBind bind) {
      if (!module.isToggleable()) {
         module.trigger();
      } else {
         switch (bind.action()) {
            case HOLD:
               module.setEnabled(true);
               break;
            case SMART:
               this.smartKeyboardStates.put(module, module.isEnabled() ? ModuleManager.SmartKeyboardState.PENDING_ENABLED : ModuleManager.SmartKeyboardState.PENDING_DISABLED);
               module.setEnabled(true);
               break;
            case TOGGLE:
               module.toggle();
         }

      }
   }

   @Listen
   private void onMouse(MouseButtonEvent event) {
      switch (event.action()) {
         case 0:
            for(Module module : this.modules) {
               InputBind bind = module.bind();
               if (module.isToggleable() && bind.matchesMouse(event.button())) {
                  switch (bind.action()) {
                     case HOLD:
                        module.setEnabled(false);
                        break;
                     case SMART:
                        SmartMouseState state = (SmartMouseState)this.smartMouseStates.remove(module);
                        if (state != null) {
                           boolean held = System.nanoTime() - state.pressTimeNanos() >= 200000000L;
                           module.setEnabled(held ? false : !state.previouslyEnabled());
                        }
                     case TOGGLE:
                  }
               }
            }
            break;
         case 1:
            if (this.mc.screen != null) {
               return;
            }

            for(Module module : this.modules) {
               InputBind bind = module.bind();
               if (bind.matchesMouse(event.button()) && bind.matchesModifiers(event.modifiers())) {
                  if (!module.isToggleable()) {
                     module.trigger();
                  } else {
                     switch (bind.action()) {
                        case HOLD:
                           module.setEnabled(true);
                           break;
                        case SMART:
                           this.smartMouseStates.put(module, new SmartMouseState(module.isEnabled(), System.nanoTime()));
                           module.setEnabled(true);
                           break;
                        case TOGGLE:
                           module.toggle();
                     }
                  }
               }
            }
      }

   }

   private static enum SmartKeyboardState {
      PENDING_ENABLED,
      PENDING_DISABLED,
      HOLDING;
   }

   private static record SmartMouseState(boolean previouslyEnabled, long pressTimeNanos) {
   }
}
