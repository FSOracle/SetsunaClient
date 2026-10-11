package com.setsuna.ui.hud;

import com.google.gson.JsonElement;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.module.Category;
import com.setsuna.notification.NotificationManager;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.ui.UiTheme;
import io.github.humbleui.skija.Canvas;
import java.util.List;
import java.util.Objects;

public final class Notifications extends HUDModuel {
   public static final Notifications INSTANCE = new Notifications();
   private static final float BASE_WIDTH = 184.0F;
   private static final float BASE_HEIGHT = 30.0F;
   private static final float GAP = 4.0F;
   private static final int EXIT_TIME = 220;
   public final BooleanSetting moduleState = (BooleanSetting)this.add(new BooleanSetting("Module State", true));
   public final BooleanSetting moduleActions = (BooleanSetting)this.add(new BooleanSetting("Module Actions", true));
   public final IntSetting duration = (IntSetting)this.add(new IntSetting("Display Time", 2000, 500, 5000, 100));
   public final IntSetting length = (IntSetting)this.add(new IntSetting("Length", 184, 123, 184, 1));
   public final IntSetting maxVisible = (IntSetting)this.add(new IntSetting("Max Visible", 4, 1, 6, 1));
   public final EnumSetting<Position> position;
   public final IntSetting scalePercent;
   public final EnumSetting<Mode> mode;
   public final BooleanSetting glow;
   public final IntSetting glowStrength;
   public final BooleanSetting shadow;
   public final IntSetting shadowStrength;

   private Notifications() {
      super("Notifications", Category.HUD, 500, 0, 184.0F, 30.0F);
      this.position = (EnumSetting)this.add(new EnumSetting("Position", Notifications.Position.TOP_CENTER));
      this.scalePercent = (IntSetting)this.add(new IntSetting("Scale", 100, 60, 160, 5));
      this.mode = (EnumSetting)this.add(new EnumSetting<Mode>("Mode", Notifications.Mode.SETSUNA) {
         {
            Objects.requireNonNull(Notifications.this);
         }

         public void fromJson(JsonElement json) {
            if (json != null && json.isJsonPrimitive() && "TENACITY".equalsIgnoreCase(json.getAsString())) {
               this.set(Notifications.Mode.MODERN);
            } else {
               super.fromJson(json);
            }
         }
      });
      this.glow = (BooleanSetting)this.add(new BooleanSetting("Glow", false));
      IntSetting var10002 = new IntSetting("Glow Strength", 6, 1, 10, 1);
      BooleanSetting var10003 = this.glow;
      Objects.requireNonNull(var10003);
      this.glowStrength = (IntSetting)this.add((IntSetting)var10002.visibleWhen(var10003::get));
      this.shadow = (BooleanSetting)this.add(new BooleanSetting("Shadow", false));
      var10002 = new IntSetting("Shadow Strength", 10, 2, 24, 1);
      var10003 = this.shadow;
      Objects.requireNonNull(var10003);
      this.shadowStrength = (IntSetting)this.add((IntSetting)var10002.visibleWhen(var10003::get));
      this.setEnabled(true);
   }

   protected void onDisable() {
      NotificationManager.INSTANCE.clear();
   }

   protected void renderHud(Render2DEvent event) {
      float scale = (float)(Integer)this.scalePercent.get() / 100.0F;
      float width = Math.min((float)(Integer)this.length.get() * scale, event.width() - 12.0F);
      float height = 30.0F * scale;
      if (!(width < 92.0F) && !(event.height() < height + 12.0F)) {
         int capacity = Math.max(1, (int)((event.height() - 12.0F) / (height + 4.0F * scale)));
         List<NotificationManager.Entry> entries = NotificationManager.INSTANCE.visible((long)((Integer)this.duration.get() + 220), Math.min((Integer)this.maxVisible.get(), capacity));
         this.updateBounds(width, height);
         if (!entries.isEmpty()) {
            float x = this.startX(event.width(), width);
            float startY = this.startY(event.height(), height, entries.size(), scale);
            float direction = this.stacksUpward() ? -1.0F : 1.0F;
            long now = System.currentTimeMillis();

            for(int index = 0; index < entries.size(); ++index) {
               NotificationManager.Entry entry = (NotificationManager.Entry)entries.get(index);
               float y = startY + (float)index * (height + 4.0F * scale) * direction;
               float progress = this.animationProgress(entry, now);
               if (!(progress <= 0.01F)) {
                  this.drawEntry(event, entry, x, y, width, height, scale, progress, now);
               }
            }

         }
      }
   }

