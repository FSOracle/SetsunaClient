package com.setsuna.ui.hud;

import com.setsuna.event.events.Render2DEvent;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.ui.clickgui.PopClickGuiScreen;
import com.setsuna.util.render.ColorUtils;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import java.awt.Color;
import java.util.Objects;
import net.minecraft.resources.Identifier;

public final class WatermarkHUD extends HUDModuel {
   private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("setsuna", "textures/hud/watermark_logo.png");
   private static final Paint LOGO_PAINT = (new Paint()).setAntiAlias(true);
   public static final WatermarkHUD INSTANCE = new WatermarkHUD();
   public final DoubleSetting scale = (DoubleSetting)this.add(new DoubleSetting("Scale", (double)1.0F, 0.65, 3.2, 0.05));
   public final BooleanSetting devName = (BooleanSetting)this.add(new BooleanSetting("Dev Name", false));
   public final StringSetting rename = (StringSetting)this.add(new StringSetting("Rename", ""));
   public final ColorSetting textColor = (ColorSetting)this.add(new ColorSetting("Text Color", new Color(255, 255, 255), false));
   public final BooleanSetting firstCharacterRainbow = (BooleanSetting)this.add(new BooleanSetting("First Character Rainbow", false));
   public final BooleanSetting logo = (BooleanSetting)this.add(new BooleanSetting("Logo", false));
   public final BooleanSetting logoAntiAlias;
   public final BooleanSetting background;
   public final ColorSetting backgroundColor;
   public final BooleanSetting border;
   public final DoubleSetting borderRadius;
   public final EnumSetting<HudRenderUtil.BorderMode> borderMode;
   public final ColorSetting borderColor;
   public final ColorSetting borderStart;
   public final ColorSetting borderEnd;
   public final BooleanSetting shadow;
   public final BooleanSetting glow;
   public final IntSetting glowStrength;
   private float fusionContentWidth;
   private float fusionContentHeight;

   private WatermarkHUD() {
      super("Watermark HUD", 4, 4, 90.0F, 14.0F);
      BooleanSetting var10002 = new BooleanSetting("Logo Anti-Alias", true);
      BooleanSetting var10003 = this.logo;
      Objects.requireNonNull(var10003);
      this.logoAntiAlias = (BooleanSetting)this.add((BooleanSetting)var10002.visibleWhen(var10003::get));
      this.background = (BooleanSetting)this.add(new BooleanSetting("Background", true));
      ColorSetting var1 = new ColorSetting("Background Color", new Color(12, 16, 18, 174), true);
      var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundColor = (ColorSetting)this.add((ColorSetting)var1.visibleWhen(var10003::get));
      this.border = (BooleanSetting)this.add(new BooleanSetting("Border", true));
      this.borderRadius = (DoubleSetting)this.add((DoubleSetting)(new DoubleSetting("Border Radius", (double)4.0F, (double)0.0F, (double)14.0F, (double)0.5F)).visibleWhen(() -> (Boolean)this.background.get() || (Boolean)this.border.get()));
      EnumSetting var2 = new EnumSetting("Border Mode", HudRenderUtil.BorderMode.Single);
      var10003 = this.border;
      Objects.requireNonNull(var10003);
      this.borderMode = (EnumSetting)this.add((EnumSetting)var2.visibleWhen(var10003::get));
      this.borderColor = (ColorSetting)this.add((ColorSetting)(new ColorSetting("Border Color", new Color(75, 155, 255), false)).visibleWhen(() -> (Boolean)this.border.get() && this.borderMode.is(HudRenderUtil.BorderMode.Single)));
      this.borderStart = (ColorSetting)this.add((ColorSetting)(new ColorSetting("Border Start", new Color(66, 225, 255), false)).visibleWhen(() -> (Boolean)this.border.get() && this.borderMode.is(HudRenderUtil.BorderMode.Gradient)));
      this.borderEnd = (ColorSetting)this.add((ColorSetting)(new ColorSetting("Border End", new Color(126, 92, 255), false)).visibleWhen(() -> (Boolean)this.border.get() && this.borderMode.is(HudRenderUtil.BorderMode.Gradient)));
      this.shadow = (BooleanSetting)this.add(new BooleanSetting("Shadow", false));
      this.glow = (BooleanSetting)this.add(new BooleanSetting("Glow", false));
      IntSetting var3 = new IntSetting("Glow Strength", 8, 1, 10, 1);
      var10003 = this.glow;
      Objects.requireNonNull(var10003);
      this.glowStrength = (IntSetting)this.add((IntSetting)var3.visibleWhen(var10003::get));
      this.fusionContentWidth = 90.0F;
      this.fusionContentHeight = 18.0F;
      this.setEnabled(true);
   }

