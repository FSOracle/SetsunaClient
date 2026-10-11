package com.setsuna.ui.clickgui;

import com.setsuna.Setsuna;
import com.setsuna.config.ConfigManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.module.modules.render.ArmorTrimOverride;
import com.setsuna.module.modules.render.EquipmentDisguise;
import com.setsuna.module.modules.render.ChestESP;
import com.setsuna.module.modules.render.ESP;
import com.setsuna.module.modules.render.KillEffect;
import com.setsuna.notification.NotificationManager;
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
import com.setsuna.ui.hud.TargetHud;
import com.setsuna.ui.hud.WatermarkHUD;
import com.setsuna.util.KeyBindText;
import com.setsuna.util.client.KeybindUtils;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import java.awt.Desktop;
import java.nio.file.Path;

public final class PopClickGuiScreen extends Screen implements SkijaScreen {
   private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("setsuna", "textures/hud/watermark_logo.png");
   private static final Category[] CATEGORIES;
   private static final float BUBBLE_RADIUS = 27.0F;
   private static final float HEADER_HEIGHT = 44.0F;
   private static final float MODULE_ROW_HEIGHT = 25.0F;
   private static final float SETTING_ROW_HEIGHT = 31.0F;
   private static final float NUMBER_ROW_HEIGHT = 42.0F;
   private static final float COLOR_CHANNEL_HEIGHT = 18.0F;
   private static final float PANEL_GAP = 10.0F;
   private static final float SCROLL_STEP = 24.0F;
   private static final int TEXT = -788488;
   private static final int TEXT_DIM = -6049868;
   private static final int TEXT_FAINT = -9931140;
   private static final int LIGHT_PANEL;
   private static final int LIGHT_PANEL_INNER;
   private static final int LIGHT_ROW_HOVER;
   private static final int LIGHT_TEXT = -14866386;
   private static final int LIGHT_TEXT_DIM = -10917777;
   private static final int LIGHT_TEXT_FAINT = -7168606;
   private static final int LIGHT_TRACK = -3550248;
   private static final Paint RING_PAINT;
   private static final Paint SKIN_PAINT;
   private static final Paint LOGO_PAINT;
   private static final Paint PREVIEW_OUTLINE_PAINT;
   private final Screen parent;
   private final Map<Category, Float> bubbleHover;
   private final Map<Module, Float> moduleEnabled;
   private final Map<Setting<?>, Float> toggleProgress;
   private Category selectedCategory;
   private Module selectedModule;
   private boolean configOpen;
   private float configScroll;
   private String configName;
   private String configStatus;
   private boolean configStatusError;
   private boolean importVisualAndHud;
   private boolean importKeybinds;
   private boolean categoryRequested;
   private boolean settingsRequested;
   private float introProgress;
   private float categoryProgress;
   private float settingsProgress;
   private float colorEditorProgress;
   private float daylightProgress;
   private float moduleScroll;
   private float settingScroll;
   private Setting<?> draggingNumber;
   private ColorSetting draggingColor;
   private int draggingColorChannel;
   private float draggingTrackX;
   private float draggingTrackWidth;
   private final List<EspPreviewHit> espPreviewHits;
   private EspPreviewPart draggingEspPreview;
   private float espDragStartX;
   private float espDragStartY;
   private double espOffsetStartX;
   private double espOffsetStartY;
   private float espViewportX;
   private float espViewportY;
   private float espViewportWidth;
   private float espViewportHeight;
   private float espInfoPreviewScale;
   private Module capturingModule;
   private KeybindSetting capturingKeybind;
   private StringSetting editingString;
   private boolean editingConfigName;
   private ColorSetting editingColor;
   private ColorSetting colorEditorSetting;
   private Color colorBeforeEdit;
   private String editText;
   private int editCursor;
   private boolean selectAll;
   private float activeScale;
   private float pointerX;
   private float pointerY;
   private float delta;
   private long lastFrame;
   private Integer originalMenuBlur;
   private boolean closing;
   private boolean closeFinished;

   public PopClickGuiScreen() {
      this((Screen)null);
   }

   public PopClickGuiScreen(Screen parent) {
      super(Component.literal("Setsuna ClickGUI / Pop"));
      this.bubbleHover = new EnumMap(Category.class);
      this.moduleEnabled = new IdentityHashMap();
      this.toggleProgress = new IdentityHashMap();
      this.draggingColorChannel = -1;
      this.espPreviewHits = new ArrayList();
      this.espInfoPreviewScale = 1.0F;
      this.editText = "";
      this.configName = "";
      this.configStatus = "";
      this.importVisualAndHud = true;
      this.importKeybinds = true;
      this.activeScale = 1.0F;
      this.lastFrame = System.nanoTime();
      this.parent = parent;

      for(Category category : CATEGORIES) {
         this.bubbleHover.put(category, 0.0F);
      }

   }

   protected void init() {
      this.activeScale = this.configuredScale();
      this.introProgress = 0.0F;
      this.categoryProgress = 0.0F;
      this.settingsProgress = 0.0F;
      this.colorEditorProgress = 0.0F;
      this.colorEditorSetting = null;
      this.daylightProgress = (Boolean)ClickGui.INSTANCE.daylightMode.get() ? 1.0F : 0.0F;
      this.categoryRequested = false;
      this.settingsRequested = false;
      this.selectedCategory = null;
      this.selectedModule = null;
      this.configOpen = false;
      this.configScroll = 0.0F;
      this.configName = ConfigManager.INSTANCE.currentProfile();
      this.configStatus = "";
      this.configStatusError = false;
      this.moduleScroll = 0.0F;
      this.settingScroll = 0.0F;
      this.draggingEspPreview = null;
      this.espPreviewHits.clear();
      this.closing = false;
      this.closeFinished = false;
      this.lastFrame = System.nanoTime();
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      int blur = (Integer)ClickGui.INSTANCE.popBackgroundBlur.get();
      if (blur > 0) {
         this.applyMenuBlur(blur);
         graphics.blurBeforeThisStratum();
      } else {
         this.restoreMenuBlur();
      }

      graphics.fill(0, 0, this.width, this.height, UiTheme.themedBackdrop(38));
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.pointerX = this.logical((double)mouseX);
      this.pointerY = this.logical((double)mouseY);
      if (SkijaRenderer.hasFailed()) {
         graphics.fill(0, 0, this.width, this.height, UiTheme.themedBackdrop(217));
         graphics.text(this.font, "Skija renderer failed - check latest.log", 8, 8, this.themeText(), false);
      }

   }

   public void renderSkija(Canvas canvas) {
      this.updateAnimations();
      if (!this.completeCloseWhenReady()) {
         float logicalWidth = this.logicalWidth();
         float logicalHeight = this.logicalHeight();
         canvas.save();
         canvas.scale(this.activeScale, this.activeScale);
         SkijaUi.fill(canvas, 0.0F, 0.0F, logicalWidth + 1.0F, logicalHeight + 1.0F, multiplyAlpha(UiTheme.themedBackdrop(70), smooth(this.introProgress)));
         canvas.save();
         if (this.closing) {
            float closeScale = lerp(0.16F, 1.0F, smooth(this.introProgress));
            canvas.translate(logicalWidth * 0.5F, logicalHeight * 0.5F);
            canvas.scale(closeScale, closeScale);
            canvas.translate(-logicalWidth * 0.5F, -logicalHeight * 0.5F);
         }

         this.renderRing(canvas, logicalWidth, logicalHeight);
         if (this.selectedCategory != null) {
            this.renderModulePanel(canvas, this.layout(logicalWidth, logicalHeight));
            if (this.settingsProgress > 0.01F && (this.selectedModule != null || this.configOpen)) {
               PopLayout layout = this.layout(logicalWidth, logicalHeight);
               this.renderSettingsPanel(canvas, layout);
               if (!this.configOpen) {
                  this.renderEspPreview(canvas, layout);
                  this.renderColorEditorPanel(canvas, layout);
               }
            }
         }

         canvas.restore();
         if (watermarkLogoTransferActive() && (this.closing || this.selectedCategory == null)) {
            this.drawPopLogo(canvas, logicalWidth * 0.5F, logicalHeight * 0.5F, smooth(this.introProgress));
         }

         canvas.restore();
      }
   }

   private void updateAnimations() {
      long now = System.nanoTime();
      this.delta = Math.min(0.05F, Math.max(0.0F, (float)(now - this.lastFrame) / 1.0E9F));
      this.lastFrame = now;
      this.introProgress = this.animate(this.introProgress, this.closing ? 0.0F : 1.0F, this.closing ? 13.5F : 7.5F);
      this.categoryProgress = this.animate(this.categoryProgress, this.categoryRequested ? 1.0F : 0.0F, this.closing ? 16.5F : 9.5F);
      this.settingsProgress = this.animate(this.settingsProgress, this.settingsRequested ? 1.0F : 0.0F, this.closing ? 18.0F : 10.5F);
      ColorSetting expandedColor = this.expandedColorSetting();
      if (expandedColor != null) {
         this.colorEditorSetting = expandedColor;
         this.colorEditorProgress = this.animate(this.colorEditorProgress, 1.0F, 14.0F);
      } else {
         // Do not leave RGB rows or the palette hanging around after the editor closes.
         this.colorEditorProgress = 0.0F;
         this.colorEditorSetting = null;
      }
      this.daylightProgress = this.animate(this.daylightProgress, (Boolean)ClickGui.INSTANCE.daylightMode.get() ? 1.0F : 0.0F, 7.0F);
      if (!this.categoryRequested && this.categoryProgress < 0.01F && this.selectedCategory != null) {
         this.selectedCategory = null;
         this.selectedModule = null;
      }

      if (!this.settingsRequested && this.settingsProgress < 0.01F) {
         this.selectedModule = null;
         this.configOpen = false;
         this.settingScroll = 0.0F;
         this.configScroll = 0.0F;
      }

      for(Category category : CATEGORIES) {
         Bubble bubble = this.bubble(category, this.logicalWidth(), this.logicalHeight());
         boolean hovered = this.selectedCategory == null && distance(this.pointerX, this.pointerY, bubble.x(), bubble.y()) <= 31.0F;
         this.bubbleHover.put(category, this.animate((Float)this.bubbleHover.getOrDefault(category, 0.0F), hovered ? 1.0F : 0.0F, 12.0F));
      }

      for(Module module : ModuleManager.INSTANCE.modules()) {
         this.moduleEnabled.put(module, this.animate((Float)this.moduleEnabled.getOrDefault(module, module.isEnabled() ? 1.0F : 0.0F), module.isEnabled() ? 1.0F : 0.0F, 13.0F));

         for(Setting<?> setting : module.settings()) {
            if (setting instanceof BooleanSetting booleanSetting) {
               this.toggleProgress.put(setting, this.animate((Float)this.toggleProgress.getOrDefault(setting, (Boolean)booleanSetting.get() ? 1.0F : 0.0F), (Boolean)booleanSetting.get() ? 1.0F : 0.0F, 14.0F));
            }
         }
      }

      this.clampScrolls(this.layout(this.logicalWidth(), this.logicalHeight()));
   }

   private void renderRing(Canvas canvas, float screenWidth, float screenHeight) {
      float centerX = screenWidth * 0.5F;
      float centerY = screenHeight * 0.5F;
      float ringRadius = ringRadius(screenWidth, screenHeight);
      float intro = smooth(this.introProgress);
      float collapse = smooth(this.categoryProgress);
      RING_PAINT.setStrokeWidth(1.0F).setColor(withAlpha(accent(), Math.round(70.0F * intro * (1.0F - collapse))));
      canvas.drawCircle(centerX, centerY, ringRadius * intro, RING_PAINT);

      for(Category category : CATEGORIES) {
         Bubble bubble = this.bubble(category, screenWidth, screenHeight);
         boolean selected = category == this.selectedCategory;
         float disappear = selected ? 1.0F : 1.0F - collapse;
         if (!(disappear <= 0.01F)) {
            float hover = (Float)this.bubbleHover.getOrDefault(category, 0.0F);
            float radius = 27.0F + hover * 4.0F;
            float x = lerp(centerX, bubble.x(), intro);
            float y = lerp(centerY, bubble.y(), intro);
            if (selected) {
               PopLayout layout = this.layout(screenWidth, screenHeight);
               x = lerp(x, layout.listX() + layout.panelWidth() * 0.5F, collapse);
               y = lerp(y, layout.listY() + 22.0F, collapse);
               radius = lerp(radius, 15.0F, collapse);
            }

            int color = categoryColor(category);
            SkijaUi.rounded(canvas, x - radius - 1.0F, y - radius - 1.0F, radius * 2.0F + 2.0F, radius * 2.0F + 2.0F, radius + 1.0F, withAlpha(color, Math.round(120.0F * disappear)));
            SkijaUi.rounded(canvas, x - radius, y - radius, radius * 2.0F, radius * 2.0F, radius, withAlpha(this.theme(UiTheme.themedSurface(255), -525830), Math.round(238.0F * disappear)));
            float iconAlpha = disappear * (selected ? 1.0F - smooth(collapse) : 1.0F);
            drawCenteredIcon(canvas, categoryIcon(category), x, y - 6.0F, withAlpha(color, Math.round(255.0F * iconAlpha)), 14.0F);
            if (!selected || collapse < 0.35F) {
               drawCenteredText(canvas, categoryTitle(category), x, y + 8.0F, withAlpha(this.themeText(), Math.round(220.0F * iconAlpha)), 6.5F, true);
            }
         }
      }

      if (this.selectedCategory == null && !watermarkLogoTransferActive()) {
         this.drawPopLogo(canvas, centerX, centerY, intro);
      }

   }

