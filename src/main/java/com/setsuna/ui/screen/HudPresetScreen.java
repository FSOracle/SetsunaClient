package com.setsuna.ui.screen;

import com.setsuna.Setsuna;
import com.setsuna.module.modules.ClickGui;
import com.setsuna.module.modules.HudPresets;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.ui.UiTheme;
import com.setsuna.ui.clickgui.ClickGuiText;
import com.setsuna.ui.clickgui.DropGridRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public final class HudPresetScreen extends AbstractSkijaScreen {
   private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("setsuna", "textures/hud/watermark_logo.png");
   private static final float ROW_HEIGHT = 29.0F;
   private static final int GLASS = -653785328;
   private static final int GLASS_ALT = -1190393325;
   private static final int HAIRLINE = 620756991;
   private final Screen parent;
   private final UiControls.TextInput name = new UiControls.TextInput(48, false, (codePoint) -> !Character.isISOControl(codePoint));
   private List<String> profiles = List.of();
   private String selectedPersonal = "";
   private HudPresets.OfficialPreset selectedOfficial;
   private HudPresets.PreviewData preview;
   private int firstVisible;
   private long lastFrame;
   private float intro;
   private float previewMotion;
   private Integer originalMenuBlur;

   public HudPresetScreen(Screen parent) {
      super(Component.literal("HUD Presets"));
      this.selectedOfficial = HudPresets.OfficialPreset.ONE;
      this.preview = HudPresets.PreviewData.EMPTY;
      this.lastFrame = System.nanoTime();
      this.parent = parent;
      this.name.setPlaceholder("Preset name");
   }

   protected void init() {
      this.lastFrame = System.nanoTime();
      this.intro = 0.0F;
      this.previewMotion = 0.0F;
      this.refreshProfiles();
      this.selectOfficial(HudPresets.OfficialPreset.ONE);
      this.updateInputBounds(this.layout());
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      int blur = (Integer)ClickGui.INSTANCE.popBackgroundBlur.get();
      if (blur > 0) {
         this.applyMenuBlur(blur);
         graphics.blurBeforeThisStratum();
      } else {
         this.restoreMenuBlur();
      }

      graphics.fill(0, 0, this.width, this.height, 637601805);
   }

   protected void drawScreen(Canvas canvas) {
      float delta = this.frameDelta();
      this.intro = approach(this.intro, 1.0F, delta, 9.0F);
      this.previewMotion = approach(this.previewMotion, 1.0F, delta, 11.0F);
      Layout layout = this.layout();
      this.updateInputBounds(layout);
      this.drawBackdrop(canvas);
      this.drawPanel(canvas, layout, easeOutCubic(this.intro));
   }

   private void drawBackdrop(Canvas canvas) {
      SkijaUi.fill(canvas, 0.0F, 0.0F, (float)this.width, (float)this.height, 1375864844);
      DropGridRenderer.drawFull(canvas, (float)this.width, (float)this.height, this.intro * 0.58F, UiTheme.accent());
   }

   private void drawPanel(Canvas canvas, Layout layout, float visibility) {
      UiControls.Box panel = layout.panel();
      float centerX = panel.x() + panel.width() * 0.5F;
      float centerY = panel.y() + panel.height() * 0.5F;
      float scale = 0.965F + 0.035F * visibility;
      canvas.save();
      canvas.translate(centerX, centerY);
      canvas.scale(scale, scale);
      canvas.translate(-centerX, -centerY);
      SkijaUi.dropShadowRounded(canvas, panel.x(), panel.y(), panel.width(), panel.height(), 7.0F, 5.0F, 20.0F, withAlpha(-805306368, visibility));
      SkijaUi.rounded(canvas, panel.x(), panel.y(), panel.width(), panel.height(), 7.0F, withAlpha(-16777216, visibility));
      SkijaUi.rounded(canvas, panel.x() + 1.0F, panel.y() + 1.0F, panel.width() - 2.0F, panel.height() - 2.0F, 6.0F, withAlpha(-653785328, visibility));
      canvas.save();
      canvas.clipRRect(RRect.makeXYWH(panel.x() + 1.0F, panel.y() + 1.0F, panel.width() - 2.0F, panel.height() - 2.0F, 6.0F), ClipMode.INTERSECT, true);
      this.drawRail(canvas, layout, visibility);
      this.drawHeader(canvas, layout, visibility);
      this.drawPresetColumn(canvas, layout, visibility);
      this.drawPreviewColumn(canvas, layout, visibility);
      canvas.restore();
      canvas.restore();
   }

   private void drawRail(Canvas canvas, Layout layout, float visibility) {
      UiControls.Box rail = layout.rail();
      SkijaUi.fill(canvas, rail.x(), rail.y(), rail.width(), rail.height(), withAlpha(-939524096, visibility));
      SkijaUi.fill(canvas, rail.x() + rail.width() - 1.0F, rail.y(), 1.0F, rail.height(), withAlpha(620756991, visibility));
      this.drawLogo(canvas, rail.x() + (rail.width() - 24.0F) * 0.5F, rail.y() + 13.0F, 24.0F, visibility);
      float markerY = rail.y() + 62.0F;
      SkijaUi.rounded(canvas, rail.x() + 7.0F, markerY, rail.width() - 14.0F, 30.0F, 5.0F, withAlpha(UiTheme.withAlpha(UiTheme.accent(), 92), visibility));
      drawFourTiles(canvas, rail.x() + rail.width() * 0.5F, markerY + 15.0F, withAlpha(UiTheme.TEXT, visibility));
      UiControls.Box back = layout.back();
      boolean hovered = back.contains((double)this.mouseX, (double)this.mouseY);
      SkijaUi.rounded(canvas, back.x(), back.y(), back.width(), back.height(), 5.0F, withAlpha(hovered ? 822083583 : 318767103, visibility));
      SkijaUi.boldText(canvas, "<", back.x() + 10.0F, back.y(), back.height(), withAlpha(hovered ? UiTheme.TEXT : UiTheme.TEXT_MUTED, visibility), 9.0F);
   }

   private void drawHeader(Canvas canvas, Layout layout, float visibility) {
      UiControls.Box header = layout.header();
      SkijaUi.rounded(canvas, header.x(), header.y(), header.width(), header.height(), 5.0F, withAlpha(UiTheme.themedHeader(230), visibility));
      SkijaUi.fill(canvas, header.x(), header.y() + header.height() - 6.0F, header.width(), 6.0F, withAlpha(UiTheme.themedHeader(230), visibility));
      SkijaUi.fill(canvas, header.x(), header.y() + header.height() - 1.0F, header.width(), 1.0F, withAlpha(UiTheme.withAlpha(UiTheme.accent(), 72), visibility));
      SkijaUi.boldText(canvas, "HUD PRESETS", header.x() + 16.0F, header.y() + 4.0F, 18.0F, withAlpha(UiTheme.TEXT, visibility), 10.5F);
      SkijaUi.text(canvas, "COLOR-INDEPENDENT LAYOUTS", header.x() + 16.0F, header.y() + 20.0F, 12.0F, withAlpha(UiTheme.TEXT_FAINT, visibility), 6.0F);
      String count = this.preview.items().size() + " ACTIVE";
      float badgeWidth = SkijaUi.boldTextWidth(count, 6.0F) + 14.0F;
      float badgeX = header.x() + header.width() - badgeWidth - 14.0F;
      SkijaUi.rounded(canvas, badgeX, header.y() + 11.0F, badgeWidth, 18.0F, 5.0F, withAlpha(UiTheme.withAlpha(UiTheme.accent(), 46), visibility));
      SkijaUi.boldText(canvas, count, badgeX + 7.0F, header.y() + 11.0F, 18.0F, withAlpha(UiTheme.accent(), visibility), 6.0F);
   }

   private void drawPresetColumn(Canvas canvas, Layout layout, float visibility) {
      UiControls.Box column = layout.presetColumn();
      SkijaUi.fill(canvas, column.x(), column.y(), column.width(), column.height(), withAlpha(-1190393325, visibility));
      SkijaUi.fill(canvas, column.x() + column.width() - 1.0F, column.y(), 1.0F, column.height(), withAlpha(620756991, visibility));
      float x = column.x() + 12.0F;
      SkijaUi.boldText(canvas, "OFFICIAL", x, column.y() + 10.0F, 13.0F, withAlpha(UiTheme.TEXT_FAINT, visibility), 6.2F);
      HudPresets.OfficialPreset[] presets = HudPresets.OfficialPreset.values();

      for(int index = 0; index < presets.length; ++index) {
         this.drawOfficialRow(canvas, officialRow(layout, index), index, presets[index], visibility);
      }

      float personalTitleY = layout.name().y() - 21.0F;
      SkijaUi.boldText(canvas, "PERSONAL", x, personalTitleY, 13.0F, withAlpha(UiTheme.TEXT_FAINT, visibility), 6.2F);
      this.name.draw(canvas, this.mouseX, this.mouseY, withAlpha(UiTheme.accent(), visibility), withAlpha(-1458959342, visibility), withAlpha(620756991, visibility));
      this.drawCompactButton(canvas, layout.save(), "Save", !this.name.text().isBlank(), true, visibility);
      this.drawCompactButton(canvas, layout.folder(), "Open Folder", true, false, visibility);
      UiControls.Box list = layout.list();
      SkijaUi.fill(canvas, list.x(), list.y() - 5.0F, list.width(), 1.0F, withAlpha(620756991, visibility));
      if (this.profiles.isEmpty()) {
         UiControls.centeredText(canvas, "No personal presets", list, withAlpha(UiTheme.TEXT_FAINT, visibility), false);
      } else {
         int visibleRows = visibleRows(layout);

         for(int slot = 0; slot < visibleRows && this.firstVisible + slot < this.profiles.size(); ++slot) {
            this.drawPersonalRow(canvas, personalRow(layout, slot), (String)this.profiles.get(this.firstVisible + slot), visibility);
         }

      }
   }

   private void drawOfficialRow(Canvas canvas, UiControls.Box row, int index, HudPresets.OfficialPreset preset, float visibility) {
      boolean hovered = row.contains((double)this.mouseX, (double)this.mouseY);
      boolean selected = this.selectedOfficial == preset;
      this.drawSelectionRow(canvas, row, hovered, selected, visibility);
      SkijaUi.boldText(canvas, "Official " + (index + 1), row.x() + 10.0F, row.y(), row.height(), withAlpha(selected ? UiTheme.TEXT : UiTheme.TEXT_MUTED, visibility), 7.2F);
      drawMiniLayout(canvas, row.x() + row.width() - 27.0F, row.y() + row.height() * 0.5F, selected, visibility);
   }

   private void drawPersonalRow(Canvas canvas, UiControls.Box row, String profile, float visibility) {
      boolean hovered = row.contains((double)this.mouseX, (double)this.mouseY);
      boolean selected = this.selectedOfficial == null && profile.equalsIgnoreCase(this.selectedPersonal);
      this.drawSelectionRow(canvas, row, hovered, selected, visibility);
      ClickGuiText.drawVerticallyCentered(canvas, profile, row.x() + 9.0F, row.y(), row.width() - 48.0F, row.height(), 9.0F, withAlpha(selected ? UiTheme.TEXT : UiTheme.TEXT_MUTED, visibility), 6.8F, true);
      UiControls.Box delete = deleteBox(row);
      boolean deleteHover = delete.contains((double)this.mouseX, (double)this.mouseY);
      SkijaUi.rounded(canvas, delete.x(), delete.y(), delete.width(), delete.height(), 4.0F, withAlpha(deleteHover ? -1192797083 : 786914405, visibility));
      UiControls.centeredText(canvas, "x", delete, withAlpha(deleteHover ? UiTheme.TEXT : -1533282, visibility), true);
   }

   private void drawSelectionRow(Canvas canvas, UiControls.Box row, boolean hovered, boolean selected, float visibility) {
      int fill = selected ? UiTheme.withAlpha(UiTheme.accent(), 82) : (hovered ? 620756991 : 251658239);
      int border = selected ? UiTheme.withAlpha(UiTheme.accent(), 126) : 620756991;
      SkijaUi.rounded(canvas, row.x(), row.y(), row.width(), row.height(), 5.0F, withAlpha(border, visibility));
      SkijaUi.rounded(canvas, row.x() + 1.0F, row.y() + 1.0F, row.width() - 2.0F, row.height() - 2.0F, 4.0F, withAlpha(fill, visibility));
   }

   private void drawPreviewColumn(Canvas canvas, Layout layout, float visibility) {
      UiControls.Box column = layout.previewColumn();
      float x = column.x() + 16.0F;
      String title = this.selectedTitle();
      SkijaUi.boldText(canvas, title, x, column.y() + 13.0F, 17.0F, withAlpha(UiTheme.TEXT, visibility), 9.0F);
      SkijaUi.text(canvas, this.selectedOfficial == null ? "PERSONAL HUD PRESET" : "OFFICIAL HUD PRESET", x, column.y() + 29.0F, 12.0F, withAlpha(UiTheme.TEXT_FAINT, visibility), 6.0F);
      this.drawCompactButton(canvas, layout.apply(), "Apply", this.hasSelection(), true, visibility);
      this.drawPreview(canvas, layout.preview(), layout.previewColumn(), visibility * easeOutCubic(this.previewMotion));
      this.drawPreviewFooter(canvas, layout, visibility);
   }

   private void drawPreview(Canvas canvas, UiControls.Box viewport, UiControls.Box column, float visibility) {
      SkijaUi.dropShadowRounded(canvas, viewport.x(), viewport.y(), viewport.width(), viewport.height(), 5.0F, 3.0F, 12.0F, withAlpha(-1979711488, visibility));
      SkijaUi.rounded(canvas, viewport.x(), viewport.y(), viewport.width(), viewport.height(), 5.0F, withAlpha(1207959552, visibility));
      SkijaUi.rounded(canvas, viewport.x() + 1.0F, viewport.y() + 1.0F, viewport.width() - 2.0F, viewport.height() - 2.0F, 4.0F, withAlpha(-1492711152, visibility));
      float grid = Math.max(12.0F, viewport.width() / 18.0F);

      for(float gx = viewport.x() + grid; gx < viewport.x() + viewport.width(); gx += grid) {
         SkijaUi.fill(canvas, gx, viewport.y() + 1.0F, 1.0F, viewport.height() - 2.0F, withAlpha(268435455, visibility));
      }

      for(float gy = viewport.y() + grid; gy < viewport.y() + viewport.height(); gy += grid) {
         SkijaUi.fill(canvas, viewport.x() + 1.0F, gy, viewport.width() - 2.0F, 1.0F, withAlpha(268435455, visibility));
      }

      if (this.preview.items().isEmpty()) {
         UiControls.centeredText(canvas, "EMPTY LAYOUT", viewport, withAlpha(UiTheme.TEXT_FAINT, visibility), true);
      } else {
         canvas.save();
         canvas.clipRRect(RRect.makeXYWH(viewport.x() + 2.0F, viewport.y() + 2.0F, viewport.width() - 4.0F, viewport.height() - 4.0F, 3.0F), ClipMode.INTERSECT, true);
         float inset = 7.0F;
         List<PreviewBlock> blocks = new ArrayList();

         for(HudPresets.PreviewItem item : this.preview.items()) {
            PreviewSize size = previewSize(item, viewport.width(), viewport.height());
            float blockX = viewport.x() + inset + item.x() * Math.max(0.0F, viewport.width() - size.width() - inset * 2.0F);
            float blockY = viewport.y() + inset + item.y() * Math.max(0.0F, viewport.height() - size.height() - inset * 2.0F);
            float localScale = 0.82F + 0.18F * visibility;
            float drawWidth = size.width() * localScale;
            float drawHeight = size.height() * localScale;
            blockX += (size.width() - drawWidth) * 0.5F;
            blockY += (size.height() - drawHeight) * 0.5F;
            int color = previewColor(item.moduleId());
            if (item.fused()) {
               SkijaUi.rounded(canvas, blockX - 2.0F, blockY - 2.0F, drawWidth + 4.0F, drawHeight + 4.0F, 4.0F, withAlpha(UiTheme.withAlpha(UiTheme.accent(), 96), visibility));
            }

            SkijaUi.rounded(canvas, blockX, blockY, drawWidth, drawHeight, Math.min(3.0F, drawHeight * 0.35F), withAlpha(color, visibility));
            blocks.add(new PreviewBlock(item, blockX, blockY, drawWidth, drawHeight, color));
         }

         canvas.restore();
         UiControls.Box labelArea = new UiControls.Box(column.x() + 8.0F, column.y() + 46.0F, column.width() - 16.0F, column.height() - 68.0F);

         for(LabelPlacement placement : this.layoutPreviewLabels(viewport, labelArea, blocks)) {
            this.drawPreviewLabel(canvas, placement, visibility);
         }

      }
   }

   private void drawPreviewFooter(Canvas canvas, Layout layout, float visibility) {
      UiControls.Box viewport = layout.preview();
      float y = viewport.y() + viewport.height() + 10.0F;
      SkijaUi.text(canvas, this.preview.items().size() + " HUD ELEMENTS", viewport.x(), y, 14.0F, withAlpha(UiTheme.TEXT_FAINT, visibility), 6.0F);
      long fused = this.preview.items().stream().filter(HudPresets.PreviewItem::fused).count();
      if (fused > 0L) {
         String fusion = fused + " FUSED GROUP";
         float textWidth = SkijaUi.boldTextWidth(fusion, 6.0F);
         SkijaUi.boldText(canvas, fusion, viewport.x() + viewport.width() - textWidth, y, 14.0F, withAlpha(UiTheme.accent(), visibility), 6.0F);
      }

   }

   private List<LabelPlacement> layoutPreviewLabels(UiControls.Box viewport, UiControls.Box labelArea, List<PreviewBlock> blocks) {
      List<LabelPlacement> placements = new ArrayList();
      float fontSize = Math.max(4.7F, Math.min(6.0F, viewport.width() / 75.0F));
      float labelWidth = Math.max(46.0F, Math.min(92.0F, viewport.width() * 0.32F));
      float lineHeight = 7.0F;

      for(int index = 0; index < blocks.size(); ++index) {
         PreviewBlock block = (PreviewBlock)blocks.get(index);
         List<String> lines = ClickGuiText.wrap(block.item().displayName(), labelWidth, fontSize, true);
         float textWidth = (Float)lines.stream().map((line) -> SkijaUi.boldTextWidth(line, fontSize)).max(Float::compare).orElse(0.0F);
         float textHeight = Math.max(lineHeight, (float)lines.size() * lineHeight);
         float centerX = block.x() + block.width() * 0.5F;
         float centerY = block.y() + block.height() * 0.5F;
         float gap = 11.0F;
         float laneX = labelArea.x() + 4.0F + (float)(index % 3) * Math.max(1.0F, (labelArea.width() - textWidth - 8.0F) * 0.5F);
         List<float[]> candidates = new ArrayList();
         candidates.add(new float[]{block.x() + block.width() + gap, centerY - textHeight * 0.5F});
         candidates.add(new float[]{block.x() - gap - textWidth, centerY - textHeight * 0.5F});
         candidates.add(new float[]{centerX - textWidth * 0.5F, block.y() - gap - textHeight});
         candidates.add(new float[]{centerX - textWidth * 0.5F, block.y() + block.height() + gap});
         candidates.add(new float[]{block.x() + block.width() + gap, block.y() - textHeight - 4.0F});
         candidates.add(new float[]{block.x() - gap - textWidth, block.y() + block.height() + 4.0F});
         candidates.add(new float[]{laneX, viewport.y() - textHeight - 9.0F - (float)(index % 2) * 9.0F});
         candidates.add(new float[]{laneX, viewport.y() + viewport.height() + 7.0F + (float)(index % 2) * 9.0F});
         float verticalStep = Math.max(10.0F, textHeight + 3.0F);

         for(float scanY = labelArea.y() + 2.0F; scanY <= labelArea.y() + labelArea.height() - textHeight - 2.0F; scanY += verticalStep) {
            candidates.add(new float[]{labelArea.x() + 2.0F, scanY});
            candidates.add(new float[]{labelArea.x() + labelArea.width() - textWidth - 2.0F, scanY});
         }

         float horizontalStep = Math.max(28.0F, textWidth + 5.0F);

         for(float scanX = labelArea.x() + 2.0F; scanX <= labelArea.x() + labelArea.width() - textWidth - 2.0F; scanX += horizontalStep) {
            candidates.add(new float[]{scanX, viewport.y() - textHeight - 8.0F});
            candidates.add(new float[]{scanX, viewport.y() + viewport.height() + 7.0F});
         }

         LabelPlacement best = null;
         float bestScore = Float.POSITIVE_INFINITY;

         for(float[] candidate : candidates) {
            float candidateX = clamp(candidate[0], labelArea.x() + 2.0F, labelArea.x() + labelArea.width() - textWidth - 2.0F);
            float candidateY = clamp(candidate[1], labelArea.y() + 2.0F, labelArea.y() + labelArea.height() - textHeight - 2.0F);
            float shift = Math.abs(candidateX - candidate[0]) + Math.abs(candidateY - candidate[1]);
            float score = shift * 1.8F;

            for(LabelPlacement placed : placements) {
               score += overlap(candidateX, candidateY, textWidth, textHeight, placed.x(), placed.y(), placed.width(), placed.height()) * 45.0F;
            }

            for(PreviewBlock other : blocks) {
               if (other != block) {
                  score += overlap(candidateX - 2.0F, candidateY - 1.0F, textWidth + 4.0F, textHeight + 2.0F, other.x(), other.y(), other.width(), other.height()) * 18.0F;
               }
            }

            score += overlap(candidateX, candidateY, textWidth, textHeight, viewport.x(), viewport.y() + viewport.height() + 5.0F, viewport.width(), 18.0F) * 60.0F;
            score += (float)Math.hypot((double)(candidateX + textWidth * 0.5F - centerX), (double)(candidateY + textHeight * 0.5F - centerY)) * 0.08F;
            if (score < bestScore) {
               bestScore = score;
               best = new LabelPlacement(block, candidateX, candidateY, textWidth, textHeight, labelWidth, lineHeight, fontSize);
            }
         }

         if (best != null) {
            placements.add(best);
         }
      }

      return placements;
   }

   private void drawPreviewLabel(Canvas canvas, LabelPlacement placement, float visibility) {
      PreviewBlock block = placement.block();
      float blockCenterX = block.x() + block.width() * 0.5F;
      float blockCenterY = block.y() + block.height() * 0.5F;
      float labelCenterX = placement.x() + placement.width() * 0.5F;
      float labelCenterY = placement.y() + placement.height() * 0.5F;
      float dx = labelCenterX - blockCenterX;
      float dy = labelCenterY - blockCenterY;
      if (Math.abs(dx) >= Math.abs(dy)) {
         float startX = dx >= 0.0F ? block.x() + block.width() : block.x();
         float startY = clamp(labelCenterY, block.y(), block.y() + block.height());
         float endX = dx >= 0.0F ? placement.x() - 2.0F : placement.x() + placement.width() + 2.0F;
         float endY = clamp(startY, placement.y(), placement.y() + placement.height());
         float bendX = startX + (endX - startX) * 0.58F;
         drawPreviewRay(canvas, block, startX, startY, bendX, startY, endX, endY, visibility);
      } else {
         float startX = clamp(labelCenterX, block.x(), block.x() + block.width());
         float startY = dy >= 0.0F ? block.y() + block.height() : block.y();
         float endX = clamp(startX, placement.x(), placement.x() + placement.width());
         float endY = dy >= 0.0F ? placement.y() - 2.0F : placement.y() + placement.height() + 2.0F;
         float bendY = startY + (endY - startY) * 0.58F;
         drawPreviewRay(canvas, block, startX, startY, startX, bendY, endX, endY, visibility);
      }

      ClickGuiText.draw(canvas, block.item().displayName(), placement.x(), placement.y(), placement.labelWidth(), placement.lineHeight(), withAlpha(UiTheme.TEXT_MUTED, visibility), placement.fontSize(), true);
   }

   private static void drawPreviewRay(Canvas canvas, PreviewBlock block, float startX, float startY, float bendX, float bendY, float endX, float endY, float visibility) {
      int color = withAlpha(UiTheme.withAlpha(block.color(), 210), visibility);
      SkijaUi.line(canvas, startX, startY, bendX, bendY, 0.8F, color);
      SkijaUi.line(canvas, bendX, bendY, endX, endY, 0.8F, color);
   }

   private static float overlap(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
      float width = Math.max(0.0F, Math.min(ax + aw, bx + bw) - Math.max(ax, bx));
      float height = Math.max(0.0F, Math.min(ay + ah, by + bh) - Math.max(ay, by));
      return width * height;
   }

   private static float clamp(float value, float minimum, float maximum) {
      return maximum < minimum ? minimum : Math.max(minimum, Math.min(maximum, value));
   }

   private void drawCompactButton(Canvas canvas, UiControls.Box box, String label, boolean enabled, boolean primary, float visibility) {
      boolean hovered = enabled && box.contains((double)this.mouseX, (double)this.mouseY);
      int fill = !enabled ? 301989888 : (primary ? UiTheme.withAlpha(UiTheme.accent(), hovered ? 220 : 170) : (hovered ? 754974719 : 352321535));
      int border = primary && enabled ? UiTheme.withAlpha(UiTheme.accent(), 180) : 620756991;
      int text = !enabled ? UiTheme.TEXT_FAINT : (primary ? UiControls.onAccent(UiTheme.accent()) : UiTheme.TEXT_MUTED);
      SkijaUi.rounded(canvas, box.x(), box.y(), box.width(), box.height(), 5.0F, withAlpha(border, visibility));
      SkijaUi.rounded(canvas, box.x() + 1.0F, box.y() + 1.0F, box.width() - 2.0F, box.height() - 2.0F, 4.0F, withAlpha(fill, visibility));
      UiControls.centeredText(canvas, label, box, withAlpha(text, visibility), true);
   }

   private void drawLogo(Canvas canvas, float x, float y, float size, float visibility) {
      try {
         try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(LOGO_TEXTURE)) {
            if (borrowed != null) {
               canvas.saveLayerAlpha(Rect.makeXYWH(x, y, size, size), Math.max(0, Math.min(255, Math.round(255.0F * visibility))));
               canvas.drawImageRect(borrowed.image(), Rect.makeXYWH(0.0F, 0.0F, (float)borrowed.image().getWidth(), (float)borrowed.image().getHeight()), Rect.makeXYWH(x, y, size, size));
               canvas.restore();
               return;
            }
         }

      } catch (Throwable var11) {
      }
   }

   private static void drawFourTiles(Canvas canvas, float centerX, float centerY, int color) {
      float size = 4.0F;
      float gap = 2.0F;
      float x = centerX - size - gap * 0.5F;
      float y = centerY - size - gap * 0.5F;
      SkijaUi.rounded(canvas, x, y, size, size, 1.0F, color);
      SkijaUi.rounded(canvas, x + size + gap, y, size, size, 1.0F, color);
      SkijaUi.rounded(canvas, x, y + size + gap, size, size, 1.0F, color);
      SkijaUi.rounded(canvas, x + size + gap, y + size + gap, size, size, 1.0F, color);
   }

   private static void drawMiniLayout(Canvas canvas, float centerX, float centerY, boolean selected, float visibility) {
      int color = withAlpha(selected ? UiTheme.accent() : UiTheme.TEXT_FAINT, visibility);
      SkijaUi.rounded(canvas, centerX - 7.0F, centerY - 5.0F, 14.0F, 10.0F, 2.0F, withAlpha(486539263, visibility));
      SkijaUi.fill(canvas, centerX - 5.0F, centerY - 3.0F, 6.0F, 2.0F, color);
      SkijaUi.fill(canvas, centerX + 2.0F, centerY - 3.0F, 3.0F, 5.0F, color);
      SkijaUi.fill(canvas, centerX - 5.0F, centerY, 5.0F, 3.0F, color);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() != 0) {
         return false;
      } else {
         Layout layout = this.layout();
         this.updateInputBounds(layout);
         if (this.name.click(event.x(), event.y(), doubleClick)) {
            return true;
         } else if (layout.back().contains(event.x(), event.y())) {
            this.onClose();
            return true;
         } else {
            HudPresets.OfficialPreset[] official = HudPresets.OfficialPreset.values();

            for(int index = 0; index < official.length; ++index) {
               if (officialRow(layout, index).contains(event.x(), event.y())) {
                  this.selectOfficial(official[index]);
                  if (doubleClick) {
                     this.applySelection();
                  }

                  return true;
               }
            }

            if (layout.save().contains(event.x(), event.y()) && !this.name.text().isBlank()) {
               this.savePersonal();
               return true;
            } else if (layout.folder().contains(event.x(), event.y())) {
               this.openFolder();
               return true;
            } else if (layout.apply().contains(event.x(), event.y()) && this.hasSelection()) {
               this.applySelection();
               return true;
            } else {
               int visibleRows = visibleRows(layout);

               for(int slot = 0; slot < visibleRows && this.firstVisible + slot < this.profiles.size(); ++slot) {
                  String profile = (String)this.profiles.get(this.firstVisible + slot);
                  UiControls.Box row = personalRow(layout, slot);
                  if (row.contains(event.x(), event.y())) {
                     if (deleteBox(row).contains(event.x(), event.y())) {
                        if (HudPresets.INSTANCE.deletePersonal(profile)) {
                           if (profile.equalsIgnoreCase(this.selectedPersonal)) {
                              this.selectOfficial(this.selectedOfficialFallback());
                           }

                           this.refreshProfiles();
                        }
                     } else {
                        this.selectPersonal(profile);
                        if (doubleClick) {
                           this.applySelection();
                        }
                     }

                     return true;
                  }
               }

               return true;
            }
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      Layout layout = this.layout();
      if (!layout.list().contains(mouseX, mouseY)) {
         return false;
      } else {
         this.firstVisible += scrollY > (double)0.0F ? -1 : (scrollY < (double)0.0F ? 1 : 0);
         this.clampScroll(layout);
         return true;
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (this.name.isFocused()) {
         if (event.isConfirmation()) {
            this.savePersonal();
            return true;
         }

         if (this.name.keyPressed(event, this.minecraft)) {
            return true;
         }
      }

      if (event.isEscape()) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   public boolean charTyped(CharacterEvent event) {
      return this.name.charTyped(event) || super.charTyped(event);
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public void removed() {
      this.restoreMenuBlur();
      super.removed();
   }

   protected int backdropColor() {
      return 0;
   }

   private void selectOfficial(HudPresets.OfficialPreset preset) {
      this.selectedOfficial = preset;
      this.selectedPersonal = "";
      this.preview = HudPresets.INSTANCE.previewOfficial(preset);
      this.previewMotion = 0.0F;
   }

   private void selectPersonal(String profile) {
      this.selectedOfficial = null;
      this.selectedPersonal = profile;
      this.name.setText(profile);
      this.preview = HudPresets.INSTANCE.previewPersonal(profile);
      this.previewMotion = 0.0F;
   }

   private void applySelection() {
      if (this.selectedOfficial != null) {
         HudPresets.INSTANCE.applyOfficial(this.selectedOfficial);
      } else if (!this.selectedPersonal.isBlank()) {
         HudPresets.INSTANCE.loadPersonal(this.selectedPersonal);
      }

   }

   private void savePersonal() {
      String savedName = HudPresets.INSTANCE.sanitizeName(this.name.text());
      if (!savedName.isBlank() && HudPresets.INSTANCE.savePersonal(savedName)) {
         this.refreshProfiles();
         this.selectPersonal(savedName);
      }
   }

   private boolean hasSelection() {
      return this.selectedOfficial != null || !this.selectedPersonal.isBlank();
   }

   private String selectedTitle() {
      if (this.selectedOfficial != null) {
         int var10000 = this.selectedOfficial.ordinal();
         return "Official " + (var10000 + 1);
      } else {
         return this.selectedPersonal.isBlank() ? "No preset selected" : this.selectedPersonal;
      }
   }

   private HudPresets.OfficialPreset selectedOfficialFallback() {
      return HudPresets.OfficialPreset.ONE;
   }

   private void openFolder() {
      try {
         Path directory = HudPresets.INSTANCE.personalDirectory();
         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.OPEN)) {
            Desktop.getDesktop().open(directory.toFile());
         } else {
            Util.getPlatform().openUri(directory.toUri().toString());
         }
      } catch (Exception error) {
         Setsuna.LOGGER.error("Failed to open personal HUD preset folder", error);
      }

   }

   private void refreshProfiles() {
      this.profiles = HudPresets.INSTANCE.personalPresets();
      this.clampScroll(this.layout());
   }

   private void updateInputBounds(Layout layout) {
      this.name.setBounds(layout.name());
   }

   private void clampScroll(Layout layout) {
      this.firstVisible = Math.max(0, Math.min(this.firstVisible, Math.max(0, this.profiles.size() - visibleRows(layout))));
   }

   private static int visibleRows(Layout layout) {
      return Math.max(1, (int)(layout.list().height() / 29.0F));
   }

   private static UiControls.Box officialRow(Layout layout, int index) {
      return new UiControls.Box(layout.presetColumn().x() + 12.0F, layout.presetColumn().y() + 29.0F + (float)index * 34.0F, layout.presetColumn().width() - 24.0F, 28.0F);
   }

   private static UiControls.Box personalRow(Layout layout, int slot) {
      return new UiControls.Box(layout.list().x(), layout.list().y() + (float)slot * 29.0F, layout.list().width(), 25.0F);
   }

   private static UiControls.Box deleteBox(UiControls.Box row) {
      return new UiControls.Box(row.x() + row.width() - 24.0F, row.y() + 3.0F, 19.0F, 19.0F);
   }

   private Layout layout() {
      float configuredScale = Math.max(0.65F, Math.min(1.25F, (float)(Integer)ClickGui.INSTANCE.guiScale.get() / 100.0F));
      float panelWidth = Math.max((float)this.width * 0.36F, Math.min((float)this.width * 0.78F, (float)this.width * 0.56F * configuredScale));
      float panelHeight = Math.max((float)this.height * 0.4F, Math.min((float)this.height * 0.84F, (float)this.height * 0.62F * configuredScale));
      panelWidth = Math.min(panelWidth, Math.max(1.0F, (float)this.width - 16.0F));
      panelHeight = Math.min(panelHeight, Math.max(1.0F, (float)this.height - 16.0F));
      float x = ((float)this.width - panelWidth) * 0.5F;
      float y = ((float)this.height - panelHeight) * 0.5F;
      UiControls.Box panel = new UiControls.Box(x, y, panelWidth, panelHeight);
      float railWidth = panelWidth < 620.0F ? 42.0F : 50.0F;
      float headerHeight = 44.0F;
      UiControls.Box rail = new UiControls.Box(x + 1.0F, y + 1.0F, railWidth, panelHeight - 2.0F);
      UiControls.Box header = new UiControls.Box(x + railWidth, y + 1.0F, panelWidth - railWidth - 1.0F, headerHeight);
      float bodyY = y + headerHeight;
      float bodyWidth = panelWidth - railWidth - 1.0F;
      float presetWidth = Math.min(272.0F, Math.max(205.0F, bodyWidth * 0.39F));
      UiControls.Box presetColumn = new UiControls.Box(x + railWidth, bodyY, presetWidth, panelHeight - headerHeight - 1.0F);
      UiControls.Box previewColumn = new UiControls.Box(presetColumn.x() + presetWidth, bodyY, bodyWidth - presetWidth, presetColumn.height());
      float controlsX = presetColumn.x() + 12.0F;
      float controlsWidth = presetColumn.width() - 24.0F;
      float nameY = presetColumn.y() + 155.0F;
      float saveWidth = Math.min(54.0F, controlsWidth * 0.26F);
      UiControls.Box nameBox = new UiControls.Box(controlsX, nameY, controlsWidth - saveWidth - 6.0F, 23.0F);
      UiControls.Box save = new UiControls.Box(nameBox.x() + nameBox.width() + 6.0F, nameY, saveWidth, 23.0F);
      UiControls.Box folder = new UiControls.Box(controlsX, nameY + 29.0F, controlsWidth, 22.0F);
      UiControls.Box list = new UiControls.Box(controlsX, nameY + 64.0F, controlsWidth, Math.max(29.0F, presetColumn.y() + presetColumn.height() - nameY - 74.0F));
      float previewPadding = 16.0F;
      float previewWidth = Math.max(100.0F, previewColumn.width() - previewPadding * 2.0F);
      float maxPreviewHeight = Math.max(80.0F, previewColumn.height() - 100.0F);
      float previewHeight = Math.min(previewWidth * 9.0F / 16.0F, maxPreviewHeight);
      previewWidth = Math.min(previewWidth, previewHeight * 16.0F / 9.0F);
      float previewX = previewColumn.x() + previewColumn.width() - previewPadding - previewWidth;
      float previewY = previewColumn.y() + previewColumn.height() - 42.0F - previewHeight;
      UiControls.Box previewBox = new UiControls.Box(previewX, previewY, previewWidth, previewHeight);
      UiControls.Box apply = new UiControls.Box(previewColumn.x() + previewColumn.width() - 72.0F, previewColumn.y() + 14.0F, 56.0F, 23.0F);
      UiControls.Box back = new UiControls.Box(rail.x() + 7.0F, rail.y() + rail.height() - 38.0F, rail.width() - 14.0F, 28.0F);
      return new Layout(panel, rail, header, presetColumn, previewColumn, nameBox, save, folder, list, previewBox, apply, back);
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

   private float frameDelta() {
      long now = System.nanoTime();
      float delta = Math.max(0.0F, Math.min(0.05F, (float)(now - this.lastFrame) / 1.0E9F));
      this.lastFrame = now;
      return delta;
   }

   private static float approach(float current, float target, float delta, float speed) {
      return current + (target - current) * Math.min(1.0F, delta * speed);
   }

   private static float easeOutCubic(float value) {
      float inverse = 1.0F - Math.max(0.0F, Math.min(1.0F, value));
      return 1.0F - inverse * inverse * inverse;
   }

   private static int withAlpha(int color, float progress) {
      int alpha = Math.round((float)(color >>> 24 & 255) * Math.max(0.0F, Math.min(1.0F, progress)));
      return alpha << 24 | color & 16777215;
   }

   private static int previewColor(String id) {
      Color theme = ClickGui.INSTANCE.accent.get();
      int variant = Math.floorMod(id == null ? 0 : id.hashCode(), 7);
      float var10000;
      switch (variant) {
         case 0 -> var10000 = 0.0F;
         case 1 -> var10000 = 0.12F;
         case 2 -> var10000 = 0.22F;
         case 3 -> var10000 = -0.1F;
         case 4 -> var10000 = -0.2F;
         case 5 -> var10000 = 0.32F;
         default -> var10000 = -0.28F;
      }

      float amount = var10000;
      int target = amount >= 0.0F ? 255 : 0;
      float blend = Math.abs(amount);
      return UiTheme.rgb(Math.round((float)theme.getRed() + (float)(target - theme.getRed()) * blend), Math.round((float)theme.getGreen() + (float)(target - theme.getGreen()) * blend), Math.round((float)theme.getBlue() + (float)(target - theme.getBlue()) * blend));
   }

   private static PreviewSize previewSize(HudPresets.PreviewItem item, float width, float height) {
      return new PreviewSize(Math.max(4.0F, item.width() * width), Math.max(3.0F, item.height() * height));
   }

   private static record PreviewSize(float width, float height) {
   }

   private static record PreviewBlock(HudPresets.PreviewItem item, float x, float y, float width, float height, int color) {
   }

   private static record LabelPlacement(PreviewBlock block, float x, float y, float width, float height, float labelWidth, float lineHeight, float fontSize) {
   }

   private static record Layout(UiControls.Box panel, UiControls.Box rail, UiControls.Box header, UiControls.Box presetColumn, UiControls.Box previewColumn, UiControls.Box name, UiControls.Box save, UiControls.Box folder, UiControls.Box list, UiControls.Box preview, UiControls.Box apply, UiControls.Box back) {
   }
}
