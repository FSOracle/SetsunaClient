package com.setsuna.ui.hud;

import com.setsuna.event.Listen;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import java.awt.Color;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public final class InventoryHUD extends HUDModuel {
   public static final InventoryHUD INSTANCE = new InventoryHUD();
   private static final int PANEL_COLOR = -771093747;
   private static final int SLOT_COLOR = 1075650597;
   private static final int SLOT_SIZE = 18;
   private static final int SLOT_GAP = 1;
   private static final int PADDING = 3;
   private static final int COLUMNS = 9;
   private static final int ROWS = 3;
   private static final int BASE_WIDTH = 176;
   private static final int BASE_HEIGHT = 62;
   public final DoubleSetting scale = (DoubleSetting)this.add(new DoubleSetting("Scale", (double)1.0F, 0.7, 1.8, 0.05));
   public final BooleanSetting showCount = (BooleanSetting)this.add(new BooleanSetting("Show Count", true));
   public final BooleanSetting blur = (BooleanSetting)this.add(new BooleanSetting("Blur", false));
   public final IntSetting blurStrength = (IntSetting)this.add((IntSetting)(new IntSetting("Blur Strength", 6, 1, 16, 1)).visibleWhen(() -> (Boolean)this.blur.get()));
   public final BooleanSetting border = (BooleanSetting)this.add(new BooleanSetting("Border", true));
   public final DoubleSetting borderWidth = (DoubleSetting)this.add((DoubleSetting)(new DoubleSetting("Border Width", 1.2, 0.5, 4.0, 0.1)).visibleWhen(() -> (Boolean)this.border.get()));
   public final DoubleSetting borderRadius = (DoubleSetting)this.add((DoubleSetting)(new DoubleSetting("Border Radius", (double)4.0F, (double)0.0F, (double)14.0F, (double)0.5F)).visibleWhen(() -> (Boolean)this.border.get()));
   public final EnumSetting<HudRenderUtil.BorderMode> borderMode = (EnumSetting)this.add((EnumSetting)(new EnumSetting("Border Mode", HudRenderUtil.BorderMode.Single)).visibleWhen(() -> (Boolean)this.border.get()));
   public final ColorSetting borderColor = (ColorSetting)this.add((ColorSetting)(new ColorSetting("Border Color", new Color(UiTheme.accent(), true), false)).visibleWhen(() -> (Boolean)this.border.get() && this.borderMode.is(HudRenderUtil.BorderMode.Single)));
   public final ColorSetting borderStart = (ColorSetting)this.add((ColorSetting)(new ColorSetting("Border Start", new Color(64, 224, 255), false)).visibleWhen(() -> (Boolean)this.border.get() && this.borderMode.is(HudRenderUtil.BorderMode.Gradient)));
   public final ColorSetting borderEnd = (ColorSetting)this.add((ColorSetting)(new ColorSetting("Border End", new Color(130, 92, 255), false)).visibleWhen(() -> (Boolean)this.border.get() && this.borderMode.is(HudRenderUtil.BorderMode.Gradient)));

   private InventoryHUD() {
      super("Inventory HUD", 410, 810, 176.0F, 62.0F);
   }

   @Listen
   private void onVanillaHud(VanillaHudRenderEvent event) {
      if (!this.noPlayer()) {
         float panelScale = ((Double)this.scale.get()).floatValue();
         float width = 176.0F * panelScale;
         float height = 62.0F * panelScale;
         float x = (float)Math.round(this.renderX(event.width(), width));
         float y = (float)Math.round(this.renderY(event.height(), height));
         this.updateBounds(width, height);
         float radius = (Boolean)this.border.get()
               ? Math.min(((Double)this.borderRadius.get()).floatValue() * panelScale, height * 0.5F)
               : 0.0F;
         this.drawPanel(x, y, width, height, radius);
         GuiGraphicsExtractor graphics = event.graphics();
         graphics.pose().pushMatrix();
         graphics.pose().translate(x, y);
         graphics.pose().scale(panelScale, panelScale);
         for(int row = 0; row < 3; ++row) {
            for(int column = 0; column < 9; ++column) {
               int slotX = 3 + column * 19;
               int slotY = 3 + row * 19;
               ItemStack stack = this.mc.player.getInventory().getItem(9 + row * 9 + column);
               if (!stack.isEmpty()) {
                  int itemX = slotX + 1;
                  int itemY = slotY + 1;
                  graphics.item(stack, itemX, itemY);
                  graphics.itemDecorations(this.mc.font, stack, itemX, itemY, (Boolean)this.showCount.get() ? null : "");
               }
            }
         }

         graphics.pose().popMatrix();
      }
   }

   private void drawPanel(float x, float y, float width, float height, float radius) {
      float activeBlur = hudBlurStrength(blur.get(), blurStrength.get(), true, PANEL_COLOR);
      Consumer<Canvas> painter = (canvas) -> {
         int save = canvas.save();
         try {
            if (activeBlur > 0.01F) {
               int blurLayer = canvas.saveLayerAlpha(null, opacityAlpha());
               try {
                  HudRenderUtil.blur(canvas, x, y, width, height, radius, activeBlur);
               } finally {
                  canvas.restoreToCount(blurLayer);
               }
            }
            HudRenderUtil.coloredSurface(canvas, x, y, width, height, radius,
                  this.applyOpacity(hudSurfaceColor(PANEL_COLOR)), HudFusionManager.Edges.NONE);
            if ((Boolean)this.border.get()) {
               HudRenderUtil.border(canvas, x, y, width, height, radius,
                     ((Double)this.borderWidth.get()).floatValue() * ((Double)this.scale.get()).floatValue(),
                     1.0F, this.borderMode.get(), this.borderColor.argb(), this.borderStart.argb(),
                     this.borderEnd.argb(), this.opacityAlpha());
            }
         } finally {
            canvas.restoreToCount(save);
         }
      };
      if (activeBlur > 0.01F) {
         SkijaRenderer.renderMainTargetWithBackdrop(painter);
      } else {
         SkijaRenderer.renderMainTarget(painter);
      }
   }

   protected boolean usesSharedOpacityLayer() {
      return false;
   }

   public int editorColor() {
      return -8211201;
   }
}