   protected void renderHud(Render2DEvent event) {
      if (!this.noPlayer()) {
         float s = ((Double)this.scale.get()).floatValue();
         boolean fused = HudFusionManager.isFused(this, event);
         float font = fused ? HudFusionManager.barFontSize() : 11.0F * s;
         float textHeight = font * 1.28F;
         float padX = fused ? HudFusionManager.barPadding() : 5.0F * s;
         float padY = 2.0F * s;
         String displayText = this.displayText();
         boolean transferLogo = (Boolean)this.logo.get() && this.mc.screen instanceof PopClickGuiScreen;
         SkijaRenderer.BorrowedImage logoImage = null;
         if ((Boolean)this.logo.get() && !transferLogo) {
            try {
               logoImage = SkijaRenderer.borrowTexture(LOGO_TEXTURE);
            } catch (Throwable var36) {
            }
         }

         SkijaRenderer.BorrowedImage borrowed = logoImage;

         try {
            boolean drawLogo = borrowed != null;
            boolean hasLogo = drawLogo || transferLogo;
            float logoGap = 3.0F * s;
            float logoExtra = hasLogo ? textHeight + logoGap : 0.0F;
            float width = SkijaUi.textWidthWithFallback(displayText, font) + padX * 2.0F + logoExtra;
            float height = fused ? HudFusionManager.barHeight() : textHeight + padY * 2.0F;
            this.fusionContentWidth = width;
            this.fusionContentHeight = height;
            float x = this.renderX(event, width);
            float y = this.renderY(event, height);
            this.updateBounds(width, height);
            Rect content = HudFusionManager.contentBounds(this, event);
            float drawX = content.getLeft();
            float drawY = content.getTop();
            float drawWidth = content.getWidth();
            float drawHeight = content.getHeight();
            float textX = drawX + padX + logoExtra;
            float textY = drawY + (drawHeight - textHeight) * 0.5F;
            Rect logoBounds = Rect.makeXYWH(drawX + padX, drawY + (drawHeight - textHeight) * 0.5F, textHeight, textHeight);
            int color = this.textColor.argb();
            int rainbowColor = ColorUtils.rainbow(2600L, color >>> 24 & 255).getRGB();
            boolean rainbowFirst = (Boolean)this.firstCharacterRainbow.get();
            boolean antialiasLogo = (Boolean)this.logoAntiAlias.get();
            float radius = Math.min(((Double)this.borderRadius.get()).floatValue() * s, height * 0.5F);
            if (!fused && (Boolean)this.background.get()) {
               drawGlobalBlur(event.canvas(), x, y, width, height, radius, backgroundColor.argb());
               HudRenderUtil.coloredSurface(event.canvas(), x, y, width, height, radius, this.backgroundColor.argb(), HudFusionManager.Edges.NONE);
            }

            if (!fused && (Boolean)this.border.get()) {
               HudRenderUtil.border(event.canvas(), x, y, width, height, radius, 1.2F * s, 1.0F, (HudRenderUtil.BorderMode)this.borderMode.get(), this.borderColor.argb(), this.borderStart.argb(), this.borderEnd.argb(), 255);
            }

            Runnable drawContent = () -> {
               if (drawLogo) {
                  drawLogo(event.canvas(), borrowed.image(), logoBounds, antialiasLogo);
               }

               drawWatermarkText(event.canvas(), displayText, textX, textY, textHeight, font, color, rainbowColor, rainbowFirst, false);
            };
            if ((Boolean)this.glow.get()) {
               SkijaUi.glowLayer(event.canvas(), drawX, drawY, drawWidth, drawHeight, 4.0F * s, (Integer)this.glowStrength.get(), drawContent);
            }

            if ((Boolean)this.shadow.get()) {
               if (drawLogo) {
                  drawLogoShadow(event.canvas(), borrowed.image(), logoBounds, s, antialiasLogo);
                  drawLogo(event.canvas(), borrowed.image(), logoBounds, antialiasLogo);
               }

               drawWatermarkText(event.canvas(), displayText, textX, textY, textHeight, font, color, rainbowColor, rainbowFirst, true);
            } else {
               drawContent.run();
            }
         } catch (Throwable var37) {
            if (logoImage != null) {
               try {
                  borrowed.close();
               } catch (Throwable var35) {
                  var37.addSuppressed(var35);
               }
            }

            throw var37;
         }

         if (logoImage != null) {
            logoImage.close();
         }

      }
   }

