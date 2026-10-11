package com.setsuna.ui.hud;

import com.setsuna.config.ConfigManager;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
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
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.ui.SkijaScreen;
import com.setsuna.ui.UiTheme;
import com.setsuna.ui.clickgui.ColorPickerState;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen extends Screen implements SkijaScreen {
   private static final float EDGE_MARGIN = 6.0F;
   private static final float SIDEBAR_WIDTH = 150.0F;
   private static final float SIDEBAR_HEADER = 22.0F;
   private static final float SIDEBAR_FOOTER = 5.0F;
   private static final float MAX_SIDEBAR_BODY = 280.0F;
   private static final float SETTING_HEIGHT = 13.0F;
   private static final float NUMBER_HEIGHT = 18.0F;
   private static final float COLOR_CHANNEL_HEIGHT = 9.0F;
   private static final float BUTTON_WIDTH = 54.0F;
   private static final float BUTTON_HEIGHT = 20.0F;
   private static final float ITEM_LABEL_HEIGHT = 11.0F;
   private static final float SETTING_FONT = 7.0F;
   private static final float SCROLL_STEP = 20.0F;
   private static final float DETACH_GAP = 18.0F;
   private static final int TRACK = UiTheme.argb(255, 67, 71, 82);
   private static final int[] CHANNEL_COLORS = new int[]{UiTheme.argb(255, 232, 84, 84), UiTheme.argb(255, 84, 218, 117), UiTheme.argb(255, 84, 151, 235), UiTheme.argb(255, 226, 228, 233)};
   private static final String[] CHANNEL_LABELS = new String[]{"R", "G", "B", "A"};
   private float sidebarX = Float.NaN;
   private float sidebarY = Float.NaN;
   private float sidebarScroll;
   private float inspectorProgress;
   private long lastFrameNanos = System.nanoTime();
   private HUDModuel selectedModule;
   private int mouseX;
   private int mouseY;
   private EditorItem dragging;
   private List<GroupDragOffset> draggingMembers = List.of();
   private float dragOffsetX;
   private float dragOffsetY;
   private Setting<?> draggingNumber;
   private ColorSetting draggingColor;
   private int draggingColorChannel = -1;
   private float draggingTrackX;
   private float draggingTrackWidth;
   private StringSetting editingString;
   private ColorSetting editingColor;
   private Color colorBeforeEdit;
   private String editText = "";
   private int editCursor;
   private boolean selectAll;

   public HudEditorScreen() {
      super(Component.literal("Setsuna HUD Editor"));
   }

   protected void init() {
      this.lastFrameNanos = System.nanoTime();
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      if (SkijaRenderer.hasFailed()) {
         graphics.fill(6, 6, 244, 26, -704116213);
         graphics.text(this.font, "Skija renderer failed - check latest.log", 11, 14, UiTheme.TEXT, false);
      }

   }

   public void renderSkija(Canvas canvas) {
      EventBus.INSTANCE.postTo(new Render2DEvent(canvas, (float)this.width, (float)this.height, (double)this.minecraft.getWindow().getGuiScale()), (subscriber) -> subscriber instanceof HUDModuel || subscriber == HudFusionManager.INSTANCE);
      List<EditorItem> items = this.items();
      this.updateInspector(items);

      for(EditorItem item : items) {
         this.drawItem(canvas, item, this.selectedModule != null && item.members().contains(this.selectedModule));
      }

      if (this.selectedModule != null) {
         this.drawSidebar(canvas);
      }

      this.drawActions(canvas);
   }

   private void updateInspector(List<EditorItem> items) {
      long now = System.nanoTime();
      float delta = Math.min(0.08F, Math.max(0.0F, (float)(now - this.lastFrameNanos) / 1.0E9F));
      this.lastFrameNanos = now;
      if (this.selectedModule == null) {
         this.inspectorProgress = approach(this.inspectorProgress, 0.0F, delta, 14.0F);
      } else {
         EditorItem selected = null;

         for(EditorItem item : items) {
            if (item.module() == this.selectedModule) {
               selected = item;
               break;
            }
         }

         if (selected == null) {
            this.selectedModule = null;
            this.inspectorProgress = 0.0F;
         } else {
            List<Slot> slots = this.buildSlots();
            float panelHeight = 22.0F + this.sidebarBodyHeight(slots) + 5.0F;
            float gap = 8.0F;
            float rightX = selected.x() + selected.width() + gap;
            float leftX = selected.x() - 150.0F - gap;
            float targetX;
            if (rightX + 150.0F <= (float)this.width - 6.0F) {
               targetX = rightX;
            } else if (leftX >= 6.0F) {
               targetX = leftX;
            } else {
               targetX = HudRenderUtil.clamp(rightX, 6.0F, Math.max(6.0F, (float)this.width - 150.0F - 6.0F));
            }

            float targetY = HudRenderUtil.clamp(selected.y(), 6.0F, Math.max(6.0F, (float)this.height - panelHeight - 6.0F));
            if (!Float.isNaN(this.sidebarX) && !Float.isNaN(this.sidebarY)) {
               this.sidebarX = approach(this.sidebarX, targetX, delta, 13.0F);
               this.sidebarY = approach(this.sidebarY, targetY, delta, 13.0F);
            } else {
               this.sidebarX = targetX;
               this.sidebarY = targetY;
            }

            this.inspectorProgress = approach(this.inspectorProgress, 1.0F, delta, 14.0F);
            this.clampScroll(slots);
         }
      }
   }

   private static float approach(float current, float target, float delta, float speed) {
      float result = current + (target - current) * (1.0F - (float)Math.exp((double)(-speed * delta)));
      return Math.abs(target - result) < 0.001F ? target : result;
   }

   private void drawSidebar(Canvas canvas) {
      List<Slot> slots = this.buildSlots();
      float bodyHeight = this.sidebarBodyHeight(slots);
      float panelHeight = 22.0F + bodyHeight + 5.0F;
      int layer = canvas.saveLayerAlpha((Rect)null, Math.round(255.0F * HudRenderUtil.clamp(this.inspectorProgress, 0.0F, 1.0F)));
      HudRenderUtil.panel(canvas, this.sidebarX, this.sidebarY, 150.0F, panelHeight, 184);
      SkijaUi.fill(canvas, this.sidebarX + 1.0F, this.sidebarY + 22.0F - 1.0F, 148.0F, 1.0F, UiTheme.withAlpha(UiTheme.BORDER, 170));
      SkijaUi.boldText(canvas, HudRenderUtil.fit(compactLabel(this.selectedModule), 136.0F, 7.2F, true), this.sidebarX + 7.0F, this.sidebarY, 22.0F, UiTheme.TEXT, 7.2F);
      float bodyY = this.sidebarY + 22.0F;
      canvas.save();
      canvas.clipRect(Rect.makeXYWH(this.sidebarX, bodyY, 150.0F, bodyHeight));
      float base = bodyY - this.sidebarScroll;

      for(Slot slot : slots) {
         float rowY = base + slot.y();
         if (!(rowY + slot.height() < bodyY) && !(rowY > bodyY + bodyHeight)) {
            this.drawSettingRow(canvas, slot.setting(), rowY, slot.height());
         }
      }

      canvas.restore();
      this.drawScrollbar(canvas, bodyY, bodyHeight, contentHeight(slots));
      canvas.restoreToCount(layer);
   }

   private void drawSettingRow(Canvas canvas, Setting<?> setting, float rowY, float rowHeight) {
      boolean hovered = contains((double)this.mouseX, (double)this.mouseY, this.sidebarX, rowY, 150.0F, rowHeight);
      if (hovered) {
         SkijaUi.rounded(canvas, this.sidebarX + 4.0F, rowY + 1.0F, 142.0F, Math.max(1.0F, rowHeight - 2.0F), 3.0F, UiTheme.withAlpha(UiTheme.SURFACE_HOVER, 120));
      }

      float labelWidth = setting instanceof ColorSetting ? 52.0F : (setting instanceof StringSetting && !(setting instanceof FontSetting) ? 64.0F : 74.0F);
      SkijaUi.text(canvas, HudRenderUtil.fit(setting.displayName(), labelWidth, 7.0F, false), this.sidebarX + 8.0F, rowY, 13.0F, UiTheme.TEXT_MUTED, 7.0F);
      if (setting instanceof BooleanSetting booleanSetting) {
         float boxX = this.sidebarX + 150.0F - 16.0F;
         SkijaUi.rounded(canvas, boxX, rowY + 3.5F, 9.0F, 9.0F, 2.0F, (Boolean)booleanSetting.get() ? UiTheme.accent() : TRACK);
      } else if (setting instanceof FontSetting fontSetting) {
         this.drawRight(canvas, HudRenderUtil.fit(fontSetting.displayValue(), 60.0F, 7.0F, false), rowY, 13.0F, UiTheme.accent(), 7.0F);
      } else if (setting instanceof EnumSetting<?> enumSetting) {
         this.drawRight(canvas, HudRenderUtil.fit(enumSetting.displayValue(), 60.0F, 7.0F, false), rowY, 13.0F, UiTheme.accent(), 7.0F);
      } else if (setting instanceof IntSetting intSetting) {
         this.drawRight(canvas, Integer.toString((Integer)intSetting.get()), rowY, 12.0F, UiTheme.TEXT, 7.0F);
         this.drawNumberTrack(canvas, rowY, intSetting.fraction());
      } else if (setting instanceof DoubleSetting doubleSetting) {
         this.drawRight(canvas, trimZeros(String.format(Locale.ROOT, "%.2f", doubleSetting.get())), rowY, 12.0F, UiTheme.TEXT, 7.0F);
         this.drawNumberTrack(canvas, rowY, doubleSetting.fraction());
      } else if (setting instanceof ColorSetting colorSetting) {
         this.drawColor(canvas, colorSetting, rowY);
      } else if (setting instanceof StringSetting stringSetting) {
         this.drawStringField(canvas, stringSetting, rowY);
      } else if (setting instanceof ButtonSetting) {
         this.drawRight(canvas, "RUN", rowY, 13.0F, UiTheme.accent(), 7.0F);
      }

   }

   private void drawStringField(Canvas canvas, StringSetting setting, float rowY) {
      boolean focused = this.editingString == setting;
      float fieldX = this.sidebarX + 150.0F - 68.0F;
      float fieldY = rowY + 1.5F;
      float fieldWidth = 61.0F;
      float fieldHeight = 11.0F;
      SkijaUi.rounded(canvas, fieldX, fieldY, fieldWidth, fieldHeight, 2.0F, focused ? UiTheme.accent() : TRACK);
      SkijaUi.rounded(canvas, fieldX + 1.0F, fieldY + 1.0F, fieldWidth - 2.0F, fieldHeight - 2.0F, 1.5F, UiTheme.withAlpha(UiTheme.SURFACE, 238));
      String value = focused ? this.editText : (String)setting.get();
      String shown = value.isEmpty() && !focused ? "Empty" : value;
      int color = value.isEmpty() && !focused ? UiTheme.TEXT_FAINT : UiTheme.TEXT;
      float innerX = fieldX + 4.0F;
      float innerWidth = fieldWidth - 8.0F;
      float offset = 0.0F;
      if (focused) {
         int safeCursor = Math.max(0, Math.min(this.editCursor, value.length()));
         offset = Math.max(0.0F, SkijaUi.textWidth(value.substring(0, safeCursor), 7.0F) - innerWidth + 1.0F);
      }

      canvas.save();
      canvas.clipRect(Rect.makeXYWH(innerX, fieldY, innerWidth, fieldHeight));
      if (focused && this.selectAll && !value.isEmpty()) {
         SkijaUi.fill(canvas, innerX, fieldY + 2.0F, Math.min(innerWidth, SkijaUi.textWidth(value, 7.0F)), fieldHeight - 4.0F, UiTheme.withAlpha(UiTheme.accent(), 52));
      }

      SkijaUi.text(canvas, shown, innerX - offset, fieldY, fieldHeight, color, 7.0F);
      if (focused && System.currentTimeMillis() / 500L % 2L == 0L) {
         float caretX = innerX - offset + SkijaUi.textWidth(value.substring(0, this.editCursor), 7.0F);
         SkijaUi.fill(canvas, caretX, fieldY + 2.0F, 1.0F, fieldHeight - 4.0F, UiTheme.accent());
      }

      canvas.restore();
   }

   private void drawNumberTrack(Canvas canvas, float rowY, float fraction) {
      float trackX = this.sidebarX + 13.0F;
      float trackWidth = 130.0F;
      float trackY = rowY + 18.0F - 4.0F;
      SkijaUi.fill(canvas, trackX, trackY, trackWidth, 2.0F, TRACK);
      SkijaUi.fill(canvas, trackX, trackY, trackWidth * HudRenderUtil.clamp(fraction, 0.0F, 1.0F), 2.0F, UiTheme.accent());
   }

   private void drawColor(Canvas canvas, ColorSetting setting, float rowY) {
      Color color = setting.get();
      boolean expanded = ColorPickerState.expanded(setting);
      boolean focused = this.editingColor == setting;
      float fieldX = this.sidebarX + 150.0F - 79.0F;
      float fieldY = rowY + 1.5F;
      float fieldWidth = 58.0F;
      float fieldHeight = 11.0F;
      SkijaUi.rounded(canvas, fieldX, fieldY, fieldWidth, fieldHeight, 2.0F, focused ? UiTheme.accent() : TRACK);
      SkijaUi.rounded(canvas, fieldX + 1.0F, fieldY + 1.0F, fieldWidth - 2.0F, fieldHeight - 2.0F, 1.5F, UiTheme.withAlpha(UiTheme.SURFACE, 238));
      String value = focused ? this.editText : setting.hex();
      float innerX = fieldX + 3.0F;
      float innerWidth = fieldWidth - 6.0F;
      float offset = 0.0F;
      if (focused) {
         int safeCursor = Math.max(0, Math.min(this.editCursor, value.length()));
         offset = Math.max(0.0F, SkijaUi.textWidth(value.substring(0, safeCursor), 7.0F) - innerWidth + 1.0F);
      }

      canvas.save();
      canvas.clipRect(Rect.makeXYWH(innerX, fieldY, innerWidth, fieldHeight));
      if (focused && this.selectAll && !value.isEmpty()) {
         SkijaUi.fill(canvas, innerX, fieldY + 2.0F, Math.min(innerWidth, SkijaUi.textWidth(value, 7.0F)), fieldHeight - 4.0F, UiTheme.withAlpha(UiTheme.accent(), 52));
      }

      if (!expanded) {
         SkijaUi.text(canvas, "Set Color", fieldX + 4.0F, fieldY, fieldHeight, UiTheme.accent(), 7.0F);
      } else if (ColorPickerState.mode(setting) == ColorPickerState.Mode.RGB) {
         SkijaUi.text(canvas, value, innerX - offset, fieldY, fieldHeight, focused ? UiTheme.TEXT : UiTheme.TEXT_MUTED, 7.0F);
      }
      if (focused && System.currentTimeMillis() / 500L % 2L == 0L) {
         float caretX = innerX - offset + SkijaUi.textWidth(value.substring(0, this.editCursor), 7.0F);
         SkijaUi.fill(canvas, caretX, fieldY + 2.0F, 1.0F, fieldHeight - 4.0F, UiTheme.accent());
      }

      canvas.restore();
      SkijaUi.rounded(canvas, this.sidebarX + 150.0F - 18.0F, rowY + 3.0F, 11.0F, 8.0F, 2.0F, setting.argb());
      if (!expanded) return;
      float modeY = rowY + 13.0F;
      ColorPickerState.drawModeSelection(canvas, this.sidebarX + 13.0F, modeY, 124.0F,
              ColorPickerState.mode(setting), UiTheme.TEXT_MUTED, UiTheme.accent());
      if (ColorPickerState.mode(setting) == ColorPickerState.Mode.PALETTE) {
         ColorPickerState.drawPalette(canvas, this.sidebarX + 13.0F, modeY + 16.0F, 124.0F, 36.0F, setting, UiTheme.TEXT);
         return;
      }
      int[] values = new int[]{color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
      int channels = setting.allowAlpha() ? 4 : 3;

      for(int i = 0; i < channels; ++i) {
         float channelY = rowY + 29.0F + (float)i * 9.0F;
         SkijaUi.boldText(canvas, CHANNEL_LABELS[i], this.sidebarX + 13.0F, channelY, 9.0F, CHANNEL_COLORS[i], 7.0F);
         float trackX = this.sidebarX + 22.0F;
         float trackWidth = 98.0F;
         SkijaUi.fill(canvas, trackX, channelY + 3.5F, trackWidth, 2.0F, TRACK);
         SkijaUi.fill(canvas, trackX, channelY + 3.5F, trackWidth * (float)values[i] / 255.0F, 2.0F, CHANNEL_COLORS[i]);
         String channelValue = Integer.toString(values[i]);
         float valueWidth = SkijaUi.textWidth(channelValue, 7.0F);
         SkijaUi.text(canvas, channelValue, this.sidebarX + 150.0F - 7.0F - valueWidth, channelY, 9.0F, UiTheme.TEXT_MUTED, 7.0F);
      }

   }

   private void drawRight(Canvas canvas, String value, float rowY, float height, int color, float size) {
      float valueWidth = SkijaUi.textWidth(value, size);
      SkijaUi.text(canvas, value, this.sidebarX + 150.0F - 8.0F - valueWidth, rowY, height, color, size);
   }

   private void drawScrollbar(Canvas canvas, float bodyY, float bodyHeight, float contentHeight) {
      if (!(contentHeight <= bodyHeight) && !(bodyHeight <= 0.0F)) {
         float thumbHeight = Math.max(12.0F, bodyHeight * bodyHeight / contentHeight);
         float travel = bodyHeight - thumbHeight;
         float maxScroll = contentHeight - bodyHeight;
         float thumbY = bodyY + (maxScroll <= 0.0F ? 0.0F : this.sidebarScroll / maxScroll * travel);
         SkijaUi.fill(canvas, this.sidebarX + 150.0F - 2.5F, bodyY, 2.0F, bodyHeight, UiTheme.withAlpha(UiTheme.BORDER, 120));
         SkijaUi.fill(canvas, this.sidebarX + 150.0F - 2.5F, thumbY, 2.0F, thumbHeight, UiTheme.accent());
      }
   }

   private void drawItem(Canvas canvas, EditorItem item, boolean active) {
      boolean hovered = item.contains((double)this.mouseX, (double)this.mouseY);
      int outline = !active && !hovered ? UiTheme.withAlpha(item.color(), 176) : item.color();
      drawOutline(canvas, item.x() - 1.0F, item.y() - 1.0F, item.width() + 2.0F, item.height() + 2.0F, outline);
      this.drawItemLabel(canvas, item, outline, active);
   }

   private void drawItemLabel(Canvas canvas, EditorItem item, int outline, boolean active) {
      String label = HudRenderUtil.fit(item.label(), 92.0F, 6.4F, true);
      float labelWidth = Math.max(18.0F, SkijaUi.boldTextWidth(label, 6.4F) + 7.0F);
      float labelX = HudRenderUtil.clamp(item.x(), 0.0F, Math.max(0.0F, (float)this.width - labelWidth));
      float labelY;
      if (item.y() >= 13.0F) {
         labelY = item.y() - 11.0F - 2.0F;
      } else if (item.y() + item.height() + 11.0F + 2.0F <= (float)this.height) {
         labelY = item.y() + item.height() + 2.0F;
      } else if (item.x() + item.width() + labelWidth + 2.0F <= (float)this.width) {
         labelX = item.x() + item.width() + 2.0F;
         labelY = HudRenderUtil.clamp(item.y(), 0.0F, Math.max(0.0F, (float)this.height - 11.0F));
      } else if (item.x() >= labelWidth + 2.0F) {
         labelX = item.x() - labelWidth - 2.0F;
         labelY = HudRenderUtil.clamp(item.y(), 0.0F, Math.max(0.0F, (float)this.height - 11.0F));
      } else {
         labelY = HudRenderUtil.clamp(item.y(), 0.0F, Math.max(0.0F, (float)this.height - 11.0F));
      }

      SkijaUi.fill(canvas, labelX, labelY, labelWidth, 11.0F, UiTheme.withAlpha(UiTheme.SURFACE, active ? 218 : 184));
      drawOutline(canvas, labelX, labelY, labelWidth, 11.0F, outline);
      SkijaUi.boldText(canvas, label, labelX + 3.5F, labelY, 11.0F, UiTheme.TEXT, 6.4F);
   }

   private static void drawOutline(Canvas canvas, float x, float y, float outlineWidth, float outlineHeight, int color) {
      if (!(outlineWidth <= 0.0F) && !(outlineHeight <= 0.0F)) {
         SkijaUi.fill(canvas, x, y, outlineWidth, 1.0F, color);
         SkijaUi.fill(canvas, x, y + Math.max(0.0F, outlineHeight - 1.0F), outlineWidth, 1.0F, color);
         if (outlineHeight > 2.0F) {
            SkijaUi.fill(canvas, x, y + 1.0F, 1.0F, outlineHeight - 2.0F, color);
            SkijaUi.fill(canvas, x + Math.max(0.0F, outlineWidth - 1.0F), y + 1.0F, 1.0F, outlineHeight - 2.0F, color);
         }

      }
   }

   private void drawActions(Canvas canvas) {
      float doneX = (float)this.width - 8.0F - 54.0F;
      float resetX = doneX - 5.0F - 54.0F;
      float y = (float)this.height - 8.0F - 20.0F;
      this.drawButton(canvas, resetX, y, "RESET", false, contains((double)this.mouseX, (double)this.mouseY, resetX, y, 54.0F, 20.0F));
      this.drawButton(canvas, doneX, y, "DONE", true, contains((double)this.mouseX, (double)this.mouseY, doneX, y, 54.0F, 20.0F));
   }

   private void drawButton(Canvas canvas, float x, float y, String label, boolean primary, boolean hovered) {
      int border = primary ? UiTheme.accent() : UiTheme.withAlpha(UiTheme.BORDER, 210);
      int background = hovered ? UiTheme.withAlpha(UiTheme.CONTROL_HOVER, 218) : UiTheme.withAlpha(UiTheme.SURFACE, 184);
      SkijaUi.rounded(canvas, x, y, 54.0F, 20.0F, 3.0F, border);
      SkijaUi.rounded(canvas, x + 1.0F, y + 1.0F, 52.0F, 18.0F, 2.0F, background);
      float textWidth = SkijaUi.boldTextWidth(label, 6.8F);
      SkijaUi.boldText(canvas, label, x + (54.0F - textWidth) * 0.5F, y, 20.0F, primary ? UiTheme.TEXT : UiTheme.TEXT_MUTED, 6.8F);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      float x = (float)event.x();
      float y = (float)event.y();
      this.finishTextEditing();
      if (this.clickSidebar(x, y, event.button())) {
         return true;
      } else {
         float doneX = (float)this.width - 8.0F - 54.0F;
         float resetX = doneX - 5.0F - 54.0F;
         float actionY = (float)this.height - 8.0F - 20.0F;
         if (contains((double)x, (double)y, doneX, actionY, 54.0F, 20.0F)) {
            if (event.button() == 0) {
               this.onClose();
            }

            return true;
         } else if (contains((double)x, (double)y, resetX, actionY, 54.0F, 20.0F)) {
            if (event.button() == 0) {
               this.resetPositions();
            }

            return true;
         } else {
            List<EditorItem> items = this.items();

            for(int index = items.size() - 1; index >= 0; --index) {
               EditorItem item = (EditorItem)items.get(index);
               if (item.contains((double)x, (double)y)) {
                  if (event.button() == 1) {
                     this.selectModule(item.module());
                     if (item.members().size() > 1) {
                        this.detachGroup(item);
                        return true;
                     }

                     for(HUDModuel member : item.members()) {
                        member.resetPosition();
                     }

                     HUDModuel var18 = item.module();
                     if (var18 instanceof Notifications) {
                        Notifications notifications = (Notifications)var18;
                        notifications.position.reset();
                     }

                     return true;
                  }

                  if (event.button() == 0) {
                     this.selectModule(item.module());
                     this.dragging = item;
                     this.dragOffsetX = x - item.x();
                     this.dragOffsetY = y - item.y();
                     this.draggingMembers = new ArrayList();

                     for(HUDModuel member : item.members()) {
                        this.draggingMembers.add(new GroupDragOffset(member, member.hudX((float)this.width) - item.x(), member.hudY((float)this.height) - item.y()));
                     }

                     HUDModuel member = item.module();
                     if (member instanceof Notifications) {
                        Notifications notifications = (Notifications)member;
                        notifications.useCustomPosition();
                     }

                     return true;
                  }
               }
            }

            return super.mouseClicked(event, doubleClick);
         }
      }
   }

   private void selectModule(HUDModuel module) {
      if (this.selectedModule != module) {
         this.finishTextEditing();
         boolean firstSelection = this.selectedModule == null;
         this.selectedModule = module;
         this.sidebarScroll = 0.0F;
         this.draggingNumber = null;
         this.draggingColor = null;
         this.draggingColorChannel = -1;
         this.inspectorProgress = firstSelection ? 0.0F : Math.min(this.inspectorProgress, 0.72F);
      }
   }

   private boolean clickSidebar(float mouseX, float mouseY, int button) {
      List<Slot> slots = this.buildSlots();
      float bodyHeight = this.sidebarBodyHeight(slots);
      float panelHeight = 22.0F + bodyHeight + 5.0F;
      if (!contains((double)mouseX, (double)mouseY, this.sidebarX, this.sidebarY, 150.0F, panelHeight)) {
         return false;
      } else {
         float bodyY = this.sidebarY + 22.0F;
         if (mouseY < bodyY) {
            return true;
         } else if (mouseY >= bodyY + bodyHeight) {
            return true;
         } else {
            float contentY = mouseY - bodyY + this.sidebarScroll;

            for(Slot slot : slots) {
               if (!(contentY < slot.y()) && !(contentY >= slot.y() + slot.height())) {
                  float localY = contentY - slot.y();
                  this.clickSetting(slot.setting(), mouseX, localY, button);
                  this.clampScroll(slots);
                  return true;
               }
            }

            return true;
         }
      }
   }

   private void clickSetting(Setting<?> setting, float mouseX, float localY, int button) {
      if (setting instanceof BooleanSetting booleanSetting) {
         if (button == 0 || button == 1) {
            booleanSetting.toggle();
         }
      } else if (setting instanceof FontSetting fontSetting) {
         fontSetting.cycle(button == 1 ? -1 : 1);
      } else if (setting instanceof EnumSetting<?> enumSetting) {
         enumSetting.cycle(button == 1 ? -1 : 1);
      } else if (setting instanceof IntSetting intSetting) {
         if (button == 1) {
            intSetting.set((Integer)intSetting.get() - intSetting.step());
         } else if (button == 0) {
            this.beginNumberDrag(setting, this.sidebarX + 13.0F, 130.0F, mouseX);
         }
      } else if (setting instanceof DoubleSetting doubleSetting) {
         if (button == 1) {
            doubleSetting.set((Double)doubleSetting.get() - doubleSetting.step());
         } else if (button == 0) {
            this.beginNumberDrag(setting, this.sidebarX + 13.0F, 130.0F, mouseX);
         }
      } else if (setting instanceof ColorSetting colorSetting) {
         if (button == 1) {
            ColorPickerState.toggle(colorSetting);
         } else if (button == 0 && localY < 13.0F && ColorPickerState.expanded(colorSetting) && mouseX >= this.sidebarX + 150.0F - 79.0F && mouseX < this.sidebarX + 150.0F - 21.0F) {
            this.focusColor(colorSetting);
         } else if (button == 0 && !ColorPickerState.expanded(colorSetting)) {
            return;
         } else if (button == 0 && ColorPickerState.expanded(colorSetting)
                 && ColorPickerState.clickMode(colorSetting, mouseX, localY,
                 this.sidebarX + 13.0F, 13.0F, 124.0F)) {
            return;
         } else if (button == 0 && ColorPickerState.expanded(colorSetting)
                 && ColorPickerState.mode(colorSetting) == ColorPickerState.Mode.PALETTE
                 && ColorPickerState.clickPalette(colorSetting, mouseX, localY,
                 this.sidebarX + 13.0F, 29.0F, 124.0F)) {
            return;
         } else if (button == 0 && localY >= 13.0F) {
            int channel = (int)((localY - 29.0F) / 9.0F);
            int channels = colorSetting.allowAlpha() ? 4 : 3;
            if (ColorPickerState.expanded(colorSetting) && ColorPickerState.mode(colorSetting) == ColorPickerState.Mode.RGB && channel >= 0 && channel < channels) {
               this.draggingColor = colorSetting;
               this.draggingColorChannel = channel;
               this.draggingTrackX = this.sidebarX + 22.0F;
               this.draggingTrackWidth = 98.0F;
               this.updateColor(mouseX);
            }
         }
      } else if (setting instanceof StringSetting stringSetting) {
         if (button == 1) {
            stringSetting.reset();
         } else if (button == 0) {
            this.editingString = stringSetting;
            this.editingColor = null;
            this.colorBeforeEdit = null;
            this.editText = (String)stringSetting.get();
            this.editCursor = this.editText.length();
            this.selectAll = false;
         }
      } else if (setting instanceof ButtonSetting buttonSetting) {
         if (button == 0) {
            buttonSetting.press();
         }
      }

   }

   public boolean keyPressed(KeyEvent event) {
      if (this.editingString == null && this.editingColor == null) {
         return super.keyPressed(event);
      } else if (event.key() != 256 && event.key() != 257 && event.key() != 335) {
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
            this.applyStringEditing();
            return true;
         } else if (event.isPaste()) {
            this.insertStringText(this.minecraft.keyboardHandler.getClipboard());
            return true;
         } else {
            switch (event.key()) {
               case 259:
                  if (this.selectAll) {
                     this.editText = "";
                     this.editCursor = 0;
                  } else if (this.editCursor > 0) {
                     int previous = previousIndex(this.editText, this.editCursor);
                     String var4 = this.editText.substring(0, previous);
                     this.editText = var4 + this.editText.substring(this.editCursor);
                     this.editCursor = previous;
                  }

                  this.applyStringEditing();
                  break;
               case 260:
               case 264:
               case 265:
               case 266:
               case 267:
               default:
                  return super.keyPressed(event);
               case 261:
                  if (this.selectAll) {
                     this.editText = "";
                     this.editCursor = 0;
                  } else if (this.editCursor < this.editText.length()) {
                     int next = nextIndex(this.editText, this.editCursor);
                     String var10001 = this.editText.substring(0, this.editCursor);
                     this.editText = var10001 + this.editText.substring(next);
                  }

                  this.applyStringEditing();
                  break;
               case 262:
                  this.editCursor = nextIndex(this.editText, this.editCursor);
                  break;
               case 263:
                  this.editCursor = previousIndex(this.editText, this.editCursor);
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

   public boolean charTyped(CharacterEvent event) {
      if ((this.editingString != null || this.editingColor != null) && event.isAllowedChatCharacter()) {
         this.insertStringText(event.codepointAsString());
         return true;
      } else {
         return super.charTyped(event);
      }
   }

   private void insertStringText(String inserted) {
      if (inserted != null && !inserted.isEmpty()) {
         String clean = inserted.replace('\n', ' ').replace("\r", "");
         if (this.selectAll) {
            this.editText = "";
            this.editCursor = 0;
         }

         int maxLength = this.editingColor != null ? 9 : 64;
         int remaining = maxLength - this.editText.length();
         if (remaining > 0) {
            if (clean.length() > remaining) {
               clean = clean.substring(0, remaining);
            }

            this.editText = this.editText.substring(0, this.editCursor) + clean + this.editText.substring(this.editCursor);
            this.editCursor += clean.length();
            this.selectAll = false;
            this.applyStringEditing();
         }
      }
   }

   private void applyStringEditing() {
      if (this.editingString != null) {
         this.editingString.set(this.editText);
      } else if (this.editingColor != null) {
         Color parsed = this.editingColor.parseHex(this.editText);
         if (parsed != null) {
            this.editingColor.set(parsed);
         }
      }

   }

   private void focusColor(ColorSetting setting) {
      this.editingString = null;
      this.editingColor = setting;
      this.colorBeforeEdit = setting.get();
      this.editText = setting.hex();
      this.editCursor = this.editText.length();
      this.selectAll = true;
   }

   private void finishTextEditing() {
      if (this.editingColor != null) {
         Color parsed = this.editingColor.parseHex(this.editText);
         this.editingColor.set(parsed != null ? parsed : this.colorBeforeEdit);
      } else {
         this.applyStringEditing();
      }

      this.editingString = null;
      this.editingColor = null;
      this.colorBeforeEdit = null;
      this.editText = "";
      this.editCursor = 0;
      this.selectAll = false;
   }

   private static int previousIndex(String value, int index) {
      return index <= 0 ? 0 : value.offsetByCodePoints(index, -1);
   }

   private static int nextIndex(String value, int index) {
      return index >= value.length() ? value.length() : value.offsetByCodePoints(index, 1);
   }

   private void beginNumberDrag(Setting<?> setting, float trackX, float trackWidth, float mouseX) {
      this.draggingNumber = setting;
      this.draggingTrackX = trackX;
      this.draggingTrackWidth = trackWidth;
      this.updateNumber(mouseX);
   }

   private void updateNumber(float mouseX) {
      float fraction = HudRenderUtil.clamp((mouseX - this.draggingTrackX) / this.draggingTrackWidth, 0.0F, 1.0F);
      Setting draggedSetting = this.draggingNumber;
      if (draggedSetting instanceof IntSetting setting) {
         double stepCount = (double)Math.round((float)(setting.max() - setting.min()) * fraction / (float)setting.step());
         setting.set((int)Math.round((double)setting.min() + stepCount * (double)setting.step()));
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
         int value = Math.round(HudRenderUtil.clamp((mouseX - this.draggingTrackX) / this.draggingTrackWidth, 0.0F, 1.0F) * 255.0F);
         Color current = this.draggingColor.get();
         int red = this.draggingColorChannel == 0 ? value : current.getRed();
         int green = this.draggingColorChannel == 1 ? value : current.getGreen();
         int blue = this.draggingColorChannel == 2 ? value : current.getBlue();
         int alpha = this.draggingColorChannel == 3 ? value : current.getAlpha();
         this.draggingColor.set(new Color(red, green, blue, alpha));
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      if (this.draggingNumber != null) {
         this.updateNumber((float)event.x());
         return true;
      } else if (this.draggingColor != null) {
         this.updateColor((float)event.x());
         return true;
      } else if (this.dragging == null) {
         return super.mouseDragged(event, dragX, dragY);
      } else {
         float x = (float)event.x() - this.dragOffsetX;
         float y = (float)event.y() - this.dragOffsetY;
         HudFusionManager.armFusion(this.dragging.module());
         SnapPoint snapped = this.snapPosition(this.dragging, x, y);

         for(GroupDragOffset member : this.draggingMembers) {
            member.module().moveTo(snapped.x() + member.offsetX(), snapped.y() + member.offsetY(), (float)this.width, (float)this.height);
         }

         HUDModuel var12 = this.dragging.module();
         if (var12 instanceof Notifications) {
            Notifications notifications = (Notifications)var12;
            notifications.useCustomPosition();
         }

         if (this.dragging.members().size() == 1 && HudFusionManager.selection(this.dragging.module(), (float)this.width, (float)this.height).fused()) {
            this.dragging = null;
            this.draggingMembers = List.of();
         }

         return true;
      }
   }

   private SnapPoint snapPosition(EditorItem moving, float rawX, float rawY) {
      float snapDistance = 7.0F;
      float snappedX = rawX;
      float snappedY = rawY;
      float bestXDistance = snapDistance + 1.0F;
      float bestYDistance = snapDistance + 1.0F;
      boolean watermarkOnly = moving.members().size() == 1 && HudFusionManager.watermarkAttachment(moving.module());
      if (watermarkOnly && !WatermarkHUD.INSTANCE.allowsHudFusion()) {
         watermarkOnly = false;
      }

      for(EditorItem other : this.items()) {
         if (!moving.members().contains(other.module()) && (!watermarkOnly || other.members().contains(WatermarkHUD.INSTANCE))) {
            if (rangesOverlap(rawY, moving.height(), other.y(), other.height())) {
               float[] xTargets = new float[]{other.x() + other.width(), other.x() - moving.width()};

               for(int index = 0; index < xTargets.length; ++index) {
                  float distance = Math.abs(rawX - xTargets[index]);
                  if (distance <= snapDistance && distance < bestXDistance) {
                     CompactAttachment attachment = this.compactAttachment(moving, other, index == 0);
                     snappedX = attachment == null ? xTargets[index] : attachment.x();
                     bestXDistance = distance;
                     if (attachment != null) {
                        snappedY = attachment.y();
                        bestYDistance = 0.0F;
                     }
                  }
               }
            }

            float[] alignedX = new float[]{other.x(), other.x() + (other.width() - moving.width()) * 0.5F, other.x() + other.width() - moving.width()};

            for(float target : alignedX) {
               float distance = Math.abs(rawX - target);
               if (distance <= snapDistance && distance < bestXDistance) {
                  snappedX = target;
                  bestXDistance = distance;
               }
            }

            if (rangesOverlap(rawX, moving.width(), other.x(), other.width())) {
               float[] yTargets = new float[]{other.y() + other.height(), other.y() - moving.height()};

               for(float target : yTargets) {
                  float distance = Math.abs(rawY - target);
                  if (distance <= snapDistance && distance < bestYDistance) {
                     snappedY = target;
                     bestYDistance = distance;
                  }
               }
            }

            float[] alignedY = new float[]{other.y(), other.y() + (other.height() - moving.height()) * 0.5F, other.y() + other.height() - moving.height()};

            for(float target : alignedY) {
               float distance = Math.abs(rawY - target);
               if (distance <= snapDistance && distance < bestYDistance) {
                  snappedY = target;
                  bestYDistance = distance;
               }
            }
         }
      }

      return new SnapPoint(snappedX, snappedY);
   }

   private CompactAttachment compactAttachment(EditorItem moving, EditorItem other, boolean attachToRight) {
      if (moving.members().size() == 1 && HudFusionManager.watermarkAttachment(moving.module()) && WatermarkHUD.INSTANCE.allowsHudFusion() && other.members().contains(WatermarkHUD.INSTANCE) && !other.members().stream().anyMatch((member) -> !HudFusionManager.compactReadout(member))) {
         HUDModuel anchor = this.edgeMember(other, attachToRight);
         float movingWidth = moving.module().hudWidth((float)this.width);
         float x = attachToRight ? anchor.hudX((float)this.width) + anchor.hudWidth((float)this.width) : anchor.hudX((float)this.width) - movingWidth;
         return new CompactAttachment(x, anchor.hudY((float)this.height));
      } else {
         return null;
      }
   }

   private HUDModuel edgeMember(EditorItem item, boolean rightEdge) {
      if (item.members().size() == 1) {
         return item.module();
      } else {
         HudFusionManager.FusionSelection selection = HudFusionManager.selection(item.module(), (float)this.width, (float)this.height);
         HUDModuel edge = (HUDModuel)item.members().getFirst();
         float edgePosition = rightEdge ? selection.cellBounds(edge).getRight() : selection.cellBounds(edge).getLeft();

         for(int index = 1; index < item.members().size(); ++index) {
            HUDModuel candidate = (HUDModuel)item.members().get(index);
            Rect cell = selection.cellBounds(candidate);
            float position = rightEdge ? cell.getRight() : cell.getLeft();
            if (rightEdge && position > edgePosition || !rightEdge && position < edgePosition) {
               edge = candidate;
               edgePosition = position;
            }
         }

         return edge;
      }
   }

   private void detachGroup(EditorItem item) {
      List<HUDModuel> ordered = new ArrayList(item.members());
      HudFusionManager.FusionSelection selection = HudFusionManager.selection(item.module(), (float)this.width, (float)this.height);
      HudFusionManager.detach(item.members());
      ordered.sort((first, second) -> Float.compare(selection.cellBounds(first).getLeft(), selection.cellBounds(second).getLeft()));
      float totalWidth = 18.0F * (float)Math.max(0, ordered.size() - 1);

      for(HUDModuel member : ordered) {
         totalWidth += member.hudWidth((float)this.width);
      }

      float startX = HudRenderUtil.clamp(item.x(), 6.0F, Math.max(6.0F, (float)this.width - totalWidth - 6.0F));
      float y = HudRenderUtil.clamp(item.y(), 6.0F, Math.max(6.0F, (float)this.height - item.height() - 6.0F));
      float cursor = startX;

      for(HUDModuel member : ordered) {
         member.moveTo(cursor, y, (float)this.width, (float)this.height);
         cursor += member.hudWidth((float)this.width) + 18.0F;
      }

   }

   private static boolean rangesOverlap(float firstStart, float firstSize, float secondStart, float secondSize) {
      return firstStart < secondStart + secondSize && firstStart + firstSize > secondStart;
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      boolean wasInteracting = this.dragging != null || this.draggingNumber != null || this.draggingColor != null;
      this.dragging = null;
      this.draggingMembers = List.of();
      this.draggingNumber = null;
      this.draggingColor = null;
      this.draggingColorChannel = -1;
      return wasInteracting || super.mouseReleased(event);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      List<Slot> slots = this.buildSlots();
      float bodyHeight = this.sidebarBodyHeight(slots);
      if (contains(mouseX, mouseY, this.sidebarX, this.sidebarY + 22.0F, 150.0F, bodyHeight)) {
         this.sidebarScroll -= (float)verticalAmount * 20.0F;
         this.clampScroll(slots);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   private List<Slot> buildSlots() {
      List<Slot> slots = new ArrayList();
      if (this.selectedModule == null) {
         return slots;
      } else {
         float y = 0.0F;

         for(Setting<?> setting : this.selectedModule.settings()) {
            if (!this.isHudPosition(this.selectedModule, setting) && setting.visible()) {
               float settingHeight = settingHeight(setting);
               slots.add(new Slot(setting, y, settingHeight));
               y += settingHeight;
            }
         }

         return slots;
      }
   }

   private static float contentHeight(List<Slot> slots) {
      if (slots.isEmpty()) {
         return 0.0F;
      } else {
         Slot last = (Slot)slots.get(slots.size() - 1);
         return last.y() + last.height();
      }
   }

   private float sidebarBodyHeight(List<Slot> slots) {
      float content = contentHeight(slots);
      float available = Math.max(0.0F, (float)this.height - 22.0F - 5.0F - 12.0F);
      return Math.min(content, Math.min(280.0F, available));
   }

   private static float settingHeight(Setting<?> setting) {
      if (!(setting instanceof IntSetting) && !(setting instanceof DoubleSetting)) {
         if (setting instanceof ColorSetting) {
            ColorSetting colorSetting = (ColorSetting)setting;
            return ColorPickerState.height(colorSetting, 13.0F, 9.0F, ColorPickerState.paletteHeight(124.0F));
         } else {
            return 13.0F;
         }
      } else {
         return 18.0F;
      }
   }

   private void clampScroll(List<Slot> slots) {
      float maxScroll = Math.max(0.0F, contentHeight(slots) - this.sidebarBodyHeight(slots));
      this.sidebarScroll = HudRenderUtil.clamp(this.sidebarScroll, 0.0F, maxScroll);
   }

   private boolean isHudPosition(HUDModuel module, Setting<?> setting) {
      return setting == module.xPosition || setting == module.yPosition;
   }

   private List<EditorItem> items() {
      List<EditorItem> result = new ArrayList();
      Set<HUDModuel> consumed = Collections.newSetFromMap(new IdentityHashMap());

      for(HUDModuel hud : this.enabledModules()) {
         if (!consumed.contains(hud)) {
            HudFusionManager.FusionSelection fusion = HudFusionManager.selection(hud, (float)this.width, (float)this.height);
            consumed.addAll(fusion.members());
            if (fusion.fused()) {
               Rect bounds = fusion.bounds();
               String label = (String)fusion.members().stream().map(HudEditorScreen::compactLabel).reduce((first, second) -> first + " + " + second).orElse(compactLabel(fusion.owner()));
               result.add(new EditorItem(fusion.owner(), List.copyOf(fusion.members()), label, bounds.getLeft(), bounds.getTop(), bounds.getWidth(), bounds.getHeight(), fusion.owner().editorColor()));
            } else {
               float itemWidth = hud.hudWidth((float)this.width);
               float itemHeight = hud.hudHeight((float)this.height);
               float itemX = hud.hudX((float)this.width);
               float itemY = hud.hudY((float)this.height);
               if (hud instanceof Notifications) {
                  Notifications notifications = (Notifications)hud;
                  if (!notifications.position.is(Notifications.Position.CUSTOM)) {
                     float var10000;
                     switch ((Notifications.Position)notifications.position.get()) {
                        case TOP_CENTER:
                           var10000 = ((float)this.width - itemWidth) * 0.5F;
                           break;
                        case TOP_LEFT:
                        case BOTTOM_LEFT:
                           var10000 = 6.0F;
                           break;
                        case TOP_RIGHT:
                        case BOTTOM_RIGHT:
                           var10000 = (float)this.width - itemWidth - 6.0F;
                           break;
                        case CUSTOM:
                           var10000 = itemX;
                           break;
                        default:
                           throw new MatchException((String)null, (Throwable)null);
                     }

                     itemX = var10000;
                     switch ((Notifications.Position)notifications.position.get()) {
                        case TOP_CENTER:
                        case TOP_LEFT:
                        case TOP_RIGHT:
                           var10000 = 6.0F;
                           break;
                        case BOTTOM_LEFT:
                        case BOTTOM_RIGHT:
                           var10000 = (float)this.height - itemHeight - 6.0F;
                           break;
                        case CUSTOM:
                           var10000 = itemY;
                           break;
                        default:
                           throw new MatchException((String)null, (Throwable)null);
                     }

                     itemY = var10000;
                  }
               }

               result.add(new EditorItem(hud, List.of(hud), compactLabel(hud), itemX, itemY, itemWidth, itemHeight, hud.editorColor()));
            }
         }
      }

      return result;
   }

   private List<HUDModuel> enabledModules() {
      List<HUDModuel> result = new ArrayList();

      for(HUDModuel module : this.hudModules()) {
         if (module.isEnabled()) {
            result.add(module);
         }
      }

      return result;
   }

   private List<HUDModuel> hudModules() {
      List<HUDModuel> result = new ArrayList();

      for(Module module : ModuleManager.INSTANCE.modules()) {
         if (module instanceof HUDModuel hud) {
            result.add(hud);
         }
      }

      return result;
   }

   private void resetPositions() {
      for(HUDModuel hud : this.hudModules()) {
         hud.resetPosition();
      }

      Notifications.INSTANCE.position.reset();
   }

   private static String compactLabel(HUDModuel module) {
      return module.editorLabel().replace(" HUD", "");
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

   private static boolean contains(double mouseX, double mouseY, float x, float y, float width, float height) {
      return mouseX >= (double)x && mouseX < (double)(x + width) && mouseY >= (double)y && mouseY < (double)(y + height);
   }

   public void onClose() {
      this.finishTextEditing();
      ConfigManager.INSTANCE.save();
      NotificationManager.INSTANCE.post(NotificationType.SUCCESS, "HUD Editor", "Layout saved");
      super.onClose();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static record Slot(Setting<?> setting, float y, float height) {
   }

   private static record SnapPoint(float x, float y) {
   }

   private static record CompactAttachment(float x, float y) {
   }

   private static record GroupDragOffset(HUDModuel module, float offsetX, float offsetY) {
   }

   private static record EditorItem(HUDModuel module, List<HUDModuel> members, String label, float x, float y, float width, float height, int color) {
      boolean contains(double mouseX, double mouseY) {
         return HudEditorScreen.contains(mouseX, mouseY, this.x, this.y, this.width, this.height);
      }
   }
}