   private static boolean watermarkLogoTransferActive() {
      return WatermarkHUD.INSTANCE.isEnabled() && (Boolean)WatermarkHUD.INSTANCE.logo.get();
   }

   private void drawPopLogo(Canvas canvas, float centerX, float centerY, float alpha) {
      boolean transfer = watermarkLogoTransferActive();
      if (transfer || !(alpha <= 0.001F)) {
         try {
            SkijaRenderer.BorrowedImage borrowed;
            label159: {
               borrowed = SkijaRenderer.borrowTexture(LOGO_TEXTURE);

               try {
                  if (borrowed == null) {
                     break label159;
                  }

                  Image image = borrowed.image();
                  float progress = smooth(alpha);
                  float logoX = centerX;
                  float logoY = centerY;
                  float size = 38.0F * lerp(0.78F, 1.0F, progress);
                  int logoAlpha = Math.round(255.0F * alpha);
                  if (transfer) {
                     float watermarkScale = ((Double)WatermarkHUD.INSTANCE.scale.get()).floatValue();
                     float sourceSize = 11.0F * watermarkScale * 1.28F / this.activeScale;
                     float sourceX = (WatermarkHUD.INSTANCE.hudX((float)this.width) + 5.0F * watermarkScale + sourceSize * this.activeScale * 0.5F) / this.activeScale;
                     float sourceY = (WatermarkHUD.INSTANCE.hudY((float)this.height) + 2.0F * watermarkScale + sourceSize * this.activeScale * 0.5F) / this.activeScale;
                     logoX = lerp(sourceX, centerX, progress);
                     logoY = lerp(sourceY, centerY, progress);
                     size = lerp(sourceSize, 38.0F, progress);
                     logoAlpha = 255;
                  }

                  Rect source = Rect.makeXYWH(0.0F, 0.0F, (float)image.getWidth(), (float)image.getHeight());
                  Rect destination = Rect.makeXYWH(logoX - size * 0.5F, logoY - size * 0.5F, size, size);
                  LOGO_PAINT.setColor(-1).setAlpha(logoAlpha);
                  float glowProgress = transfer ? progress : alpha;
                  if (glowProgress > 0.01F) {
                     SkijaUi.glowLayer(canvas, destination.getLeft(), destination.getTop(), destination.getWidth(), destination.getHeight(), 8.0F * glowProgress, Math.max(1, Math.round(6.0F * glowProgress)), () -> canvas.drawImageRect(image, source, destination, SamplingMode.MITCHELL, LOGO_PAINT, true));
                  }

                  canvas.drawImageRect(image, source, destination, SamplingMode.MITCHELL, LOGO_PAINT, true);
               } catch (Throwable var23) {
                  if (borrowed != null) {
                     try {
                        borrowed.close();
                     } catch (Throwable var22) {
                        var23.addSuppressed(var22);
                     }
                  }

                  throw var23;
               }

               if (borrowed != null) {
                  borrowed.close();
               }

               return;
            }

            if (borrowed != null) {
               borrowed.close();
            }
         } catch (Throwable var24) {
            return;
         } finally {
            LOGO_PAINT.setAlpha(255);
         }

      }
   }

   private void renderModulePanel(Canvas canvas, PopLayout layout) {
      float progress = smooth(this.categoryProgress);
      Bubble origin = this.bubble(this.selectedCategory, layout.screenWidth(), layout.screenHeight());
      float startX = origin.x() - 27.0F;
      float startY = origin.y() - 27.0F;
      float startSize = 54.0F;
      float panelX = lerp(startX, layout.listX(), progress);
      float panelY = lerp(startY, layout.listY(), progress);
      float panelWidth = lerp(startSize, layout.panelWidth(), progress);
      float panelHeight = lerp(startSize, layout.listHeight(), progress);
      float radius = lerp(27.0F, 9.0F, progress);
      int categoryColor = categoryColor(this.selectedCategory);
      SkijaUi.rounded(canvas, panelX - 1.0F, panelY - 1.0F, panelWidth + 2.0F, panelHeight + 2.0F, radius + 1.0F, withAlpha(categoryColor, Math.round(150.0F * progress)));
      SkijaUi.rounded(canvas, panelX, panelY, panelWidth, panelHeight, radius, withAlpha(this.themePanel(), Math.round(255.0F * progress)));
      if (!(progress < 0.36F)) {
         float contentAlpha = smooth((progress - 0.36F) / 0.64F);
         this.renderPanelHeader(canvas, panelX, panelY, panelWidth, this.selectedCategory, categoryTitle(this.selectedCategory), contentAlpha, true);
         float bodyY = panelY + 44.0F;
         float bodyHeight = panelHeight - 44.0F;
         canvas.save();
         canvas.clipRect(Rect.makeXYWH(panelX, bodyY, panelWidth, bodyHeight));
         float rowY = bodyY - this.moduleScroll;
         List<Module> modules = ModuleManager.INSTANCE.modulesIn(this.selectedCategory);

         for(Module module : modules) {
            this.renderModuleRow(canvas, module, panelX, rowY, panelWidth, contentAlpha);
            rowY += 25.0F;
         }

         if (this.selectedCategory == Category.CLIENT) {
            this.renderConfigRow(canvas, panelX, rowY, panelWidth, contentAlpha);
         }

         canvas.restore();
         float contentHeight = (float)modules.size() * 25.0F + (this.selectedCategory == Category.CLIENT ? 25.0F : 0.0F);
         this.renderScrollbar(canvas, panelX, bodyY, panelWidth, bodyHeight, contentHeight, this.moduleScroll, categoryColor);
      }
   }

   private void renderPanelHeader(Canvas canvas, float x, float y, float width, Category category, String title, float alpha, boolean categoryButton) {
      int color = categoryColor(category);
      SkijaUi.rounded(canvas, x + 8.0F, y + 8.0F, 28.0F, 28.0F, 14.0F, withAlpha(color, Math.round(205.0F * alpha)));
      if (categoryButton) {
         drawCenteredIcon(canvas, categoryIcon(category), x + 22.0F, y + 22.0F, withAlpha(this.themeText(), Math.round(255.0F * alpha)), 11.0F);
      } else {
         this.renderModuleKeybindBadge(canvas, x + 22.0F, y + 22.0F, alpha);
      }

      ClickGuiText.drawVerticallyCentered(canvas, title, x + 44.0F, y, width - 58.0F, 44.0F, 10.0F, withAlpha(this.themeText(), Math.round(255.0F * alpha)), 8.6F, true);
      SkijaUi.fill(canvas, x + 12.0F, y + 44.0F - 1.0F, Math.max(0.0F, width - 24.0F), 1.0F, withAlpha(color, Math.round(105.0F * alpha)));
   }

   private void renderModuleRow(Canvas canvas, Module module, float x, float y, float width, float alpha) {
      boolean hovered = inside(this.pointerX, this.pointerY, x + 7.0F, y + 2.0F, width - 14.0F, 21.0F);
      float enabled = (Float)this.moduleEnabled.getOrDefault(module, module.isEnabled() ? 1.0F : 0.0F);
      if (hovered || enabled > 0.01F) {
         SkijaUi.rounded(canvas, x + 7.0F, y + 2.0F, width - 14.0F, 21.0F, 5.0F, withAlpha(hovered ? this.theme(UiTheme.themedHover(96), LIGHT_ROW_HOVER) : categoryColor(this.selectedCategory), Math.round((hovered ? 180.0F : 28.0F + enabled * 36.0F) * alpha)));
      }

      ClickGuiText.drawVerticallyCentered(canvas, module.displayName(), x + 14.0F, y, width - 54.0F, 25.0F, 9.0F, withAlpha(mix(this.themeTextDim(), this.themeText(), enabled), Math.round(255.0F * alpha)), 7.6F, false);
      float dotRadius = 2.5F + enabled;
      SkijaUi.rounded(canvas, x + width - 18.0F - dotRadius, y + 12.5F - dotRadius, dotRadius * 2.0F, dotRadius * 2.0F, dotRadius, withAlpha(enabled > 0.01F ? categoryColor(this.selectedCategory) : this.themeTextFaint(), Math.round(255.0F * alpha)));
   }

   private void renderConfigRow(Canvas canvas, float x, float y, float width, float alpha) {
      boolean hovered = inside(this.pointerX, this.pointerY, x + 7.0F, y + 2.0F, width - 14.0F, 21.0F);
      if (hovered) {
         SkijaUi.rounded(canvas, x + 7.0F, y + 2.0F, width - 14.0F, 21.0F, 5.0F,
               withAlpha(this.theme(UiTheme.themedHover(96), LIGHT_ROW_HOVER), Math.round(180.0F * alpha)));
      }
      ClickGuiText.drawVerticallyCentered(canvas, "Config", x + 14.0F, y, width - 54.0F, 25.0F,
            9.0F, withAlpha(this.themeTextDim(), Math.round(255.0F * alpha)), 7.6F, false);
      SkijaUi.text(canvas, ">", x + width - 20.0F, y + 6.0F, 14.0F,
            withAlpha(this.themeTextFaint(), Math.round(255.0F * alpha)), 8.0F);
   }

   private void renderSettingsPanel(Canvas canvas, PopLayout layout) {
      float progress = smooth(this.settingsProgress);
      float x = this.currentSettingsX(layout);
      float y = this.currentSettingsY(layout);
      float height = this.currentSettingsHeight(layout);
      int color = categoryColor(this.selectedCategory);
      SkijaUi.rounded(canvas, x - 1.0F, y - 1.0F, layout.panelWidth() + 2.0F, height + 2.0F, 10.0F, withAlpha(color, Math.round(145.0F * progress)));
      SkijaUi.rounded(canvas, x, y, layout.panelWidth(), height, 9.0F, withAlpha(this.themePanel(), Math.round(255.0F * progress)));
      if (!(progress < 0.18F)) {
         float alpha = smooth((progress - 0.18F) / 0.82F);
         this.renderPanelHeader(canvas, x, y, layout.panelWidth(), this.selectedCategory,
               this.configOpen ? "Config" : this.selectedModule.displayName(), alpha, this.configOpen);
         float bodyY = y + 44.0F;
         float bodyHeight = height - 44.0F;
         canvas.save();
         canvas.clipRect(Rect.makeXYWH(x, bodyY, layout.panelWidth(), bodyHeight));
         float rowY = bodyY - this.settingScroll;

         if (this.configOpen) {
            this.renderConfigContent(canvas, x, bodyY, layout.panelWidth(), bodyHeight, alpha);
         } else {
            for(Setting<?> setting : this.visibleSettings()) {
               this.renderSetting(canvas, setting, x, rowY, layout.panelWidth(), alpha);
               rowY += settingHeight(setting);
            }
         }

         canvas.restore();
         this.renderScrollbar(canvas, x, bodyY, layout.panelWidth(), bodyHeight,
               this.configOpen ? this.configContentHeight() : this.settingsContentHeight(),
               this.configOpen ? this.configScroll : this.settingScroll, color);
      }
   }

   private void renderConfigContent(Canvas canvas, float x, float bodyY, float width, float height, float alpha) {
      float pad = 13.0F;
      float innerWidth = Math.max(1.0F, width - pad * 2.0F);
      float fieldY = bodyY + 6.0F;
      this.renderConfigTextField(canvas, x + pad, fieldY, innerWidth, 21.0F, alpha);

      float buttonY = fieldY + 27.0F;
      float gap = 4.0F;
      float buttonWidth = Math.max(1.0F, (innerWidth - gap * 2.0F) / 3.0F);
      this.renderConfigButton(canvas, x + pad, buttonY, buttonWidth, 21.0F, "Save", alpha);
      this.renderConfigButton(canvas, x + pad + buttonWidth + gap, buttonY, buttonWidth, 21.0F, "Load", alpha);
      this.renderConfigButton(canvas, x + pad + (buttonWidth + gap) * 2.0F, buttonY, buttonWidth, 21.0F, "Delete", alpha);

      float utilityY = buttonY + 26.0F;
      float utilityWidth = Math.max(1.0F, (innerWidth - gap) / 2.0F);
      this.renderConfigButton(canvas, x + pad, utilityY, utilityWidth, 21.0F, "Open Folder", alpha);
      this.renderConfigButton(canvas, x + pad + utilityWidth + gap, utilityY, utilityWidth, 21.0F, "Refresh", alpha);

      float optionY = utilityY + 28.0F;
      this.renderConfigOption(canvas, x + pad, optionY, innerWidth, "Visual & HUD", this.importVisualAndHud, alpha);
      this.renderConfigOption(canvas, x + pad, optionY + 21.0F, innerWidth, "Keybinds", this.importKeybinds, alpha);

      float listY = optionY + 48.0F - this.configScroll;
      List<String> profiles = ConfigManager.INSTANCE.listProfiles();
      for (String profile : profiles) {
         boolean selected = profile.equalsIgnoreCase(this.configName.trim());
         boolean hovered = inside(this.pointerX, this.pointerY, x + pad, listY, innerWidth, 22.0F);
         if (selected || hovered) {
            SkijaUi.rounded(canvas, x + pad, listY, innerWidth, 22.0F, 5.0F,
                  withAlpha(selected ? accent() : this.theme(UiTheme.themedHover(70), LIGHT_ROW_HOVER),
                        Math.round((selected ? 54.0F : 100.0F) * alpha)));
         }
         ClickGuiText.drawVerticallyCentered(canvas, profile, x + pad + 7.0F, listY, innerWidth - 14.0F, 22.0F,
               8.0F, withAlpha(selected ? this.themeText() : this.themeTextDim(), Math.round(255.0F * alpha)), 7.0F, false);
         listY += 25.0F;
      }
      if (profiles.isEmpty()) {
         ClickGuiText.drawVerticallyCentered(canvas, "No configs", x + pad, optionY + 51.0F, innerWidth, 22.0F,
               8.0F, withAlpha(this.themeTextFaint(), Math.round(255.0F * alpha)), 7.0F, false);
      }
      if (!this.configStatus.isBlank()) {
         ClickGuiText.drawVerticallyCentered(canvas, this.configStatus, x + pad, bodyY + height - 22.0F, innerWidth, 18.0F,
               8.0F, withAlpha(this.configStatusError ? 0xFFE06060 : accent(), Math.round(230.0F * alpha)), 6.4F, false);
      }
   }

