package com.setsuna.ui.clickgui;

import com.setsuna.Setsuna;
import com.setsuna.config.ConfigManager;
import com.setsuna.i18n.LocalizedText;
import com.setsuna.i18n.TranslationKey;
import com.setsuna.manager.AltManager;
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
import com.setsuna.util.KeyBindText;
import com.setsuna.util.client.KeybindUtils;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public final class ClickGuiScreen extends Screen implements SkijaScreen {
   private static final float MODULE_ROW_HEIGHT = 44.0F;
   private static final float PANEL_CARD_GAP = 7.0F;
   private static final float PANEL_MODULE_INSET = 10.0F;
   private static final float SCROLL_STEP = 28.0F;
   private static final float SETTING_MARGIN = 8.0F;
   private static final float SETTING_COLUMN_GAP = 8.0F;
   private static final float TWO_COLUMN_SETTINGS_MIN_WIDTH = 300.0F;
   private static final float FONT_SMALL = 7.5F;
   private static final float FONT_NORMAL = 9.0F;
   private static final float FONT_MEDIUM = 10.0F;
   private static final float FONT_TITLE = 13.0F;
   private static final float PANEL_RADIUS = 9.0F;
   private static final float CONTROL_RADIUS = 6.0F;
   private static final float FIXED_RENDER_SCALE = 2.0F;
   private static final float ACCOUNT_HEIGHT = 48.0F;
   private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("setsuna", "textures/hud/watermark_logo.png");
   private static final int HAIRLINE = 620756991;
   private static final int HAIRLINE_STRONG = 989855743;
   private static final float SLIDER_FADE_WIDTH = 7.0F;
   private static final Paint SLIDER_PAINT;
   private static final Paint PANEL_LAYER_PAINT;
   private static final Paint ESP_SKIN_PAINT;
   private static final Paint ESP_OUTLINE_PAINT;
   private static final PanelMemory MEMORY;
   private Category selectedCategory;
   private Module selectedModule;
   private String search;
   private float moduleScroll;
   private float settingsScroll;
   private float configScroll;
   private boolean configsOpen;
   private boolean settingsPage;
   private String configName;
   private String configStatus;
   private boolean configStatusError;
   private boolean importVisualAndHud;
   private boolean importKeybinds;
   private int renderMouseX;
   private int renderMouseY;
   private float activeGuiScale;
   private float animationDelta;
   private float categoryHighlightY;
   private float moduleHighlightY;
   private float configHighlightY;
   private float visibilityProgress;
   private float categoryRevealProgress;
   private float settingsTransition;
   private String railTooltip;
   private float railTooltipY;
   private long lastFrame;
   private final Map<String, Float> transitions;
   private Setting<?> draggingNumber;
   private float draggingTrackX;
   private float draggingTrackWidth;
   private ColorSetting draggingColor;
   private int draggingColorChannel;
   private final List<PanelEspHit> panelEspHits;
   private PanelEspPart draggingPanelEsp;
   private float panelEspDragStartX;
   private float panelEspDragStartY;
   private double panelEspOffsetStartX;
   private double panelEspOffsetStartY;
   private float panelEspViewportX;
   private float panelEspViewportY;
   private float panelEspViewportWidth;
   private float panelEspViewportHeight;
   private float panelEspInfoScale;
   private KeybindSetting capturingKeybind;
   private Module capturingModule;
   private TextTarget textTarget;
   private StringSetting editingString;
   private ColorSetting editingColor;
   private Color colorBeforeEdit;
   private String editText;
   private int editCursor;
   private boolean selectAll;
   private final Screen parent;
   private boolean memoryRestored;
   private boolean closing;
   private boolean closeFinished;

   public ClickGuiScreen() {
      this((Screen)null);
   }

   public ClickGuiScreen(Screen parent) {
      super(Component.literal(tr("title", "Setsuna ClickGUI")));
      this.search = "";
      this.configName = "";
      this.configStatus = "";
      this.importVisualAndHud = true;
      this.importKeybinds = true;
      this.activeGuiScale = 1.0F;
      this.categoryHighlightY = Float.NaN;
      this.moduleHighlightY = Float.NaN;
      this.configHighlightY = Float.NaN;
      this.categoryRevealProgress = 1.0F;
      this.lastFrame = System.nanoTime();
      this.transitions = new HashMap();
      this.draggingColorChannel = -1;
      this.panelEspHits = new ArrayList();
      this.panelEspInfoScale = 1.0F;
      this.textTarget = ClickGuiScreen.TextTarget.NONE;
      this.editText = "";
      this.parent = parent;
   }

   protected void init() {
      this.activeGuiScale = this.configuredGuiScale();
      this.lastFrame = System.nanoTime();
      this.categoryHighlightY = Float.NaN;
      this.moduleHighlightY = Float.NaN;
      this.configHighlightY = Float.NaN;
      this.transitions.clear();
      this.draggingPanelEsp = null;
      this.panelEspHits.clear();
      if (!this.memoryRestored) {
         this.restoreMemory();
         this.memoryRestored = true;
      }

      this.settingsTransition = this.settingsPage ? 1.0F : 0.0F;
      this.ensureSelection();
      this.clampScrolls(this.currentLayout());
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      graphics.blurBeforeThisStratum();
      graphics.fill(0, 0, this.width, this.height, UiTheme.themedBackdrop(82));
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.renderMouseX = (int)this.logicalX((double)mouseX);
      this.renderMouseY = (int)this.logicalY((double)mouseY);
      if (SkijaRenderer.hasFailed()) {
         graphics.fill(0, 0, this.width, this.height, UiTheme.BACKDROP);
         graphics.text(this.font, "Skija renderer failed - check latest.log", 8, 8, UiTheme.TEXT, false);
      }

   }

   public void renderSkija(Canvas canvas) {
      this.ensureSelection();
      this.activeGuiScale = this.configuredGuiScale();
      long now = System.nanoTime();
      this.animationDelta = Math.min(0.05F, Math.max(0.0F, (float)(now - this.lastFrame) / 1.0E9F));
      this.lastFrame = now;
      this.visibilityProgress = approach(this.visibilityProgress, this.closing ? 0.0F : 1.0F, this.animationDelta, this.closing ? 25.0F : 18.0F);
      this.settingsTransition = approach(this.settingsTransition, this.settingsPage ? 1.0F : 0.0F, this.animationDelta, 15.0F);
      if (!this.completeCloseWhenReady()) {
         float visibility = easeOutCubic(this.visibilityProgress);
         int logicalWidth = this.logicalWidth();
         int logicalHeight = this.logicalHeight();
         ClickGuiLayout layout = this.currentLayout();
         this.clampScrolls(layout);
         canvas.save();
         float minecraftScale = this.minecraftGuiScale();
         canvas.scale(2.0F / minecraftScale, 2.0F / minecraftScale);
         int panelTransform = canvas.save();
         float centerX = (float)logicalWidth * 0.5F;
         float centerY = (float)logicalHeight * 0.5F;
         canvas.translate(centerX, centerY);
         float panelScale = this.panelRenderScale();
         canvas.scale(panelScale, panelScale);
         canvas.translate(-centerX, -centerY);
         PANEL_LAYER_PAINT.setAlpha(Math.max(0, Math.min(255, Math.round(255.0F * visibility))));
         int animationLayer = canvas.saveLayer(Rect.makeXYWH(layout.x() - 28.0F, layout.y() - 28.0F, layout.width() + 56.0F, layout.height() + 62.0F), PANEL_LAYER_PAINT);
         SkijaUi.dropShadowRounded(canvas, layout.x(), layout.y(), layout.width(), layout.height(), 9.0F, 5.0F, 20.0F, -1207959552);
         SkijaUi.rounded(canvas, layout.x(), layout.y(), layout.width(), layout.height(), 8.0F, UiTheme.BORDER);
         SkijaUi.rounded(canvas, layout.x() + 1.0F, layout.y() + 1.0F, Math.max(0.0F, layout.width() - 2.0F), Math.max(0.0F, layout.height() - 2.0F), 7.0F, UiTheme.SURFACE);
         canvas.save();
         canvas.clipRRect(RRect.makeXYWH(layout.x() + 1.0F, layout.y() + 1.0F, Math.max(0.0F, layout.width() - 2.0F), Math.max(0.0F, layout.height() - 2.0F), 8.0F), ClipMode.INTERSECT, true);

         try {
            this.drawColumnSurfaces(canvas, layout);
            this.renderTopBar(canvas, layout);
            this.renderCategoryRail(canvas, layout);
            if (this.configsOpen) {
               this.renderConfigWorkspace(canvas, layout);
            } else {
               this.renderPanelPageTransition(canvas, layout);
            }
         } finally {
            canvas.restore();
         }

         this.renderRailTooltip(canvas, layout);

         canvas.restoreToCount(animationLayer);
         PANEL_LAYER_PAINT.setAlpha(255);
         canvas.restoreToCount(panelTransform);
         canvas.restore();
      }
   }

   private void renderPanelPageTransition(Canvas canvas, ClickGuiLayout layout) {
      float transition = easeOutCubic(this.settingsTransition);
      if (!this.settingsPage && !(this.settingsTransition > 0.01F)) {
         this.renderModuleColumn(canvas, layout);
      } else {
         this.renderPanelPageLayer(canvas, layout, 1.0F - transition, false);
         this.renderPanelPageLayer(canvas, layout, transition, true);
      }

   }

   private void renderPanelPageLayer(Canvas canvas, ClickGuiLayout layout, float opacity, boolean settings) {
      if (!(opacity <= 0.005F)) {
         PANEL_LAYER_PAINT.setAlpha(Math.max(0, Math.min(255, Math.round(opacity * 255.0F))));
         int layer = canvas.saveLayer(Rect.makeXYWH(layout.x() + 1.0F, layout.bodyY(), layout.width() - 2.0F, Math.max(1.0F, layout.tabsY() - layout.bodyY())), PANEL_LAYER_PAINT);

         try {
            if (settings) {
               this.renderDetailColumn(canvas, layout);
            } else {
               this.renderModuleColumn(canvas, layout);
            }
         } finally {
            canvas.restoreToCount(layer);
         }

         PANEL_LAYER_PAINT.setAlpha(255);
      }
   }

   private void drawColumnSurfaces(Canvas canvas, ClickGuiLayout layout) {
      SkijaUi.fill(canvas, layout.x() + 1.0F, layout.bodyY(), layout.width() - 2.0F, Math.max(0.0F, layout.bodyHeight() - 1.0F), UiTheme.SURFACE);
      SkijaUi.fill(canvas, layout.x() + 1.0F, layout.tabsY() - 6.0F, layout.width() - 2.0F, layout.tabsHeight() + 13.0F, UiTheme.HEADER);
      SkijaUi.fill(canvas, layout.contentX(), layout.tabsY() - 6.0F, layout.contentWidth(), 1.0F, UiTheme.BORDER_SOFT);
   }

   private void renderTopBar(Canvas canvas, ClickGuiLayout layout) {
      float header = layout.headerHeight();
      SkijaUi.fill(canvas, layout.x() + 1.0F, layout.y() + 1.0F, layout.width() - 2.0F, header - 1.0F, UiTheme.HEADER);
      SkijaUi.fill(canvas, layout.contentX(), layout.bodyY() - 1.0F, layout.contentWidth(), 1.0F, UiTheme.BORDER_SOFT);
      this.renderSetsunaLogo(canvas, layout, header);
      if (layout.width() >= 360.0F) {
         SkijaUi.boldText(canvas, "Setsuna", layout.x() + 46.0F, layout.y(), header, UiTheme.TEXT, 11.0F);
      }
      this.renderTextField(canvas, topSearchX(layout), topSearchY(layout), topSearchWidth(layout), topSearchHeight(layout), this.search, this.textTarget == ClickGuiScreen.TextTarget.SEARCH, tr("search_modules", "Search modules"), 9.0F);
      if (layout.width() >= 360.0F) {
         float detailPad = columnPadding(layout.detailWidth(), 14.0F);
         long var10000 = enabledCount();
         String active = var10000 + " " + tr("active", "ACTIVE");
         float badgeWidth = SkijaUi.boldTextWidth(active, 7.5F) + 15.0F;
         float badgeX = layout.detailX() + layout.detailWidth() - detailPad - badgeWidth;
         SkijaUi.rounded(canvas, badgeX + 2.0F, layout.y() + header * 0.5F - 2.0F, 4.0F, 4.0F, 2.0F, UiTheme.SUCCESS);
         SkijaUi.boldText(canvas, active, badgeX + 12.0F, layout.y() + (header - 18.0F) * 0.5F, 18.0F, UiTheme.TEXT_MUTED, 7.5F);
      }

   }

   private void renderSetsunaLogo(Canvas canvas, ClickGuiLayout layout, float header) {
      float size = Math.min(25.0F, Math.max(18.0F, header - 13.0F));
      float x = layout.x() + 14.0F;
      float y = layout.y() + (header - size) * 0.5F;

      try {
         try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(LOGO_TEXTURE)) {
            if (borrowed != null) {
               Rect source = Rect.makeXYWH(0.0F, 0.0F, (float)borrowed.image().getWidth(), (float)borrowed.image().getHeight());
               Rect destination = Rect.makeXYWH(x, y, size, size);
               canvas.drawImageRect(borrowed.image(), source, destination);
               return;
            }
         }

      } catch (Throwable var12) {
      }
   }

   private void renderCategoryRail(Canvas canvas, ClickGuiLayout layout) {
      this.railTooltip = null;
      float tabY = layout.tabsY();
      float tabHeight = layout.tabsHeight();
      float rowX = layout.contentX();
      float rowWidth = layout.contentWidth();
      int count = Category.guiVisibleCount() + 1;
      float gap = 5.0F;
      float tabWidth = Math.max(1.0F, (rowWidth - gap * (float)(count - 1)) / (float)count);
      int selectedIndex = this.configsOpen ? Category.guiVisibleCount() : visibleCategoryIndex(this.selectedCategory);
      float targetX = selectedIndex < 0 ? rowX : rowX + (float)selectedIndex * (tabWidth + gap);
      this.categoryHighlightY = Float.isNaN(this.categoryHighlightY) ? targetX : approach(this.categoryHighlightY, targetX, this.animationDelta, 18.0F);
      SkijaUi.rounded(canvas, this.categoryHighlightY, tabY, tabWidth, tabHeight, 5.0F, UiTheme.withAlpha(UiTheme.accent(), 28));
      SkijaUi.rounded(canvas, this.categoryHighlightY + tabWidth * 0.3F, tabY + tabHeight - 3.0F, tabWidth * 0.4F, 2.0F, 1.0F, UiTheme.accent());
      int index = 0;

      for(Category category : Category.values()) {
         if (category.guiVisible()) {
            float tabX = rowX + (float)index * (tabWidth + gap);
            boolean selected = !this.configsOpen && category == this.selectedCategory;
            boolean hovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, tabX, tabY, tabWidth, tabHeight);
            if (hovered) {
               this.railTooltip = category.displayName();
               this.railTooltipY = tabY - 4.0F;
            }

            if (hovered && !selected) {
               SkijaUi.rounded(canvas, tabX, tabY, tabWidth, tabHeight, 6.0F, UiTheme.themedHover(184));
            }

            this.renderCategoryTab(canvas, category.displayName(), categoryIcon(category), tabX, tabY, tabWidth, tabHeight, selected);
            ++index;
         }
      }

      float configX = rowX + (float)index * (tabWidth + gap);
      boolean configHovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, configX, tabY, tabWidth, tabHeight);
      if (configHovered && !this.configsOpen) {
         this.railTooltip = tr("configs", "Configs");
         this.railTooltipY = tabY - 4.0F;
         SkijaUi.rounded(canvas, configX, tabY, tabWidth, tabHeight, 6.0F, UiTheme.themedHover(184));
      }

      this.renderCategoryTab(canvas, tr("configs", "Configs"), "\ue247", configX, tabY, tabWidth, tabHeight, this.configsOpen);
   }

   private void renderCategoryTab(Canvas canvas, String label, String icon, float x, float y, float width, float height, boolean selected) {
      float iconSize = 16.0F;
      boolean showLabel = width >= 52.0F;
      float iconX = x + (width - iconSize) * 0.5F;
      float iconY = showLabel ? y + 3.0F : y + (height - iconSize) * 0.5F;
      float glyphSize = 11.0F;
      float glyphWidth = SkijaUi.iconWidth(icon, glyphSize, SkijaUi.IconSet.LUCIDE);
      SkijaUi.icon(canvas, icon, iconX + (iconSize - glyphWidth) * 0.5F, iconY, iconSize, selected ? UiTheme.accent() : UiTheme.TEXT_MUTED, glyphSize, SkijaUi.IconSet.LUCIDE);
      if (!showLabel) return;
      String text = fitText(label, width - 6.0F, 7.5F, selected);
      float textWidth = selected ? SkijaUi.boldTextWidth(text, 7.5F) : SkijaUi.textWidth(text, 7.5F);
      if (selected) {
         SkijaUi.boldText(canvas, text, x + (width - textWidth) * 0.5F, y + height - 20.0F, 14.0F, UiTheme.TEXT, 7.5F);
      } else {
         SkijaUi.text(canvas, text, x + (width - textWidth) * 0.5F, y + height - 20.0F, 14.0F, UiTheme.TEXT_MUTED, 7.5F);
      }
   }

   private void renderRailTooltip(Canvas canvas, ClickGuiLayout layout) {
      if (this.railTooltip != null && !this.railTooltip.isBlank()) {
         float textWidth = SkijaUi.boldTextWidth(this.railTooltip, 7.5F);
         float width = textWidth + 12.0F;
         float height = 18.0F;
         float x = clamp(this.renderMouseX - width * 0.5F, layout.x() + 4.0F, layout.x() + layout.width() - width - 4.0F);
         float y = clamp(this.railTooltipY - height - 4.0F, layout.bodyY(), layout.tabsY() - height - 7.0F);
         SkijaUi.rounded(canvas, x, y, width, height, 6.0F, UiTheme.withAlpha(UiTheme.accent(), 190));
         SkijaUi.rounded(canvas, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, 5.0F, UiTheme.themedSurfaceAlt(240));
         SkijaUi.boldText(canvas, this.railTooltip, x + 6.0F, y, height, UiTheme.TEXT, 7.5F);
      }
   }

   private void renderAccountSummary(Canvas canvas, ClickGuiLayout layout) {
      float x = layout.x() + 9.0F;
      float y = accountY(layout) + 3.0F;
      float avatarSize = 28.0F;
      if (layout.railWidth() < 72.0F) {
         float avatarX = layout.x() + (layout.railWidth() - avatarSize) * 0.5F;
         SkijaUi.fill(canvas, layout.x() + 8.0F, y - 5.0F, Math.max(0.0F, layout.railWidth() - 16.0F), 1.0F, 620756991);
         this.drawCurrentAvatar(canvas, avatarX, y + 4.0F, avatarSize);
      } else {
         SkijaUi.fill(canvas, x, y - 5.0F, Math.max(0.0F, layout.railWidth() - 18.0F), 1.0F, 620756991);
         this.drawCurrentAvatar(canvas, x, y + 4.0F, avatarSize);
         float textX = x + avatarSize + 8.0F;
         float textWidth = Math.max(1.0F, layout.x() + layout.railWidth() - textX - 8.0F);
         String minecraftUsername = AltManager.INSTANCE.getCurrentUsername();
         SkijaUi.boldText(canvas, fitText(minecraftUsername, textWidth, 8.5F, true), textX, y + 3.0F, 15.0F, UiTheme.TEXT, 8.5F);
         SkijaUi.text(canvas, fitText(tr("local_profile", "Local profile"), textWidth, 7.0F, false), textX, y + 18.0F, 13.0F, UiTheme.TEXT_FAINT, 7.0F);
      }
   }

   private void drawCurrentAvatar(Canvas canvas, float x, float y, float size) {
      if (this.minecraft.player != null) {
         try {
            Identifier skin = this.minecraft.player.getSkin().body().texturePath();
            try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(skin)) {
               if (borrowed != null) {
                  float textureWidth = (float)borrowed.image().getWidth();
                  float textureHeight = (float)borrowed.image().getHeight();
                  Rect destination = Rect.makeXYWH(x, y, size, size);
                  canvas.save();

                  try {
                     canvas.clipRRect(RRect.makeXYWH(x, y, size, size, 6.0F), ClipMode.INTERSECT, true);
                     drawSkinLayer(canvas, borrowed, destination, textureWidth, textureHeight, 8.0F, 8.0F);
                     drawSkinLayer(canvas, borrowed, destination, textureWidth, textureHeight, 40.0F, 8.0F);
                     return;
                  } finally {
                     canvas.restore();
                  }
               }
            }
         } catch (Throwable var18) {
         }
      }

      String name = AltManager.INSTANCE.getCurrentUsername();
      String initial = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
      SkijaUi.rounded(canvas, x, y, size, size, 6.0F, UiTheme.withAlpha(UiTheme.accent(), 110));
      float textWidth = SkijaUi.boldTextWidth(initial, 12.0F);
      SkijaUi.boldText(canvas, initial, x + (size - textWidth) * 0.5F, y, size, UiTheme.TEXT, 12.0F);
   }

   private static void drawSkinLayer(Canvas canvas, SkijaRenderer.BorrowedImage borrowed, Rect destination, float textureWidth, float textureHeight, float sourceX, float sourceY) {
      Rect source = Rect.makeLTRB(sourceX / 64.0F * textureWidth, sourceY / 64.0F * textureHeight, (sourceX + 8.0F) / 64.0F * textureWidth, (sourceY + 8.0F) / 64.0F * textureHeight);
      canvas.drawImageRect(borrowed.image(), source, destination);
   }

   private void renderConfigWorkspace(Canvas canvas, ClickGuiLayout layout) {
      List<String> profiles = ConfigManager.INSTANCE.listProfiles();
      float contentX = layout.panelContentX();
      float contentWidth = layout.panelContentWidth();
      float columnGap = 14.0F;
      float listWidth = Math.max(1.0F, contentWidth * 0.26F);
      float listX = contentX;
      float listY = layout.bodyY() + 22.0F;
      float listHeight = Math.max(1.0F, layout.tabsY() - listY - 8.0F);
      float rowHeight = 25.0F;
      this.configScroll = ClickGuiLayout.clamp(this.configScroll, 0.0F, Math.max(0.0F, (float)profiles.size() * rowHeight - listHeight));
      SkijaUi.boldText(canvas, tr("profiles", "PROFILES"), contentX, layout.bodyY() + 1.0F, 22.0F, UiTheme.TEXT_MUTED, 7.5F);
      canvas.save();
      canvas.clipRect(Rect.makeXYWH(contentX, listY, listWidth, listHeight));
      float rowY = listY - this.configScroll;
      String selectedName = this.selectedConfigName();
      int selectedIndex = -1;

      for(int index = 0; index < profiles.size(); ++index) {
         if (((String)profiles.get(index)).equalsIgnoreCase(selectedName)) {
            selectedIndex = index;
            break;
         }
      }

      if (selectedIndex >= 0) {
         float targetY = listY + (float)selectedIndex * rowHeight - this.configScroll + 2.0F;
         this.configHighlightY = Float.isNaN(this.configHighlightY) ? targetY : approach(this.configHighlightY, targetY, this.animationDelta, 16.0F);
         SkijaUi.rounded(canvas, contentX, this.configHighlightY, listWidth, rowHeight - 4.0F, 6.0F, UiTheme.withAlpha(UiTheme.accent(), 96));
      }

      for(String profile : profiles) {
         boolean selected = profile.equalsIgnoreCase(selectedName);
         boolean hovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, listX, rowY, listWidth, rowHeight);
         if (hovered && !selected) {
            SkijaUi.rounded(canvas, listX, rowY + 2.0F, listWidth, rowHeight - 4.0F, 6.0F, UiTheme.themedHover(184));
         }

         ClickGuiText.drawVerticallyCentered(canvas, profile, listX + 8.0F, rowY, listWidth - 18.0F, rowHeight, 10.0F, selected ? UiTheme.TEXT : (hovered ? UiTheme.TEXT : UiTheme.TEXT_MUTED), 9.0F, selected);
         SkijaUi.fill(canvas, listX + 8.0F, rowY + rowHeight - 1.0F, Math.max(0.0F, listWidth - 16.0F), 1.0F, 620756991);
         rowY += rowHeight;
      }

      canvas.restore();
      this.renderScrollbar(canvas, listX + listWidth - 2.0F, listY, listHeight, (float)profiles.size() * rowHeight, this.configScroll);
      float x = listX + listWidth + columnGap;
      float width = Math.max(1.0F, contentX + contentWidth - x);
      float y = layout.bodyY() + 1.0F;
      SkijaUi.boldText(canvas, tr("config_manager", "Config Manager"), x, y, 18.0F, UiTheme.TEXT, 13.0F);
      float inputY = y + 21.0F;
      String input = this.textTarget == ClickGuiScreen.TextTarget.CONFIG ? this.editText : this.configName;
      this.renderTextField(canvas, x, inputY, width, 24.0F, input, this.textTarget == ClickGuiScreen.TextTarget.CONFIG, tr("enter_profile_name", "Enter profile name"), 9.0F);
      float buttonY = inputY + 30.0F;
      float gap = 5.0F;
      float buttonWidth = Math.max(34.0F, (width - gap * 2.0F) / 3.0F);
      this.renderConfigButton(canvas, x, buttonY, buttonWidth, tr("save", "Save"), true);
      this.renderConfigButton(canvas, x + buttonWidth + gap, buttonY, buttonWidth, tr("load", "Load"), false);
      this.renderConfigButton(canvas, x + (buttonWidth + gap) * 2.0F, buttonY, buttonWidth, tr("delete", "Delete"), false);
      float utilityY = buttonY + 28.0F;
      float utilityWidth = Math.max(1.0F, (width - gap) / 2.0F);
      this.renderConfigButton(canvas, x, utilityY, utilityWidth, tr("open_folder", "Open Folder"), false);
      this.renderConfigButton(canvas, x + utilityWidth + gap, utilityY, utilityWidth, tr("refresh", "Refresh"), false);
      float optionY = utilityY + 27.0F;
      this.renderConfigImportOption(canvas, x, optionY, width, "Visual & HUD", this.importVisualAndHud, "config:visual-hud");
      this.renderConfigImportOption(canvas, x, optionY + 22.0F, width, "Keybinds", this.importKeybinds, "config:keybinds");
      if (!this.configStatus.isBlank()) {
         ClickGuiText.draw(canvas, this.configStatus, x, optionY + 46.0F, width, 9.0F, this.configStatusError ? UiTheme.DANGER : UiTheme.TEXT_MUTED, 7.5F, false);
      }

   }

   private void renderConfigImportOption(Canvas canvas, float x, float y, float width, String label, boolean enabled, String key) {
      boolean hovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, x, y, width, 18.0F);
      SkijaUi.rounded(canvas, x, y, width, 18.0F, 6.0F, hovered ? UiTheme.themedHover(184) : UiTheme.themedSurface(234));
      SkijaUi.text(canvas, label, x + 7.0F, y, 18.0F, hovered ? UiTheme.TEXT : UiTheme.TEXT_MUTED, 7.5F);
      this.renderToggle(canvas, x + width - 31.0F, y + 3.0F, enabled, hovered, key);
   }

   private void renderConfigButton(Canvas canvas, float x, float y, float width, String label, boolean primary) {
      boolean hovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, x, y, width, 24.0F);
      this.drawControl(canvas, x, y, width, 24.0F, hovered, primary, "config:" + label);
      ClickGuiText.drawCentered(canvas, label, x + 4.0F, y, width - 8.0F, 24.0F, 9.0F, !primary && !hovered ? UiTheme.TEXT_MUTED : UiTheme.TEXT, 7.5F, true);
   }

   private void renderModuleColumn(Canvas canvas, ClickGuiLayout layout) {
      List<Module> modules = this.filteredModules();
      float listY = layout.moduleListY();
      float listHeight = layout.moduleListHeight();
      float headerX = layout.panelContentX();
      float headerWidth = layout.panelContentWidth();
      float contentX = headerX;
      float contentWidth = headerWidth;
      float cardWidth = layout.moduleCardWidth();
      float reveal = this.categoryRevealProgress = approach(this.categoryRevealProgress, 1.0F, this.animationDelta, 12.0F);
      float contentHeight = layout.moduleContentHeight(modules.size());
      this.moduleScroll = ClickGuiLayout.clamp(this.moduleScroll, 0.0F, Math.max(0.0F, contentHeight - listHeight));
      String categoryName = this.selectedCategory == null ? tr("modules", "Modules") : this.selectedCategory.displayName();
      ClickGuiText.draw(canvas, categoryName, contentX, layout.bodyY() + 2.0F, contentWidth - 60.0F, 22.0F, UiTheme.TEXT, 13.0F, true);
      int var10000 = modules.size();
      String count = var10000 + " " + tr("modules", "modules");
      SkijaUi.text(canvas, count, contentX + contentWidth - SkijaUi.textWidth(count, 7.5F), layout.bodyY() + 5.0F, 18.0F, UiTheme.TEXT_FAINT, 7.5F);
      canvas.save();
      canvas.clipRect(Rect.makeXYWH(contentX, listY, contentWidth, listHeight));
      if (modules.isEmpty()) {
         SkijaUi.text(canvas, this.search.isEmpty() ? tr("no_modules", "No modules") : tr("no_matches", "No matches"), contentX, listY + 8.0F, 20.0F, UiTheme.TEXT_FAINT, 9.0F);
      }

      for(int index = 0; index < modules.size(); ++index) {
         float x = layout.moduleCardX(index);
         float y = layout.moduleCardY(index, this.moduleScroll);
         if (y + 50.0F >= listY && y < listY + listHeight) {
            this.renderPanelModuleCard(canvas, modules.get(index), x, y, cardWidth, 50.0F, reveal);
         }
      }

      canvas.restore();
      this.renderScrollbar(canvas, contentX + contentWidth - 3.0F, listY, listHeight, contentHeight, this.moduleScroll);
   }

   private void renderPanelModuleCard(Canvas canvas, Module module, float x, float y, float width, float height, float reveal) {
      boolean hovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, x, y, width, height);
      float hover = this.animate("card:hover:" + module.id(), hovered ? 1.0F : 0.0F);
      SkijaUi.rounded(canvas, x, y, width, height, 6.0F, UiTheme.SURFACE_ALT);
      SkijaUi.rounded(canvas, x, y, width, height, 6.0F, UiTheme.withAlpha(UiTheme.TEXT, Math.round(hover * 8.0F)));
      SkijaUi.outline(canvas, x + 0.5F, y + 0.5F, width - 1.0F, height - 1.0F, 6.0F, 0.75F,
            module.isEnabled() ? UiTheme.withAlpha(UiTheme.accent(), Math.round(100.0F + hover * 65.0F)) : UiTheme.BORDER_SOFT);

      int alpha = Math.round(255.0F * reveal);
      String name = module.displayName();
      int nameColor = module.isEnabled() ? UiTheme.TEXT : UiTheme.TEXT_MUTED;
      nameColor = UiTheme.withAlpha(nameColor, alpha);
      SkijaUi.boldText(canvas, fitText(name, Math.max(1.0F, width - 51.0F), 9.0F, true), x + 10.0F, y + 6.0F, 17.0F, nameColor, 9.0F);
      String state = module.getInfo();
      if (state != null && !state.isBlank()) {
         state = LocalizedText.freeform(state);
      } else {
         state = module.isToggleable() ? (module.isEnabled() ? trState("enabled", "Enabled") : trState("disabled", "Disabled")) : tr("action", "Action");
      }

      SkijaUi.text(canvas, fitText(state, Math.max(1.0F, width - 38.0F), 7.5F, false), x + 10.0F, y + 27.0F, 13.0F, UiTheme.withAlpha(module.isEnabled() ? UiTheme.accent() : UiTheme.TEXT_FAINT, alpha), 7.5F);
      if (module.isToggleable()) {
         this.renderToggle(canvas, x + width - 36.0F, y + 9.0F, module.isEnabled(), hovered, "card:" + module.id());
      } else {
         SkijaUi.icon(canvas, "\ue13c", x + width - 23.0F, y + 9.0F, 14.0F, UiTheme.TEXT_MUTED, 10.0F, SkijaUi.IconSet.LUCIDE);
      }
      SkijaUi.icon(canvas, "\ue154", x + width - 21.0F, y + 29.0F, 12.0F, hovered ? UiTheme.TEXT : UiTheme.TEXT_FAINT, 8.0F, SkijaUi.IconSet.LUCIDE);
      if (ClickGuiLayout.contains(this.renderMouseX, this.renderMouseY, x + width - 29.0F, y + 26.0F, 27.0F, 22.0F)) {
         this.railTooltip = tr("settings", "Settings");
         this.railTooltipY = y + 25.0F;
      }

   }

   private void renderDetailColumn(Canvas canvas, ClickGuiLayout layout) {
      float x = layout.panelContentX();
      float width = layout.panelContentWidth();
      float bodyY = layout.bodyY() + 28.0F;
      float padding = columnPadding(width, 14.0F);
      float backX = x + 10.0F;
      boolean backHovered = ClickGuiLayout.contains(this.renderMouseX, this.renderMouseY, backX, layout.bodyY(), 52.0F, 22.0F);
      if (backHovered) SkijaUi.rounded(canvas, backX, layout.bodyY(), 52.0F, 22.0F, 4.0F, UiTheme.SURFACE_HOVER);
      SkijaUi.icon(canvas, "\ue048", backX + 6.0F, layout.bodyY() + 4.0F, 14.0F, UiTheme.TEXT_MUTED, 9.0F, SkijaUi.IconSet.LUCIDE);
      SkijaUi.text(canvas, tr("back", "Back"), backX + 22.0F, layout.bodyY() + 2.0F, 18.0F, UiTheme.TEXT_MUTED, 7.5F);
      if (this.selectedModule == null) return;

      float controlWidth = this.selectedModule.isToggleable() ? 28.0F : 24.0F;
      float controlX = x + width - padding - controlWidth;
      String title = fitText(this.selectedModule.displayName(), controlX - x - padding - 12.0F, 13.0F, true);
      SkijaUi.boldText(canvas, title, x + padding, bodyY + 4.0F, 18.0F, UiTheme.TEXT, 13.0F);
      SkijaUi.text(canvas, this.selectedModule.category().displayName(), x + padding, bodyY + 25.0F, 11.0F, UiTheme.TEXT_FAINT, 7.5F);
      boolean hovered = ClickGuiLayout.contains(this.renderMouseX, this.renderMouseY, controlX - 3.0F, bodyY + 7.0F, controlWidth + 6.0F, 26.0F);
      if (this.selectedModule.isToggleable()) {
         this.renderToggle(canvas, controlX, bodyY + 12.0F, this.selectedModule.isEnabled(), hovered, "module-detail:" + this.selectedModule.id());
      } else {
         this.drawControl(canvas, controlX, bodyY + 8.0F, controlWidth, 22.0F, hovered, true, "module-open:" + this.selectedModule.id());
         SkijaUi.icon(canvas, "\ue13c", controlX + 6.0F, bodyY + 12.0F, 14.0F, UiTheme.TEXT, 10.0F, SkijaUi.IconSet.LUCIDE);
         if (hovered) {
            this.railTooltip = tr("open", "Open");
            this.railTooltipY = bodyY + 7.0F;
         }
      }

      float bindWidth = this.moduleBindWidth(width, padding);
      float bindY = bodyY + 42.0F;
      boolean bindHovered = ClickGuiLayout.contains(this.renderMouseX, this.renderMouseY, x + padding, bindY, bindWidth, 18.0F);
      this.drawControl(canvas, x + padding, bindY, bindWidth, 18.0F, bindHovered,
            this.capturingModule == this.selectedModule, "module-bind:" + this.selectedModule.id());
      SkijaUi.text(canvas, fitText(this.moduleBindText(), bindWidth - 12.0F, 7.5F, false),
            x + padding + 6.0F, bindY, 18.0F, UiTheme.TEXT_MUTED, 7.5F);
      String count = this.visibleSettings().size() + " " + tr("settings", "Settings");
      count = fitText(count, width - padding * 2.0F - bindWidth - 10.0F, 7.5F, false);
      SkijaUi.text(canvas, count, x + width - padding - SkijaUi.textWidth(count, 7.5F),
            bindY, 18.0F, UiTheme.TEXT_FAINT, 7.5F);
      SkijaUi.fill(canvas, x, layout.settingsY() + 27.0F, width, 1.0F, UiTheme.BORDER_SOFT);
      this.renderSettings(canvas, layout);
   }

   private String moduleBindText() {
      return this.capturingModule == this.selectedModule ? tr("press_key", "Press a key")
            : tr("key", "KEY") + "  " + keyTextOrNone(this.selectedModule.keyBind());
   }

   private float moduleBindWidth(float width, float padding) {
      return Math.min(Math.max(44.0F, SkijaUi.textWidth(this.moduleBindText(), 7.5F) + 16.0F),
            Math.max(44.0F, width - padding * 2.0F - 64.0F));
   }

   private void renderSettings(Canvas canvas, ClickGuiLayout layout) {
      List<Setting<?>> settings = this.visibleSettings();
      float viewY = this.panelSettingsY(layout);
      float viewHeight = this.panelSettingsHeight(layout);
      float x = this.panelSettingsX(layout);
      float w = this.panelSettingsWidth(layout);
      this.renderPanelEspPreview(canvas, layout);
      canvas.save();
      canvas.clipRect(Rect.makeXYWH(x + 1.0F, viewY, Math.max(0.0F, w - 1.0F), viewHeight));
      float contentX = x + 8.0F;
      float contentWidth = Math.max(1.0F, w - 16.0F);
      int columns = settingColumns(contentWidth);
      float columnWidth = settingColumnWidth(contentWidth, columns);
      float rowY = viewY - this.settingsScroll;
      if (settings.isEmpty()) {
         SkijaUi.text(canvas, tr("no_settings", "No settings available"), x + 12.0F, viewY + 10.0F, 20.0F, UiTheme.TEXT_FAINT, 9.0F);
      }

      for(int index = 0; index < settings.size(); index += columns) {
         float rowHeight = settingRowHeight(settings, index, columns);

         for(int column = 0; column < columns && index + column < settings.size(); ++column) {
            Setting<?> setting = (Setting)settings.get(index + column);
            float settingX = contentX + (float)column * (columnWidth + 8.0F);
            this.renderSetting(canvas, setting, settingX, rowY, columnWidth, Math.max(1.0F, settingHeight(setting)));
         }

         rowY += rowHeight;
      }

      if (columns == 2) {
         float dividerX = contentX + columnWidth + 4.0F;
         SkijaUi.fill(canvas, dividerX - 0.5F, viewY + 7.0F, 1.0F, Math.max(1.0F, viewHeight - 14.0F), 620756991);
      }

      canvas.restore();
      this.renderScrollbar(canvas, x + w - 3.0F, viewY, viewHeight, settingsContentHeight(settings, contentWidth), this.settingsScroll);
   }

   private float panelEspAreaHeight(ClickGuiLayout layout) {
      if (!this.hasPanelPreview()) {
         return 0.0F;
      } else {
         float available = Math.max(0.0F, layout.tabsY() - layout.settingsY() - 36.0F);
         return this.usesSidePreview(layout) || available < 150.0F ? 0.0F : Math.min(138.0F, available * 0.5F);
      }
   }

   private boolean hasPanelPreview() {
      return this.selectedModule == ESP.INSTANCE || this.selectedModule == TargetHud.INSTANCE || this.selectedModule == KillEffect.INSTANCE || this.selectedModule == ChestESP.INSTANCE || this.selectedModule == ArmorTrimOverride.INSTANCE || this.selectedModule == EquipmentDisguise.INSTANCE;
   }

   private boolean usesSidePreview(ClickGuiLayout layout) {
      return this.hasPanelPreview() && layout.panelContentWidth() >= 360.0F;
   }

   private float panelPreviewWidth(ClickGuiLayout layout) {
      return Math.min(220.0F, Math.max(148.0F, layout.panelContentWidth() * 0.34F));
   }

   private float panelSettingsX(ClickGuiLayout layout) {
      float baseX = layout.panelContentX();
      return this.usesSidePreview(layout) ? baseX + this.panelPreviewWidth(layout) + 12.0F : baseX;
   }

   private float panelSettingsWidth(ClickGuiLayout layout) {
      float baseWidth = layout.panelContentWidth();
      return this.usesSidePreview(layout) ? Math.max(1.0F, baseWidth - this.panelPreviewWidth(layout) - 12.0F) : baseWidth;
   }

   private float panelSettingsY(ClickGuiLayout layout) {
      float base = layout.settingsY() + 28.0F;
      return base + this.panelEspAreaHeight(layout);
   }

   private float panelSettingsHeight(ClickGuiLayout layout) {
      float base = layout.settingsY() + 28.0F;
      return Math.max(0.0F, layout.tabsY() - base - this.panelEspAreaHeight(layout) - 8.0F);
   }

   private void renderPanelEspPreview(Canvas canvas, ClickGuiLayout layout) {
      this.panelEspHits.clear();
      if (!this.usesSidePreview(layout) && this.panelEspAreaHeight(layout) <= 0.0F) return;
      boolean espPreview = this.selectedModule == ESP.INSTANCE;
      boolean chestPreview = this.selectedModule == ChestESP.INSTANCE;
      boolean targetHudPreview = this.selectedModule == TargetHud.INSTANCE;
      boolean armorTrimPreview = this.selectedModule == ArmorTrimOverride.INSTANCE;
      boolean equipmentPreview = this.selectedModule == EquipmentDisguise.INSTANCE;
      if (espPreview || chestPreview || targetHudPreview || armorTrimPreview || equipmentPreview || this.selectedModule == KillEffect.INSTANCE) {
         float baseX = layout.panelContentX();
         float baseWidth = layout.panelContentWidth();
         boolean sidePreview = this.usesSidePreview(layout);
         float x = baseX + 8.0F;
         float y = layout.settingsY() + 35.0F;
         float width = sidePreview ? Math.max(1.0F, this.panelPreviewWidth(layout) - 8.0F) : Math.max(1.0F, baseWidth - 16.0F);
         float height = sidePreview ? Math.max(1.0F, layout.tabsY() - y - 8.0F) : Math.max(1.0F, this.panelEspAreaHeight(layout) - 14.0F);
         SkijaUi.rounded(canvas, x, y, width, height, 6.0F, UiTheme.themedSurfaceAlt(182));
         SkijaUi.outline(canvas, x, y, width, height, 6.0F, 1.0F, UiTheme.withAlpha(UiTheme.accent(), 100));
         String previewTitle = equipmentPreview ? "EQUIPMENT PREVIEW" : espPreview ? "ESP PREVIEW" : (chestPreview ? "CHEST ESP PREVIEW" : (targetHudPreview ? "TARGET HUD PREVIEW" : (armorTrimPreview ? "ARMOR TRIM PREVIEW" : "KILL EFFECT PREVIEW")));
         SkijaUi.boldText(canvas, fitText(previewTitle, width - 20.0F, 7.5F, true), x + 10.0F, y, 25.0F, UiTheme.TEXT, 7.5F);
         SkijaUi.fill(canvas, x + 10.0F, y + 24.0F, width - 20.0F, 1.0F, UiTheme.withAlpha(UiTheme.accent(), 72));
         this.panelEspViewportX = x + 6.0F;
         this.panelEspViewportY = y + 29.0F;
         this.panelEspViewportWidth = width - 12.0F;
         this.panelEspViewportHeight = height - 35.0F;
         SkijaUi.rounded(canvas, this.panelEspViewportX, this.panelEspViewportY, this.panelEspViewportWidth, this.panelEspViewportHeight, 4.0F, UiTheme.themedSurface(158));
         canvas.save();
         canvas.clipRect(Rect.makeXYWH(this.panelEspViewportX, this.panelEspViewportY, this.panelEspViewportWidth, this.panelEspViewportHeight));
         if (!espPreview && !chestPreview) {
            if (targetHudPreview) {
               TargetHud.INSTANCE.renderPreview(canvas, this.panelEspViewportX, this.panelEspViewportY, this.panelEspViewportWidth, this.panelEspViewportHeight);
            } else if (armorTrimPreview) {
               ArmorTrimOverride.INSTANCE.renderPreview(canvas, this.panelEspViewportX, this.panelEspViewportY, this.panelEspViewportWidth, this.panelEspViewportHeight);
            } else if (equipmentPreview) {
               EquipmentDisguise.INSTANCE.renderPreview(canvas, this.panelEspViewportX, this.panelEspViewportY, this.panelEspViewportWidth, this.panelEspViewportHeight);
            } else {
               KillEffect.INSTANCE.renderPreview(canvas, this.panelEspViewportX, this.panelEspViewportY, this.panelEspViewportWidth, this.panelEspViewportHeight);
            }
         } else {
            this.drawPanelEspScene(canvas);
            if (espPreview) {
               this.drawPanelEspHover(canvas);
            }
         }

         canvas.restore();
      }
   }

   private void drawPanelEspScene(Canvas canvas) {
      if (this.selectedModule == ChestESP.INSTANCE) {
         this.drawPanelChest(canvas);
      } else {
         float width = this.panelEspViewportWidth;
         float height = this.panelEspViewportHeight;
         float scale = Math.max(1.0F, Math.min((width - 88.0F) / 16.0F, (height - 14.0F) / 32.0F));
         float figureWidth = 16.0F * scale;
         float figureHeight = 32.0F * scale;
         float figureX = this.panelEspViewportX + width * 0.47F - figureWidth * 0.5F;
         float figureY = this.panelEspViewportY + (height - figureHeight) * 0.5F;
         boolean skinDrawn = false;
         if (this.minecraft.player != null && this.minecraft.player.getSkin() != null && this.minecraft.player.getSkin().body() != null) {
            try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(this.minecraft.player.getSkin().body().texturePath())) {
               if (borrowed != null) {
                  drawPanelSkin(canvas, borrowed.image(), figureX, figureY, scale);
                  skinDrawn = true;
               }
            } catch (Throwable var21) {
            }
         }

         if (!skinDrawn) {
            drawPanelFallbackPlayer(canvas, figureX, figureY, scale);
         }

         if ((Boolean)ESP.INSTANCE.players.get()) {
            float inset = Math.max(2.0F, scale * 0.7F);
            float left = figureX - inset;
            float top = figureY - inset;
            float boxWidth = figureWidth + inset * 2.0F;
            float boxHeight = figureHeight + inset * 2.0F;
            int color = ESP.INSTANCE.playerColor.argb();
            switch ((ESP.PlayerMode)ESP.INSTANCE.playerMode.get()) {
               case CSGO:
                  this.drawPanelCsgo(canvas, left, top, boxWidth, boxHeight, color);
                  break;
               case Outline:
                  drawPanelPlayerOutline(canvas, figureX, figureY, scale, color);
                  break;
               case Box2D:
                  float boxX = left + ((Double)ESP.INSTANCE.playerOffsetX.get()).floatValue();
                  float boxY = top + ((Double)ESP.INSTANCE.playerOffsetY.get()).floatValue();
                  float radius = Math.min(((Double)ESP.INSTANCE.box2dCornerRadius.get()).floatValue(), Math.min(boxWidth, boxHeight) * 0.5F);
                  if ((Boolean)ESP.INSTANCE.box2dBackground.get()) {
                     SkijaUi.rounded(canvas, boxX, boxY, boxWidth, boxHeight, radius, UiTheme.withAlpha(ESP.INSTANCE.box2dBackgroundColor.argb(), (Integer)ESP.INSTANCE.box2dBackgroundOpacity.get()));
                  }

                  drawPanelRoundedBox(canvas, boxX, boxY, boxWidth, boxHeight, radius, color);
                  this.addPanelEspHit(ClickGuiScreen.PanelEspPart.PLAYER, boxX - 2.0F, boxY - 2.0F, boxWidth + 4.0F, boxHeight + 4.0F);
                  break;
               case Box3D:
                  drawPanel3DBox(canvas, left, top, boxWidth, boxHeight, color);
                  break;
               case Cylinder:
                  drawPanelCylinder(canvas, left, top, boxWidth, boxHeight, color);
            }

            this.panelEspInfoScale = ((Double)ESP.INSTANCE.infoScale.get()).floatValue();
            this.drawPanelEquipment(canvas, left, top, boxWidth, this.panelEspInfoScale);
            this.drawPanelEffects(canvas, left, top, boxWidth, this.panelEspInfoScale);
         }
      }
   }

   private void drawPanelCsgo(Canvas canvas, float x, float y, float width, float height, int color) {
      if ((Boolean)ESP.INSTANCE.playerBox.get()) {
         float boxX = x + ((Double)ESP.INSTANCE.playerOffsetX.get()).floatValue();
         float boxY = y + ((Double)ESP.INSTANCE.playerOffsetY.get()).floatValue();
         drawPanelBox(canvas, boxX, boxY, width, height, color);
         this.addPanelEspHit(ClickGuiScreen.PanelEspPart.PLAYER, boxX - 2.0F, boxY - 2.0F, width + 4.0F, height + 4.0F);
      }

      if ((Boolean)ESP.INSTANCE.healthBar.get()) {
         float barX = x - 7.0F + ((Double)ESP.INSTANCE.healthOffsetX.get()).floatValue();
         float barY = y + ((Double)ESP.INSTANCE.healthOffsetY.get()).floatValue();
         SkijaUi.fill(canvas, barX, barY, 4.0F, height, -16777216);
         SkijaUi.fill(canvas, barX + 1.0F, barY + 1.0F, 2.0F, height - 2.0F, -14406866);
         float fillHeight = (height - 2.0F) * 0.72F;
         SkijaUi.fill(canvas, barX + 1.0F, barY + height - 1.0F - fillHeight, 2.0F, fillHeight, -8527009);
         this.addPanelEspHit(ClickGuiScreen.PanelEspPart.HEALTH, barX - 3.0F, barY, 10.0F, height);
      }

      if ((Boolean)ESP.INSTANCE.armorBar.get()) {
         float barX = x + width + 3.0F + ((Double)ESP.INSTANCE.armorOffsetX.get()).floatValue();
         float barY = y + ((Double)ESP.INSTANCE.armorOffsetY.get()).floatValue();
         float gap = 1.0F;
         float segmentHeight = (height - gap * 3.0F) / 4.0F;

         for(int i = 0; i < 4; ++i) {
            float segmentY = barY + (float)i * (segmentHeight + gap);
            SkijaUi.fill(canvas, barX, segmentY, 4.0F, segmentHeight, -16777216);
            float fillHeight = Math.max(1.0F, (segmentHeight - 2.0F) * (1.0F - (float)i * 0.12F));
            SkijaUi.fill(canvas, barX + 1.0F, segmentY + segmentHeight - 1.0F - fillHeight, 2.0F, fillHeight, -11612443);
         }

         this.addPanelEspHit(ClickGuiScreen.PanelEspPart.ARMOR, barX - 2.0F, barY, 8.0F, height);
      }

   }

   private void drawPanelEquipment(Canvas canvas, float boxX, float boxY, float boxWidth, float previewScale) {
      if ((Boolean)ESP.INSTANCE.showEquipment.get()) {
         int count = (Boolean)ESP.INSTANCE.showHands.get() ? 6 : 4;
         float itemSize = 8.0F;
         float gap = 1.5F;
         float rowWidth = (float)count * itemSize + (float)(count - 1) * gap;
         float x = -rowWidth * 0.5F + ((Double)ESP.INSTANCE.equipmentOffsetX.get()).floatValue();
         float y = -itemSize - 4.0F + ((Double)ESP.INSTANCE.equipmentOffsetY.get()).floatValue();
         float anchorX = boxX + boxWidth * 0.5F;
         canvas.save();
         canvas.translate(anchorX, boxY);
         canvas.scale(previewScale, previewScale);
         int[] colors = new int[]{-3549731, -7419941, -9401435, -10127989, -6520861, -2629145};

         for(int i = 0; i < count; ++i) {
            float itemX = x + (float)i * (itemSize + gap);
            SkijaUi.rounded(canvas, itemX, y, itemSize, itemSize, 1.5F, UiTheme.themedSurfaceAlt(192));
            float inset = i != 0 && i != count - 1 ? 1.7F : 2.2F;
            SkijaUi.rounded(canvas, itemX + inset, y + inset, itemSize - inset * 2.0F, itemSize - inset * 2.0F, 0.8F, colors[i]);
         }

         canvas.restore();
         this.addPanelEspHit(ClickGuiScreen.PanelEspPart.EQUIPMENT, anchorX + (x - 2.0F) * previewScale, boxY + (y - 2.0F) * previewScale, (rowWidth + 4.0F) * previewScale, (itemSize + 4.0F) * previewScale);
      }
   }

   private void drawPanelEffects(Canvas canvas, float boxX, float boxY, float boxWidth, float previewScale) {
      if ((Boolean)ESP.INSTANCE.showEffects.get()) {
         String[] effects = new String[]{"Speed II 1:24", "Strength I 0:48"};
         int[] colors = new int[]{-8599822, -1018764};
         float textSize = 5.8F;
         float rowHeight = 8.0F;
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
            float rowY = y + (float)i * 9.0F;
            SkijaUi.fill(canvas, left, rowY, rowWidth, rowHeight, ESP.INSTANCE.infoBackgroundColor.argb());
            SkijaUi.text(canvas, effects[i], left + 2.5F, rowY, rowHeight, colors[i], textSize);
            unionLeft = Math.min(unionLeft, left);
            unionRight = Math.max(unionRight, left + rowWidth);
         }

         canvas.restore();
         this.addPanelEspHit(ClickGuiScreen.PanelEspPart.EFFECTS, anchorX + (unionLeft - 2.0F) * previewScale, boxY + (y - 2.0F) * previewScale, (unionRight - unionLeft + 4.0F) * previewScale, 21.0F * previewScale);
      }
   }

   private void drawPanelChest(Canvas canvas) {
      boolean drawChest = (Boolean)ChestESP.INSTANCE.chests.get();
      boolean drawEnder = (Boolean)ChestESP.INSTANCE.enderChests.get();
      if (drawChest || drawEnder) {
         float width = 24.0F;
         float height = 16.0F;
         float gap = 13.0F;
         float totalWidth = drawChest && drawEnder ? width * 2.0F + gap : width;
         float x = this.panelEspViewportX + (this.panelEspViewportWidth - totalWidth) * 0.5F;
         float y = this.panelEspViewportY + (this.panelEspViewportHeight - height) * 0.5F;
         if (drawChest) {
            drawPanelChestIcon(canvas, x, y, width, height, ChestESP.INSTANCE.chestColor.argb());
         }

         if (drawEnder) {
            float enderX = drawChest ? x + width + gap : x;
            drawPanelChestIcon(canvas, enderX, y, width, height, ChestESP.INSTANCE.enderChestColor.argb());
         }

      }
   }

   private static void drawPanelChestIcon(Canvas canvas, float x, float y, float width, float height, int fillColor) {
      int outlineColor = ChestESP.INSTANCE.outlineColor.argb();
      SkijaUi.fill(canvas, x, y, width, height, fillColor);
      SkijaUi.fill(canvas, x, y + 5.0F, width, 1.0F, outlineColor);
      SkijaUi.fill(canvas, x + width * 0.5F - 1.5F, y + 6.0F, 3.0F, 4.0F, outlineColor);
      if ((Boolean)ChestESP.INSTANCE.outline.get()) {
         ESP_OUTLINE_PAINT.setStrokeWidth(((Double)ChestESP.INSTANCE.outlineThickness.get()).floatValue()).setColor(outlineColor);
         canvas.drawRect(Rect.makeXYWH(x, y, width, height), ESP_OUTLINE_PAINT);
      }

   }

   private static void drawPanelBox(Canvas canvas, float x, float y, float width, float height, int color) {
      drawPanelOutline(canvas, x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F, -16777216);
      drawPanelOutline(canvas, x, y, width, height, color);
      drawPanelOutline(canvas, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, -16777216);
   }

   private static void drawPanelRoundedBox(Canvas canvas, float x, float y, float width, float height, float radius, int color) {
      SkijaUi.outline(canvas, x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F, radius + 1.0F, 1.0F, -16777216);
      SkijaUi.outline(canvas, x, y, width, height, radius, 1.0F, color);
      if (width > 2.0F && height > 2.0F) {
         SkijaUi.outline(canvas, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, Math.max(0.0F, radius - 1.0F), 1.0F, -16777216);
      }

   }

   private static void drawPanelPlayerOutline(Canvas canvas, float x, float y, float scale, int color) {
      ESP_OUTLINE_PAINT.setStrokeWidth(Math.max(1.0F, scale * 0.38F)).setColor(color);
      canvas.drawRect(Rect.makeXYWH(x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale), ESP_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x + 4.0F * scale, y + 8.0F * scale, 8.0F * scale, 12.0F * scale), ESP_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x, y + 8.0F * scale, 4.0F * scale, 12.0F * scale), ESP_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x + 12.0F * scale, y + 8.0F * scale, 4.0F * scale, 12.0F * scale), ESP_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x + 4.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale), ESP_OUTLINE_PAINT);
      canvas.drawRect(Rect.makeXYWH(x + 8.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale), ESP_OUTLINE_PAINT);
   }

   private static void drawPanel3DBox(Canvas canvas, float x, float y, float width, float height, int color) {
      float offset = Math.max(4.0F, width * 0.16F);
      ESP_OUTLINE_PAINT.setStrokeWidth(1.2F).setColor(color);
      Rect front = Rect.makeXYWH(x, y + offset, width - offset, height - offset);
      Rect back = Rect.makeXYWH(x + offset, y, width - offset, height - offset);
      canvas.drawRect(front, ESP_OUTLINE_PAINT);
      canvas.drawRect(back, ESP_OUTLINE_PAINT);
      canvas.drawLine(front.getLeft(), front.getTop(), back.getLeft(), back.getTop(), ESP_OUTLINE_PAINT);
      canvas.drawLine(front.getRight(), front.getTop(), back.getRight(), back.getTop(), ESP_OUTLINE_PAINT);
      canvas.drawLine(front.getLeft(), front.getBottom(), back.getLeft(), back.getBottom(), ESP_OUTLINE_PAINT);
      canvas.drawLine(front.getRight(), front.getBottom(), back.getRight(), back.getBottom(), ESP_OUTLINE_PAINT);
   }

   private static void drawPanelCylinder(Canvas canvas, float x, float y, float width, float height, int color) {
      float ellipseHeight = Math.max(5.0F, width * 0.28F);
      ESP_OUTLINE_PAINT.setStrokeWidth(1.2F).setColor(color);
      canvas.drawOval(Rect.makeXYWH(x, y, width, ellipseHeight), ESP_OUTLINE_PAINT);
      canvas.drawOval(Rect.makeXYWH(x, y + height - ellipseHeight, width, ellipseHeight), ESP_OUTLINE_PAINT);
      canvas.drawLine(x, y + ellipseHeight * 0.5F, x, y + height - ellipseHeight * 0.5F, ESP_OUTLINE_PAINT);
      canvas.drawLine(x + width, y + ellipseHeight * 0.5F, x + width, y + height - ellipseHeight * 0.5F, ESP_OUTLINE_PAINT);
   }

   private static void drawPanelOutline(Canvas canvas, float x, float y, float width, float height, int color) {
      SkijaUi.fill(canvas, x, y, width, 1.0F, color);
      SkijaUi.fill(canvas, x, y + height - 1.0F, width, 1.0F, color);
      SkijaUi.fill(canvas, x, y + 1.0F, 1.0F, height - 2.0F, color);
      SkijaUi.fill(canvas, x + width - 1.0F, y + 1.0F, 1.0F, height - 2.0F, color);
   }

   private static void drawPanelSkin(Canvas canvas, Image image, float x, float y, float scale) {
      ESP_SKIN_PAINT.setColor(-1).setAlpha(255);
      drawPanelSkinPart(canvas, image, 8.0F, 8.0F, 8.0F, 8.0F, x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale);
      drawPanelSkinPart(canvas, image, 20.0F, 20.0F, 8.0F, 12.0F, x + 4.0F * scale, y + 8.0F * scale, 8.0F * scale, 12.0F * scale);
      drawPanelSkinPart(canvas, image, 44.0F, 20.0F, 4.0F, 12.0F, x, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
      drawPanelSkinPart(canvas, image, 36.0F, 52.0F, 4.0F, 12.0F, x + 12.0F * scale, y + 8.0F * scale, 4.0F * scale, 12.0F * scale);
      drawPanelSkinPart(canvas, image, 4.0F, 20.0F, 4.0F, 12.0F, x + 4.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);
      drawPanelSkinPart(canvas, image, 20.0F, 52.0F, 4.0F, 12.0F, x + 8.0F * scale, y + 20.0F * scale, 4.0F * scale, 12.0F * scale);
      drawPanelSkinPart(canvas, image, 40.0F, 8.0F, 8.0F, 8.0F, x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale);
   }

   private static void drawPanelSkinPart(Canvas canvas, Image image, float sourceX, float sourceY, float sourceWidth, float sourceHeight, float x, float y, float width, float height) {
      float textureWidth = (float)image.getWidth();
      float textureHeight = (float)image.getHeight();
      Rect source = Rect.makeLTRB(sourceX / 64.0F * textureWidth, sourceY / 64.0F * textureHeight, (sourceX + sourceWidth) / 64.0F * textureWidth, (sourceY + sourceHeight) / 64.0F * textureHeight);
      canvas.drawImageRect(image, source, Rect.makeXYWH(x, y, width, height), SamplingMode.DEFAULT, ESP_SKIN_PAINT, true);
   }

   private static void drawPanelFallbackPlayer(Canvas canvas, float x, float y, float scale) {
      int color = UiTheme.withAlpha(UiTheme.TEXT_MUTED, 180);
      SkijaUi.rounded(canvas, x + 4.0F * scale, y, 8.0F * scale, 8.0F * scale, scale, color);
      SkijaUi.rounded(canvas, x, y + 8.0F * scale, 16.0F * scale, 12.0F * scale, scale, color);
      SkijaUi.rounded(canvas, x + 4.0F * scale, y + 20.0F * scale, 8.0F * scale, 12.0F * scale, scale, color);
   }

   private void addPanelEspHit(PanelEspPart part, float x, float y, float width, float height) {
      this.panelEspHits.add(new PanelEspHit(part, x, y, width, height));
   }

   private void drawPanelEspHover(Canvas canvas) {
      for(int i = this.panelEspHits.size() - 1; i >= 0; --i) {
         PanelEspHit hit = (PanelEspHit)this.panelEspHits.get(i);
         if (hit.part() == this.draggingPanelEsp || ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, hit.x(), hit.y(), hit.width(), hit.height())) {
            drawPanelOutline(canvas, hit.x(), hit.y(), hit.width(), hit.height(), UiTheme.withAlpha(UiTheme.accent(), 190));
            return;
         }
      }

   }

   private void renderSetting(Canvas canvas, Setting<?> setting, float x, float y, float width, float height) {
      boolean hovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, x, y, width, height);
      if (hovered) {
         SkijaUi.rounded(canvas, x, y + 1.0F, width, Math.max(1.0F, height - 2.0F), 6.0F, UiTheme.themedRaised(52));
         SkijaUi.rounded(canvas, x + 1.0F, y + 6.0F, 2.0F, Math.max(2.0F, height - 12.0F), 1.0F, UiTheme.withAlpha(UiTheme.accent(), 135));
      }

      float pad = Math.min(11.0F, Math.max(6.0F, width * 0.07F));
      SkijaUi.fill(canvas, x + pad, y + height - 1.0F, Math.max(0.0F, width - pad * 2.0F), 1.0F, 620756991);
      if (setting instanceof BooleanSetting booleanSetting) {
         this.renderSettingLabel(canvas, setting, x + pad, y, Math.max(1.0F, width - pad * 2.0F - 34.0F), height);
         float toggleX = x + width - pad - 28.0F;
         float toggleY = y + (height - 12.0F) * 0.5F;
         boolean var10004 = (Boolean)booleanSetting.get();
         boolean var10005 = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, toggleX - 3.0F, toggleY - 4.0F, 34.0F, 20.0F);
         String var10006 = this.selectedModule.id();
         this.renderToggle(canvas, toggleX, toggleY, var10004, var10005, "setting:" + var10006 + ":" + setting.name());
      } else if (setting instanceof FontSetting fontSetting) {
         this.renderChoiceSetting(canvas, setting, fontSetting.displayValue(), x, y, width, height, pad);
      } else if (setting instanceof EnumSetting<?> enumSetting) {
         if (enumSetting.isSlider()) {
            this.renderNumberSetting(canvas, setting, enumSetting.displayValue(),
                  enumSetting.sliderFraction(), x, y, width, height);
         } else {
            float choiceHeight = MusicPresetPreview.matches(setting) ? 48.0F : height;
            this.renderChoiceSetting(canvas, setting, enumSetting.displayValue(), x, y, width, choiceHeight, pad);
            if (MusicPresetPreview.matches(setting)) {
               MusicPresetPreview.draw(canvas, x + pad, y + choiceHeight, Math.max(1.0F, width - pad * 2.0F), height - choiceHeight, 1.0F);
            }
         }
      } else if (setting instanceof IntSetting intSetting) {
         this.renderNumberSetting(canvas, setting, Integer.toString((Integer)intSetting.get()), intSetting.fraction(), x, y, width, height);
      } else if (setting instanceof DoubleSetting doubleSetting) {
         this.renderNumberSetting(canvas, setting, trimDouble((Double)doubleSetting.get()), doubleSetting.fraction(), x, y, width, height);
      } else if (setting instanceof ColorSetting colorSetting) {
         this.renderColorSetting(canvas, colorSetting, x, y, width);
      } else if (setting instanceof KeybindSetting keybindSetting) {
         String value = this.capturingKeybind == keybindSetting ? tr("press_key", "Press a key") : keyTextOrNone((Integer)keybindSetting.get());
         float valueWidth = Math.min(Math.max(46.0F, SkijaUi.textWidth(value, 7.5F) + 14.0F), Math.max(46.0F, width * 0.46F));
         float valueX = x + width - pad - valueWidth;
         this.renderSettingLabel(canvas, setting, x + pad, y, Math.max(1.0F, valueX - x - pad * 2.0F), height);
         boolean bindHovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, valueX, y + (height - 20.0F) * 0.5F, valueWidth, 20.0F);
         float var10003 = y + (height - 18.0F) * 0.5F;
         boolean var10007 = this.capturingKeybind == keybindSetting;
         String var10008 = this.selectedModule.id();
         this.drawControl(canvas, valueX, var10003, valueWidth, 18.0F, bindHovered, var10007, "keybind:" + var10008 + ":" + setting.name());
         SkijaUi.text(canvas, fitText(value, valueWidth - 10.0F, 7.5F, false), valueX + 6.0F, y + (height - 18.0F) * 0.5F, 18.0F, UiTheme.TEXT, 7.5F);
      } else if (setting instanceof StringSetting stringSetting) {
         boolean focused = this.textTarget == ClickGuiScreen.TextTarget.STRING && this.editingString == stringSetting;
         String value = focused ? this.editText : (String)stringSetting.get();
         float fieldWidth = Math.max(84.0F, Math.min(170.0F, width * 0.52F));
         float fieldX = x + width - pad - fieldWidth;
         this.renderSettingLabel(canvas, setting, x + pad, y, Math.max(1.0F, fieldX - x - pad * 2.0F), height);
         this.renderTextField(canvas, fieldX, y + (height - 24.0F) * 0.5F, fieldWidth, 24.0F, value, focused, tr("empty", "Empty"), 7.5F);
      } else if (setting instanceof ButtonSetting) {
         float buttonX = x + pad;
         float buttonY = y + 7.0F;
         float buttonWidth = Math.max(20.0F, width - pad * 2.0F);
         float buttonHeight = Math.max(22.0F, height - 14.0F);
         String var32 = this.selectedModule.id();
         this.drawControl(canvas, buttonX, buttonY, buttonWidth, buttonHeight, hovered, true, "button:" + var32 + ":" + setting.name());
         ClickGuiText.drawCentered(canvas, setting.displayName(), buttonX + 8.0F, buttonY, buttonWidth - 16.0F, buttonHeight, 11.0F, UiTheme.TEXT, 9.0F, true);
      }

   }

   private void renderChoiceSetting(Canvas canvas, Setting<?> setting, String choiceValue, float x, float y, float width, float height, float pad) {
      float controlWidth = Math.min(Math.max(48.0F, SkijaUi.textWidth(choiceValue, 7.5F) + 24.0F), Math.max(48.0F, width * 0.48F));
      float controlX = x + width - pad - controlWidth;
      this.renderSettingLabel(canvas, setting, x + pad, y, Math.max(1.0F, controlX - x - pad * 2.0F), height);
      boolean controlHovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, controlX, y + (height - 22.0F) * 0.5F, controlWidth, 22.0F);
      float var10003 = y + (height - 22.0F) * 0.5F;
      String var10008 = this.selectedModule.id();
      this.drawControl(canvas, controlX, var10003, controlWidth, 22.0F, controlHovered, false, "choice:" + var10008 + ":" + setting.name());
      SkijaUi.text(canvas, "<", controlX + 6.0F, y + (height - 22.0F) * 0.5F, 22.0F, UiTheme.TEXT_FAINT, 7.5F);
      ClickGuiText.drawCentered(canvas, choiceValue, controlX + 11.0F, y + (height - 22.0F) * 0.5F, controlWidth - 22.0F, 22.0F, 9.0F, UiTheme.accent(), 7.5F, true);
      SkijaUi.text(canvas, ">", controlX + controlWidth - 10.0F, y + (height - 22.0F) * 0.5F, 22.0F, UiTheme.TEXT_FAINT, 7.5F);
   }

   private void renderSettingLabel(Canvas canvas, Setting<?> setting, float x, float y, float maxWidth, float height) {
      float width = Math.max(1.0F, maxWidth);
      int lines = ClickGuiText.lineCount(setting.displayName(), width, 9.0F, false);
      float lineHeight = 11.0F;
      float top = y + Math.max(0.0F, (height - (float)lines * lineHeight) * 0.5F);
      ClickGuiText.draw(canvas, setting.displayName(), x, top, width, lineHeight, UiTheme.TEXT_MUTED, 9.0F, false);
   }

   private void renderNumberSetting(Canvas canvas, Setting<?> setting, String value, float fraction, float x, float y, float width, float height) {
      float pad = Math.min(11.0F, Math.max(6.0F, width * 0.07F));
      ClickGuiText.draw(canvas, setting.displayName(), x + pad, y + 3.0F, Math.max(1.0F, width - pad * 2.0F), 10.0F, UiTheme.TEXT_MUTED, 9.0F, false);
      float trackX = x + pad;
      float trackWidth = numberTrackWidth(width, pad);
      float trackY = y + height - 16.0F;
      float clamped = ClickGuiLayout.clamp(fraction, 0.0F, 1.0F);
      boolean hovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, trackX, trackY - 6.0F, trackWidth, 15.0F);
      this.renderMovingValueSlider(canvas, trackX, trackY, trackWidth, clamped, value, UiTheme.accent(), hovered ? UiTheme.TEXT : UiTheme.TEXT_MUTED, hovered ? UiTheme.withAlpha(UiTheme.accent(), 82) : 989855743, 9.0F, 10.0F);
   }

   private void renderColorSetting(Canvas canvas, ColorSetting setting, float x, float y, float width) {
      Color color = setting.get();
      float pad = Math.min(11.0F, Math.max(6.0F, width * 0.07F));
      float fieldWidth = colorHexFieldWidth(setting);
      float fieldX = colorHexFieldX(x, width, pad, fieldWidth);
      boolean focused = this.textTarget == ClickGuiScreen.TextTarget.COLOR && this.editingColor == setting;
      float labelWidth = Math.max(1.0F, fieldX - x - pad * 2.0F);
      ClickGuiText.draw(canvas, setting.displayName(), x + pad, y + 2.0F, labelWidth, 11.0F, UiTheme.TEXT_MUTED, 9.0F, false);
      boolean expanded = ColorPickerState.expanded(setting);
      if (expanded && ColorPickerState.mode(setting) == ColorPickerState.Mode.RGB) {
         this.renderTextField(canvas, fieldX, y + 6.0F, fieldWidth, 20.0F, focused ? this.editText : setting.hex(), focused, setting.allowAlpha() ? "#AARRGGBB" : "#RRGGBB", 7.5F);
      }
      SkijaUi.rounded(canvas, x + width - pad - 18.0F, y + 8.0F, 18.0F, 16.0F, 3.0F, 989855743);
      SkijaUi.rounded(canvas, x + width - pad - 17.0F, y + 9.0F, 16.0F, 14.0F, 2.0F, color.getRGB());
      if (!expanded) return;
      float modeWidth = Math.min(116.0F, width - pad * 2.0F);
      ColorPickerState.drawModeSelection(canvas, x + pad, y + 27.0F, modeWidth,
            ColorPickerState.mode(setting), UiTheme.TEXT_MUTED, UiTheme.accent());
      if (ColorPickerState.mode(setting) == ColorPickerState.Mode.PALETTE) {
         ColorPickerState.drawPalette(canvas, x + pad, y + 46.0F, width - pad * 2.0F, 36.0F, setting, UiTheme.TEXT);
         return;
      }
      int[] channels = new int[]{color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
      String[] labels = new String[]{"R", "G", "B", "A"};
      int[] colors = new int[]{UiTheme.rgb(226, 91, 91), UiTheme.rgb(80, 201, 128), UiTheme.rgb(91, 159, 235), UiTheme.TEXT_MUTED};
      int count = setting.allowAlpha() ? 4 : 3;

      for(int channel = 0; channel < count; ++channel) {
         float channelY = y + 46.0F + (float)channel * 22.0F;
         SkijaUi.boldText(canvas, labels[channel], x + pad, channelY, 18.0F, colors[channel], 7.5F);
         float trackX = colorTrackX(x);
         float trackWidth = colorTrackWidth(width);
         float trackY = channelY + 7.0F;
         float fraction = (float)channels[channel] / 255.0F;
         boolean trackHovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, trackX, trackY - 5.0F, trackWidth, 14.0F);
         String value = Integer.toString(channels[channel]);
         this.renderMovingValueSlider(canvas, trackX, trackY, trackWidth, fraction, value, colors[channel], trackHovered ? UiTheme.TEXT : UiTheme.TEXT_MUTED, 989855743, 7.5F, 8.0F);
      }

   }

   private void renderMovingValueSlider(Canvas canvas, float trackX, float trackY, float trackWidth, float fraction, String value, int progressColor, int knobColor, int trackColor, float fontSize, float knobSize) {
      float clamped = ClickGuiLayout.clamp(fraction, 0.0F, 1.0F);
      float trackEnd = trackX + trackWidth;
      float knobX = trackX + trackWidth * clamped;
      float valueWidth = SkijaUi.textWidth(value, fontSize);
      float valueX = knobX + knobSize * 0.5F + 2.0F;
      if (valueX + valueWidth > trackEnd) {
         valueX = knobX - knobSize * 0.5F - 2.0F - valueWidth;
      }

      valueX = ClickGuiLayout.clamp(valueX, trackX, Math.max(trackX, trackEnd - valueWidth));
      float gapStart = valueX - 3.0F;
      float gapEnd = valueX + valueWidth + 3.0F;
      drawSliderLayer(canvas, trackX, trackEnd, gapStart, gapEnd, trackY, 4.0F, trackColor);
      drawSliderLayer(canvas, trackX, knobX, gapStart, gapEnd, trackY, 4.0F, progressColor);
      SkijaUi.rounded(canvas, knobX - knobSize * 0.5F, trackY - (knobSize - 4.0F) * 0.5F, knobSize, knobSize, knobSize * 0.5F, knobColor);
      SkijaUi.text(canvas, value, valueX, trackY - 7.0F, 18.0F, UiTheme.TEXT, fontSize);
   }

   private void renderToggle(Canvas canvas, float x, float y, boolean enabled, boolean hovered, String key) {
      float enabledProgress = this.animate(key + ":enabled", enabled ? 1.0F : 0.0F);
      float hoverProgress = this.animate(key + ":hover", hovered ? 1.0F : 0.0F);
      int trackColor = enabledProgress > 0.01F ? UiTheme.withAlpha(UiTheme.accent(), Math.round(40.0F + enabledProgress * 215.0F)) : UiTheme.withAlpha(UiTheme.BORDER_STRONG, Math.round(150.0F + hoverProgress * 70.0F));
      SkijaUi.rounded(canvas, x, y, 26.0F, 12.0F, 6.0F, trackColor);
      if (enabledProgress < 0.99F) {
         SkijaUi.rounded(canvas, x + 1.0F, y + 1.0F, 24.0F, 10.0F, 5.0F, UiTheme.themedRaised(Math.round(18.0F + hoverProgress * 18.0F)));
      }

      float knobX = x + 2.0F + 14.0F * enabledProgress;
      SkijaUi.rounded(canvas, knobX, y + 2.0F, 8.0F, 8.0F, 4.0F, enabledProgress > 0.5F ? -854792 : UiTheme.withAlpha(hovered ? UiTheme.TEXT : UiTheme.TEXT_FAINT, Math.round(180.0F + hoverProgress * 75.0F)));
   }

   private void renderTextField(Canvas canvas, float x, float y, float width, float height, String value, boolean focused, String placeholder, float fontSize) {
      boolean hovered = ClickGuiLayout.contains((double)this.renderMouseX, (double)this.renderMouseY, x, y, width, height);
      int border = focused ? UiTheme.accent() : (hovered ? UiTheme.withAlpha(UiTheme.accent(), 88) : 620756991);
      SkijaUi.rounded(canvas, x, y, width, height, 6.0F, border);
      SkijaUi.rounded(canvas, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F, 5.0F, focused ? UiTheme.themedRaised(107) : (hovered ? UiTheme.themedHover(74) : UiTheme.themedSurfaceAlt(48)));
      String shown = value.isEmpty() && !focused ? placeholder : value;
      int color = value.isEmpty() && !focused ? UiTheme.TEXT_FAINT : UiTheme.TEXT;
      float innerX = x + 7.0F;
      float innerWidth = Math.max(1.0F, width - 14.0F);
      float offset = 0.0F;
      if (focused) {
         int safeCursor = Math.max(0, Math.min(this.editCursor, value.length()));
         float cursorWidth = SkijaUi.textWidth(value.substring(0, safeCursor), fontSize);
         offset = Math.max(0.0F, cursorWidth - innerWidth + 2.0F);
      }

      canvas.save();
      canvas.clipRect(Rect.makeXYWH(innerX, y + 1.0F, innerWidth, height - 2.0F));
      if (focused && this.selectAll && !value.isEmpty()) {
         SkijaUi.fill(canvas, innerX, y + 4.0F, Math.min(innerWidth, SkijaUi.textWidth(value, fontSize)), height - 8.0F, UiTheme.withAlpha(UiTheme.accent(), 46));
      }

      SkijaUi.text(canvas, shown, innerX - offset, y, height, color, fontSize);
      if (focused && System.currentTimeMillis() / 500L % 2L == 0L) {
         int safeCursor = Math.max(0, Math.min(this.editCursor, value.length()));
         float caretX = innerX - offset + SkijaUi.textWidth(value.substring(0, safeCursor), fontSize);
         SkijaUi.fill(canvas, caretX, y + 5.0F, 1.0F, Math.max(2.0F, height - 10.0F), UiTheme.accent());
      }

      canvas.restore();
   }

   private void drawControl(Canvas canvas, float x, float y, float width, float height, boolean hovered, boolean active, String key) {
      float hoverProgress = this.animate(key + ":hover", hovered ? 1.0F : 0.0F);
      float activeProgress = this.animate(key + ":active", active ? 1.0F : 0.0F);
      int border = activeProgress > 0.01F ? UiTheme.withAlpha(UiTheme.accent(), Math.round(110.0F + activeProgress * 125.0F)) : UiTheme.withAlpha(UiTheme.accent(), Math.round(28.0F + hoverProgress * 64.0F));
      int fill = activeProgress > 0.01F ? UiTheme.withAlpha(UiTheme.accent(), Math.round(12.0F + hoverProgress * 34.0F)) : UiTheme.themedRaised(Math.round(12.0F + hoverProgress * 38.0F));
      SkijaUi.rounded(canvas, x, y, width, height, 6.0F, border);
      SkijaUi.rounded(canvas, x + 1.0F, y + 1.0F, Math.max(0.0F, width - 2.0F), Math.max(0.0F, height - 2.0F), 5.0F, fill);
   }

   private float animate(String key, float target) {
      float current = (Float)this.transitions.getOrDefault(key, target);
      float next = approach(current, target, this.animationDelta, 18.0F);
      this.transitions.put(key, next);
      return next;
   }

   private void renderScrollbar(Canvas canvas, float x, float y, float height, float contentHeight, float scroll) {
      if (!(height <= 0.0F) && !(contentHeight <= height)) {
         float thumbHeight = Math.max(18.0F, height * height / contentHeight);
         float maxScroll = contentHeight - height;
         float thumbY = y + scroll / maxScroll * (height - thumbHeight);
         SkijaUi.rounded(canvas, x, y + 2.0F, 2.0F, height - 4.0F, 1.0F, 620756991);
         SkijaUi.rounded(canvas, x, thumbY, 2.0F, thumbHeight, 1.0F, UiTheme.accent());
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
         double mouseX = this.logicalX(event.x());
         double mouseY = this.logicalY(event.y());
         int button = event.button();
         ClickGuiLayout layout = this.currentLayout();
         this.capturingKeybind = null;
         this.capturingModule = null;
         this.draggingNumber = null;
         this.draggingColor = null;
         this.draggingPanelEsp = null;
         if (!layout.contains(mouseX, mouseY)) {
            this.finishTextEditing();
            return super.mouseClicked(event, doubleClick);
         } else if (this.handleCategoryClick(layout, mouseX, mouseY, button)) {
            return true;
         } else if (ClickGuiLayout.contains(mouseX, mouseY, topSearchX(layout), topSearchY(layout), topSearchWidth(layout), topSearchHeight(layout))) {
            this.configsOpen = false;
            this.settingsPage = false;
            this.focusSearch();
            return true;
         } else if (this.configsOpen) {
            return this.handleConfigClick(layout, mouseX, mouseY, button);
         } else if (this.handleModuleClick(layout, mouseX, mouseY, button)) {
            return true;
         } else if (this.handleDetailHeaderClick(layout, mouseX, mouseY, button)) {
            return true;
         } else if (this.beginPanelEspDrag(mouseX, mouseY, button)) {
            return true;
         } else if (this.handleSettingClick(layout, mouseX, mouseY, button)) {
            return true;
         } else {
            this.finishTextEditing();
            return true;
         }
      }
   }

   private boolean handleCategoryClick(ClickGuiLayout layout, double mouseX, double mouseY, int button) {
      if (button == 0 && ClickGuiLayout.contains(mouseX, mouseY, layout.panelContentX(), layout.tabsY(), layout.panelContentWidth(), layout.tabsHeight())) {
         float gap = 5.0F;
         int count = Category.guiVisibleCount() + 1;
         float tabWidth = Math.max(1.0F, (layout.panelContentWidth() - gap * (float)(count - 1)) / (float)count);
         float tabX = layout.panelContentX();

         for(Category category : Category.values()) {
            if (category.guiVisible()) {
               if (ClickGuiLayout.contains(mouseX, mouseY, tabX, layout.tabsY(), tabWidth, layout.tabsHeight())) {
                  this.finishTextEditing();
                  this.configsOpen = false;
                  this.settingsPage = false;
                  this.selectedCategory = category;
                  List<Module> modules = ModuleManager.INSTANCE.modulesIn(category);
                  this.selectedModule = modules.isEmpty() ? null : (Module)modules.getFirst();
                  this.moduleScroll = 0.0F;
                  this.settingsScroll = 0.0F;
                  this.categoryRevealProgress = 0.0F;
                  return true;
               }

               tabX += tabWidth + gap;
            }
         }

         if (ClickGuiLayout.contains(mouseX, mouseY, tabX, layout.tabsY(), tabWidth, layout.tabsHeight())) {
            this.finishTextEditing();
            this.configsOpen = true;
            this.settingsPage = false;
            this.configScroll = 0.0F;
            if (this.configName.isBlank()) {
               this.configName = ConfigManager.INSTANCE.currentProfile();
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean handleConfigClick(ClickGuiLayout layout, double mouseX, double mouseY, int button) {
      if (button != 0) {
         return true;
      } else {
         List<String> profiles = ConfigManager.INSTANCE.listProfiles();
         float contentX = layout.panelContentX();
         float contentWidth = layout.panelContentWidth();
         float listWidth = Math.max(1.0F, contentWidth * 0.26F);
         float listX = contentX;
         float listY = layout.bodyY() + 22.0F;
         float listHeight = Math.max(1.0F, layout.tabsY() - listY - 8.0F);
         if (ClickGuiLayout.contains(mouseX, mouseY, contentX, listY, listWidth, listHeight)) {
            float rowY = listY - this.configScroll;

            for(String profile : profiles) {
               if (ClickGuiLayout.contains(mouseX, mouseY, listX, rowY, listWidth, 25.0F)) {
                  this.finishTextEditing();
                  this.configName = profile;
                  this.configStatus = "";
                  return true;
               }

               rowY += 25.0F;
            }

            return true;
         } else {
            float x = contentX + listWidth + 14.0F;
            float width = Math.max(1.0F, contentX + contentWidth - x);
            float inputY = layout.bodyY() + 22.0F;
            if (ClickGuiLayout.contains(mouseX, mouseY, x, inputY, width, 24.0F)) {
               this.focusConfigName();
               return true;
            } else {
               float buttonY = inputY + 30.0F;
               float gap = 5.0F;
               float buttonWidth = Math.max(34.0F, (width - gap * 2.0F) / 3.0F);
               if (ClickGuiLayout.contains(mouseX, mouseY, x, buttonY, buttonWidth, 24.0F)) {
                  this.saveConfig();
               } else if (ClickGuiLayout.contains(mouseX, mouseY, x + buttonWidth + gap, buttonY, buttonWidth, 24.0F)) {
                  this.loadConfig();
               } else if (ClickGuiLayout.contains(mouseX, mouseY, x + (buttonWidth + gap) * 2.0F, buttonY, buttonWidth, 24.0F)) {
                  this.deleteConfig();
               } else {
                  float utilityY = buttonY + 28.0F;
                  float utilityWidth = Math.max(1.0F, (width - gap) / 2.0F);
                  if (ClickGuiLayout.contains(mouseX, mouseY, x, utilityY, utilityWidth, 24.0F)) {
                     this.openConfigFolder();
                  } else if (ClickGuiLayout.contains(mouseX, mouseY, x + utilityWidth + gap, utilityY, utilityWidth, 24.0F)) {
                     this.refreshConfigs();

                  } else if (ClickGuiLayout.contains(mouseX, mouseY, x, utilityY + 27.0F, width, 18.0F)) {
                     this.importVisualAndHud = !this.importVisualAndHud;
                  } else if (ClickGuiLayout.contains(mouseX, mouseY, x, utilityY + 49.0F, width, 18.0F)) {
                     this.importKeybinds = !this.importKeybinds;
                  } else {
                     this.finishTextEditing();
                  }
               }

               return true;
            }
         }
      }
   }

   private void saveConfig() {
      this.finishTextEditing();

      try {
         this.configName = ConfigManager.INSTANCE.saveProfile(this.selectedConfigName());
         this.setConfigStatus(tr("status_saved", "Saved") + " " + this.configName, false);
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to save config profile {}", this.selectedConfigName(), error);
         String var10001 = tr("status_save_failed", "Save failed");
         this.setConfigStatus(var10001 + ": " + error.getMessage(), true);
      }

   }

   private void loadConfig() {
      this.finishTextEditing();
      String name = this.selectedConfigName();

      try {
         if (ConfigManager.INSTANCE.loadProfile(name, new ConfigManager.ImportOptions(this.importVisualAndHud, this.importKeybinds))) {
            this.configName = ConfigManager.INSTANCE.currentProfile();
            this.setConfigStatus(tr("status_loaded", "Loaded") + " " + this.configName, false);
         } else {
            this.setConfigStatus(tr("status_profile_not_found", "Profile not found"), true);
         }
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to load config profile {}", name, error);
         String var10001 = tr("status_load_failed", "Load failed");
         this.setConfigStatus(var10001 + ": " + error.getMessage(), true);
      }

   }

   private void deleteConfig() {
      this.finishTextEditing();
      String name = this.selectedConfigName();

      try {
         if (ConfigManager.INSTANCE.deleteProfile(name)) {
            this.configName = ConfigManager.INSTANCE.currentProfile();
            this.setConfigStatus(tr("status_deleted", "Deleted") + " " + name, false);
         } else {
            this.setConfigStatus(tr("status_default_not_deletable", "Default config cannot be deleted"), true);
         }
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to delete config profile {}", name, error);
         String var10001 = tr("status_delete_failed", "Delete failed");
         this.setConfigStatus(var10001 + ": " + error.getMessage(), true);
      }

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

         this.setConfigStatus(tr("status_folder_opened", "Opened config folder"), false);
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to open the config folder", error);
         this.setConfigStatus(tr("status_folder_failed", "Open folder failed") + ": " + error.getMessage(), true);
      }

   }

   private void refreshConfigs() {
      this.finishTextEditing();

      try {
         int count = ConfigManager.INSTANCE.refreshProfiles();
         List<String> profiles = ConfigManager.INSTANCE.listProfiles();
         boolean selectedStillExists = profiles.stream().anyMatch((profile) -> profile.equalsIgnoreCase(this.configName.trim()));
         if (!selectedStillExists) {
            this.configName = ConfigManager.INSTANCE.currentProfile();
         }

         this.configScroll = 0.0F;
         this.configHighlightY = Float.NaN;
         this.setConfigStatus(tr("status_refreshed", "Refreshed") + " " + count + " " + tr("profiles_lower", "profiles"), false);
      } catch (RuntimeException error) {
         Setsuna.LOGGER.error("Failed to refresh config profiles", error);
         this.setConfigStatus(tr("status_refresh_failed", "Refresh failed") + ": " + error.getMessage(), true);
      }

   }

   private boolean handleModuleClick(ClickGuiLayout layout, double mouseX, double mouseY, int button) {
      if (!this.settingsPage && ClickGuiLayout.contains(mouseX, mouseY, layout.panelContentX(), layout.moduleListY(), layout.panelContentWidth(), layout.moduleListHeight())) {
         List<Module> modules = this.filteredModules();
         float cardWidth = layout.moduleCardWidth();

         for(int index = 0; index < modules.size(); ++index) {
            float cardX = layout.moduleCardX(index);
            float cardY = layout.moduleCardY(index, this.moduleScroll);
            if (ClickGuiLayout.contains(mouseX, mouseY, cardX, cardY, cardWidth, 50.0F)) {
               this.finishTextEditing();
               Module module = (Module)modules.get(index);
               this.selectedModule = module;
               this.settingsScroll = 0.0F;
               if (button == 0 && !ClickGuiLayout.contains(mouseX, mouseY, cardX + cardWidth - 29.0F, cardY + 26.0F, 27.0F, 22.0F)) {
                  module.interactFromClickGui();
               } else if (button == 1 || button == 0) {
                  this.settingsPage = true;
                  this.categoryRevealProgress = 1.0F;
               }

               return true;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean handleDetailHeaderClick(ClickGuiLayout layout, double mouseX, double mouseY, int button) {
      if (!this.settingsPage) return false;
      float x = layout.panelContentX();
      float width = layout.panelContentWidth();
      if (button == 0 && ClickGuiLayout.contains(mouseX, mouseY, x + 10.0F, layout.bodyY(), 52.0F, 22.0F)) {
         this.finishTextEditing();
         this.settingsPage = false;
         return true;
      }
      float bodyY = layout.bodyY() + 28.0F;
      if (this.selectedModule == null || !ClickGuiLayout.contains(mouseX, mouseY, x, bodyY, width, layout.inspectorHeaderHeight())) return false;
      this.finishTextEditing();
      float padding = columnPadding(width, 14.0F);
      float controlWidth = this.selectedModule.isToggleable() ? 28.0F : 24.0F;
      float controlX = x + width - padding - controlWidth;
      if (button == 0 && ClickGuiLayout.contains(mouseX, mouseY, controlX - 3.0F, bodyY + 7.0F, controlWidth + 6.0F, 26.0F)) {
         this.selectedModule.interactFromClickGui();
      } else if (button == 0 && ClickGuiLayout.contains(mouseX, mouseY, x + padding, bodyY + 42.0F, this.moduleBindWidth(width, padding), 18.0F)) {
         this.capturingModule = this.selectedModule;
      }
      return true;
   }

   private boolean handleSettingClick(ClickGuiLayout layout, double mouseX, double mouseY, int button) {
      if (!this.settingsPage) return false;
      float settingsY = this.panelSettingsY(layout);
      float settingsHeight = this.panelSettingsHeight(layout);
      float baseX = this.panelSettingsX(layout);
      float baseWidth = this.panelSettingsWidth(layout);
      if (this.selectedModule != null && ClickGuiLayout.contains(mouseX, mouseY, baseX, settingsY, baseWidth, settingsHeight)) {
         List<Setting<?>> settings = this.visibleSettings();
         float contentX = baseX + 8.0F;
         float contentWidth = Math.max(1.0F, baseWidth - 16.0F);
         int columns = settingColumns(contentWidth);
         float columnWidth = settingColumnWidth(contentWidth, columns);
         float rowY = settingsY - this.settingsScroll;

         for(int index = 0; index < settings.size(); index += columns) {
            float rowHeight = settingRowHeight(settings, index, columns);

            for(int column = 0; column < columns && index + column < settings.size(); ++column) {
               Setting<?> setting = (Setting)settings.get(index + column);
               float cardX = contentX + (float)column * (columnWidth + 8.0F);
               float cardHeight = settingHeight(setting);
               if (ClickGuiLayout.contains(mouseX, mouseY, cardX, rowY, columnWidth, cardHeight)) {
                  this.clickSetting(setting, cardX, rowY, columnWidth, cardHeight, mouseX, mouseY, button);
                  return true;
               }
            }

            rowY += rowHeight;
         }

         this.finishTextEditing();
         return true;
      } else {
         return false;
      }
   }

   private boolean beginPanelEspDrag(double mouseX, double mouseY, int button) {
      if (!this.settingsPage) return false;
      if (button == 0 && this.selectedModule == ESP.INSTANCE && ClickGuiLayout.contains(mouseX, mouseY, this.panelEspViewportX, this.panelEspViewportY, this.panelEspViewportWidth, this.panelEspViewportHeight)) {
         for(int i = this.panelEspHits.size() - 1; i >= 0; --i) {
            PanelEspHit hit = (PanelEspHit)this.panelEspHits.get(i);
            if (ClickGuiLayout.contains(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height())) {
               this.finishTextEditing();
               this.draggingPanelEsp = hit.part();
               this.panelEspDragStartX = (float)mouseX;
               this.panelEspDragStartY = (float)mouseY;
               this.panelEspOffsetStartX = (Double)panelEspOffsetX(hit.part()).get();
               this.panelEspOffsetStartY = (Double)panelEspOffsetY(hit.part()).get();
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void updatePanelEspDrag(double mouseX, double mouseY) {
      if (this.draggingPanelEsp != null) {
         float boundedX = ClickGuiLayout.clamp((float)mouseX, this.panelEspViewportX + 1.0F, this.panelEspViewportX + this.panelEspViewportWidth - 1.0F);
         float boundedY = ClickGuiLayout.clamp((float)mouseY, this.panelEspViewportY + 1.0F, this.panelEspViewportY + this.panelEspViewportHeight - 1.0F);
         float dragScale = this.draggingPanelEsp != ClickGuiScreen.PanelEspPart.EQUIPMENT && this.draggingPanelEsp != ClickGuiScreen.PanelEspPart.EFFECTS ? 1.0F : Math.max(0.01F, this.panelEspInfoScale);
         panelEspOffsetX(this.draggingPanelEsp).set(this.panelEspOffsetStartX + (double)((boundedX - this.panelEspDragStartX) / dragScale));
         panelEspOffsetY(this.draggingPanelEsp).set(this.panelEspOffsetStartY + (double)((boundedY - this.panelEspDragStartY) / dragScale));
      }
   }

   private static DoubleSetting panelEspOffsetX(PanelEspPart part) {
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

   private static DoubleSetting panelEspOffsetY(PanelEspPart part) {
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

   private void clickSetting(Setting<?> setting, float x, float y, float width, float height, double mouseX, double mouseY, int button) {
      if (setting instanceof FontSetting fontSetting) {
         this.finishTextEditing();
         fontSetting.cycle(button == 1 ? -1 : 1);
      } else if (setting instanceof StringSetting stringSetting) {
         if (button == 0) {
            this.focusString(stringSetting);
         }

      } else if (setting instanceof ColorSetting colorSetting) {
         float pad = Math.min(11.0F, Math.max(6.0F, width * 0.07F));
         if (button == 1 || button == 0 && ClickGuiLayout.contains(mouseX, mouseY, x + width - pad - 22.0F, y + 5.0F, 24.0F, 22.0F)) {
            ColorPickerState.toggle(colorSetting);
            this.finishTextEditing();
            return;
         }
         if (ColorPickerState.expanded(colorSetting)) {
            float modeX = x + pad;
            float modeY = y + 27.0F;
            float modeWidth = Math.min(116.0F, width - pad * 2.0F);
            if (ColorPickerState.clickMode(colorSetting, (float)mouseX, (float)mouseY, modeX, modeY, modeWidth)) return;
            if (ColorPickerState.mode(colorSetting) == ColorPickerState.Mode.PALETTE
                  && ColorPickerState.clickPalette(colorSetting, (float)mouseX, (float)mouseY, x + pad, y + 46.0F, width - pad * 2.0F)) return;
         } else if (button == 0) {
            return;
         }
         float fieldWidth = colorHexFieldWidth(colorSetting);
         float fieldX = colorHexFieldX(x, width, pad, fieldWidth);
         if (button == 0 && ClickGuiLayout.contains(mouseX, mouseY, fieldX, y + 6.0F, fieldWidth, 20.0F)) {
            this.focusColor(colorSetting);
         } else {
            this.finishTextEditing();
            if (button == 1) {
               colorSetting.reset();
            } else {
               int channelCount = colorSetting.allowAlpha() ? 4 : 3;
               int channel = (int)((mouseY - (double)(y + 46.0F)) / (double)22.0F);
               if (button == 0 && ColorPickerState.expanded(colorSetting) && ColorPickerState.mode(colorSetting) == ColorPickerState.Mode.RGB && mouseY >= (double)(y + 46.0F) && channel >= 0 && channel < channelCount) {
                  this.draggingColor = colorSetting;
                  this.draggingColorChannel = channel;
                  this.draggingTrackX = colorTrackX(x);
                  this.draggingTrackWidth = colorTrackWidth(width);
                  this.updateColor(mouseX);
               }
            }

         }
      } else {
         this.finishTextEditing();
         if (setting instanceof BooleanSetting booleanSetting) {
            if (button == 0 || button == 1) {
               NotificationManager var10000 = NotificationManager.INSTANCE;
               Objects.requireNonNull(booleanSetting);
               var10000.withoutModuleFeedback(booleanSetting::toggle);
            }
         } else if (setting instanceof EnumSetting<?> enumSetting) {
            if (enumSetting.isSlider()) {
               if (button == 0 && mouseY >= y + height - 24.0F) {
                  float pad = Math.min(11.0F, Math.max(6.0F, width * 0.07F));
                  this.beginNumberDrag(setting, x + pad, numberTrackWidth(width, pad), mouseX);
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
               float pad = Math.min(11.0F, Math.max(6.0F, width * 0.07F));
               if (mouseY >= (double)(y + height - 24.0F)) {
                  this.beginNumberDrag(setting, x + pad, numberTrackWidth(width, pad), mouseX);
               }
            }
         } else if (setting instanceof DoubleSetting doubleSetting) {
            if (button == 1) {
               doubleSetting.set((Double)doubleSetting.get() - doubleSetting.step());
            } else if (button == 0) {
               float pad = Math.min(11.0F, Math.max(6.0F, width * 0.07F));
               if (mouseY >= (double)(y + height - 24.0F)) {
                  this.beginNumberDrag(setting, x + pad, numberTrackWidth(width, pad), mouseX);
               }
            }
         } else if (setting instanceof KeybindSetting keybindSetting) {
            if (button == 0) {
               this.capturingKeybind = keybindSetting;
            } else if (button == 1) {
               keybindSetting.set(-1);
            }
         } else if (setting instanceof ButtonSetting buttonSetting) {
            if (button == 0) {
               buttonSetting.press();
            }
         }

      }
   }

   private void beginNumberDrag(Setting<?> setting, float trackX, float trackWidth, double mouseX) {
      this.draggingNumber = setting;
      this.draggingTrackX = trackX;
      this.draggingTrackWidth = trackWidth;
      this.updateNumber(mouseX);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      if (this.draggingPanelEsp != null) {
         this.updatePanelEspDrag(this.logicalX(event.x()), this.logicalY(event.y()));
         return true;
      } else if (this.draggingNumber != null) {
         this.updateNumber(this.logicalX(event.x()));
         return true;
      } else if (this.draggingColor != null) {
         this.updateColor(this.logicalX(event.x()));
         return true;
      } else {
         return super.mouseDragged(event, dragX, dragY);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.draggingNumber = null;
      this.draggingColor = null;
      this.draggingColorChannel = -1;
      this.draggingPanelEsp = null;
      return super.mouseReleased(event);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      mouseX = this.logicalX(mouseX);
      mouseY = this.logicalY(mouseY);
      ClickGuiLayout layout = this.currentLayout();
      if (this.configsOpen) {
         float listX = layout.panelContentX();
         float listY = layout.bodyY() + 22.0F;
         float listWidth = Math.max(1.0F, layout.panelContentWidth() * 0.26F);
         float listHeight = Math.max(1.0F, layout.tabsY() - listY - 8.0F);
         if (ClickGuiLayout.contains(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
            float contentHeight = (float)ConfigManager.INSTANCE.listProfiles().size() * 25.0F;
            this.configScroll = ClickGuiLayout.clamp(this.configScroll - (float)verticalAmount * 28.0F, 0.0F, Math.max(0.0F, contentHeight - listHeight));
            return true;
         } else {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
         }
      } else if (!this.settingsPage && ClickGuiLayout.contains(mouseX, mouseY, layout.panelContentX(), layout.moduleListY(), layout.panelContentWidth(), layout.moduleListHeight())) {
         this.moduleScroll -= (float)verticalAmount * 28.0F;
         this.clampScrolls(layout);
         return true;
      } else if (this.settingsPage && ClickGuiLayout.contains(mouseX, mouseY, this.panelSettingsX(layout), this.panelSettingsY(layout), this.panelSettingsWidth(layout), this.panelSettingsHeight(layout))) {
         this.settingsScroll -= (float)verticalAmount * 28.0F;
         this.clampScrolls(layout);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (this.closing) {
         return true;
      } else if (this.capturingKeybind != null) {
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
         if (this.textTarget != ClickGuiScreen.TextTarget.NONE && this.handleTextKey(event)) return true;
         if (event.key() == 256 && this.settingsPage) {
            this.settingsPage = false;
            this.draggingNumber = null;
            this.draggingColor = null;
            this.draggingPanelEsp = null;
            return true;
         }
         return super.keyPressed(event);
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
      if (this.textTarget != ClickGuiScreen.TextTarget.NONE && event.isAllowedChatCharacter()) {
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
         } else if (event.isCut()) {
            this.minecraft.keyboardHandler.setClipboard(this.editText);
            this.editText = "";
            this.editCursor = 0;
            this.selectAll = false;
            this.applyEditedText();
            return true;
         } else if (event.isPaste()) {
            this.insertText(sanitizeText(this.minecraft.keyboardHandler.getClipboard()));
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

                  this.selectAll = false;
                  this.applyEditedText();
                  return true;
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

                  this.selectAll = false;
                  this.applyEditedText();
                  return true;
               case 262:
                  this.editCursor = Math.min(this.editText.length(), this.editCursor + 1);
                  this.selectAll = false;
                  return true;
               case 263:
                  this.editCursor = Math.max(0, this.editCursor - 1);
                  this.selectAll = false;
                  return true;
               case 268:
                  this.editCursor = 0;
                  this.selectAll = false;
                  return true;
               case 269:
                  this.editCursor = this.editText.length();
                  this.selectAll = false;
                  return true;
            }
         }
      } else {
         this.finishTextEditing();
         return true;
      }
   }

   private void focusSearch() {
      if (this.textTarget != ClickGuiScreen.TextTarget.SEARCH) {
         this.finishTextEditing();
         this.textTarget = ClickGuiScreen.TextTarget.SEARCH;
         this.editingString = null;
         this.editText = this.search;
         this.editCursor = this.editText.length();
         this.selectAll = false;
      }
   }

   private void focusString(StringSetting setting) {
      if (this.textTarget != ClickGuiScreen.TextTarget.STRING || this.editingString != setting) {
         this.finishTextEditing();
         this.textTarget = ClickGuiScreen.TextTarget.STRING;
         this.editingString = setting;
         this.editText = (String)setting.get();
         this.editCursor = this.editText.length();
         this.selectAll = false;
      }
   }

   private void focusColor(ColorSetting setting) {
      if (this.textTarget != ClickGuiScreen.TextTarget.COLOR || this.editingColor != setting) {
         this.finishTextEditing();
         this.textTarget = ClickGuiScreen.TextTarget.COLOR;
         this.editingString = null;
         this.editingColor = setting;
         this.colorBeforeEdit = setting.get();
         this.editText = setting.hex();
         this.editCursor = this.editText.length();
         this.selectAll = true;
      }
   }

   private void focusConfigName() {
      if (this.textTarget != ClickGuiScreen.TextTarget.CONFIG) {
         this.finishTextEditing();
         this.textTarget = ClickGuiScreen.TextTarget.CONFIG;
         this.editingString = null;
         this.editText = this.configName;
         this.editCursor = this.editText.length();
         this.selectAll = false;
      }
   }

   private void finishTextEditing() {
      if (this.textTarget != ClickGuiScreen.TextTarget.NONE) {
         if (this.textTarget == ClickGuiScreen.TextTarget.COLOR && this.editingColor != null) {
            Color parsed = this.editingColor.parseHex(this.editText);
            this.editingColor.set(parsed != null ? parsed : this.colorBeforeEdit);
         } else {
            this.applyEditedText();
         }

         this.textTarget = ClickGuiScreen.TextTarget.NONE;
         this.editingString = null;
         this.editingColor = null;
         this.colorBeforeEdit = null;
         this.editText = "";
         this.editCursor = 0;
         this.selectAll = false;
      }
   }

   private void insertText(String inserted) {
      if (inserted != null && !inserted.isEmpty()) {
         String clean = sanitizeText(inserted);
         if (this.selectAll) {
            this.editText = "";
            this.editCursor = 0;
         }

         int maxLength = this.textTarget == ClickGuiScreen.TextTarget.STRING ? 256 : (this.textTarget == ClickGuiScreen.TextTarget.COLOR ? 9 : 64);
         int remaining = maxLength - this.editText.length();
         if (remaining <= 0) {
            this.selectAll = false;
         } else {
            if (clean.length() > remaining) {
               clean = clean.substring(0, remaining);
            }

            this.editText = this.editText.substring(0, this.editCursor) + clean + this.editText.substring(this.editCursor);
            this.editCursor += clean.length();
            this.selectAll = false;
            this.applyEditedText();
         }
      }
   }

   private void applyEditedText() {
      if (this.textTarget == ClickGuiScreen.TextTarget.SEARCH) {
         this.search = this.editText;
         this.moduleScroll = 0.0F;
      } else if (this.textTarget == ClickGuiScreen.TextTarget.CONFIG) {
         this.configName = this.editText;
      } else if (this.textTarget == ClickGuiScreen.TextTarget.STRING && this.editingString != null) {
         this.editingString.set(this.editText);
      } else if (this.textTarget == ClickGuiScreen.TextTarget.COLOR && this.editingColor != null) {
         Color parsed = this.editingColor.parseHex(this.editText);
         if (parsed != null) {
            this.editingColor.set(parsed);
         }
      }

   }

   private void updateNumber(double mouseX) {
      float fraction = ClickGuiLayout.clamp((float)((mouseX - (double)this.draggingTrackX) / (double)this.draggingTrackWidth), 0.0F, 1.0F);
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

   private void updateColor(double mouseX) {
      if (this.draggingColor != null && this.draggingColorChannel >= 0) {
         int value = Math.round(ClickGuiLayout.clamp((float)((mouseX - (double)this.draggingTrackX) / (double)this.draggingTrackWidth), 0.0F, 1.0F) * 255.0F);
         Color current = this.draggingColor.get();
         int red = this.draggingColorChannel == 0 ? value : current.getRed();
         int green = this.draggingColorChannel == 1 ? value : current.getGreen();
         int blue = this.draggingColorChannel == 2 ? value : current.getBlue();
         int alpha = this.draggingColorChannel == 3 ? value : current.getAlpha();
         this.draggingColor.set(new Color(red, green, blue, alpha));
      }
   }

   private void ensureSelection() {
      if (this.selectedCategory == null) {
         for(Category category : Category.values()) {
            if (category.guiVisible() && !ModuleManager.INSTANCE.modulesIn(category).isEmpty()) {
               this.selectedCategory = category;
               break;
            }
         }
      }

      if (this.selectedModule == null && this.selectedCategory != null) {
         List<Module> modules = ModuleManager.INSTANCE.modulesIn(this.selectedCategory);
         if (!modules.isEmpty()) {
            this.selectedModule = (Module)modules.getFirst();
         }
      }

   }

   private List<Module> filteredModules() {
      if (this.selectedCategory == null) {
         return List.of();
      } else {
         List<Module> source = ModuleManager.INSTANCE.modulesIn(this.selectedCategory);
         if (this.search.isBlank()) {
            return source;
         } else {
            String query = this.search.toLowerCase(Locale.ROOT).trim();
            List<Module> result = new ArrayList();

            for(Module module : source) {
               String info = module.getInfo();
               if (module.name().toLowerCase(Locale.ROOT).contains(query) || module.displayName().toLowerCase(Locale.ROOT).contains(query) || info != null && info.toLowerCase(Locale.ROOT).contains(query)) {
                  result.add(module);
               }
            }

            return result;
         }
      }
   }

   private List<Setting<?>> visibleSettings() {
      if (this.selectedModule == null) {
         return List.of();
      } else {
         List<Setting<?>> result = new ArrayList();

         for(Setting<?> setting : this.selectedModule.settings()) {
            if (setting.visible()) {
               result.add(setting);
            }
         }

         return result;
      }
   }

   private void clampScrolls(ClickGuiLayout layout) {
      float moduleContent = layout.moduleContentHeight(this.filteredModules().size());
      this.moduleScroll = ClickGuiLayout.clamp(this.moduleScroll, 0.0F, Math.max(0.0F, moduleContent - layout.moduleListHeight()));
      float settingsWidth = Math.max(1.0F, this.panelSettingsWidth(layout) - 16.0F);
      float settingsContent = settingsContentHeight(this.visibleSettings(), settingsWidth);
      this.settingsScroll = ClickGuiLayout.clamp(this.settingsScroll, 0.0F, Math.max(0.0F, settingsContent - this.panelSettingsHeight(layout)));
   }

   private static float settingsContentHeight(List<Setting<?>> settings, float width) {
      float height = 0.0F;
      int columns = settingColumns(width);

      for(int index = 0; index < settings.size(); index += columns) {
         height += settingRowHeight(settings, index, columns);
      }

      return height;
   }

   private static int settingColumns(float width) {
      return width >= 300.0F ? 2 : 1;
   }

   private static float settingColumnWidth(float width, int columns) {
      return columns == 2 ? Math.max(1.0F, (width - 8.0F) * 0.5F) : Math.max(1.0F, width);
   }

   private static float settingRowHeight(List<Setting<?>> settings, int start, int columns) {
      float height = 0.0F;

      for(int column = 0; column < columns && start + column < settings.size(); ++column) {
         height = Math.max(height, settingHeight((Setting)settings.get(start + column)));
      }

      return height;
   }

   private static float settingHeight(Setting<?> setting) {
      if (!(setting instanceof IntSetting) && !(setting instanceof DoubleSetting)
            && !(setting instanceof EnumSetting<?> enumSetting && enumSetting.isSlider())) {
         if (setting instanceof ColorSetting) {
            ColorSetting colorSetting = (ColorSetting)setting;
            return ColorPickerState.height(colorSetting, 45.0F, 22.0F, ColorPickerState.paletteHeight(124.0F));
         } else if (setting instanceof ButtonSetting) {
            return 50.0F;
         } else {
            return MusicPresetPreview.matches(setting) ? 71.0F : 48.0F;
         }
      } else {
         return 60.0F;
      }
   }

   private static float categoryRowHeight(ClickGuiLayout layout) {
      float available = accountY(layout) - (layout.y() + layout.categoryTop()) - 4.0F;
      return Math.max(1.0F, Math.min(38.0F, available / ((float)Category.guiVisibleCount() + 1.0F)));
   }

   private static float accountY(ClickGuiLayout layout) {
      return layout.y() + layout.height() - 48.0F - 6.0F;
   }

   private static String categoryIcon(Category category) {
      return CategoryGlyphs.forCategory(category);
   }

   private static int visibleCategoryIndex(Category selected) {
      int index = 0;

      for(Category category : Category.values()) {
         if (category.guiVisible()) {
            if (category == selected) {
               return index;
            }

            ++index;
         }
      }

      return -1;
   }

   private static String tr(String suffix, String fallback) {
      return TranslationKey.of("setsuna.gui." + suffix, fallback).get();
   }

   private static String trState(String suffix, String fallback) {
      return TranslationKey.of("setsuna.state." + suffix, fallback).get();
   }

   private static float topSearchX(ClickGuiLayout layout) {
      return layout.x() + (layout.width() >= 360.0F ? 116.0F : 48.0F);
   }

   private static float topSearchY(ClickGuiLayout layout) {
      return layout.y() + (layout.headerHeight() - topSearchHeight(layout)) * 0.5F;
   }

   private static float topSearchWidth(ClickGuiLayout layout) {
      float right = layout.x() + layout.width() - (layout.width() >= 360.0F ? 104.0F : 12.0F);
      return Math.max(1.0F, right - topSearchX(layout));
   }

   private static float topSearchHeight(ClickGuiLayout layout) {
      return Math.min(24.0F, Math.max(18.0F, layout.headerHeight() - 12.0F));
   }

   private static float approach(float current, float target, float delta, float speed) {
      return current + (target - current) * (1.0F - (float)Math.exp((double)(-speed * delta)));
   }

   private static float easeOutCubic(float value) {
      float clamped = Math.max(0.0F, Math.min(1.0F, value));
      float inverse = 1.0F - clamped;
      return 1.0F - inverse * inverse * inverse;
   }

   private String selectedConfigName() {
      return this.configName.isBlank() ? ConfigManager.INSTANCE.currentProfile() : this.configName.trim();
   }

   private void setConfigStatus(String message, boolean error) {
      this.configStatus = message == null ? "" : message;
      this.configStatusError = error;
   }

   private static float columnPadding(float width, float preferred) {
      return Math.min(preferred, Math.max(1.0F, width * 0.1F));
   }

   private static float colorTrackX(float rowX) {
      return rowX + 30.0F;
   }

   private static float colorHexFieldWidth(ColorSetting setting) {
      return setting.allowAlpha() ? 74.0F : 62.0F;
   }

   private static float colorHexFieldX(float rowX, float rowWidth, float padding, float fieldWidth) {
      return rowX + rowWidth - padding - 22.0F - fieldWidth;
   }

   private static float colorTrackWidth(float rowWidth) {
      return Math.max(12.0F, rowWidth - 38.0F);
   }

   private static float numberTrackWidth(float rowWidth, float padding) {
      return Math.max(24.0F, rowWidth - padding * 2.0F);
   }

   private static void drawSliderLayer(Canvas canvas, float start, float end, float gapStart, float gapEnd, float y, float height, int color) {
      if (!(end <= start)) {
         if (!(gapEnd <= start) && !(gapStart >= end)) {
            float leftEnd = Math.min(end, gapStart);
            if (leftEnd > start) {
               float fadeStart = Math.max(start, leftEnd - 7.0F);
               if (fadeStart > start) {
                  SkijaUi.rounded(canvas, start, y, fadeStart - start, height, height * 0.5F, color);
               }

               drawHorizontalGradient(canvas, fadeStart, y, leftEnd - fadeStart, height, color, UiTheme.withAlpha(color, 0));
            }

            float rightStart = Math.max(start, gapEnd);
            if (end > rightStart) {
               float fadeEnd = Math.min(end, rightStart + 7.0F);
               drawHorizontalGradient(canvas, rightStart, y, fadeEnd - rightStart, height, UiTheme.withAlpha(color, 0), color);
               if (end > fadeEnd) {
                  SkijaUi.rounded(canvas, fadeEnd, y, end - fadeEnd, height, height * 0.5F, color);
               }
            }

         } else {
            SkijaUi.rounded(canvas, start, y, end - start, height, height * 0.5F, color);
         }
      }
   }

   private static void drawHorizontalGradient(Canvas canvas, float x, float y, float width, float height, int start, int end) {
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         try {
            Shader shader = Shader.makeLinearGradient(x, y, x + width, y, new int[]{start, end});

            try {
               SLIDER_PAINT.setShader(shader).setColor(-1).setAlpha(255);
               canvas.drawRect(Rect.makeXYWH(x, y, width, height), SLIDER_PAINT);
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
            SLIDER_PAINT.setShader((Shader)null).setAlpha(255);
         }

      }
   }

   private void restoreMemory() {
      if (MEMORY.saved) {
         this.selectedCategory = MEMORY.selectedCategory;
         this.selectedModule = MEMORY.selectedModule;
         if (this.selectedModule != null && (this.selectedCategory == null || !ModuleManager.INSTANCE.modulesIn(this.selectedCategory).contains(this.selectedModule))) {
            this.selectedModule = null;
         }

         this.search = MEMORY.search;
         this.moduleScroll = MEMORY.moduleScroll;
         this.settingsScroll = MEMORY.settingsScroll;
         this.configScroll = MEMORY.configScroll;
         this.configsOpen = MEMORY.configsOpen;
         this.settingsPage = MEMORY.settingsPage;
         this.configName = MEMORY.configName;
      }
   }

   private void saveMemory() {
      MEMORY.saved = true;
      MEMORY.selectedCategory = this.selectedCategory;
      MEMORY.selectedModule = this.selectedModule;
      MEMORY.search = this.search;
      MEMORY.moduleScroll = this.moduleScroll;
      MEMORY.settingsScroll = this.settingsScroll;
      MEMORY.configScroll = this.configScroll;
      MEMORY.configsOpen = this.configsOpen;
      MEMORY.settingsPage = this.settingsPage;
      MEMORY.configName = this.configName;
   }

   private static String keyText(int key) {
      return KeyBindText.of(key);
   }

   private static String keyTextOrNone(int key) {
      String value = keyText(key);
      return value.isEmpty() ? tr("none", "None") : value;
   }

   private static String trimDouble(double value) {
      String text = String.format(Locale.ROOT, "%.3f", value);

      int end;
      for(end = text.length(); end > 0 && text.charAt(end - 1) == '0'; --end) {
      }

      if (end > 0 && text.charAt(end - 1) == '.') {
         --end;
      }

      return text.substring(0, end);
   }

   private static long enabledCount() {
      return ModuleManager.INSTANCE.modules().stream().filter(Module::isToggleable).filter(Module::isEnabled).count();
   }

   private static String fitText(String text, float maxWidth, float size, boolean bold) {
      if (text != null && !text.isEmpty() && !(maxWidth <= 0.0F)) {
         float width = bold ? SkijaUi.boldTextWidth(text, size) : SkijaUi.textWidth(text, size);
         if (width <= maxWidth) {
            return text;
         } else {
            String ellipsis = "...";
            float ellipsisWidth = bold ? SkijaUi.boldTextWidth(ellipsis, size) : SkijaUi.textWidth(ellipsis, size);
            if (ellipsisWidth > maxWidth) {
               return "";
            } else {
               int end = text.length();

               while(end > 0) {
                  --end;
                  String var10000 = text.substring(0, end);
                  String candidate = var10000 + ellipsis;
                  float candidateWidth = bold ? SkijaUi.boldTextWidth(candidate, size) : SkijaUi.textWidth(candidate, size);
                  if (candidateWidth <= maxWidth) {
                     return candidate;
                  }
               }

               return ellipsis;
            }
         }
      } else {
         return "";
      }
   }

   private static String sanitizeText(String input) {
      if (input != null && !input.isEmpty()) {
         StringBuilder result = new StringBuilder(input.length());
         input.codePoints().forEach((codePoint) -> {
            if (codePoint != 10 && codePoint != 13) {
               if (!Character.isISOControl(codePoint)) {
                  result.appendCodePoint(codePoint);
               }
            } else {
               result.append(' ');
            }

         });
         return result.toString();
      } else {
         return "";
      }
   }

   private float configuredGuiScale() {
      return ClickGuiLayout.clamp((float)(Integer)ClickGui.INSTANCE.guiScale.get() / 100.0F, 0.65F, 1.25F);
   }

   private double logicalX(double coordinate) {
      return this.inverseGuiScale(this.rawLogical(coordinate), (double)this.logicalWidth() * (double)0.5F);
   }

   private double logicalY(double coordinate) {
      return this.inverseGuiScale(this.rawLogical(coordinate), (double)this.logicalHeight() * (double)0.5F);
   }

   private double rawLogical(double coordinate) {
      return coordinate * (double)this.minecraftGuiScale() / (double)2.0F;
   }

   private double inverseGuiScale(double coordinate, double center) {
      return center + (coordinate - center) / (double)this.panelRenderScale();
   }

   private float panelRenderScale() {
      return this.currentLayout().fittedScale(this.logicalWidth(), this.logicalHeight(), this.activeGuiScale)
            * (0.95F + easeOutCubic(this.visibilityProgress) * 0.05F);
   }

   private int logicalWidth() {
      return Math.max(1, (int)Math.ceil((double)((float)this.minecraft.getWindow().getWidth() / 2.0F)));
   }

   private int logicalHeight() {
      return Math.max(1, (int)Math.ceil((double)((float)this.minecraft.getWindow().getHeight() / 2.0F)));
   }

   private float minecraftGuiScale() {
      return (float)this.minecraft.getWindow().getWidth() / (float)Math.max(1, this.width);
   }

   private ClickGuiLayout currentLayout() {
      return ClickGuiLayout.forViewport(this.logicalWidth(), this.logicalHeight());
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   public void onClose() {
      if (!this.closing && !this.closeFinished) {
         this.finishTextEditing();
         this.saveMemory();
         this.draggingNumber = null;
         this.draggingColor = null;
         this.draggingColorChannel = -1;
         this.draggingPanelEsp = null;
         this.capturingKeybind = null;
         this.capturingModule = null;
         this.closing = true;
      }
   }

   private boolean completeCloseWhenReady() {
      if (this.closing && !(this.visibilityProgress > 0.025F)) {
         this.closeFinished = true;
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
      this.saveMemory();
      if (!this.closeFinished) {
         ConfigManager.INSTANCE.save();
      }

      super.removed();
   }

   public boolean isPauseScreen() {
      return false;
   }

   static {
      SLIDER_PAINT = (new Paint()).setAntiAlias(true);
      PANEL_LAYER_PAINT = (new Paint()).setAntiAlias(true);
      ESP_SKIN_PAINT = (new Paint()).setAntiAlias(true);
      ESP_OUTLINE_PAINT = (new Paint()).setAntiAlias(true).setMode(PaintMode.STROKE);
      MEMORY = new PanelMemory();
   }

   private static final class PanelMemory {
      private boolean saved;
      private Category selectedCategory;
      private Module selectedModule;
      private String search = "";
      private float moduleScroll;
      private float settingsScroll;
      private float configScroll;
      private boolean configsOpen;
      private boolean settingsPage;
      private String configName = "";
   }

   private static enum PanelEspPart {
      PLAYER,
      HEALTH,
      ARMOR,
      EQUIPMENT,
      EFFECTS;
   }

   private static record PanelEspHit(PanelEspPart part, float x, float y, float width, float height) {
   }

   private static enum TextTarget {
      NONE,
      SEARCH,
      STRING,
      CONFIG,
      COLOR;
   }
}
