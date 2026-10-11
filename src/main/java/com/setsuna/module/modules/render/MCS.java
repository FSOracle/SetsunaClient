package com.setsuna.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyInputEvent;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.MouseButtonEvent;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.event.events.WorldItemSubmitEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.util.render.Render3DUtils;
import io.github.humbleui.skija.Canvas;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MCS extends Module {
   public static final MCS INSTANCE = new MCS();
   private static final Identifier PISTOL_MODEL = Identifier.fromNamespaceAndPath("setsuna", "mcs_pistol");
   private static final Identifier RIFLE_MODEL = Identifier.fromNamespaceAndPath("setsuna", "mcs_rifle");
   private static final Identifier PISTOL_RELOAD_SOUND = clientSound("mcs.pistol_reload");
   private static final Identifier RIFLE_FIRE_SOUND = clientSound("mcs.rifle_fire");
   private static final Identifier RIFLE_RELOAD_SOUND = clientSound("mcs.rifle_reload");
   private static final int TARGET_RADIUS = 2;
   private static final double BULLET_SPEED = 3.2;
   private static final double BULLET_GRAVITY = 0.0025;
   private static final double BULLET_DRAG = 0.999;
   private static final int MAX_BULLET_TICKS = 40;
   private static final long DAMAGE_FEEDBACK_NANOS = 1200000000L;
   private static final long SWITCH_ANIMATION_NANOS = 460000000L;
   private static final long MAP_ANIMATION_NANOS = 1250000000L;
   private static final long RECOIL_ANIMATION_NANOS = 150000000L;
   private static final int MAP_WIDTH = 15;
   private static final int MAP_LENGTH = 25;
   private static final double MAP_START_DISTANCE = (double)2.0F;
   private static final int SYNC_INTERVAL_TICKS = 2;
   private static final long REMOTE_STATE_TIMEOUT_MILLIS = 2000L;
   private static final long REMOTE_INTERPOLATION_NANOS = 120000000L;
   private static final int RIFLE_SLOT = 0;
   private static final int PISTOL_SLOT = 1;
   private static final int INITIAL_RESERVE_AMMO = 999;
   public final ColorSetting tracerColor = (ColorSetting)this.add(new ColorSetting("Tracer Color", new Color(255, 190, 70, 255)));
   public final ColorSetting hitColor = (ColorSetting)this.add(new ColorSetting("Hit Color", new Color(105, 220, 255, 255)));
   public final EnumSetting<TargetMode> targetMode;
   public final BooleanSetting practiceRange;
   public final IntSetting targetSpawnRange;
   public final IntSetting targetCount;
   public final BooleanSetting movingTargets;
   public final BooleanSetting moveVertical;
   public final BooleanSetting moveHorizontal;
   public final BooleanSetting moveDepth;
   public final DoubleSetting targetSpeed;
   public final EnumSetting<HitSound> hitSound;
   public final EnumSetting<TrainingAmmo> trainingAmmo;
   public final KeybindSetting reloadKey;
   public final BooleanSetting humanoidKillEffect;
   private ItemStack visualPistol;
   private ItemStack visualRifle;
   private ItemStack targetBlock;
   private int mapMinX;
   private int mapMaxX;
   private int mapMinZ;
   private int mapMaxZ;
   private int mapFloorY;
   private double mapStartX;
   private double mapStartZ;
   private double mapForwardX;
   private double mapForwardZ;
   private double mapRightX;
   private double mapRightZ;
   private double mapCenterX;
   private double mapCenterZ;
   private long mapAnimationStartedNanos;
   private final List<Cover> covers;
   private final List<Shot> shots;
   private final List<Target> targets;
   private long targetMotionTicks;
   private HitMarker hitMarker;
   private Vec3 origin;
   private boolean aiming;
   private boolean fireButtonHeld;
   private boolean aimButtonHeld;
   private boolean aimStartedSneaking;
   private boolean aimBlockedUntilRelease;
   private int sneakGraceTicks;
   private float scopeAnimation;
   private long lastScopeAnimationNanos;
   private long lastShotNanos;
   private long recoilStartedNanos;
   private float recoilPitch;
   private float recoilYaw;
   private long damageFeedbackStartedNanos;
   private int lastDamage;
   private int lastTargetHealth;
   private int pistolMagazineAmmo;
   private int pistolReserveAmmo;
   private int rifleMagazineAmmo;
   private int rifleReserveAmmo;
   private boolean reloading;
   private Weapon reloadingWeapon;
   private long reloadStartedNanos;
   private Weapon previouslySelectedWeapon;
   private long switchAnimationStartedNanos;
   private final AnimatedText titleText;
   private final AnimatedText leftStatusText;
   private final AnimatedText rightStatusText;

   private MCS() {
      super("MCS", Category.RENDER);
      this.targetMode = (EnumSetting)this.add(new EnumSetting("Target", MCS.TargetMode.TARGET));
      this.practiceRange = (BooleanSetting)this.add(new BooleanSetting("Practice Range", false));
      this.targetSpawnRange = (IntSetting)this.add((IntSetting)(new IntSetting("Target Spawn Range", 10, 1, 15, 1)).visibleWhen(this.practiceRange::get));
      this.targetCount = (IntSetting)this.add((IntSetting)(new IntSetting("Target Count", 1, 1, 10, 1)).visibleWhen(this.practiceRange::get));
      this.movingTargets = (BooleanSetting)this.add((BooleanSetting)(new BooleanSetting("Moving Targets", false)).visibleWhen(this.practiceRange::get));
      this.moveVertical = (BooleanSetting)this.add((BooleanSetting)(new BooleanSetting("Move Vertical", false)).visibleWhen(() -> this.practiceRange.get() && this.movingTargets.get()));
      this.moveHorizontal = (BooleanSetting)this.add((BooleanSetting)(new BooleanSetting("Move Horizontal", false)).visibleWhen(() -> this.practiceRange.get() && this.movingTargets.get()));
      this.moveDepth = (BooleanSetting)this.add((BooleanSetting)(new BooleanSetting("Move Depth", false)).visibleWhen(() -> this.practiceRange.get() && this.movingTargets.get()));
      this.targetSpeed = (DoubleSetting)this.add((DoubleSetting)(new DoubleSetting("Target Speed", 2.0, 0.1, 10.0, 0.1)).visibleWhen(() -> this.practiceRange.get() && this.movingTargets.get()));
      this.hitSound = (EnumSetting)this.add(new EnumSetting("Hit Sound", MCS.HitSound.HITMARKER));
      this.trainingAmmo = (EnumSetting)this.add(new EnumSetting("Training Ammo", MCS.TrainingAmmo.NORMAL));
      this.reloadKey = (KeybindSetting)this.add(new KeybindSetting("Reload Key", 82));
      this.humanoidKillEffect = (BooleanSetting)this.add((BooleanSetting)(new BooleanSetting("Kill Effect", false)).visibleWhen(() -> this.targetMode.is(MCS.TargetMode.HUMANOID)));
      this.covers = new ArrayList();
      this.shots = new ArrayList();
      this.targets = new ArrayList();
      this.lastTargetHealth = 100;
      this.pistolMagazineAmmo = MCS.Weapon.PISTOL.magazineCapacity;
      this.pistolReserveAmmo = 999;
      this.rifleMagazineAmmo = MCS.Weapon.RIFLE.magazineCapacity;
      this.rifleReserveAmmo = 999;
      this.reloadingWeapon = MCS.Weapon.NONE;
      this.previouslySelectedWeapon = MCS.Weapon.NONE;
      this.titleText = new AnimatedText();
      this.leftStatusText = new AnimatedText();
      this.rightStatusText = new AnimatedText();
      this.targetMode.onChange((mode) -> {
         if (this.isEnabled() && !this.noPlayer()) {
            this.targets.clear();
            this.spawnRandomTargets();
         }

      });
      this.practiceRange.onChange((enabled) -> {
         if (!this.isEnabled() || this.noPlayer()) return;
         this.targets.clear();
         this.shots.clear();
         if (enabled) {
            this.createTrainingMap();
            this.spawnRandomTargets();
         } else {
            this.clearTrainingMap();
         }
      });
      this.targetCount.onChange((ignored) -> {
         if (this.isEnabled() && this.practiceRange.get() && !this.noPlayer() && this.targets.isEmpty()) {
            this.spawnRandomTargets();
         }
      });
      this.trainingAmmo.onChange((mode) -> this.resetAmmo());
   }

   protected void onEnable() {
      if (!this.noPlayer()) {
         if (Football.INSTANCE.isEnabled()) {
            Football.INSTANCE.setEnabled(false);
         }

         if (Basketball.INSTANCE.isEnabled()) {
            Basketball.INSTANCE.setEnabled(false);
         }

         if (Billiards.INSTANCE.isEnabled()) {
            Billiards.INSTANCE.setEnabled(false);
         }

         this.origin = this.mc.player.position();
         this.targetMotionTicks = 0L;
         this.mapFloorY = Integer.MIN_VALUE;
         this.targets.clear();
         if (this.practiceRange.get()) {
            this.createTrainingMap();
            this.spawnRandomTargets();
         }
         this.shots.clear();
         this.aiming = false;
         this.fireButtonHeld = false;
         this.aimButtonHeld = false;
         this.aimStartedSneaking = false;
         this.aimBlockedUntilRelease = false;
         this.sneakGraceTicks = 0;
         this.scopeAnimation = 0.0F;
         this.lastScopeAnimationNanos = System.nanoTime();
         this.lastShotNanos = 0L;
         this.recoilStartedNanos = 0L;
         this.recoilPitch = 0.0F;
         this.recoilYaw = 0.0F;
         this.damageFeedbackStartedNanos = 0L;
         this.lastDamage = 0;
         this.lastTargetHealth = 100;
         this.resetAmmo();
         this.previouslySelectedWeapon = this.selectedWeapon();
         this.switchAnimationStartedNanos = this.previouslySelectedWeapon != MCS.Weapon.NONE ? System.nanoTime() : 0L;
         this.titleText.reset("MCS RANGE");
         this.leftStatusText.reset("LEFT CLICK TO FIRE");
         this.rightStatusText.reset("RIGHT CLICK TO AIM");
      }
   }

   protected void onDisable() {
      this.aiming = false;
      this.fireButtonHeld = false;
      this.aimButtonHeld = false;
      this.aimStartedSneaking = false;
      this.aimBlockedUntilRelease = false;
      this.sneakGraceTicks = 0;
      this.shots.clear();
      this.targets.clear();
      this.hitMarker = null;
      this.origin = null;
      this.covers.clear();
      this.mapFloorY = Integer.MIN_VALUE;
      this.mapAnimationStartedNanos = 0L;
      this.recoilStartedNanos = 0L;
      this.recoilPitch = 0.0F;
      this.recoilYaw = 0.0F;
      this.damageFeedbackStartedNanos = 0L;
      this.reloading = false;
      this.reloadingWeapon = MCS.Weapon.NONE;
      this.reloadStartedNanos = 0L;
      this.previouslySelectedWeapon = MCS.Weapon.NONE;
      this.switchAnimationStartedNanos = 0L;
   }

   private void clearTrainingMap() {
      this.targets.clear();
      this.covers.clear();
      this.mapFloorY = Integer.MIN_VALUE;
      this.mapAnimationStartedNanos = 0L;
   }

   @Listen
   private void onMouse(MouseButtonEvent event) {
      if (!this.noPlayer()) {
         if (event.button() == 0 && event.action() == 0) {
            this.fireButtonHeld = false;
            if (this.mc.screen == null && this.isWeaponSelected()) {
               event.cancel();
            }

         } else if (event.button() == 1 && event.action() == 0) {
            this.aimButtonHeld = false;
            this.aimBlockedUntilRelease = false;
            this.stopAiming();
            if (this.mc.screen == null && this.isWeaponSelected()) {
               event.cancel();
            }

         } else if (this.mc.screen == null && this.isWeaponSelected()) {
            if (event.action() == 1 && this.reloadKey.matchesMouse(event.button())) {
               this.startReload();
               event.cancel();
            } else if (event.button() == 1) {
               if (event.action() == 1) {
                  this.aimButtonHeld = true;
                  if (!this.aimBlockedUntilRelease) {
                     this.beginAiming();
                  }
               }

               event.cancel();
            } else if (event.button() == 0 && event.action() == 1) {
               this.fireButtonHeld = this.selectedWeapon() == MCS.Weapon.RIFLE;
               this.fire();
               event.cancel();
            }

         }
      }
   }

   @Listen
   private void onTick(TickEvent.Post event) {
      if (!this.noPlayer()) {
         Weapon selectedWeapon = this.selectedWeapon();
         if (selectedWeapon != MCS.Weapon.NONE && selectedWeapon != this.previouslySelectedWeapon) {
            this.switchAnimationStartedNanos = System.nanoTime();
         }

         if (this.reloading && selectedWeapon != this.reloadingWeapon) {
            this.cancelReload();
         }

         this.previouslySelectedWeapon = selectedWeapon;
         this.updateReload();
         if (this.mc.screen == null && selectedWeapon == MCS.Weapon.RIFLE && this.fireButtonHeld && !this.reloading) {
            this.fire();
         }

         if (this.mc.screen == null && selectedWeapon != MCS.Weapon.NONE && !this.reloading) {
            if (this.aiming) {
               if (this.sneakGraceTicks > 0) {
                  --this.sneakGraceTicks;
               } else if (!this.mc.player.isShiftKeyDown()) {
                  this.aimBlockedUntilRelease = this.aimButtonHeld;
                  this.stopAiming();
               }
            }
         } else {
            if (this.mc.screen != null) {
               this.fireButtonHeld = false;
            }

            this.aimBlockedUntilRelease = this.aimButtonHeld;
            this.stopAiming();
         }

         if (this.practiceRange.get()) {
            this.updateTargets();
         }
         this.updateShots();
         if (this.hitMarker != null && --this.hitMarker.ticks <= 0) {
            this.hitMarker = null;
         }
      }
   }

   @Listen
   private void onKeyInput(KeyInputEvent event) {
      if (!this.noPlayer() && this.mc.screen == null && event.action() == 1 && this.reloadKey.matches(event.key()) && this.isWeaponSelected()) {
         this.startReload();
         event.cancel();
      }
   }

   @Listen
   private void onKeyboardInput(KeyboardInputEvent event) {
      if (this.aiming) {
         boolean physicalSneak = event.isSneak();
         boolean cancelledSneak = this.aimStartedSneaking ? !physicalSneak : physicalSneak;
         if (cancelledSneak) {
            event.setSneak(false);
            this.aimBlockedUntilRelease = this.aimButtonHeld;
            this.stopAiming();
         } else {
            event.setSneak(true);
         }
      }
   }

   @Listen
   private void onRender3D(Render3DEvent event) {
      if (!this.noPlayer() && this.origin != null) {
         PoseStack poseStack = event.getPoseStack();
         if (this.practiceRange.get()) {
            this.drawTrainingMap(poseStack);
         }

         for(Shot shot : this.shots) {
            this.drawTracer(poseStack, shot);
         }

         for (Target target : this.targets) {
            boolean targetVisible = this.isTargetVisible(target) && this.isMapAreaRevealed(target.position);
            if (targetVisible && this.targetMode.is(MCS.TargetMode.HUMANOID)) {
               this.drawHumanoidTarget(poseStack, target);
            }
            if (this.hitMarker != null && targetVisible && this.hitMarker.target == target) {
               Render3DUtils.drawOutlineBox(poseStack, this.hitMarker.box.inflate(0.025), this.hitColor.get().getRGB(), 2.6F);
            }
         }
      }
   }

   @Listen
   private void onWorldItemSubmit(WorldItemSubmitEvent event) {
      if (!this.noPlayer() && this.origin != null) {
         if (!this.targetMode.is(MCS.TargetMode.HUMANOID)) {
            if (this.targetBlock == null) {
               this.targetBlock = new ItemStack(Items.TARGET);
            }
            for (Target target : this.targets) {
               if (this.isTargetVisible(target) && this.isMapAreaRevealed(target.position)) {
                  this.submitItem(event, this.targetBlock, target.position, target.blockPos, 2.0F);
               }
            }
         }
      }
   }

   @Listen
   private void onRender2D(Render2DEvent event) {
      if (!this.noPlayer() && this.origin != null) {
         Canvas canvas = event.canvas();
         float width = 178.0F;
         float x = event.width() * 0.5F - width * 0.5F;
         SkijaUi.rounded(canvas, x, event.height() - 48.0F, width, 28.0F, 5.0F, -1206642656);
         boolean damageVisible = this.damageFeedbackStartedNanos > 0L && System.nanoTime() - this.damageFeedbackStartedNanos < 1200000000L;
         boolean reloadVisible = this.reloading && this.trainingAmmo.is(MCS.TrainingAmmo.NORMAL);
         Weapon weapon = this.selectedWeapon();
         String title = reloadVisible ? "RELOADING" : (damageVisible ? "DAMAGE " + this.lastDamage : "MCS RANGE");
         String leftStatus = reloadVisible ? "AMMO " + this.magazineAmmo(weapon) + "/" + this.reserveAmmo(weapon) : (damageVisible ? "TARGET HP " + this.lastTargetHealth + "/100" : (this.aiming ? "AIM 30 FOV" : "LEFT CLICK TO FIRE"));
         String rightStatus = reloadVisible ? "PLEASE WAIT" : (this.aiming ? (this.mc.player.isShiftKeyDown() ? "SNEAKING" : "NOT SNEAKING") : "RIGHT CLICK TO AIM");
         this.titleText.set(title);
         this.leftStatusText.set(leftStatus);
         this.rightStatusText.set(rightStatus);
         drawAnimatedText(canvas, this.titleText, x + 9.0F, event.height() - 46.0F, 10.0F, -1, 8.0F, true, false);
         if (reloadVisible) {
            float barX = x + 15.0F + SkijaUi.boldTextWidth("RELOADING", 8.0F);
            float barY = event.height() - 41.5F;
            float barWidth = x + width - 9.0F - barX;
            SkijaUi.rounded(canvas, barX, barY, barWidth, 2.5F, 1.25F, 1077758036);
            SkijaUi.rounded(canvas, barX, barY, barWidth * this.reloadProgress(), 2.5F, 1.25F, -9382401);
         }

         drawAnimatedText(canvas, this.leftStatusText, x + 9.0F, event.height() - 32.0F, 10.0F, reloadVisible ? -9382401 : (damageVisible ? -15253 : (this.aiming ? -9382401 : -2695963)), 7.0F, false, false);
         drawAnimatedText(canvas, this.rightStatusText, x + width - 9.0F, event.height() - 32.0F, 10.0F, reloadVisible ? -1345991466 : (this.aiming ? (this.mc.player.isShiftKeyDown() ? -9247070 : -37002) : -1345991466), this.aiming && !reloadVisible ? 7.0F : 6.5F, false, true);
      }
   }

   public ItemStack modifyRenderedMainHandStack(ItemStack original) {
      return original != null && this.shouldRenderWeapon() ? this.getVisualWeapon() : original;
   }

   public ItemStack getVisualPistol() {
      if (this.visualPistol == null) {
         this.visualPistol = new ItemStack(Items.STICK);
         this.visualPistol.set(DataComponents.ITEM_MODEL, PISTOL_MODEL);
      }

      return this.visualPistol;
   }

   public ItemStack getVisualWeapon() {
      if (this.selectedWeapon() == MCS.Weapon.RIFLE) {
         if (this.visualRifle == null) {
            this.visualRifle = new ItemStack(Items.STICK);
            this.visualRifle.set(DataComponents.ITEM_MODEL, RIFLE_MODEL);
         }

         return this.visualRifle;
      } else {
         return this.getVisualPistol();
      }
   }

   public boolean shouldRenderFirstPersonWeapon(AbstractClientPlayer player, InteractionHand hand) {
      return player == this.mc.player && hand == InteractionHand.MAIN_HAND && this.shouldRenderWeapon();
   }

   public boolean shouldSuppressOffhand(AbstractClientPlayer player, InteractionHand hand) {
      return player == this.mc.player && hand == InteractionHand.OFF_HAND && this.shouldRenderWeapon();
   }

   public void transformFirstPersonWeapon(PoseStack poseStack, HumanoidArm arm) {
      float progress = this.scopeProgress();
      float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
      poseStack.translate(-side * 0.52F * progress, -0.08F * progress, -0.3F * progress);
      float switchProgress = this.switchAnimationProgress();
      if (switchProgress < 1.0F) {
         if (this.selectedWeapon() == MCS.Weapon.PISTOL) {
            float throwY;
            if (switchProgress < 0.22F) {
               float rise = smoothStep(switchProgress / 0.22F);
               throwY = Mth.lerp(rise, -0.58F, 0.48F);
            } else if (switchProgress < 0.68F) {
               float airborne = (switchProgress - 0.22F) / 0.46F;
               throwY = 0.48F + 0.16F * Mth.sin((double)(airborne * (float)Math.PI));
            } else {
               float catchProgress = smoothStep((switchProgress - 0.68F) / 0.32F);
               throwY = Mth.lerp(catchProgress, 0.48F, 0.0F);
            }

            float spin = smoothStep(Mth.clamp((switchProgress - 0.16F) / 0.66F, 0.0F, 1.0F));
            poseStack.translate(0.0F, throwY, -0.1F * Mth.sin((double)(switchProgress * (float)Math.PI)));
            poseStack.mulPose(Axis.XP.rotationDegrees(-360.0F * spin));
         } else {
            float eased = smoothStep(switchProgress);
            float remaining = 1.0F - eased;
            poseStack.translate(0.0F, -0.68F * remaining, 0.14F * remaining);
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * 18.0F * remaining));
         }
      }

      if (this.reloading) {
         float reload = this.reloadProgress();
         float dip = Mth.sin((double)(reload * (float)Math.PI));
         poseStack.translate(side * 0.1F * dip, -0.36F * dip, 0.08F * dip);
         poseStack.mulPose(Axis.ZP.rotationDegrees(side * 24.0F * dip));
         poseStack.mulPose(Axis.XP.rotationDegrees(-18.0F * dip));
      }

   }

   public boolean shouldRenderSupportHand() {
      float switchProgress = this.switchAnimationProgress();
      return this.shouldRenderWeapon() && (this.reloading || this.selectedWeapon() == MCS.Weapon.PISTOL && switchProgress >= 0.62F && switchProgress < 0.99F);
   }

   public void transformSupportHand(PoseStack poseStack, HumanoidArm arm) {
      float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
      float reach;
      if (this.reloading) {
         float progress = this.reloadProgress();
         reach = Mth.sin((double)(Mth.clamp(progress / 0.82F, 0.0F, 1.0F) * (float)Math.PI));
         float pull = Mth.sin((double)(Mth.clamp((progress - 0.48F) / 0.3F, 0.0F, 1.0F) * (float)Math.PI));
         poseStack.translate(-side * 0.42F * reach, -0.46F + 0.46F * reach, -0.28F + 0.16F * pull);
         poseStack.mulPose(Axis.XP.rotationDegrees(-35.0F * reach));
      } else {
         float phase = Mth.clamp((this.switchAnimationProgress() - 0.62F) / 0.37F, 0.0F, 1.0F);
         reach = Mth.sin((double)(phase * (float)Math.PI));
         float pull = Mth.sin((double)(Mth.clamp((phase - 0.12F) / 0.72F, 0.0F, 1.0F) * (float)Math.PI));
         poseStack.translate(-side * 0.34F * reach, -0.54F + 0.82F * reach, -0.38F + 0.28F * pull);
         poseStack.mulPose(Axis.XP.rotationDegrees(-58.0F * reach));
      }

      poseStack.mulPose(Axis.YP.rotationDegrees(side * 16.0F * reach));
   }

   public float modifyFov(float vanillaFov) {
      return Mth.lerp(this.scopeProgress(), vanillaFov, 30.0F);
   }

   public boolean isPistolSelected() {
      return this.selectedWeapon() == MCS.Weapon.PISTOL;
   }

   public boolean isWeaponSelected() {
      return this.selectedWeapon() != MCS.Weapon.NONE;
   }

   public Weapon selectedWeapon() {
      if (this.isEnabled() && !this.noPlayer()) {
         int slot = this.mc.player.getInventory().getSelectedSlot();
         if (slot == 0) {
            return MCS.Weapon.RIFLE;
         } else {
            return slot == 1 ? MCS.Weapon.PISTOL : MCS.Weapon.NONE;
         }
      } else {
         return MCS.Weapon.NONE;
      }
   }

   public String displayedWeaponName() {
      return this.selectedWeapon().displayName;
   }

   public int displayedMagazineAmmo() {
      return this.trainingAmmo.is(MCS.TrainingAmmo.INFINITE) ? 999 : this.magazineAmmo(this.selectedWeapon());
   }

   public int displayedReserveAmmo() {
      return this.trainingAmmo.is(MCS.TrainingAmmo.INFINITE) ? 999 : this.reserveAmmo(this.selectedWeapon());
   }

   private boolean shouldRenderWeapon() {
      return this.isWeaponSelected();
   }

   private void fire() {
      if (this.shouldRenderWeapon() && !this.reloading && !(this.switchAnimationProgress() < 1.0F)) {
         Weapon weapon = this.selectedWeapon();
         if (this.trainingAmmo.is(MCS.TrainingAmmo.NORMAL) && this.magazineAmmo(weapon) <= 0) {
            this.startReload();
         } else {
            long now = System.nanoTime();
            if (this.lastShotNanos == 0L || now - this.lastShotNanos >= weapon.fireIntervalNanos) {
               this.lastShotNanos = now;
               boolean scopedShot = this.aiming;
               Vec3 look = this.applyWeaponSpread(this.mc.player.getLookAngle(), scopedShot, weapon);
               this.triggerRecoil(scopedShot, weapon);
               Vec3 collisionStart = this.mc.player.getEyePosition();
               Vec3 visualStart = this.muzzlePosition(look, this.scopeProgress());
               long sequence = (long)this.shots.size();
               Vec3 velocity = look.scale(3.2);
               this.shots.add(new Shot(collisionStart, visualStart, velocity, weapon, false, sequence));
               this.playWeaponFireSound();
               if (this.trainingAmmo.is(MCS.TrainingAmmo.NORMAL)) {
                  this.setMagazineAmmo(weapon, this.magazineAmmo(weapon) - 1);
                  if (this.magazineAmmo(weapon) <= 0) {
                     this.startReload();
                  }
               }

            }
         }
      }
   }

   private Vec3 applyWeaponSpread(Vec3 look, boolean scopedShot, Weapon weapon) {
      Vec3 right = look.cross(new Vec3((double)0.0F, (double)1.0F, (double)0.0F));
      if (right.lengthSqr() < 1.0E-8) {
         right = new Vec3((double)1.0F, (double)0.0F, (double)0.0F);
      } else {
         right = right.normalize();
      }

      Vec3 up = right.cross(look).normalize();
      double spread = scopedShot ? 0.002 : (weapon == MCS.Weapon.RIFLE ? 0.06 : 0.09);
      ThreadLocalRandom random = ThreadLocalRandom.current();
      double horizontal = random.nextDouble(-spread, spread);
      double vertical = random.nextDouble(-spread, spread);
      return look.add(right.scale(horizontal)).add(up.scale(vertical)).normalize();
   }

   private void triggerRecoil(boolean scopedShot, Weapon weapon) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      float scale = scopedShot ? 0.1F : 1.0F;
      float kick = (weapon == MCS.Weapon.RIFLE ? 2.8F : 2.1F) * scale;
      this.recoilPitch = -kick - random.nextFloat() * kick * 0.35F;
      this.recoilYaw = (random.nextFloat() - 0.5F) * kick * 0.65F;
      this.recoilStartedNanos = System.nanoTime();
   }

   public float cameraRecoilPitch() {
      return this.cameraRecoilComponent(this.recoilPitch);
   }

   public float cameraRecoilYaw() {
      return this.cameraRecoilComponent(this.recoilYaw);
   }

   private float cameraRecoilComponent(float value) {
      if (this.recoilStartedNanos == 0L) {
         return 0.0F;
      } else {
         float progress = Mth.clamp((float)(System.nanoTime() - this.recoilStartedNanos) / 1.5E8F, 0.0F, 1.0F);
         if (progress >= 1.0F) {
            this.recoilStartedNanos = 0L;
            return 0.0F;
         } else {
            float envelope = (1.0F - progress) * (1.0F - progress);
            return value * envelope;
         }
      }
   }

   private void resetAmmo() {
      this.pistolMagazineAmmo = MCS.Weapon.PISTOL.magazineCapacity;
      this.pistolReserveAmmo = 999;
      this.rifleMagazineAmmo = MCS.Weapon.RIFLE.magazineCapacity;
      this.rifleReserveAmmo = 999;
      this.reloading = false;
      this.reloadingWeapon = MCS.Weapon.NONE;
      this.reloadStartedNanos = 0L;
   }

   private void startReload() {
      Weapon weapon = this.selectedWeapon();
      if (!this.trainingAmmo.is(MCS.TrainingAmmo.INFINITE) && !this.reloading && weapon != MCS.Weapon.NONE && this.reserveAmmo(weapon) > 0 && this.magazineAmmo(weapon) < weapon.magazineCapacity) {
         this.reloading = true;
         this.reloadingWeapon = weapon;
         this.reloadStartedNanos = System.nanoTime();
         this.playWeaponSound(weapon.reloadSound, 0.92F);
         this.aimBlockedUntilRelease = this.aimButtonHeld;
         this.stopAiming();
      }
   }

   private void updateReload() {
      if (this.reloading) {
         if (this.trainingAmmo.is(MCS.TrainingAmmo.INFINITE)) {
            this.cancelReload();
         } else if (System.nanoTime() - this.reloadStartedNanos >= this.reloadingWeapon.reloadDurationNanos) {
            int loaded = Math.min(this.reloadingWeapon.magazineCapacity - this.magazineAmmo(this.reloadingWeapon), this.reserveAmmo(this.reloadingWeapon));
            this.setMagazineAmmo(this.reloadingWeapon, this.magazineAmmo(this.reloadingWeapon) + loaded);
            this.setReserveAmmo(this.reloadingWeapon, this.reserveAmmo(this.reloadingWeapon) - loaded);
            this.cancelReload();
         }
      }
   }

   private float reloadProgress() {
      if (this.reloading && this.reloadStartedNanos != 0L) {
         float linear = Mth.clamp((float)(System.nanoTime() - this.reloadStartedNanos) / (float)this.reloadingWeapon.reloadDurationNanos, 0.0F, 1.0F);
         return smoothStep(linear);
      } else {
         return 0.0F;
      }
   }

   private void cancelReload() {
      this.reloading = false;
      this.reloadingWeapon = MCS.Weapon.NONE;
      this.reloadStartedNanos = 0L;
   }

   private int magazineAmmo(Weapon weapon) {
      int var10000;
      switch (weapon.ordinal()) {
         case 0 -> var10000 = this.rifleMagazineAmmo;
         case 1 -> var10000 = this.pistolMagazineAmmo;
         case 2 -> var10000 = 0;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private int reserveAmmo(Weapon weapon) {
      int var10000;
      switch (weapon.ordinal()) {
         case 0 -> var10000 = this.rifleReserveAmmo;
         case 1 -> var10000 = this.pistolReserveAmmo;
         case 2 -> var10000 = 0;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private void setMagazineAmmo(Weapon weapon, int value) {
      if (weapon == MCS.Weapon.RIFLE) {
         this.rifleMagazineAmmo = value;
      } else if (weapon == MCS.Weapon.PISTOL) {
         this.pistolMagazineAmmo = value;
      }

   }

   private void setReserveAmmo(Weapon weapon, int value) {
      if (weapon == MCS.Weapon.RIFLE) {
         this.rifleReserveAmmo = value;
      } else if (weapon == MCS.Weapon.PISTOL) {
         this.pistolReserveAmmo = value;
      }

   }

   private float switchAnimationProgress() {
      return this.switchAnimationStartedNanos == 0L ? 1.0F : Mth.clamp((float)(System.nanoTime() - this.switchAnimationStartedNanos) / 4.6E8F, 0.0F, 1.0F);
   }

   private static float smoothStep(float value) {
      float clamped = Mth.clamp(value, 0.0F, 1.0F);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }

   private Vec3 muzzlePosition(Vec3 look, float scopeProgress) {
      Vec3 screenRight = look.cross(new Vec3((double)0.0F, (double)1.0F, (double)0.0F));
      if (screenRight.lengthSqr() < 1.0E-8) {
         screenRight = new Vec3((double)1.0F, (double)0.0F, (double)0.0F);
      } else {
         screenRight = screenRight.normalize();
      }

      Vec3 screenUp = screenRight.cross(look).normalize();
      double armSide = this.mc.player.getMainArm() == HumanoidArm.RIGHT ? (double)1.0F : (double)-1.0F;
      double lateral = Mth.lerp((double)scopeProgress, 0.34 * armSide, (double)0.0F);
      double vertical = Mth.lerp((double)scopeProgress, -0.27, -0.19);
      double forwardDistance = Mth.lerp((double)scopeProgress, (double)0.5F, 0.64);
      return this.mc.player.getEyePosition().add(look.scale(forwardDistance)).add(screenRight.scale(lateral)).add(screenUp.scale(vertical));
   }

   private void beginAiming() {
      if (this.shouldRenderWeapon() && !this.reloading) {
         this.aiming = true;
         this.aimStartedSneaking = this.mc.player.isShiftKeyDown() || this.mc.options.keyShift.isDown();
         this.sneakGraceTicks = 2;
      }
   }

   private void stopAiming() {
      this.aiming = false;
      this.sneakGraceTicks = 0;
   }

   private float scopeProgress() {
      long now = System.nanoTime();
      if (this.lastScopeAnimationNanos == 0L) {
         this.lastScopeAnimationNanos = now;
      }

      float deltaSeconds = Math.min(0.05F, (float)(now - this.lastScopeAnimationNanos) / 1.0E9F);
      this.lastScopeAnimationNanos = now;
      float targetProgress = this.isEnabled() && this.aiming ? 1.0F : 0.0F;
      float blend = 1.0F - (float)Math.exp((double)(-13.0F * deltaSeconds));
      this.scopeAnimation += (targetProgress - this.scopeAnimation) * blend;
      if (Math.abs(targetProgress - this.scopeAnimation) < 0.001F) {
         this.scopeAnimation = targetProgress;
      }

      return this.scopeAnimation * this.scopeAnimation * (3.0F - 2.0F * this.scopeAnimation);
   }

   private void updateShots() {
      Iterator<Shot> iterator = this.shots.iterator();

      while(iterator.hasNext()) {
         Shot shot = (Shot)iterator.next();
         if (shot.terminal) {
            iterator.remove();
         } else {
            ++shot.age;
            Vec3 previousCollision = shot.collisionPosition;
            Vec3 intendedCollision = previousCollision.add(shot.velocity);
            // Practice rounds use local projectile logic: real world blocks do not consume a shot.
            boolean collisionBlocked = false;
            Vec3 collisionEnd = intendedCollision;
            Vec3 coverImpact = this.firstCoverImpact(previousCollision, intendedCollision);
            if (coverImpact != null) {
               collisionEnd = coverImpact;
               collisionBlocked = true;
            }

            double fullDistance = Math.max(1.0E-8, previousCollision.distanceTo(intendedCollision));
            double traveledFraction = Mth.clamp(previousCollision.distanceTo(collisionEnd) / fullDistance, (double)0.0F, (double)1.0F);
            Vec3 intendedVisual = shot.renderPosition.add(shot.velocity.scale(traveledFraction));
            shot.collisionPosition = collisionEnd;
            shot.renderPosition = intendedVisual;
            boolean hit = this.hitTarget(shot, previousCollision, collisionEnd);
            if (hit) {
               iterator.remove();
            } else if (!collisionBlocked && shot.age < 40) {
               shot.velocity = shot.velocity.scale(0.999).add((double)0.0F, -0.0025, (double)0.0F);
            } else {
               shot.terminal = true;
            }
         }
      }

   }

   private Vec3 firstCoverImpact(Vec3 from, Vec3 to) {
      Vec3 closest = null;
      double closestDistance = Double.POSITIVE_INFINITY;

      for(Cover cover : this.covers) {
         AABB activeBox = this.activeCoverBox(cover);
         if (activeBox != null) {
            Vec3 impact = activeBox.contains(from.x, from.y, from.z) ? from : activeBox.clip(from, to).orElse(null);
            if (impact != null) {
               double distance = from.distanceToSqr(impact);
               if (distance < closestDistance) {
                  closestDistance = distance;
                  closest = impact;
               }
            }
         }
      }

      return closest;
   }

   private AABB activeCoverBox(Cover cover) {
      int index = this.covers.indexOf(cover);
      if (index >= 0 && this.isMapAreaRevealed(cover.box().getCenter())) {
         AABB risen = this.risenCoverBox(cover, index);
         return risen.maxY > risen.minY ? risen : null;
      } else {
         return null;
      }
   }

   private boolean isTargetVisible(Target target) {
      Vec3 camera = this.mc.getEntityRenderDispatcher().camera.position();
      if (this.isInsideActiveCover(camera)) {
         return false;
      }
      AABB targetBox = this.targetMode.is(MCS.TargetMode.HUMANOID)
              ? target.humanoidRegions[1].box : targetRegion(target).box;
      Vec3 center = targetBox.getCenter();
      Vec3 coverImpact = this.firstCoverImpact(camera, center);
      return coverImpact == null || camera.distanceToSqr(coverImpact) >= camera.distanceToSqr(center);
   }

   private boolean isRemotePartVisible(Vec3 center) {
      Vec3 camera = this.mc.getEntityRenderDispatcher().camera.position();
      if (this.isInsideActiveCover(camera)) {
         return false;
      } else {
         Vec3 coverImpact = this.firstCoverImpact(camera, center);
         return coverImpact == null || camera.distanceToSqr(coverImpact) >= camera.distanceToSqr(center);
      }
   }

   private boolean isInsideActiveCover(Vec3 point) {
      for(Cover cover : this.covers) {
         AABB activeBox = this.activeCoverBox(cover);
         if (activeBox != null && activeBox.contains(point.x, point.y, point.z)) {
            return true;
         }
      }

      return false;
   }

   private TargetRegion closestBodyHit(TargetRegion[] regions, Vec3 previous, Vec3 current) {
      TargetRegion closest = null;
      double closestDistance = Double.POSITIVE_INFINITY;

      for(TargetRegion region : regions) {
         Vec3 hit = region.box.clip(previous, current).orElse(null);
         if (hit != null) {
            double distance = previous.distanceToSqr(hit);
            if (distance < closestDistance) {
               closestDistance = distance;
               closest = region;
            }
         }
      }

      return closest;
   }

   private TargetRegion[] localBodyRegions() {
      return this.playerBodyRegions(this.mc.player.position(), this.mc.player.getYRot());
   }

   private static TargetRegion bodyRegion(int index, Vec3 center) {
      double halfX = index == 0 ? (double)0.25F : (index < 2 ? 0.32 : 0.16);
      double halfY = index == 0 ? 0.23 : (index == 1 ? 0.38 : 0.38);
      double halfZ = index == 0 ? 0.22 : (index < 2 ? 0.18 : 0.14);
      BodyPart part = index == 0 ? MCS.BodyPart.HEAD : (index == 1 ? MCS.BodyPart.TORSO : MCS.BodyPart.LIMB);
      return new TargetRegion(new AABB(center.x - halfX, center.y - halfY, center.z - halfZ, center.x + halfX, center.y + halfY, center.z + halfZ), part);
   }

   private TargetRegion[] playerBodyRegions(Vec3 feet, float yaw) {
      double radians = Math.toRadians((double)yaw);
      double rightX = Math.cos(radians);
      double rightZ = -Math.sin(radians);
      Vec3 torso = feet.add((double)0.0F, 1.05, (double)0.0F);
      Vec3[] centers = new Vec3[]{feet.add((double)0.0F, 1.72, (double)0.0F), torso, torso.add(rightX * -0.36, (double)0.0F, rightZ * -0.36), torso.add(rightX * 0.36, (double)0.0F, rightZ * 0.36), feet.add(rightX * -0.16, 0.37, rightZ * -0.16), feet.add(rightX * 0.16, 0.37, rightZ * 0.16)};
      TargetRegion[] regions = new TargetRegion[6];

      for(int index = 0; index < regions.length; ++index) {
         regions[index] = bodyRegion(index, centers[index]);
      }

      return regions;
   }

   private boolean hitTarget(Shot shot, Vec3 previous, Vec3 current) {
      TargetHit hit = this.closestTargetHit(previous, current);
      if (hit == null) {
         return false;
      }

      Target target = hit.target;
      TargetRegion region = hit.region;
      int damage = this.targetMode.is(MCS.TargetMode.HUMANOID) ? damageFor(shot.weapon, region.part) : 100;
      target.health = Math.max(0, target.health - damage);
      this.lastDamage = damage;
      this.lastTargetHealth = target.health;
      this.damageFeedbackStartedNanos = System.nanoTime();
      this.hitMarker = new HitMarker(target, region.box, 6);
      this.playHitSound();
      if (target.health <= 0 && this.targetMode.is(MCS.TargetMode.HUMANOID) && this.humanoidKillEffect.get()) {
         KillEffect.INSTANCE.triggerLocalTarget("MCS Target", target.position, 1.9F);
      }
      if (target.health <= 0 || this.targetMode.is(MCS.TargetMode.TARGET)) {
         this.targets.remove(target);
         if (this.targets.isEmpty()) {
            this.spawnRandomTargets();
         }
      }
      return true;
   }

   private TargetHit closestTargetHit(Vec3 previous, Vec3 current) {
      TargetHit closest = null;
      double closestDistance = Double.POSITIVE_INFINITY;
      for (Target target : this.targets) {
         TargetRegion region = this.targetMode.is(MCS.TargetMode.HUMANOID)
                 ? this.closestHumanoidHit(target, previous, current) : targetRegion(target);
         if (region != null) {
            Vec3 hit = region.box.clip(previous, current).orElse(null);
            if (hit != null) {
               double distance = previous.distanceToSqr(hit);
               if (distance < closestDistance) {
                  closestDistance = distance;
                  closest = new TargetHit(target, region);
               }
            }
         }
      }
      return closest;
   }

   private static int damageFor(Weapon weapon, BodyPart part) {
      if (part == MCS.BodyPart.TARGET) {
         return 100;
      } else {
         int var10000;
         switch (weapon.ordinal()) {
            case 0:
               switch (part.ordinal()) {
                  case 0:
                     var10000 = 100;
                     return var10000;
                  case 1:
                     var10000 = 50;
                     return var10000;
                  case 2:
                     var10000 = 34;
                     return var10000;
                  case 3:
                     var10000 = 20;
                     return var10000;
                  default:
                     throw new MatchException((String)null, (Throwable)null);
               }
            case 1:
            case 2:
               var10000 = part.pistolDamage;
               return var10000;
            default:
               throw new MatchException((String)null, (Throwable)null);
         }
      }
   }

   private TargetRegion closestHumanoidHit(Target target, Vec3 previous, Vec3 current) {
      TargetRegion closest = null;
      double closestDistance = Double.POSITIVE_INFINITY;

      for(TargetRegion region : target.humanoidRegions) {
         Vec3 hit = region.box.clip(previous, current).orElse(null);
         if (hit != null) {
            double distance = previous.distanceToSqr(hit);
            if (distance < closestDistance) {
               closestDistance = distance;
               closest = region;
            }
         }
      }

      return closest;
   }

   private static TargetRegion targetRegion(Target target) {
      Vec3 position = target.position;
      return new TargetRegion(new AABB(position.x - 0.5, position.y, position.z - 0.5,
              position.x + 0.5, position.y + 1.0, position.z + 0.5), MCS.BodyPart.TARGET);
   }

   private void drawTracer(PoseStack poseStack, Shot shot) {
      if (shot.age > 0 && !(shot.velocity.lengthSqr() < 1.0E-8)) {
         Vec3 direction = shot.velocity.normalize();
         double trailLength = Math.min((double)3.0F, shot.velocity.length() * 1.15);
         Vec3 head = shot.renderPosition;
         trailLength = Math.min(trailLength, shot.renderStart.distanceTo(head));
         Vec3 tail = head.subtract(direction.scale(trailLength));
         Render3DUtils.drawLine(poseStack, tail, head, this.tracerColor.get().getRGB(), 4.6F);
         Render3DUtils.drawLine(poseStack, tail.lerp(head, 0.32), head, (new Color(255, 252, 232, 255)).getRGB(), 1.8F);
         Render3DUtils.drawLine(poseStack, head.subtract(direction.scale(0.38)), head, (new Color(255, 255, 255, 255)).getRGB(), 2.4F);
      }
   }

   private void drawHumanoidTarget(PoseStack poseStack, Target target) {
      for(TargetRegion region : target.humanoidRegions) {
         int var10000;
         switch (region.part.ordinal()) {
            case 0 -> var10000 = 1717268868;
            case 1 -> var10000 = 1719379906;
            case 2 -> var10000 = 1717268868;
            case 3 -> var10000 = 1715884896;
            default -> throw new MatchException((String)null, (Throwable)null);
         }

         int fill = var10000;
         Render3DUtils.drawFilledBox(region.box, fill);
         Render3DUtils.drawOutlineBox(poseStack, region.box, -1445644, 1.5F);
      }

   }

   private static TargetRegion[] createHumanoidRegions(Vec3 feet, float yaw) {
      double radians = Math.toRadians((double)yaw);
      double rightX = Math.cos(radians);
      double rightZ = -Math.sin(radians);
      Vec3 torso = feet.add((double)0.0F, 1.05, (double)0.0F);
      Vec3[] centers = new Vec3[]{feet.add((double)0.0F, 1.72, (double)0.0F), torso, torso.add(rightX * -0.36, (double)0.0F, rightZ * -0.36), torso.add(rightX * 0.36, (double)0.0F, rightZ * 0.36), feet.add(rightX * -0.16, 0.37, rightZ * -0.16), feet.add(rightX * 0.16, 0.37, rightZ * 0.16)};
      TargetRegion[] regions = new TargetRegion[6];
      for (int index = 0; index < regions.length; ++index) {
         regions[index] = bodyRegion(index, centers[index]);
      }
      return regions;
   }

   private void playHitSound() {
      SoundEvent var10000;
      switch (((HitSound)this.hitSound.get()).ordinal()) {
         case 0 -> var10000 = null;
         case 1 -> var10000 = SoundEvents.ARROW_HIT_PLAYER;
         case 2 -> var10000 = (SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value();
         case 3 -> var10000 = SoundEvents.PLAYER_ATTACK_CRIT;
         case 4 -> var10000 = SoundEvents.AMETHYST_BLOCK_HIT;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      SoundEvent sound = var10000;
      if (sound != null) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 0.85F));
      }

   }

   private void playWeaponFireSound() {
      this.playWeaponSound(RIFLE_FIRE_SOUND, 1.0F);
   }

   private void playWeaponSound(Identifier sound, float volume) {
      if (sound != null) {
         if (this.mc.getSoundManager().getSoundEvent(sound) == null) {
            SoundEvent fallback = sound != PISTOL_RELOAD_SOUND && sound != RIFLE_RELOAD_SOUND ? SoundEvents.FIREWORK_ROCKET_BLAST : SoundEvents.IRON_TRAPDOOR_CLOSE;
            this.mc.getSoundManager().play(SimpleSoundInstance.forUI(fallback, 0.92F, volume));
            return;
         }

         this.mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.MASTER, volume, 1.0F, SoundInstance.createUnseededRandom(), false, 0, Attenuation.NONE, (double)0.0F, (double)0.0F, (double)0.0F, true));
      }

   }

   private static Identifier clientSound(String path) {
      return Identifier.fromNamespaceAndPath("setsuna", path);
   }

   private static void drawAnimatedText(Canvas canvas, AnimatedText text, float anchorX, float y, float height, int color, float size, boolean bold, boolean rightAligned) {
      float progress = text.progress();
      if (!text.previous.isEmpty() && progress < 1.0F) {
         drawTextLayer(canvas, text.previous, anchorX, y, height, withAlpha(color, 1.0F - progress), size, bold, rightAligned);
      }

      if (!text.current.isEmpty()) {
         drawTextLayer(canvas, text.current, anchorX, y, height, withAlpha(color, progress), size, bold, rightAligned);
      }

   }

   private static void drawTextLayer(Canvas canvas, String text, float anchorX, float y, float height, int color, float size, boolean bold, boolean rightAligned) {
      float x = rightAligned ? anchorX - (bold ? SkijaUi.boldTextWidth(text, size) : SkijaUi.textWidth(text, size)) : anchorX;
      if (bold) {
         SkijaUi.boldText(canvas, text, x, y, height, color, size);
      } else {
         SkijaUi.text(canvas, text, x, y, height, color, size);
      }

   }

   private static int withAlpha(int color, float alpha) {
      int original = color >>> 24;
      int applied = Math.round((float)original * Mth.clamp(alpha, 0.0F, 1.0F));
      return color & 16777215 | applied << 24;
   }

   private void submitItem(WorldItemSubmitEvent event, ItemStack stack, BlockPos blockPos, float scale) {
      this.submitItem(event, stack, Vec3.atCenterOf(blockPos), blockPos, scale, (double)0.0F);
   }

   private void submitItem(WorldItemSubmitEvent event, ItemStack stack, BlockPos blockPos, float scale, double yOffset) {
      this.submitItem(event, stack, Vec3.atCenterOf(blockPos), blockPos, scale, yOffset);
   }

   private void submitItem(WorldItemSubmitEvent event, ItemStack stack, Vec3 position,
                           BlockPos lightPos, float scale) {
      this.submitItem(event, stack, position, lightPos, scale, (double)0.0F);
   }

   private void submitItem(WorldItemSubmitEvent event, ItemStack stack, Vec3 position,
                           BlockPos lightPos, float scale, double yOffset) {
      PoseStack poseStack = event.getPoseStack();
      poseStack.pushPose();
      poseStack.translate(position.subtract(event.getCameraPosition()).add((double)0.0F, yOffset, (double)0.0F));
      poseStack.scale(scale, scale, scale);
      int light = LevelRenderer.getLightCoords(this.mc.level, lightPos);
      ItemStackRenderState renderState = new ItemStackRenderState();
      this.mc.getItemModelResolver().updateForLiving(renderState, stack, ItemDisplayContext.FIXED, this.mc.player);
      renderState.submit(poseStack, event.getCollector(), light, OverlayTexture.NO_OVERLAY, 0);
      poseStack.popPose();
   }

   private void createTrainingMap() {
      BlockPos playerPos = this.mc.player.blockPosition();
      Vec3 facing = this.horizontalLook();
      if (Math.abs(facing.x) > Math.abs(facing.z)) {
         this.mapForwardX = Math.copySign((double)1.0F, facing.x);
         this.mapForwardZ = (double)0.0F;
      } else {
         this.mapForwardX = (double)0.0F;
         this.mapForwardZ = Math.copySign((double)1.0F, facing.z);
      }

      this.mapRightX = -this.mapForwardZ;
      this.mapRightZ = this.mapForwardX;
      this.mapStartX = this.mc.player.getX() + this.mapForwardX * (double)2.0F;
      this.mapStartZ = this.mc.player.getZ() + this.mapForwardZ * (double)2.0F;
      this.createTrainingMapAt(playerPos.getY());
   }

   private void createTrainingMapAt(int floorY) {
      this.mapMinX = 0;
      this.mapMaxX = 14;
      this.mapMinZ = 0;
      this.mapMaxZ = 24;
      this.mapFloorY = floorY;
      Vec3 mapCenter = this.mapPoint((double)7.5F, (double)12.5F);
      this.mapCenterX = mapCenter.x;
      this.mapCenterZ = mapCenter.z;
      this.mapAnimationStartedNanos = System.nanoTime();
      this.covers.clear();
      int fieldCenterX = 7;
      this.addSpawnCover(fieldCenterX, 2);
      this.addSpawnCover(fieldCenterX, this.mapMaxZ - 2);
      ThreadLocalRandom random = ThreadLocalRandom.current();

      for(int attempt = 0; attempt < 80 && this.covers.size() < 9; ++attempt) {
         int width = random.nextInt(1, 4);
         int length = random.nextInt(1, 3);
         int x = random.nextInt(this.mapMinX + 1, this.mapMaxX - width + 1);
         int z = random.nextInt(this.mapMinZ + 4, this.mapMaxZ - length + 1);
         int height = random.nextInt(1, 3);
         AABB box = this.mapBox((double)x + 0.08, (double)z + 0.08, (double)(x + width) - 0.08, (double)(z + length) - 0.08, (double)this.mapFloorY, (double)(this.mapFloorY + height));
         if (!this.covers.stream().anyMatch((existing) -> existing.box().inflate(0.8).intersects(box))) {
            BlockPos position = this.mapBlockPos((double)(x + Math.max(0, width / 2)) + (double)0.5F, (double)(z + Math.max(0, length / 2)) + (double)0.5F);
            ItemStack item = random.nextBoolean() ? new ItemStack(Items.CHEST) : new ItemStack(Items.BARREL);
            this.covers.add(new Cover(box, position, item));
         }
      }

   }

   private void addSpawnCover(int centerX, int z) {
      AABB box = this.mapBox((double)centerX - (double)1.0F, (double)z - (double)0.25F, (double)centerX + (double)1.0F, (double)z + (double)0.25F, (double)this.mapFloorY, (double)this.mapFloorY + (double)2.0F);
      this.covers.add(new Cover(box, this.mapBlockPos((double)centerX, (double)z), new ItemStack(Items.CHEST)));
   }

   private Vec3 mapPoint(double localX, double localZ) {
      return new Vec3(this.mapStartX + this.mapRightX * (localX - (double)7.5F) + this.mapForwardX * localZ, (double)this.mapFloorY, this.mapStartZ + this.mapRightZ * (localX - (double)7.5F) + this.mapForwardZ * localZ);
   }

   private BlockPos mapBlockPos(double localX, double localZ) {
      Vec3 point = this.mapPoint(localX, localZ);
      return BlockPos.containing(point.x, (double)this.mapFloorY, point.z);
   }

   private AABB mapBox(double minLocalX, double minLocalZ, double maxLocalX, double maxLocalZ, double minY, double maxY) {
      Vec3 a = this.mapPoint(minLocalX, minLocalZ);
      Vec3 b = this.mapPoint(minLocalX, maxLocalZ);
      Vec3 c = this.mapPoint(maxLocalX, minLocalZ);
      Vec3 d = this.mapPoint(maxLocalX, maxLocalZ);
      return new AABB(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)), minY, Math.min(Math.min(a.z, b.z), Math.min(c.z, d.z)), Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)), maxY, Math.max(Math.max(a.z, b.z), Math.max(c.z, d.z)));
   }

   private void drawTrainingMap(PoseStack poseStack) {
      if (this.mapFloorY != Integer.MIN_VALUE) {
         AABB revealed = this.revealedMapBox();
         AABB floor = new AABB(revealed.minX, (double)this.mapFloorY - 0.04, revealed.minZ, revealed.maxX, (double)this.mapFloorY, revealed.maxZ);
         Render3DUtils.drawFilledBox(floor, 1429881418);
         Render3DUtils.drawOutlineBox(poseStack, new AABB(revealed.minX, (double)this.mapFloorY - 0.03, revealed.minZ, revealed.maxX, (double)this.mapFloorY + 0.04, revealed.maxZ), -1195916083, 1.5F);
         for(int index = 0; index < this.covers.size(); ++index) {
            Cover cover = (Cover)this.covers.get(index);
            if (this.isMapAreaRevealed(cover.box().getCenter())) {
               AABB risen = this.risenCoverBox(cover, index);
               if (!(risen.maxY <= risen.minY)) {
                  Render3DUtils.drawFilledBox(risen, -1200009937);
                  Render3DUtils.drawOutlineBox(poseStack, risen, -2710420, 1.4F);
               }
            }
         }

      }
   }

   private float mapAnimationLinear() {
      if (this.mapAnimationStartedNanos == 0L) {
         return 1.0F;
      } else {
         float progress = Mth.clamp((float)(System.nanoTime() - this.mapAnimationStartedNanos) / 1.25E9F, 0.0F, 1.0F);
         return smoothStep(progress);
      }
   }

   private AABB revealedMapBox() {
      float linear = this.mapAnimationLinear();
      float lineProgress = Mth.clamp(linear / 0.26F, 0.0F, 1.0F);
      float planeProgress = Mth.clamp((linear - 0.26F) / 0.74F, 0.0F, 1.0F);
      double widthProgress = Math.max(0.025, (double)smoothStep(lineProgress));
      double lengthProgress = Math.max(0.025, (double)smoothStep(planeProgress));
      double halfWidth = (double)7.5F * widthProgress;
      double halfLength = (double)12.5F * lengthProgress;
      return this.mapBox((double)7.5F - halfWidth, (double)12.5F - halfLength, (double)7.5F + halfWidth, (double)12.5F + halfLength, (double)this.mapFloorY, (double)this.mapFloorY + (double)1.0F);
   }

   private boolean isMapAreaRevealed(Vec3 point) {
      return this.revealedMapBox().contains(point.x, (double)this.mapFloorY, point.z);
   }

   private float coverRiseProgress(int index) {
      float start = 0.28F + (float)Math.min(index, 8) * 0.055F;
      return smoothStep(Mth.clamp((this.mapAnimationLinear() - start) / 0.32F, 0.0F, 1.0F));
   }

   private AABB risenCoverBox(Cover cover, int index) {
      double height = cover.box().maxY - cover.box().minY;
      double bottom = (double)this.mapFloorY + height * ((double)1.0F - (double)this.coverRiseProgress(index));
      return new AABB(cover.box().minX, bottom, cover.box().minZ, cover.box().maxX, cover.box().maxY, cover.box().maxZ);
   }

   private double coverRiseOffset(Cover cover) {
      int index = this.covers.indexOf(cover);
      return (double)-1.25F * ((double)1.0F - (double)this.coverRiseProgress(Math.max(0, index)));
   }

   private void spawnRandomTargets() {
      if (this.noPlayer() || !this.practiceRange.get() || this.mapFloorY == Integer.MIN_VALUE) {
         this.targets.clear();
         return;
      }
      if (!this.targets.isEmpty()) {
         return;
      }

      double range = this.targetSpawnRange.get().doubleValue();
      double rangeSqr = range * range;
      Vec3 playerPosition = this.mc.player.position();
      List<BlockPos> candidates = new ArrayList();
      for (int x = this.mapMinX + 1; x < this.mapMaxX; ++x) {
         for (int z = this.mapMinZ + 1; z < this.mapMaxZ; ++z) {
            BlockPos candidate = this.mapBlockPos((double)x + 0.5, (double)z + 0.5);
            Vec3 center = Vec3.atCenterOf(candidate);
            if (center.distanceToSqr(playerPosition) <= rangeSqr
                    && !this.isCoverPosition(candidate)
                    && this.targetCanOccupy(center, this.targetMode.is(MCS.TargetMode.HUMANOID))) {
               candidates.add(candidate);
            }
         }
      }

      int wanted = this.targetCount.get();
      for (int index = 0; index < wanted; ++index) {
         BlockPos candidate = candidates.isEmpty()
                 ? this.mapBlockPos(7.5, Math.min(this.mapMaxZ - 2.5, Math.max(3.5, 4.5 + index * 2.0)))
                 : candidates.remove(ThreadLocalRandom.current().nextInt(candidates.size()));
         Vec3 position = new Vec3(candidate.getX() + 0.5, this.mapFloorY, candidate.getZ() + 0.5);
         this.targets.add(new Target(position, this.targetFacingYaw(position)));
         if (candidates.isEmpty() && index + 1 < wanted) {
            break;
         }
      }
   }

   private void updateTargets() {
      if (!this.practiceRange.get()) {
         return;
      }
      ++this.targetMotionTicks;
      for (Target target : this.targets) {
         float facingYaw = this.targetFacingYaw(target.position);
         Vec3 current = target.position;
         Vec3 desired = target.spawnPosition;
         if (this.movingTargets.get()) {
            double time = this.targetMotionTicks * 0.055 + target.phase;
            double amplitude = Math.min(2.8, Math.max(0.75, this.targetSpawnRange.get() * 0.2));
            if (this.moveVertical.get()) {
               desired = desired.add(0.0, Math.sin(time) * Math.min(1.25, amplitude * 0.55), 0.0);
            }
            if (this.moveHorizontal.get()) {
               desired = desired.add(this.mapRightX * Math.sin(time * 0.83) * amplitude,
                       0.0, this.mapRightZ * Math.sin(time * 0.83) * amplitude);
            }
            if (this.moveDepth.get()) {
               desired = desired.add(this.mapForwardX * Math.sin(time * 0.71 + 1.2) * amplitude,
                       0.0, this.mapForwardZ * Math.sin(time * 0.71 + 1.2) * amplitude);
            }
            double maxStep = this.targetSpeed.get() / 20.0;
            Vec3 delta = desired.subtract(current);
            double distance = delta.length();
            if (distance > maxStep && distance > 1.0E-6) {
               desired = current.add(delta.scale(maxStep / distance));
            }
         }

         TargetRegion[] regions = this.targetMode.is(MCS.TargetMode.HUMANOID)
                 ? createHumanoidRegions(desired, facingYaw) : null;
         AABB bounds = this.targetBounds(desired, regions);
         if (this.targetCanOccupy(bounds, desired)) {
            target.position = desired;
            target.blockPos = BlockPos.containing(desired.x, desired.y, desired.z);
            target.facingYaw = facingYaw;
            if (regions != null) {
               target.humanoidRegions = regions;
            }
         }
      }
   }

   private boolean targetCanOccupy(Vec3 position, boolean humanoid) {
      return this.targetCanOccupy(this.targetBounds(position,
              humanoid ? createHumanoidRegions(position, this.targetFacingYaw(position)) : null), position);
   }

   private boolean targetCanOccupy(AABB bounds, Vec3 position) {
      if (this.mc.level == null || !this.mc.level.noCollision(this.mc.player, bounds)) {
         return false;
      }
      if (this.mapFloorY != Integer.MIN_VALUE) {
         AABB arena = this.mapBox(0.65, 0.65, this.mapMaxX - 0.65, this.mapMaxZ - 0.65,
                 this.mapFloorY, this.mapFloorY + 3.0);
         if (position.x < arena.minX || position.x > arena.maxX
                 || position.z < arena.minZ || position.z > arena.maxZ) {
            return false;
         }
         if (position.distanceToSqr(this.mc.player.position())
                 > (double)this.targetSpawnRange.get() * this.targetSpawnRange.get()) {
            return false;
         }
      }
      return this.covers.stream().noneMatch(cover -> {
         AABB active = this.activeCoverBox(cover);
         return active != null && active.intersects(bounds);
      });
   }

   private AABB targetBounds(Vec3 position, TargetRegion[] regions) {
      if (regions == null) {
         return new AABB(position.x - 0.5, position.y, position.z - 0.5,
                 position.x + 0.5, position.y + 1.0, position.z + 0.5);
      }
      double minX = Double.POSITIVE_INFINITY;
      double minY = Double.POSITIVE_INFINITY;
      double minZ = Double.POSITIVE_INFINITY;
      double maxX = Double.NEGATIVE_INFINITY;
      double maxY = Double.NEGATIVE_INFINITY;
      double maxZ = Double.NEGATIVE_INFINITY;
      for (TargetRegion region : regions) {
         minX = Math.min(minX, region.box.minX);
         minY = Math.min(minY, region.box.minY);
         minZ = Math.min(minZ, region.box.minZ);
         maxX = Math.max(maxX, region.box.maxX);
         maxY = Math.max(maxY, region.box.maxY);
         maxZ = Math.max(maxZ, region.box.maxZ);
      }
      return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
   }

   private float targetFacingYaw(Vec3 position) {
      return (float)(Math.atan2(this.mc.player.getZ() - position.z,
              this.mc.player.getX() - position.x) * 180.0 / Math.PI) - 90.0F;
   }

   private boolean isCoverPosition(BlockPos position) {
      AABB targetBox = (new AABB(position)).inflate((double)0.25F);
      return this.covers.stream().anyMatch((cover) -> cover.box().intersects(targetBox));
   }

   public boolean isDuelSession() {
      return false;
   }

   public int duelHealth() {
      return 100;
   }

   private Vec3 horizontalLook() {
      Vec3 look = this.mc.player.getLookAngle();
      double length = Math.sqrt(look.x * look.x + look.z * look.z);
      return length < 1.0E-5 ? new Vec3((double)0.0F, (double)0.0F, (double)1.0F) : new Vec3(look.x / length, (double)0.0F, look.z / length);
   }

   public static enum Weapon {
      RIFLE(0, "ASSAULT RIFLE", 30, 105000000L, 2220000000L, MCS.RIFLE_FIRE_SOUND, MCS.RIFLE_RELOAD_SOUND),
      PISTOL(1, "PISTOL", 13, 160000000L, 2300000000L, MCS.RIFLE_FIRE_SOUND, MCS.PISTOL_RELOAD_SOUND),
      NONE(-1, "", 0, 0L, 0L, (Identifier)null, (Identifier)null);

      private final int slot;
      private final String displayName;
      private final int magazineCapacity;
      private final long fireIntervalNanos;
      private final long reloadDurationNanos;
      private final Identifier fireSound;
      private final Identifier reloadSound;

      private Weapon(int slot, String displayName, int magazineCapacity, long fireIntervalNanos, long reloadDurationNanos, Identifier fireSound, Identifier reloadSound) {
         this.slot = slot;
         this.displayName = displayName;
         this.magazineCapacity = magazineCapacity;
         this.fireIntervalNanos = fireIntervalNanos;
         this.reloadDurationNanos = reloadDurationNanos;
         this.fireSound = fireSound;
         this.reloadSound = reloadSound;
      }
   }

   public static enum TargetMode {
      TARGET,
      HUMANOID;
   }

   public static enum HitSound {
      NONE,
      HITMARKER,
      PLING,
      CRITICAL,
      AMETHYST;
   }

   public static enum TrainingAmmo {
      NORMAL,
      INFINITE;
   }

   private static final class Target {
      private BlockPos blockPos;
      private Vec3 position;
      private final Vec3 spawnPosition;
      private TargetRegion[] humanoidRegions;
      private float facingYaw;
      private final double phase;
      private int health = 100;

      private Target(Vec3 position, float facingYaw) {
         this.position = position;
         this.spawnPosition = position;
         this.blockPos = BlockPos.containing(position.x, position.y, position.z);
         this.facingYaw = facingYaw;
         this.phase = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
         this.humanoidRegions = MCS.createHumanoidRegions(position, facingYaw);
      }
   }

   private static record Cover(AABB box, BlockPos position, ItemStack item) {
   }

   private static final class HitMarker {
      private final Target target;
      private final AABB box;
      private int ticks;

      private HitMarker(Target target, AABB box, int ticks) {
         this.target = target;
         this.box = box;
         this.ticks = ticks;
      }
   }

   private static record TargetHit(Target target, TargetRegion region) {
   }

   private static enum BodyPart {
      TARGET(100),
      HEAD(33),
      TORSO(25),
      LIMB(15);

      private final int pistolDamage;

      private BodyPart(int pistolDamage) {
         this.pistolDamage = pistolDamage;
      }
   }

   private static record TargetRegion(AABB box, BodyPart part) {
   }

   private static final class AnimatedText {
      private static final long DURATION_NANOS = 180000000L;
      private String previous = "";
      private String current = "";
      private long changedNanos;

      private void reset(String text) {
         this.previous = "";
         this.current = text == null ? "" : text;
         this.changedNanos = 0L;
      }

      private void set(String text) {
         String next = text == null ? "" : text;
         if (!next.equals(this.current)) {
            this.previous = this.current;
            this.current = next;
            this.changedNanos = System.nanoTime();
         }
      }

      private float progress() {
         if (this.changedNanos == 0L) {
            return 1.0F;
         } else {
            float linear = Mth.clamp((float)(System.nanoTime() - this.changedNanos) / 1.8E8F, 0.0F, 1.0F);
            return linear * linear * (3.0F - 2.0F * linear);
         }
      }
   }

   private static final class Shot {
      private Vec3 collisionPosition;
      private Vec3 renderPosition;
      private final Vec3 renderStart;
      private Vec3 velocity;
      private final Weapon weapon;
      private final boolean remote;
      private final long sequence;
      private int age;
      private boolean terminal;

      private Shot(Vec3 collisionPosition, Vec3 renderPosition, Vec3 velocity, Weapon weapon, boolean remote, long sequence) {
         this.collisionPosition = collisionPosition;
         this.renderPosition = renderPosition;
         this.renderStart = renderPosition;
         this.velocity = velocity;
         this.weapon = weapon;
         this.remote = remote;
         this.sequence = sequence;
      }
   }
}
