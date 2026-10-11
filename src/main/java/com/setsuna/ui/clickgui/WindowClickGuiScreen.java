package com.setsuna.ui.clickgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.setsuna.Setsuna;
import com.setsuna.config.ConfigManager;
import com.setsuna.i18n.TranslationKey;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.notification.NotificationManager;
import com.setsuna.notification.NotificationType;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.FontSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.ui.CategoryGlyphs;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.ui.UiTheme;
import com.setsuna.ui.hud.HUD;
import com.setsuna.ui.hud.HUDModuel;
import com.setsuna.util.KeyBindText;
import com.setsuna.util.client.KeybindUtils;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class WindowClickGuiScreen extends Screen implements SkijaScreen {
   private static final float PANEL_WIDTH = 124.0F;
   private static final float MARGIN = 12.0F;
   private static final float HEADER_HEIGHT = 24.0F;
   private static final float MODULE_HEIGHT = 20.0F;
   private static final float SETTING_HEIGHT = 20.0F;
   private static final float NUMBER_HEIGHT = 34.0F;
   private static final float COLOR_CHANNEL_HEIGHT = 10.0F;
   private static final float MAX_BODY_HEIGHT = 310.0F;
   private static final float SCROLL_STEP = 22.0F;
   private static final float BODY_INSET = 2.0F;
   private static final float SECTION_INSET = 4.0F;
   private static final float SLIDER_FADE_WIDTH = 5.0F;
   private static final float EXPAND_SPEED = 14.0F;
   private static final float GRID_BUTTON_SIZE = 28.0F;
   private static final float MIN_SIX_COLUMN_WIDTH = 808.0F;
   private static final float FONT = 9.0F;
   private static final float FONT_SMALL = 7.5F;
   private static final float PANEL_RADIUS = 7.0F;
   private static final float CONTROL_RADIUS = 5.0F;
   private static final int HAIRLINE = 620756991;
   private static final int TEXT = argb(255, 255, 255, 255);
   private static final int TEXT_DIM = argb(255, 170, 170, 170);
   private static final int TEXT_OFF = argb(255, 85, 85, 85);
   private static final int DELETE_BACKGROUND = argb(230, 145, 38, 38);
   private static final Paint GRADIENT_PAINT = (new Paint()).setAntiAlias(true);
   private static final Paint SCREEN_LAYER_PAINT = (new Paint()).setAntiAlias(true);
   private static final Paint SEARCH_STROKE_PAINT;
   private static final Paint SEARCH_LAYER_PAINT;
   private static final Gson STATE_GSON;
   private static final String STATE_FILE_NAME = "drop-clickgui-state.json";
   private static final ConfigProvider DEFAULT_CONFIG_PROVIDER;
   private static volatile ConfigProvider configProvider;
   private static final Map<String, PanelMemory> PANEL_MEMORY;
   private static final List<String> PANEL_ORDER_MEMORY;
   private static boolean panelMemorySaved;
   private static boolean panelMemoryDiskLoaded;
   private static boolean gridEnabledMemory;
   private String searchQuery;
   private boolean searchOpen;
   private float searchOpenProgress;
   private float searchProgress;
   private List<Module> searchResults;
   private ModulePanel searchSettingsPanel;
   private final List<Panel> panels;
   private Panel draggingPanel;
   private float dragOffsetX;
   private float dragOffsetY;
   private Setting<?> draggingNumber;
   private float draggingTrackX;
   private float draggingTrackWidth;
   private ColorSetting draggingColor;
   private int draggingColorChannel;
   private final Map<BooleanSetting, Float> toggleAnimations;
   private Module capturingModule;
   private KeybindSetting capturingKeybind;
   private TextTarget textTarget;
   private StringSetting editingString;
   private ColorSetting editingColor;
   private Color colorBeforeEdit;
   private String editText;
   private int editCursor;
   private boolean selectAll;
   private float activeScale;
   private float animationDelta;
   private float renderMouseX;
   private float renderMouseY;
   private float visibilityProgress;
   private boolean gridEnabled;
   private boolean gridStateInitialized;
   private boolean gridTransitioning;
   private float gridExpansion;
   private float gridOpacity;
   private long lastFrame;
   private final Screen parent;

   public static void installConfigProvider(ConfigProvider provider) {
      configProvider = provider == null ? DEFAULT_CONFIG_PROVIDER : provider;
   }

   public WindowClickGuiScreen() {
      this((Screen)null);
   }

   public WindowClickGuiScreen(Screen parent) {
      super(Component.literal(tr("title", "Setsuna ClickGUI")));
      this.searchQuery = "";
      this.searchResults = List.of();
      this.panels = new ArrayList();
      this.draggingColorChannel = -1;
      this.toggleAnimations = new HashMap();
      this.textTarget = WindowClickGuiScreen.TextTarget.NONE;
      this.editText = "";
      this.activeScale = 1.0F;
      this.lastFrame = System.nanoTime();
      this.parent = parent;
   }

   protected void init() {
      this.activeScale = this.configuredScale();
      this.lastFrame = System.nanoTime();
      this.visibilityProgress = 0.0F;
      if (this.panels.isEmpty()) {
         loadPanelMemoryFromDisk();
         Category[] categories = new Category[]{Category.COMBAT, Category.MISC, Category.RENDER, Category.MOVEMENT, Category.PLAYER, Category.CLIENT};
         float y = 12.0F;

         for(int index = 0; index < categories.length; ++index) {
            Category category = categories[index];
            this.panels.add(new ModulePanel(category, ModuleManager.INSTANCE.modulesIn(category), 12.0F, y));
            y += 28.0F;
         }

         this.panels.add(new ConfigPanel(12.0F, y));
         this.restorePanelMemory();
      }

      if (!this.gridStateInitialized) {
         this.gridEnabled = gridEnabledMemory;
         this.gridExpansion = this.gridEnabled ? 1.0F : 0.0F;
         this.gridStateInitialized = true;
      }

      this.clampPanels();
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, UiTheme.themedBackdrop(36));
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.renderMouseX = (float)this.logical((double)mouseX);
      this.renderMouseY = (float)this.logical((double)mouseY);
      if (SkijaRenderer.hasFailed()) {
         graphics.fill(0, 0, this.width, this.height, backdrop());
         graphics.text(this.font, "Skija renderer failed - check latest.log", 8, 8, TEXT, false);
      }

   }

   public void renderSkija(Canvas canvas) {
      long now = System.nanoTime();
      this.animationDelta = Math.min(0.05F, Math.max(0.0F, (float)(now - this.lastFrame) / 1.0E9F));
      this.lastFrame = now;
      this.visibilityProgress = animateTowards(this.visibilityProgress, 1.0F, 10.0F, this.animationDelta);
      this.updateGridAnimation();
      float logicalWidth = this.logicalWidth();
      float logicalHeight = this.logicalHeight();
      this.clampPanels();
      boolean searching = this.searchOpen && !this.searchQuery.isBlank();
      this.searchOpenProgress = animateTowards(this.searchOpenProgress, this.searchOpen ? 1.0F : 0.0F, this.searchOpen ? 14.0F : 18.0F, this.animationDelta);
      this.searchProgress = animateTowards(this.searchProgress, searching ? 1.0F : 0.0F, searching ? 11.0F : 17.0F, this.animationDelta);
      canvas.save();
      canvas.scale(this.activeScale, this.activeScale);
      SkijaUi.fill(canvas, 0.0F, 0.0F, logicalWidth + 1.0F, logicalHeight + 1.0F, backdrop());
      float visibility = easeOutCubic(this.visibilityProgress);
      float centerX = logicalWidth * 0.5F;
      float centerY = logicalHeight * 0.5F;
      canvas.translate(centerX, centerY);
      float openingScale = 0.955F + visibility * 0.045F;
      canvas.scale(openingScale, openingScale);
      canvas.translate(-centerX, -centerY);
      if (visibility < 0.999F) {
         SCREEN_LAYER_PAINT.setAlpha(Math.max(0, Math.min(255, Math.round(255.0F * visibility))));
         int screenLayer = canvas.saveLayer(Rect.makeXYWH(-32.0F, -32.0F, logicalWidth + 64.0F, logicalHeight + 64.0F), SCREEN_LAYER_PAINT);
         this.renderScreenContent(canvas, logicalWidth, logicalHeight);
         canvas.restoreToCount(screenLayer);
         SCREEN_LAYER_PAINT.setAlpha(255);
      } else {
         this.renderScreenContent(canvas, logicalWidth, logicalHeight);
      }

      canvas.restore();
   }

   private void renderScreenContent(Canvas canvas, float logicalWidth, float logicalHeight) {
      if (this.searchProgress < 0.995F) {
         if (this.searchProgress > 0.002F) {
            SEARCH_LAYER_PAINT.setAlpha(Math.round(255.0F * (1.0F - this.searchProgress)));
            int normalLayer = canvas.saveLayer(Rect.makeXYWH(-16.0F, -16.0F, logicalWidth + 32.0F, logicalHeight + 32.0F), SEARCH_LAYER_PAINT);
            this.renderDropPanels(canvas, logicalWidth, logicalHeight);
            canvas.restoreToCount(normalLayer);
            SEARCH_LAYER_PAINT.setAlpha(255);
         } else {
            this.renderDropPanels(canvas, logicalWidth, logicalHeight);
         }
      }

      this.renderSearch(canvas, logicalWidth, logicalHeight);
   }

   private void renderDropPanels(Canvas canvas, float logicalWidth, float logicalHeight) {
      this.renderGrid(canvas, logicalWidth, logicalHeight);

      for(Panel panel : this.panels) {
         panel.render(canvas, logicalHeight);
      }

      this.renderGridButton(canvas, logicalWidth, logicalHeight);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (KeybindUtils.isAuxiliaryMouseButton(event.button())
            && (this.capturingKeybind != null || this.capturingModule != null)) {
         int binding = KeybindUtils.encodeMouseButton(event.button());
         if (this.capturingKeybind != null) this.capturingKeybind.set(binding);
         else this.capturingModule.setKeyBind(binding);
         this.capturingKeybind = null;
         this.capturingModule = null;
         return true;
      }
      float mouseX = (float)this.logical(event.x());
      float mouseY = (float)this.logical(event.y());
      int button = event.button();
      if (this.searchToggleBounds(this.logicalWidth()).contains(mouseX, mouseY)) {
         if (!this.searchOpen) {
            this.searchOpen = true;
            this.focusSearch();
         } else {
            this.searchOpen = false;
            this.finishTextEditing();
         }

         return true;
      } else if (this.searchOpen && this.searchInputBounds(this.logicalWidth()).contains(mouseX, mouseY)) {
         this.focusSearch();
         return true;
      } else if (this.searchOpen && !this.searchQuery.isBlank() && this.handleSearchResultClick(mouseX, mouseY, button, this.logicalWidth(), this.logicalHeight())) {
         return true;
      } else if (this.searchOpen && !this.searchQuery.isBlank()) {
         return true;

      } else if (button == 0 && this.gridButtonBounds(this.logicalWidth(), this.logicalHeight()).contains(mouseX, mouseY)) {
         this.finishTextEditing();
         this.gridEnabled = !this.gridEnabled;
         this.gridTransitioning = true;
         return true;
      } else {
         this.finishTextEditing();
         this.capturingModule = null;
         this.capturingKeybind = null;

         for(int i = this.panels.size() - 1; i >= 0; --i) {
            Panel panel = (Panel)this.panels.get(i);
            if (panel.mouseClicked(mouseX, mouseY, button, this.logicalHeight())) {
               this.panels.remove(i);
               this.panels.add(panel);
               return true;
            }
         }

         return super.mouseClicked(event, doubleClick);
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      float mouseX = (float)this.logical(event.x());
      float mouseY = (float)this.logical(event.y());
      if (this.draggingPanel != null) {
         float targetX = mouseX - this.dragOffsetX;
         float targetY = mouseY - this.dragOffsetY;
         this.draggingPanel.x = this.gridEnabled ? snapToGrid(targetX) : targetX;
         this.draggingPanel.y = this.gridEnabled ? snapToGrid(targetY) : targetY;
         this.draggingPanel.clampPosition(this.logicalWidth(), this.logicalHeight());
         return true;
      } else if (this.draggingNumber != null) {
         this.updateNumber(mouseX);
         return true;
      } else if (this.draggingColor != null) {
         this.updateColor(mouseX);
         return true;
      } else {
         return super.mouseDragged(event, dragX, dragY);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.draggingPanel = null;
      this.draggingNumber = null;
      this.draggingColor = null;
      this.draggingColorChannel = -1;
      this.activeScale = this.configuredScale();
      this.clampPanels();
      return super.mouseReleased(event);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      float logicalX = (float)this.logical(mouseX);
      float logicalY = (float)this.logical(mouseY);
      for(int i = this.panels.size() - 1; i >= 0; --i) {
         Panel panel = (Panel)this.panels.get(i);
         if (panel.scrollAt(logicalX, logicalY, (float)verticalAmount, this.logicalHeight())) {
            return true;
         }
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public boolean keyPressed(KeyEvent event) {
      if (this.capturingKeybind != null) {
         if (event.key() == 256) {
            this.capturingKeybind = null;
         } else if (event.key() != 261 && event.key() != 259) {
            if (event.key() != -1) {
               this.capturingKeybind.set(event.key());
               this.capturingKeybind = null;
            }
         } else {
            this.capturingKeybind.set(-1);
            this.capturingKeybind = null;
         }

         return true;
      } else if (this.capturingModule == null) {
         return this.textTarget != WindowClickGuiScreen.TextTarget.NONE && this.handleTextKey(event) ? true : super.keyPressed(event);
      } else {
         if (event.key() == 256) {
            this.capturingModule = null;
         } else if (event.key() != 261 && event.key() != 259) {
            if (event.key() != -1) {
               this.capturingModule.setKeyBind(event.key());
               this.capturingModule = null;
            }
         } else {
            this.capturingModule.setKeyBind(-1);
            this.capturingModule = null;
         }

         return true;
      }
   }

   public boolean charTyped(CharacterEvent event) {
      if (this.textTarget != WindowClickGuiScreen.TextTarget.NONE && event.isAllowedChatCharacter()) {
         this.insertText(event.codepointAsString());
         return true;
      } else {
         return super.charTyped(event);
      }
   }

   public void onClose() {
      this.finishTextEditing();
      this.savePanelMemory();
      configProvider.persist();
      if (this.parent != null) {
         this.minecraft.setScreen(this.parent);
      } else {
         super.onClose();
      }

   }

   public void removed() {
      this.savePanelMemory();
      super.removed();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private void openConfigFolder() {
      this.finishTextEditing();

      try {
         Path directory = ConfigManager.INSTANCE.configDirectory();
         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.OPEN)) {
            Desktop.getDesktop().open(directory.toFile());
         } else {
            Util.getPlatform().openUri(directory.toUri().toString());
         }

         NotificationManager.INSTANCE.post(NotificationType.INFO, "Configs", tr("status_folder_opened", "Opened config folder"));
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to open the config folder", error);
         NotificationManager.INSTANCE.post(NotificationType.ERROR, "Configs", tr("status_folder_failed", "Open folder failed"));
      }

   }

   private void beginNumberDrag(Setting<?> setting, float trackX, float trackWidth, float mouseX) {
      this.draggingNumber = setting;
      this.draggingTrackX = trackX;
      this.draggingTrackWidth = trackWidth;
      this.updateNumber(mouseX);
   }

   private void updateNumber(float mouseX) {
      float fraction = clamp((mouseX - this.draggingTrackX) / this.draggingTrackWidth, 0.0F, 1.0F);
      Setting draggedSetting = this.draggingNumber;
      if (draggedSetting instanceof IntSetting setting) {
         double stepCount = (double)Math.round((float)(setting.max() - setting.min()) * fraction / (float)setting.step());
         setting.set((int)Math.round((double)setting.min() + stepCount * (double)setting.step()));
      } else if (draggedSetting instanceof EnumSetting<?> setting && setting.isSlider()) {
         setting.setSliderFraction(fraction);
      } else {
         draggedSetting = this.draggingNumber;
         if (draggedSetting instanceof DoubleSetting setting) {
            double raw = setting.min() + (setting.max() - setting.min()) * (double)fraction;
            double stepCount = (double)Math.round((raw - setting.min()) / setting.step());
            setting.set(setting.min() + stepCount * setting.step());
         }
      }

   }

   private void updateColor(float mouseX) {
      if (this.draggingColor != null && this.draggingColorChannel >= 0) {
         int value = Math.round(clamp((mouseX - this.draggingTrackX) / this.draggingTrackWidth, 0.0F, 1.0F) * 255.0F);
         Color current = this.draggingColor.get();
         int red = this.draggingColorChannel == 0 ? value : current.getRed();
         int green = this.draggingColorChannel == 1 ? value : current.getGreen();
         int blue = this.draggingColorChannel == 2 ? value : current.getBlue();
         int alpha = this.draggingColorChannel == 3 ? value : current.getAlpha();
         this.draggingColor.set(new Color(red, green, blue, alpha));
      }
   }

   private void focusString(StringSetting setting) {
      this.textTarget = WindowClickGuiScreen.TextTarget.STRING;
      this.editingString = setting;
      this.editingColor = null;
      this.colorBeforeEdit = null;
      this.editText = (String)setting.get();
      this.editCursor = this.editText.length();
      this.selectAll = false;
   }

   private void focusConfigName(String current) {
      this.textTarget = WindowClickGuiScreen.TextTarget.CONFIG;
      this.editingString = null;
      this.editingColor = null;
      this.colorBeforeEdit = null;
      this.editText = current;
      this.editCursor = this.editText.length();
      this.selectAll = false;
   }

   private void focusSearch() {
      this.textTarget = WindowClickGuiScreen.TextTarget.SEARCH;
      this.editingString = null;
      this.editingColor = null;
      this.colorBeforeEdit = null;
      this.editText = this.searchQuery;
      this.editCursor = this.editText.length();
      this.selectAll = false;
   }

   private void updateSearchPanel() {
      String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
      if (query.isEmpty()) {
         this.searchResults = List.of();
         this.searchSettingsPanel = null;
      } else {
         this.searchResults = ModuleManager.INSTANCE.modules().stream().filter((module) -> module.displayName().toLowerCase(Locale.ROOT).contains(query) || module.id().toLowerCase(Locale.ROOT).contains(query)).limit(10L).toList();
         if (this.searchSettingsPanel != null && this.searchResults.stream().noneMatch((module) -> module == ((Entry)this.searchSettingsPanel.entries.get(0)).module)) {
            this.searchSettingsPanel = null;
         }

      }
   }

   private void renderSearch(Canvas canvas, float screenWidth, float screenHeight) {
      float progress = easeOutCubic(this.searchOpenProgress);
      Bounds button = this.searchButtonBounds(screenWidth);
      if (progress <= 0.002F) {
         boolean hovered = button.contains(this.renderMouseX, this.renderMouseY);
         drawLowCostShadow(canvas, button.x(), button.y(), button.width(), button.height(), 7.0F);
         SkijaUi.rounded(canvas, button.x(), button.y(), button.width(), button.height(), 7.0F, hovered ? settingHover() : bodySurface());
         SkijaUi.outline(canvas, button.x(), button.y(), button.width(), button.height(), 7.0F, 1.0F, withAlpha(accent(), hovered ? 220 : 130));
         drawSearchGlyph(canvas, button.x() + button.width() * 0.5F, button.y() + button.height() * 0.5F, hovered ? TEXT : TEXT_DIM, 5.0F);
      } else {
         Bounds input = this.searchInputBounds(screenWidth);
         float resultHeight = this.searchResultHeight();
         float panelHeight = 24.0F + resultHeight * easeOutCubic(this.searchProgress);
         float panelX = lerp(button.x(), input.x(), progress);
         float panelWidth = lerp(button.width(), input.width(), progress);
         canvas.save();

         try {
            float centerX = button.x() + button.width() * 0.5F;
            float centerY = button.y() + button.height() * 0.5F;
            canvas.translate(centerX, centerY);
            canvas.scale(0.82F + progress * 0.18F, 0.82F + progress * 0.18F);
            canvas.translate(-centerX, -centerY);
            drawLowCostShadow(canvas, panelX, input.y(), panelWidth, panelHeight, 7.0F, progress);
            SkijaUi.rounded(canvas, panelX, input.y(), panelWidth, panelHeight, 7.0F, bodySurface());
            SkijaUi.outline(canvas, panelX, input.y(), panelWidth, panelHeight, 7.0F, 1.0F, withAlpha(accent(), Math.round(120.0F + 85.0F * progress)));
            if (this.searchProgress > 0.002F) {
               SkijaUi.fill(canvas, input.x() + 1.0F, input.y() + 24.0F, input.width() - 2.0F, 1.0F, withAlpha(accent(), Math.round(75.0F * this.searchProgress)));
            }

            boolean focused = this.textTarget == WindowClickGuiScreen.TextTarget.SEARCH;
            String value = focused ? this.textWithCursor() : this.searchQuery;
            String shown = value.isBlank() ? "Search modules" : value;
            if (progress > 0.22F) {
               if (progress < 0.999F) {
                  SEARCH_LAYER_PAINT.setAlpha(Math.round(255.0F * progress));
                  int textLayer = canvas.saveLayer(Rect.makeXYWH(input.x(), input.y(), input.width(), 24.0F), SEARCH_LAYER_PAINT);
                  ClickGuiText.drawVerticallyCentered(canvas, shown, input.x() + 10.0F, input.y(), input.width() - 38.0F, 24.0F, 9.0F, value.isBlank() ? TEXT_OFF : TEXT, 7.5F, false);
                  canvas.restoreToCount(textLayer);
                  SEARCH_LAYER_PAINT.setAlpha(255);
               } else {
                  ClickGuiText.drawVerticallyCentered(canvas, shown, input.x() + 10.0F, input.y(), input.width() - 38.0F, 24.0F, 9.0F, value.isBlank() ? TEXT_OFF : TEXT, 7.5F, false);
               }
            }

            Bounds toggle = this.searchToggleBounds(screenWidth);
            drawSearchGlyph(canvas, toggle.x() + toggle.width() * 0.5F, toggle.y() + toggle.height() * 0.5F, focused ? accent() : TEXT_DIM, 5.0F);
            if (this.searchProgress > 0.002F) {
               canvas.save();
               canvas.clipRRect(RRect.makeXYWH(input.x(), input.y(), input.width(), panelHeight, 7.0F), ClipMode.INTERSECT, true);
               if (this.searchProgress < 0.999F) {
                  SEARCH_LAYER_PAINT.setAlpha(Math.round(255.0F * this.searchProgress));
                  int resultLayer = canvas.saveLayer(Rect.makeXYWH(input.x(), input.y() + 24.0F, input.width(), Math.max(1.0F, panelHeight - 24.0F)), SEARCH_LAYER_PAINT);
                  this.renderSearchCards(canvas, input.x() + 7.0F, input.y() + 30.0F, input.width() - 14.0F, this.searchProgress);
                  canvas.restoreToCount(resultLayer);
                  SEARCH_LAYER_PAINT.setAlpha(255);
               } else {
                  this.renderSearchCards(canvas, input.x() + 7.0F, input.y() + 30.0F, input.width() - 14.0F, 1.0F);
               }

               canvas.restore();
               if (this.searchSettingsPanel != null) {
                  float settingsY = input.y() + 36.0F + resultHeight;
                  this.searchSettingsPanel.x = input.x() + input.width() + 10.0F;
                  this.searchSettingsPanel.y = settingsY;
                  this.searchSettingsPanel.clampPosition(screenWidth, screenHeight);
                  this.searchSettingsPanel.render(canvas, screenHeight);
               }
            }
         } finally {
            canvas.restore();
         }

      }
   }

   private void renderSearchCards(Canvas canvas, float x, float y, float width, float progress) {
      int columns = 2;
      float gap = 6.0F;
      float cardWidth = (width - gap) * 0.5F;
      float cardHeight = 34.0F;

      for(int index = 0; index < this.searchResults.size(); ++index) {
         Module module = (Module)this.searchResults.get(index);
         int row = index / columns;
         int column = index % columns;
         float cardX = x + (float)column * (cardWidth + gap);
         float cardY = y + (float)row * (cardHeight + gap);
         boolean hovered = inside((double)this.renderMouseX, (double)this.renderMouseY, cardX, cardY, cardWidth, cardHeight);
         int fill = hovered ? settingHover() : settingSurface();
         SkijaUi.rounded(canvas, cardX, cardY, cardWidth, cardHeight, 5.0F, withAlpha(fill, Math.round(235.0F * progress)));
         int nameColor = module.isEnabled() ? TEXT : TEXT_DIM;
         ClickGuiText.drawVerticallyCentered(canvas, module.displayName(), cardX + 7.0F, cardY + 1.0F, cardWidth - 14.0F, 17.0F, 8.5F, nameColor, 7.5F, false);
         ClickGuiText.drawVerticallyCentered(canvas, module.category().displayName(), cardX + 7.0F, cardY + 17.0F, cardWidth - 14.0F, 12.0F, 8.0F, TEXT_OFF, 7.0F, false);
      }

      if (this.searchResults.isEmpty()) {
         ClickGuiText.drawCentered(canvas, "No modules found", x, y, width, cardHeight, 8.5F, TEXT_DIM, 7.5F, false);
      }

   }

   private boolean handleSearchResultClick(float mouseX, float mouseY, int button, float screenWidth, float screenHeight) {
      Bounds input = this.searchInputBounds(screenWidth);
      float x = input.x() + 7.0F;
      float y = input.y() + 30.0F;
      float width = input.width() - 14.0F;
      float gap = 6.0F;
      float cardWidth = (width - gap) * 0.5F;
      float cardHeight = 34.0F;

      for(int index = 0; index < this.searchResults.size(); ++index) {
         int row = index / 2;
         int column = index % 2;
         float cardX = x + (float)column * (cardWidth + gap);
         float cardY = y + (float)row * (cardHeight + gap);
         if (inside((double)mouseX, (double)mouseY, cardX, cardY, cardWidth, cardHeight)) {
            Module module = (Module)this.searchResults.get(index);
            if (button == 0) {
               module.interactFromClickGui();
            } else if (button == 1) {
               this.searchSettingsPanel = new ModulePanel(module.category(), List.of(module), input.x() + input.width() + 10.0F, cardY);
               this.searchSettingsPanel.expandImmediately();
            }

            return true;
         }
      }

      if (this.searchSettingsPanel != null && this.searchSettingsPanel.mouseClicked(mouseX, mouseY, button, screenHeight)) {
         return true;
      } else {
         return false;
      }
   }

   private Bounds searchButtonBounds(float screenWidth) {
      return new Bounds(screenWidth * 0.5F - 14.0F, 10.0F, 28.0F, 28.0F);
   }

   private Bounds searchToggleBounds(float screenWidth) {
      Bounds button = this.searchButtonBounds(screenWidth);
      Bounds input = this.searchInputBounds(screenWidth);
      float progress = easeOutCubic(this.searchOpenProgress);
      float x = lerp(button.x(), input.x() + input.width() - 28.0F, progress);
      float y = lerp(button.y(), input.y() - 2.0F, progress);
      return new Bounds(x, y, 28.0F, 28.0F);
   }

   private Bounds searchInputBounds(float screenWidth) {
      float width = Math.min(300.0F, Math.max(180.0F, screenWidth * 0.3F));
      return new Bounds((screenWidth - width) * 0.5F, 10.0F, width, 24.0F);
   }

   private float searchResultHeight() {
      int rows = Math.max(1, (int)Math.ceil((double)((float)this.searchResults.size() / 2.0F)));
      return 7.0F + (float)rows * 34.0F + (float)Math.max(0, rows - 1) * 6.0F + 7.0F;
   }

   private static void drawSearchGlyph(Canvas canvas, float centerX, float centerY, int color, float radius) {
      SEARCH_STROKE_PAINT.setColor(color).setStrokeWidth(1.5F);
      canvas.drawCircle(centerX - 1.5F, centerY - 1.5F, radius, SEARCH_STROKE_PAINT);
      SkijaUi.line(canvas, centerX + 1.0F, centerY + 1.0F, centerX + radius + 3.0F, centerY + radius + 3.0F, 1.5F, color);
   }

   private void focusColor(ColorSetting setting) {
      this.textTarget = WindowClickGuiScreen.TextTarget.COLOR;
      this.editingString = null;
      this.editingColor = setting;
      this.colorBeforeEdit = setting.get();
      this.editText = setting.hex();
      this.editCursor = this.editText.length();
      this.selectAll = true;
   }

   private boolean handleTextKey(KeyEvent event) {
      if (event.key() != 256 && event.key() != 257 && event.key() != 335) {
         if (event.isSelectAll()) {
            this.selectAll = true;
            this.editCursor = this.editText.length();
            return true;
         } else if (event.isCopy()) {
            this.minecraft.keyboardHandler.setClipboard(this.editText);
            return true;
         } else if (event.isCut()) {
            this.minecraft.keyboardHandler.setClipboard(this.editText);
            this.editText = "";
            this.editCursor = 0;
            this.selectAll = false;
            this.applyEditedText();
            return true;
         } else if (event.isPaste()) {
            this.insertText(this.minecraft.keyboardHandler.getClipboard());
            return true;
         } else {
            switch (event.key()) {
               case 259:
                  if (this.selectAll) {
                     this.editText = "";
                     this.editCursor = 0;
                  } else if (this.editCursor > 0) {
                     String var2 = this.editText.substring(0, this.editCursor - 1);
                     this.editText = var2 + this.editText.substring(this.editCursor);
                     --this.editCursor;
                  }

                  this.applyEditedText();
                  break;
               case 260:
               case 264:
               case 265:
               case 266:
               case 267:
               default:
                  return false;
               case 261:
                  if (this.selectAll) {
                     this.editText = "";
                     this.editCursor = 0;
                  } else if (this.editCursor < this.editText.length()) {
                     String var10001 = this.editText.substring(0, this.editCursor);
                     this.editText = var10001 + this.editText.substring(this.editCursor + 1);
                  }

                  this.applyEditedText();
                  break;
               case 262:
                  this.editCursor = Math.min(this.editText.length(), this.editCursor + 1);
                  break;
               case 263:
                  this.editCursor = Math.max(0, this.editCursor - 1);
                  break;
               case 268:
                  this.editCursor = 0;
                  break;
               case 269:
                  this.editCursor = this.editText.length();
            }

            this.selectAll = false;
            return true;
         }
      } else {
         this.finishTextEditing();
         return true;
      }
   }

   private void insertText(String inserted) {
      if (inserted != null && !inserted.isEmpty()) {
         StringBuilder clean = new StringBuilder();
         IntStream var10000 = inserted.codePoints().filter((codepoint) -> codepoint >= 32 && codepoint != 127);
         Objects.requireNonNull(clean);
         var10000.forEach(clean::appendCodePoint);
         if (this.selectAll) {
            this.editText = "";
            this.editCursor = 0;
         }

         int maxLength = this.textTarget == WindowClickGuiScreen.TextTarget.COLOR ? 9 : 128;
         int remaining = maxLength - this.editText.length();
         if (remaining <= 0) {
            this.selectAll = false;
         } else {
            String value = clean.length() > remaining ? clean.substring(0, remaining) : clean.toString();
            this.editText = this.editText.substring(0, this.editCursor) + value + this.editText.substring(this.editCursor);
            this.editCursor += value.length();
            this.selectAll = false;
            this.applyEditedText();
         }
      }
   }

   private void applyEditedText() {
      if (this.textTarget == WindowClickGuiScreen.TextTarget.STRING && this.editingString != null) {
         this.editingString.set(this.editText);
      } else if (this.textTarget == WindowClickGuiScreen.TextTarget.CONFIG) {
         for(Panel panel : this.panels) {
            if (panel instanceof ConfigPanel) {
               ConfigPanel configPanel = (ConfigPanel)panel;
               configPanel.name = this.editText;
               break;
            }
         }
      } else if (this.textTarget == WindowClickGuiScreen.TextTarget.SEARCH) {
         this.searchQuery = this.editText;
         this.updateSearchPanel();
      } else if (this.textTarget == WindowClickGuiScreen.TextTarget.COLOR && this.editingColor != null) {
         Color parsed = this.editingColor.parseHex(this.editText);
         if (parsed != null) {
            this.editingColor.set(parsed);
         }
      }

   }

   private String textWithCursor() {
      int cursor = Math.max(0, Math.min(this.editCursor, this.editText.length()));
      String var10000 = this.editText.substring(0, cursor);
      return var10000 + "|" + this.editText.substring(cursor);
   }

   private void finishTextEditing() {
      if (this.textTarget != WindowClickGuiScreen.TextTarget.NONE) {
         boolean finishingSearch = this.textTarget == WindowClickGuiScreen.TextTarget.SEARCH;
         if (this.textTarget == WindowClickGuiScreen.TextTarget.COLOR && this.editingColor != null) {
            Color parsed = this.editingColor.parseHex(this.editText);
            this.editingColor.set(parsed != null ? parsed : this.colorBeforeEdit);
         } else {
            this.applyEditedText();
         }

         this.textTarget = WindowClickGuiScreen.TextTarget.NONE;
         this.editingString = null;
         this.editingColor = null;
         this.colorBeforeEdit = null;
         this.editText = "";
         this.editCursor = 0;
         this.selectAll = false;
         if (finishingSearch && this.searchQuery.isBlank()) {
            this.searchOpen = false;
            this.searchSettingsPanel = null;
         }

      }
   }

   private void clampPanels() {
      float logicalWidth = this.logicalWidth();
      float logicalHeight = this.logicalHeight();

      for(Panel panel : this.panels) {
         panel.clampPosition(logicalWidth, logicalHeight);
      }

   }

   private static void loadPanelMemoryFromDisk() {
      if (!panelMemoryDiskLoaded) {
         panelMemoryDiskLoaded = true;
         if (!panelMemorySaved) {
            try {
               Path file = ConfigManager.INSTANCE.configDirectory().resolve("drop-clickgui-state.json");
               if (!Files.isRegularFile(file, new LinkOption[0])) {
                  return;
               }

               Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);

               label93: {
                  try {
                     DropMemoryFile state = (DropMemoryFile)STATE_GSON.fromJson(reader, DropMemoryFile.class);
                     if (state != null && state.panels != null && !state.panels.isEmpty()) {
                        PANEL_MEMORY.clear();

                        for(Map.Entry<String, PanelMemory> entry : state.panels.entrySet()) {
                           PanelMemory memory = (PanelMemory)entry.getValue();
                           if (entry.getKey() != null && memory != null) {
                              memory.normalize();
                              PANEL_MEMORY.put((String)entry.getKey(), memory);
                           }
                        }

                        PANEL_ORDER_MEMORY.clear();
                        if (state.order != null) {
                           PANEL_ORDER_MEMORY.addAll(state.order);
                        }

                        gridEnabledMemory = state.gridEnabled;
                        panelMemorySaved = !PANEL_MEMORY.isEmpty();
                        break label93;
                     }
                  } catch (Throwable var7) {
                     if (reader != null) {
                        try {
                           reader.close();
                        } catch (Throwable var6) {
                           var7.addSuppressed(var6);
                        }
                     }

                     throw var7;
                  }

                  if (reader != null) {
                     reader.close();
                  }

                  return;
               }

               if (reader != null) {
                  reader.close();
               }
            } catch (Exception error) {
               Setsuna.LOGGER.warn("Failed to load Drop ClickGUI state; using defaults", error);
            }

         }
      }
   }

   private static void writePanelMemoryToDisk() {
      if (panelMemorySaved) {
         try {
            Path file = ConfigManager.INSTANCE.configDirectory().resolve("drop-clickgui-state.json");
            DropMemoryFile state = new DropMemoryFile();
            state.panels.putAll(PANEL_MEMORY);
            state.order.addAll(PANEL_ORDER_MEMORY);
            state.gridEnabled = gridEnabledMemory;
            Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);

            try {
               STATE_GSON.toJson(state, writer);
            } catch (Throwable var6) {
               if (writer != null) {
                  try {
                     writer.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }
               }

               throw var6;
            }

            if (writer != null) {
               writer.close();
            }
         } catch (Exception error) {
            Setsuna.LOGGER.warn("Failed to save Drop ClickGUI state", error);
         }

      }
   }

   private void restorePanelMemory() {
      if (panelMemorySaved) {
         Map<String, Panel> byTitle = new HashMap();

         for(Panel panel : this.panels) {
            byTitle.put(panel.memoryId, panel);
            PanelMemory memory = (PanelMemory)PANEL_MEMORY.get(panel.memoryId);
            if (memory != null) {
               panel.x = memory.x;
               panel.y = memory.y;
               panel.scroll = memory.scroll;
               panel.collapsed = memory.collapsed;
               panel.expandProgress = memory.collapsed ? 0.0F : 1.0F;
               if (panel instanceof ModulePanel) {
                  ModulePanel modulePanel = (ModulePanel)panel;

                  for(Entry entry : modulePanel.entries) {
                     entry.expanded = memory.expandedModules.contains(entry.module.id());
                     entry.expandProgress = entry.expanded ? 1.0F : 0.0F;
                  }

                  modulePanel.expandedHudComponents.clear();

                  for(HUDModuel component : HUD.INSTANCE.components()) {
                     if (memory.expandedHudComponents.contains(component.id())) {
                        modulePanel.expandedHudComponents.add(component);
                        modulePanel.hudExpandProgress.put(component, 1.0F);
                     }
                  }
               } else if (panel instanceof ConfigPanel) {
                  ConfigPanel configPanel = (ConfigPanel)panel;
                  configPanel.name = memory.configName;
               }
            }
         }

         List<Panel> restoredOrder = new ArrayList();

         for(String title : PANEL_ORDER_MEMORY) {
            Panel panel = (Panel)byTitle.remove(title);
            if (panel != null) {
               restoredOrder.add(panel);
            }
         }

         restoredOrder.addAll(byTitle.values());
         this.panels.clear();
         this.panels.addAll(restoredOrder);
      }
   }

   private void savePanelMemory() {
      if (!this.panels.isEmpty()) {
         PANEL_MEMORY.clear();
         PANEL_ORDER_MEMORY.clear();
         gridEnabledMemory = this.gridEnabled;

         for(Panel panel : this.panels) {
            PanelMemory memory = new PanelMemory();
            memory.x = panel.x;
            memory.y = panel.y;
            memory.scroll = panel.scroll;
            memory.collapsed = panel.collapsed;
            if (!(panel instanceof ModulePanel)) {
               if (panel instanceof ConfigPanel) {
                  ConfigPanel configPanel = (ConfigPanel)panel;
                  memory.configName = configPanel.name;
               }
            } else {
               ModulePanel modulePanel = (ModulePanel)panel;

               for(Entry entry : modulePanel.entries) {
                  if (entry.expanded) {
                     memory.expandedModules.add(entry.module.id());
                  }
               }

               for(HUDModuel component : modulePanel.expandedHudComponents) {
                  memory.expandedHudComponents.add(component.id());
               }
            }

            PANEL_MEMORY.put(panel.memoryId, memory);
            PANEL_ORDER_MEMORY.add(panel.memoryId);
         }

         panelMemorySaved = true;
         writePanelMemoryToDisk();
      }
   }

   private float configuredScale() {
      float requested = clamp((float)(Integer)ClickGui.INSTANCE.guiScale.get() / 100.0F, 0.65F, 1.25F);
      if (this.width <= 0) {
         return requested;
      } else {
         float fit = (float)this.width / 808.0F;
         return clamp(Math.min(requested, fit), 0.5F, 1.25F);
      }
   }

   private double logical(double coordinate) {
      return coordinate / (double)this.activeScale;
   }

   private float logicalWidth() {
      return Math.max(1.0F, (float)this.width / this.activeScale);
   }

   private float logicalHeight() {
      return Math.max(1.0F, (float)this.height / this.activeScale);
   }

   private Bounds gridButtonBounds(float screenWidth, float screenHeight) {
      return new Bounds(Math.max(8.0F, screenWidth - 28.0F - 12.0F), Math.max(8.0F, screenHeight - 28.0F - 12.0F), 28.0F, 28.0F);
   }

   private void updateGridAnimation() {
      float target = this.gridEnabled ? 1.0F : 0.0F;
      this.gridExpansion = animateTowards(this.gridExpansion, target, 8.5F, this.animationDelta);
      if (this.gridTransitioning && Math.abs(this.gridExpansion - target) < 0.004F) {
         this.gridExpansion = target;
         this.gridTransitioning = false;
      }

      boolean shouldShow = this.gridTransitioning || this.gridEnabled && this.draggingPanel != null;
      this.gridOpacity = animateTowards(this.gridOpacity, shouldShow ? 1.0F : 0.0F, shouldShow ? 13.0F : 9.0F, this.animationDelta);
   }

   private void renderGrid(Canvas canvas, float screenWidth, float screenHeight) {
      if (!(this.gridOpacity <= 0.002F) && !(this.gridExpansion <= 0.002F)) {
         Bounds button = this.gridButtonBounds(screenWidth, screenHeight);
         float centerX = button.x() + button.width() * 0.5F;
         float centerY = button.y() + button.height() * 0.5F;
         float maxRadius = (float)Math.hypot((double)Math.max(centerX, screenWidth - centerX), (double)Math.max(centerY, screenHeight - centerY));
         float radius = maxRadius * easeOutCubic(this.gridExpansion);
         DropGridRenderer.draw(canvas, screenWidth, screenHeight, centerX, centerY, radius, this.gridOpacity, accent());
      }
   }

   private void renderGridButton(Canvas canvas, float screenWidth, float screenHeight) {
      Bounds bounds = this.gridButtonBounds(screenWidth, screenHeight);
      boolean hovered = bounds.contains(this.renderMouseX, this.renderMouseY);
      int surface = this.gridEnabled ? withAlpha(accent(), hovered ? 205 : 158) : (hovered ? settingHover() : headerSurface());
      drawLowCostShadow(canvas, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 7.0F);
      SkijaUi.rounded(canvas, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 7.0F, panelEdge());
      SkijaUi.rounded(canvas, bounds.x() + 1.0F, bounds.y() + 1.0F, bounds.width() - 2.0F, bounds.height() - 2.0F, 6.0F, surface);
      float cellSize = 3.2F;
      float gap = 2.2F;
      float iconSize = cellSize * 3.0F + gap * 2.0F;
      float startX = bounds.x() + (bounds.width() - iconSize) * 0.5F;
      float startY = bounds.y() + (bounds.height() - iconSize) * 0.5F;
      int iconColor = !this.gridEnabled && !hovered ? TEXT_DIM : TEXT;

      for(int row = 0; row < 3; ++row) {
         for(int column = 0; column < 3; ++column) {
            SkijaUi.rounded(canvas, startX + (float)column * (cellSize + gap), startY + (float)row * (cellSize + gap), cellSize, cellSize, 0.8F, iconColor);
         }
      }

   }

   private static float snapToGrid(float coordinate) {
      return (float)Math.round(coordinate / 16.0F) * 16.0F;
   }

   private static float settingHeight(Setting<?> setting) {
      float headerHeight = settingHeaderHeight(setting);
      if (!(setting instanceof IntSetting) && !(setting instanceof DoubleSetting)
            && !(setting instanceof EnumSetting<?> enumSetting && enumSetting.isSlider())) {
         if (setting instanceof ColorSetting) {
            ColorSetting colorSetting = (ColorSetting)setting;
            return ColorPickerState.height(colorSetting, headerHeight, 10.0F, ColorPickerState.paletteHeight(124.0F));
         } else {
            return MusicPresetPreview.matches(setting) ? headerHeight + 23.0F : headerHeight;
         }
      } else {
         return headerHeight + 14.0F;
      }
   }

   private static float settingHeaderHeight(Setting<?> setting) {
      float labelWidth = setting instanceof ColorSetting ? 28.0F : (setting instanceof StringSetting && !(setting instanceof FontSetting) ? 34.0F : (setting instanceof ButtonSetting ? 114.0F : 56.0F));
      int lines = ClickGuiText.lineCount(setting.displayName(), labelWidth, 7.5F, setting instanceof ButtonSetting);
      if (setting instanceof FontSetting fontSetting) {
         lines = Math.max(lines, ClickGuiText.lineCount(fontSetting.displayValue(), 42.0F, 7.5F, false));
      } else if (setting instanceof EnumSetting<?> enumSetting) {
         lines = Math.max(lines, ClickGuiText.lineCount(enumSetting.displayValue(), 42.0F, 7.5F, false));
      }

      return Math.max(20.0F, (float)lines * 10.0F);
   }

   private static String categoryMemoryId(Category category) {
      String var10000;
      switch (category) {
         case COMBAT -> var10000 = "Combat";
         case MISC -> var10000 = "Misc";
         case HUD -> var10000 = "HUD";
         case RENDER -> var10000 = "Render";
         case MOVEMENT -> var10000 = "Movement";
         case PLAYER -> var10000 = "Player";
         case CLIENT -> var10000 = "Client";
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static String categoryIcon(Category category) {
      return CategoryGlyphs.forCategory(category);
   }

   private static String currentConfigName() {
      String current = configProvider.currentName();
      return current != null && !current.isBlank() ? current : "default";
   }

   private static List<String> configProfiles() {
      List<String> profiles = configProvider.profiles();
      return profiles == null ? List.of() : List.copyOf(profiles);
   }

   private static int accent() {
      Color configured = ClickGui.INSTANCE.accent.get();
      return argb(255, configured.getRed(), configured.getGreen(), configured.getBlue());
   }

   private static int headerSurface() {
      return UiTheme.themedHeader(230);
   }

   private static int backdrop() {
      return UiTheme.themedBackdrop(54);
   }

   private static int bodySurface() {
      return UiTheme.themedSurface(234);
   }

   private static int panelEdge() {
      return UiTheme.themedRaised(244);
   }

   private static int settingSurface() {
      return UiTheme.themedSurfaceAlt(200);
   }

   private static int settingHover() {
      return UiTheme.themedHover(184);
   }

   private static int trackSurface() {
      return UiTheme.themedRaised(255);
   }

   private static String fit(String text, float maxWidth, boolean bold) {
      String value = (String)Objects.requireNonNullElse(text, "");
      if (maxWidth <= 0.0F) {
         return "";
      } else if (textWidth(value, bold) <= maxWidth) {
         return value;
      } else {
         String suffix;
         for(suffix = "..."; !value.isEmpty() && textWidth(value + suffix, bold) > maxWidth; value = value.substring(0, value.length() - 1)) {
         }

         return value.isEmpty() ? "" : value + suffix;
      }
   }

   private static float textWidth(String text, boolean bold) {
      return bold ? SkijaUi.boldTextWidth(text, 9.0F) : SkijaUi.textWidth(text, 9.0F);
   }

   private static void drawText(Canvas canvas, String text, float x, float y, float height, int color, float size) {
      SkijaUi.text(canvas, text, x, y, height, color, size);
   }

   private static void drawBoldText(Canvas canvas, String text, float x, float y, float height, int color, float size) {
      SkijaUi.boldText(canvas, text, x, y, height, color, size);
   }

   private static void drawCentered(Canvas canvas, String text, float x, float y, float width, float height, int color) {
      float textWidth = SkijaUi.textWidth(text, 9.0F);
      drawText(canvas, text, x + (width - textWidth) * 0.5F, y, height, color, 9.0F);
   }

   private static void drawChevron(Canvas canvas, float centerX, float centerY, boolean expanded, int color) {
      float halfWidth = 3.2F;
      float halfHeight = 2.0F;
      float middleY = expanded ? centerY - halfHeight : centerY + halfHeight;
      float edgeY = expanded ? centerY + halfHeight : centerY - halfHeight;
      SkijaUi.line(canvas, centerX - halfWidth, edgeY, centerX, middleY, 1.1F, color);
      SkijaUi.line(canvas, centerX, middleY, centerX + halfWidth, edgeY, 1.1F, color);
   }

   private static void drawLowCostShadow(Canvas canvas, float x, float y, float width, float height, float radius) {
      drawLowCostShadow(canvas, x, y, width, height, radius, 1.0F);
   }

   private static void drawLowCostShadow(Canvas canvas, float x, float y, float width, float height, float radius, float opacity) {
      int alpha = Math.round(90.0F * clamp(opacity, 0.0F, 1.0F));
      SkijaUi.rounded(canvas, x + 1.0F, y + 3.0F, width, height, radius, withAlpha(-16777216, alpha));
   }

   private static void drawGradientRect(Canvas canvas, float x, float y, float width, float height, int start, int end) {
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         try {
            Shader shader = Shader.makeLinearGradient(x, y, x + width, y, new int[]{start, end});

            try {
               GRADIENT_PAINT.setShader(shader).setColor(-1).setAlpha(255);
               canvas.drawRect(Rect.makeXYWH(x, y, width, height), GRADIENT_PAINT);
            } catch (Throwable var15) {
               if (shader != null) {
                  try {
                     shader.close();
                  } catch (Throwable var14) {
                     var15.addSuppressed(var14);
                  }
               }

               throw var15;
            }

            if (shader != null) {
               shader.close();
            }
         } finally {
            GRADIENT_PAINT.setShader((Shader)null).setAlpha(255);
         }

      }
   }

   private static void drawSliderLayer(Canvas canvas, float start, float end, float gapStart, float gapEnd, float y, float height, int color) {
      if (!(end <= start)) {
         if (!(gapEnd <= start) && !(gapStart >= end)) {
            float leftEnd = Math.min(end, gapStart);
            if (leftEnd > start) {
               float fadeStart = Math.max(start, leftEnd - 5.0F);
               if (fadeStart > start) {
                  SkijaUi.fill(canvas, start, y, fadeStart - start, height, color);
               }

               drawGradientRect(canvas, fadeStart, y, leftEnd - fadeStart, height, color, withAlpha(color, 0));
            }

            float rightStart = Math.max(start, gapEnd);
            if (end > rightStart) {
               float fadeEnd = Math.min(end, rightStart + 5.0F);
               drawGradientRect(canvas, rightStart, y, fadeEnd - rightStart, height, withAlpha(color, 0), color);
               if (end > fadeEnd) {
                  SkijaUi.fill(canvas, fadeEnd, y, end - fadeEnd, height, color);
               }
            }

         } else {
            SkijaUi.fill(canvas, start, y, end - start, height, color);
         }
      }
   }

   private static String trimZeros(String value) {
      int end;
      for(end = value.length(); end > 0 && value.charAt(end - 1) == '0'; --end) {
      }

      if (end > 0 && value.charAt(end - 1) == '.') {
         --end;
      }

      return value.substring(0, end);
   }

   private static String tr(String suffix, String fallback) {
      return TranslationKey.of("setsuna.gui." + suffix, fallback).get();
   }

   private static boolean inside(double mouseX, double mouseY, float x, float y, float width, float height) {
      return mouseX >= (double)x && mouseX < (double)(x + width) && mouseY >= (double)y && mouseY < (double)(y + height);
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private static float animateTowards(float current, float target, float speed, float delta) {
      if (Math.abs(target - current) < 0.002F) {
         return target;
      } else {
         float amount = 1.0F - (float)Math.exp((double)(-speed * Math.max(0.0F, delta)));
         return current + (target - current) * amount;
      }
   }

   private static float easeOutCubic(float value) {
      float clamped = clamp(value, 0.0F, 1.0F);
      float inverse = 1.0F - clamped;
      return 1.0F - inverse * inverse * inverse;
   }

   private static int argb(int alpha, int red, int green, int blue) {
      return alpha << 24 | red << 16 | green << 8 | blue;
   }

   private static int withAlpha(int color, int alpha) {
      return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
   }

   private static int mixColor(int first, int second, float amount) {
      float t = clamp(amount, 0.0F, 1.0F);
      int a = Math.round((float)(first >>> 24 & 255) + (float)((second >>> 24 & 255) - (first >>> 24 & 255)) * t);
      int r = Math.round((float)(first >>> 16 & 255) + (float)((second >>> 16 & 255) - (first >>> 16 & 255)) * t);
      int g = Math.round((float)(first >>> 8 & 255) + (float)((second >>> 8 & 255) - (first >>> 8 & 255)) * t);
      int b = Math.round((float)(first & 255) + (float)((second & 255) - (first & 255)) * t);
      return argb(a, r, g, b);
   }

   private static float lerp(float start, float end, float progress) {
      return start + (end - start) * clamp(progress, 0.0F, 1.0F);
   }

   static {
      SEARCH_STROKE_PAINT = (new Paint()).setAntiAlias(true).setMode(PaintMode.STROKE).setStrokeWidth(1.4F);
      SEARCH_LAYER_PAINT = (new Paint()).setAntiAlias(true);
      STATE_GSON = (new GsonBuilder()).setPrettyPrinting().create();
      DEFAULT_CONFIG_PROVIDER = new ConfigProvider() {
         public String currentName() {
            return ConfigManager.INSTANCE.currentProfile();
         }

         public List<String> profiles() {
            return ConfigManager.INSTANCE.listProfiles();
         }

         public void save(String name) {
            try {
               ConfigManager.INSTANCE.saveProfile(name);
            } catch (Exception error) {
               Setsuna.LOGGER.error("Failed to save Drop ClickGUI profile {}", name, error);
            }

         }

         public void load(String name) {
            try {
               ConfigManager.INSTANCE.loadProfile(name);
            } catch (Exception error) {
               Setsuna.LOGGER.error("Failed to load Drop ClickGUI profile {}", name, error);
            }

         }

         public void load(String name, boolean visualAndHud, boolean keybinds) {
            try {
               ConfigManager.INSTANCE.loadProfile(name, new ConfigManager.ImportOptions(visualAndHud, keybinds));
            } catch (Exception error) {
               Setsuna.LOGGER.error("Failed to load Drop ClickGUI profile {}", name, error);
            }

         }

         public void delete(String name) {
            try {
               ConfigManager.INSTANCE.deleteProfile(name);
            } catch (Exception error) {
               Setsuna.LOGGER.error("Failed to delete Drop ClickGUI profile {}", name, error);
            }

         }

         public void persist() {
            ConfigManager.INSTANCE.save();
         }
      };
      configProvider = DEFAULT_CONFIG_PROVIDER;
      PANEL_MEMORY = new HashMap();
      PANEL_ORDER_MEMORY = new ArrayList();
   }

   public interface ConfigProvider {
      default String currentName() {
         return "default";
      }

      default List<String> profiles() {
         return List.of();
      }

      default void save(String name) {
      }

      default void load(String name) {
      }

      default void load(String name, boolean visualAndHud, boolean keybinds) {
         this.load(name);
      }

      default void delete(String name) {
      }

      default void persist() {
      }
   }

   private abstract class Panel {
      private final String memoryId;
      private final String title;
      private final String icon;
      protected final float panelWidth;
      protected float x;
      protected float y;
      private float scroll;
      private boolean collapsed;
      private float expandProgress;

      private Panel(String memoryId, String title, String icon, float x, float y) {
         Objects.requireNonNull(WindowClickGuiScreen.this);
         super();
         this.collapsed = true;
         this.memoryId = memoryId;
         this.title = title;
         this.icon = icon;
         this.x = x;
         this.y = y;
         this.panelWidth = 124.0F;
      }

      protected final void render(Canvas canvas, float screenHeight) {
         this.updateAnimations();
         this.expandProgress = WindowClickGuiScreen.animateTowards(this.expandProgress, this.collapsed ? 0.0F : 1.0F, 14.0F, WindowClickGuiScreen.this.animationDelta);
         // Measure after animation updates and reuse the result for clipping and scrolling.
         float contentHeight = this.expandProgress > 0.0F ? this.contentHeight() : 0.0F;
         if (this.expandProgress > 0.0F) this.clampScroll(screenHeight, contentHeight);
         float bodyHeight = this.expandedBodyHeight(screenHeight, contentHeight) * this.expandProgress;
         float cardHeight = 24.0F + bodyHeight;
         WindowClickGuiScreen.drawLowCostShadow(canvas, this.x, this.y, this.panelWidth, cardHeight, 7.0F);
         SkijaUi.rounded(canvas, this.x, this.y, this.panelWidth, cardHeight, 7.0F, WindowClickGuiScreen.panelEdge());
         SkijaUi.rounded(canvas, this.x + 1.0F, this.y + 1.0F, Math.max(0.0F, this.panelWidth - 2.0F), Math.max(0.0F, cardHeight - 2.0F), 6.0F, WindowClickGuiScreen.bodySurface());
         SkijaUi.rounded(canvas, this.x + 2.0F, this.y + 2.0F, Math.max(0.0F, this.panelWidth - 4.0F), Math.max(0.0F, cardHeight - 4.0F), 5.0F, WindowClickGuiScreen.withAlpha(WindowClickGuiScreen.accent(), 12));
         canvas.save();
         canvas.clipRRect(RRect.makeXYWH(this.x + 1.0F, this.y + 1.0F, Math.max(0.0F, this.panelWidth - 2.0F), Math.max(0.0F, cardHeight - 2.0F), 6.0F), ClipMode.INTERSECT, true);
         SkijaUi.rounded(canvas, this.x + 1.0F, this.y + 1.0F, this.panelWidth - 2.0F, 24.0F, 6.0F, WindowClickGuiScreen.headerSurface());
         SkijaUi.fill(canvas, this.x + 1.0F, this.y + 24.0F - 7.0F, this.panelWidth - 2.0F, 7.0F, WindowClickGuiScreen.headerSurface());
         SkijaUi.fill(canvas, this.x + 1.0F, this.y + 24.0F - 1.0F, this.panelWidth - 2.0F, 1.0F, WindowClickGuiScreen.withAlpha(WindowClickGuiScreen.accent(), 72));
         canvas.restore();
         SkijaUi.icon(canvas, this.icon, this.x + 7.0F, this.y, 24.0F, WindowClickGuiScreen.accent(), 12.0F, SkijaUi.IconSet.LUCIDE);
         ClickGuiText.drawVerticallyCentered(canvas, this.title, this.x + 26.0F, this.y, this.panelWidth - 39.0F, 24.0F, 9.0F, WindowClickGuiScreen.TEXT, 9.0F, true);
         WindowClickGuiScreen.drawChevron(canvas, this.x + this.panelWidth - 12.0F, this.y + 12.0F, !this.collapsed, WindowClickGuiScreen.TEXT_DIM);
         if (!(bodyHeight <= 0.1F)) {
            float bodyY = this.y + 24.0F;
            float bodyX = this.bodyX();
            float bodyWidth = this.bodyWidth();
            canvas.save();
            canvas.clipRRect(RRect.makeXYWH(this.x + 1.0F, this.y + 1.0F, this.panelWidth - 2.0F, Math.max(1.0F, cardHeight - 2.0F), 6.0F), ClipMode.INTERSECT, true);
            canvas.clipRect(Rect.makeXYWH(bodyX, bodyY, bodyWidth, bodyHeight));
            this.renderContent(canvas, bodyY - this.scroll, bodyY, bodyHeight);
            canvas.restore();
            this.renderScrollbar(canvas, bodyY, bodyHeight, contentHeight);
         }
      }

      protected final boolean mouseClicked(float mouseX, float mouseY, int button, float screenHeight) {
         if (!WindowClickGuiScreen.inside((double)mouseX, (double)mouseY, this.x, this.y, this.panelWidth, 24.0F)) {
            return !this.collapsed && WindowClickGuiScreen.inside((double)mouseX, (double)mouseY, this.bodyX(), this.y + 24.0F, this.bodyWidth(), this.bodyHeight(screenHeight)) ? this.clickContent(mouseX, mouseY + this.scroll, button) : false;
         } else {
            if (button == 1 || button == 0 && mouseX >= this.x + this.panelWidth - 24.0F) {
               this.collapsed = !this.collapsed;
               this.clampScroll(screenHeight);
            } else if (button == 0) {
               WindowClickGuiScreen.this.draggingPanel = this;
               WindowClickGuiScreen.this.dragOffsetX = mouseX - this.x;
               WindowClickGuiScreen.this.dragOffsetY = mouseY - this.y;
            }

            return true;
         }
      }

      private boolean scrollAt(float mouseX, float mouseY, float amount, float screenHeight) {
         float bodyHeight = this.bodyHeight(screenHeight);
         if (!this.collapsed && !(bodyHeight <= 0.0F) && WindowClickGuiScreen.inside((double)mouseX, (double)mouseY, this.bodyX(), this.y + 24.0F, this.bodyWidth(), bodyHeight)) {
            this.scroll -= amount * 22.0F;
            this.clampScroll(screenHeight);
            return true;
         } else {
            return false;
         }
      }

      private float bodyHeight(float screenHeight) {
         return this.expandProgress <= 0.0F ? 0.0F : this.expandedBodyHeight(screenHeight, this.contentHeight()) * this.expandProgress;
      }

      private float expandedBodyHeight(float screenHeight, float contentHeight) {
         float available = Math.max(0.0F, screenHeight - this.y - 24.0F - 12.0F);
         return Math.min(contentHeight, Math.min(310.0F, available));
      }

      protected final void clampPosition(float screenWidth, float screenHeight) {
         this.x = WindowClickGuiScreen.clamp(this.x, 0.0F, Math.max(0.0F, screenWidth - this.panelWidth));
         this.y = WindowClickGuiScreen.clamp(this.y, 0.0F, Math.max(0.0F, screenHeight - 24.0F));
      }

      protected final void expandImmediately() {
         this.collapsed = false;
         this.expandProgress = 1.0F;
      }

      protected final void clampScroll(float screenHeight) {
         if (this.collapsed && this.expandProgress <= 0.0F) return;
         this.clampScroll(screenHeight, this.contentHeight());
      }

      private void clampScroll(float screenHeight, float contentHeight) {
         this.scroll = WindowClickGuiScreen.clamp(this.scroll, 0.0F, Math.max(0.0F, contentHeight - this.expandedBodyHeight(screenHeight, contentHeight)));
      }

      protected void updateAnimations() {
      }

      private void renderScrollbar(Canvas canvas, float bodyY, float bodyHeight, float contentHeight) {
         if (!(contentHeight <= bodyHeight) && !(bodyHeight <= 0.0F)) {
            float thumbHeight = Math.max(10.0F, bodyHeight * bodyHeight / contentHeight);
            float travel = bodyHeight - thumbHeight;
            float maxScroll = contentHeight - bodyHeight;
            float thumbY = bodyY + (maxScroll <= 0.0F ? 0.0F : this.scroll / maxScroll * travel);
            float scrollbarX = this.bodyX() + this.bodyWidth() - 2.0F;
            SkijaUi.rounded(canvas, scrollbarX, bodyY + 2.0F, 2.0F, Math.max(1.0F, bodyHeight - 4.0F), 1.0F, 1711276032);
            SkijaUi.rounded(canvas, scrollbarX, thumbY, 2.0F, thumbHeight, 1.0F, WindowClickGuiScreen.accent());
         }
      }

      protected final void drawSettingSection(Canvas canvas, float rowY, float height) {
         float sectionX = this.x + 4.0F;
         float sectionWidth = this.panelWidth - 8.0F;
         SkijaUi.rounded(canvas, sectionX, rowY + 1.5F, sectionWidth, Math.max(1.0F, height - 3.0F), 5.0F, WindowClickGuiScreen.settingSurface());
         if (WindowClickGuiScreen.inside((double)WindowClickGuiScreen.this.renderMouseX, (double)WindowClickGuiScreen.this.renderMouseY, sectionX, rowY + 1.5F, sectionWidth, Math.max(1.0F, height - 3.0F))) {
            SkijaUi.rounded(canvas, sectionX, rowY + 1.5F, sectionWidth, Math.max(1.0F, height - 3.0F), 5.0F, WindowClickGuiScreen.settingHover());
         }

      }

      private float bodyX() {
         return this.x + 2.0F;
      }

      private float bodyWidth() {
         return this.panelWidth - 4.0F;
      }

      protected abstract float contentHeight();

      protected abstract void renderContent(Canvas var1, float var2, float var3, float var4);

      protected abstract boolean clickContent(float var1, float var2, int var3);
   }

   private final class EmptyPanel extends Panel {
      private EmptyPanel(String title, float x, float y) {
         Objects.requireNonNull(WindowClickGuiScreen.this);
         super(title, title, "N", x, y);
      }

      protected float contentHeight() {
         return 0.0F;
      }

      protected void renderContent(Canvas canvas, float top, float bodyY, float bodyHeight) {
      }

      protected boolean clickContent(float mouseX, float contentMouseY, int button) {
         return false;
      }
   }

   private final class ModulePanel extends Panel {
      private final List<Entry> entries;
      private final Set<HUDModuel> expandedHudComponents;
      private final Map<HUDModuel, Float> hudExpandProgress;
      private final boolean hasHudEntry;

      private ModulePanel(Category category, List<Module> modules, float x, float y) {
         Objects.requireNonNull(WindowClickGuiScreen.this);
         super(WindowClickGuiScreen.categoryMemoryId(category), category.displayName(), WindowClickGuiScreen.categoryIcon(category), x, y);
         this.entries = new ArrayList();
         this.expandedHudComponents = new HashSet();
         this.hudExpandProgress = new HashMap();
         this.hasHudEntry = modules.contains(HUD.INSTANCE);

         for(Module module : modules) {
            this.entries.add(new Entry(module, false));
         }

      }

      protected float contentHeight() {
         float height = 0.0F;

         for(Entry entry : this.entries) {
            height += 20.0F;
            entry.detailsHeight = this.entryDetailsHeight(entry);
            height += entry.detailsHeight * entry.expandProgress;
         }

         return height;
      }

      protected void updateAnimations() {
         for(Entry entry : this.entries) {
            entry.expandProgress = WindowClickGuiScreen.animateTowards(entry.expandProgress, entry.expanded ? 1.0F : 0.0F, 14.0F, WindowClickGuiScreen.this.animationDelta);
            entry.enabledProgress = WindowClickGuiScreen.animateTowards(entry.enabledProgress, entry.module.isEnabled() ? 1.0F : 0.0F, 16.0F, WindowClickGuiScreen.this.animationDelta);
         }

         if (!this.hasHudEntry) return;
         for(HUDModuel component : HUD.INSTANCE.components()) {
            float current = (Float)this.hudExpandProgress.getOrDefault(component, 0.0F);
            float target = this.expandedHudComponents.contains(component) ? 1.0F : 0.0F;
            if (current != target) {
               this.hudExpandProgress.put(component, WindowClickGuiScreen.animateTowards(current, target, 14.0F, WindowClickGuiScreen.this.animationDelta));
            }
         }

      }

      protected void renderContent(Canvas canvas, float top, float bodyY, float bodyHeight) {
         float rowY = top;

         for(Entry entry : this.entries) {
            this.renderModule(canvas, entry, rowY, bodyY, bodyHeight);
            rowY += 20.0F;
            float visibleDetails = entry.detailsHeight * entry.expandProgress;
            if (visibleDetails > 0.1F && this.rowVisible(rowY, visibleDetails, bodyY, bodyHeight)) {
               canvas.save();
               canvas.clipRect(Rect.makeXYWH(this.x + 2.0F, rowY, this.panelWidth - 4.0F, visibleDetails));
               this.renderEntryDetails(canvas, entry, rowY, bodyY, bodyHeight);
               canvas.restore();
            }
            rowY += visibleDetails;
         }

      }

      private float renderEntryDetails(Canvas canvas, Entry entry, float rowY, float bodyY, float bodyHeight) {
         float startY = rowY;
         this.renderModuleKeybind(canvas, entry.module, rowY, bodyY, bodyHeight);
         rowY += 20.0F;
         for (Setting<?> setting : this.entrySettings(entry)) {
            if (setting.visible()) {
               this.renderSetting(canvas, setting, rowY, bodyY, bodyHeight);
               rowY += WindowClickGuiScreen.settingHeight(setting);
            }
         }
         if (entry.module == HUD.INSTANCE) {
            for(HUDModuel component : HUD.INSTANCE.components()) {
               this.renderHudComponent(canvas, component, rowY, bodyY, bodyHeight);
               rowY += 20.0F;
               float visibleSettings = this.visibleComponentSettingsHeight(component);
               if (visibleSettings > 0.1F && this.rowVisible(rowY, visibleSettings, bodyY, bodyHeight)) {
                  canvas.save();
                  canvas.clipRect(Rect.makeXYWH(this.x + 2.0F, rowY, this.panelWidth - 4.0F, visibleSettings));
                  float settingY = rowY;

                  for(Setting<?> setting : component.settings()) {
                     if (!this.isHudPosition(component, setting) && setting.visible()) {
                        this.renderSetting(canvas, setting, settingY, bodyY, bodyHeight, true);
                        settingY += WindowClickGuiScreen.settingHeight(setting);
                     }
                  }

                  canvas.restore();
               }
               rowY += visibleSettings;
            }
         }

         return rowY - startY;
      }

      private float entryDetailsHeight(Entry entry) {
         if (entry.expandProgress <= 0.0F) return 0.0F;
         float height = 20.0F;
         for (Setting<?> setting : this.entrySettings(entry)) {
            if (setting.visible()) {
               height += WindowClickGuiScreen.settingHeight(setting);
            }
         }
         if (entry.module == HUD.INSTANCE) {
            for(HUDModuel component : HUD.INSTANCE.components()) {
               height += 20.0F + this.visibleComponentSettingsHeight(component);
            }
         }
         return height;
      }

      private List<Setting<?>> entrySettings(Entry entry) {
         return entry.module == HUD.INSTANCE ? HUD.INSTANCE.controllerSettings() : entry.module.settings();
      }

      private float visibleComponentSettingsHeight(HUDModuel component) {
         float progress = this.hudExpandProgress.getOrDefault(component, 0.0F);
         return progress <= 0.0F ? 0.0F : this.componentSettingsHeight(component) * progress;
      }

      private float componentSettingsHeight(HUDModuel component) {
         float height = 0.0F;

         for(Setting<?> setting : component.settings()) {
            if (!this.isHudPosition(component, setting) && setting.visible()) {
               height += WindowClickGuiScreen.settingHeight(setting);
            }
         }

         return height;
      }

      private void renderModule(Canvas canvas, Entry entry, float rowY, float bodyY, float bodyHeight) {
         Module module = entry.module;
         if (!this.rowVisible(rowY, 20.0F, bodyY, bodyHeight)) {
            entry.hoverProgress = WindowClickGuiScreen.animateTowards(entry.hoverProgress, 0.0F, 18.0F, WindowClickGuiScreen.this.animationDelta);
         } else {
            boolean hovered = WindowClickGuiScreen.inside((double)WindowClickGuiScreen.this.renderMouseX, (double)WindowClickGuiScreen.this.renderMouseY, this.x + 4.0F, rowY + 1.5F, this.panelWidth - 8.0F, 17.0F) && this.rowVisible(rowY, 20.0F, bodyY, bodyHeight);
            entry.hoverProgress = WindowClickGuiScreen.animateTowards(entry.hoverProgress, hovered ? 1.0F : 0.0F, 18.0F, WindowClickGuiScreen.this.animationDelta);
            if (entry.enabledProgress > 0.002F) {
               SkijaUi.rounded(canvas, this.x + 4.0F, rowY + 1.5F, this.panelWidth - 8.0F, 17.0F, 5.0F, WindowClickGuiScreen.withAlpha(WindowClickGuiScreen.accent(), Math.round(82.0F * entry.enabledProgress)));
            }

            if (entry.hoverProgress > 0.002F) {
               SkijaUi.rounded(canvas, this.x + 4.0F, rowY + 1.5F, this.panelWidth - 8.0F, 17.0F, 5.0F, WindowClickGuiScreen.withAlpha(WindowClickGuiScreen.settingHover(), Math.round(183.0F * entry.hoverProgress)));
            }

            int nameColor = WindowClickGuiScreen.mixColor(WindowClickGuiScreen.TEXT_DIM, WindowClickGuiScreen.TEXT, entry.enabledProgress);
            String compactName = module.displayName();
            ClickGuiText.drawVerticallyCentered(canvas, compactName, this.x + 9.0F, rowY, this.panelWidth - 22.0F, 20.0F, 9.0F, nameColor, 9.0F, false);
            if (WindowClickGuiScreen.this.capturingModule == module) {
               WindowClickGuiScreen.drawText(canvas, "...", this.x + this.panelWidth - 18.0F, rowY, 20.0F, WindowClickGuiScreen.accent(), 9.0F);
            } else {
               WindowClickGuiScreen.drawChevron(canvas, this.x + this.panelWidth - 11.0F, rowY + 10.0F, entry.expanded, entry.expanded ? WindowClickGuiScreen.accent() : WindowClickGuiScreen.TEXT_DIM);
            }

         }
      }

      private void renderModuleKeybind(Canvas canvas, Module module, float rowY, float bodyY, float bodyHeight) {
         if (this.rowVisible(rowY, 20.0F, bodyY, bodyHeight)) {
            this.drawSettingSection(canvas, rowY, 20.0F);
            WindowClickGuiScreen.drawText(canvas, WindowClickGuiScreen.tr("keybind", "Keybind"), this.x + 7.0F, rowY, 20.0F, WindowClickGuiScreen.TEXT_DIM, 7.5F);
            String value = WindowClickGuiScreen.this.capturingModule == module ? "..." : KeyBindText.of(module.keyBind());
            if (value == null || value.isBlank()) {
               value = WindowClickGuiScreen.tr("none", "NONE");
            }

            this.drawRight(canvas, WindowClickGuiScreen.fit(value, 45.0F, false), rowY, 20.0F, WindowClickGuiScreen.this.capturingModule == module ? WindowClickGuiScreen.accent() : WindowClickGuiScreen.TEXT, 7.5F);
         }
      }

      private void renderHudComponent(Canvas canvas, HUDModuel component, float rowY, float bodyY, float bodyHeight) {
         if (this.rowVisible(rowY, 20.0F, bodyY, bodyHeight)) {
            this.drawSettingSection(canvas, rowY, 20.0F);
            BooleanSetting toggle = HUD.INSTANCE.componentSetting(component);
            String label = component.displayName();
            ClickGuiText.drawVerticallyCentered(canvas, label, this.x + 7.0F, rowY, 65.0F, 20.0F, 9.0F, (Boolean)toggle.get() ? WindowClickGuiScreen.TEXT : WindowClickGuiScreen.TEXT_DIM, 7.5F, false);
            WindowClickGuiScreen.drawChevron(canvas, this.x + this.panelWidth - 34.0F, rowY + 10.0F, this.expandedHudComponents.contains(component), WindowClickGuiScreen.TEXT_DIM);
            this.renderToggle(canvas, toggle, this.x + this.panelWidth - 25.0F, rowY + 5.0F);
         }
      }

      private void renderSetting(Canvas canvas, Setting<?> setting, float rowY, float bodyY, float bodyHeight) {
         this.renderSetting(canvas, setting, rowY, bodyY, bodyHeight, false);
      }

      private void renderSetting(Canvas canvas, Setting<?> setting, float rowY, float bodyY, float bodyHeight, boolean nested) {
         float height = WindowClickGuiScreen.settingHeight(setting);
         if (this.rowVisible(rowY, height, bodyY, bodyHeight)) {
            float headerHeight = WindowClickGuiScreen.settingHeaderHeight(setting);
            this.drawSettingSection(canvas, rowY, height);
            if (setting instanceof ButtonSetting) {
               ClickGuiText.drawCentered(canvas, setting.displayName(), this.x + 5.0F, rowY, this.panelWidth - 10.0F, headerHeight, 10.0F, WindowClickGuiScreen.accent(), 9.0F, false);
            } else {
               float labelWidth = setting instanceof ColorSetting ? (nested ? 28.0F : 34.0F) : (setting instanceof StringSetting && !(setting instanceof FontSetting) ? (nested ? 34.0F : 38.0F) : (nested ? 56.0F : 62.0F));
               ClickGuiText.drawVerticallyCentered(canvas, setting.displayName(), this.x + (nested ? 10.0F : 7.0F), rowY, labelWidth, headerHeight, 10.0F, WindowClickGuiScreen.TEXT_DIM, 7.5F, false);
               if (setting instanceof BooleanSetting) {
                  BooleanSetting booleanSetting = (BooleanSetting)setting;
                  this.renderToggle(canvas, booleanSetting, this.x + this.panelWidth - 25.0F, rowY + (headerHeight - 10.0F) * 0.5F);
               } else if (setting instanceof FontSetting) {
                  FontSetting fontSetting = (FontSetting)setting;
                  ClickGuiText.drawRightCentered(canvas, fontSetting.displayValue(), this.x + this.panelWidth - 6.0F, rowY, 42.0F, headerHeight, 9.0F, WindowClickGuiScreen.accent(), 7.5F, false);
               } else if (setting instanceof EnumSetting) {
                  EnumSetting<?> enumSetting = (EnumSetting)setting;
                  if (enumSetting.isSlider()) {
                     this.renderNumberSlider(canvas, rowY, enumSetting.sliderFraction(),
                           enumSetting.displayValue(), headerHeight);
                  } else {
                     ClickGuiText.drawRightCentered(canvas, enumSetting.displayValue(), this.x + this.panelWidth - 6.0F, rowY, 42.0F, headerHeight, 9.0F, WindowClickGuiScreen.accent(), 7.5F, false);
                  }
                  if (!enumSetting.isSlider() && MusicPresetPreview.matches(setting)) {
                     MusicPresetPreview.draw(canvas, this.x + 4.0F, rowY + headerHeight, Math.max(1.0F, this.panelWidth - 8.0F), height - headerHeight, 1.0F);
                  }
               } else if (setting instanceof IntSetting) {
                  IntSetting intSetting = (IntSetting)setting;
                  this.renderNumberSlider(canvas, rowY, intSetting.fraction(), Integer.toString((Integer)intSetting.get()), headerHeight);
               } else if (setting instanceof DoubleSetting) {
                  DoubleSetting doubleSetting = (DoubleSetting)setting;
                  this.renderNumberSlider(canvas, rowY, doubleSetting.fraction(), WindowClickGuiScreen.trimZeros(String.format(Locale.ROOT, "%.3f", doubleSetting.get())), headerHeight);
               } else if (setting instanceof ColorSetting) {
                  ColorSetting colorSetting = (ColorSetting)setting;
                  this.renderColor(canvas, colorSetting, rowY, headerHeight);
               } else if (setting instanceof KeybindSetting) {
                  KeybindSetting keybindSetting = (KeybindSetting)setting;
                  String value = WindowClickGuiScreen.this.capturingKeybind == keybindSetting ? "..." : KeyBindText.of((Integer)keybindSetting.get());
                  this.drawRight(canvas, value.isEmpty() ? WindowClickGuiScreen.tr("none", "NONE") : WindowClickGuiScreen.fit(value, 43.0F, false), rowY, 20.0F, WindowClickGuiScreen.accent(), 7.5F);
               } else if (setting instanceof StringSetting) {
                  StringSetting stringSetting = (StringSetting)setting;
                  boolean focused = WindowClickGuiScreen.this.textTarget == WindowClickGuiScreen.TextTarget.STRING && WindowClickGuiScreen.this.editingString == stringSetting;
                  String value = WindowClickGuiScreen.this.textTarget == WindowClickGuiScreen.TextTarget.STRING && WindowClickGuiScreen.this.editingString == stringSetting ? WindowClickGuiScreen.this.textWithCursor() : (String)stringSetting.get();
                  float fieldX = this.x + this.panelWidth - 60.0F;
                  float fieldY = rowY + 2.0F;
                  float fieldWidth = 54.0F;
                  float fieldHeight = 12.0F;
                  SkijaUi.rounded(canvas, fieldX, fieldY, fieldWidth, fieldHeight, 5.0F, focused ? WindowClickGuiScreen.accent() : 620756991);
                  SkijaUi.rounded(canvas, fieldX + 1.0F, fieldY + 1.0F, fieldWidth - 2.0F, fieldHeight - 2.0F, 4.0F, UiTheme.themedSurfaceAlt(229));
                  String display = value.isEmpty() ? WindowClickGuiScreen.tr("empty", "Empty") : value;
                  WindowClickGuiScreen.drawText(canvas, WindowClickGuiScreen.fit(display, fieldWidth - 8.0F, false), fieldX + 4.0F, fieldY, fieldHeight, value.isEmpty() ? WindowClickGuiScreen.TEXT_OFF : WindowClickGuiScreen.TEXT, 7.5F);
               }

            }
         }
      }

      private boolean rowVisible(float rowY, float height, float bodyY, float bodyHeight) {
         return rowY + height > bodyY && rowY < bodyY + bodyHeight;
      }

      private void renderNumberSlider(Canvas canvas, float rowY, float fraction, String value, float headerHeight) {
         float trackX = this.numberTrackX();
         float trackWidth = this.numberTrackWidth();
         float trackY = rowY + headerHeight + 2.0F;
         this.renderMovingValueSlider(canvas, trackX, trackY, trackWidth, fraction, value, WindowClickGuiScreen.accent(), 14.0F);
      }

      private void renderColor(Canvas canvas, ColorSetting setting, float rowY, float headerHeight) {
         Color color = setting.get();
         boolean expanded = ColorPickerState.expanded(setting);
         float fieldX = this.x + this.panelWidth - 72.0F;
         float fieldY = rowY + 2.0F;
         float fieldWidth = 52.0F;
         float fieldHeight = 12.0F;
         boolean focused = WindowClickGuiScreen.this.textTarget == WindowClickGuiScreen.TextTarget.COLOR && WindowClickGuiScreen.this.editingColor == setting;
         if (!expanded) {
            WindowClickGuiScreen.drawText(canvas, "Set Color", fieldX, fieldY, fieldHeight, WindowClickGuiScreen.accent(), 7.0F);
         } else if (ColorPickerState.mode(setting) == ColorPickerState.Mode.RGB) {
            String value = focused ? WindowClickGuiScreen.this.textWithCursor() : setting.hex();
            SkijaUi.rounded(canvas, fieldX, fieldY, fieldWidth, fieldHeight, 5.0F, focused ? WindowClickGuiScreen.accent() : 620756991);
            SkijaUi.rounded(canvas, fieldX + 1.0F, fieldY + 1.0F, fieldWidth - 2.0F, fieldHeight - 2.0F, 4.0F, UiTheme.themedSurfaceAlt(229));
            WindowClickGuiScreen.drawText(canvas, WindowClickGuiScreen.fit(value, fieldWidth - 6.0F, false), fieldX + 3.0F, fieldY, fieldHeight, focused ? WindowClickGuiScreen.TEXT : WindowClickGuiScreen.TEXT_DIM, 7.5F);
         }
         SkijaUi.rounded(canvas, this.x + this.panelWidth - 17.0F, rowY + 5.0F, 11.0F, 10.0F, 3.0F, setting.argb());
         if (!expanded) return;
         float modeY = rowY + headerHeight + 2.0F;
         float modeWidth = Math.min(116.0F, this.panelWidth - 12.0F);
         ColorPickerState.drawModeSelection(canvas, this.x + 6.0F, modeY, modeWidth, ColorPickerState.mode(setting), WindowClickGuiScreen.TEXT_DIM, WindowClickGuiScreen.accent());
         if (ColorPickerState.mode(setting) == ColorPickerState.Mode.PALETTE) {
            ColorPickerState.drawPalette(canvas, this.x + 6.0F, modeY + 16.0F, this.panelWidth - 12.0F, 36.0F, setting, WindowClickGuiScreen.TEXT);
            return;
         }
         int[] values = new int[]{color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
         int[] colors = new int[]{WindowClickGuiScreen.argb(255, 232, 84, 84), WindowClickGuiScreen.argb(255, 84, 218, 117), WindowClickGuiScreen.argb(255, 84, 151, 235), WindowClickGuiScreen.argb(255, 226, 228, 233)};
         String[] labels = new String[]{"R", "G", "B", "A"};
         int channels = setting.allowAlpha() ? 4 : 3;

         for(int i = 0; i < channels; ++i) {
            float channelY = modeY + 16.0F + (float)i * 10.0F;
            WindowClickGuiScreen.drawBoldText(canvas, labels[i], this.x + 6.0F, channelY, 10.0F, colors[i], 7.5F);
            float trackX = this.colorTrackX();
            float trackWidth = this.colorTrackWidth();
            String channelValue = Integer.toString(values[i]);
            this.renderMovingValueSlider(canvas, trackX, channelY + 5.0F, trackWidth, (float)values[i] / 255.0F, channelValue, colors[i], 10.0F);
         }

      }

      private void renderMovingValueSlider(Canvas canvas, float trackX, float trackY, float trackWidth, float fraction, String value, int color, float textHeight) {
         float clamped = WindowClickGuiScreen.clamp(fraction, 0.0F, 1.0F);
         float trackEnd = trackX + trackWidth;
         float handleX = trackX + trackWidth * clamped;
         float valueWidth = SkijaUi.textWidth(value, 7.5F);
         float valueX = handleX + 5.0F;
         if (valueX + valueWidth > trackEnd) {
            valueX = handleX - 5.0F - valueWidth;
         }

         valueX = WindowClickGuiScreen.clamp(valueX, trackX, Math.max(trackX, trackEnd - valueWidth));
         float gapStart = valueX - 2.5F;
         float gapEnd = valueX + valueWidth + 2.5F;
         WindowClickGuiScreen.drawSliderLayer(canvas, trackX, trackEnd, gapStart, gapEnd, trackY, 2.0F, WindowClickGuiScreen.trackSurface());
         WindowClickGuiScreen.drawSliderLayer(canvas, trackX, handleX, gapStart, gapEnd, trackY, 2.0F, color);
         SkijaUi.rounded(canvas, handleX - 2.0F, trackY - 3.0F, 4.0F, 8.0F, 2.0F, color);
         WindowClickGuiScreen.drawText(canvas, value, valueX, trackY - 7.0F, textHeight, WindowClickGuiScreen.TEXT, 7.5F);
      }

      private void drawRight(Canvas canvas, String value, float rowY, float height, int color, float size) {
         float width = SkijaUi.textWidth(value, size);
         WindowClickGuiScreen.drawText(canvas, value, this.x + this.panelWidth - 6.0F - width, rowY, height, color, size);
      }

      private void renderToggle(Canvas canvas, BooleanSetting setting, float toggleX, float toggleY) {
         float current = (Float)WindowClickGuiScreen.this.toggleAnimations.getOrDefault(setting, (Boolean)setting.get() ? 1.0F : 0.0F);
         current = WindowClickGuiScreen.animateTowards(current, (Boolean)setting.get() ? 1.0F : 0.0F, 18.0F, WindowClickGuiScreen.this.animationDelta);
         WindowClickGuiScreen.this.toggleAnimations.put(setting, current);
         SkijaUi.rounded(canvas, toggleX, toggleY, 20.0F, 10.0F, 5.0F, WindowClickGuiScreen.mixColor(WindowClickGuiScreen.trackSurface(), WindowClickGuiScreen.withAlpha(WindowClickGuiScreen.accent(), 190), current));
         SkijaUi.rounded(canvas, toggleX + 1.0F + current * 10.0F, toggleY + 1.0F, 8.0F, 8.0F, 4.0F, WindowClickGuiScreen.TEXT);
      }

      private float numberTrackX() {
         return this.x + 7.0F;
      }

      private float numberTrackWidth() {
         return Math.max(12.0F, this.panelWidth - 14.0F);
      }

      private float colorTrackX() {
         return this.x + 16.0F;
      }

      private float colorTrackWidth() {
         return Math.max(12.0F, this.panelWidth - 23.0F);
      }

      protected boolean clickContent(float mouseX, float contentMouseY, int button) {
         float rowY = this.y + 24.0F;

         for(Entry entry : this.entries) {
            if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x, rowY, this.panelWidth, 20.0F)) {
               if (button == 2) {
                  WindowClickGuiScreen.this.capturingModule = entry.module;
               } else if (button == 1) {
                  entry.expanded = !entry.expanded;
               } else if (button == 0) {
                  entry.module.interactFromClickGui();
               }

               this.clampScroll(WindowClickGuiScreen.this.logicalHeight());
               return true;
            }

            rowY += 20.0F;
            float visibleDetails = this.entryDetailsHeight(entry) * entry.expandProgress;
            if (!(visibleDetails <= 0.1F)) {
               if (entry.expandProgress < 0.995F) {
                  rowY += visibleDetails;
               } else {
                  if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x, rowY, this.panelWidth, 20.0F)) {
                     if (button == 0) {
                        WindowClickGuiScreen.this.capturingModule = entry.module;
                     } else if (button == 1) {
                        entry.module.setKeyBind(-1);
                        WindowClickGuiScreen.this.capturingModule = null;
                     }

                     return true;
                  }

                  rowY += 20.0F;
                  for(Setting<?> setting : this.entrySettings(entry)) {
                     if (setting.visible()) {
                        float height = WindowClickGuiScreen.settingHeight(setting);
                        if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x, rowY, this.panelWidth, height)) {
                           this.clickSetting(setting, rowY, mouseX, contentMouseY, button);
                           this.clampScroll(WindowClickGuiScreen.this.logicalHeight());
                           return true;
                        }

                        rowY += height;
                     }
                  }
                  if (entry.module == HUD.INSTANCE) {
                     for(HUDModuel component : HUD.INSTANCE.components()) {
                        if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x, rowY, this.panelWidth, 20.0F)) {
                           BooleanSetting toggle = HUD.INSTANCE.componentSetting(component);
                           boolean markerClicked = mouseX >= this.x + this.panelWidth - 38.0F && mouseX < this.x + this.panelWidth - 17.0F;
                           if (button == 1 || button == 0 && markerClicked) {
                              if (!this.expandedHudComponents.add(component)) {
                                 this.expandedHudComponents.remove(component);
                              }
                           } else if (button == 0) {
                              NotificationManager var10000 = NotificationManager.INSTANCE;
                              Objects.requireNonNull(toggle);
                              var10000.withoutModuleFeedback(toggle::toggle);
                           }

                           this.clampScroll(WindowClickGuiScreen.this.logicalHeight());
                           return true;
                        }

                        rowY += 20.0F;
                        float componentProgress = (Float)this.hudExpandProgress.getOrDefault(component, 0.0F);
                        float visibleSettings = this.visibleComponentSettingsHeight(component);
                        if (!(visibleSettings <= 0.1F)) {
                           if (componentProgress < 0.995F) {
                              rowY += visibleSettings;
                           } else {
                              for(Setting<?> setting : component.settings()) {
                                 if (!this.isHudPosition(component, setting) && setting.visible()) {
                                    float height = WindowClickGuiScreen.settingHeight(setting);
                                    if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x, rowY, this.panelWidth, height)) {
                                       this.clickSetting(setting, rowY, mouseX, contentMouseY, button);
                                       this.clampScroll(WindowClickGuiScreen.this.logicalHeight());
                                       return true;
                                    }

                                    rowY += height;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return false;
      }

      private void clickSetting(Setting<?> setting, float rowY, float mouseX, float mouseY, int button) {
         if (setting instanceof BooleanSetting booleanSetting) {
            if (button == 0 || button == 1) {
               NotificationManager var10000 = NotificationManager.INSTANCE;
               Objects.requireNonNull(booleanSetting);
               var10000.withoutModuleFeedback(booleanSetting::toggle);
            }
         } else if (setting instanceof FontSetting fontSetting) {
            if (button == 0 || button == 1) {
               fontSetting.cycle(button == 1 ? -1 : 1);
            }
         } else if (setting instanceof EnumSetting<?> enumSetting) {
            if (enumSetting.isSlider() && button == 0
                  && mouseY >= rowY + WindowClickGuiScreen.settingHeaderHeight(setting) - 4.0F) {
               WindowClickGuiScreen.this.beginNumberDrag(setting, this.numberTrackX(), this.numberTrackWidth(), mouseX);
            } else if (button == 1 || button == 0 && !enumSetting.isSlider()) {
               enumSetting.cycle(button == 1 ? -1 : 1);
            }
         } else if (setting instanceof IntSetting intSetting) {
            if (button == 1) {
               intSetting.set((Integer)intSetting.get() - intSetting.step());
            } else if (button == 0) {
               WindowClickGuiScreen.this.beginNumberDrag(setting, this.numberTrackX(), this.numberTrackWidth(), mouseX);
            }
         } else if (setting instanceof DoubleSetting doubleSetting) {
            if (button == 1) {
               doubleSetting.set((Double)doubleSetting.get() - doubleSetting.step());
            } else if (button == 0) {
               WindowClickGuiScreen.this.beginNumberDrag(setting, this.numberTrackX(), this.numberTrackWidth(), mouseX);
            }
         } else if (setting instanceof ColorSetting colorSetting) {
            float fieldX = this.x + this.panelWidth - 72.0F;
            if (button == 1) {
               ColorPickerState.toggle(colorSetting);
               WindowClickGuiScreen.this.finishTextEditing();
            } else if (button == 0 && ColorPickerState.expanded(colorSetting)
                  && ColorPickerState.clickMode(colorSetting, mouseX, mouseY, this.x + 6.0F,
                  rowY + WindowClickGuiScreen.settingHeaderHeight(setting) + 2.0F,
                  Math.min(116.0F, this.panelWidth - 12.0F))) {
               return;
            } else if (button == 0 && ColorPickerState.expanded(colorSetting)
                  && ColorPickerState.mode(colorSetting) == ColorPickerState.Mode.PALETTE
                  && ColorPickerState.clickPalette(colorSetting, mouseX, mouseY, this.x + 6.0F,
                  rowY + WindowClickGuiScreen.settingHeaderHeight(setting) + 18.0F,
                  this.panelWidth - 12.0F)) {
               return;
            } else if (button == 0 && ColorPickerState.expanded(colorSetting)
                  && WindowClickGuiScreen.inside((double)mouseX, (double)mouseY, fieldX, rowY + 2.0F, 52.0F, 12.0F)) {
               WindowClickGuiScreen.this.focusColor(colorSetting);
            } else if (button == 0 && !ColorPickerState.expanded(colorSetting)) {
               return;
            } else if (button == 0 && mouseY >= rowY + WindowClickGuiScreen.settingHeaderHeight(setting)) {
               int channel = (int)((mouseY - rowY - WindowClickGuiScreen.settingHeaderHeight(setting) - 18.0F) / 10.0F);
               int channels = colorSetting.allowAlpha() ? 4 : 3;
               if (channel >= 0 && channel < channels) {
                  WindowClickGuiScreen.this.draggingColor = colorSetting;
                  WindowClickGuiScreen.this.draggingColorChannel = channel;
                  WindowClickGuiScreen.this.draggingTrackX = this.colorTrackX();
                  WindowClickGuiScreen.this.draggingTrackWidth = this.colorTrackWidth();
                  WindowClickGuiScreen.this.updateColor(mouseX);
               }
            }
         } else if (setting instanceof KeybindSetting keybindSetting) {
            if (button == 1) {
               keybindSetting.set(-1);
            } else if (button == 0) {
               WindowClickGuiScreen.this.capturingKeybind = keybindSetting;
            }
         } else if (setting instanceof StringSetting stringSetting) {
            if (button == 1) {
               stringSetting.reset();
            } else if (button == 0) {
               WindowClickGuiScreen.this.focusString(stringSetting);
            }
         } else if (setting instanceof ButtonSetting buttonSetting) {
            if (button == 0) {
               buttonSetting.press();
            }
         }

      }

      private boolean isHudPosition(HUDModuel component, Setting<?> setting) {
         return setting == component.xPosition || setting == component.yPosition;
      }
   }

   private final class ConfigPanel extends Panel {
      private static final float TOP_HEIGHT = 106.0F;
      private String name;
      private boolean importVisualAndHud;
      private boolean importKeybinds;

      private ConfigPanel(float x, float y) {
         Objects.requireNonNull(WindowClickGuiScreen.this);
         super("Configs", WindowClickGuiScreen.tr("configs", "Configs"), "\ue247", x, y);
         this.name = "";
         this.importVisualAndHud = true;
         this.importKeybinds = true;
      }

      protected float contentHeight() {
         return TOP_HEIGHT + (float)WindowClickGuiScreen.configProfiles().size() * 20.0F;
      }

      protected void renderContent(Canvas canvas, float top, float bodyY, float bodyHeight) {
         WindowClickGuiScreen.drawText(canvas, WindowClickGuiScreen.tr("current_colon", "Current:"), this.x + 5.0F, top, 16.0F, WindowClickGuiScreen.TEXT_DIM, 9.0F);
         WindowClickGuiScreen.drawText(canvas, WindowClickGuiScreen.fit(WindowClickGuiScreen.currentConfigName(), 60.0F, false), this.x + 52.0F, top, 16.0F, WindowClickGuiScreen.accent(), 9.0F);
         float inputY = top + 16.0F;
         boolean inputFocused = WindowClickGuiScreen.this.textTarget == WindowClickGuiScreen.TextTarget.CONFIG;
         SkijaUi.rounded(canvas, this.x + 5.0F, inputY + 1.0F, this.panelWidth - 10.0F, 14.0F, 5.0F, inputFocused ? WindowClickGuiScreen.accent() : 620756991);
         SkijaUi.rounded(canvas, this.x + 6.0F, inputY + 2.0F, this.panelWidth - 12.0F, 12.0F, 4.0F, UiTheme.themedSurfaceAlt(229));
         String input = WindowClickGuiScreen.this.textTarget == WindowClickGuiScreen.TextTarget.CONFIG ? WindowClickGuiScreen.this.textWithCursor() : this.name;
         if (input.isEmpty()) {
            input = WindowClickGuiScreen.tr("enter_name", "Enter name...");
         }

         WindowClickGuiScreen.drawText(canvas, WindowClickGuiScreen.fit(input, this.panelWidth - 16.0F, false), this.x + 7.0F, inputY, 16.0F, this.name.isEmpty() ? WindowClickGuiScreen.TEXT_OFF : WindowClickGuiScreen.TEXT, 9.0F);
         float buttonY = top + 32.0F;
         float buttonWidth = (this.panelWidth - 17.0F) * 0.5F;
         int saveColor = WindowClickGuiScreen.inside((double)WindowClickGuiScreen.this.renderMouseX, (double)WindowClickGuiScreen.this.renderMouseY, this.x + 5.0F, buttonY + 1.0F, buttonWidth, 14.0F) ? WindowClickGuiScreen.withAlpha(WindowClickGuiScreen.accent(), 220) : WindowClickGuiScreen.withAlpha(WindowClickGuiScreen.accent(), 165);
         int loadColor = WindowClickGuiScreen.inside((double)WindowClickGuiScreen.this.renderMouseX, (double)WindowClickGuiScreen.this.renderMouseY, this.x + 12.0F + buttonWidth, buttonY + 1.0F, buttonWidth, 14.0F) ? WindowClickGuiScreen.settingHover() : WindowClickGuiScreen.settingSurface();
         SkijaUi.rounded(canvas, this.x + 5.0F, buttonY + 1.0F, buttonWidth, 14.0F, 5.0F, saveColor);
         SkijaUi.rounded(canvas, this.x + 12.0F + buttonWidth, buttonY + 1.0F, buttonWidth, 14.0F, 5.0F, loadColor);
         WindowClickGuiScreen.drawCentered(canvas, WindowClickGuiScreen.tr("save", "Save"), this.x + 5.0F, buttonY, buttonWidth, 16.0F, WindowClickGuiScreen.TEXT);
         WindowClickGuiScreen.drawCentered(canvas, WindowClickGuiScreen.tr("load", "Load"), this.x + 12.0F + buttonWidth, buttonY, buttonWidth, 16.0F, WindowClickGuiScreen.TEXT);
         float optionY = top + 50.0F;
         this.drawImportOption(canvas, optionY, "Visual & HUD", this.importVisualAndHud);
         this.drawImportOption(canvas, optionY + 18.0F, "Keybinds", this.importKeybinds);
         float folderY = top + 86.0F;
         boolean folderHovered = WindowClickGuiScreen.inside((double)WindowClickGuiScreen.this.renderMouseX, (double)WindowClickGuiScreen.this.renderMouseY, this.x + 5.0F, folderY + 1.0F, this.panelWidth - 10.0F, 16.0F);
         SkijaUi.rounded(canvas, this.x + 5.0F, folderY + 1.0F, this.panelWidth - 10.0F, 16.0F, 5.0F, folderHovered ? WindowClickGuiScreen.settingHover() : WindowClickGuiScreen.settingSurface());
         WindowClickGuiScreen.drawCentered(canvas, WindowClickGuiScreen.tr("open_folder", "Open Folder"), this.x + 5.0F, folderY, this.panelWidth - 10.0F, 18.0F, folderHovered ? WindowClickGuiScreen.TEXT : WindowClickGuiScreen.TEXT_DIM);
         float rowY = top + TOP_HEIGHT;

         for(String profile : WindowClickGuiScreen.configProfiles()) {
            this.drawSettingSection(canvas, rowY, 20.0F);
            ClickGuiText.drawVerticallyCentered(canvas, profile, this.x + 7.0F, rowY, this.panelWidth - 24.0F, 20.0F, 9.0F, WindowClickGuiScreen.TEXT, 9.0F, false);
            float deleteX = this.x + this.panelWidth - 21.0F;
            SkijaUi.rounded(canvas, deleteX, rowY + 3.0F, 15.0F, 14.0F, 5.0F, WindowClickGuiScreen.DELETE_BACKGROUND);
            WindowClickGuiScreen.drawCentered(canvas, "x", deleteX, rowY, 15.0F, 20.0F, WindowClickGuiScreen.TEXT);
            rowY += 20.0F;
         }

      }

      private void drawImportOption(Canvas canvas, float rowY, String label, boolean enabled) {
         boolean hovered = WindowClickGuiScreen.inside((double)WindowClickGuiScreen.this.renderMouseX, (double)WindowClickGuiScreen.this.renderMouseY, this.x + 5.0F, rowY, this.panelWidth - 10.0F, 16.0F);
         SkijaUi.rounded(canvas, this.x + 5.0F, rowY + 1.0F, this.panelWidth - 10.0F, 14.0F, 5.0F, hovered ? WindowClickGuiScreen.settingHover() : WindowClickGuiScreen.settingSurface());
         WindowClickGuiScreen.drawText(canvas, label, this.x + 8.0F, rowY, 16.0F, hovered ? WindowClickGuiScreen.TEXT : WindowClickGuiScreen.TEXT_DIM, 7.5F);
         float toggleX = this.x + this.panelWidth - 25.0F;
         SkijaUi.rounded(canvas, toggleX, rowY + 4.0F, 15.0F, 8.0F, 4.0F, enabled ? WindowClickGuiScreen.withAlpha(WindowClickGuiScreen.accent(), 220) : WindowClickGuiScreen.trackSurface());
         float knobX = toggleX + (enabled ? 8.0F : 1.0F);
         SkijaUi.rounded(canvas, knobX, rowY + 5.0F, 6.0F, 6.0F, 3.0F, WindowClickGuiScreen.TEXT);
      }

      protected boolean clickContent(float mouseX, float contentMouseY, int button) {
         float top = this.y + 24.0F;
         if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x + 5.0F, top + 16.0F, this.panelWidth - 10.0F, 16.0F) && button == 0) {
            WindowClickGuiScreen.this.focusConfigName(this.name);
            return true;
         } else {
            float buttonWidth = (this.panelWidth - 17.0F) * 0.5F;
            if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x + 5.0F, top + 32.0F, buttonWidth, 16.0F) && button == 0) {
               WindowClickGuiScreen.configProvider.save(this.configTargetName());
               return true;
            } else if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x + 12.0F + buttonWidth, top + 32.0F, buttonWidth, 16.0F) && button == 0) {
               WindowClickGuiScreen.configProvider.load(this.configTargetName(), this.importVisualAndHud, this.importKeybinds);
               return true;
            } else if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x + 5.0F, top + 50.0F, this.panelWidth - 10.0F, 16.0F) && button == 0) {
               this.importVisualAndHud = !this.importVisualAndHud;
               return true;
            } else if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x + 5.0F, top + 68.0F, this.panelWidth - 10.0F, 16.0F) && button == 0) {
               this.importKeybinds = !this.importKeybinds;
               return true;
            } else if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x + 5.0F, top + 86.0F, this.panelWidth - 10.0F, 18.0F) && button == 0) {
               WindowClickGuiScreen.this.openConfigFolder();
               return true;

            } else {
               float rowY = top + TOP_HEIGHT;

               for(String profile : WindowClickGuiScreen.configProfiles()) {
                  if (WindowClickGuiScreen.inside((double)mouseX, (double)contentMouseY, this.x, rowY, this.panelWidth, 20.0F)) {
                     if (button != 1 && !(mouseX >= this.x + this.panelWidth - 22.0F)) {
                        if (button == 0) {
                           this.name = profile;
                        }
                     } else {
                        WindowClickGuiScreen.configProvider.delete(profile);
                     }

                     return true;
                  }

                  rowY += 20.0F;
               }

               return false;
            }
         }
      }

      private String configTargetName() {
         return this.name.isBlank() ? WindowClickGuiScreen.currentConfigName() : this.name.trim();
      }
   }

   private static final class Entry {
      private final Module module;
      private boolean expanded;
      private float expandProgress;
      private float detailsHeight;
      private float enabledProgress;
      private float hoverProgress;

      private Entry(Module module, boolean expanded) {
         this.module = module;
         this.expanded = expanded;
         this.enabledProgress = module.isEnabled() ? 1.0F : 0.0F;
      }
   }

   private static final class PanelMemory {
      private float x;
      private float y;
      private float scroll;
      private boolean collapsed;
      private Set<String> expandedModules = new HashSet();
      private Set<String> expandedHudComponents = new HashSet();
      private String configName = "";

      private void normalize() {
         if (!Float.isFinite(this.x)) {
            this.x = 12.0F;
         }

         if (!Float.isFinite(this.y)) {
            this.y = 12.0F;
         }

         if (!Float.isFinite(this.scroll) || this.scroll < 0.0F) {
            this.scroll = 0.0F;
         }

         if (this.expandedModules == null) {
            this.expandedModules = new HashSet();
         }

         if (this.expandedHudComponents == null) {
            this.expandedHudComponents = new HashSet();
         }

         if (this.configName == null) {
            this.configName = "";
         }

      }
   }

   private static final class DropMemoryFile {
      private Map<String, PanelMemory> panels = new HashMap();
      private List<String> order = new ArrayList();
      private boolean gridEnabled;
   }

   private static enum TextTarget {
      NONE,
      STRING,
      CONFIG,
      COLOR,
      SEARCH;
   }

   private record Bounds(float x, float y, float width, float height) {
      boolean contains(float mouseX, float mouseY) {
         return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
      }
   }
}
