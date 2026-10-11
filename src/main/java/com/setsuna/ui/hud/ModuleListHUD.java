package com.setsuna.ui.hud;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.i18n.LocalizedText;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.FontSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.CategoryGlyphs;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.PathOp;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ModuleListHUD extends HUDModuel {
   private static final int GRAY = -5592406;
   private static final Paint BORDER_PAINT = (new Paint()).setAntiAlias(true);
   public static final ModuleListHUD INSTANCE = new ModuleListHUD();
   public final DoubleSetting scale = (DoubleSetting)this.add(new DoubleSetting("Scale", (double)1.0F, 0.65, 1.6, 0.05));
   public final FontSetting font = (FontSetting)this.add(new FontSetting("Font", "Client", ModuleListHUD::fontChoices));
   public final EnumSetting<ColorMode> colorMode;
   public final ColorSetting gradientStart;
   public final ColorSetting gradientEnd;
   public final BooleanSetting dynamicGradient;
   public final ColorSetting customColor;
   public final BooleanSetting onlyImportant;
   public final BooleanSetting moduleInfo;
   public final BooleanSetting shadow;
   public final BooleanSetting glow;
   public final IntSetting glowStrength;
   public final BooleanSetting background;
   public final ColorSetting backgroundColor;
   public final IntSetting backgroundRadius;
   public final BooleanSetting backgroundBorder;
   public final IntSetting backgroundBorderThickness;
   public final BooleanSetting backgroundShadow;
   public final BooleanSetting ico;
   public final BooleanSetting iconContinuous;
   public final BooleanSetting continuous;
   public final BooleanSetting bar;
   public final IntSetting spacing;
   private final Map<Module, Float> visibility;

   private ModuleListHUD() {
      super("Array List", 1000, 16, 132.0F, 82.0F);
      this.colorMode = (EnumSetting)this.add(new EnumSetting("Color Mode", ModuleListHUD.ColorMode.Sync));
      this.gradientStart = (ColorSetting)this.add((ColorSetting)(new ColorSetting("Start Color", new Color(114, 189, 244), false)).visibleWhen(() -> this.colorMode.is(ModuleListHUD.ColorMode.Gradient)));
      this.gradientEnd = (ColorSetting)this.add((ColorSetting)(new ColorSetting("End Color", new Color(238, 132, 176), false)).visibleWhen(() -> this.colorMode.is(ModuleListHUD.ColorMode.Gradient)));
      this.dynamicGradient = (BooleanSetting)this.add((BooleanSetting)(new BooleanSetting("Dynamic Gradient", false)).visibleWhen(() -> this.colorMode.is(ModuleListHUD.ColorMode.Gradient)));
      this.customColor = (ColorSetting)this.add((ColorSetting)(new ColorSetting("Custom Color", new Color(120, 198, 255), false)).visibleWhen(() -> this.colorMode.is(ModuleListHUD.ColorMode.Custom)));
      this.onlyImportant = (BooleanSetting)this.add(new BooleanSetting("Only Important", false));
      this.moduleInfo = (BooleanSetting)this.add(new BooleanSetting("Module Info", true));
      this.shadow = (BooleanSetting)this.add(new BooleanSetting("Shadow", false));
      this.glow = (BooleanSetting)this.add(new BooleanSetting("Glow", false));
      IntSetting var10002 = new IntSetting("Glow Strength", 8, 1, 10, 1);
      BooleanSetting var10003 = this.glow;
      Objects.requireNonNull(var10003);
      this.glowStrength = (IntSetting)this.add((IntSetting)var10002.visibleWhen(var10003::get));
      this.background = (BooleanSetting)this.add(new BooleanSetting("Background", false));
      ColorSetting var1 = new ColorSetting("Background Color", new Color(5, 9, 14, 128), true);
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundColor = (ColorSetting)this.add((ColorSetting)var1.visibleWhen(var10003::get));
      IntSetting var2 = new IntSetting("Background Radius", 3, 0, 8, 1);
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundRadius = (IntSetting)this.add((IntSetting)var2.visibleWhen(var10003::get));
      BooleanSetting var3 = new BooleanSetting("Background Border", false);
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundBorder = (BooleanSetting)this.add((BooleanSetting)var3.visibleWhen(var10003::get));
      this.backgroundBorderThickness = (IntSetting)this.add((IntSetting)(new IntSetting("Background Border Thickness", 1, 1, 4, 1)).visibleWhen(() -> (Boolean)this.background.get() && (Boolean)this.backgroundBorder.get()));
      var3 = new BooleanSetting("Background Shadow", false);
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundShadow = (BooleanSetting)this.add((BooleanSetting)var3.visibleWhen(var10003::get));
      var3 = new BooleanSetting("Ico", false);
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.ico = (BooleanSetting)this.add((BooleanSetting)var3.visibleWhen(var10003::get));
      this.iconContinuous = (BooleanSetting)this.add((BooleanSetting)(new BooleanSetting("Icon Continuous", false)).visibleWhen(() -> (Boolean)this.background.get() && (Boolean)this.ico.get()));
      var3 = new BooleanSetting("Continuous", false);
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.continuous = (BooleanSetting)this.add((BooleanSetting)var3.visibleWhen(var10003::get));
      var3 = new BooleanSetting("Bar", false);
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.bar = (BooleanSetting)this.add((BooleanSetting)var3.visibleWhen(var10003::get));
      this.spacing = (IntSetting)this.add(new IntSetting("Spacing", 0, -3, 3, 1));
      this.visibility = new IdentityHashMap();
      this.setEnabled(true);
   }

   protected void renderHud(Render2DEvent event) {
      if (!this.noPlayer() && !this.isMinecraftFont()) {
         float s = ((Double)this.scale.get()).floatValue();
         float fontSize = 10.0F * s;
         float rowHeight = fontSize * 1.35F;
         float sidePadding = 3.0F * s;
         float anchorExtension = 2.0F * s;
         float plainPadding = 4.0F * s;
         float iconDiameter = iconDiameter(rowHeight, s);
         float iconGap = this.iconGap(s);
         float iconExtra = this.iconsEnabled() ? iconDiameter + iconGap : 0.0F;
         List<Row> rows = this.collectRows();
         if (rows.isEmpty()) {
            this.updateBounds(this.defaultWidth(), this.defaultHeight());
         } else {
            float maxWidth = 0.0F;

            for(Row row : rows) {
               if (row.alpha() > 0.01F) {
                  maxWidth = Math.max(maxWidth, this.skijaWidth(row, fontSize));
               }
            }

            float blockWidth = maxWidth + ((Boolean)this.background.get() ? sidePadding * 2.0F + anchorExtension : plainPadding) + iconExtra;
            float blockX = this.renderX(event, blockWidth);
            float blockY = this.renderY(event, this.estimatedHeight(rows, rowHeight, s));
            boolean right = (Integer)this.xPosition.get() > 500;
            Canvas canvas = event.canvas();
            float offsetY = blockY;
            int index = 0;
            List<RowLayout> layouts = new ArrayList();

            for(Row row : rows) {
               float alpha = row.alpha();
               if (!(alpha <= 0.01F)) {
                  float nameWidth = this.clientTextWidth(row.name(), fontSize);
                  float rowWidth = nameWidth + (row.suffix().isBlank() ? 0.0F : this.clientTextWidth(row.suffix(), fontSize));
                  float slide = (1.0F - alpha) * (blockWidth + 10.0F * s);
                  float backgroundX = 0.0F;
                  float backgroundWidth = 0.0F;
                  float iconX = 0.0F;
                  float textX;
                  if ((Boolean)this.background.get()) {
                     backgroundWidth = rowWidth + sidePadding * 2.0F + anchorExtension;
                     backgroundX = right ? blockX + blockWidth - iconExtra - backgroundWidth + slide : blockX - slide;
                     textX = backgroundX + sidePadding + (right ? 0.0F : anchorExtension);
                     iconX = backgroundX + backgroundWidth + iconGap;
                  } else {
                     float x = right ? blockX + blockWidth - rowWidth + slide : blockX - slide;
                     textX = right ? x - 2.0F * s : x + 2.0F * s;
                  }

                  layouts.add(new RowLayout(row, textX, offsetY, rowWidth, nameWidth, index++, backgroundX, backgroundWidth, iconX, iconDiameter));
                  offsetY += (rowHeight + (float)(Integer)this.spacing.get() * s) * alpha;
               }
            }

            if ((Boolean)this.background.get()) {
               this.drawBackground(canvas, layouts, rowHeight, s, right, withAlpha(this.backgroundColor.argb(), maxAlpha(layouts)), false);
               if ((Boolean)this.bar.get()) {
                  this.drawBar(canvas, layouts, rowHeight, s, right, right ? blockX + blockWidth : blockX, rows.size(), false);
               }
            }

            if (this.iconsEnabled()) {
               this.drawCategoryIcons(canvas, layouts, rowHeight, rows.size(), false);
            }

            float renderedHeight = Math.max(rowHeight, offsetY - blockY);
            if ((Boolean)this.glow.get()) {
               SkijaUi.glowLayer(canvas, blockX, blockY, blockWidth, renderedHeight, 4.0F * s, (Integer)this.glowStrength.get(), () -> this.drawClientRows(canvas, layouts, rows.size(), rowHeight, fontSize, false));
            }

            this.drawClientRows(canvas, layouts, rows.size(), rowHeight, fontSize, true);
            this.updateBounds(blockWidth, renderedHeight);
         }
      }
   }

   @Listen
   private void onVanillaHud(VanillaHudRenderEvent event) {
      if (!this.noPlayer() && this.isMinecraftFont()) {
         float s = ((Double)this.scale.get()).floatValue();
         Objects.requireNonNull(this.mc.font);
         int lineHeight = 9;
         float rowHeight = (float)(lineHeight + (Integer)this.spacing.get());
         float sidePadding = 3.0F;
         float anchorExtension = 2.0F;
         float plainPadding = 4.0F;
         float iconDiameter = iconDiameter(rowHeight, 1.0F);
         float iconGap = this.iconGap(1.0F);
         float iconExtra = this.iconsEnabled() ? iconDiameter + iconGap : 0.0F;
         List<Row> rows = this.collectRows();
         if (rows.isEmpty()) {
            this.updateBounds(this.defaultWidth(), this.defaultHeight());
         } else {
            float maxWidth = 0.0F;

            for(Row row : rows) {
               if (row.alpha() > 0.01F) {
                  maxWidth = Math.max(maxWidth, this.vanillaWidth(row));
               }
            }

            float blockWidth = maxWidth + ((Boolean)this.background.get() ? sidePadding * 2.0F + anchorExtension : plainPadding) + iconExtra;
            float blockHeight = this.estimatedHeight(rows, rowHeight, 1.0F);
            float screenWidth = blockWidth * s;
            float screenHeight = blockHeight * s;
            float blockX = this.renderX(event.width(), screenWidth);
            float blockY = this.renderY(event.height(), screenHeight);
            boolean right = (Integer)this.xPosition.get() > 500;
            this.updateBounds(screenWidth, Math.max(rowHeight * s, screenHeight));
            float offsetY = 0.0F;
            int index = 0;
            List<RowLayout> layouts = new ArrayList();

            for(Row row : rows) {
               float alpha = row.alpha();
               if (!(alpha <= 0.01F)) {
                  int nameWidth = this.mc.font.width(row.name());
                  float rowWidth = (float)(nameWidth + (row.suffix().isBlank() ? 0 : this.mc.font.width(row.suffix())));
                  float slide = (1.0F - alpha) * (blockWidth + 10.0F);
                  float backgroundX = 0.0F;
                  float backgroundWidth = 0.0F;
                  float iconX = 0.0F;
                  float textX;
                  if ((Boolean)this.background.get()) {
                     backgroundWidth = rowWidth + sidePadding * 2.0F + anchorExtension;
                     backgroundX = right ? blockWidth - iconExtra - backgroundWidth + slide : -slide;
                     textX = backgroundX + sidePadding + (right ? 0.0F : anchorExtension);
                     iconX = backgroundX + backgroundWidth + iconGap;
                  } else {
                     float x = right ? blockWidth - rowWidth + slide : -slide;
                     textX = right ? x - 2.0F : x + 2.0F;
                  }

                  layouts.add(new RowLayout(row, textX, offsetY, rowWidth, (float)nameWidth, index++, backgroundX, backgroundWidth, iconX, iconDiameter));
                  offsetY += (rowHeight + (float)(Integer)this.spacing.get()) * alpha;
               }
            }

            if ((Boolean)this.background.get()) {
               int color = this.applyOpacity(withAlpha(this.backgroundColor.argb(), maxAlpha(layouts)));
               java.util.function.Consumer<Canvas> painter = (canvas) -> {
                  int save = canvas.save();

                  try {
                     canvas.translate(blockX, blockY);
                     canvas.scale(s, s);
                     this.drawBackground(canvas, layouts, rowHeight, 1.0F, right, color, true);
                     if ((Boolean)this.bar.get()) {
                        this.drawBar(canvas, layouts, rowHeight, 1.0F, right, right ? blockWidth : 0.0F, rows.size(), true);
                     }

                     if (this.iconsEnabled()) {
                        this.drawCategoryIcons(canvas, layouts, rowHeight, rows.size(), true);
                     }
                  } finally {
                     canvas.restoreToCount(save);
                  }

               };
               if (hudBlurStrength(false, 0.0F, true, color) > 0.01F) {
                  SkijaRenderer.renderMainTargetWithBackdrop(painter);
               } else {
                  SkijaRenderer.renderMainTarget(painter);
               }
            }

            GuiGraphicsExtractor graphics = event.graphics();
            graphics.pose().pushMatrix();
            graphics.pose().translate(blockX, blockY);
            graphics.pose().scale(s, s);

            for(RowLayout layout : layouts) {
               Row row = layout.row();
               int accent = this.applyOpacity(withAlpha(this.rowColor(layout.index(), rows.size()), row.alpha()));
               this.drawVanillaText(graphics, row.name(), layout.textX(), layout.y(), accent);
               if (!row.suffix().isBlank()) {
                  this.drawVanillaText(graphics, row.suffix(), layout.textX() + layout.nameWidth(), layout.y(), this.applyOpacity(withAlpha(-5592406, row.alpha())));
               }
            }

            graphics.pose().popMatrix();
         }
      }
   }

   private void drawBackground(Canvas canvas, List<RowLayout> layouts, float rowHeight, float unit, boolean right, int color, boolean bakeOpacity) {
      if (!layouts.isEmpty() && (color >>> 24 & 255) != 0) {
         float verticalPadding = (Boolean)this.continuous.get() ? 1.25F * unit : -0.65F * unit;
         float radius = (float)(Integer)this.backgroundRadius.get() * unit;
         PathBuilder builder = new PathBuilder();

         try {
            for(int i = 0; i < layouts.size(); ++i) {
               RowLayout layout = (RowLayout)layouts.get(i);
               float x = layout.backgroundX();
               float width = layout.backgroundWidth();
               float top = layout.y() - verticalPadding;
               float bottom = layout.y() + rowHeight + verticalPadding;
               if ((Boolean)this.continuous.get() && i + 1 < layouts.size()) {
                  bottom = Math.max(bottom, ((RowLayout)layouts.get(i + 1)).y() + verticalPadding);
               }

               float height = Math.max(1.0F, bottom - top);
               boolean mergedIcon = this.iconsEnabled() && (Boolean)this.iconContinuous.get();
               float shapeWidth = width + (mergedIcon ? layout.iconDiameter() : 0.0F);
               float rowRadius = Math.min(radius, Math.min(shapeWidth, height) * 0.5F);
               float mergedRadius = Math.min(height * 0.5F, Math.max(rowRadius, layout.iconDiameter() * 0.5F));
               float[] radii = mergedIcon ? new float[]{rowRadius, rowRadius, mergedRadius, mergedRadius, mergedRadius, mergedRadius, rowRadius, rowRadius} : (this.iconsEnabled() ? new float[]{rowRadius, rowRadius, rowRadius, rowRadius, rowRadius, rowRadius, rowRadius, rowRadius} : (right ? new float[]{rowRadius, rowRadius, 0.0F, 0.0F, 0.0F, 0.0F, rowRadius, rowRadius} : new float[]{0.0F, 0.0F, rowRadius, rowRadius, rowRadius, rowRadius, 0.0F, 0.0F}));
               builder.addRRect(RRect.makeComplexXYWH(x, top, shapeWidth, height, radii));
               if (this.iconsEnabled() && !mergedIcon) {
                  float iconY = iconY(layout, rowHeight);
                  float diameter = layout.iconDiameter();
                  builder.addRRect(RRect.makeXYWH(layout.iconX(), iconY, diameter, diameter, diameter * 0.5F));
               }
            }

            Path rawPath = builder.detach();

            try {
               Path path = (Boolean)this.continuous.get() ? Path.makeCombining(rawPath, rawPath, PathOp.UNION) : new Path(rawPath);

               try {
                  if ((Boolean)this.backgroundShadow.get()) {
                     int shadowColor = withAlpha(-1946157056, maxAlpha(layouts));
                     if (bakeOpacity) {
                        shadowColor = this.applyOpacity(shadowColor);
                     }

                     SkijaUi.dropShadowPath(canvas, path, 7.0F * unit, shadowColor);
                  }

                  float blurStrength = hudBlurStrength(false, 0.0F, true, color);
                  if (blurStrength > 0.01F) {
                     Rect bounds = path.getBounds();
                     int blurAlpha = Math.round(255.0F * maxAlpha(layouts)
                           * (bakeOpacity ? opacityFactor() : 1.0F));
                     int blurLayer = canvas.saveLayerAlpha(null, blurAlpha);
                     try {
                        SkijaRenderer.drawGaussianBackdrop(canvas, path, bounds.getLeft(), bounds.getTop(),
                              bounds.getWidth(), bounds.getHeight(), blurStrength);
                     } finally {
                        canvas.restoreToCount(blurLayer);
                     }
                  }
                  int surfaceColor = withAlpha(hudSurfaceColor(backgroundColor.argb()), maxAlpha(layouts));
                  SkijaUi.fillPath(canvas, path, bakeOpacity ? applyOpacity(surfaceColor) : surfaceColor);
                  if ((Boolean)this.backgroundBorder.get()) {
                     this.drawRowColoredBorder(canvas, path, layouts, (float)(Integer)this.backgroundBorderThickness.get() * unit, rowHeight, bakeOpacity);
                  }
               } catch (Throwable var28) {
                  if (path != null) {
                     try {
                        path.close();
                     } catch (Throwable var27) {
                        var28.addSuppressed(var27);
                     }
                  }

                  throw var28;
               }

               if (path != null) {
                  path.close();
               }
            } catch (Throwable var29) {
               if (rawPath != null) {
                  try {
                     rawPath.close();
                  } catch (Throwable var26) {
                     var29.addSuppressed(var26);
                  }
               }

               throw var29;
            }

            if (rawPath != null) {
               rawPath.close();
            }
         } catch (Throwable var30) {
            try {
               builder.close();
            } catch (Throwable var25) {
               var30.addSuppressed(var25);
            }

            throw var30;
         }

         builder.close();
      }
   }

   private void drawRowColoredBorder(Canvas canvas, Path path, List<RowLayout> layouts, float thickness, float rowHeight, boolean bakeOpacity) {
      if (!layouts.isEmpty()) {
         if (layouts.size() == 1) {
            RowLayout layout = (RowLayout)layouts.getFirst();
            int color = withAlpha(this.rowColor(layout.index(), 1), layout.row().alpha());
            if (bakeOpacity) {
               color = this.applyOpacity(color);
            }

            SkijaUi.outlinePath(canvas, path, thickness, color);
         } else {
            int[] colors = new int[layouts.size()];
            float[] positions = new float[layouts.size()];
            float firstCenter = ((RowLayout)layouts.getFirst()).y() + rowHeight * 0.5F;
            float lastCenter = ((RowLayout)layouts.getLast()).y() + rowHeight * 0.5F;
            float span = Math.max(1.0F, lastCenter - firstCenter);

            for(int index = 0; index < layouts.size(); ++index) {
               RowLayout layout = (RowLayout)layouts.get(index);
               int color = withAlpha(this.rowColor(layout.index(), layouts.size()), layout.row().alpha());
               colors[index] = bakeOpacity ? this.applyOpacity(color) : color;
               positions[index] = (layout.y() + rowHeight * 0.5F - firstCenter) / span;
            }

            try {
               Shader shader = Shader.makeLinearGradient(0.0F, firstCenter, 0.0F, lastCenter, colors, positions);

               try {
                  BORDER_PAINT.setAntiAlias(true).setMode(PaintMode.STROKE).setStrokeWidth(Math.max(0.1F, thickness)).setColor(-1).setShader(shader).setAlpha(255);
                  canvas.drawPath(path, BORDER_PAINT);
               } catch (Throwable var20) {
                  if (shader != null) {
                     try {
                        shader.close();
                     } catch (Throwable var19) {
                        var20.addSuppressed(var19);
                     }
                  }

                  throw var20;
               }

               if (shader != null) {
                  shader.close();
               }
            } finally {
               BORDER_PAINT.setShader((Shader)null).setMode(PaintMode.FILL).setStrokeWidth(1.0F).setAlpha(255);
            }

         }
      }
   }

   private void drawCategoryIcons(Canvas canvas, List<RowLayout> layouts, float rowHeight, int rowCount, boolean bakeOpacity) {
      for(RowLayout layout : layouts) {
         float size = layout.iconDiameter() * 0.64F;
         String glyph = categoryIcon(layout.row().category());
         float glyphWidth = SkijaUi.iconWidth(glyph, size, SkijaUi.IconSet.LUCIDE);
         int color = withAlpha(this.rowColor(layout.index(), rowCount), layout.row().alpha());
         if (bakeOpacity) {
            color = this.applyOpacity(color);
         }

         SkijaUi.icon(canvas, glyph, layout.iconX() + (layout.iconDiameter() - glyphWidth) * 0.5F, iconY(layout, rowHeight), layout.iconDiameter(), color, size, SkijaUi.IconSet.LUCIDE);
      }

   }

   private boolean iconsEnabled() {
      return (Boolean)this.background.get() && (Boolean)this.ico.get();
   }

   private float iconGap(float unit) {
      return (Boolean)this.iconContinuous.get() ? 0.0F : 2.5F * unit;
   }

   private static float iconDiameter(float rowHeight, float unit) {
      return Math.max(6.0F * unit, rowHeight - 2.0F * unit);
   }

   private static float iconY(RowLayout layout, float rowHeight) {
      return layout.y() + (rowHeight - layout.iconDiameter()) * 0.5F;
   }

   private static String categoryIcon(Category category) {
      return CategoryGlyphs.forCategory(category);
   }

   private void drawBar(Canvas canvas, List<RowLayout> layouts, float rowHeight, float unit, boolean right, float anchorX, int rowCount, boolean bakeOpacity) {
      if (!layouts.isEmpty()) {
         float verticalPadding = 1.25F * unit;
         float barWidth = Math.max(1.0F, 1.5F * unit);
         float barX = right ? anchorX - barWidth : anchorX;

         for(int index = 0; index < layouts.size(); ++index) {
            RowLayout layout = (RowLayout)layouts.get(index);
            float top = layout.y() - verticalPadding;
            float bottom = layout.y() + rowHeight + verticalPadding;
            if (index + 1 < layouts.size()) {
               bottom = Math.max(bottom, ((RowLayout)layouts.get(index + 1)).y() + verticalPadding);
            }

            int color = withAlpha(this.rowColor(layout.index(), rowCount), layout.row().alpha());
            if (bakeOpacity) {
               color = this.applyOpacity(color);
            }

            SkijaUi.fill(canvas, barX, top, barWidth, Math.max(1.0F, bottom - top), color);
         }

      }
   }

   private static float maxAlpha(List<RowLayout> layouts) {
      float alpha = 0.0F;

      for(RowLayout layout : layouts) {
         alpha = Math.max(alpha, layout.row().alpha());
      }

      return alpha;
   }

   private void drawClientText(Canvas canvas, String text, float x, float y, float height, int color, float fontSize) {
      if ((Boolean)this.shadow.get()) {
         SkijaUi.textShadowWithFallback(canvas, text, x, y, height, color, fontSize, this.clientFontOverride());
      } else {
         SkijaUi.textWithFallback(canvas, text, x, y, height, color, fontSize, this.clientFontOverride());
      }

   }

   private void drawClientRows(Canvas canvas, List<RowLayout> layouts, int rowCount, float rowHeight, float fontSize, boolean includeShadow) {
      for(RowLayout layout : layouts) {
         Row row = layout.row();
         int accent = withAlpha(this.rowColor(layout.index(), rowCount), row.alpha());
         if (includeShadow) {
            this.drawClientText(canvas, row.name(), layout.textX(), layout.y(), rowHeight, accent, fontSize);
         } else {
            SkijaUi.textWithFallback(canvas, row.name(), layout.textX(), layout.y(), rowHeight, accent, fontSize, this.clientFontOverride());
         }

         if (!row.suffix().isBlank()) {
            int suffix = withAlpha(-5592406, row.alpha());
            if (includeShadow) {
               this.drawClientText(canvas, row.suffix(), layout.textX() + layout.nameWidth(), layout.y(), rowHeight, suffix, fontSize);
            } else {
               SkijaUi.textWithFallback(canvas, row.suffix(), layout.textX() + layout.nameWidth(), layout.y(), rowHeight, suffix, fontSize, this.clientFontOverride());
            }
         }
      }

   }

   private void drawVanillaText(GuiGraphicsExtractor graphics, String text, float x, float y, int color) {
      int drawX = Math.round(x);
      int drawY = Math.round(y);
      if ((Boolean)this.glow.get()) {
         int glowLimit = 48 + Math.max(1, Math.min(10, (Integer)this.glowStrength.get())) * 20;
         int alpha = Math.min(glowLimit, color >>> 24 & 255);
         int glowColor = alpha << 24 | color & 16777215;
         graphics.text(this.mc.font, text, drawX - 1, drawY, glowColor, false);
         graphics.text(this.mc.font, text, drawX + 1, drawY, glowColor, false);
         graphics.text(this.mc.font, text, drawX, drawY - 1, glowColor, false);
         graphics.text(this.mc.font, text, drawX, drawY + 1, glowColor, false);
         graphics.text(this.mc.font, text, drawX - 1, drawY - 1, glowColor, false);
         graphics.text(this.mc.font, text, drawX + 1, drawY - 1, glowColor, false);
         graphics.text(this.mc.font, text, drawX - 1, drawY + 1, glowColor, false);
         graphics.text(this.mc.font, text, drawX + 1, drawY + 1, glowColor, false);
      }

      graphics.text(this.mc.font, text, drawX, drawY, color, (Boolean)this.shadow.get());
   }

   protected boolean usesSharedOpacityLayer() {
      return !this.isMinecraftFont();
   }

   private List<Row> collectRows() {
      List<Row> rows = new ArrayList();

      for(Module module : ModuleManager.INSTANCE.modules()) {
         if (module != this && module != HUD.INSTANCE && !module.isHidden() && !(module instanceof HUDModuel) && module.category() != Category.HUD && module.isToggleable() && (!(Boolean)this.onlyImportant.get() || module.category() != Category.RENDER)) {
            float target = module.isEnabled() ? 1.0F : 0.0F;
            float current = (Float)this.visibility.getOrDefault(module, target);
            current += (target - current) * 0.16F;
            if (Math.abs(target - current) < 0.004F) {
               current = target;
            }

            this.visibility.put(module, current);
            if (!(current <= 0.01F)) {
               String name = splitName(module.displayName());
               String suffix = (Boolean)this.moduleInfo.get() && module.getInfo() != null ? " " + LocalizedText.freeform(module.getInfo().trim()) : "";
               rows.add(new Row(name, suffix, module.category(), current));
            }
         }
      }

      this.sortRowsByWidth(rows);
      return rows;
   }

   private int rowColor(int index, int rowCount) {
      int var10000;
      switch (((ColorMode)this.colorMode.get()).ordinal()) {
         case 0 -> var10000 = HUD.INSTANCE.getColor(index);
         case 1 -> var10000 = this.gradient(index, rowCount);
         case 2 -> var10000 = rainbow(index);
         case 3 -> var10000 = this.customColor.argb();
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private int gradient(int index, int rowCount) {
      float progress = rowCount <= 1 ? 0.0F : (float)index / (float)(rowCount - 1);
      if ((Boolean)this.dynamicGradient.get()) {
         float phase = (float)(System.currentTimeMillis() % 4000L) / 4000.0F;
         progress = (progress + phase * 2.0F) % 2.0F;
         if (progress > 1.0F) {
            progress = 2.0F - progress;
         }
      }

      Color start = this.gradientStart.get();
      Color end = this.gradientEnd.get();
      int red = Math.round((float)start.getRed() + (float)(end.getRed() - start.getRed()) * progress);
      int green = Math.round((float)start.getGreen() + (float)(end.getGreen() - start.getGreen()) * progress);
      int blue = Math.round((float)start.getBlue() + (float)(end.getBlue() - start.getBlue()) * progress);
      return -16777216 | red << 16 | green << 8 | blue;
   }

   private static int rainbow(int index) {
      double state = Math.ceil((double)(System.currentTimeMillis() - (long)index * 110L)) / (double)11.0F % (double)360.0F;
      int rgb = Color.HSBtoRGB((float)(state / (double)360.0F), 1.0F, 1.0F);
      return -16777216 | rgb & 16777215;
   }

   private float estimatedHeight(List<Row> rows, float rowHeight, float scale) {
      float total = 0.0F;

      for(Row row : rows) {
         if (row.alpha() > 0.01F) {
            total += (rowHeight + (float)(Integer)this.spacing.get() * scale) * row.alpha();
         }
      }

      return Math.max(rowHeight, total);
   }

   private float skijaWidth(Row row, float fontSize) {
      float width = this.clientTextWidth(row.name(), fontSize);
      return row.suffix().isBlank() ? width : width + this.clientTextWidth(row.suffix(), fontSize);
   }

   private void sortRowsByWidth(List<Row> rows) {
      float[] widths = new float[rows.size()];

      for(int index = 0; index < rows.size(); ++index) {
         widths[index] = this.rowSortWidth((Row)rows.get(index));
      }

      for(int end = rows.size() - 1; end > 0; --end) {
         boolean swapped = false;

         for(int index = 0; index < end; ++index) {
            if (widths[index] < widths[index + 1]) {
               Row current = (Row)rows.get(index);
               rows.set(index, (Row)rows.get(index + 1));
               rows.set(index + 1, current);
               float currentWidth = widths[index];
               widths[index] = widths[index + 1];
               widths[index + 1] = currentWidth;
               swapped = true;
            }
         }

         if (!swapped) {
            return;
         }
      }

   }

   private float rowSortWidth(Row row) {
      return this.isMinecraftFont() ? this.vanillaWidth(row) : this.skijaWidth(row, 10.0F);
   }

   private boolean isMinecraftFont() {
      return "Minecraft".equalsIgnoreCase((String)this.font.get());
   }

   private String clientFontOverride() {
      return "Client".equalsIgnoreCase((String)this.font.get()) ? null : (String)this.font.get();
   }

   private float clientTextWidth(String text, float size) {
      return SkijaUi.textWidthWithFallback(text, size, this.clientFontOverride());
   }

   private static List<String> fontChoices() {
      List<String> choices = new ArrayList(SkijaUi.availableFontNames());
      choices.add(1, "Minecraft");
      return choices;
   }

   private float vanillaWidth(Row row) {
      int width = this.mc.font.width(row.name());
      return row.suffix().isBlank() ? (float)width : (float)(width + this.mc.font.width(row.suffix()));
   }

   private static String splitName(String value) {
      return value.replaceAll("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ");
   }

   private static int withAlpha(int color, float alpha) {
      int a = Math.max(0, Math.min(255, Math.round((float)(color >>> 24 & 255) * alpha)));
      return a << 24 | color & 16777215;
   }

   public int editorColor() {
      return this.rowColor(0, 2);
   }

   public static enum ColorMode {
      Sync,
      Gradient,
      Rainbow,
      Custom;
   }

   private static record Row(String name, String suffix, Category category, float alpha) {
   }

   private static record RowLayout(Row row, float textX, float y, float rowWidth, float nameWidth, int index, float backgroundX, float backgroundWidth, float iconX, float iconDiameter) {
   }
}