   private void renderConfigTextField(Canvas canvas, float x, float y, float width, float height, float alpha) {
      boolean focused = this.editingConfigName;
      SkijaUi.rounded(canvas, x, y, width, height, 4.0F,
            withAlpha(focused ? accent() : this.themeTrack(), Math.round(255.0F * alpha)));
      String value = focused ? this.textWithCursor() : (this.configName.isBlank() ? "default" : this.configName);
      ClickGuiText.drawVerticallyCentered(canvas, value, x + 6.0F, y, width - 12.0F, height,
            8.0F, withAlpha(this.themeText(), Math.round(255.0F * alpha)), 7.0F, false);
   }

   private void renderConfigButton(Canvas canvas, float x, float y, float width, float height, String label, float alpha) {
      boolean hovered = inside(this.pointerX, this.pointerY, x, y, width, height);
      SkijaUi.rounded(canvas, x, y, width, height, 4.0F,
            withAlpha(hovered ? accent() : this.themePanelInner(), Math.round((hovered ? 125.0F : 205.0F) * alpha)));
      ClickGuiText.drawCentered(canvas, label, x, y, width, height, 8.0F,
            withAlpha(hovered ? this.themeText() : this.themeTextDim(), Math.round(255.0F * alpha)), 6.4F, true);
   }

   private void renderConfigOption(Canvas canvas, float x, float y, float width, String label, boolean checked, float alpha) {
      ClickGuiText.drawVerticallyCentered(canvas, label, x, y, width - 28.0F, 19.0F, 8.0F,
            withAlpha(this.themeTextDim(), Math.round(255.0F * alpha)), 6.8F, false);
      SkijaUi.rounded(canvas, x + width - 18.0F, y + 4.0F, 12.0F, 12.0F, 3.0F,
            withAlpha(checked ? accent() : this.themeTrack(), Math.round(255.0F * alpha)));
      if (checked) {
         SkijaUi.text(canvas, "x", x + width - 15.0F, y + 3.0F, 13.0F, this.themeText(), 7.0F);
      }
   }

   private void renderEspPreview(Canvas canvas, PopLayout layout) {
      this.espPreviewHits.clear();
      boolean espPreview = this.selectedModule == ESP.INSTANCE;
      boolean chestPreview = this.selectedModule == ChestESP.INSTANCE;
      boolean targetHudPreview = this.selectedModule == TargetHud.INSTANCE;
      boolean armorTrimPreview = this.selectedModule == ArmorTrimOverride.INSTANCE;
      boolean equipmentPreview = this.selectedModule == EquipmentDisguise.INSTANCE;
      if (espPreview || chestPreview || targetHudPreview || armorTrimPreview || equipmentPreview || this.selectedModule == KillEffect.INSTANCE) {
         if (!(layout.previewWidth() < 80.0F)) {
            float progress = smooth(this.settingsProgress);
            if (!(progress <= 0.01F)) {
               float x = lerp(this.currentSettingsX(layout) + layout.panelWidth() - layout.previewWidth(), layout.previewX(), progress);
               float y = layout.previewY();
               float width = layout.previewWidth();
               float height = layout.previewHeight();
               int color = categoryColor(Category.RENDER);
               SkijaUi.rounded(canvas, x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F, 10.0F, withAlpha(color, Math.round(145.0F * progress)));
               SkijaUi.rounded(canvas, x, y, width, height, 9.0F, withAlpha(this.themePanel(), Math.round(255.0F * progress)));
               float contentAlpha = smooth((progress - 0.16F) / 0.84F);
               String previewTitle = equipmentPreview ? "EQUIPMENT PREVIEW" : espPreview ? "ESP PREVIEW" : (chestPreview ? "CHEST ESP PREVIEW" : (targetHudPreview ? "TARGET HUD PREVIEW" : (armorTrimPreview ? "ARMOR TRIM PREVIEW" : "KILL EFFECT PREVIEW")));
               SkijaUi.boldText(canvas, previewTitle, x + 12.0F, y, 36.0F, withAlpha(this.themeText(), Math.round(240.0F * contentAlpha)), 7.4F);
               SkijaUi.fill(canvas, x + 12.0F, y + 35.0F, width - 24.0F, 1.0F, withAlpha(color, Math.round(95.0F * contentAlpha)));
               float viewportX = x + 10.0F;
               float viewportY = y + 45.0F;
               float viewportWidth = width - 20.0F;
               float viewportHeight = height - 55.0F;
               this.espViewportX = viewportX;
               this.espViewportY = viewportY;
               this.espViewportWidth = viewportWidth;
               this.espViewportHeight = viewportHeight;
               SkijaUi.rounded(canvas, viewportX, viewportY, viewportWidth, viewportHeight, 6.0F, withAlpha(this.themePanelInner(), Math.round(215.0F * contentAlpha)));
               canvas.save();
               canvas.clipRect(Rect.makeXYWH(viewportX, viewportY, viewportWidth, viewportHeight));
               if (espPreview) {
                  this.drawEspPlayer(canvas, viewportX, viewportY, viewportWidth, viewportHeight, contentAlpha);
                  this.drawEspPreviewHover(canvas, contentAlpha);
               } else if (chestPreview) {
                  this.drawPreviewChest(canvas, viewportX, viewportY, viewportWidth, viewportHeight, contentAlpha);
               } else if (targetHudPreview) {
                  int previewLayer = canvas.saveLayerAlpha((Rect)null, Math.round(255.0F * contentAlpha));
                  TargetHud.INSTANCE.renderPreview(canvas, viewportX, viewportY, viewportWidth, viewportHeight);
                  canvas.restoreToCount(previewLayer);
               } else if (armorTrimPreview) {
                  int previewLayer = canvas.saveLayerAlpha((Rect)null, Math.round(255.0F * contentAlpha));
                  ArmorTrimOverride.INSTANCE.renderPreview(canvas, viewportX, viewportY, viewportWidth, viewportHeight);
                  canvas.restoreToCount(previewLayer);
               } else if (equipmentPreview) {
                  int previewLayer = canvas.saveLayerAlpha((Rect)null, Math.round(255.0F * contentAlpha));
                  EquipmentDisguise.INSTANCE.renderPreview(canvas, viewportX, viewportY, viewportWidth, viewportHeight);
                  canvas.restoreToCount(previewLayer);
               } else {
                  int previewLayer = canvas.saveLayerAlpha((Rect)null, Math.round(255.0F * contentAlpha));
                  KillEffect.INSTANCE.renderPreview(canvas, viewportX, viewportY, viewportWidth, viewportHeight);
                  canvas.restoreToCount(previewLayer);
               }

               canvas.restore();
            }
         }
      }
   }

   private void drawEspPlayer(Canvas canvas, float x, float y, float width, float height, float alpha) {
      float scale = Math.max(1.0F, Math.min((width - 42.0F) / 16.0F, (height - 34.0F) / 32.0F));
      float figureWidth = 16.0F * scale;
      float figureHeight = 32.0F * scale;
      float figureX = x + (width - figureWidth) * 0.5F;
      float figureY = y + (height - figureHeight) * 0.5F;
      float boxInset = Math.max(3.0F, scale * 0.8F);
      boolean renderedSkin = false;
      if (this.minecraft.player != null && this.minecraft.player.getSkin() != null && this.minecraft.player.getSkin().body() != null) {
         Identifier skin = this.minecraft.player.getSkin().body().texturePath();

         try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(skin)) {
            if (borrowed != null) {
               drawSkinFigure(canvas, borrowed.image(), figureX, figureY, scale, alpha);
               renderedSkin = true;
            }
         } catch (Throwable var24) {
         }
      }

      if (!renderedSkin) {
         this.drawFallbackFigure(canvas, figureX, figureY, scale, alpha);
      }

