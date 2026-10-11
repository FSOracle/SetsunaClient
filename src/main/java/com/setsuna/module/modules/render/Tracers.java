package com.setsuna.module.modules.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.setsuna.event.Listen;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.util.client.ViewBobbingSuppressor;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.render.Render3DUtils;
import java.awt.Color;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Tracers extends Module {
   public static final Tracers INSTANCE = new Tracers();
   public final EnumSetting<Target> target;
   public final DoubleSetting range;
   public final DoubleSetting lineWidth;
   public final ColorSetting playerColor;
   public final ColorSetting friendColor;
   public final ColorSetting mobColor;
   public final ColorSetting animalColor;
   public final BooleanSetting rainbow;
   public final DoubleSetting warningRange;

   private Tracers() {
      super("Tracers", Category.RENDER);
      this.target = (EnumSetting)this.add(new EnumSetting("TargetHUD", Tracers.Target.PLAYERS));
      this.range = (DoubleSetting)this.add(new DoubleSetting("Range", (double)128.0F, (double)4.0F, (double)512.0F, (double)1.0F));
      this.lineWidth = (DoubleSetting)this.add(new DoubleSetting("Line Width", (double)1.5F, (double)0.5F, (double)5.0F, (double)0.5F));
      this.playerColor = (ColorSetting)this.add(new ColorSetting("Player Color", new Color(100, 200, 255, 200)));
      this.friendColor = (ColorSetting)this.add(new ColorSetting("Friend Color", new Color(80, 255, 80, 200)));
      this.mobColor = (ColorSetting)this.add(new ColorSetting("Mob Color", new Color(255, 80, 80, 200)));
      this.animalColor = (ColorSetting)this.add(new ColorSetting("Animal Color", new Color(255, 220, 80, 200)));
      this.rainbow = (BooleanSetting)this.add(new BooleanSetting("Rainbow", false));
      this.warningRange = (DoubleSetting)this.add(new DoubleSetting("Warning Range", (double)5.0F, (double)1.0F, (double)10.0F, (double)1.0F));
   }

   protected void onDisable() {
      ViewBobbingSuppressor.release("tracers");
   }

   @Listen
   private void onRender3D(Render3DEvent event) {
      if (!this.noPlayer()) {
         ViewBobbingSuppressor.acquire("tracers");
         float partialTick = this.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
         double maxDistSq = (Double)this.range.get() * (Double)this.range.get();
         Camera camera = this.mc.gameRenderer.getMainCamera();
         Vec3 cameraPos = camera.position();
         PoseStack.Pose entry = event.getPoseStack().last();
         Matrix4f matrix = entry.pose();
         float thickness = ((Double)this.lineWidth.get()).floatValue();
         BufferBuilder buffer = Tesselator.getInstance().begin(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

         for(Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity != this.mc.player && entity instanceof LivingEntity) {
               LivingEntity living = (LivingEntity)entity;
               if (living.isAlive() && !living.isDeadOrDying() && this.shouldTarget(entity) && !(this.mc.player.distanceToSqr(entity) > maxDistSq)) {
                  Color color = this.resolveColor(entity);
                  float x = (float)(Mth.lerp((double)partialTick, entity.xOld, entity.getX()) - cameraPos.x);
                  float y = (float)(Mth.lerp((double)partialTick, entity.yOld, entity.getY()) - cameraPos.y);
                  float z = (float)(Mth.lerp((double)partialTick, entity.zOld, entity.getZ()) - cameraPos.z);
                  float topY = y + entity.getBbHeight();
                  // Render3D coordinates are camera-relative, so the camera
                  // origin is the stable crosshair anchor for every tracer.
                  this.addLine(buffer, matrix, entry, 0.0F, 0.0F, 0.0F, x, y, z, color, thickness);
                  this.addLine(buffer, matrix, entry, x, y, z, x, topY, z, color, thickness);
               }
            }
         }

         MeshData meshData = buffer.build();
         if (meshData != null) {
            Render3DUtils.LINES.draw(meshData);
         }

      }
   }

   private void addLine(BufferBuilder buffer, Matrix4f matrix, PoseStack.Pose entry, float x1, float y1, float z1, float x2, float y2, float z2, Color color, float thickness) {
      Vector3f normal = (new Vector3f(x2 - x1, y2 - y1, z2 - z1)).normalize();
      buffer.addVertex(matrix, x1, y1, z1).setColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).setNormal(entry, normal.x, normal.y, normal.z).setLineWidth(thickness);
      buffer.addVertex(matrix, x2, y2, z2).setColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).setNormal(entry, normal.x, normal.y, normal.z).setLineWidth(thickness);
   }

   private boolean shouldTarget(Entity entity) {
      boolean var10000;
      switch (((Target)this.target.get()).ordinal()) {
         case 0 -> var10000 = entity instanceof Player;
         case 1 -> var10000 = entity instanceof Monster;
         case 2 -> var10000 = entity instanceof Animal;
         case 3 -> var10000 = entity instanceof LivingEntity;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private Color resolveColor(Entity entity) {
      if (this.isWarningPlayer(entity)) {
         return new Color(255, 55, 55, 235);
      }
      if ((Boolean)this.rainbow.get()) {
         float hue = (float)(System.currentTimeMillis() % 3000L) / 3000.0F;
         return Color.getHSBColor(hue, 0.9F, 1.0F);
      } else {
         if (entity instanceof Player) {
            Player player = (Player)entity;
            if (FriendManager.INSTANCE.isFriend(player)) {
               return this.friendColor.get();
            }
         }

         if (entity instanceof Monster) {
            return this.mobColor.get();
         } else {
            return entity instanceof Animal ? this.animalColor.get() : this.playerColor.get();
         }
      }
   }

   private boolean isWarningPlayer(Entity entity) {
      if (!(entity instanceof Player player) || this.mc.player == null) {
         return false;
      }
      double warningDistance = (Double)this.warningRange.get();
      return this.mc.player.distanceToSqr(player) <= warningDistance * warningDistance
            && !FriendManager.INSTANCE.isFriend(player)
            && !TeamColorUtils.isTeammate(this.mc.player, player)
            && !TeamColorUtils.isFriendlyColor(player);
   }

   public static enum Target {
      PLAYERS,
      MOBS,
      ANIMALS,
      ALL;
   }
}