   private void drawEntry(Render2DEvent event, NotificationManager.Entry entry, float x, float y, float width, float height, float scale, float progress, long now) {
      float slide = (1.0F - ease(progress)) * (width + 10.0F);
      float drawX = this.docksLeft() ? x - slide : x + slide;
      int alpha = Math.round(255.0F * progress);
      int accent = withAlpha(this.mode.is(Notifications.Mode.MODERN) ? modernColor(entry) : entry.type().color(), alpha);
      float radius = this.mode.is(Notifications.Mode.MODERN) ? 7.0F * scale : 4.0F;
      Runnable body = () -> this.drawBody(event, entry, drawX, y, width, height, scale, progress, now, alpha, accent, radius);
      if ((Boolean)this.shadow.get()) {
         int shadowAlpha = Math.round(115.0F * progress);
         SkijaUi.dropShadowRounded(event.canvas(), drawX, y, width, height, radius, 1.5F * scale, (float)(Integer)this.shadowStrength.get() * scale, withAlpha(0, shadowAlpha));
      }

      int blurLayer = event.canvas().saveLayerAlpha(null, alpha);
      try {
         drawGlobalBlur(event.canvas(), drawX, y, width, height, radius, 0xFF000000);
      } finally {
         event.canvas().restoreToCount(blurLayer);
      }

      if ((Boolean)this.glow.get()) {
         SkijaUi.glowLayer(event.canvas(), drawX, y, width, height, 4.0F * scale, (Integer)this.glowStrength.get(), body);
      }

      body.run();
   }

