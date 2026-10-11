package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.ui.UiTheme;
import com.setsuna.ui.hud.TargetHud;
import com.setsuna.util.legendwatch.LegendSuffixUtil;
import com.setsuna.util.player.HealthDetectionUtils;
import com.setsuna.util.player.HeypixelHealthSource;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4d;

public final class NameTags extends Module {
   private static final Color NAME_COLOR = new Color(255, 255, 255, 235);
   private static final Color FRIEND_COLOR = new Color(20, 255, 20, 235);
   private static final Identifier TEAM_FLAGS = Identifier.fromNamespaceAndPath("setsuna", "textures/nametags/flags.png");
   private static final Identifier TEAM_FLAGS_ACCESSIBLE = Identifier.fromNamespaceAndPath("setsuna", "textures/nametags/flags_accessibility.png");
   private static final Identifier TEAM_FLAG_GENERIC = Identifier.fromNamespaceAndPath("setsuna", "textures/nametags/flag_generic.png");
   private static final char[] TAB_FLAG_CHARS = new char[]{'\ue237', '\ue238', '\ue239', '\ue23a', '\ue23b', '\ue23c', '\ue23d', '\ue23e', '\ue23f', '\ue240', '\ue241', '\ue342', '\ue38d'};
   private static final char[] TAB_ACCESSIBLE_FLAG_CHARS = new char[]{'\ue391', '\ue392', '\ue393', '\ue394', '\ue395', '\ue396', '\ue397', '\ue398', '\ue399', '\ue39a', '\ue39b', '\ue39c', '\ue39d'};
   private static final char TAB_GENERIC_FLAG_CHAR = '\ue405';
   private static final int FLAG_ROWS = 13;
   private static final float FLAG_SIZE = 10.0F;
   private static final float FLAG_GAP = 2.0F;
   private static final float TAG_HEIGHT = 11.0F;
   private static final float TAG_OFFSET = 4.0F;
   private static final int NATIVE_ITEM_SIZE = 16;
   private static final float ITEM_RENDER_SCALE = 0.82F;
   private static final float ITEM_SIZE = 13.12F;
   private static final float ITEM_GAP = 2.0F;
   private static final float ITEM_ROW_GAP = 3.0F;
   private static final float SEPARATE_ITEM_ROW_GAP = 7.0F;
   private static final float SURFACE_PADDING = 2.0F;
   private static final float SURFACE_RADIUS = 2.0F;
   private static final Paint FLAG_PAINT = (new Paint()).setAntiAlias(false);
   public static final NameTags INSTANCE = new NameTags();
   public final DoubleSetting range = (DoubleSetting)this.add(new DoubleSetting("Range", (double)64.0F, (double)4.0F, (double)128.0F, (double)1.0F));
   public final DoubleSetting scale = (DoubleSetting)this.add(new DoubleSetting("Scale", (double)1.0F, 0.3, (double)3.0F, 0.1));
   public final BooleanSetting showDistance = (BooleanSetting)this.add(new BooleanSetting("Show Distance", true));
   public final EnumSetting<TargetHud.HealthDetection> healthDetection;
   public final DoubleSetting heightOffset;
   public final BooleanSetting background;
   public final ColorSetting backgroundColor;
   public final BooleanSetting border;
   public final ColorSetting borderColor;
   public final BooleanSetting showEquipment;
   public final BooleanSetting showHeldItems;
   public final BooleanSetting continuous;
   public final BooleanSetting vanillaNameTags;
   public final BooleanSetting showSelf;
   public final BooleanSetting teamFlags;
   public final BooleanSetting accessibleFlags;
   public final BooleanSetting showMobs;
   private final List<TagDrawData> drawList;

