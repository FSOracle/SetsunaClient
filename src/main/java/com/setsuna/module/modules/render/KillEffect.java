package com.setsuna.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.setsuna.event.Listen;
import com.setsuna.event.events.AttackEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.event.events.WorldChangeEvent;
import com.setsuna.event.events.WorldItemSubmitEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.combat.KillAura;
import com.setsuna.module.modules.combat.KillAuraPlus;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Point;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class KillEffect extends Module {
   public static final KillEffect INSTANCE = new KillEffect();
   private static final int MAX_TRACKED_TARGETS = 32;
   private static final long ENTER_DURATION_NANOS = 220000000L;
   private static final long EXIT_DURATION_NANOS = 240000000L;
   private static final long SWORD_EFFECT_DURATION_NANOS = 1800000000L;
   private static final long SWORD_DROP_DURATION_NANOS = 420000000L;
   private static final long KILL_DEDUPE_NANOS = 3000000000L;
   private static final long SCOREBOARD_SUPPRESS_NANOS = 2500000000L;
   private static final int MAX_RECENT_KILLS = 12;
   private static final float SWORD_MODEL_SCALE = 0.775F;
   private static final float SWORD_CENTER_TO_TIP = 0.45F;
   private static final Vector3f SWORD_MODEL_AXIS = (new Vector3f(-1.0F, 1.0F, 0.0F)).normalize();
   private static final Paint PREVIEW_PAINT = (new Paint()).setAntiAlias(true);
   public final EnumSetting<Style> style;
   public final EnumSetting<Targets> targets;
   public final ColorSetting accent;
   public final DoubleSetting scale;
   public final IntSetting yOffset;
   public final DoubleSetting duration;
   public final DoubleSetting confirmWindow;
   public final DoubleSetting comboWindow;
   public final BooleanSetting visualEffects;
   public final BooleanSetting lightningEffect;
   public final BooleanSetting bloodEffect;
   public final BooleanSetting fireworkEffect;
   public final BooleanSetting swordsEffect;
   public final BooleanSetting magmaEffect;
   public final BooleanSetting readTargetHudHealth;
   public final EnumSetting<KillSound> sound;
   public final DoubleSetting soundVolume;
   public final BooleanSetting targetName;
   public final BooleanSetting distance;
   public final BooleanSetting glow;
   public final IntSetting glowStrength;
   public final ButtonSetting preview;
   private final Map<UUID, TrackedTarget> trackedTargets;
   private final List<SwordStrike> swordStrikes;
   private final Map<String, Long> recentKillNames;
   private final Map<String, Integer> killScores;
   private Paint effectPaint;
   private ItemStack diamondSwordStack;
   private long effectStartedNanos;
   private long lastKillNanos;
   private String victimName;
   private float victimDistance;
   private int comboCount;
   private int previewCombo;
   private boolean showing;

   protected KillEffect() {
      this("Kill Effect");
   }

   protected KillEffect(String displayName) {
      super(displayName, Category.RENDER); // super("Kill Effect", Category.RENDER);
      this.style = this.firesOnHit() ? null : (EnumSetting)this.add(new EnumSetting("Style", KillEffect.Style.COMBO));
      this.targets = this.firesOnHit() ? null : (EnumSetting)this.add(new EnumSetting("Targets", KillEffect.Targets.PLAYERS));
      this.accent = this.firesOnHit() ? null : (ColorSetting)this.add(new ColorSetting("Accent", new Color(87, 226, 255), false));
      this.scale = this.firesOnHit() ? null : (DoubleSetting)this.add(new DoubleSetting("Scale", (double)1.0F, (double)0.5F, (double)2.0F, 0.05));
      this.yOffset = this.firesOnHit() ? null : (IntSetting)this.add(new IntSetting("Y Offset", 34, -160, 160, 2));
      this.duration = this.firesOnHit() ? null : (DoubleSetting)this.add(new DoubleSetting("Duration", 1.8, 0.6, (double)4.0F, 0.1));
      this.confirmWindow = this.firesOnHit() ? null : (DoubleSetting)this.add(new DoubleSetting("Confirm Window", (double)6.0F, (double)1.0F, (double)12.0F, (double)0.5F));
      this.comboWindow = this.firesOnHit() ? null : (DoubleSetting)this.add(new DoubleSetting("Combo Window", (double)4.0F, (double)1.0F, (double)10.0F, (double)0.5F));
      this.visualEffects = (BooleanSetting)this.add(new BooleanSetting("Visual Effects", false));
      BooleanSetting var10002 = new BooleanSetting("Lightning", true);
      BooleanSetting var10003 = this.visualEffects;
      Objects.requireNonNull(var10003);
      this.lightningEffect = (BooleanSetting)this.add((BooleanSetting)var10002.visibleWhen(var10003::get));
      var10002 = new BooleanSetting("Blood Spray", true);
      var10003 = this.visualEffects;
      Objects.requireNonNull(var10003);
      this.bloodEffect = (BooleanSetting)this.add((BooleanSetting)var10002.visibleWhen(var10003::get));
      var10002 = new BooleanSetting("Firework Explosion", true);
      var10003 = this.visualEffects;
      Objects.requireNonNull(var10003);
      this.fireworkEffect = (BooleanSetting)this.add((BooleanSetting)var10002.visibleWhen(var10003::get));
      var10002 = new BooleanSetting("Swords", true);
      var10003 = this.visualEffects;
      Objects.requireNonNull(var10003);
      this.swordsEffect = (BooleanSetting)this.add((BooleanSetting)var10002.visibleWhen(var10003::get));
      var10002 = new BooleanSetting("Magma Splash", true);
      var10003 = this.visualEffects;
      Objects.requireNonNull(var10003);
      this.magmaEffect = (BooleanSetting)this.add((BooleanSetting)var10002.visibleWhen(var10003::get));
      this.readTargetHudHealth = this.firesOnHit() ? null : (BooleanSetting)this.add(new BooleanSetting("Read Health", false));
      this.sound = (EnumSetting)this.add(new EnumSetting("Sound", KillEffect.KillSound.LEVEL_UP));
      this.soundVolume = (DoubleSetting)this.add((DoubleSetting)(new DoubleSetting("Sound Volume", (double)0.75F, 0.1, (double)1.0F, 0.05)).visibleWhen(() -> !this.sound.is(KillEffect.KillSound.NONE)));
      this.targetName = this.firesOnHit() ? null : (BooleanSetting)this.add(new BooleanSetting("Target Name", true));
      this.distance = this.firesOnHit() ? null : (BooleanSetting)this.add(new BooleanSetting("Distance", false));
      this.glow = this.firesOnHit() ? null : (BooleanSetting)this.add(new BooleanSetting("Glow", true));
      IntSetting var4 = new IntSetting("Glow Strength", 5, 1, 10, 1);
      this.glowStrength = this.firesOnHit() ? null : (IntSetting)this.add((IntSetting)var4.visibleWhen(this.glow::get));
      this.preview = this.firesOnHit() ? null : (ButtonSetting)this.add(new ButtonSetting("Preview", this::showPreview));
      this.trackedTargets = new LinkedHashMap();
      this.swordStrikes = new ArrayList();
      this.recentKillNames = new LinkedHashMap();
      this.killScores = new LinkedHashMap();
      this.victimName = "";
   }

   protected void onEnable() {
      this.effectPaint = (new Paint()).setAntiAlias(true);
      this.clearState();
   }

   protected void onDisable() {
      this.clearState();
      if (this.effectPaint != null) {
         this.effectPaint.close();
         this.effectPaint = null;
      }

   }

   private boolean isLocalPlayerDead() {
      return this.mc.player == null
            || !this.mc.player.isAlive()
            || this.mc.player.isDeadOrDying()
            || this.mc.player.getHealth() <= 0.0F
            || this.mc.player.deathTime > 0;
   }

   public String getInfo() {
      return this.style == null ? "" : this.style.displayValue();
   }

   /** When true the effect fires on the attack itself instead of confirming a kill. */
   protected boolean firesOnHit() {
      return false;
   }

   @Listen
   private void onAttack(AttackEvent event) {
      if (!this.noPlayer() && !this.isLocalPlayerDead() && event.getAttacker() == this.mc.player) {
         Entity var3 = event.getTarget();
         if (var3 instanceof LivingEntity) {
            LivingEntity target = (LivingEntity)var3;
            if (target != this.mc.player && target.isAlive()) {
               if (!this.firesOnHit() && (this.targets == null || this.targets.is(KillEffect.Targets.PLAYERS)) && !(target instanceof Player)) {
                  return;
               }

               long now = System.nanoTime();
               TrackedTarget tracked = new TrackedTarget(target, now, displayName(target), this.mc.player.distanceTo(target));
               if (this.firesOnHit()) {
                  this.recordHitCombo(now);
                  this.showEffect(tracked.name(), tracked.distance(), now, target.getX(), target.getY(), target.getZ(), target.getBbHeight(), target.getId());
                  return;
               }
               this.trackedTargets.remove(target.getUUID());
               this.trackedTargets.put(target.getUUID(), tracked);
               this.trimTrackedTargets();
               return;
            }
         }
      }

   }

   @Listen
   private void onPacketReceive(PacketEvent.Receive event) {
      if (this.firesOnHit() || this.noPlayer() || this.isLocalPlayerDead()) {
         return;
      }

      String message = null;
      if (event.getPacket() instanceof ClientboundSystemChatPacket systemPacket) {
         if (!systemPacket.overlay()) {
            message = systemPacket.content().getString();
         }
      } else if (event.getPacket() instanceof ClientboundDisguisedChatPacket disguisedPacket) {
         message = disguisedPacket.message().getString();
      } else if (event.getPacket() instanceof ClientboundPlayerChatPacket playerPacket) {
         message = playerPacket.unsignedContent() != null
               ? playerPacket.unsignedContent().getString()
               : playerPacket.body().content();
      }

      if (message != null && !message.isBlank()) {
         this.handleChatKillMessage(message);
      }
   }

   @Listen
   private void onTick(TickEvent.Post event) {
      if (this.firesOnHit()) {
         return;
      }
      if (this.noPlayer() || this.isLocalPlayerDead()) {
         this.clearState();
      } else {
         long now = System.nanoTime();
         long confirmationNanos = secondsToNanos((Double)this.confirmWindow.get());
         Iterator<TrackedTarget> iterator = this.trackedTargets.values().iterator();

         while(iterator.hasNext()) {
            TrackedTarget tracked = (TrackedTarget)iterator.next();
            LivingEntity target = tracked.entity();
            if (target.level() == this.mc.level && now - tracked.attackedAtNanos() <= confirmationNanos) {
               boolean killed = (Boolean)this.readTargetHudHealth.get()
                     ? com.setsuna.util.player.HealthDetectionUtils.getHealth(target,
                     com.setsuna.ui.hud.TargetHud.INSTANCE.healthDetection.get()) <= 0.0F
                     : target.isDeadOrDying() || target.getHealth() <= 0.0F;
               if (killed) {
                  iterator.remove();
                  this.confirmKill(tracked, now);
               }
            } else {
               iterator.remove();
            }
         }

         this.detectScoreboardKill(now);
      }
   }

   @Listen
   private void onRespawn(RespawnEvent event) {
      this.clearState();
   }

   @Listen
   private void onWorldChange(WorldChangeEvent event) {
      this.killScores.clear();
   }

   @Listen
   private void onWorldItemSubmit(WorldItemSubmitEvent event) {
      if (!this.swordStrikes.isEmpty() && !this.noPlayer()) {
         if (this.diamondSwordStack == null) {
            this.diamondSwordStack = new ItemStack(Items.DIAMOND_SWORD);
         }

         ItemStackRenderState renderState = new ItemStackRenderState();
         this.mc.getItemModelResolver().updateForLiving(renderState, this.diamondSwordStack, ItemDisplayContext.FIXED, this.mc.player);
         long now = System.nanoTime();
         Iterator<SwordStrike> iterator = this.swordStrikes.iterator();

         while(iterator.hasNext()) {
            SwordStrike strike = (SwordStrike)iterator.next();
            long elapsed = now - strike.startedAtNanos() - strike.delayNanos();
            if (elapsed >= 0L) {
               if (elapsed >= 1800000000L) {
                  iterator.remove();
               } else {
                  this.submitSwordModel(event, renderState, strike, elapsed);
               }
            }
         }

      }
   }

   @Listen
   private void onRender2D(Render2DEvent event) {
      if (this.firesOnHit()) {
         return;
      }
      if (this.showing && this.effectPaint != null) {
         long now = System.nanoTime();
         long displayNanos = secondsToNanos((Double)this.duration.get());
         long elapsedNanos = now - this.effectStartedNanos;
         if (elapsedNanos >= 0L && elapsedNanos < displayNanos) {
            float elapsed = (float)elapsedNanos / (float)displayNanos;
            float enter = easeOutCubic(clamp((float)elapsedNanos / 2.2E8F));
            float remainingExit = clamp((float)(displayNanos - elapsedNanos) / 2.4E8F);
            float alpha = enter * smoothStep(remainingExit);
            float popScale = lerp(1.58F, 1.0F, enter) * lerp(0.94F, 1.0F, remainingExit);
            float renderScale = ((Double)this.scale.get()).floatValue() * popScale;
            float centerX = event.width() * 0.5F;
            float centerY = event.height() * 0.5F + (float)(Integer)this.yOffset.get() + (1.0F - enter) * 9.0F;
            int color = withAlpha(this.accent.argb(), alpha);
            Canvas canvas = event.canvas();
            int save = canvas.save();
            canvas.translate(centerX, centerY);
            canvas.scale(renderScale, renderScale);

            try {
               Runnable drawing = () -> this.drawStyle(canvas, color, alpha, 1.0F - elapsed, this.comboCount, this.victimName, this.victimDistance);
               if ((Boolean)this.glow.get() && alpha > 0.02F) {
                  SkijaUi.glowLayer(canvas, -112.0F, -42.0F, 224.0F, 102.0F, 8.0F, (Integer)this.glowStrength.get(), drawing);
               }

               drawing.run();
            } finally {
               canvas.restoreToCount(save);
            }

         } else {
            this.showing = false;
         }
      }
   }

   public void renderPreview(Canvas canvas, float x, float y, float width, float height) {
      if (this.firesOnHit()) {
         return;
      }
      if (canvas != null && !(width <= 1.0F) && !(height <= 1.0F)) {
         float contentWidth = this.style.is(KillEffect.Style.MINIMAL) ? 200.0F : 112.0F;
         float contentHeight = this.style.is(KillEffect.Style.MINIMAL) ? 44.0F : 72.0F;
         float fitScale = Math.min(Math.max(0.1F, width - 16.0F) / contentWidth, Math.max(0.1F, height - 16.0F) / contentHeight);
         float scaleProgress = clamp((((Double)this.scale.get()).floatValue() - 0.5F) / 1.5F);
         float previewScale = Math.max(0.12F, fitScale * lerp(0.58F, 1.0F, scaleProgress));
         int color = this.accent.argb();
         int save = canvas.save();
         canvas.translate(x + width * 0.5F, y + height * 0.46F);
         canvas.scale(previewScale, previewScale);

         try {
            Runnable drawing = () -> this.drawStyle(canvas, color, 1.0F, 0.68F, 3, "Preview Target", 12.4F);
            if ((Boolean)this.glow.get()) {
               SkijaUi.glowLayer(canvas, -112.0F, -42.0F, 224.0F, 102.0F, 8.0F, (Integer)this.glowStrength.get(), drawing);
            }

            drawing.run();
         } finally {
            canvas.restoreToCount(save);
         }

      }
   }

   private void confirmKill(TrackedTarget tracked, long now) {
      if (this.isLocalPlayerDead() || this.recentlyKilled(tracked.name(), now)) {
         return;
      }
      this.recordHitCombo(now);
      this.showEffect(tracked.name(), tracked.distance(), now, tracked.entity().getX(), tracked.entity().getY(), tracked.entity().getZ(), tracked.entity().getBbHeight(), tracked.entity().getId());
      this.recordRecentKill(tracked.name(), now);
   }

   public void triggerLocalTarget(String name, Vec3 position, float targetHeight) {
      if (this.isEnabled() && !this.noPlayer() && !this.isLocalPlayerDead() && position != null) {
         long now = System.nanoTime();
         this.recordHitCombo(now);
         float targetDistance = (float)this.mc.player.position().distanceTo(position);
         this.showEffect(name, targetDistance, now, position.x, position.y, position.z, targetHeight, -1);
      }
   }

   private void handleChatKillMessage(String rawMessage) {
      if (this.isLocalPlayerDead()) {
         return;
      }
      String message = stripFormatting(rawMessage);
      String localName = this.mc.player.getGameProfile().name();
      String localDisplay = stripFormatting(this.mc.player.getDisplayName().getString());
      if (!message.contains(localName) && (localDisplay.isEmpty() || !message.contains(localDisplay))) {
         return;
      }
      if (!containsKillWord(message)) {
         return;
      }

      String victim = this.findVictimName(message, localName, localDisplay);
      if (victim == null) {
         return;
      }

      if (this.isLocalPlayerVictim(message, localName, localDisplay, victim)) {
         return;
      }

      long now = System.nanoTime();
      if (this.recentlyKilled(victim, now)) {
         return;
      }

      Player victimPlayer = this.findPlayerByName(victim);
      float height = victimPlayer == null ? 0.0F : victimPlayer.getBbHeight();
      double x = victimPlayer == null ? this.mc.player.getX() : victimPlayer.getX();
      double y = victimPlayer == null ? this.mc.player.getY() : victimPlayer.getY();
      double z = victimPlayer == null ? this.mc.player.getZ() : victimPlayer.getZ();
      float distance = victimPlayer == null ? 0.0F : (float)this.mc.player.distanceTo(victimPlayer);
      this.recordHitCombo(now);
      this.showEffect(victim, distance, now, x, y, z, height, victimPlayer == null ? -1 : victimPlayer.getId());
      this.recordRecentKill(victim, now);
   }

   private boolean isLocalPlayerVictim(String message, String localName, String localDisplay, String victim) {
      String lower = message.toLowerCase(Locale.ROOT);
      String victimLower = victim.toLowerCase(Locale.ROOT);
      String localLower = localName.toLowerCase(Locale.ROOT);
      String displayLower = localDisplay.toLowerCase(Locale.ROOT);

      int localPos = lower.indexOf(localLower);
      if (localPos == -1 && !displayLower.isEmpty()) {
         localPos = lower.indexOf(displayLower);
      }
      int victimPos = lower.indexOf(victimLower);

      if (localPos == -1 || victimPos == -1) {
         return false;
      }

      // Check Chinese passive: "localName \u88ab victim \u51fb\u6740/\u51fb\u8d25" (被)
      int beiIndex = lower.indexOf("\u88ab");
      if (beiIndex != -1) {
         if (localPos < beiIndex && victimPos > beiIndex) {
            return true;
         }
         if (victimPos < beiIndex && localPos > beiIndex) {
            return false;
         }
      }

      // Check English passive: "localName was slain / killed by victim"
      int byIndex = lower.indexOf(" by ");
      if (byIndex != -1) {
         if (localPos < byIndex && victimPos > byIndex) {
            return true;
         }
         if (victimPos < byIndex && localPos > byIndex) {
            return false;
         }
      }

      // Active voice: "Killer killed/\u51fb\u6740 Victim"
      int killWordIndex = -1;
      String[] killWords = {"\u51fb\u6740", "\u51fb\u8d25", " killed ", " slain ", " defeated ", " eliminated ", " murdered ", " destroyed ", " obliterated "};
      for (String kw : killWords) {
         int idx = lower.indexOf(kw);
         if (idx != -1 && (killWordIndex == -1 || idx < killWordIndex)) {
            killWordIndex = idx;
         }
      }

      if (killWordIndex != -1) {
         if (victimPos < killWordIndex && localPos > killWordIndex) {
            return true;
         }
         if (localPos < killWordIndex && victimPos > killWordIndex) {
            return false;
         }
      }

      return victimPos < localPos;
   }

   private void detectScoreboardKill(long now) {
      if (this.isLocalPlayerDead() || this.mc.level == null) {
         return;
      }

      Scoreboard scoreboard = this.mc.level.getScoreboard();
      ScoreHolder holder = ScoreHolder.fromGameProfile(this.mc.player.getGameProfile());
      for (Objective objective : scoreboard.getObjectives()) {
         if (!isKillObjective(objective)) {
            continue;
         }

         ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(holder, objective);
         int score = info == null ? -1 : info.value();
         String key = objective.getName();
         Integer previous = this.killScores.get(key);
         this.killScores.put(key, score);
         if (previous == null || score <= previous || this.anyRecentKill(now, SCOREBOARD_SUPPRESS_NANOS)) {
            continue;
         }

         Player victim = this.findLikelyVictim();
         float height = victim == null ? 0.0F : victim.getBbHeight();
         double x = victim == null ? this.mc.player.getX() : victim.getX();
         double y = victim == null ? this.mc.player.getY() : victim.getY();
         double z = victim == null ? this.mc.player.getZ() : victim.getZ();
         float distance = victim == null ? 0.0F : (float)this.mc.player.distanceTo(victim);
         String victimName = victim == null ? "Scoreboard" : displayName(victim);
         this.recordHitCombo(now);
         this.showEffect(victimName, distance, now, x, y, z, height, victim == null ? -1 : victim.getId());
         this.recordRecentKill(victimName, now);
      }
   }

   private String findVictimName(String message, String localName, String localDisplay) {
      List<String> candidates = new ArrayList();
      if (this.mc.level != null) {
         for (Player player : this.mc.level.players()) {
            if (player == this.mc.player) {
               continue;
            }
            addNameCandidate(candidates, player.getGameProfile().name());
            addNameCandidate(candidates, stripFormatting(player.getDisplayName().getString()));
         }
      }

      if (this.mc.getConnection() != null) {
         for (PlayerInfo info : this.mc.getConnection().getOnlinePlayers()) {
            addNameCandidate(candidates, info.getProfile().name());
            if (info.getTabListDisplayName() != null) {
               addNameCandidate(candidates, info.getTabListDisplayName().getString());
            }
         }
      }

      candidates.sort((first, second) -> Integer.compare(second.length(), first.length()));
      for (String candidate : candidates) {
         if (candidate.length() < 2 || candidate.equalsIgnoreCase(localName) || candidate.equalsIgnoreCase(localDisplay)) {
            continue;
         }
         if (message.contains(candidate)) {
            return candidate;
         }
      }
      return null;
   }

   private static void addNameCandidate(List<String> candidates, String rawName) {
      String name = stripFormatting(rawName).trim();
      if (!name.isEmpty() && !candidates.contains(name)) {
         candidates.add(name);
      }
   }

   private Player findPlayerByName(String name) {
      if (this.mc.level == null) {
         return null;
      }
      String target = stripFormatting(name);
      for (Player player : this.mc.level.players()) {
         if (player == this.mc.player) {
            continue;
         }
         if (player.getGameProfile().name().equals(target)
               || stripFormatting(player.getDisplayName().getString()).equals(target)) {
            return player;
         }
      }
      return null;
   }

   private Player findLikelyVictim() {
      if (this.mc.level == null) {
         return null;
      }

      Player best = null;
      float bestDistance = Float.MAX_VALUE;
      for (Player player : this.mc.level.players()) {
         if (player == this.mc.player) {
            continue;
         }
         if (player.isDeadOrDying() || player.getHealth() <= 0.0F) {
            return player;
         }
         float distance = this.mc.player.distanceTo(player);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = player;
         }
      }
      return best;
   }

   private boolean recentlyKilled(String name, long now) {
      Long last = this.recentKillNames.get(stripFormatting(name).toLowerCase(Locale.ROOT));
      return last != null && now - last < KILL_DEDUPE_NANOS;
   }

   private boolean anyRecentKill(long now, long windowNanos) {
      for (Long timestamp : this.recentKillNames.values()) {
         if (now - timestamp < windowNanos) {
            return true;
         }
      }
      return false;
   }

   private void recordRecentKill(String name, long now) {
      this.recentKillNames.put(stripFormatting(name).toLowerCase(Locale.ROOT), now);
      while (this.recentKillNames.size() > MAX_RECENT_KILLS) {
         Iterator<Map.Entry<String, Long>> iterator = this.recentKillNames.entrySet().iterator();
         iterator.next();
         iterator.remove();
      }
   }

   private static boolean containsKillWord(String message) {
      String lower = message.toLowerCase(Locale.ROOT);
      String[] words = {"kill", "\u51fb\u6740", "\u51fb\u8d25", "eliminat", "slain", "destroyed", "defeated", "murdered", "obliterated"};
      for (String word : words) {
         if (lower.contains(word)) {
            return true;
         }
      }
      return false;
   }

   private static boolean isKillObjective(Objective objective) {
      String display = stripFormatting(objective.getDisplayName().getString()).toLowerCase(Locale.ROOT);
      if (display.contains("kill") || display.contains("\u51fb\u6740") || display.contains("\u51fb\u8d25")
            || display.contains("eliminat") || display.contains("slain") || display.contains("sword")) {
         return true;
      }

      for (int i = 0; i < display.length(); i++) {
         char c = display.charAt(i);
         if (c == '\u2694' || c == '\u2620' || c == '\uD83D' || (c >= '\uE000' && c <= '\uF8FF')) {
            return true;
         }
      }
      return false;
   }

   private static String stripFormatting(String text) {
      if (text == null) {
         return "";
      }
      StringBuilder builder = new StringBuilder(text.length());
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == '\u00a7') {
            i++;
            continue;
         }
         builder.append(c);
      }
      return builder.toString();
   }

   private void recordHitCombo(long now) {
      long comboNanos = secondsToNanos(this.comboWindow == null ? 4.0D : (Double)this.comboWindow.get());
      this.comboCount = this.lastKillNanos != 0L && now - this.lastKillNanos <= comboNanos ? this.comboCount + 1 : 1;
      this.lastKillNanos = now;
   }

   private void showPreview() {
      this.previewCombo = this.previewCombo % 6 + 1;
      this.comboCount = this.previewCombo;
      this.showEffect("Preview Target", 12.4F, System.nanoTime());
   }

   private void showEffect(String name, float targetDistance, long now) {
      this.showEffect(name, targetDistance, now, (double)0.0F, (double)0.0F, (double)0.0F, 0.0F, -1);
   }

   private void showEffect(String name, float targetDistance, long now, double targetX, double targetY, double targetZ, float targetHeight, int targetEntityId) {
      if (this.isLocalPlayerDead()) {
         return;
      }
      this.victimName = ellipsize(name, 24);
      this.victimDistance = targetDistance;
      this.effectStartedNanos = now;
      this.showing = !this.firesOnHit();
      if ((Boolean)this.visualEffects.get() && targetHeight > 0.0F && ((Boolean)this.lightningEffect.get() || (Boolean)this.bloodEffect.get() || (Boolean)this.fireworkEffect.get() || (Boolean)this.swordsEffect.get() || (Boolean)this.magmaEffect.get())) {
         this.spawnVisualEffects(targetX, targetY, targetZ, targetHeight, (Boolean)this.lightningEffect.get(), (Boolean)this.bloodEffect.get(), (Boolean)this.fireworkEffect.get(), (Boolean)this.swordsEffect.get(), (Boolean)this.magmaEffect.get(), targetEntityId);
      }

      this.playSelectedSound();
   }

   private void spawnVisualEffects(double x, double baseY, double z, float targetHeight, boolean lightning, boolean blood, boolean firework, boolean swords, boolean magma, int targetEntityId) {
      ClientLevel level = this.mc.level;
      if (level != null) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         double y = baseY + (double)targetHeight * (double)0.5F;
         if (blood) {
            for(int i = 0; i < 18; ++i) {
               double angle = random.nextDouble((double)0.0F, (Math.PI * 2D));
               double speed = random.nextDouble(0.035, 0.12);
               level.addParticle(ParticleTypes.DAMAGE_INDICATOR, x + random.nextDouble(-0.22, 0.22), y + random.nextDouble(-0.35, 0.35), z + random.nextDouble(-0.22, 0.22), Math.cos(angle) * speed, random.nextDouble(0.04, 0.16), Math.sin(angle) * speed);
            }
         }

         if (firework) {
            for(int i = 0; i < 26; ++i) {
               double angle = random.nextDouble((double)0.0F, (Math.PI * 2D));
               double speed = random.nextDouble(0.08, 0.24);
               level.addParticle(ParticleTypes.FIREWORK, x + random.nextDouble(-0.12, 0.12), y + random.nextDouble(-0.15, (double)0.25F), z + random.nextDouble(-0.12, 0.12), Math.cos(angle) * speed, random.nextDouble(0.05, 0.22), Math.sin(angle) * speed);
            }
         }

         if (magma) {
            for(int i = 0; i < 20; ++i) {
               double angle = random.nextDouble((double)0.0F, (Math.PI * 2D));
               double speed = random.nextDouble(0.06, 0.22);
               level.addParticle(ParticleTypes.LAVA, x + random.nextDouble(-0.28, 0.28), y + random.nextDouble(-0.35, 0.35), z + random.nextDouble(-0.28, 0.28), Math.cos(angle) * speed, random.nextDouble(0.08, 0.32), Math.sin(angle) * speed);
            }

            for(int i = 0; i < 14; ++i) {
               double angle = random.nextDouble((double)0.0F, (Math.PI * 2D));
               double speed = random.nextDouble(0.04, 0.14);
               level.addParticle(ParticleTypes.FLAME, x + random.nextDouble(-0.18, 0.18), y + random.nextDouble(-0.2, (double)0.3F), z + random.nextDouble(-0.18, 0.18), Math.cos(angle) * speed, random.nextDouble(0.02, 0.1), Math.sin(angle) * speed);
            }

            for(int i = 0; i < 8; ++i) {
               double angle = random.nextDouble((double)0.0F, (Math.PI * 2D));
               level.addParticle(ParticleTypes.SMOKE, x + random.nextDouble(-0.25, 0.25), y + random.nextDouble(-0.1, (double)0.45F), z + random.nextDouble(-0.25, 0.25), Math.cos(angle) * 0.03, random.nextDouble(0.02, 0.07), Math.sin(angle) * 0.03);
            }
         }

         if (lightning) {
            LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
               bolt.setPos(x, y - (double)0.5F, z);
               bolt.setVisualOnly(true);
               level.addEntity(bolt);
            }
         }

         if (swords) {
            boolean linkedTargetEsp = targetEntityId >= 0 && (KillAura.INSTANCE.isSwordsEspActive() || KillAuraPlus.INSTANCE.isSwordsEspActive());
            this.spawnSwordStrikes(x, baseY, z, targetHeight, random, linkedTargetEsp, targetEntityId);
         }

      }
   }

   private void spawnSwordStrikes(double x, double baseY, double z, float targetHeight, ThreadLocalRandom random, boolean linkedTargetEsp, int targetEntityId) {
      long now = System.nanoTime();

      for(int index = 0; index < 3; ++index) {
         double angle = (Math.PI * 2D) * (double)index / (double)3.0F + (linkedTargetEsp ? (double)targetEntityId * 0.37 : random.nextDouble(-0.48, 0.48));
         double impactAngle = linkedTargetEsp ? random.nextDouble((double)0.0F, (Math.PI * 2D)) : angle;
         double impactRadius = random.nextDouble(0.18, 0.46);
         double approachRadius = random.nextDouble(0.72, 1.08);
         double impactX = x + Math.cos(impactAngle) * impactRadius;
         double impactZ = z + Math.sin(impactAngle) * impactRadius;
         double groundY = this.findGroundSurfaceY(impactX, baseY, impactZ);
         double startX = linkedTargetEsp ? x + Math.cos(angle) * 0.82 : impactX + Math.cos(angle) * approachRadius;
         double startY = linkedTargetEsp ? baseY + (double)targetHeight * 0.52 + 0.32 : baseY + (double)targetHeight + random.nextDouble((double)3.0F, 4.2);
         double startZ = linkedTargetEsp ? z + Math.sin(angle) * 0.82 : impactZ + Math.sin(angle) * approachRadius;
         this.swordStrikes.add(new SwordStrike(startX, startY, startZ, impactX, groundY - 0.36, impactZ, linkedTargetEsp ? 0L : (long)index * 85000000L, now));
      }

   }

   private double findGroundSurfaceY(double x, double targetY, double z) {
      if (this.mc.level != null && this.mc.player != null) {
         double startY = Math.min((double)this.mc.level.getMaxY() - 0.01, targetY + (double)0.5F);
         double endY = (double)this.mc.level.getMinY() - (double)1.0F;
         BlockHitResult hit = this.mc.level.clip(new ClipContext(new Vec3(x, startY, z), new Vec3(x, endY, z), Block.COLLIDER, Fluid.NONE, this.mc.player));
         return hit.getType() == Type.BLOCK ? hit.getLocation().y : targetY;
      } else {
         return targetY;
      }
   }

   private void submitSwordModel(WorldItemSubmitEvent event, ItemStackRenderState renderState, SwordStrike strike, long elapsedNanos) {
      float insertion = easeOutCubic(clamp((float)elapsedNanos / 4.2E8F));
      Vec3 start = new Vec3(strike.startX(), strike.startY(), strike.startZ());
      Vec3 impact = new Vec3(strike.impactX(), strike.impactY(), strike.impactZ());
      Vec3 direction = impact.subtract(start).normalize();
      Vec3 tip = start.add(impact.subtract(start).scale((double)insertion));
      Vec3 center = tip.subtract(direction.scale((double)0.45F));
      Quaternionf orientation = (new Quaternionf()).rotationTo(SWORD_MODEL_AXIS, new Vector3f((float)direction.x, (float)direction.y, (float)direction.z));
      PoseStack poseStack = event.getPoseStack();
      poseStack.pushPose();
      poseStack.translate(center.subtract(event.getCameraPosition()));
      poseStack.mulPose(orientation);
      poseStack.scale(0.775F, 0.775F, 0.775F);
      int light = LevelRenderer.getLightCoords(this.mc.level, BlockPos.containing(center));
      renderState.submit(poseStack, event.getCollector(), light, OverlayTexture.NO_OVERLAY, 0);
      poseStack.popPose();
   }

   private void playSelectedSound() {
      SoundEvent var10000;
      switch (((KillSound)this.sound.get()).ordinal()) {
         case 0 -> var10000 = null;
         case 1 -> var10000 = SoundEvents.PLAYER_ATTACK_CRIT;
         case 2 -> var10000 = SoundEvents.PLAYER_LEVELUP;
         case 3 -> var10000 = SoundEvents.TOTEM_USE;
         case 4 -> var10000 = SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
         case 5 -> var10000 = (SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value();
         case 6 -> var10000 = SoundEvents.ANVIL_LAND;
         case 7 -> var10000 = SoundEvents.LIGHTNING_BOLT_THUNDER;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      SoundEvent event = var10000;
      if (event != null) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, ((Double)this.soundVolume.get()).floatValue()));
      }

   }

   private void drawStyle(Canvas canvas, int color, float alpha, float progress, int displayedCombo, String displayedName, float displayedDistance) {
      switch (((Style)this.style.get()).ordinal()) {
         case 0 -> this.drawCombo(canvas, color, alpha, progress, displayedCombo, displayedName, displayedDistance);
         case 1 -> this.drawBadge(canvas, color, alpha, progress, displayedCombo, displayedName, displayedDistance);
         case 2 -> this.drawMinimal(canvas, color, alpha, progress, displayedCombo, displayedName, displayedDistance);
      }

   }

   private void drawCombo(Canvas canvas, int color, float alpha, float progress, int displayedCombo, String displayedName, float displayedDistance) {
      int dim = withAlpha(-1, alpha * 0.18F);
      int white = withAlpha(-1, alpha);
      this.strokeCircle(canvas, 0.0F, 0.0F, 22.0F, 1.1F, dim);
      this.strokeArc(canvas, 0.0F, 0.0F, 22.0F, -90.0F, 360.0F * clamp(progress), 2.0F, color);
      this.drawDiamond(canvas, 0.0F, 0.0F, 15.5F, 1.1F, withAlpha(color, alpha * 0.72F));
      this.drawSkull(canvas, 0.0F, -0.5F, color, alpha);

      for(int i = 0; i < 5; ++i) {
         float x = (float)(i - 2) * 6.0F;
         int pipColor = i < Math.min(displayedCombo, 5) ? color : dim;
         this.fillCircle(canvas, x, 27.0F, 1.35F, pipColor);
      }

      if (displayedCombo > 5) {
         drawCenteredText(canvas, "+" + (displayedCombo - 5), 31.0F, 7.0F, color, 6.5F);
      }

      drawCenteredText(canvas, comboLabel(displayedCombo), 34.0F, 10.0F, white, 8.5F);
      this.drawTargetLine(canvas, 45.0F, alpha, displayedName, displayedDistance);
   }

   private void drawBadge(Canvas canvas, int color, float alpha, float progress, int displayedCombo, String displayedName, float displayedDistance) {
      int dim = withAlpha(-1, alpha * 0.2F);
      int white = withAlpha(-1, alpha);
      Point[] shield = new Point[]{new Point(0.0F, -22.0F), new Point(19.0F, -10.0F), new Point(14.0F, 14.0F), new Point(0.0F, 24.0F), new Point(-14.0F, 14.0F), new Point(-19.0F, -10.0F)};
      this.drawPolygon(canvas, shield, false, 1.2F, dim);
      this.drawSkull(canvas, 0.0F, -1.0F, color, alpha);
      float sweep = 22.0F * clamp(progress);

      for(int side : new int[]{-1, 1}) {
         for(int i = 0; i < Math.min(displayedCombo, 4); ++i) {
            float inset = (float)i * 5.0F;
            SkijaUi.line(canvas, (float)side * (27.0F + inset), -sweep * 0.45F, (float)side * (31.0F + inset), 0.0F, 1.5F, color);
            SkijaUi.line(canvas, (float)side * (31.0F + inset), 0.0F, (float)side * (27.0F + inset), sweep * 0.45F, 1.5F, color);
         }
      }

      drawCenteredText(canvas, comboLabel(displayedCombo), 30.0F, 10.0F, white, 8.5F);
      this.drawTargetLine(canvas, 41.0F, alpha, displayedName, displayedDistance);
   }

   private void drawMinimal(Canvas canvas, int color, float alpha, float progress, int displayedCombo, String displayedName, float displayedDistance) {
      String title = comboLabel(displayedCombo);
      String detail = this.targetLine(displayedName, displayedDistance);
      float titleWidth = SkijaUi.textWidth(title, 8.5F);
      float detailWidth = detail.isEmpty() ? 0.0F : SkijaUi.textWidthWithFallback(detail, 7.0F);
      float width = Math.max(92.0F, Math.min(200.0F, Math.max(titleWidth, detailWidth) + 52.0F));
      float height = detail.isEmpty() ? 28.0F : 37.0F;
      float left = -width * 0.5F;
      float top = -height * 0.5F;
      SkijaUi.rounded(canvas, left, top, width, height, 5.0F, withAlpha(-435548652, alpha));
      SkijaUi.rounded(canvas, left, top, 3.0F, height * clamp(progress), 1.5F, color);
      this.drawSkull(canvas, left + 19.0F, 0.0F, color, alpha);
      SkijaUi.text(canvas, title, left + 37.0F, top + 5.0F, 10.0F, withAlpha(-1, alpha), 8.5F);
      if (!detail.isEmpty()) {
         SkijaUi.textWithFallback(canvas, detail, left + 37.0F, top + 18.0F, 10.0F, withAlpha(-4603444, alpha), 7.0F);
      }

   }

   private void drawSkull(Canvas canvas, float centerX, float centerY, int color, float alpha) {
      int cutout = withAlpha(-15723236, alpha);
      this.fillCircle(canvas, centerX, centerY - 2.5F, 7.7F, color);
      SkijaUi.rounded(canvas, centerX - 5.4F, centerY + 2.0F, 10.8F, 6.0F, 2.0F, color);
      SkijaUi.rounded(canvas, centerX - 4.8F, centerY - 4.5F, 3.0F, 2.4F, 0.8F, cutout);
      SkijaUi.rounded(canvas, centerX + 1.8F, centerY - 4.5F, 3.0F, 2.4F, 0.8F, cutout);

      for(int i = -1; i <= 1; ++i) {
         SkijaUi.fill(canvas, centerX + (float)i * 3.0F - 0.55F, centerY + 4.0F, 1.1F, 4.0F, cutout);
      }

   }

   private void drawTargetLine(Canvas canvas, float top, float alpha, String displayedName, float displayedDistance) {
      String line = this.targetLine(displayedName, displayedDistance);
      if (!line.isEmpty()) {
         drawCenteredFallbackText(canvas, line, top, 9.0F, withAlpha(-2629912, alpha), 7.5F);
      }

   }

   private String targetLine(String displayedName, float displayedDistance) {
      if (!(Boolean)this.targetName.get() && !(Boolean)this.distance.get()) {
         return "";
      } else {
         String distanceText = String.format(Locale.ROOT, "%.1fm", displayedDistance);
         if (!(Boolean)this.targetName.get()) {
            return distanceText;
         } else {
            return (Boolean)this.distance.get() ? displayedName + "  " + distanceText : displayedName;
         }
      }
   }

   private static String comboLabel(int displayedCombo) {
      String var10000;
      switch (displayedCombo) {
         case 1 -> var10000 = "ELIMINATED";
         case 2 -> var10000 = "DOUBLE KILL";
         case 3 -> var10000 = "TRIPLE KILL";
         case 4 -> var10000 = "QUAD KILL";
         case 5 -> var10000 = "PENTA KILL";
         default -> var10000 = displayedCombo + " KILL CHAIN";
      }

      return var10000;
   }

   private void strokeCircle(Canvas canvas, float x, float y, float radius, float thickness, int color) {
      Paint paint = this.renderPaint();
      paint.setMode(PaintMode.STROKE).setStrokeWidth(thickness).setColor(color);
      canvas.drawCircle(x, y, radius, paint);
      paint.setMode(PaintMode.FILL).setStrokeWidth(1.0F);
   }

   private void strokeArc(Canvas canvas, float x, float y, float radius, float start, float sweep, float thickness, int color) {
      Paint paint = this.renderPaint();
      paint.setMode(PaintMode.STROKE).setStrokeWidth(thickness).setColor(color);
      canvas.drawArc(x - radius, y - radius, x + radius, y + radius, start, sweep, false, paint);
      paint.setMode(PaintMode.FILL).setStrokeWidth(1.0F);
   }

   private void fillCircle(Canvas canvas, float x, float y, float radius, int color) {
      Paint paint = this.renderPaint();
      paint.setMode(PaintMode.FILL).setColor(color);
      canvas.drawCircle(x, y, radius, paint);
   }

   private void drawDiamond(Canvas canvas, float x, float y, float radius, float thickness, int color) {
      Point[] points = new Point[]{new Point(x, y - radius), new Point(x + radius, y), new Point(x, y + radius), new Point(x - radius, y)};
      this.drawPolygon(canvas, points, false, thickness, color);
   }

   private void drawPolygon(Canvas canvas, Point[] points, boolean fill, float thickness, int color) {
      try {
         Path path = Path.makePolygon(points, true);

         try {
            Paint paint = this.renderPaint();
            paint.setMode(fill ? PaintMode.FILL : PaintMode.STROKE).setStrokeWidth(thickness).setColor(color);
            canvas.drawPath(path, paint);
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
      } finally {
         this.renderPaint().setMode(PaintMode.FILL).setStrokeWidth(1.0F);
      }

   }

   private Paint renderPaint() {
      return this.effectPaint != null ? this.effectPaint : PREVIEW_PAINT;
   }

   private static void drawCenteredText(Canvas canvas, String text, float top, float height, int color, float size) {
      float width = SkijaUi.textWidth(text, size);
      SkijaUi.text(canvas, text, -width * 0.5F, top, height, color, size);
   }

   private static void drawCenteredFallbackText(Canvas canvas, String text, float top, float height, int color, float size) {
      float width = SkijaUi.textWidthWithFallback(text, size);
      SkijaUi.textWithFallback(canvas, text, -width * 0.5F, top, height, color, size);
   }

   private void trimTrackedTargets() {
      while(this.trackedTargets.size() > 32) {
         Iterator<UUID> iterator = this.trackedTargets.keySet().iterator();
         if (!iterator.hasNext()) {
            return;
         }

         iterator.next();
         iterator.remove();
      }

   }

   private void clearState() {
      this.trackedTargets.clear();
      this.swordStrikes.clear();
      this.recentKillNames.clear();
      this.killScores.clear();
      this.showing = false;
      this.effectStartedNanos = 0L;
      this.lastKillNanos = 0L;
      this.victimName = "";
      this.victimDistance = 0.0F;
      this.comboCount = 0;
      this.previewCombo = 0;
   }

   private static String displayName(LivingEntity target) {
      String name = target.getDisplayName().getString();
      return name.isBlank() ? target.getName().getString() : name;
   }

   private static String ellipsize(String value, int maxCodePoints) {
      String safe = value == null ? "" : value.strip();
      int count = safe.codePointCount(0, safe.length());
      if (count <= maxCodePoints) {
         return safe;
      } else {
         int end = safe.offsetByCodePoints(0, Math.max(1, maxCodePoints - 3));
         String var10000 = safe.substring(0, end);
         return var10000 + "...";
      }
   }

   private static long secondsToNanos(double seconds) {
      return (long)(seconds * (double)1.0E9F);
   }

   private static float easeOutCubic(float value) {
      float remaining = 1.0F - clamp(value);
      return 1.0F - remaining * remaining * remaining;
   }

   private static float smoothStep(float value) {
      float clamped = clamp(value);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }

   private static float lerp(float from, float to, float progress) {
      return from + (to - from) * progress;
   }

   private static float clamp(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private static int withAlpha(int color, float alpha) {
      int baseAlpha = color >>> 24;
      int adjusted = Math.max(0, Math.min(255, Math.round((float)baseAlpha * clamp(alpha))));
      return adjusted << 24 | color & 16777215;
   }

   public static enum Style {
      COMBO,
      BADGE,
      MINIMAL;
   }

   public static enum Targets {
      PLAYERS,
      ALL;
   }

   public static enum KillSound {
      NONE,
      CRITICAL,
      LEVEL_UP,
      TOTEM,
      CHALLENGE,
      PLING,
      ANVIL,
      THUNDER;
   }

   private static record TrackedTarget(LivingEntity entity, long attackedAtNanos, String name, float distance) {
   }

   private static record SwordStrike(double startX, double startY, double startZ, double impactX, double impactY, double impactZ, long delayNanos, long startedAtNanos) {
   }
}