      if ((Boolean)ESP.INSTANCE.players.get()) {
         int playerColor = multiplyAlpha(ESP.INSTANCE.playerColor.argb(), alpha);
         float left = figureX - boxInset;
         float top = figureY - boxInset;
         float boxWidth = figureWidth + boxInset * 2.0F;
         float boxHeight = figureHeight + boxInset * 2.0F;
         switch ((ESP.PlayerMode)ESP.INSTANCE.playerMode.get()) {
            case CSGO:
               this.drawPreviewCsgo(canvas, left, top, boxWidth, boxHeight, playerColor, alpha);
               break;
            case Outline:
               drawPreviewPlayerOutline(canvas, figureX, figureY, scale, playerColor);
               break;
            case Box2D:
               float boxX = left + ((Double)ESP.INSTANCE.playerOffsetX.get()).floatValue();
               float boxY = top + ((Double)ESP.INSTANCE.playerOffsetY.get()).floatValue();
               float radius = Math.min(((Double)ESP.INSTANCE.box2dCornerRadius.get()).floatValue(), Math.min(boxWidth, boxHeight) * 0.5F);
               if ((Boolean)ESP.INSTANCE.box2dBackground.get()) {
                  SkijaUi.rounded(canvas, boxX, boxY, boxWidth, boxHeight, radius, multiplyAlpha(UiTheme.withAlpha(ESP.INSTANCE.box2dBackgroundColor.argb(), (Integer)ESP.INSTANCE.box2dBackgroundOpacity.get()), alpha));
               }

               drawPreviewRoundedBox(canvas, boxX, boxY, boxWidth, boxHeight, radius, playerColor);
               this.addEspPreviewHit(PopClickGuiScreen.EspPreviewPart.PLAYER, boxX - 2.0F, boxY - 2.0F, boxWidth + 4.0F, boxHeight + 4.0F);
               break;
            case Box3D:
               drawPreview3DBox(canvas, left, top, boxWidth, boxHeight, playerColor);
               break;
            case Cylinder:
               drawPreviewCylinder(canvas, left, top, boxWidth, boxHeight, playerColor);
         }

         this.espInfoPreviewScale = ((Double)ESP.INSTANCE.infoScale.get()).floatValue();
         this.drawPreviewEquipment(canvas, left, top, boxWidth, alpha, this.espInfoPreviewScale);
         this.drawPreviewEffects(canvas, left, top, boxWidth, alpha, this.espInfoPreviewScale);
      }
   }

   private void drawPreviewEquipment(Canvas canvas, float boxX, float boxY, float boxWidth, float alpha, float previewScale) {
      if ((Boolean)ESP.INSTANCE.showEquipment.get()) {
         int itemCount = (Boolean)ESP.INSTANCE.showHands.get() ? 6 : 4;
         float itemSize = 8.0F;
         float gap = 1.5F;
         float rowWidth = (float)itemCount * itemSize + (float)(itemCount - 1) * gap;
         float x = -rowWidth * 0.5F + ((Double)ESP.INSTANCE.equipmentOffsetX.get()).floatValue();
         float y = -itemSize - 4.0F + ((Double)ESP.INSTANCE.equipmentOffsetY.get()).floatValue();
         float anchorX = boxX + boxWidth * 0.5F;
         canvas.save();
         canvas.translate(anchorX, boxY);
         canvas.scale(previewScale, previewScale);
         int[] colors = new int[]{-3549731, -7419941, -9401435, -10127989, -6520861, -2629145};

         for(int i = 0; i < itemCount; ++i) {
            float itemX = x + (float)i * (itemSize + gap);
            SkijaUi.rounded(canvas, itemX, y, itemSize, itemSize, 1.5F, multiplyAlpha(UiTheme.themedSurfaceAlt(176), alpha));
            float inset = i != 0 && i != itemCount - 1 ? 1.7F : 2.2F;
            SkijaUi.rounded(canvas, itemX + inset, y + inset, itemSize - inset * 2.0F, itemSize - inset * 2.0F, 0.8F, multiplyAlpha(colors[i], alpha));
         }

         canvas.restore();
         this.addEspPreviewHit(PopClickGuiScreen.EspPreviewPart.EQUIPMENT, anchorX + (x - 2.0F) * previewScale, boxY + (y - 2.0F) * previewScale, (rowWidth + 4.0F) * previewScale, (itemSize + 4.0F) * previewScale);
      }
   }

   private void drawPreviewEffects(Canvas canvas, float boxX, float boxY, float boxWidth, float alpha, float previewScale) {
      if ((Boolean)ESP.INSTANCE.showEffects.get()) {
         String[] effects = new String[]{"Speed II 1:24", "Strength I 0:48"};
         int[] colors = new int[]{-8599822, -1018764};
         float textSize = 5.8F;
         float rowHeight = 8.0F;
         float gap = 1.0F;
         float centerX = ((Double)ESP.INSTANCE.effectsOffsetX.get()).floatValue();
         float y = 2.0F + ((Double)ESP.INSTANCE.effectsOffsetY.get()).floatValue();
         float anchorX = boxX + boxWidth * 0.5F;
         canvas.save();
         canvas.translate(anchorX, boxY);
         canvas.scale(previewScale, previewScale);
         float unionLeft = Float.MAX_VALUE;
         float unionRight = -Float.MAX_VALUE;

         for(int i = 0; i < effects.length; ++i) {
            float rowWidth = SkijaUi.textWidth(effects[i], textSize) + 5.0F;
            float left = centerX - rowWidth * 0.5F;
            float rowY = y + (float)i * (rowHeight + gap);
            SkijaUi.fill(canvas, left, rowY, rowWidth, rowHeight, multiplyAlpha(ESP.INSTANCE.infoBackgroundColor.argb(), alpha));
            SkijaUi.text(canvas, effects[i], left + 2.5F, rowY, rowHeight, multiplyAlpha(colors[i], alpha), textSize);
            unionLeft = Math.min(unionLeft, left);
            unionRight = Math.max(unionRight, left + rowWidth);
         }

         canvas.restore();
         this.addEspPreviewHit(PopClickGuiScreen.EspPreviewPart.EFFECTS, anchorX + (unionLeft - 2.0F) * previewScale, boxY + (y - 2.0F) * previewScale, (unionRight - unionLeft + 4.0F) * previewScale, ((float)effects.length * rowHeight + (float)(effects.length - 1) * gap + 4.0F) * previewScale);
      }
   }

   private void drawPreviewChest(Canvas canvas, float x, float y, float width, float height, float alpha) {
      boolean drawChest = (Boolean)ChestESP.INSTANCE.chests.get();
      boolean drawEnder = (Boolean)ChestESP.INSTANCE.enderChests.get();
      if (drawChest || drawEnder) {
         float chestWidth = 23.0F;
         float chestHeight = 15.0F;
         float gap = 12.0F;
         float totalWidth = drawChest && drawEnder ? chestWidth * 2.0F + gap : chestWidth;
         float chestX = x + (width - totalWidth) * 0.5F;
         float chestY = y + (height - chestHeight) * 0.5F;
         if (drawChest) {
            drawPreviewChestIcon(canvas, chestX, chestY, chestWidth, chestHeight, ChestESP.INSTANCE.chestColor.argb(), alpha);
         }

         if (drawEnder) {
            float enderX = drawChest ? chestX + chestWidth + gap : chestX;
            drawPreviewChestIcon(canvas, enderX, chestY, chestWidth, chestHeight, ChestESP.INSTANCE.enderChestColor.argb(), alpha);
         }

      }
   }

   private static void drawPreviewChestIcon(Canvas canvas, float x, float y, float width, float height, int fillColor, float alpha) {
      int outlineColor = multiplyAlpha(ChestESP.INSTANCE.outlineColor.argb(), alpha);
      SkijaUi.fill(canvas, x, y, width, height, multiplyAlpha(fillColor, alpha));
      SkijaUi.fill(canvas, x, y + 5.0F, width, 1.0F, outlineColor);
      SkijaUi.fill(canvas, x + width * 0.5F - 1.5F, y + 6.0F, 3.0F, 4.0F, outlineColor);
      if ((Boolean)ChestESP.INSTANCE.outline.get()) {
         PREVIEW_OUTLINE_PAINT.setStrokeWidth(((Double)ChestESP.INSTANCE.outlineThickness.get()).floatValue()).setColor(outlineColor);
         canvas.drawRect(Rect.makeXYWH(x, y, width, height), PREVIEW_OUTLINE_PAINT);
      }

   }

   private void addEspPreviewHit(EspPreviewPart part, float x, float y, float width, float height) {
      this.espPreviewHits.add(new EspPreviewHit(part, x, y, width, height));
   }

   private void drawEspPreviewHover(Canvas canvas, float alpha) {
      for(int i = this.espPreviewHits.size() - 1; i >= 0; --i) {
         EspPreviewHit hit = (EspPreviewHit)this.espPreviewHits.get(i);
         if (hit.part() == this.draggingEspPreview || inside(this.pointerX, this.pointerY, hit.x(), hit.y(), hit.width(), hit.height())) {
            drawPreviewOutline(canvas, hit.x(), hit.y(), hit.width(), hit.height(), multiplyAlpha(accent(), alpha * 0.7F));
            return;
         }
      }

   }

   private void drawPreviewCsgo(Canvas canvas, float x, float y, float width, float height, int color, float alpha) {
      if ((Boolean)ESP.INSTANCE.playerBox.get()) {
         float boxX = x + ((Double)ESP.INSTANCE.playerOffsetX.get()).floatValue();
         float boxY = y + ((Double)ESP.INSTANCE.playerOffsetY.get()).floatValue();
         drawPreviewBox(canvas, boxX, boxY, width, height, color);
         this.addEspPreviewHit(PopClickGuiScreen.EspPreviewPart.PLAYER, boxX - 2.0F, boxY - 2.0F, width + 4.0F, height + 4.0F);
      }

      if ((Boolean)ESP.INSTANCE.healthBar.get()) {
         float barX = x - 7.0F + ((Double)ESP.INSTANCE.healthOffsetX.get()).floatValue();
         float barY = y + ((Double)ESP.INSTANCE.healthOffsetY.get()).floatValue();
         SkijaUi.fill(canvas, barX, barY, 4.0F, height, multiplyAlpha(-16777216, alpha));
         SkijaUi.fill(canvas, barX + 1.0F, barY + 1.0F, 2.0F, height - 2.0F, multiplyAlpha(-14406866, alpha));
         float health = 0.72F;
         float fillHeight = (height - 2.0F) * health;
         SkijaUi.fill(canvas, barX + 1.0F, barY + height - 1.0F - fillHeight, 2.0F, fillHeight, multiplyAlpha(-8527009, alpha));
         this.addEspPreviewHit(PopClickGuiScreen.EspPreviewPart.HEALTH, barX - 3.0F, barY, 10.0F, height);
      }

      if ((Boolean)ESP.INSTANCE.armorBar.get()) {
         float barX = x + width + 3.0F + ((Double)ESP.INSTANCE.armorOffsetX.get()).floatValue();
         float barY = y + ((Double)ESP.INSTANCE.armorOffsetY.get()).floatValue();
         float gap = 1.0F;
         float segmentHeight = (height - gap * 3.0F) / 4.0F;

         for(int i = 0; i < 4; ++i) {
            float segmentY = barY + (float)i * (segmentHeight + gap);
            SkijaUi.fill(canvas, barX, segmentY, 4.0F, segmentHeight, multiplyAlpha(-16777216, alpha));
            float durability = 1.0F - (float)i * 0.12F;
            float fillHeight = Math.max(1.0F, (segmentHeight - 2.0F) * durability);
            SkijaUi.fill(canvas, barX + 1.0F, segmentY + segmentHeight - 1.0F - fillHeight, 2.0F, fillHeight, multiplyAlpha(-11612443, alpha));
         }

         this.addEspPreviewHit(PopClickGuiScreen.EspPreviewPart.ARMOR, barX - 2.0F, barY, 8.0F, height);
      }

   }

   private static void drawPreviewBox(Canvas canvas, float x, float y, float width, float height, int color) {
      drawPreviewOutline(canvas, x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F, multiplyAlpha(-16777216, (float)(color >>> 24 & 255) / 255.0F));
      drawPreviewOutline(canvas, x, y, width, height, color);
      drawPreviewOutline(canvas, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, multiplyAlpha(-16777216, (float)(color >>> 24 & 255) / 255.0F));
   }

   private static void drawPreviewRoundedBox(Canvas canvas, float x, float y, float width, float height, float radius, int color) {
      int dark = multiplyAlpha(-16777216, (float)(color >>> 24 & 255) / 255.0F);
      SkijaUi.outline(canvas, x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F, radius + 1.0F, 1.0F, dark);
      SkijaUi.outline(canvas, x, y, width, height, radius, 1.0F, color);
      if (width > 2.0F && height > 2.0F) {
         SkijaUi.outline(canvas, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, Math.max(0.0F, radius - 1.0F), 1.0F, dark);
      }

   }

   private static void drawPreviewPlayerOutline(Canvas canvas, float x, float y, float scale, int color) {
      PREVIEW_OUTLINE_PAINT.setStrokeWidth(Math.max(1.0F, scale * 0.38F)).setColor(color);
      canvas.drawRect(Rect.makeXYWH(x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale), PREVIEW_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x + 4.0F * scale, y + 8.0F * scale, 8.0F * scale, 12.0F * scale), PREVIEW_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x, y + 8.0F * scale, 4.0F * scale, 12.0F * scale), PREVIEW_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x + 12.0F * scale, y + 8.0F * scale, 4.0F * scale, 12.0F * scale), PREVIEW_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x + 4.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale), PREVIEW_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x + 8.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale), PREVIEW_OUTLINE_PAINT);
   }

   private static void drawPreview3DBox(Canvas canvas, float x, float y, float width, float height, int color) {
      float offset = Math.max(4.0F, width * 0.16F);
      PREVIEW_OUTLINE_PAINT.setStrokeWidth(1.2F).setColor(color);
      Rect front = Rect.makeXYWH(x, y + offset, width - offset, height - offset);
      Rect back = Rect.makeXYWH(x + offset, y, width - offset, height - offset);
      canvas.drawRect(front, PREVIEW_OUTLINE_PAINT);
      canvas.drawRect(back, PREVIEW_OUTLINE_PAINT);
      canvas.drawLine(front.getLeft(), front.getTop(), back.getLeft(), back.getTop(), PREVIEW_OUTLINE_PAINT);
      canvas.drawLine(front.getRight(), front.getTop(), back.getRight(), back.getTop(), PREVIEW_OUTLINE_PAINT);
      canvas.drawLine(front.getLeft(), front.getBottom(), back.getLeft(), back.getBottom(), PREVIEW_OUTLINE_PAINT);
      canvas.drawLine(front.getRight(), front.getBottom(), back.getRight(), back.getBottom(), PREVIEW_OUTLINE_PAINT);
   }

   private static void drawPreviewCylinder(Canvas canvas, float x, float y, float width, float height, int color) {
      float ellipseHeight = Math.max(5.0F, width * 0.28F);
      PREVIEW_OUTLINE_PAINT.setStrokeWidth(1.2F).setColor(color);
      canvas.drawOval(Rect.makeXYWH(x, y, width, ellipseHeight), PREVIEW_OUTLINE_PAINT);
      canvas.drawOval(Rect.makeXYWH(x, y + height - ellipseHeight, width, ellipseHeight), PREVIEW_OUTLINE_PAINT);
      canvas.drawLine(x, y + ellipseHeight * 0.5F, x, y + height - ellipseHeight * 0.5F, PREVIEW_OUTLINE_PAINT);
      canvas.drawLine(x + width, y + ellipseHeight * 0.5F, x + width, y + height - ellipseHeight * 0.5F, PREVIEW_OUTLINE_PAINT);
   }

   private static void drawPreviewOutline(Canvas canvas, float x, float y, float width, float height, int color) {
      SkijaUi.fill(canvas, x, y, width, 1.0F, color);
      SkijaUi.fill(canvas, x, y + height - 1.0F, width, 1.0F, color);
      SkijaUi.fill(canvas, x, y + 1.0F, 1.0F, height - 2.0F, color);
      SkijaUi.fill(canvas, x + width - 1.0F, y + 1.0F, 1.0F, height - 2.0F, color);
   }

   private static void drawSkinFigure(Canvas canvas, Image image, float x, float y, float scale, float alpha) {
      SKIN_PAINT.setColor(-1).setAlpha(Math.round(255.0F * alpha));
      drawSkinPart(canvas, image, 8.0F, 8.0F, 8.0F, 8.0F, x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale);
      drawSkinPart(canvas, image, 20.0F, 20.0F, 8.0F, 12.0F, x + 4.0F * scale, y + 8.0F * scale, 8.0F * scale, 12.0F * scale);
      drawSkinPart(canvas, image, 44.0F, 20.0F, 4.0F, 12.0F, x, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
      drawSkinPart(canvas, image, 36.0F, 52.0F, 4.0F, 12.0F, x + 12.0F * scale, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
      drawSkinPart(canvas, image, 4.0F, 20.0F, 4.0F, 12.0F, x + 4.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);
      drawSkinPart(canvas, image, 20.0F, 52.0F, 4.0F, 12.0F, x + 8.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);
      drawSkinPart(canvas, image, 40.0F, 8.0F, 8.0F, 8.0F, x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale);
      drawSkinPart(canvas, image, 20.0F, 36.0F, 8.0F, 12.0F, x + 4.0F * scale, y + 8.0F * scale, 8.0F * scale, 12.0F * scale);
      drawSkinPart(canvas, image, 44.0F, 36.0F, 4.0F, 12.0F, x, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
      drawSkinPart(canvas, image, 52.0F, 52.0F, 4.0F, 12.0F, x + 12.0F * scale, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
      drawSkinPart(canvas, image, 4.0F, 36.0F, 4.0F, 12.0F, x + 4.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);
      drawSkinPart(canvas, image, 4.0F, 52.0F, 4.0F, 12.0F, x + 8.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);
      SKIN_PAINT.setAlpha(255);
   }

   private static void drawSkinPart(Canvas canvas, Image image, float sourceX, float sourceY, float sourceWidth, float sourceHeight, float x, float y, float width, float height) {
      float textureWidth = (float)image.getWidth();
      float textureHeight = (float)image.getHeight();
      Rect source = Rect.makeLTRB(sourceX / 64.0F * textureWidth, sourceY / 64.0F * textureHeight, (sourceX + sourceWidth) / 64.0F * textureWidth, (sourceY + sourceHeight) / 64.0F * textureHeight);
      canvas.drawImageRect(image, source, Rect.makeXYWH(x, y, width, height), SamplingMode.DEFAULT, SKIN_PAINT, true);
   }

   private void drawFallbackFigure(Canvas canvas, float x, float y, float scale, float alpha) {
      int color = withAlpha(this.themeTextDim(), Math.round(170.0F * alpha));
      SkijaUi.rounded(canvas, x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale, scale, color);
      SkijaUi.rounded(canvas, x, y + 8.0F * scale, 16.0F * scale, 12.0F * scale, scale, color);
      SkijaUi.rounded(canvas, x + 4.0F * scale, y + 20.0F * scale, 8.0F * scale, 12.0F * scale, scale, color);
   }

   private void renderModuleKeybindBadge(Canvas canvas, float centerX, float centerY, float alpha) {
      int color = withAlpha(this.themeText(), Math.round(255.0F * alpha));
      if (this.capturingModule == this.selectedModule) {
         drawCenteredText(canvas, "...", centerX, centerY, color, 7.4F, true);
      } else {
         String key = KeyBindText.of(this.selectedModule.keyBind());
         if (key != null && !key.isBlank()) {
            String label = key.toUpperCase(Locale.ROOT);
            float size = label.length() <= 2 ? 8.0F : (label.length() <= 4 ? 6.5F : 5.1F);
            drawCenteredText(canvas, fit(label, 20.0F, size, true), centerX, centerY, color, size, true);
         } else {
            this.drawKeyboardIcon(canvas, centerX, centerY, color);
         }
      }
   }

   private void drawKeyboardIcon(Canvas canvas, float centerX, float centerY, int color) {
      SkijaUi.rounded(canvas, centerX - 7.5F, centerY - 5.0F, 15.0F, 10.0F, 2.0F, color);
      SkijaUi.rounded(canvas, centerX - 6.3F, centerY - 3.8F, 12.6F, 7.6F, 1.2F, this.theme(UiTheme.themedSurfaceAlt(255), -525830));

      for(int row = 0; row < 2; ++row) {
         for(int column = 0; column < 4; ++column) {
            SkijaUi.rounded(canvas, centerX - 5.2F + (float)column * 2.8F, centerY - 2.8F + (float)row * 2.8F, 1.7F, 1.5F, 0.4F, color);
         }
      }

      SkijaUi.rounded(canvas, centerX - 3.7F, centerY + 2.4F, 7.4F, 1.0F, 0.4F, color);
   }

   private void renderSetting(Canvas canvas, Setting<?> setting, float x, float y, float width, float alpha) {
      float height = settingHeight(setting);
      float headerHeight = settingHeaderHeight(setting);
      this.renderSettingSurface(canvas, x, y, width, height, alpha);
      int labelColor = withAlpha(this.themeTextDim(), Math.round(255.0F * alpha));
      int valueColor = withAlpha(this.themeValue(), Math.round(255.0F * alpha));
      if (setting instanceof ButtonSetting) {
         ClickGuiText.drawCentered(canvas, setting.displayName(), x + 13.0F, y, width - 26.0F, height, 10.0F, valueColor, 7.4F, true);
      } else {
         float labelReserve = setting instanceof ColorSetting ? 112.0F : 68.0F;
         ClickGuiText.drawVerticallyCentered(canvas, setting.displayName(), x + 13.0F, y, Math.max(1.0F, width - labelReserve), headerHeight, 9.0F, labelColor, 7.2F, false);
         if (setting instanceof BooleanSetting) {
            BooleanSetting booleanSetting = (BooleanSetting)setting;
            float progress = (Float)this.toggleProgress.getOrDefault(setting, (Boolean)booleanSetting.get() ? 1.0F : 0.0F);
            float toggleX = x + width - 40.0F;
            float toggleY = y + 10.0F;
            SkijaUi.rounded(canvas, toggleX, toggleY, 27.0F, 12.0F, 6.0F, mix(this.themeTrack(), accent(), progress));
            SkijaUi.rounded(canvas, toggleX + 2.0F + progress * 15.0F, toggleY + 2.0F, 8.0F, 8.0F, 4.0F, this.themeText());
         } else if (setting instanceof FontSetting) {
            FontSetting fontSetting = (FontSetting)setting;
            ClickGuiText.drawRightCentered(canvas, fontSetting.displayValue(), x + width - 13.0F, y, 66.0F, headerHeight, 9.0F, valueColor, 7.0F, false);
         } else if (setting instanceof EnumSetting) {
            EnumSetting<?> enumSetting = (EnumSetting)setting;
            ClickGuiText.drawRightCentered(canvas, enumSetting.displayValue(), x + width - 13.0F, y, 66.0F, headerHeight, 9.0F, valueColor, 7.0F, false);
            if (enumSetting.isSlider()) {
               this.renderSlider(canvas, x + 13.0F, y + headerHeight - 1.0F,
                     width - 26.0F, enumSetting.sliderFraction(), valueColor);
            }
            if (MusicPresetPreview.matches(setting)) {
               MusicPresetPreview.draw(canvas, x + 13.0F, y + headerHeight, Math.max(1.0F, width - 26.0F), height - headerHeight, alpha);
            }
         } else if (setting instanceof IntSetting) {
            IntSetting intSetting = (IntSetting)setting;
            String value = Integer.toString((Integer)intSetting.get());
            drawRight(canvas, value, x + width - 13.0F, y, 31.0F, valueColor, 7.0F);
            this.renderSlider(canvas, x + 13.0F, y + headerHeight - 1.0F, width - 26.0F, intSetting.fraction(), valueColor);
         } else if (setting instanceof DoubleSetting) {
            DoubleSetting doubleSetting = (DoubleSetting)setting;
            String value = trim((Double)doubleSetting.get());
            drawRight(canvas, value, x + width - 13.0F, y, 31.0F, valueColor, 7.0F);
            this.renderSlider(canvas, x + 13.0F, y + headerHeight - 1.0F, width - 26.0F, doubleSetting.fraction(), valueColor);
         } else if (setting instanceof ColorSetting) {
            ColorSetting colorSetting = (ColorSetting)setting;
            this.renderColor(canvas, colorSetting, x, y, width, alpha);
         } else if (setting instanceof KeybindSetting) {
            KeybindSetting keybindSetting = (KeybindSetting)setting;
            String value = this.capturingKeybind == keybindSetting ? "..." : KeyBindText.of((Integer)keybindSetting.get());
            drawRight(canvas, value != null && !value.isBlank() ? value : "NONE", x + width - 13.0F, y, 31.0F, valueColor, 7.0F);
         } else if (setting instanceof StringSetting) {
            StringSetting stringSetting = (StringSetting)setting;
            String value = this.editingString == stringSetting ? this.textWithCursor() : (String)stringSetting.get();
            float fieldX = x + width - 78.0F;
            SkijaUi.rounded(canvas, fieldX, y + 7.0F, 65.0F, 17.0F, 4.0F, this.editingString == stringSetting ? withAlpha(accent(), 92) : this.themeTrack());
            SkijaUi.text(canvas, fit(value.isEmpty() ? "Empty" : value, 57.0F, 6.8F, false), fieldX + 4.0F, y + 7.0F, 17.0F, value.isEmpty() ? this.themeTextFaint() : this.themeText(), 6.8F);
         }

      }
   }

   private void renderSettingSurface(Canvas canvas, float x, float y, float width, float height, float alpha) {
      boolean hovered = inside(this.pointerX, this.pointerY, x + 7.0F, y + 2.0F, width - 14.0F, height - 4.0F);
      SkijaUi.rounded(canvas, x + 7.0F, y + 2.0F, width - 14.0F, Math.max(1.0F, height - 4.0F), 5.0F, withAlpha(hovered ? this.theme(UiTheme.themedHover(96), LIGHT_ROW_HOVER) : this.themePanelInner(), Math.round((hovered ? 155.0F : 205.0F) * alpha)));
   }

   private void renderSlider(Canvas canvas, float x, float y, float width, float fraction, int color) {
      float progress = clamp(fraction, 0.0F, 1.0F);
      float handleX = x + width * progress;
      SkijaUi.rounded(canvas, x, y, width, 2.0F, 1.0F, this.themeTrack());
      SkijaUi.rounded(canvas, x, y, width * progress, 2.0F, 1.0F, color);
      SkijaUi.rounded(canvas, handleX - 3.0F, y - 3.0F, 6.0F, 8.0F, 3.0F, color);
   }

   private void renderColor(Canvas canvas, ColorSetting setting, float x, float y, float width, float alpha) {
      Color color = setting.get();
      float fieldX = x + width - 105.0F;
      float fieldY = y + 7.0F;
      float fieldWidth = 68.0F;
      float fieldHeight = 17.0F;
      boolean expanded = ColorPickerState.expanded(setting);
      boolean focused = this.editingColor == setting;
      if (!expanded) {
         SkijaUi.text(canvas, "Set Color", fieldX, fieldY, fieldHeight, accent(), 6.6F);
      } else if (ColorPickerState.mode(setting) == ColorPickerState.Mode.RGB) {
         String hex = focused ? this.textWithCursor() : setting.hex();
         SkijaUi.rounded(canvas, fieldX, fieldY, fieldWidth, fieldHeight, 4.0F, focused ? withAlpha(accent(), 92) : this.themeTrack());
         SkijaUi.text(canvas, fit(hex, fieldWidth - 8.0F, 6.6F, false), fieldX + 4.0F, fieldY, fieldHeight, focused ? this.themeText() : this.themeTextDim(), 6.6F);
      }
      SkijaUi.rounded(canvas, x + width - 31.0F, y + 8.0F, 18.0F, 15.0F, 4.0F, setting.argb());
      if (!expanded) return;
      // Pop keeps the expanded editor in its own surface so the module list stays compact.
      return;

   }

   private void renderColorEditorPanel(Canvas canvas, PopLayout layout) {
      ColorSetting setting = this.colorEditorSetting;
      float colorProgress = smooth(this.colorEditorProgress);
      if (setting == null || this.settingsProgress <= 0.01F || colorProgress <= 0.01F) return;
      boolean hasPreview = layout.previewWidth() >= 80.0F;
      float width = this.colorEditorWidth(layout);
      float height = this.colorEditorHeight(setting, layout);
      float targetX = hasPreview ? layout.previewX() : layout.settingsX() + layout.panelWidth() + 10.0F;
      float startX = hasPreview ? this.currentSettingsX(layout) + layout.panelWidth() - width
              : this.currentSettingsX(layout) + layout.panelWidth() + 10.0F;
      float x = lerp(startX, targetX, smooth(this.settingsProgress));
      float y = hasPreview ? layout.previewY() + layout.previewHeight() + 10.0F
              : (layout.screenHeight() - height) * 0.5F;
      float progress = smooth(this.settingsProgress) * colorProgress;
      float scale = 0.92F + 0.08F * colorProgress;
      canvas.save();
      canvas.translate(x + width * 0.5F, y + height * 0.5F);
      canvas.scale(scale, scale);
      canvas.translate(-width * 0.5F - x, -height * 0.5F - y);
      int color = categoryColor(this.selectedCategory);
      SkijaUi.rounded(canvas, x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F, 10.0F, withAlpha(color, Math.round(145.0F * progress)));
      SkijaUi.rounded(canvas, x, y, width, height, 9.0F, withAlpha(this.themePanel(), Math.round(255.0F * progress)));
      SkijaUi.boldText(canvas, "COLOR", x + 12.0F, y + 4.0F, 16.0F, this.themeText(), 7.4F);
      SkijaUi.rounded(canvas, x + width - 31.0F, y + 7.0F, 18.0F, 15.0F, 4.0F, setting.argb());
      float modeY = y + 25.0F;
      float modeWidth = Math.min(116.0F, width - 26.0F);
      ColorPickerState.drawModeSelection(canvas, x + 13.0F, modeY, modeWidth, ColorPickerState.mode(setting), this.themeTextDim(), accent());
      if (ColorPickerState.mode(setting) == ColorPickerState.Mode.PALETTE) {
         ColorPickerState.drawPalette(canvas, x + 13.0F, modeY + 16.0F, width - 26.0F, 36.0F, setting, this.themeText());
      } else {
         Color current = setting.get();
         int[] values = new int[]{current.getRed(), current.getGreen(), current.getBlue(), current.getAlpha()};
         int[] colors = new int[]{-1549477, -11151488, -10838037, -1644310};
         int channels = setting.allowAlpha() ? 4 : 3;
         for (int i = 0; i < channels; i++) {
            float rowY = modeY + 17.0F + i * 17.0F;
            SkijaUi.text(canvas, new String[]{"R", "G", "B", "A"}[i], x + 13.0F, rowY, 13.0F, withAlpha(colors[i], Math.round(255.0F * progress)), 6.6F);
            drawRight(canvas, Integer.toString(values[i]), x + width - 13.0F, rowY, 13.0F, this.themeText(), 6.4F);
            this.renderSlider(canvas, x + 28.0F, rowY + 9.0F, width - 76.0F, values[i] / 255.0F, withAlpha(colors[i], Math.round(255.0F * progress)));
         }
      }
      canvas.restore();
   }

   /** Keeps the detached editor compact and gives drawing and hit testing one geometry source. */
   private float colorEditorWidth(PopLayout layout) {
      if (layout.previewWidth() >= 80.0F) {
         return layout.previewWidth();
      }
      return Math.min(layout.panelWidth(), 148.0F);
   }

   private float colorEditorHeight(ColorSetting setting, PopLayout layout) {
      float width = this.colorEditorWidth(layout);
      return ColorPickerState.mode(setting) == ColorPickerState.Mode.PALETTE
              ? 41.0F + ColorPickerState.paletteHeight(width)
              : 116.0F;
   }

   private ColorSetting expandedColorSetting() {
      for (Setting<?> setting : this.visibleSettings()) {
         if (setting instanceof ColorSetting color && ColorPickerState.expanded(color)) return color;
      }
      return null;
   }

   private boolean handleColorEditorClick(PopLayout layout, float mouseX, float mouseY, int button) {
      ColorSetting setting = this.expandedColorSetting();
      if (setting == null || button != 0) return false;
      boolean hasPreview = layout.previewWidth() >= 80.0F;
      float width = this.colorEditorWidth(layout);
      float x = hasPreview ? layout.previewX() : layout.settingsX() + layout.panelWidth() + 10.0F;
      float height = this.colorEditorHeight(setting, layout);
      float y = hasPreview ? layout.previewY() + layout.previewHeight() + 10.0F : (layout.screenHeight() - height) * 0.5F;
      if (!inside(mouseX, mouseY, x, y, width, height)) return false;
      float modeY = y + 25.0F;
      float modeWidth = Math.min(116.0F, width - 26.0F);
      if (ColorPickerState.clickMode(setting, mouseX, mouseY, x + 13.0F, modeY, modeWidth)) return true;
      if (ColorPickerState.mode(setting) == ColorPickerState.Mode.PALETTE) {
         return ColorPickerState.clickPalette(setting, mouseX, mouseY, x + 13.0F, modeY + 16.0F, width - 26.0F);
      }
      int channel = (int)((mouseY - modeY - 17.0F) / 17.0F);
      int channels = setting.allowAlpha() ? 4 : 3;
      if (channel >= 0 && channel < channels) {
         this.draggingColor = setting;
         this.draggingColorChannel = channel;
         this.draggingTrackX = x + 28.0F;
         this.draggingTrackWidth = width - 76.0F;
         this.updateColor(mouseX);
         return true;
      }
      return true;
   }

   private void renderScrollbar(Canvas canvas, float x, float y, float width, float height, float contentHeight, float scroll, int color) {
      if (!(contentHeight <= height) && !(height <= 0.0F)) {
         float trackTop = y + 4.0F;
         float trackHeight = Math.max(1.0F, height - 8.0F);
         float thumbHeight = Math.min(trackHeight, Math.max(18.0F, trackHeight * height / contentHeight));
         float maxScroll = contentHeight - height;
         float thumbY = trackTop + clamp(scroll / maxScroll, 0.0F, 1.0F) * Math.max(0.0F, trackHeight - thumbHeight);
         SkijaUi.rounded(canvas, x + width - 3.0F, y + 4.0F, 2.0F, trackHeight, 1.0F, withAlpha(this.themeTrack(), 150));
         SkijaUi.rounded(canvas, x + width - 3.0F, thumbY, 2.0F, thumbHeight, 1.0F, color);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (this.closing) {
         return true;
      } else {
         if (KeybindUtils.isAuxiliaryMouseButton(event.button())
               && (this.capturingKeybind != null || this.capturingModule != null)) {
            int binding = KeybindUtils.encodeMouseButton(event.button());
            if (this.capturingKeybind != null) this.capturingKeybind.set(binding);
            else this.capturingModule.setKeyBind(binding);
            this.capturingKeybind = null;
            this.capturingModule = null;
            return true;
         }
         float mouseX = this.logical(event.x());
         float mouseY = this.logical(event.y());
         int button = event.button();
         this.finishTextEditing();
         this.capturingModule = null;
         this.capturingKeybind = null;
         if (this.selectedCategory == null) {
            if (button != 0) {
               return false;
            } else {
               for(Category category : CATEGORIES) {
                  Bubble bubble = this.bubble(category, this.logicalWidth(), this.logicalHeight());
                  if (distance(mouseX, mouseY, bubble.x(), bubble.y()) <= 33.0F) {
                     this.selectedCategory = category;
                     this.categoryRequested = true;
                     this.moduleScroll = 0.0F;
                     return true;
                  }
               }

               return false;
            }
         } else {
            PopLayout layout = this.layout(this.logicalWidth(), this.logicalHeight());
            if (button == 0 && this.settingsProgress > 0.7F && this.beginEspPreviewDrag(mouseX, mouseY)) {
               return true;
            } else if (this.categoryProgress > 0.75F && inside(mouseX, mouseY, layout.listX() + 8.0F, layout.listY() + 8.0F, 28.0F, 28.0F)) {
               this.settingsRequested = false;
               this.categoryRequested = false;
               return true;
            } else {
               if (this.settingsProgress > 0.7F && (this.selectedModule != null || this.configOpen)) {
                  float settingsX = this.currentSettingsX(layout);
                  float settingsY = this.currentSettingsY(layout);
                  if (inside(mouseX, mouseY, settingsX + 8.0F, settingsY + 8.0F, 28.0F, 28.0F)) {
                     if (this.configOpen) {
                        this.configOpen = false;
                        this.settingsRequested = false;
                     } else if (button == 1) {
                        this.selectedModule.setKeyBind(-1);
                        this.capturingModule = null;
                     } else if (button == 0) {
                        this.capturingModule = this.selectedModule;
                     }

                     return true;
                  }

                  if (!this.configOpen && button == 0 && this.handleColorEditorClick(layout, mouseX, mouseY, button)) {
                     return true;
                  }

                  if (this.handleSettingClick(layout, mouseX, mouseY, button)) {
                     return true;
                  }
               }

               return this.categoryProgress > 0.75F && this.handleModuleClick(layout, mouseX, mouseY, button) ? true : super.mouseClicked(event, doubleClick);
            }
         }
      }
   }

   private boolean beginEspPreviewDrag(float mouseX, float mouseY) {
      if (this.selectedModule == ESP.INSTANCE && inside(mouseX, mouseY, this.espViewportX, this.espViewportY, this.espViewportWidth, this.espViewportHeight)) {
         for(int i = this.espPreviewHits.size() - 1; i >= 0; --i) {
            EspPreviewHit hit = (EspPreviewHit)this.espPreviewHits.get(i);
            if (inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height())) {
               this.draggingEspPreview = hit.part();
               this.espDragStartX = mouseX;
               this.espDragStartY = mouseY;
               this.espOffsetStartX = (Double)espOffsetX(hit.part()).get();
               this.espOffsetStartY = (Double)espOffsetY(hit.part()).get();
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void updateEspPreviewDrag(float mouseX, float mouseY) {
      if (this.draggingEspPreview != null) {
         float boundedX = clamp(mouseX, this.espViewportX + 1.0F, this.espViewportX + this.espViewportWidth - 1.0F);
         float boundedY = clamp(mouseY, this.espViewportY + 1.0F, this.espViewportY + this.espViewportHeight - 1.0F);
         float dragScale = this.draggingEspPreview != PopClickGuiScreen.EspPreviewPart.EQUIPMENT && this.draggingEspPreview != PopClickGuiScreen.EspPreviewPart.EFFECTS ? 1.0F : Math.max(0.01F, this.espInfoPreviewScale);
         espOffsetX(this.draggingEspPreview).set(this.espOffsetStartX + (double)((boundedX - this.espDragStartX) / dragScale));
         espOffsetY(this.draggingEspPreview).set(this.espOffsetStartY + (double)((boundedY - this.espDragStartY) / dragScale));
      }
   }

   private static DoubleSetting espOffsetX(EspPreviewPart part) {
      DoubleSetting var10000;
      switch (part.ordinal()) {
         case 0 -> var10000 = ESP.INSTANCE.playerOffsetX;
         case 1 -> var10000 = ESP.INSTANCE.healthOffsetX;
         case 2 -> var10000 = ESP.INSTANCE.armorOffsetX;
         case 3 -> var10000 = ESP.INSTANCE.equipmentOffsetX;
         case 4 -> var10000 = ESP.INSTANCE.effectsOffsetX;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static DoubleSetting espOffsetY(EspPreviewPart part) {
      DoubleSetting var10000;
      switch (part.ordinal()) {
         case 0 -> var10000 = ESP.INSTANCE.playerOffsetY;
         case 1 -> var10000 = ESP.INSTANCE.healthOffsetY;
         case 2 -> var10000 = ESP.INSTANCE.armorOffsetY;
         case 3 -> var10000 = ESP.INSTANCE.equipmentOffsetY;
         case 4 -> var10000 = ESP.INSTANCE.effectsOffsetY;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private boolean handleModuleClick(PopLayout layout, float mouseX, float mouseY, int button) {
      if (!inside(mouseX, mouseY, layout.listX(), layout.listY() + 44.0F, layout.panelWidth(), layout.listHeight() - 44.0F)) {
         return false;
      } else {
         float rowY = layout.listY() + 44.0F - this.moduleScroll;

         for(Module module : ModuleManager.INSTANCE.modulesIn(this.selectedCategory)) {
            if (inside(mouseX, mouseY, layout.listX(), rowY, layout.panelWidth(), 25.0F)) {
               this.configOpen = false;
               if (button == 0) {
                  module.interactFromClickGui();
               } else if (button == 1) {
                  if (this.selectedModule == module && this.settingsRequested) {
                     this.settingsRequested = false;
                  } else {
                     this.configOpen = false;
                     this.selectedModule = module;
                     this.settingsRequested = true;
                     this.settingScroll = 0.0F;
                  }
               }

               return true;
            }

            rowY += 25.0F;
         }

         if (this.selectedCategory == Category.CLIENT && inside(mouseX, mouseY, layout.listX(), rowY,
               layout.panelWidth(), 25.0F)) {
            this.finishTextEditing();
            this.selectedModule = null;
            this.configOpen = true;
            this.settingsRequested = true;
            this.configScroll = 0.0F;
            if (this.configName.isBlank()) {
               this.configName = ConfigManager.INSTANCE.currentProfile();
            }
            return true;
         }

         return false;
      }
   }

   private boolean handleSettingClick(PopLayout layout, float mouseX, float mouseY, int button) {
      float settingsX = this.currentSettingsX(layout);
      float settingsY = this.currentSettingsY(layout);
      float settingsHeight = this.currentSettingsHeight(layout);
      float bodyY = settingsY + 44.0F;
      if (!inside(mouseX, mouseY, settingsX, bodyY, layout.panelWidth(), settingsHeight - 44.0F)) {
         return false;
      } else {
         if (this.configOpen) {
            return this.handleConfigClick(layout, mouseX, mouseY, button);
         }
         float rowY = bodyY - this.settingScroll;

         for(Setting<?> setting : this.visibleSettings()) {
            float height = settingHeight(setting);
            if (inside(mouseX, mouseY, settingsX, rowY, layout.panelWidth(), height)) {
               this.clickSetting(setting, layout, rowY, mouseX, mouseY, button);
               return true;
            }

            rowY += height;
         }

         return false;
      }
   }

   private void clickSetting(Setting<?> setting, PopLayout layout, float rowY, float mouseX, float mouseY, int button) {
      if (setting instanceof BooleanSetting booleanSetting) {
         if (button == 0 || button == 1) {
            NotificationManager var10000 = NotificationManager.INSTANCE;
            Objects.requireNonNull(booleanSetting);
            var10000.withoutModuleFeedback(booleanSetting::toggle);
         }
      } else if (setting instanceof FontSetting fontSetting) {
         fontSetting.cycle(button == 1 ? -1 : 1);
      } else if (setting instanceof EnumSetting<?> enumSetting) {
         if (enumSetting.isSlider()) {
            if (button == 0 && mouseY >= rowY + settingHeaderHeight(setting) - 6.0F) {
               this.beginNumberDrag(setting, this.currentSettingsX(layout) + 13.0F,
                     layout.panelWidth() - 26.0F, mouseX);
            } else if (button == 1) {
               enumSetting.cycle(-1);
            }
         } else {
            enumSetting.cycle(button == 1 ? -1 : 1);
         }
      } else if (setting instanceof IntSetting intSetting) {
         if (button == 1) {
            intSetting.set((Integer)intSetting.get() - intSetting.step());
         } else if (button == 0) {
            this.beginNumberDrag(setting, this.currentSettingsX(layout) + 13.0F, layout.panelWidth() - 26.0F, mouseX);
         }
      } else if (setting instanceof DoubleSetting doubleSetting) {
         if (button == 1) {
            doubleSetting.set((Double)doubleSetting.get() - doubleSetting.step());
         } else if (button == 0) {
            this.beginNumberDrag(setting, this.currentSettingsX(layout) + 13.0F, layout.panelWidth() - 26.0F, mouseX);
         }
      } else if (setting instanceof ColorSetting colorSetting) {
         float fieldX = this.currentSettingsX(layout) + layout.panelWidth() - 105.0F;
         if (button == 1) {
            ColorPickerState.toggle(colorSetting);
            this.finishTextEditing();
         } else if (button == 0 && ColorPickerState.expanded(colorSetting)
               && ColorPickerState.clickMode(colorSetting, mouseX, mouseY, this.currentSettingsX(layout) + 13.0F,
               rowY + settingHeaderHeight(colorSetting) + 2.0F, Math.min(116.0F, layout.panelWidth() - 26.0F))) {
            return;
         } else if (button == 0 && ColorPickerState.expanded(colorSetting)
               && ColorPickerState.mode(colorSetting) == ColorPickerState.Mode.PALETTE
               && ColorPickerState.clickPalette(colorSetting, mouseX, mouseY, this.currentSettingsX(layout) + 13.0F,
               rowY + settingHeaderHeight(colorSetting) + 18.0F, layout.panelWidth() - 26.0F)) {
            return;
         } else if (button == 0 && ColorPickerState.expanded(colorSetting)
               && inside(mouseX, mouseY, fieldX, rowY + 7.0F, 68.0F, 17.0F)) {
            this.focusColor(colorSetting);
         } else if (button == 0 && !ColorPickerState.expanded(colorSetting)) {
            return;
         } else if (button == 0 && mouseY >= rowY + settingHeaderHeight(setting)) {
            int channel = (int)((mouseY - rowY - settingHeaderHeight(setting) - 18.0F) / 18.0F);
            int channels = colorSetting.allowAlpha() ? 4 : 3;
            if (channel >= 0 && channel < channels) {
               this.draggingColor = colorSetting;
               this.draggingColorChannel = channel;
               this.draggingTrackX = this.currentSettingsX(layout) + 28.0F;
               this.draggingTrackWidth = layout.panelWidth() - 76.0F;
               this.updateColor(mouseX);
            }
         }
      } else if (setting instanceof KeybindSetting keybindSetting) {
         if (button == 1) {
            keybindSetting.set(-1);
         } else if (button == 0) {
            this.capturingKeybind = keybindSetting;
         }
      } else if (setting instanceof StringSetting stringSetting) {
         if (button == 1) {
            stringSetting.reset();
         } else if (button == 0) {
            this.focusString(stringSetting);
         }
      } else if (setting instanceof ButtonSetting buttonSetting) {
         if (button == 0) {
            buttonSetting.press();
         }
      }

   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      if (this.closing) {
         return true;
      } else {
         float mouseX = this.logical(event.x());
         float mouseY = this.logical(event.y());
         if (this.draggingEspPreview != null) {
            this.updateEspPreviewDrag(mouseX, mouseY);
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
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.closing) {
         return true;
      } else {
         this.draggingNumber = null;
         this.draggingColor = null;
         this.draggingColorChannel = -1;
         this.draggingEspPreview = null;
         this.activeScale = this.configuredScale();
         return super.mouseReleased(event);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.closing) {
         return true;
      } else {
         float x = this.logical(mouseX);
         float y = this.logical(mouseY);
         PopLayout layout = this.layout(this.logicalWidth(), this.logicalHeight());
         if (this.settingsProgress > 0.65F && inside(x, y, this.currentSettingsX(layout), this.currentSettingsY(layout), layout.panelWidth(), this.currentSettingsHeight(layout))) {
            if (this.configOpen) {
               this.configScroll -= (float)verticalAmount * 24.0F;
            } else {
               this.settingScroll -= (float)verticalAmount * 24.0F;
            }
            this.clampScrolls(layout);
            return true;
         } else if (this.selectedCategory != null && this.categoryProgress > 0.65F && inside(x, y, layout.listX(), layout.listY(), layout.panelWidth(), layout.listHeight())) {
            this.moduleScroll -= (float)verticalAmount * 24.0F;
            this.clampScrolls(layout);
            return true;
         } else {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
         }
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (this.closing) {
         return true;
      } else if (this.capturingKeybind != null) {
         this.captureKey(event, this.capturingKeybind);
         return true;
      } else if (this.capturingModule == null) {
         if (this.hasTextInput() && this.handleTextKey(event)) {
            return true;
         } else if (event.key() == 256) {
            if (this.settingsRequested) {
               this.settingsRequested = false;
            } else if (this.categoryRequested) {
               this.categoryRequested = false;
            } else {
               this.onClose();
            }

            return true;
         } else {
            return super.keyPressed(event);
         }
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

   private void captureKey(KeyEvent event, KeybindSetting setting) {
      if (event.key() == 256) {
         this.capturingKeybind = null;
      } else if (event.key() != 261 && event.key() != 259) {
         if (event.key() != -1) {
            setting.set(event.key());
            this.capturingKeybind = null;
         }
      } else {
         setting.set(-1);
         this.capturingKeybind = null;
      }

   }

   public boolean charTyped(CharacterEvent event) {
      if (this.hasTextInput() && event.isAllowedChatCharacter()) {
         this.insertText(event.codepointAsString());
         return true;
      } else {
         return super.charTyped(event);
      }
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

   private void focusString(StringSetting setting) {
      this.editingString = setting;
      this.editingColor = null;
      this.colorBeforeEdit = null;
      this.editText = (String)setting.get();
      this.editCursor = this.editText.length();
      this.selectAll = false;
   }

   private void focusColor(ColorSetting setting) {
      this.editingString = null;
      this.editingColor = setting;
      this.colorBeforeEdit = setting.get();
      this.editText = setting.hex();
      this.editCursor = this.editText.length();
      this.selectAll = true;
   }

   private boolean hasTextInput() {
      return this.editingConfigName || this.editingString != null || this.editingColor != null;
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

         int maxLength = this.editingColor != null ? 9 : 128;
         int remaining = maxLength - this.editText.length();
         if (remaining > 0) {
            String value = clean.length() > remaining ? clean.substring(0, remaining) : clean.toString();
            this.editText = this.editText.substring(0, this.editCursor) + value + this.editText.substring(this.editCursor);
            this.editCursor += value.length();
            this.selectAll = false;
            this.applyEditedText();
         }
      }
   }

   private void applyEditedText() {
      if (this.editingConfigName) {
         this.configName = this.editText;
      } else if (this.editingString != null) {
         this.editingString.set(this.editText);
      } else if (this.editingColor != null) {
         Color parsed = this.editingColor.parseHex(this.editText);
         if (parsed != null) {
            this.editingColor.set(parsed);
         }
      }

   }

   private void finishTextEditing() {
      if (this.editingColor != null) {
         Color parsed = this.editingColor.parseHex(this.editText);
         this.editingColor.set(parsed != null ? parsed : this.colorBeforeEdit);
      } else {
         this.applyEditedText();
      }

      this.editingConfigName = false;
      this.editingString = null;
      this.editingColor = null;
      this.colorBeforeEdit = null;
      this.editText = "";
      this.editCursor = 0;
      this.selectAll = false;
   }

   private String textWithCursor() {
      int cursor = Math.max(0, Math.min(this.editCursor, this.editText.length()));
      String var10000 = this.editText.substring(0, cursor);
      return var10000 + "|" + this.editText.substring(cursor);
   }

   private void beginNumberDrag(Setting<?> setting, float x, float width, float mouseX) {
      this.draggingNumber = setting;
      this.draggingTrackX = x;
      this.draggingTrackWidth = width;
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
         this.draggingColor.set(new Color(this.draggingColorChannel == 0 ? value : current.getRed(), this.draggingColorChannel == 1 ? value : current.getGreen(), this.draggingColorChannel == 2 ? value : current.getBlue(), this.draggingColorChannel == 3 ? value : current.getAlpha()));
      }
   }

   public void onClose() {
      if (!this.closing && !this.closeFinished) {
         this.finishTextEditing();
         this.capturingModule = null;
         this.capturingKeybind = null;
         this.draggingNumber = null;
         this.draggingColor = null;
         this.draggingColorChannel = -1;
         this.settingsRequested = false;
         this.categoryRequested = false;
         this.closing = true;
      }
   }

   private boolean completeCloseWhenReady() {
      if (this.closing && !(this.introProgress > 0.055F) && !(this.categoryProgress > 0.055F) && !(this.settingsProgress > 0.055F)) {
         this.closeFinished = true;
         this.restoreMenuBlur();
         ConfigManager.INSTANCE.save();
         if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
         } else {
            super.onClose();
         }

         return true;
      } else {
         return false;
      }
   }

   public void removed() {
      this.restoreMenuBlur();
      ConfigManager.INSTANCE.save();
      super.removed();
   }

   private void applyMenuBlur(int strength) {
      if (this.originalMenuBlur == null) {
         this.originalMenuBlur = this.minecraft.options.getMenuBackgroundBlurriness();
      }

      if (this.minecraft.options.getMenuBackgroundBlurriness() != strength) {
         this.minecraft.options.menuBackgroundBlurriness().set(strength);
      }

   }

   private void restoreMenuBlur() {
      if (this.originalMenuBlur != null) {
         if (this.minecraft.options.getMenuBackgroundBlurriness() != this.originalMenuBlur) {
            this.minecraft.options.menuBackgroundBlurriness().set(this.originalMenuBlur);
         }

         this.originalMenuBlur = null;
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private List<Setting<?>> visibleSettings() {
      return this.selectedModule == null ? List.of() : this.selectedModule.settings().stream().filter(Setting::visible).toList();
   }

   private float settingsContentHeight() {
      float height = 0.0F;

      for(Setting<?> setting : this.visibleSettings()) {
         height += settingHeight(setting);
      }

      return height;
   }

   private static float settingHeight(Setting<?> setting) {
      float headerHeight = settingHeaderHeight(setting);
      if (!(setting instanceof IntSetting) && !(setting instanceof DoubleSetting)
            && !(setting instanceof EnumSetting<?> enumSetting && enumSetting.isSlider())) {
         if (setting instanceof ColorSetting) {
            // The full editor is detached below the preview; keep the setting row compact.
            return headerHeight;
         } else {
            return MusicPresetPreview.matches(setting) ? headerHeight + 23.0F : headerHeight;
         }
      } else {
         return headerHeight + 11.0F;
      }
   }

   private static float settingHeaderHeight(Setting<?> setting) {
      float labelWidth = setting instanceof ColorSetting ? 30.0F : (setting instanceof ButtonSetting ? 116.0F : 74.0F);
      int lines = ClickGuiText.lineCount(setting.displayName(), labelWidth, 7.2F, setting instanceof ButtonSetting);
      if (setting instanceof FontSetting fontSetting) {
         lines = Math.max(lines, ClickGuiText.lineCount(fontSetting.displayValue(), 66.0F, 7.0F, false));
      } else if (setting instanceof EnumSetting<?> enumSetting) {
         lines = Math.max(lines, ClickGuiText.lineCount(enumSetting.displayValue(), 66.0F, 7.0F, false));
      }

      return Math.max(31.0F, (float)lines * 9.0F + 4.0F);
   }

   private void clampScrolls(PopLayout layout) {
      float moduleContent = this.selectedCategory == null ? 0.0F : (float)ModuleManager.INSTANCE.modulesIn(this.selectedCategory).size() * 25.0F;
      this.moduleScroll = clamp(this.moduleScroll, 0.0F, Math.max(0.0F, moduleContent - (layout.listHeight() - 44.0F)));
      this.settingScroll = clamp(this.settingScroll, 0.0F, Math.max(0.0F, this.settingsContentHeight() - (layout.settingsHeight() - 44.0F)));
      this.configScroll = clamp(this.configScroll, 0.0F, Math.max(0.0F, this.configContentHeight() - (layout.settingsHeight() - 44.0F)));
   }

   private boolean handleConfigClick(PopLayout layout, float mouseX, float mouseY, int button) {
      if (button != 0) {
         return true;
      }
      float x = this.currentSettingsX(layout);
      float y = this.currentSettingsY(layout) + 44.0F;
      float width = layout.panelWidth();
      float pad = 13.0F;
      float innerWidth = Math.max(1.0F, width - pad * 2.0F);
      float fieldY = y + 6.0F;
      if (inside(mouseX, mouseY, x + pad, fieldY, innerWidth, 21.0F)) {
         this.focusConfigName();
         return true;
      }

      float buttonY = fieldY + 27.0F;
      float gap = 4.0F;
      float buttonWidth = Math.max(1.0F, (innerWidth - gap * 2.0F) / 3.0F);
      if (inside(mouseX, mouseY, x + pad, buttonY, buttonWidth, 21.0F)) {
         this.saveConfig();
         return true;
      }
      if (inside(mouseX, mouseY, x + pad + buttonWidth + gap, buttonY, buttonWidth, 21.0F)) {
         this.loadConfig();
         return true;
      }
      if (inside(mouseX, mouseY, x + pad + (buttonWidth + gap) * 2.0F, buttonY, buttonWidth, 21.0F)) {
         this.deleteConfig();
         return true;
      }

      float utilityY = buttonY + 26.0F;
      float utilityWidth = Math.max(1.0F, (innerWidth - gap) / 2.0F);
      if (inside(mouseX, mouseY, x + pad, utilityY, utilityWidth, 21.0F)) {
         this.openConfigFolder();
         return true;
      }
      if (inside(mouseX, mouseY, x + pad + utilityWidth + gap, utilityY, utilityWidth, 21.0F)) {
         this.refreshConfigs();
         return true;
      }

      float optionY = utilityY + 28.0F;
      if (inside(mouseX, mouseY, x + pad, optionY, innerWidth, 19.0F)) {
         this.importVisualAndHud = !this.importVisualAndHud;
         return true;
      }
      if (inside(mouseX, mouseY, x + pad, optionY + 21.0F, innerWidth, 19.0F)) {
         this.importKeybinds = !this.importKeybinds;
         return true;
      }

      float rowY = optionY + 48.0F - this.configScroll;
      for (String profile : ConfigManager.INSTANCE.listProfiles()) {
         if (inside(mouseX, mouseY, x + pad, rowY, innerWidth, 22.0F)) {
            this.finishTextEditing();
            this.configName = profile;
            this.configStatus = "";
            return true;
         }
         rowY += 25.0F;
      }
      return true;
   }

   private void focusConfigName() {
      this.editingConfigName = true;
      this.editingString = null;
      this.editingColor = null;
      this.colorBeforeEdit = null;
      this.editText = this.configName;
      this.editCursor = this.editText.length();
      this.selectAll = false;
   }

   private void saveConfig() {
      this.finishTextEditing();
      try {
         this.configName = ConfigManager.INSTANCE.saveProfile(this.configName);
         this.setConfigStatus("Saved " + this.configName, false);
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to save config profile {}", this.configName, error);
         this.setConfigStatus("Save failed", true);
      }
   }

   private void loadConfig() {
      this.finishTextEditing();
      try {
         if (ConfigManager.INSTANCE.loadProfile(this.configName,
               new ConfigManager.ImportOptions(this.importVisualAndHud, this.importKeybinds))) {
            this.configName = ConfigManager.INSTANCE.currentProfile();
            this.setConfigStatus("Loaded " + this.configName, false);
         } else {
            this.setConfigStatus("Profile not found", true);
         }
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to load config profile {}", this.configName, error);
         this.setConfigStatus("Load failed", true);
      }
   }

   private void deleteConfig() {
      this.finishTextEditing();
      String name = this.configName;
      try {
         if (ConfigManager.INSTANCE.deleteProfile(name)) {
            this.configName = ConfigManager.INSTANCE.currentProfile();
            this.setConfigStatus("Deleted " + name, false);
         } else {
            this.setConfigStatus("Default config cannot be deleted", true);
         }
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to delete config profile {}", name, error);
         this.setConfigStatus("Delete failed", true);
      }
   }

   private void openConfigFolder() {
      this.finishTextEditing();
      try {
         Path directory = ConfigManager.INSTANCE.configDirectory();
         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(directory.toFile());
         } else {
            Util.getPlatform().openUri(directory.toUri().toString());
         }
         this.setConfigStatus("Opened config folder", false);
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to open the config folder", error);
         this.setConfigStatus("Open folder failed", true);
      }
   }

   private void refreshConfigs() {
      this.finishTextEditing();
      try {
         ConfigManager.INSTANCE.refreshProfiles();
         if (ConfigManager.INSTANCE.listProfiles().stream().noneMatch(profile -> profile.equalsIgnoreCase(this.configName.trim()))) {
            this.configName = ConfigManager.INSTANCE.currentProfile();
         }
         this.configScroll = 0.0F;
         this.setConfigStatus("Profiles refreshed", false);
      } catch (RuntimeException error) {
         Setsuna.LOGGER.error("Failed to refresh config profiles", error);
         this.setConfigStatus("Refresh failed", true);
      }
   }

   private void setConfigStatus(String message, boolean error) {
      this.configStatus = message == null ? "" : message;
      this.configStatusError = error;
   }

   private float configContentHeight() {
      return 6.0F + 21.0F + 27.0F + 21.0F + 26.0F + 21.0F + 28.0F + 19.0F + 21.0F + 48.0F
            + (float)ConfigManager.INSTANCE.listProfiles().size() * 25.0F;
   }

   private PopLayout layout(float screenWidth, float screenHeight) {
      float maxPairWidth = Math.max(220.0F, screenWidth - 28.0F);
      float panelWidth = clamp(screenWidth * 0.23F, 142.0F, 196.0F);
      panelWidth = Math.min(panelWidth, (maxPairWidth - 10.0F) * 0.5F);
      float listHeight = clamp(screenHeight * 0.61F, 218.0F, 322.0F);
      listHeight = Math.min(listHeight, screenHeight - 24.0F);
      float settingsHeight = clamp(screenHeight * 0.75F, 260.0F, 405.0F);
      settingsHeight = Math.min(settingsHeight, screenHeight - 16.0F);
      float closedListX = (screenWidth - panelWidth) * 0.5F;
      float previewWidth = 0.0F;
      if (this.selectedModule == ESP.INSTANCE || this.selectedModule == ChestESP.INSTANCE || this.selectedModule == TargetHud.INSTANCE || this.selectedModule == KillEffect.INSTANCE || this.selectedModule == ArmorTrimOverride.INSTANCE || this.selectedModule == EquipmentDisguise.INSTANCE) {
         float available = screenWidth - 16.0F - panelWidth * 2.0F - 20.0F;
         float desired = this.selectedModule == TargetHud.INSTANCE ? clamp(panelWidth * 0.95F, 128.0F, 176.0F) : (this.selectedModule == ChestESP.INSTANCE ? clamp(panelWidth * 0.76F, 112.0F, 148.0F) : (this.selectedModule == KillEffect.INSTANCE || this.selectedModule == ArmorTrimOverride.INSTANCE || this.selectedModule == EquipmentDisguise.INSTANCE ? clamp(panelWidth * 0.9F, 128.0F, 170.0F) : clamp(panelWidth * 0.76F, 112.0F, 148.0F)));
         previewWidth = Math.min(desired, Math.max(0.0F, available));
         if (previewWidth < 92.0F) {
            previewWidth = 0.0F;
         }
      }

      ColorSetting colorSetting = this.colorEditorSetting;
      float colorProgress = smooth(this.colorEditorProgress);
      boolean colorEditor = colorSetting != null && colorProgress > 0.01F;
      float expandedWidth = panelWidth * 2.0F + 10.0F;
      if (previewWidth > 0.0F) {
         expandedWidth += 10.0F + previewWidth;
      } else if (colorEditor) {
         expandedWidth += (10.0F + Math.min(panelWidth, 148.0F)) * colorProgress;
      }

      float pairStart = (screenWidth - expandedWidth) * 0.5F;
      float listX = lerp(closedListX, pairStart, smooth(this.settingsProgress));
      float listY = (screenHeight - listHeight) * 0.5F;
      float settingsX = pairStart + panelWidth + 10.0F;
      float settingsY = (screenHeight - settingsHeight) * 0.5F;
      float editorWidth = previewWidth >= 80.0F ? previewWidth : Math.min(panelWidth, 148.0F);
      float editorHeight = colorSetting != null
              ? (ColorPickerState.mode(colorSetting) == ColorPickerState.Mode.PALETTE
                 ? 41.0F + ColorPickerState.paletteHeight(editorWidth) : 116.0F)
              : 116.0F;
      float previewHeight = previewWidth > 0.0F ? Math.min(clamp(settingsHeight * 0.62F, 190.0F, 250.0F),
              screenHeight - (colorEditor ? (editorHeight + 10.0F) * colorProgress + 16.0F : 16.0F)) : 0.0F;
      float previewX = settingsX + panelWidth + 10.0F;
      float previewY = (screenHeight - previewHeight - (colorEditor ? (editorHeight + 10.0F) * colorProgress : 0.0F)) * 0.5F;
      return new PopLayout(screenWidth, screenHeight, panelWidth, listHeight, settingsHeight, closedListX, listX, listY, settingsX, settingsY, previewX, previewY, previewWidth, previewHeight);
   }

   private float currentSettingsX(PopLayout layout) {
      return lerp(layout.closedListX(), layout.settingsX(), smooth(this.settingsProgress));
   }

   private float currentSettingsY(PopLayout layout) {
      return lerp(layout.listY(), layout.settingsY(), smooth(this.settingsProgress));
   }

   private float currentSettingsHeight(PopLayout layout) {
      return lerp(layout.listHeight(), layout.settingsHeight(), smooth(this.settingsProgress));
   }

   private Bubble bubble(Category category, float screenWidth, float screenHeight) {
      int index;
      for(index = 0; index < CATEGORIES.length && CATEGORIES[index] != category; ++index) {
      }

      float angle = -90.0F + (float)index * (360.0F / (float)CATEGORIES.length);
      double radians = Math.toRadians((double)angle);
      float radius = ringRadius(screenWidth, screenHeight);
      return new Bubble(screenWidth * 0.5F + (float)Math.cos(radians) * radius, screenHeight * 0.5F + (float)Math.sin(radians) * radius);
   }

   private static float ringRadius(float width, float height) {
      return clamp(Math.min(width, height) * 0.27F, 78.0F, 135.0F);
   }

   private float animate(float current, float target, float speed) {
      return current + (target - current) * (1.0F - (float)Math.exp((double)(-speed * this.delta)));
   }

   private float configuredScale() {
      return clamp((float)(Integer)ClickGui.INSTANCE.guiScale.get() / 100.0F, 0.65F, 1.25F);
   }

   private float logical(double value) {
      return (float)(value / (double)this.activeScale);
   }

   private float logicalWidth() {
      return (float)this.width / this.activeScale;
   }

   private float logicalHeight() {
      return (float)this.height / this.activeScale;
   }

   private static void drawCenteredIcon(Canvas canvas, String icon, float centerX, float centerY, int color, float size) {
      float width = SkijaUi.iconWidth(icon, size, SkijaUi.IconSet.LUCIDE);
      SkijaUi.icon(canvas, icon, centerX - width * 0.5F, centerY - size * 0.5F, size, color, size, SkijaUi.IconSet.LUCIDE);
   }

   private static void drawCenteredText(Canvas canvas, String text, float centerX, float centerY, int color, float size, boolean bold) {
      float width = bold ? SkijaUi.boldTextWidth(text, size) : SkijaUi.textWidth(text, size);
      if (bold) {
         SkijaUi.boldText(canvas, text, centerX - width * 0.5F, centerY - size * 0.5F, size, color, size);
      } else {
         SkijaUi.text(canvas, text, centerX - width * 0.5F, centerY - size * 0.5F, size, color, size);
      }

   }

   private static void drawRight(Canvas canvas, String text, float right, float y, float height, int color, float size) {
      SkijaUi.text(canvas, text, right - SkijaUi.textWidth(text, size), y, height, color, size);
   }

   private static String fit(String text, float maxWidth, float size, boolean bold) {
      if (text != null && !(maxWidth <= 0.0F)) {
         if ((bold ? SkijaUi.boldTextWidth(text, size) : SkijaUi.textWidth(text, size)) <= maxWidth) {
            return text;
         } else {
            for(String value = text; !value.isEmpty(); value = value.substring(0, value.length() - 1)) {
               String result = value + "...";
               float width = bold ? SkijaUi.boldTextWidth(result, size) : SkijaUi.textWidth(result, size);
               if (width <= maxWidth) {
                  return result;
               }
            }

            return "";
         }
      } else {
         return "";
      }
   }

   private static String trim(double value) {
      String text;
      for(text = String.format(Locale.ROOT, "%.3f", value); text.contains(".") && (text.endsWith("0") || text.endsWith(".")); text = text.substring(0, text.length() - 1)) {
      }

      return text;
   }

   private static String categoryTitle(Category category) {
      String var10000;
      switch (category) {
         case COMBAT -> var10000 = "Combat";
         case MISC -> var10000 = "Misc";
         case RENDER -> var10000 = "Render";
         case MOVEMENT -> var10000 = "Movement";
         case PLAYER -> var10000 = "Player";
         case CLIENT -> var10000 = "Client";
         case HUD -> var10000 = "HUD";
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private static String categoryIcon(Category category) {
      return CategoryGlyphs.forCategory(category);
   }

   private static int categoryColor(Category category) {
      return accent();
   }

   private static int accent() {
      Color color = ClickGui.INSTANCE.accent.get();
      return argb(255, color.getRed(), color.getGreen(), color.getBlue());
   }

   private int theme(int dark, int light) {
      return mix(dark, light, smooth(this.daylightProgress));
   }

   private int themePanel() {
      return this.theme(UiTheme.themedSurface(224), LIGHT_PANEL);
   }

   private int themePanelInner() {
      return this.theme(UiTheme.themedSurfaceAlt(205), LIGHT_PANEL_INNER);
   }

   private int themeText() {
      return this.theme(-788488, -14866386);
   }

   private int themeTextDim() {
      return this.theme(-6049868, -10917777);
   }

   private int themeTextFaint() {
      return this.theme(-9931140, -7168606);
   }

   private int themeTrack() {
      return this.theme(UiTheme.themedRaised(255), -3550248);
   }

   private int themeValue() {
      return this.theme(accent(), -14866386);
   }

   private static float distance(float firstX, float firstY, float secondX, float secondY) {
      return (float)Math.hypot((double)(firstX - secondX), (double)(firstY - secondY));
   }

   private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
      return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
   }

   private static float smooth(float value) {
      float clamped = clamp(value, 0.0F, 1.0F);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }

   private static float lerp(float start, float end, float amount) {
      return start + (end - start) * amount;
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private static int withAlpha(int color, int alpha) {
      return clamp(alpha, 0, 255) << 24 | color & 16777215;
   }

   private static int multiplyAlpha(int color, float factor) {
      return withAlpha(color, Math.round((float)(color >>> 24 & 255) * clamp(factor, 0.0F, 1.0F)));
   }

   private static int mix(int first, int second, float amount) {
      float value = clamp(amount, 0.0F, 1.0F);
      int a = Math.round((float)(first >>> 24 & 255) + (float)((second >>> 24 & 255) - (first >>> 24 & 255)) * value);
      int r = Math.round((float)(first >>> 16 & 255) + (float)((second >>> 16 & 255) - (first >>> 16 & 255)) * value);
      int g = Math.round((float)(first >>> 8 & 255) + (float)((second >>> 8 & 255) - (first >>> 8 & 255)) * value);
      int b = Math.round((float)(first & 255) + (float)((second & 255) - (first & 255)) * value);
      return argb(a, r, g, b);
   }

   private static int argb(int alpha, int red, int green, int blue) {
      return clamp(alpha, 0, 255) << 24 | clamp(red, 0, 255) << 16 | clamp(green, 0, 255) << 8 | clamp(blue, 0, 255);
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   static {
      CATEGORIES = new Category[]{Category.COMBAT, Category.MISC, Category.RENDER, Category.MOVEMENT, Category.PLAYER, Category.CLIENT};
      LIGHT_PANEL = argb(240, 248, 250, 252);
      LIGHT_PANEL_INNER = argb(224, 235, 240, 243);
      LIGHT_ROW_HOVER = argb(58, 82, 174, 214);
      RING_PAINT = (new Paint()).setAntiAlias(true).setMode(PaintMode.STROKE);
      SKIN_PAINT = (new Paint()).setAntiAlias(true);
      LOGO_PAINT = (new Paint()).setAntiAlias(true);
      PREVIEW_OUTLINE_PAINT = (new Paint()).setAntiAlias(true).setMode(PaintMode.STROKE);
   }

   private static record Bubble(float x, float y) {
   }

   private static enum EspPreviewPart {
      PLAYER,
      HEALTH,
      ARMOR,
      EQUIPMENT,
      EFFECTS;
   }

   private static record EspPreviewHit(EspPreviewPart part, float x, float y, float width, float height) {
   }

   private static record PopLayout(float screenWidth, float screenHeight, float panelWidth, float listHeight, float settingsHeight, float closedListX, float listX, float listY, float settingsX, float settingsY, float previewX, float previewY, float previewWidth, float previewHeight) {
   }
}