   private NameTags() {
      super("Name Tags", Category.RENDER);
      this.healthDetection = (EnumSetting)this.add(new EnumSetting("Health Detection", TargetHud.HealthDetection.Hoplite));
      this.heightOffset = (DoubleSetting)this.add(new DoubleSetting("Height Offset", 0.15, (double)-0.5F, (double)1.0F, 0.05));
      this.background = (BooleanSetting)this.add(new BooleanSetting("Background", true));
      ColorSetting var10002 = new ColorSetting("Background Color", new Color(0, 0, 0, 130));
      BooleanSetting var10003 = this.background;
      Objects.requireNonNull(var10003);
      this.backgroundColor = (ColorSetting)this.add((ColorSetting)var10002.visibleWhen(var10003::get));
      this.border = (BooleanSetting)this.add(new BooleanSetting("Border", false));
      var10002 = new ColorSetting("Border Color", new Color(76, 178, 184, 220));
      var10003 = this.border;
      Objects.requireNonNull(var10003);
      this.borderColor = (ColorSetting)this.add((ColorSetting)var10002.visibleWhen(var10003::get));
      this.showEquipment = (BooleanSetting)this.add(new BooleanSetting("Show Equipment", false));
      this.showHeldItems = (BooleanSetting)this.add(new BooleanSetting("Show Held Items", false));
      this.continuous = (BooleanSetting)this.add((BooleanSetting)(new BooleanSetting("Continuous", true)).visibleWhen(() -> (Boolean)this.showEquipment.get() || (Boolean)this.showHeldItems.get()));
      this.vanillaNameTags = (BooleanSetting)this.add(new BooleanSetting("Vanilla Name Tags", false));
      this.showSelf = (BooleanSetting)this.add(new BooleanSetting("Show Self", false));
      this.showMobs = (BooleanSetting)this.add(new BooleanSetting("Show Mobs", false));
      this.teamFlags = (BooleanSetting)this.add(new BooleanSetting("Team Flags", true));
      BooleanSetting var10001 = (BooleanSetting)this.add(new BooleanSetting("Accessible Flags", false));
      BooleanSetting var2 = this.teamFlags;
      Objects.requireNonNull(var2);
      this.accessibleFlags = (BooleanSetting)var10001.visibleWhen(var2::get);
      this.drawList = new ArrayList();
   }

   @Listen(
      priority = 100
   )
   private void onPacket(PacketEvent.Receive event) {
      if (this.mc.player != null && this.healthDetection.is(TargetHud.HealthDetection.Heypixel)) {
         Packet var3 = event.getPacket();
         if (var3 instanceof ClientboundSetHealthPacket) {
            ClientboundSetHealthPacket healthPacket = (ClientboundSetHealthPacket)var3;
            if (HeypixelHealthSource.shouldCancelLocalHealth(healthPacket.getHealth())) {
               event.cancel();
            }
         }
      }

   }

