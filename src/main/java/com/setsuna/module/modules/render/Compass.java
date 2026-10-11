package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.util.player.TeamColorUtils;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Point;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class Compass extends Module {
   public static final Compass INSTANCE = new Compass();
   private static final float RADIUS = 55.0F;
   private static final float ARROW_SIZE = 7.0F;
   private static final float TEXT_SIZE = 9.0F;
   private static final double MAX_DISTANCE = (double)128.0F;
   private static final int COLOR = -419430401;
   private static final int CLOSE_ENEMY_COLOR = -218154192;
   private Paint arrowPaint;
   public final DoubleSetting warningRange;

   private Compass() {
      super("Compass", Category.RENDER);
      this.warningRange = (DoubleSetting)this.add(new DoubleSetting("Warning Range", (double)5.0F, (double)1.0F, (double)10.0F, (double)1.0F));
   }

   protected void onEnable() {
      this.arrowPaint = (new Paint()).setAntiAlias(true).setColor(-419430401);
   }

   protected void onDisable() {
      if (this.arrowPaint != null) {
         this.arrowPaint.close();
         this.arrowPaint = null;
      }

   }

   @Listen
   private void onRender2D(Render2DEvent event) {
      if (!this.noPlayer() && this.arrowPaint != null) {
         float partialTick = this.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
         float centerX = event.width() / 2.0F;
         float centerY = event.height() / 2.0F;
         double selfX = Mth.lerp((double)partialTick, this.mc.player.xOld, this.mc.player.getX());
         double selfZ = Mth.lerp((double)partialTick, this.mc.player.zOld, this.mc.player.getZ());
         float yawRadians = (float)Math.toRadians((double)this.mc.player.getYRot(partialTick));
         Canvas canvas = event.canvas();

         for(Player target : this.mc.level.players()) {
            if (target != this.mc.player && target.isAlive() && !target.isSpectator()) {
               double distance = (double)this.mc.player.distanceTo(target);
               if (!(distance > (double)128.0F)) {
                  double targetX = Mth.lerp((double)partialTick, target.xOld, target.getX());
                  double targetZ = Mth.lerp((double)partialTick, target.zOld, target.getZ());
                  double angle = Math.atan2(targetZ - selfZ, targetX - selfX) - (double)yawRadians - (Math.PI / 2D);
                  float x = centerX + (float)(Math.sin(angle) * (double)55.0F);
                  float y = centerY - (float)(Math.cos(angle) * (double)55.0F);
                  boolean closeEnemy = this.isWarningPlayer(target, distance);
                  this.arrowPaint.setColor(closeEnemy ? -218154192 : -419430401);
                  this.drawArrow(canvas, x, y, (float)angle);
                  String distanceText = Math.round(distance) + "m";
                  float textWidth = SkijaUi.textWidth(distanceText, 9.0F);
                  SkijaUi.text(canvas, distanceText, x - textWidth / 2.0F, y - 7.0F - 9.0F, 9.0F, -419430401, 9.0F);
               }
            }
         }

      }
   }

   private boolean isWarningPlayer(Player target, double distance) {
      double warningDistance = (Double)this.warningRange.get();
      return distance <= warningDistance
            && !FriendManager.INSTANCE.isFriend(target)
            && !TeamColorUtils.isTeammate(this.mc.player, target)
            && !TeamColorUtils.isFriendlyColor(target);
   }

   private void drawArrow(Canvas canvas, float centerX, float centerY, float angle) {
      float cos = (float)Math.cos((double)angle);
      float sin = (float)Math.sin((double)angle);
      Point tip = rotatePoint(centerX, centerY, 0.0F, -7.0F, cos, sin);
      Point left = rotatePoint(centerX, centerY, -4.5499997F, 5.6F, cos, sin);
      Point right = rotatePoint(centerX, centerY, 4.5499997F, 5.6F, cos, sin);
      Path path = Path.makePolygon(new Point[]{tip, left, right}, true);

      try {
         canvas.drawPath(path, this.arrowPaint);
      } catch (Throwable var14) {
         if (path != null) {
            try {
               path.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }
         }

         throw var14;
      }

      if (path != null) {
         path.close();
      }

   }

   private static Point rotatePoint(float centerX, float centerY, float x, float y, float cos, float sin) {
      return new Point(centerX + x * cos - y * sin, centerY + x * sin + y * cos);
   }
}