   private void drawBody(Render2DEvent event, NotificationManager.Entry entry, float drawX, float y, float width, float height, float scale, float progress, long now, int alpha, int accent, float radius) {
      if (this.mode.is(Notifications.Mode.MODERN)) {
         this.drawModernBody(event, entry, drawX, y, width, height, scale, progress, now, alpha, accent, radius);
      } else {
         int surface = withAlpha(UiTheme.SURFACE, Math.round(184.0F * progress));
         drawPanel(event, drawX, y, width, height, radius, surface);
         SkijaUi.fill(event.canvas(), drawX + 4.0F * scale, y + 5.0F * scale, 2.0F * scale, height - 10.0F * scale, accent);
         String var10000;
         switch (entry.type()) {
            case SUCCESS -> var10000 = "+";
            case ERROR -> var10000 = "x";
            case WARNING -> var10000 = "!";
            case INFO -> var10000 = "i";
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         String glyph = var10000;
         float glyphSize = 7.6F * scale;
         float glyphWidth = SkijaUi.boldTextWidth(glyph, glyphSize);
         SkijaUi.boldText(event.canvas(), glyph, drawX + 10.0F * scale - glyphWidth * 0.5F, y, height, accent, glyphSize);
         float textX = drawX + 17.0F * scale;
         float textWidth = width - 23.0F * scale;
         String title = HudRenderUtil.fit(entry.title(), textWidth, 7.8F * scale, true);
         String message = HudRenderUtil.fit(entry.message(), textWidth, 6.7F * scale, false);
         float textBlockY = y + (height - 22.0F * scale) * 0.5F;
         SkijaUi.boldText(event.canvas(), title, textX, textBlockY, 12.0F * scale, withAlpha(UiTheme.TEXT, alpha), 7.8F * scale);
         SkijaUi.text(event.canvas(), message, textX, textBlockY + 12.0F * scale, 10.0F * scale, withAlpha(UiTheme.TEXT_MUTED, Math.round(220.0F * progress)), 6.7F * scale);
         float remaining = 1.0F - Math.min(1.0F, (float)Math.max(0L, now - entry.visibleAt()) / (float)Math.max(1, (Integer)this.duration.get()));
         SkijaUi.fill(event.canvas(), drawX + 5.0F * scale, y + height - 2.0F * scale, (width - 10.0F * scale) * remaining, Math.max(1.0F, scale), accent);
      }
   }

   private void drawModernBody(Render2DEvent event, NotificationManager.Entry entry, float drawX, float y, float width, float height, float scale, float progress, long now, int alpha, int accent, float radius) {
      int tone = withAlpha(modernColor(entry), alpha);
      int surface = tintSurface(modernColor(entry), Math.round(238.0F * progress));
      drawPanel(event, drawX, y, width, height, radius, surface);
      float remaining = 1.0F - Math.min(1.0F, (float)Math.max(0L, now - entry.visibleAt()) / (float)Math.max(1, (Integer)this.duration.get()));
      SkijaUi.rounded(event.canvas(), drawX, y, width, height, radius, withAlpha(tone, Math.round(34.0F * progress * remaining)));
      SkijaUi.outline(event.canvas(), drawX, y, width, height, radius, Math.max(0.7F, scale * 0.8F), tone);
      float iconProgress = ease(Math.min(1.0F, (float)Math.max(0L, now - entry.visibleAt()) / 180.0F));
      drawModernIcon(event.canvas(), drawX + 13.0F * scale, y + height * 0.5F, 9.0F * scale, tone, entry, iconProgress);
      String message = entry.message();
      String content = !message.equalsIgnoreCase("Enabled") && !message.equalsIgnoreCase("Disabled") ? entry.title() + " " + message : entry.title() + " toggled";
      float textWidth = width - 32.0F * scale;
      content = HudRenderUtil.fit(content, textWidth, 8.6F * scale, true);
      float textX = drawX + 25.0F * scale;
      float textY = y + (height - 11.0F * scale) * 0.5F;
      SkijaUi.boldText(event.canvas(), content, textX, textY, 11.0F * scale, withAlpha(UiTheme.TEXT, alpha), 8.6F * scale);
   }

   private static int modernColor(NotificationManager.Entry entry) {
      if (isEnabled(entry)) {
         return UiTheme.SUCCESS;
      } else {
         return isDisabled(entry) ? UiTheme.DANGER : UiTheme.INFO;
      }
   }

   private static boolean isEnabled(NotificationManager.Entry entry) {
      return entry.message().equalsIgnoreCase("Enabled");
   }

   private static boolean isDisabled(NotificationManager.Entry entry) {
      return entry.message().equalsIgnoreCase("Disabled");
   }

   private static void drawModernIcon(Canvas canvas, float centerX, float centerY, float size, int color, NotificationManager.Entry entry, float progress) {
      float half = size * 0.5F;
      if (isEnabled(entry)) {
         float first = Math.min(1.0F, progress * 2.0F);
         float second = Math.max(0.0F, Math.min(1.0F, progress * 2.0F - 1.0F));
         SkijaUi.line(canvas, centerX - half * 0.82F, centerY, centerX - half * (0.82F - 0.64F * first), centerY + half * 0.58F * first, Math.max(1.2F, size * 0.18F), color);
         SkijaUi.line(canvas, centerX - half * 0.18F, centerY + half * 0.58F, centerX - half * 0.18F + half * 1.08F * second, centerY + half * 0.58F - half * 1.26F * second, Math.max(1.2F, size * 0.18F), color);
      } else if (isDisabled(entry)) {
         float first = Math.min(1.0F, progress * 2.0F);
         float second = Math.max(0.0F, Math.min(1.0F, progress * 2.0F - 1.0F));
         SkijaUi.line(canvas, centerX - half * 0.68F, centerY - half * 0.68F, centerX - half * 0.68F + half * 1.36F * first, centerY - half * 0.68F + half * 1.36F * first, Math.max(1.2F, size * 0.18F), color);
         SkijaUi.line(canvas, centerX + half * 0.68F, centerY - half * 0.68F, centerX + half * 0.68F - half * 1.36F * second, centerY - half * 0.68F + half * 1.36F * second, Math.max(1.2F, size * 0.18F), color);
      } else {
         SkijaUi.outline(canvas, centerX - half, centerY - half, size, size, half, Math.max(0.9F, size * 0.12F), color);
         SkijaUi.line(canvas, centerX, centerY - half * 0.02F, centerX, centerY + half * 0.58F, Math.max(0.9F, size * 0.12F), color);
         SkijaUi.rounded(canvas, centerX - half * 0.12F, centerY - half * 0.58F, half * 0.24F, half * 0.24F, half * 0.12F, color);
      }

   }

   private static int tintSurface(int tone, int alpha) {
      float tint = 0.2F;
      int red = Math.round((float)(tone >> 16 & 255) * tint + 8.0F * (1.0F - tint));
      int green = Math.round((float)(tone >> 8 & 255) * tint + 12.0F * (1.0F - tint));
      int blue = Math.round((float)(tone & 255) * tint + 15.0F * (1.0F - tint));
      return Math.max(0, Math.min(255, alpha)) << 24 | red << 16 | green << 8 | blue;
   }

   private static void drawPanel(Render2DEvent event, float x, float y, float width, float height, float radius, int color) {
      color = HudRenderUtil.glassColor(color);
      float safeRadius = Math.min(radius, Math.min(width, height) * 0.5F);
      int alpha = color >>> 24 & 255;
      SkijaUi.rounded(event.canvas(), x, y, width, height, safeRadius, color);
      SkijaUi.outline(event.canvas(), x, y, width, height, safeRadius, 1.0F,
            UiTheme.withAlpha(UiTheme.BORDER, Math.min(224, Math.max(104, alpha + 16))));
   }

   private float animationProgress(NotificationManager.Entry entry, long now) {
      long elapsed = Math.max(0L, now - entry.visibleAt());
      if (elapsed < 190L) {
         return (float)elapsed / 190.0F;
      } else {
         return elapsed <= (long)(Integer)this.duration.get() ? 1.0F : 1.0F - Math.min(1.0F, (float)(elapsed - (long)(Integer)this.duration.get()) / 220.0F);
      }
   }

   private float startX(float screenWidth, float width) {
      float var10000;
      switch (((Position)this.position.get()).ordinal()) {
         case 0:
            var10000 = (screenWidth - width) * 0.5F;
            break;
         case 1:
         case 3:
            var10000 = screenWidth - width - 6.0F;
            break;
         case 2:
         case 4:
            var10000 = 6.0F;
            break;
         case 5:
            var10000 = HudRenderUtil.normalizedPosition(this.xPosition, screenWidth, width);
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private float startY(float screenHeight, float height, int count, float scale) {
      float var10000;
      switch (((Position)this.position.get()).ordinal()) {
         case 0:
         case 1:
         case 2:
            var10000 = 6.0F;
            break;
         case 3:
         case 4:
            var10000 = screenHeight - height - 6.0F;
            break;
         case 5:
            float y = HudRenderUtil.normalizedPosition(this.yPosition, screenHeight, height);
            float stack = (float)(count - 1) * (height + 4.0F * scale);
            var10000 = this.stacksUpward() ? HudRenderUtil.clamp(y, 6.0F + stack, screenHeight - height - 6.0F) : HudRenderUtil.clamp(y, 6.0F, Math.max(6.0F, screenHeight - height - 6.0F - stack));
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public void useCustomPosition() {
      this.position.set(Notifications.Position.CUSTOM);
   }

   private boolean stacksUpward() {
      return this.position.is(Notifications.Position.BOTTOM_LEFT) || this.position.is(Notifications.Position.BOTTOM_RIGHT) || this.position.is(Notifications.Position.CUSTOM) && (Integer)this.yPosition.get() > 700;
   }

   private boolean docksLeft() {
      return this.position.is(Notifications.Position.TOP_LEFT) || this.position.is(Notifications.Position.BOTTOM_LEFT) || this.position.is(Notifications.Position.CUSTOM) && (Integer)this.xPosition.get() < 500;
   }

   private static float ease(float value) {
      float x = HudRenderUtil.clamp(value, 0.0F, 1.0F);
      return 1.0F - (float)Math.pow((double)(1.0F - x), (double)3.0F);
   }

   private static int withAlpha(int color, int alpha) {
      return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
   }

   public int editorColor() {
      return UiTheme.INFO;
   }

   public static enum Position {
      TOP_CENTER,
      TOP_RIGHT,
      TOP_LEFT,
      BOTTOM_RIGHT,
      BOTTOM_LEFT,
      CUSTOM;
   }

   public static enum Mode {
      SETSUNA,
      MODERN;
   }
}