   private static void drawWatermarkText(Canvas canvas, String text, float x, float y, float height, float fontSize, int color, int rainbowColor, boolean rainbowFirst, boolean shadow) {
      if (rainbowFirst && !text.isEmpty()) {
         int firstEnd = text.offsetByCodePoints(0, 1);
         String first = text.substring(0, firstEnd);
         String rest = text.substring(firstEnd);
         if (shadow) {
            SkijaUi.textShadowWithFallback(canvas, first, x, y, height, rainbowColor, fontSize);
         } else {
            SkijaUi.textWithFallback(canvas, first, x, y, height, rainbowColor, fontSize);
         }

         float restX = x + SkijaUi.textWidthWithFallback(first, fontSize);
         if (shadow) {
            SkijaUi.textShadowWithFallback(canvas, rest, restX, y, height, color, fontSize);
         } else {
            SkijaUi.textWithFallback(canvas, rest, restX, y, height, color, fontSize);
         }

      } else {
         if (shadow) {
            SkijaUi.textShadowWithFallback(canvas, text, x, y, height, color, fontSize);
         } else {
            SkijaUi.textWithFallback(canvas, text, x, y, height, color, fontSize);
         }

      }
   }

   private static void drawLogo(Canvas canvas, Image image, Rect bounds, boolean antiAlias) {
      Rect source = Rect.makeXYWH(0.0F, 0.0F, (float)image.getWidth(), (float)image.getHeight());
      LOGO_PAINT.setAntiAlias(antiAlias).setColor(-1).setAlpha(255);
      if (!antiAlias) {
         LOGO_PAINT.setImageFilter((ImageFilter)null);
         canvas.drawImageRect(image, source, bounds, SamplingMode.DEFAULT, LOGO_PAINT, true);
      } else {
         try {
            ImageFilter filter = ImageFilter.makeBlur(0.32F, 0.32F, FilterTileMode.DECAL);

            try {
               LOGO_PAINT.setImageFilter(filter);
               canvas.drawImageRect(image, source, bounds, SamplingMode.MITCHELL, LOGO_PAINT, true);
            } catch (Throwable var13) {
               if (filter != null) {
                  try {
                     filter.close();
                  } catch (Throwable var12) {
                     var13.addSuppressed(var12);
                  }
               }

               throw var13;
            }

            if (filter != null) {
               filter.close();
            }
         } finally {
            LOGO_PAINT.setImageFilter((ImageFilter)null).setAlpha(255);
         }

      }
   }

   private static void drawLogoShadow(Canvas canvas, Image image, Rect bounds, float scale, boolean antiAlias) {
      float offset = Math.max(0.7F, scale);
      float sigma = Math.max(0.35F, scale * 0.45F);
      Rect source = Rect.makeXYWH(0.0F, 0.0F, (float)image.getWidth(), (float)image.getHeight());

      try {
         ImageFilter filter = ImageFilter.makeDropShadowOnly(offset, offset, sigma, sigma, -1610612736);

         try {
            LOGO_PAINT.setAntiAlias(antiAlias).setImageFilter(filter).setColor(-1).setAlpha(255);
            canvas.drawImageRect(image, source, bounds, antiAlias ? SamplingMode.MITCHELL : SamplingMode.DEFAULT, LOGO_PAINT, true);
         } catch (Throwable var16) {
            if (filter != null) {
               try {
                  filter.close();
               } catch (Throwable var15) {
                  var16.addSuppressed(var15);
               }
            }

            throw var16;
         }

         if (filter != null) {
            filter.close();
         }
      } finally {
         LOGO_PAINT.setImageFilter((ImageFilter)null).setAlpha(255);
      }

   }

   public boolean supportsHudFusion() {
      return true;
   }

   boolean allowsHudFusion() {
      return !(Boolean)this.logo.get() || !this.displayText().isEmpty() || (Boolean)this.border.get() || (Boolean)this.background.get();
   }

   private String displayText() {
      String customName = ((String)this.rename.get()).trim();
      String text = customName.isEmpty() ? "" : customName + " B11";
      if ((Boolean)this.devName.get()) {
         text = text + (text.isEmpty() ? "" : " / ") + "FS_Oracle / ShiYi";
      }

      return text;
   }

   public boolean hudFusionBorderEnabled() {
      return (Boolean)this.border.get();
   }

   public boolean hudFusionBackgroundEnabled() {
      return (Boolean)this.background.get();
   }

   public HudFusionManager.FusionStyle hudFusionStyle() {
      float s = ((Double)this.scale.get()).floatValue();
      return new HudFusionManager.FusionStyle((Boolean)this.background.get(), this.backgroundColor.argb(), false, 0.0F, (Boolean)this.shadow.get(), 4.0F * s, (Boolean)this.border.get(), ((Double)this.borderRadius.get()).floatValue() * s, 1.2F * s, (HudRenderUtil.BorderMode)this.borderMode.get(), this.borderColor.argb(), this.borderStart.argb(), this.borderEnd.argb());
   }

   public float hudFusionContentWidth(float screenWidth) {
      return Math.max(4.0F, this.fusionContentWidth);
   }

   public float hudFusionContentHeight(float screenHeight) {
      return Math.min(this.hudHeight(screenHeight), Math.max(4.0F, this.fusionContentHeight));
   }

   public int editorColor() {
      return this.textColor.argb();
   }
}
