package com.setsuna.ui.clickgui;

import com.setsuna.Setsuna;
import com.setsuna.config.ConfigManager;
import com.setsuna.manager.AltManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.module.modules.ClickGui;
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
import com.setsuna.util.KeyBindText;
import com.setsuna.util.client.KeybindUtils;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
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
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FluxClickGuiScreen extends Screen implements SkijaScreen {
   private static final float SIDEBAR_WIDTH = 100.0F;
   private static final float BRAND_HEIGHT = 35.0F;
   private static final float CATEGORY_HEIGHT = 25.0F;
   private static final float ACCOUNT_HEIGHT = 25.0F;
   private static final float MODULE_HEIGHT = 25.0F;
   private static final float MODULE_GAP = 15.0F;
   private static final float MODULE_TOP = 25.0F;
   private static final float SETTINGS_PADDING = 9.0F;
   private static final float FONT = 8.0F;
   private static final float FONT_SMALL = 7.0F;
   private static final float FONT_BRAND = 10.0F;
   private static final float FONT_VERSION = 6.5F;
   private static final float SCROLL_STEP = 28.0F;
   private static final float EXPAND_SPEED = 13.0F;
   private static final float TOGGLE_SPEED = 16.0F;
   private static final int TEXT = -1;
   private static final int TEXT_DIM = -6447715;
   private static final int TEXT_OFF = -9473158;
   private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("setsuna", "textures/hud/watermark_logo.png");
   private static final Paint IMAGE_PAINT;
   private static final Memory MEMORY;
   private final Screen parent;
   private final Map<Category, Float> scrollByCategory;
   private final Map<Module, Float> expandProgress;
   private final Map<Module, Float> toggleProgress;
   private final List<HitTarget> hits;
   private Category selectedCategory;
   private Module selectedModule;
   private float windowX;
   private float windowY;
   private float windowWidth;
   private float windowHeight;
   private float activeScale;
   private float categorySelectorY;
   private float renderMouseX;
   private float renderMouseY;
   private float animationDelta;
   private long lastFrame;
   private boolean draggingWindow;
   private boolean resizingWindow;
   private float dragOffsetX;
   private float dragOffsetY;
   private float resizeStartX;
   private float resizeStartY;
   private float resizeStartWidth;
   private float resizeStartHeight;
   private Setting<?> draggingNumber;
   private ColorSetting draggingColor;
   private int draggingColorChannel;
   private float draggingTrackX;
   private float draggingTrackWidth;
   private Module capturingModule;
   private KeybindSetting capturingKeybind;
   private TextTarget textTarget;
   private StringSetting editingString;
   private ColorSetting editingColor;
   private Color colorBeforeEdit;
   private String editText;
   private int editCursor;
   private boolean selectAll;
   private boolean configsOpen;
   private float configProgress;
   private float configScroll;
   private boolean importVisualAndHud;
   private boolean importKeybinds;
   private String configName;
   private String configStatus;
   private boolean configError;

   public FluxClickGuiScreen() {
      this((Screen)null);
   }

   public FluxClickGuiScreen(Screen parent) {
      super(Component.literal("Setsuna ClickGUI / Flux"));
      this.scrollByCategory = new EnumMap(Category.class);
      this.expandProgress = new IdentityHashMap();
      this.toggleProgress = new IdentityHashMap();
      this.hits = new ArrayList();
      this.activeScale = 1.0F;
      this.categorySelectorY = Float.NaN;
      this.lastFrame = System.nanoTime();
      this.draggingColorChannel = -1;
      this.textTarget = FluxClickGuiScreen.TextTarget.NONE;
      this.editText = "";
      this.importVisualAndHud = true;
      this.importKeybinds = true;
      this.configName = "";
      this.configStatus = "";
      this.parent = parent;
   }

   protected void init() {
      this.activeScale = this.configuredScale();
      FluxClickGuiLayout.Bounds bounds = FluxClickGuiLayout.resolve(this.logicalWidth(), this.logicalHeight(), MEMORY.x, MEMORY.y, MEMORY.width, MEMORY.height);
      this.applyBounds(bounds);
      this.selectInitialCategory();
      String current = ConfigManager.INSTANCE.currentProfile();
      this.configName = current == null ? "" : current;
      this.lastFrame = System.nanoTime();
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      graphics.blurBeforeThisStratum();
      graphics.fill(0, 0, this.width, this.height, UiTheme.themedBackdrop(48));
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.renderMouseX = (float)this.logical((double)mouseX);
      this.renderMouseY = (float)this.logical((double)mouseY);
      if (SkijaRenderer.hasFailed()) {
         graphics.fill(0, 0, this.width, this.height, UiTheme.themedBackdrop(208));
         graphics.text(this.font, "Skija renderer failed - check latest.log", 8, 8, -1, false);
      }

   }

   public void renderSkija(Canvas canvas) {
      long now = System.nanoTime();
      this.animationDelta = Math.min(0.05F, Math.max(0.0F, (float)(now - this.lastFrame) / 1.0E9F));
      this.lastFrame = now;
      this.clampWindow();
      this.hits.clear();
      canvas.save();

      try {
         canvas.scale(this.activeScale, this.activeScale);
         SkijaUi.fill(canvas, 0.0F, 0.0F, this.logicalWidth() + 1.0F, this.logicalHeight() + 1.0F, UiTheme.themedBackdrop(48));
         this.renderWindow(canvas);
         this.renderConfigPanel(canvas);
      } finally {
         canvas.restore();
      }

   }

   private void renderWindow(Canvas canvas) {
      SkijaUi.dropShadowRounded(canvas, this.windowX, this.windowY, this.windowWidth, this.windowHeight, 2.0F, 2.0F, 15.0F, -1610612736);
      SkijaUi.rounded(canvas, this.windowX, this.windowY, this.windowWidth, this.windowHeight, 2.0F, UiTheme.themedSurface(255));
      canvas.save();

      try {
         canvas.clipRRect(RRect.makeXYWH(this.windowX, this.windowY, this.windowWidth, this.windowHeight, 2.0F), ClipMode.INTERSECT, true);
         SkijaUi.fill(canvas, this.windowX, this.windowY, 100.0F, this.windowHeight, UiTheme.themedSurfaceAlt(255));
         this.renderBrand(canvas);
         this.renderCategories(canvas);
         this.renderAccount(canvas);
         SkijaUi.gradient(canvas, this.windowX + 100.0F, this.windowY, 10.0F, this.windowHeight, 637534208, 0, false, 0.0F);
         this.renderModules(canvas);
      } finally {
         canvas.restore();
      }

      float cornerX = this.windowX + this.windowWidth - 8.0F;
      float cornerY = this.windowY + this.windowHeight - 8.0F;
      int resizeColor = inside(this.renderMouseX, this.renderMouseY, cornerX - 2.0F, cornerY - 2.0F, 10.0F, 10.0F) ? accent() : -9473158;
      SkijaUi.line(canvas, cornerX + 2.0F, cornerY - 1.0F, cornerX - 1.0F, cornerY + 2.0F, 1.0F, resizeColor);
      SkijaUi.line(canvas, cornerX + 4.0F, cornerY, cornerX, cornerY + 4.0F, 1.0F, resizeColor);
      this.addHit(FluxClickGuiScreen.HitKind.RESIZE, (Object)null, 0, (String)null, this.windowX + this.windowWidth - 10.0F, this.windowY + this.windowHeight - 10.0F, 10.0F, 10.0F);
   }

   private void renderBrand(Canvas canvas) {
      this.drawLogo(canvas, this.windowX + 8.0F, this.windowY + 9.0F, 16.0F);
      String brand = Setsuna.clientName().toUpperCase(Locale.ROOT);
      String version = "B11".toUpperCase(Locale.ROOT);
      float brandX = this.windowX + 27.0F;
      float versionWidth = SkijaUi.textWidth(version, 6.5F);
      float versionX = this.windowX + 100.0F - 5.0F - versionWidth;
      float brandWidth = Math.max(0.0F, versionX - brandX - 4.0F);
      drawBold(canvas, fit(brand, brandWidth, 10.0F, true), brandX, this.windowY + 7.0F, 18.0F, -1, 10.0F);
      draw(canvas, version, versionX, this.windowY + 12.0F, 12.0F, -2302498, 6.5F);
      this.addHit(FluxClickGuiScreen.HitKind.DRAG, (Object)null, 0, (String)null, this.windowX, this.windowY, 100.0F, 35.0F);
   }

   private void renderCategories(Canvas canvas) {
      List<Category> categories = visibleCategories();
      int selectedIndex = Math.max(0, categories.indexOf(this.selectedCategory));
      float targetY = this.windowY + 35.0F + (float)selectedIndex * 25.0F;
      if (!Float.isFinite(this.categorySelectorY)) {
         this.categorySelectorY = targetY;
      }

      this.categorySelectorY = this.animate(this.categorySelectorY, targetY, 18.0F);
      SkijaUi.fill(canvas, this.windowX, this.categorySelectorY, 100.0F, 25.0F, accent());
      float y = this.windowY + 35.0F;

      for(Category category : categories) {
         boolean selected = category == this.selectedCategory;
         int color = selected ? -1 : -9473158;
         String glyph = CategoryGlyphs.forCategory(category);
         SkijaUi.icon(canvas, glyph, this.windowX + 10.0F, y, 25.0F, color, 12.0F, SkijaUi.IconSet.LUCIDE);
         ClickGuiText.drawVerticallyCentered(canvas, category.displayName(), this.windowX + 27.0F, y, 66.0F, 25.0F, 9.0F, color, 8.0F, false);
         this.addHit(FluxClickGuiScreen.HitKind.CATEGORY, category, 0, (String)null, this.windowX, y, 100.0F, 25.0F);
         y += 25.0F;
      }

   }

   private void renderAccount(Canvas canvas) {
      float y = this.windowY + this.windowHeight - 25.0F;
      SkijaUi.fill(canvas, this.windowX, y, 100.0F, 25.0F, UiTheme.themedRaised(255));
      SkijaUi.fill(canvas, this.windowX, y, 100.0F, 1.0F, 402653184);
      this.drawAvatar(canvas, this.windowX + 4.0F, y + 2.0F, 21.0F);
      String username = AltManager.INSTANCE.getCurrentUsername();
      if (username == null || username.isBlank()) {
         username = "Player";
      }

      drawBold(canvas, fit(username, 65.0F, 7.0F, true), this.windowX + 30.0F, y + 6.0F, 13.0F, -1, 7.0F);
   }

   private void renderModules(Canvas canvas) {
      if (this.selectedCategory != null) {
         List<Module> modules = ModuleManager.INSTANCE.modulesIn(this.selectedCategory);
         float listX = this.windowX + 100.0F + 20.0F;
         float listWidth = Math.max(80.0F, this.windowWidth - 100.0F - 40.0F);
         float viewportTop = this.windowY;
         float viewportHeight = this.windowHeight;
         float contentHeight = 35.0F;

         for(Module module : modules) {
            float target = module == this.selectedModule ? 1.0F : 0.0F;
            float progress = this.animate((Float)this.expandProgress.getOrDefault(module, 0.0F), target, 13.0F);
            this.expandProgress.put(module, progress);
            float settingsHeight = this.settingsLayout(module, 0.0F, 0.0F, listWidth - 18.0F).height();
            contentHeight += 40.0F + settingsHeight * progress;
         }

         float scroll = FluxClickGuiLayout.clampScroll((Float)this.scrollByCategory.getOrDefault(this.selectedCategory, 0.0F), contentHeight, viewportHeight);
         this.scrollByCategory.put(this.selectedCategory, scroll);
         canvas.save();

         try {
            canvas.clipRect(Rect.makeXYWH(this.windowX + 100.0F, viewportTop, this.windowWidth - 100.0F, viewportHeight));
            float y = this.windowY + 25.0F - scroll;

            for(Module module : modules) {
               float progress = (Float)this.expandProgress.getOrDefault(module, 0.0F);
               SettingLayout layout = this.settingsLayout(module, listX + 9.0F, y + 25.0F, listWidth - 18.0F);
               float expandedHeight = layout.height() * progress;
               float cardHeight = 25.0F + expandedHeight;
               this.renderModuleCard(canvas, module, listX, y, listWidth, cardHeight, progress, layout, viewportTop, viewportHeight);
               y += cardHeight + 15.0F;
            }

            this.renderScrollBar(canvas, contentHeight, viewportHeight, scroll);
         } finally {
            canvas.restore();
         }

      }
   }

   private void renderModuleCard(Canvas canvas, Module module, float x, float y, float width, float height, float expansion, SettingLayout settings, float viewportTop, float viewportHeight) {
      boolean selected = module == this.selectedModule;
      boolean hovered = inside(this.renderMouseX, this.renderMouseY, x, y, width, 25.0F);
      int fill = hovered ? UiTheme.themedHover(255) : UiTheme.themedRaised(255);
      SkijaUi.rounded(canvas, x, y, width, height, 1.5F, fill);
      if (selected) {
         SkijaUi.outline(canvas, x - 0.5F, y - 0.5F, width + 1.0F, height + 1.0F, 1.5F, 1.0F, accent());
      }

      int titleColor = selected ? accent() : -6447715;
      String marker = selected ? "-" : "+";
      drawBold(canvas, marker, x + 9.0F, y, 25.0F, titleColor, 9.0F);
      float toggleSpace = module.isToggleable() ? 38.0F : 20.0F;
      ClickGuiText.drawVerticallyCentered(canvas, module.displayName(), x + 20.0F, y, width - 36.0F - toggleSpace, 25.0F, 9.0F, titleColor, 8.0F, false);
      this.addHit(FluxClickGuiScreen.HitKind.MODULE, module, 0, (String)null, x, y, width, 25.0F);
      if (module.isToggleable()) {
         float target = module.isEnabled() ? 1.0F : 0.0F;
         float progress = this.animate((Float)this.toggleProgress.getOrDefault(module, target), target, 16.0F);
         this.toggleProgress.put(module, progress);
         float trackX = x + width - 29.0F;
         float trackY = y + 8.5F;
         SkijaUi.rounded(canvas, trackX, trackY, 18.0F, 8.0F, 4.0F, -1710619);
         float knobX = trackX - 1.0F + progress * 10.0F;
         SkijaUi.rounded(canvas, knobX, trackY - 1.0F, 10.0F, 10.0F, 5.0F, progress > 0.5F ? accent() : -1);
         SkijaUi.outline(canvas, knobX, trackY - 1.0F, 10.0F, 10.0F, 5.0F, 0.8F, progress > 0.5F ? accent() : -6316129);
         this.addHit(FluxClickGuiScreen.HitKind.MODULE_TOGGLE, module, 0, (String)null, trackX - 3.0F, trackY - 4.0F, 24.0F, 16.0F);
      }

      if (!(expansion <= 0.01F) && !(settings.height() <= 0.0F)) {
         canvas.save();

         try {
            canvas.clipRect(Rect.makeXYWH(x, y + 25.0F, width, Math.max(0.0F, settings.height() * expansion)));
            this.renderSettings(canvas, module, settings, expansion >= 0.96F, viewportTop, viewportHeight);
         } finally {
            canvas.restore();
         }

      }
   }

   private void renderSettings(Canvas canvas, Module module, SettingLayout layout, boolean interactive, float viewportTop, float viewportHeight) {
      for(Control control : layout.controls()) {
         switch (control.kind().ordinal()) {
            case 0:
               draw(canvas, "No Settings.", control.x(), control.y(), control.height(), -6447715, 8.0F);
               break;
            case 1:
               this.renderBindControl(canvas, module, control, interactive);
               break;
            case 2:
               drawWrappedLabel(canvas, control.setting().displayName(), control, accent(), 8.0F);
               break;
            case 3:
               this.renderEnumOption(canvas, control, interactive);
               break;
            case 4:
               this.renderNumber(canvas, control, interactive);
               break;
            case 5:
               this.renderBoolean(canvas, control, interactive);
               break;
            case 6:
               this.renderFont(canvas, control, interactive);
               break;
            case 7:
               this.renderColor(canvas, control, interactive);
               break;
            case 8:
               this.renderColorChannel(canvas, control, interactive);
               break;
            case 9:
               this.renderString(canvas, control, interactive);
               break;
            case 10:
               this.renderKeybind(canvas, control, interactive);
               break;
            case 11:
               this.renderButton(canvas, control, interactive);
               break;
            case 12:
               this.renderValue(canvas, control);
         }
      }

   }

   private void renderBindControl(Canvas canvas, Module module, Control control, boolean interactive) {
      draw(canvas, "Bind", control.x(), control.y(), control.height(), -6447715, 7.0F);
      String value = this.capturingModule == module ? "..." : KeyBindText.of(module.keyBind());
      if (value.isBlank()) {
         value = "None";
      }

      drawRight(canvas, value, control.x(), control.y(), control.width(), control.height(), this.capturingModule == module ? accent() : -6447715, 7.0F);
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.MODULE_BIND, module, 0, (String)null, control);
      }

   }

   private void renderEnumOption(Canvas canvas, Control control, boolean interactive) {
      EnumSetting<?> setting = (EnumSetting)control.setting();
      boolean selected = ((Enum)setting.get()).ordinal() == control.index();
      float dotX = control.x() + 1.0F;
      float dotY = control.y() + (control.height() - 7.0F) * 0.5F;
      SkijaUi.outline(canvas, dotX, dotY, 7.0F, 7.0F, 3.5F, 1.0F, selected ? accent() : -3289651);
      if (selected) {
         SkijaUi.rounded(canvas, dotX + 2.0F, dotY + 2.0F, 3.0F, 3.0F, 1.5F, accent());
      }

      ClickGuiText.drawVerticallyCentered(canvas, control.text(), control.x() + 12.0F, control.y(), Math.max(1.0F, control.width() - 12.0F), control.height(), 8.0F, selected ? accent() : -6447715, 7.0F, false);
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.ENUM_OPTION, setting, control.index(), (String)null, control);
      }

   }

   private void renderNumber(Canvas canvas, Control control, boolean interactive) {
      Setting<?> setting = control.setting();
      float fraction = numberFraction(setting);
      String value = numberText(setting);
      ClickGuiText.drawVerticallyCentered(canvas, setting.displayName(), control.x(), control.y(), Math.max(1.0F, control.width() * 0.68F), Math.max(8.0F, control.height() - 10.0F), 8.0F, -6447715, 7.0F, false);
      drawRight(canvas, value, control.x(), control.y(), control.width(), 13.0F, -6447715, 7.0F);
      float trackY = control.y() + control.height() - 7.0F;
      SkijaUi.fill(canvas, control.x(), trackY, control.width(), 1.0F, UiTheme.themedRaised(255));
      SkijaUi.fill(canvas, control.x(), trackY, control.width() * fraction, 1.0F, accent());
      SkijaUi.rounded(canvas, control.x() + control.width() * fraction - 2.0F, trackY - 1.5F, 4.0F, 4.0F, 2.0F, accent());
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.NUMBER, setting, 0, (String)null, control.x(), trackY - 5.0F, control.width(), 11.0F);
      }

   }

   private void renderBoolean(Canvas canvas, Control control, boolean interactive) {
      BooleanSetting setting = (BooleanSetting)control.setting();
      boolean checked = (Boolean)setting.get();
      float boxY = control.y() + (control.height() - 8.0F) * 0.5F;
      SkijaUi.rounded(canvas, control.x(), boxY, 8.0F, 8.0F, 2.0F, checked ? accent() : -3289651);
      if (checked) {
         SkijaUi.line(canvas, control.x() + 2.0F, boxY + 4.0F, control.x() + 3.5F, boxY + 6.0F, 1.1F, -1);
         SkijaUi.line(canvas, control.x() + 3.5F, boxY + 6.0F, control.x() + 6.5F, boxY + 2.0F, 1.1F, -1);
      }

      ClickGuiText.drawVerticallyCentered(canvas, control.text(), control.x() + 12.0F, control.y(), Math.max(1.0F, control.width() - 12.0F), control.height(), 8.0F, checked ? accent() : -6447715, 7.0F, false);
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.BOOLEAN, setting, 0, (String)null, control);
      }

   }

   private void renderFont(Canvas canvas, Control control, boolean interactive) {
      FontSetting setting = (FontSetting)control.setting();
      drawWrappedLabel(canvas, setting.displayName(), control, -6447715, 7.0F);
      ClickGuiText.drawRightCentered(canvas, setting.displayValue(), control.x() + control.width(), control.y(), control.width() * 0.58F, control.height(), 8.0F, accent(), 7.0F, false);
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.FONT, setting, 0, (String)null, control);
      }

   }

   private void renderColor(Canvas canvas, Control control, boolean interactive) {
      ColorSetting setting = (ColorSetting)control.setting();
      boolean expanded = ColorPickerState.expanded(setting);
      float header = Math.max(17.0F, (float)ClickGuiText.lineCount(setting.displayName(), Math.max(1.0F, control.width() * 0.42F), 7.0F, false) * 8.0F);
      ClickGuiText.drawVerticallyCentered(canvas, setting.displayName(), control.x(), control.y(), Math.max(1.0F, control.width() * 0.42F), header, 8.0F, -6447715, 7.0F, false);
      float swatchX = control.x() + control.width() - 12.0F;
      SkijaUi.rounded(canvas, swatchX, control.y() + 3.0F, 10.0F, Math.max(8.0F, header - 6.0F), 2.0F, setting.argb());
      SkijaUi.outline(canvas, swatchX, control.y() + 3.0F, 10.0F, control.height() - 6.0F, 2.0F, 0.7F, 1728053247);
      if (!expanded) {
         drawRight(canvas, "Set Color", control.x(), control.y(), control.width() - 17.0F, header, accent(), 7.0F);
      } else {
         float modeY = control.y() + header + 3.0F;
         float modeWidth = Math.min(116.0F, control.width());
         ColorPickerState.drawModeSelection(canvas, control.x(), modeY, modeWidth, ColorPickerState.mode(setting), -6447715, accent());
         if (ColorPickerState.mode(setting) == ColorPickerState.Mode.PALETTE) {
            ColorPickerState.drawPalette(canvas, control.x(), modeY + 16.0F, control.width(), 36.0F, setting, -1);
         } else {
            String value = this.editingColor == setting ? this.textWithCursor() : setting.hex();
            drawRight(canvas, value, control.x(), control.y(), control.width() - 17.0F, header, this.editingColor == setting ? accent() : -6447715, 7.0F);
            int[] values = new int[]{setting.get().getRed(), setting.get().getGreen(), setting.get().getBlue(), setting.get().getAlpha()};
            int count = setting.allowAlpha() ? 4 : 3;
            for (int i = 0; i < count; i++) {
               float channelY = modeY + 16.0F + i * 11.0F;
               SkijaUi.fill(canvas, control.x() + 13.0F, channelY + 5.0F, Math.max(20.0F, control.width() - 42.0F), 1.0F, -9999502);
               SkijaUi.fill(canvas, control.x() + 13.0F, channelY + 5.0F, Math.max(20.0F, control.width() - 42.0F) * values[i] / 255.0F, 1.0F, new int[]{-495247, -11870592, -10443270, -3025445}[i]);
               draw(canvas, new String[]{"R", "G", "B", "A"}[i] + values[i], control.x(), channelY, 11.0F, -6447715, 6.5F);
            }
         }
      }
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.COLOR, setting, 0, (String)null, control);
      }

   }

   private void renderColorChannel(Canvas canvas, Control control, boolean interactive) {
      ColorSetting setting = (ColorSetting)control.setting();
      Color color = setting.get();
      int[] values = new int[]{color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
      int[] colors = new int[]{-495247, -11870592, -10443270, -3025445};
      String[] labels = new String[]{"R", "G", "B", "A"};
      int index = control.index();
      float trackX = control.x() + 13.0F;
      float trackWidth = Math.max(20.0F, control.width() - 42.0F);
      float trackY = control.y() + control.height() * 0.5F;
      drawBold(canvas, labels[index], control.x(), control.y(), control.height(), colors[index], 7.0F);
      SkijaUi.fill(canvas, trackX, trackY, trackWidth, 1.0F, -9999502);
      SkijaUi.fill(canvas, trackX, trackY, trackWidth * (float)values[index] / 255.0F, 1.0F, colors[index]);
      SkijaUi.rounded(canvas, trackX + trackWidth * (float)values[index] / 255.0F - 1.5F, trackY - 2.0F, 3.0F, 5.0F, 1.5F, colors[index]);
      drawRight(canvas, Integer.toString(values[index]), control.x(), control.y(), control.width(), control.height(), -6447715, 7.0F);
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.COLOR_CHANNEL, setting, index, (String)null, trackX, control.y(), trackWidth, control.height());
      }

   }

   private void renderString(Canvas canvas, Control control, boolean interactive) {
      StringSetting setting = (StringSetting)control.setting();
      drawWrappedLabel(canvas, setting.displayName(), control, -6447715, 7.0F);
      String value = this.editingString == setting ? this.textWithCursor() : (String)setting.get();
      ClickGuiText.drawRightCentered(canvas, value, control.x() + control.width(), control.y(), control.width() * 0.58F, control.height(), 8.0F, this.editingString == setting ? accent() : -6447715, 7.0F, false);
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.STRING, setting, 0, (String)null, control);
      }

   }

   private void renderKeybind(Canvas canvas, Control control, boolean interactive) {
      KeybindSetting setting = (KeybindSetting)control.setting();
      drawWrappedLabel(canvas, setting.displayName(), control, -6447715, 7.0F);
      String value = this.capturingKeybind == setting ? "..." : KeyBindText.of((Integer)setting.get());
      if (value.isBlank()) {
         value = "None";
      }

      ClickGuiText.drawRightCentered(canvas, value, control.x() + control.width(), control.y(), control.width() * 0.58F, control.height(), 8.0F, this.capturingKeybind == setting ? accent() : -6447715, 7.0F, false);
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.KEYBIND, setting, 0, (String)null, control);
      }

   }

   private void renderButton(Canvas canvas, Control control, boolean interactive) {
      boolean hovered = inside(this.renderMouseX, this.renderMouseY, control.x(), control.y(), control.width(), control.height());
      SkijaUi.rounded(canvas, control.x(), control.y() + 2.0F, control.width(), control.height() - 4.0F, 2.0F, hovered ? UiTheme.themedHover(255) : UiTheme.themedRaised(255));
      SkijaUi.outline(canvas, control.x(), control.y() + 2.0F, control.width(), control.height() - 4.0F, 2.0F, 0.8F, accent());
      ClickGuiText.drawCentered(canvas, control.setting().displayName(), control.x() + 4.0F, control.y(), control.width() - 8.0F, control.height(), 8.0F, accent(), 7.0F, true);
      if (interactive) {
         this.addHit(FluxClickGuiScreen.HitKind.BUTTON, control.setting(), 0, (String)null, control);
      }

   }

   private void renderValue(Canvas canvas, Control control) {
      drawWrappedLabel(canvas, control.setting().displayName(), control, -6447715, 7.0F);
      ClickGuiText.drawRightCentered(canvas, Objects.toString(control.setting().get(), ""), control.x() + control.width(), control.y(), control.width() * 0.58F, control.height(), 8.0F, -6447715, 7.0F, false);
   }

   private SettingLayout settingsLayout(Module module, float x, float startY, float width) {
      List<Control> controls = new ArrayList();
      float y = startY + 4.0F;
      controls.add(new Control(FluxClickGuiScreen.ControlKind.MODULE_BIND, (Setting)null, 0, (String)null, x, y, width, 15.0F));
      y += 17.0F;
      List<Setting<?>> visible = module.settings().stream().filter(Setting::visible).toList();
      List<EnumSetting<?>> enums = new ArrayList();

      for(Setting<?> setting : visible) {
         if (setting instanceof EnumSetting<?> enumSetting && !enumSetting.isSlider()) {
            enums.add(enumSetting);
         }
      }

      for(EnumSetting<?> setting : enums) {
         float labelHeight = Math.max(13.0F, (float)ClickGuiText.lineCount(setting.displayName(), width, 8.0F, false) * 9.0F);
         controls.add(new Control(FluxClickGuiScreen.ControlKind.ENUM_LABEL, setting, 0, (String)null, x, y, width, labelHeight));
         y += labelHeight;
         float optionX = x;
         float rowHeight = 15.0F;
         Enum<?>[] options = setting.values();

         for(int index = 0; index < options.length; ++index) {
            String label = prettify(options[index].name());
            float optionWidth = Math.min(width, SkijaUi.textWidth(label, 7.0F) + 24.0F);
            if (optionX > x && optionX + optionWidth > x + width) {
               optionX = x;
               y += rowHeight;
               rowHeight = 15.0F;
            }

            float optionHeight = Math.max(15.0F, (float)ClickGuiText.lineCount(label, Math.max(1.0F, optionWidth - 12.0F), 7.0F, false) * 8.0F);
            controls.add(new Control(FluxClickGuiScreen.ControlKind.ENUM_OPTION, setting, index, label, optionX, y, optionWidth, optionHeight));
            rowHeight = Math.max(rowHeight, optionHeight);
            optionX += optionWidth;
         }

         y += rowHeight + 4.0F;
      }

      List<Setting<?>> numbers = visible.stream().filter((settingx) -> settingx instanceof IntSetting
            || settingx instanceof DoubleSetting
            || settingx instanceof EnumSetting<?> choice && choice.isSlider()).toList();
      int columns = FluxClickGuiLayout.numericColumns(width);
      float gap = columns == 2 ? 15.0F : 0.0F;
      float cellWidth = (width - gap * (float)(columns - 1)) / (float)columns;

      for(int rowStart = 0; rowStart < numbers.size(); rowStart += columns) {
         float rowHeight = 26.0F;

         for(int column = 0; column < columns && rowStart + column < numbers.size(); ++column) {
            Setting<?> setting = (Setting)numbers.get(rowStart + column);
            rowHeight = Math.max(rowHeight, (float)ClickGuiText.lineCount(setting.displayName(), Math.max(1.0F, cellWidth * 0.68F), 7.0F, false) * 8.0F + 13.0F);
         }

         for(int column = 0; column < columns && rowStart + column < numbers.size(); ++column) {
            controls.add(new Control(FluxClickGuiScreen.ControlKind.NUMBER, (Setting)numbers.get(rowStart + column), 0, (String)null, x + (float)column * (cellWidth + gap), y, cellWidth, rowHeight));
         }

         y += rowHeight + 3.0F;
      }

      if (!numbers.isEmpty()) {
         y += 2.0F;
      }

      Stream var10000 = visible.stream();
      Objects.requireNonNull(BooleanSetting.class);
      List<BooleanSetting> booleans = var10000.filter(BooleanSetting.class::isInstance).map((settingx) -> (BooleanSetting)settingx).toList();
      float booleanX = x;
      if (!booleans.isEmpty()) {
         float booleanRowHeight = 15.0F;

         for(BooleanSetting setting : booleans) {
            float itemWidth = Math.min(width, SkijaUi.textWidth(setting.displayName(), 7.0F) + 24.0F);
            float itemHeight = Math.max(15.0F, (float)ClickGuiText.lineCount(setting.displayName(), Math.max(1.0F, itemWidth - 12.0F), 7.0F, false) * 8.0F);
            if (booleanX > x && booleanX + itemWidth > x + width) {
               booleanX = x;
               y += booleanRowHeight;
               booleanRowHeight = 15.0F;
            }

            controls.add(new Control(FluxClickGuiScreen.ControlKind.BOOLEAN, setting, 0, setting.displayName(), booleanX, y, itemWidth, itemHeight));
            booleanRowHeight = Math.max(booleanRowHeight, itemHeight);
            booleanX += itemWidth;
         }

         y += booleanRowHeight + 4.0F;
      }

      for(Setting<?> setting : visible) {
         if (!(setting instanceof EnumSetting) && !(setting instanceof IntSetting) && !(setting instanceof DoubleSetting) && !(setting instanceof BooleanSetting)) {
            if (setting instanceof FontSetting) {
               float controlHeight = pairedControlHeight(setting, width, ((FontSetting)setting).displayValue());
               controls.add(new Control(FluxClickGuiScreen.ControlKind.FONT, setting, 0, (String)null, x, y, width, controlHeight));
               y += controlHeight + 2.0F;
            } else if (!(setting instanceof ColorSetting)) {
               if (setting instanceof KeybindSetting) {
                  float controlHeight = pairedControlHeight(setting, width, KeyBindText.of((Integer)((KeybindSetting)setting).get()));
                  controls.add(new Control(FluxClickGuiScreen.ControlKind.KEYBIND, setting, 0, (String)null, x, y, width, controlHeight));
                  y += controlHeight + 2.0F;
               } else if (setting instanceof StringSetting) {
                  float controlHeight = pairedControlHeight(setting, width, Objects.toString(setting.get(), ""));
                  controls.add(new Control(FluxClickGuiScreen.ControlKind.STRING, setting, 0, (String)null, x, y, width, controlHeight));
                  y += controlHeight + 2.0F;
               } else if (setting instanceof ButtonSetting) {
                  float controlHeight = Math.max(20.0F, (float)ClickGuiText.lineCount(setting.displayName(), Math.min(122.0F, width - 8.0F), 7.0F, true) * 8.0F + 4.0F);
                  controls.add(new Control(FluxClickGuiScreen.ControlKind.BUTTON, setting, 0, (String)null, x, y, Math.min(130.0F, width), controlHeight));
                  y += controlHeight + 2.0F;
               } else {
                  float controlHeight = pairedControlHeight(setting, width, Objects.toString(setting.get(), ""));
                  controls.add(new Control(FluxClickGuiScreen.ControlKind.VALUE, setting, 0, (String)null, x, y, width, controlHeight));
                  y += controlHeight + 2.0F;
               }
            } else {
               ColorSetting colorSetting = (ColorSetting)setting;
               float headerHeight = Math.max(17.0F, (float)ClickGuiText.lineCount(setting.displayName(), Math.max(1.0F, width * 0.42F), 7.0F, false) * 8.0F);
               float controlHeight = ColorPickerState.height(colorSetting, headerHeight, 11.0F, ColorPickerState.paletteHeight(width));
               controls.add(new Control(FluxClickGuiScreen.ControlKind.COLOR, setting, 0, (String)null, x, y, width, controlHeight));
               y += controlHeight;
               y += 3.0F;
            }
         }
      }

      if (visible.isEmpty()) {
         controls.add(new Control(FluxClickGuiScreen.ControlKind.EMPTY, (Setting)null, 0, (String)null, x, y, width, 16.0F));
         y += 18.0F;
      }

      return new SettingLayout(List.copyOf(controls), y - startY + 4.0F);
   }

   private void renderScrollBar(Canvas canvas, float contentHeight, float viewportHeight, float scroll) {
      if (!(contentHeight <= viewportHeight + 1.0F)) {
         float trackHeight = Math.max(20.0F, viewportHeight - 12.0F);
         float thumbHeight = Math.max(22.0F, trackHeight * viewportHeight / contentHeight);
         float range = Math.max(1.0F, contentHeight - viewportHeight);
         float y = this.windowY + 6.0F + (trackHeight - thumbHeight) * scroll / range;
         SkijaUi.rounded(canvas, this.windowX + this.windowWidth - 3.0F, y, 2.0F, thumbHeight, 1.0F, UiTheme.withAlpha(accent(), 128));
      }
   }

   private void renderConfigPanel(Canvas canvas) {
      this.configProgress = this.animate(this.configProgress, this.configsOpen ? 1.0F : 0.0F, 14.0F);
      List<String> profiles = ConfigManager.INSTANCE.listProfiles();
      float panelWidth = 130.0F;
      float fullBodyHeight = this.configBodyHeight(profiles.size());
      this.configScroll = clamp(this.configScroll, 0.0F,
            Math.max(0.0F, (float)profiles.size() * 18.0F - Math.max(0.0F, fullBodyHeight - 75.0F)));
      float bodyHeight = fullBodyHeight * this.configProgress;
      float x = this.logicalWidth() - panelWidth - 5.0F;
      float headerY = this.logicalHeight() - 23.0F - bodyHeight;
      SkijaUi.fill(canvas, x, headerY - 2.0F, panelWidth, 2.0F, accent());
      SkijaUi.fill(canvas, x, headerY, panelWidth, 18.0F, UiTheme.themedRaised(255));
      SkijaUi.icon(canvas, "\ue247", x + 5.0F, headerY, 18.0F, -6447715, 10.0F, SkijaUi.IconSet.LUCIDE);
      String current = ConfigManager.INSTANCE.currentProfile();
      draw(canvas, fit(current, panelWidth - 43.0F, 7.0F, false), x + 20.0F, headerY, 18.0F, -6447715, 7.0F);
      drawCentered(canvas, "+", x + panelWidth - 20.0F, headerY, 20.0F, 18.0F, -6447715, 10.0F);
      this.addHit(FluxClickGuiScreen.HitKind.CONFIG_TOGGLE, (Object)null, 0, (String)null, x, headerY, panelWidth - 20.0F, 18.0F);
      this.addHit(FluxClickGuiScreen.HitKind.CONFIG_SAVE, (Object)null, 0, (String)null, x + panelWidth - 20.0F, headerY, 20.0F, 18.0F);
      if (!(this.configProgress <= 0.01F)) {
         float bodyY = headerY + 18.0F;
         SkijaUi.fill(canvas, x, bodyY, panelWidth, bodyHeight, UiTheme.themedRaised(255));
         canvas.save();

         try {
            canvas.clipRect(Rect.makeXYWH(x, bodyY, panelWidth, bodyHeight));
            String shownName = this.textTarget == FluxClickGuiScreen.TextTarget.CONFIG ? this.textWithCursor() : this.configName;
            SkijaUi.rounded(canvas, x + 5.0F, bodyY + 4.0F, panelWidth - 10.0F, 17.0F, 2.0F, UiTheme.themedSurfaceAlt(255));
            draw(canvas, fit(shownName, panelWidth - 20.0F, 7.0F, false), x + 9.0F, bodyY + 4.0F, 17.0F, this.textTarget == FluxClickGuiScreen.TextTarget.CONFIG ? accent() : -6447715, 7.0F);
            if (this.configProgress >= 0.96F) {
               this.addHit(FluxClickGuiScreen.HitKind.CONFIG_NAME, (Object)null, 0, (String)null, x + 5.0F, bodyY + 4.0F, panelWidth - 10.0F, 17.0F);
            }

            float optionY = bodyY + 25.0F;
            this.renderConfigImportOption(canvas, x + 5.0F, optionY, panelWidth - 10.0F, "Visual & HUD", this.importVisualAndHud);
            this.renderConfigImportOption(canvas, x + 5.0F, optionY + 18.0F, panelWidth - 10.0F, "Keybinds", this.importKeybinds);
            if (this.configProgress >= 0.96F) {
               this.addHit(FluxClickGuiScreen.HitKind.CONFIG_IMPORT_VISUAL, (Object)null, 0, (String)null, x + 5.0F, optionY, panelWidth - 10.0F, 17.0F);
               this.addHit(FluxClickGuiScreen.HitKind.CONFIG_IMPORT_KEYBINDS, (Object)null, 0, (String)null, x + 5.0F, optionY + 18.0F, panelWidth - 10.0F, 17.0F);
            }

            float listTop = bodyY + 63.0F;
            float listBottom = Math.max(listTop, bodyY + bodyHeight - 12.0F);
            float listY = listTop - this.configScroll;
            canvas.save();
            try {
               canvas.clipRect(Rect.makeXYWH(x, listTop, panelWidth, listBottom - listTop));
               for(String profile : profiles) {
                  boolean active = profile.equalsIgnoreCase(current);
                  boolean hovered = inside(this.renderMouseX, this.renderMouseY, x + 5.0F, listY, panelWidth - 10.0F, 17.0F);
                  if (hovered) {
                     SkijaUi.fill(canvas, x + 5.0F, listY, panelWidth - 10.0F, 17.0F, UiTheme.themedHover(255));
                  }
                  draw(canvas, fit(profile, panelWidth - 18.0F, 7.0F, false), x + 9.0F, listY, 17.0F, active ? accent() : -6447715, 7.0F);
                  if (this.configProgress >= 0.96F) {
                     float hitY = Math.max(listTop, listY);
                     float hitHeight = Math.min(listBottom, listY + 17.0F) - hitY;
                     this.addHit(FluxClickGuiScreen.HitKind.CONFIG_PROFILE, profile, 0, null,
                           x + 5.0F, hitY, panelWidth - 10.0F, hitHeight);
                  }
                  listY += 18.0F;
               }
            } finally {
               canvas.restore();
            }

            if (!this.configStatus.isBlank()) {
               drawRight(canvas, this.configStatus, x + 5.0F, bodyY + bodyHeight - 14.0F, panelWidth - 10.0F, 12.0F, this.configError ? -495247 : -11870592, 6.0F);
            }
         } finally {
            canvas.restore();
         }

      }
   }

   private float configBodyHeight(int profileCount) {
      return Math.min(Math.max(0.0F, this.logicalHeight() - 28.0F),
            Math.min(154.0F, 75.0F + (float)profileCount * 18.0F));
   }

   private void renderConfigImportOption(Canvas canvas, float x, float y, float width, String label, boolean enabled) {
      boolean hovered = inside(this.renderMouseX, this.renderMouseY, x, y, width, 17.0F);
      if (hovered) {
         SkijaUi.fill(canvas, x, y, width, 17.0F, UiTheme.themedHover(255));
      }

      draw(canvas, label, x + 4.0F, y, 17.0F, hovered ? -1 : -6447715, 7.0F);
      float boxX = x + width - 14.0F;
      SkijaUi.fill(canvas, boxX, y + 4.0F, 9.0F, 9.0F, enabled ? accent() : UiTheme.themedSurfaceAlt(255));
      if (enabled) {
         drawCentered(canvas, "x", boxX, y + 1.0F, 9.0F, 15.0F, -1, 6.0F);
      }

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
      HitTarget hit = this.hitAt(mouseX, mouseY);
      if (hit != null) {
         if (hit.kind() != FluxClickGuiScreen.HitKind.STRING && hit.kind() != FluxClickGuiScreen.HitKind.COLOR && hit.kind() != FluxClickGuiScreen.HitKind.CONFIG_NAME) {
            this.finishTextEditing();
         }

         if (this.handleHit(hit, button, mouseX, mouseY)) {
            return true;
         }
      }

      this.finishTextEditing();
      this.capturingModule = null;
      this.capturingKeybind = null;
      return super.mouseClicked(event, doubleClick);
   }

   private boolean handleHit(HitTarget hit, int button, float mouseX, float mouseY) {
      switch (hit.kind().ordinal()) {
         case 0:
            if (button == 0) {
               this.draggingWindow = true;
               this.dragOffsetX = mouseX - this.windowX;
               this.dragOffsetY = this.renderMouseY - this.windowY;
               return true;
            }
            break;
         case 1:
            if (button == 0) {
               this.resizingWindow = true;
               this.resizeStartX = mouseX;
               this.resizeStartY = this.renderMouseY;
               this.resizeStartWidth = this.windowWidth;
               this.resizeStartHeight = this.windowHeight;
               return true;
            }
            break;
         case 2:
            if (button == 0) {
               this.selectCategory((Category)hit.owner());
               return true;
            }
            break;
         case 3:
            Module module = (Module)hit.owner();
            if (button == 2) {
               this.capturingModule = module;
            } else {
               this.selectedModule = module;
            }

            return true;
         case 4:
            if (button == 0) {
               ((Module)hit.owner()).interactFromClickGui();
               return true;
            }
            break;
         case 5:
            Module boundModule = (Module)hit.owner();
            if (button == 1) {
               boundModule.setKeyBind(-1);
               this.capturingModule = null;
            } else if (button == 0) {
               this.capturingModule = boundModule;
            }

            return true;
         case 6:
            if (button == 0 || button == 1) {
               setEnum((EnumSetting)hit.owner(), hit.index());
               return true;
            }
            break;
         case 7:
            Setting<?> setting = (Setting)hit.owner();
            if (button == 1) {
               decrementNumber(setting);
            } else if (button == 0) {
               this.draggingNumber = setting;
               this.draggingTrackX = hit.x();
               this.draggingTrackWidth = hit.width();
               this.updateNumber(mouseX);
            }

            return true;
         case 8:
            if (button == 0 || button == 1) {
               BooleanSetting booleanSetting = (BooleanSetting)hit.owner();
               NotificationManager var10000 = NotificationManager.INSTANCE;
               Objects.requireNonNull(booleanSetting);
               var10000.withoutModuleFeedback(booleanSetting::toggle);
               return true;
            }
            break;
         case 9:
            if (button != 0 && button != 1) {
               break;
            }

            ((FontSetting)hit.owner()).cycle(button == 1 ? -1 : 1);
            return true;
         case 10:
            ColorSetting colorSetting = (ColorSetting)hit.owner();
            if (button == 1) {
               ColorPickerState.toggle(colorSetting);
               this.finishTextEditing();
            } else if (button == 0 && ColorPickerState.expanded(colorSetting)) {
               float header = Math.max(17.0F, (float)ClickGuiText.lineCount(colorSetting.displayName(), Math.max(1.0F, hit.width() * 0.42F), 7.0F, false) * 8.0F);
               float modeY = hit.y() + header + 3.0F;
               if (ColorPickerState.clickMode(colorSetting, (float)mouseX, (float)mouseY, hit.x(), modeY, Math.min(116.0F, hit.width()))) return true;
               if (ColorPickerState.mode(colorSetting) == ColorPickerState.Mode.PALETTE && ColorPickerState.clickPalette(colorSetting, (float)mouseX, (float)mouseY, hit.x(), modeY + 16.0F, hit.width())) return true;
               if (ColorPickerState.mode(colorSetting) == ColorPickerState.Mode.RGB && mouseY >= modeY + 16.0F) {
                  int channel = (int)((mouseY - modeY - 16.0F) / 11.0F);
                  int channels = colorSetting.allowAlpha() ? 4 : 3;
                  if (channel >= 0 && channel < channels) {
                     this.draggingColor = colorSetting;
                     this.draggingColorChannel = channel;
                     this.draggingTrackX = hit.x() + 13.0F;
                     this.draggingTrackWidth = Math.max(20.0F, hit.width() - 42.0F);
                     this.updateColor(mouseX);
                  }
               }
            } else if (button == 0) {
               return true;
            }

            return true;
         case 11:
            if (button == 0) {
               this.draggingColor = (ColorSetting)hit.owner();
               this.draggingColorChannel = hit.index();
               this.draggingTrackX = hit.x();
               this.draggingTrackWidth = hit.width();
               this.updateColor(mouseX);
               return true;
            }
            break;
         case 12:
            StringSetting stringSetting = (StringSetting)hit.owner();
            if (button == 1) {
               stringSetting.reset();
               this.finishTextEditing();
            } else if (button == 0) {
               this.focusString(stringSetting);
            }

            return true;
         case 13:
            KeybindSetting keybindSetting = (KeybindSetting)hit.owner();
            if (button == 1) {
               keybindSetting.set(-1);
               this.capturingKeybind = null;
            } else if (button == 0) {
               this.capturingKeybind = keybindSetting;
            }

            return true;
         case 14:
            if (button == 0) {
               ((ButtonSetting)hit.owner()).press();
               return true;
            }
            break;
         case 15:
            if (button == 0) {
               this.configsOpen = !this.configsOpen;
               this.configStatus = "";
               return true;
            }
            break;
         case 16:
            if (button == 0) {
               this.saveProfile();
               return true;
            }
            break;
         case 17:
            if (button == 0) {
               this.focusConfigName();
               return true;
            }
            break;
         case 18:
            if (button == 0) {
               this.importVisualAndHud = !this.importVisualAndHud;
               return true;
            }
            break;
         case 19:
            if (button == 0) {
               this.importKeybinds = !this.importKeybinds;
               return true;
            }
            break;
         case 20:
            String profile = (String)hit.owner();
            if (button == 1) {
               this.deleteProfile(profile);
            } else if (button == 0) {
               this.loadProfile(profile);
            }

            return true;
      }

      return false;
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      float mouseX = (float)this.logical(event.x());
      float mouseY = (float)this.logical(event.y());
      if (this.draggingWindow) {
         this.windowX = mouseX - this.dragOffsetX;
         this.windowY = mouseY - this.dragOffsetY;
         this.clampWindow();
         return true;
      } else if (this.resizingWindow) {
         this.windowWidth = this.resizeStartWidth + mouseX - this.resizeStartX;
         this.windowHeight = this.resizeStartHeight + mouseY - this.resizeStartY;
         this.clampWindow();
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
      this.draggingWindow = false;
      this.resizingWindow = false;
      this.draggingNumber = null;
      this.draggingColor = null;
      this.draggingColorChannel = -1;
      this.rememberWindow();
      return super.mouseReleased(event);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      float x = (float)this.logical(mouseX);
      float y = (float)this.logical(mouseY);
      int profileCount = ConfigManager.INSTANCE.listProfiles().size();
      float configBodyHeight = this.configBodyHeight(profileCount);
      if (this.configsOpen && inside(x, y, this.logicalWidth() - 135.0F,
            this.logicalHeight() - 23.0F - configBodyHeight, 130.0F, configBodyHeight + 18.0F)) {
         float max = Math.max(0.0F, (float)profileCount * 18.0F - Math.max(0.0F, configBodyHeight - 75.0F));
         this.configScroll = clamp(this.configScroll - (float)verticalAmount * 28.0F, 0.0F, max);
         return true;
      } else if (this.selectedCategory != null && inside(x, y, this.windowX + 100.0F, this.windowY, this.windowWidth - 100.0F, this.windowHeight)) {
         float current = (Float)this.scrollByCategory.getOrDefault(this.selectedCategory, 0.0F);
         this.scrollByCategory.put(this.selectedCategory, Math.max(0.0F, current - (float)verticalAmount * 28.0F));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
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
         return this.textTarget != FluxClickGuiScreen.TextTarget.NONE && this.handleTextKey(event) ? true : super.keyPressed(event);
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
      if (this.textTarget != FluxClickGuiScreen.TextTarget.NONE && event.isAllowedChatCharacter()) {
         this.insertText(event.codepointAsString());
         return true;
      } else {
         return super.charTyped(event);
      }
   }

   public void onClose() {
      this.finishTextEditing();
      this.rememberWindow();
      ConfigManager.INSTANCE.save();
      if (this.parent != null) {
         this.minecraft.setScreen(this.parent);
      } else {
         super.onClose();
      }

   }

   public void removed() {
      this.rememberWindow();
      super.removed();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private void selectInitialCategory() {
      if (this.selectedCategory != null && this.selectedCategory.guiVisible()) {
         this.selectCategory(this.selectedCategory);
      } else {
         List<Category> categories = visibleCategories();
         if (!categories.isEmpty()) {
            this.selectCategory((Category)categories.getFirst());
         }

      }
   }

   private void selectCategory(Category category) {
      if (category != null && category.guiVisible()) {
         this.selectedCategory = category;
         List<Module> modules = ModuleManager.INSTANCE.modulesIn(category);
         if (!modules.contains(this.selectedModule)) {
            this.selectedModule = modules.isEmpty() ? null : (Module)modules.getFirst();
         }

      }
   }

   private static List<Category> visibleCategories() {
      List<Category> categories = new ArrayList();

      for(Category category : Category.values()) {
         if (category.guiVisible()) {
            categories.add(category);
         }
      }

      return categories;
   }

   private void updateNumber(float mouseX) {
      if (this.draggingNumber != null && !(this.draggingTrackWidth <= 0.0F)) {
         double fraction = (double)clamp((mouseX - this.draggingTrackX) / this.draggingTrackWidth, 0.0F, 1.0F);
         Setting draggedSetting = this.draggingNumber;
         if (draggedSetting instanceof IntSetting) {
            IntSetting setting = (IntSetting)draggedSetting;
            double rawValue = (double)setting.min() + (double)(setting.max() - setting.min()) * fraction;
            setting.set((int)Math.round(FluxClickGuiLayout.snap(rawValue, (double)setting.min(), (double)setting.max(), (double)setting.step())));
         } else if (draggedSetting instanceof EnumSetting<?> setting && setting.isSlider()) {
            setting.setSliderFraction((float)fraction);
         } else {
            draggedSetting = this.draggingNumber;
            if (draggedSetting instanceof DoubleSetting) {
               DoubleSetting setting = (DoubleSetting)draggedSetting;
               double rawValue = setting.min() + (setting.max() - setting.min()) * fraction;
               setting.set(FluxClickGuiLayout.snap(rawValue, setting.min(), setting.max(), setting.step()));
            }
         }

      }
   }

   private static void decrementNumber(Setting<?> setting) {
      if (setting instanceof IntSetting value) {
         value.set((Integer)value.get() - value.step());
      } else if (setting instanceof DoubleSetting value) {
         value.set((Double)value.get() - value.step());
      } else if (setting instanceof EnumSetting<?> choice && choice.isSlider()) {
         choice.cycle(-1);
      }

   }

   private void updateColor(float mouseX) {
      if (this.draggingColor != null && this.draggingColorChannel >= 0 && !(this.draggingTrackWidth <= 0.0F)) {
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
      this.finishTextEditing();
      this.textTarget = FluxClickGuiScreen.TextTarget.STRING;
      this.editingString = setting;
      this.editText = (String)setting.get();
      this.editCursor = this.editText.length();
   }

   private void focusColor(ColorSetting setting) {
      this.finishTextEditing();
      this.textTarget = FluxClickGuiScreen.TextTarget.COLOR;
      this.editingColor = setting;
      this.colorBeforeEdit = setting.get();
      this.editText = setting.hex();
      this.editCursor = this.editText.length();
      this.selectAll = true;
   }

   private void focusConfigName() {
      this.finishTextEditing();
      this.textTarget = FluxClickGuiScreen.TextTarget.CONFIG;
      this.editText = this.configName;
      this.editCursor = this.editText.length();
      this.selectAll = true;
   }

   private boolean handleTextKey(KeyEvent event) {
      if (event.key() != 257 && event.key() != 335 && event.key() != 256) {
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

         int maxLength = this.textTarget == FluxClickGuiScreen.TextTarget.COLOR ? 9 : 128;
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
      if (this.textTarget == FluxClickGuiScreen.TextTarget.STRING && this.editingString != null) {
         this.editingString.set(this.editText);
      } else if (this.textTarget == FluxClickGuiScreen.TextTarget.COLOR && this.editingColor != null) {
         Color parsed = this.editingColor.parseHex(this.editText);
         if (parsed != null) {
            this.editingColor.set(parsed);
         }
      } else if (this.textTarget == FluxClickGuiScreen.TextTarget.CONFIG) {
         this.configName = this.editText;
      }

   }

   private void finishTextEditing() {
      if (this.textTarget != FluxClickGuiScreen.TextTarget.NONE) {
         if (this.textTarget == FluxClickGuiScreen.TextTarget.COLOR && this.editingColor != null) {
            Color parsed = this.editingColor.parseHex(this.editText);
            this.editingColor.set(parsed != null ? parsed : this.colorBeforeEdit);
         } else {
            this.applyEditedText();
         }

         this.textTarget = FluxClickGuiScreen.TextTarget.NONE;
         this.editingString = null;
         this.editingColor = null;
         this.colorBeforeEdit = null;
         this.editText = "";
         this.editCursor = 0;
         this.selectAll = false;
      }
   }

   private String textWithCursor() {
      int cursor = Math.max(0, Math.min(this.editCursor, this.editText.length()));
      String var10000 = this.editText.substring(0, cursor);
      return var10000 + "|" + this.editText.substring(cursor);
   }

   private void saveProfile() {
      this.finishTextEditing();
      String name = this.configName != null && !this.configName.isBlank() ? this.configName.trim() : ConfigManager.INSTANCE.currentProfile();

      try {
         this.configName = ConfigManager.INSTANCE.saveProfile(name);
         this.configStatus = "Saved";
         this.configError = false;
      } catch (Exception error) {
         this.configStatus = "Failed";
         this.configError = true;
         Setsuna.LOGGER.error("Failed to save Flux ClickGUI profile {}", name, error);
      }

   }

   private void loadProfile(String profile) {
      try {
         if (ConfigManager.INSTANCE.loadProfile(profile, new ConfigManager.ImportOptions(this.importVisualAndHud, this.importKeybinds))) {
            this.configName = profile;
            this.configStatus = "Loaded";
            this.configError = false;
         }
      } catch (Exception error) {
         this.configStatus = "Failed";
         this.configError = true;
         Setsuna.LOGGER.error("Failed to load Flux ClickGUI profile {}", profile, error);
      }

   }

   private void deleteProfile(String profile) {
      try {
         if (ConfigManager.INSTANCE.deleteProfile(profile)) {
            this.configStatus = "Deleted";
            this.configError = false;
         }
      } catch (Exception error) {
         this.configStatus = "Failed";
         this.configError = true;
         Setsuna.LOGGER.error("Failed to delete Flux ClickGUI profile {}", profile, error);
      }

   }

   private void drawLogo(Canvas canvas, float x, float y, float size) {
      try {
         try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(LOGO_TEXTURE)) {
            if (borrowed != null) {
               Rect source = Rect.makeXYWH(0.0F, 0.0F, (float)borrowed.image().getWidth(), (float)borrowed.image().getHeight());
               Rect destination = Rect.makeXYWH(x, y, size, size);
               IMAGE_PAINT.setColor(-1).setAlpha(255);
               canvas.drawImageRect(borrowed.image(), source, destination, SamplingMode.DEFAULT, IMAGE_PAINT, true);
               return;
            }
         }
      } catch (Throwable var10) {
      }

      SkijaUi.rounded(canvas, x, y, size, size, 4.0F, accent());
      drawCentered(canvas, "S", x, y, size, size, -1, 9.0F);
   }

   private void drawAvatar(Canvas canvas, float x, float y, float size) {
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

      String username = AltManager.INSTANCE.getCurrentUsername();
      String initial = username != null && !username.isBlank() ? username.substring(0, 1).toUpperCase(Locale.ROOT) : "?";
      SkijaUi.rounded(canvas, x, y, size, size, 6.0F, UiTheme.withAlpha(accent(), 110));
      drawCentered(canvas, initial, x, y, size, size, -1, 9.0F);
   }

   private static void drawSkinLayer(Canvas canvas, SkijaRenderer.BorrowedImage borrowed, Rect destination, float textureWidth, float textureHeight, float sourceX, float sourceY) {
      Rect source = Rect.makeLTRB(sourceX / 64.0F * textureWidth, sourceY / 64.0F * textureHeight, (sourceX + 8.0F) / 64.0F * textureWidth, (sourceY + 8.0F) / 64.0F * textureHeight);
      canvas.drawImageRect(borrowed.image(), source, destination);
   }

   private void clampWindow() {
      FluxClickGuiLayout.Bounds bounds = FluxClickGuiLayout.resolve(this.logicalWidth(), this.logicalHeight(), this.windowX, this.windowY, this.windowWidth, this.windowHeight);
      this.applyBounds(bounds);
   }

   private void applyBounds(FluxClickGuiLayout.Bounds bounds) {
      this.windowX = bounds.x();
      this.windowY = bounds.y();
      this.windowWidth = bounds.width();
      this.windowHeight = bounds.height();
   }

   private void rememberWindow() {
      MEMORY.x = this.windowX;
      MEMORY.y = this.windowY;
      MEMORY.width = this.windowWidth;
      MEMORY.height = this.windowHeight;
   }

   private float configuredScale() {
      float requested = clamp((float)(Integer)ClickGui.INSTANCE.guiScale.get() / 100.0F, 0.65F, 1.25F);
      float fitX = (float)this.width / 316.0F;
      float fitY = (float)this.height / 246.0F;
      return clamp(Math.min(requested, Math.min(fitX, fitY)), 0.5F, 1.25F);
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

   private float animate(float current, float target, float speed) {
      float amount = 1.0F - (float)Math.exp((double)(-speed * this.animationDelta));
      return current + (target - current) * amount;
   }

   private static float numberFraction(Setting<?> setting) {
      if (setting instanceof IntSetting value) {
         return clamp(value.fraction(), 0.0F, 1.0F);
      } else if (setting instanceof DoubleSetting value) {
         return clamp(value.fraction(), 0.0F, 1.0F);
      } else if (setting instanceof EnumSetting<?> choice && choice.isSlider()) {
         return choice.sliderFraction();
      } else {
         return 0.0F;
      }
   }

   private static String numberText(Setting<?> setting) {
      if (setting instanceof IntSetting value) {
         return Integer.toString((Integer)value.get());
      } else if (setting instanceof DoubleSetting value) {
         String text = String.format(Locale.ROOT, "%.2f", value.get());
         return text.replaceAll("0+$", "").replaceAll("\\.$", "");
      } else if (setting instanceof EnumSetting<?> choice && choice.isSlider()) {
         return choice.displayValue();
      } else {
         return "";
      }
   }

   private static void setEnum(EnumSetting<?> setting, int index) {
      Enum[] values = setting.values();
      if (index >= 0 && index < values.length) {
         ((EnumSetting)setting).set(values[index]);
      }

   }

   private HitTarget hitAt(float x, float y) {
      for(int index = this.hits.size() - 1; index >= 0; --index) {
         HitTarget hit = (HitTarget)this.hits.get(index);
         if (inside(x, y, hit.x(), hit.y(), hit.width(), hit.height())) {
            return hit;
         }
      }

      return null;
   }

   private void addHit(HitKind kind, Object owner, int index, String value, Control control) {
      this.addHit(kind, owner, index, value, control.x(), control.y(), control.width(), control.height());
   }

   private void addHit(HitKind kind, Object owner, int index, String value, float x, float y, float width, float height) {
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         this.hits.add(new HitTarget(kind, owner, index, value, x, y, width, height));
      }
   }

   private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
      return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private static String prettify(String text) {
      if (text != null && !text.isEmpty()) {
         String spaced = text.replace('_', ' ').replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
         String var10000 = spaced.substring(0, 1).toUpperCase(Locale.ROOT);
         return var10000 + spaced.substring(1).toLowerCase(Locale.ROOT);
      } else {
         return "";
      }
   }

   private static String fit(String text, float maxWidth, float size, boolean bold) {
      String value = (String)Objects.requireNonNullElse(text, "");
      if (maxWidth <= 0.0F) {
         return "";
      } else if (textWidth(value, size, bold) <= maxWidth) {
         return value;
      } else {
         String suffix;
         for(suffix = "..."; !value.isEmpty() && textWidth(value + suffix, size, bold) > maxWidth; value = value.substring(0, value.length() - 1)) {
         }

         return value.isEmpty() ? "" : value + suffix;
      }
   }

   private static void drawWrappedLabel(Canvas canvas, String text, Control control, int color, float size) {
      ClickGuiText.drawVerticallyCentered(canvas, text, control.x(), control.y(), Math.max(1.0F, control.width() * 0.42F), control.height(), 8.0F, color, size, false);
   }

   private static float pairedControlHeight(Setting<?> setting, float width, String value) {
      int labelLines = ClickGuiText.lineCount(setting.displayName(), Math.max(1.0F, width * 0.42F), 7.0F, false);
      int valueLines = ClickGuiText.lineCount(value, Math.max(1.0F, width * 0.58F), 7.0F, false);
      return Math.max(17.0F, (float)Math.max(labelLines, valueLines) * 8.0F);
   }

   private static float textWidth(String text, float size, boolean bold) {
      return bold ? SkijaUi.boldTextWidth(text, size) : SkijaUi.textWidth(text, size);
   }

   private static int accent() {
      return UiTheme.accent();
   }

   private static void draw(Canvas canvas, String text, float x, float y, float height, int color, float size) {
      SkijaUi.text(canvas, text, x + 0.5F, y + 0.5F, height, shadowColor(color), size);
      SkijaUi.text(canvas, text, x, y, height, color, size);
   }

   private static void drawBold(Canvas canvas, String text, float x, float y, float height, int color, float size) {
      SkijaUi.boldText(canvas, text, x + 0.5F, y + 0.5F, height, shadowColor(color), size);
      SkijaUi.boldText(canvas, text, x, y, height, color, size);
   }

   private static void drawRight(Canvas canvas, String text, float x, float y, float width, float height, int color, float size) {
      draw(canvas, text, x + width - SkijaUi.textWidth(text, size), y, height, color, size);
   }

   private static void drawCentered(Canvas canvas, String text, float x, float y, float width, float height, int color, float size) {
      draw(canvas, text, x + (width - SkijaUi.textWidth(text, size)) * 0.5F, y, height, color, size);
   }

   private static int shadowColor(int color) {
      int alpha = color >>> 24 & 255;
      return Math.min(alpha, 120) << 24;
   }

   static {
      IMAGE_PAINT = (new Paint()).setAntiAlias(true);
      MEMORY = new Memory();
   }

   private static enum ControlKind {
      EMPTY,
      MODULE_BIND,
      ENUM_LABEL,
      ENUM_OPTION,
      NUMBER,
      BOOLEAN,
      FONT,
      COLOR,
      COLOR_CHANNEL,
      STRING,
      KEYBIND,
      BUTTON,
      VALUE;
   }

   private static enum HitKind {
      DRAG,
      RESIZE,
      CATEGORY,
      MODULE,
      MODULE_TOGGLE,
      MODULE_BIND,
      ENUM_OPTION,
      NUMBER,
      BOOLEAN,
      FONT,
      COLOR,
      COLOR_CHANNEL,
      STRING,
      KEYBIND,
      BUTTON,
      CONFIG_TOGGLE,
      CONFIG_SAVE,
      CONFIG_NAME,
      CONFIG_IMPORT_VISUAL,
      CONFIG_IMPORT_KEYBINDS,
      CONFIG_PROFILE;
   }

   private static enum TextTarget {
      NONE,
      STRING,
      COLOR,
      CONFIG;
   }

   private static record Control(ControlKind kind, Setting<?> setting, int index, String text, float x, float y, float width, float height) {
   }

   private static record SettingLayout(List<Control> controls, float height) {
   }

   private static record HitTarget(HitKind kind, Object owner, int index, String value, float x, float y, float width, float height) {
   }

   private static final class Memory {
      private float x = Float.NaN;
      private float y = Float.NaN;
      private float width = 395.0F;
      private float height = 280.0F;
   }
}