   @Listen
   private void onRender3D(Render3DEvent event) {
      if (!this.noPlayer()) {
         this.drawList.clear();
         float partialTick = this.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
         double maxDistanceSq = (Double)this.range.get() * (Double)this.range.get();
         float baseScale = ((Double)this.scale.get()).floatValue();
         double guiScale = (double)this.mc.getWindow().getGuiScale();

         for(Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity target
                  && this.shouldDisplay(target, maxDistanceSq)) {
               Vector4d projected = WorldToScreen.getEntityPositionsOn2D(target, partialTick);
               if (projected != null) {
                  float projectedHeight = (float)Math.max((double)1.0F, projected.w - projected.y);
                  float renderScale = baseScale * Mth.clamp(projectedHeight / 36.0F, 0.55F, 2.2F);
                  Vec3 current = WorldToScreen.interpolate(target, partialTick);
                  Vector3f head = WorldToScreen.getWorldPositionToScreen(current.add((double)0.0F, (Double)this.heightOffset.get() + (double)target.getEyeHeight(), (double)0.0F));
                  if (!(head.z > 1.0F) && !(head.z < 0.0F)) {
                     String username = target.getName().getString();
                     String name = username + LegendSuffixUtil.getPlainSuffix(username);
                     boolean playerTarget = target instanceof Player;
                     float health = this.getDisplayHealth(target);
                     String healthText = String.format(Locale.ROOT, "%.1f", health);
                     String distanceText = (Boolean)this.showDistance.get()
                           ? String.format(Locale.ROOT, "%.1fm", this.mc.player.distanceTo(target))
                           : "";
                     int healthColor = (health < 10.0F ? new Color(255, 214, 64, 240) : new Color(120, 255, 120, 240)).getRGB();
                     boolean friend = playerTarget && FriendManager.INSTANCE.isFriend(username);
                     int nameColor = playerTarget
                           ? TeamColorUtils.getNameColor((Player)target, friend ? FRIEND_COLOR : NAME_COLOR).getRGB()
                           : NAME_COLOR.getRGB();
                     TabFlag tabFlag = playerTarget ? this.getTabFlag((Player)target) : null;
                     this.drawList.add(new TagDrawData(name, nameColor,
                           healthText, healthColor, distanceText, tabFlag, this.buildEquipment(target),
                           (float)((double)head.x / guiScale), (float)((double)head.y / guiScale), renderScale));
                  }
               }
            }
         }

      }
   }

   @Listen
   private void onRender2D(Render2DEvent event) {
      if (!this.drawList.isEmpty()) {
         Canvas canvas = event.canvas();

         for(TagDrawData d : this.drawList) {
            canvas.save();
            canvas.translate(d.centerX(), d.topY());
            canvas.scale(d.scale(), d.scale());
            float pad = 3.0F;
            float gap = 4.0F;
            float boxHeight = 11.0F;
            float nameWidth = SkijaUi.textWidth(d.name());
            float healthWidth = SkijaUi.textWidth(d.health());
            float distanceWidth = d.distance().isEmpty() ? 0.0F : SkijaUi.textWidth(d.distance());
            float flagWidth = d.flag() == null ? 0.0F : 12.0F;
            float contentWidth = flagWidth + nameWidth + gap + healthWidth
                  + (d.distance().isEmpty() ? 0.0F : gap + distanceWidth);
            float boxWidth = contentWidth + pad * 2.0F;
            float left = -boxWidth / 2.0F;
            float top = -boxHeight - 4.0F;
            float contentX = left + pad;
            int itemCount = d.equipment().size();
            float itemRowWidth = (float)itemCount * 13.12F + (float)Math.max(0, itemCount - 1) * 2.0F;
            float itemY = top - this.itemRowGap() - 13.12F;
            if (itemCount > 0 && (Boolean)this.continuous.get()) {
               float outerWidth = Math.max(boxWidth, itemRowWidth + 4.0F);
               float outerLeft = -outerWidth * 0.5F;
               float outerTop = itemY - 2.0F;
               this.drawSurface(canvas, outerLeft, outerTop, outerWidth, top + boxHeight - outerTop);
            } else {
               this.drawSurface(canvas, left, top, boxWidth, boxHeight);
               if (itemCount > 0) {
                  this.drawSurface(canvas, -itemRowWidth * 0.5F - 2.0F, itemY - 2.0F, itemRowWidth + 4.0F, 17.119999F);
               }
            }

            if (d.flag() != null) {
               this.drawFlag(canvas, d.flag(), contentX, top + (boxHeight - 10.0F) * 0.5F, 10.0F);
               contentX += 12.0F;
            }

            SkijaUi.text(canvas, d.name(), contentX, top, boxHeight, d.nameColor());
            SkijaUi.text(canvas, d.health(), contentX + nameWidth + gap, top, boxHeight, d.healthColor());
            if (!d.distance().isEmpty()) {
               SkijaUi.text(canvas, d.distance(), contentX + nameWidth + gap + healthWidth + gap,
                     top, boxHeight, UiTheme.withAlpha(UiTheme.TEXT_MUTED, 230));
            }
            canvas.restore();
         }

      }
   }

   @Listen
   private void onVanillaHud(VanillaHudRenderEvent event) {
      if (!this.drawList.isEmpty()) {
         GuiGraphicsExtractor graphics = event.graphics();

         for(TagDrawData data : this.drawList) {
            if (!data.equipment().isEmpty()) {
               float rowWidth = (float)data.equipment().size() * 13.12F + (float)Math.max(0, data.equipment().size() - 1) * 2.0F;
               float itemX = -rowWidth * 0.5F;
               float itemY = -15.0F - this.itemRowGap() - 13.12F;
               graphics.pose().pushMatrix();
               graphics.pose().translate(data.centerX(), data.topY());
               graphics.pose().scale(data.scale(), data.scale());

               for(ItemStack stack : data.equipment()) {
                  graphics.pose().pushMatrix();
                  graphics.pose().translate(itemX, itemY);
                  graphics.pose().scale(0.82F, 0.82F);
                  graphics.item(stack, 0, 0);
                  graphics.itemDecorations(this.mc.font, stack, 0, 0);
                  graphics.pose().popMatrix();
                  itemX += 15.12F;
               }

               graphics.pose().popMatrix();
            }
         }

      }
   }

   private float itemRowGap() {
      return (Boolean)this.continuous.get() ? 3.0F : 7.0F;
   }

   private void drawSurface(Canvas canvas, float x, float y, float width, float height) {
      float radius = Math.min(2.0F, Math.min(width, height) * 0.5F);
      if ((Boolean)this.background.get()) {
         SkijaUi.rounded(canvas, x, y, width, height, radius, this.backgroundColor.argb());
      }

      if ((Boolean)this.border.get()) {
         SkijaUi.outline(canvas, x, y, width, height, radius, 1.0F, this.borderColor.argb());
      }

   }

   private boolean shouldDisplay(LivingEntity target, double maxDistanceSq) {
      if (!target.isAlive() || target.isSpectator()
            || this.mc.player.distanceToSqr(target) > maxDistanceSq) {
         return false;
      }
      if (target instanceof Player player) {
         return (!this.mc.options.getCameraType().isFirstPerson() || player != this.mc.player)
               && (player != this.mc.player || (Boolean)this.showSelf.get());
      }
      return (Boolean)this.showMobs.get()
            && (target instanceof Monster || target instanceof Animal || target instanceof LivingEntity);
   }

   private List<ItemStack> buildEquipment(LivingEntity player) {
      if (!(Boolean)this.showEquipment.get() && !(Boolean)this.showHeldItems.get()) {
         return List.of();
      } else {
         List<ItemStack> items = new ArrayList();
         if ((Boolean)this.showHeldItems.get()) {
            appendItem(items, player.getOffhandItem());
         }

         if ((Boolean)this.showEquipment.get()) {
            appendItem(items, player.getItemBySlot(EquipmentSlot.HEAD));
            appendItem(items, player.getItemBySlot(EquipmentSlot.CHEST));
            appendItem(items, player.getItemBySlot(EquipmentSlot.LEGS));
            appendItem(items, player.getItemBySlot(EquipmentSlot.FEET));
         }

         if ((Boolean)this.showHeldItems.get()) {
            appendItem(items, player.getMainHandItem());
         }

         return List.copyOf(items);
      }
   }

   private static void appendItem(List<ItemStack> items, ItemStack stack) {
      if (!stack.isEmpty()) {
         items.add(stack.copy());
      }

   }

   private float getDisplayHealth(LivingEntity player) {
      return HealthDetectionUtils.getHealth(player, (TargetHud.HealthDetection)this.healthDetection.get());
   }

   private TabFlag getTabFlag(Player player) {
      if ((Boolean)this.teamFlags.get() && this.mc.getConnection() != null) {
         TabFlag flag = this.getTabFlag(this.mc.getConnection().getPlayerInfo(player.getUUID()));
         if (flag != null) {
            return flag;
         } else {
            flag = this.getTabFlag(this.mc.getConnection().getPlayerInfoIgnoreCase(player.getGameProfile().name()));
            return flag != null ? flag : this.getTabFlag(player.getDisplayName());
         }
      } else {
         return null;
      }
   }

   private TabFlag getTabFlag(PlayerInfo info) {
      if (info == null) {
         return null;
      } else {
         TabFlag flag = this.getTabFlag(info.getTabListDisplayName());
         return flag != null ? flag : this.getTabFlag((Component)Component.literal(info.getProfile().name()));
      }
   }

   private TabFlag getTabFlag(Component component) {
      if (component == null) {
         return null;
      } else {
         String text = component.getString();

         for(int i = 0; i < TAB_FLAG_CHARS.length; ++i) {
            if (text.indexOf(TAB_FLAG_CHARS[i]) >= 0) {
               return new TabFlag((Boolean)this.accessibleFlags.get() ? TEAM_FLAGS_ACCESSIBLE : TEAM_FLAGS, i);
            }
         }

         for(int i = 0; i < TAB_ACCESSIBLE_FLAG_CHARS.length; ++i) {
            if (text.indexOf(TAB_ACCESSIBLE_FLAG_CHARS[i]) >= 0) {
               return new TabFlag(TEAM_FLAGS_ACCESSIBLE, i);
            }
         }

         return text.indexOf(58373) >= 0 ? new TabFlag(TEAM_FLAG_GENERIC, 0) : null;
      }
   }

   private void drawFlag(Canvas canvas, TabFlag flag, float x, float y, float size) {
      try {
         try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(flag.texture())) {
            if (borrowed != null) {
               float textureWidth = (float)borrowed.image().getWidth();
               float textureHeight = (float)borrowed.image().getHeight();
               float rowHeight = flag.texture().equals(TEAM_FLAG_GENERIC) ? textureHeight : textureHeight / 13.0F;
               float sourceTop = (float)flag.index() * rowHeight;
               Rect source = Rect.makeLTRB(0.0F, sourceTop, textureWidth, sourceTop + rowHeight);
               Rect destination = Rect.makeXYWH(x, y, size, size);
               canvas.drawImageRect(borrowed.image(), source, destination, SamplingMode.DEFAULT, FLAG_PAINT, true);
               return;
            }
         }

      } catch (Throwable var15) {
      }
   }

   private static record TagDrawData(String name,
                                     int nameColor, String health, int healthColor, String distance,
                                     TabFlag flag, List<ItemStack> equipment, float centerX, float topY,
                                     float scale) {
   }

   private static record TabFlag(Identifier texture, int index) {
   }
}
