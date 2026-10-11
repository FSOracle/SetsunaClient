package com.setsuna.ui.hud;

import com.setsuna.i18n.LocalizedText;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.render.TeamViewer;
import com.setsuna.setting.Setting;
import com.setsuna.ui.UiTheme;
import com.setsuna.ui.screen.MainMenuStyle;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HUD extends Module {
   public static final HUD INSTANCE = new HUD();
   public final EnumSetting<LocalizedText.LanguageOverride> language;
   public final BooleanSetting globalBlur;
   public final IntSetting globalBlurStrength;
   public final EnumSetting<MainMenuStyle> mainMenuStyle;
   public final BooleanSetting watermark;
   public final BooleanSetting arrayList;
   public final BooleanSetting fps;
   public final BooleanSetting bps;
   public final BooleanSetting coordinates;
   public final BooleanSetting inventory;
   public final BooleanSetting keybindOverlay;
   public final BooleanSetting potion;
   public final BooleanSetting pig;
   public final BooleanSetting radar;
   public final BooleanSetting teamViewer;
   public final BooleanSetting scaffoldBlocks;
   public final BooleanSetting scoreboard;
   public final BooleanSetting musicLyrics;
   public final BooleanSetting notifications;
   private final List<Setting<?>> controllerSettings;
   private boolean synchronizing;

   private HUD() {
      super("HUD", Category.RENDER);
      this.language = (EnumSetting)this.add((EnumSetting)(new EnumSetting("Language", LocalizedText.LanguageOverride.CLIENT)).onChange(value -> LocalizedText.setLanguageOverride((LocalizedText.LanguageOverride)value)));
      this.globalBlur = add(new BooleanSetting("Global Blur", true));
      this.globalBlurStrength = add(new IntSetting("Global Blur Strength", 8, 1, 16, 1)
            .visibleWhen(globalBlur::get));
      this.mainMenuStyle = add(new EnumSetting<>("Main Menu Style", MainMenuStyle.CLASSIC));
      this.watermark = this.control("Watermark", true, WatermarkHUD.INSTANCE);
      this.arrayList = this.control("Array List", true, ModuleListHUD.INSTANCE);
      this.fps = this.control("FPS", false, FPSHUD.INSTANCE);
      this.bps = this.control("BPS", false, BPSHUD.INSTANCE);
      this.coordinates = this.control("Coordinates", false, CoordinatesHUD.INSTANCE);
      this.inventory = this.control("Inventory", false, InventoryHUD.INSTANCE);
      this.keybindOverlay = this.control("Keybind Overlay", false, KeybindOverlayHUD.INSTANCE);
      this.potion = this.control("Potion", false, PotionHUD.INSTANCE);
      this.pig = this.control("Pig", false, PigHUD.INSTANCE);
      this.radar = this.control("Radar", false, RadarHUD.INSTANCE);
      this.teamViewer = this.control("Team Viewer", false, TeamViewer.INSTANCE);
      this.scaffoldBlocks = this.control("Scaffold Blocks", false, ScaffoldBlockHUD.INSTANCE);
      this.scoreboard = this.control("Scoreboard", false, ScoreboardHUD.INSTANCE);
      this.musicLyrics = (BooleanSetting)this.control("Music Lyrics", false, MusicLyricsHUD.INSTANCE).displayAs("Music HUD");
      this.notifications = this.control("Notifications", true, Notifications.INSTANCE);
      Set<Setting<?>> componentToggles = new HashSet<>();
      for (HUDModuel component : this.components()) {
         componentToggles.add(this.componentSetting(component));
      }
      this.controllerSettings = this.settings().stream()
            .filter(setting -> !componentToggles.contains(setting)).toList();
      this.setEnabled(true);
   }

   private BooleanSetting control(String name, boolean defaultValue, HUDModuel module) {
      return (BooleanSetting)this.add((BooleanSetting)(new BooleanSetting(name, defaultValue)).onChange((value) -> {
         if (!this.synchronizing && this.isEnabled()) {
            module.setEnabled(value);
         }

      }));
   }

   protected void onEnable() {
      this.applyControls();
   }

   protected void onDisable() {
      disableHudModules();
   }

   public void reconcileAfterConfigLoad(boolean hasControllerConfig) {
      if (hasControllerConfig) {
         if (this.isEnabled()) {
            this.applyControls();
         } else {
            disableHudModules();
         }

      } else {
         this.captureChildStates();
         if (this.isEnabled()) {
            this.applyControls();
         } else {
            this.setEnabled(true);
         }

      }
   }

   public void captureChildStates() {
      this.synchronizing = true;

      try {
         this.watermark.set(WatermarkHUD.INSTANCE.isEnabled());
         this.arrayList.set(ModuleListHUD.INSTANCE.isEnabled());
         this.fps.set(FPSHUD.INSTANCE.isEnabled());
         this.bps.set(BPSHUD.INSTANCE.isEnabled());
         this.coordinates.set(CoordinatesHUD.INSTANCE.isEnabled());
         this.inventory.set(InventoryHUD.INSTANCE.isEnabled());
         this.keybindOverlay.set(KeybindOverlayHUD.INSTANCE.isEnabled());
         this.potion.set(PotionHUD.INSTANCE.isEnabled());
         this.pig.set(PigHUD.INSTANCE.isEnabled());
         this.radar.set(RadarHUD.INSTANCE.isEnabled());
         this.teamViewer.set(TeamViewer.INSTANCE.isEnabled());
         this.scaffoldBlocks.set(ScaffoldBlockHUD.INSTANCE.isEnabled());
         this.scoreboard.set(ScoreboardHUD.INSTANCE.isEnabled());
         this.musicLyrics.set(MusicLyricsHUD.INSTANCE.isEnabled());
         this.notifications.set(Notifications.INSTANCE.isEnabled());
      } finally {
         this.synchronizing = false;
      }

   }

   public BooleanSetting componentSetting(Module module) {
      if (module == WatermarkHUD.INSTANCE) {
         return this.watermark;
      } else if (module == ModuleListHUD.INSTANCE) {
         return this.arrayList;
      } else if (module == FPSHUD.INSTANCE) {
         return this.fps;
      } else if (module == BPSHUD.INSTANCE) {
         return this.bps;
      } else if (module == CoordinatesHUD.INSTANCE) {
         return this.coordinates;
      } else if (module == InventoryHUD.INSTANCE) {
         return this.inventory;
      } else if (module == KeybindOverlayHUD.INSTANCE) {
         return this.keybindOverlay;
      } else if (module == PotionHUD.INSTANCE) {
         return this.potion;
      } else if (module == PigHUD.INSTANCE) {
         return this.pig;
      } else if (module == RadarHUD.INSTANCE) {
         return this.radar;
      } else if (module == TeamViewer.INSTANCE) {
         return this.teamViewer;
      } else if (module == ScaffoldBlockHUD.INSTANCE) {
         return this.scaffoldBlocks;
      } else if (module == ScoreboardHUD.INSTANCE) {
         return this.scoreboard;
      } else if (module == MusicLyricsHUD.INSTANCE) {
         return this.musicLyrics;
      } else {
         return module == Notifications.INSTANCE ? this.notifications : null;
      }
   }

   public List<HUDModuel> components() {
      return List.of(WatermarkHUD.INSTANCE, ModuleListHUD.INSTANCE, FPSHUD.INSTANCE, BPSHUD.INSTANCE, CoordinatesHUD.INSTANCE, InventoryHUD.INSTANCE, KeybindOverlayHUD.INSTANCE, PotionHUD.INSTANCE, PigHUD.INSTANCE, RadarHUD.INSTANCE, TeamViewer.INSTANCE, ScaffoldBlockHUD.INSTANCE, ScoreboardHUD.INSTANCE, MusicLyricsHUD.INSTANCE, Notifications.INSTANCE);
   }

   /** Global controls shown above the component rows in nested HUD editors. */
   public List<Setting<?>> controllerSettings() {
      return this.controllerSettings;
   }

   public void setComponentEnabled(HUDModuel module, boolean enabled) {
      BooleanSetting setting = this.componentSetting(module);
      if (setting != null) {
         setting.set(enabled);
         if (enabled && !this.isEnabled()) {
            this.setEnabled(true);
         }

      }
   }

   private void applyControls() {
      WatermarkHUD.INSTANCE.setEnabled((Boolean)this.watermark.get());
      ModuleListHUD.INSTANCE.setEnabled((Boolean)this.arrayList.get());
      FPSHUD.INSTANCE.setEnabled((Boolean)this.fps.get());
      BPSHUD.INSTANCE.setEnabled((Boolean)this.bps.get());
      CoordinatesHUD.INSTANCE.setEnabled((Boolean)this.coordinates.get());
      InventoryHUD.INSTANCE.setEnabled((Boolean)this.inventory.get());
      KeybindOverlayHUD.INSTANCE.setEnabled((Boolean)this.keybindOverlay.get());
      PotionHUD.INSTANCE.setEnabled((Boolean)this.potion.get());
      PigHUD.INSTANCE.setEnabled((Boolean)this.pig.get());
      RadarHUD.INSTANCE.setEnabled((Boolean)this.radar.get());
      TeamViewer.INSTANCE.setEnabled((Boolean)this.teamViewer.get());
      ScaffoldBlockHUD.INSTANCE.setEnabled((Boolean)this.scaffoldBlocks.get());
      ScoreboardHUD.INSTANCE.setEnabled((Boolean)this.scoreboard.get());
      MusicLyricsHUD.INSTANCE.setEnabled((Boolean)this.musicLyrics.get());
      Notifications.INSTANCE.setEnabled((Boolean)this.notifications.get());
   }

   private static void disableHudModules() {
      WatermarkHUD.INSTANCE.setEnabled(false);
      ModuleListHUD.INSTANCE.setEnabled(false);
      FPSHUD.INSTANCE.setEnabled(false);
      BPSHUD.INSTANCE.setEnabled(false);
      CoordinatesHUD.INSTANCE.setEnabled(false);
      InventoryHUD.INSTANCE.setEnabled(false);
      KeybindOverlayHUD.INSTANCE.setEnabled(false);
      PotionHUD.INSTANCE.setEnabled(false);
      PigHUD.INSTANCE.setEnabled(false);
      RadarHUD.INSTANCE.setEnabled(false);
      TeamViewer.INSTANCE.setEnabled(false);
      ScaffoldBlockHUD.INSTANCE.setEnabled(false);
      ScoreboardHUD.INSTANCE.setEnabled(false);
      MusicLyricsHUD.INSTANCE.setEnabled(false);
      Notifications.INSTANCE.setEnabled(false);
   }

   public int getColor() {
      return this.getColor(0);
   }

   public int getColor(int counter) {
      return this.getColor(counter, 255);
   }

   public int getColor(int counter, int alpha) {
      return UiTheme.withAlpha(UiTheme.accent(), alpha);
   }
}
