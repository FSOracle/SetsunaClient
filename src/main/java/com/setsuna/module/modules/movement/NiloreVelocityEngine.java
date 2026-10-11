package com.setsuna.module.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.mixin.DeltaTrackerTimerAccessor;
import com.setsuna.mixin.KeyMappingAccessor;
import com.setsuna.module.modules.combat.Backtrack;
import com.setsuna.module.modules.combat.KillAura;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.player.ChatUtils;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.Rot2f;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Minecraft 26.1.2 adapter for Nilore AntiKB's Jump Reset, Mix and NoXZ modes. */
final class NiloreVelocityEngine {

    enum AntiKbMode {
        JumpReset,
        Mix,
        NoXZ
    }

    private enum JumpPhase {
        IDLE,
        AIR,
        GROUND
    }

    private static final Minecraft mc = Minecraft.getInstance();

    private final Velocity parent;
    private final Set<Setting<?>> settings = Collections.newSetFromMap(new IdentityHashMap<>());
    private final EnumSetting<AntiKbMode> mode;
    private final BooleanSetting rotate;
    private final BooleanSetting tryAttack;
    private final BooleanSetting movementOverride;
    private final BooleanSetting followDirection;
    private final IntSetting rotateTicks;
    private final BooleanSetting autoAttackCount;
    private final IntSetting attackAmount;
    private final BooleanSetting instantAttack;
    private final BooleanSetting sprintStateCheck;
    private final BooleanSetting debugLog;

    private AntiKbMode runningMode;
    private Rot2f rotation;
    private float originalMsPerTick = Float.NaN;

    // Jump Reset state
    private final Queue<Packet<?>> jumpPacketQueue = new ConcurrentLinkedQueue<>();
    private Packet<?> jumpPendingPacket;
    private boolean jumpSuspending;
    private int jumpDelayTicks;
    private JumpPhase jumpPhase = JumpPhase.IDLE;
    private int jumpTicks;
    private int rotationHeldTicks;
    private Rot2f jumpTargetRotation;
    private ClientboundSetEntityMotionPacket jumpKnockbackPacket;

    // Mix state
    private final Queue<Packet<?>> mixPacketQueue = new ConcurrentLinkedQueue<>();
    private boolean mixShouldAttack;
    private boolean mixSuspending;
    private int mixAirTicks;
    private ClientboundSetEntityMotionPacket mixKnockbackPacket;
    private int mixSprintTick = -1;

    // NoXZ state
    private final Queue<Packet<?>> noXzPacketQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Packet<?>> noXzMovePacketQueue = new ConcurrentLinkedQueue<>();
    private boolean noXzFlushing;
    private boolean noXzSuspending;
    private boolean noXzShouldFlushMotion;
    private ClientboundSetEntityMotionPacket noXzKnockbackPacket;
    private int noXzSuspendTicks;
    private int noXzFlagCooldown;
    private int noXzHitCounter;
    private boolean noXzShouldJump;
    private int noXzSprintBoostCounter;
    private Entity noXzAttackTarget;
    private int noXzAttacksRemaining;
    private int noXzAttackCooldown;
    private boolean noXzAttacking;
    private int noXzAttackCount;
    private float noXzInstantAttackProgress;
    private boolean noXzInstantAttacking;

    NiloreVelocityEngine(Velocity parent) {
        this.parent = parent;
        mode = setting(new EnumSetting<>("Heypixel/Mode", AntiKbMode.NoXZ).displayAs("Mode"));
        rotate = setting(new BooleanSetting("Heypixel/Rotate", false)
                .visibleWhen(() -> mode.is(AntiKbMode.JumpReset) || mode.is(AntiKbMode.Mix))
                .displayAs("Rotate"));
        tryAttack = setting(new BooleanSetting("Heypixel/Try Attack", false)
                .visibleWhen(() -> mode.is(AntiKbMode.Mix)).displayAs("Try Attack"));
        movementOverride = setting(new BooleanSetting("Heypixel/Movement Override", false)
                .visibleWhen(() -> mode.is(AntiKbMode.Mix)).displayAs("Movement Override"));
        followDirection = setting(new BooleanSetting("Heypixel/Follow Direction", false)
                .visibleWhen(() -> mode.is(AntiKbMode.JumpReset)).displayAs("Follow Direction"));
        rotateTicks = setting(new IntSetting("Heypixel/Rotate Ticks", 12, 3, 20, 1)
                .visibleWhen(() -> mode.is(AntiKbMode.JumpReset)
                        && (rotate.get() || followDirection.get()))
                .displayAs("Rotate Ticks"));
        autoAttackCount = setting(new BooleanSetting("Heypixel/Auto Attack Count", true)
                .visibleWhen(() -> mode.is(AntiKbMode.NoXZ)).displayAs("Auto Attack Count"));
        attackAmount = setting(new IntSetting("Heypixel/Attack Amount", 5, 1, 20, 1)
                .visibleWhen(() -> mode.is(AntiKbMode.NoXZ) && !autoAttackCount.get())
                .displayAs("Attack Amount"));
        instantAttack = setting(new BooleanSetting("Heypixel/Instant Attack", false)
                .visibleWhen(() -> mode.is(AntiKbMode.NoXZ)).displayAs("Instant Attack"));
        sprintStateCheck = setting(new BooleanSetting("Heypixel/Sprint State Check", true)
                .visibleWhen(() -> mode.is(AntiKbMode.NoXZ)).displayAs("Sprint State Check"));
        debugLog = setting(new BooleanSetting("Heypixel/Debug Log", false).displayAs("Debug Log"));
    }

    private <T extends Setting<?>> T setting(T setting) {
        settings.add(setting);
        setting.andVisibleWhen(parent::isNiloreMode);
        return parent.registerNiloreSetting(setting);
    }

    boolean owns(Setting<?> setting) {
        return settings.contains(setting);
    }

    String info() {
        return switch (mode.get()) {
            case JumpReset -> "Jump Reset";
            case Mix -> "Mix";
            case NoXZ -> "NoXZ";
        };
    }

    void enable() {
        resetAll(false);
        runningMode = mode.get();
    }

    void disable() {
        resetAll(true);
        runningMode = null;
    }

    int attackReduction() {
        return runningMode == AntiKbMode.NoXZ && mode.is(AntiKbMode.NoXZ) && noXzAttacking
                ? attackAmount.get() : 0;
    }

    void onPacketReceive(PacketEvent.Receive event) {
        if (!prepare()) {
            return;
        }
        switch (runningMode) {
            case JumpReset -> receiveJumpReset(event);
            case Mix -> receiveMix(event);
            case NoXZ -> receiveNoXz(event);
        }
    }

    void onPacketSend(PacketEvent.Send event) {
        if (!prepare() || runningMode != AntiKbMode.NoXZ || !noXzSuspending || noXzFlushing) {
            return;
        }
        if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
            noXzMovePacketQueue.add(event.getPacket());
            event.setCancelled(true);
        }
    }

    void tick() {
        if (!prepare()) {
            return;
        }
        if (noXzShouldFlushMotion) {
            flushInbound(noXzPacketQueue);
            noXzShouldFlushMotion = false;
        }
        switch (runningMode) {
            case JumpReset -> tickJumpReset();
            case Mix -> tickMix();
            case NoXZ -> tickNoXz();
        }
        applyRotation();
    }

    void tickPost() {
        if (runningMode == AntiKbMode.Mix && mc.player != null) {
            mixAirTicks = mc.player.onGround() ? 0 : mixAirTicks + 1;
        }
    }

    void onKeyboardInput(KeyboardInputEvent event) {
        if (!prepare()) {
            return;
        }
        switch (runningMode) {
            case JumpReset -> inputJumpReset(event);
            case Mix -> inputMix(event);
            case NoXZ -> inputNoXz(event);
        }
    }

    private boolean prepare() {
        if (mc.player == null || mc.level == null) {
            resetAll(true);
            return false;
        }
        if (runningMode != mode.get()) {
            resetAll(true);
            runningMode = mode.get();
        }
        if (Scaffold.INSTANCE.isEnabled()) {
            clearRotation();
            restoreTimerSpeed();
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Jump Reset

    private void receiveJumpReset(PacketEvent.Receive event) {
        if (isJumpResetSuspendedByOtherModule()) {
            if (jumpSuspending) {
                flushJumpQueue(false);
            }
            resetJumpReset();
            return;
        }
        Packet<?> packet = event.getPacket();
        if (jumpSuspending
                && !(packet instanceof ClientboundSystemChatPacket)
                && !(packet instanceof ClientboundSetTimePacket)) {
            event.setCancelled(true);
            jumpPacketQueue.add(packet);
            return;
        }
        if (!(packet instanceof ClientboundSetEntityMotionPacket motion)
                || motion.id() != mc.player.getId()) {
            return;
        }

        jumpKnockbackPacket = motion;
        boolean wantRotate = rotate.get() || followDirection.get();
        Rot2f kbRotation = wantRotate
                ? new Rot2f(knockbackYaw(motion.movement()), mc.player.getXRot())
                : null;
        jumpSuspending = true;
        jumpPendingPacket = packet;
        jumpPacketQueue.add(packet);
        event.setCancelled(true);
        if (!mc.player.onGround()) {
            if (kbRotation != null) {
                rotation = kbRotation;
                rotationHeldTicks = 0;
            }
            jumpPhase = JumpPhase.AIR;
            jumpDelayTicks = 20;
        } else {
            jumpTargetRotation = kbRotation;
            jumpPhase = JumpPhase.GROUND;
            jumpDelayTicks = 10;
        }
    }

    private void tickJumpReset() {
        if (isJumpResetSuspendedByOtherModule()) {
            if (jumpSuspending) {
                flushJumpQueue(false);
            }
            resetJumpReset();
            return;
        }
        if (jumpSuspending) {
            if (jumpPhase == JumpPhase.AIR) {
                if (mc.player.onGround() || jumpDelayTicks-- <= 0) {
                    flushJumpQueue(false);
                    resetJumpResetRuntime();
                }
            } else if (jumpPhase == JumpPhase.GROUND && jumpDelayTicks-- <= 0) {
                flushJumpQueue(false);
                if (jumpTargetRotation != null) {
                    rotation = jumpTargetRotation;
                    rotationHeldTicks = 0;
                    jumpTargetRotation = null;
                }
                resetJumpResetRuntime();
                jumpTicks = 1;
            }
        }
        if (rotation != null) {
            rotationHeldTicks++;
        }
        if (mc.player.hurtTime == 0
                || rotationHeldTicks > rotateTicks.get()
                || !rotate.get() && !followDirection.get()) {
            clearRotation();
            jumpKnockbackPacket = null;
            rotationHeldTicks = 0;
        }
    }

    private void inputJumpReset(KeyboardInputEvent event) {
        if (followDirection.get() && rotation != null) {
            event.setForward(1.0F);
            event.setStrafe(0.0F);
        }
        if (jumpTicks > 0) {
            event.setJump(true);
            jumpTicks--;
        }
    }

    private boolean isJumpResetSuspendedByOtherModule() {
        return Backtrack.INSTANCE.isLagging();
    }

    private void flushJumpQueue(boolean dropPending) {
        if (dropPending && jumpPacketQueue.peek() == jumpPendingPacket) {
            jumpPacketQueue.poll();
        }
        flushInbound(jumpPacketQueue);
    }

    private void resetJumpReset() {
        clearRotation();
        jumpKnockbackPacket = null;
        rotationHeldTicks = 0;
        resetJumpResetRuntime();
        jumpPacketQueue.clear();
    }

    private void resetJumpResetRuntime() {
        jumpSuspending = false;
        jumpDelayTicks = 0;
        jumpPendingPacket = null;
        jumpPhase = JumpPhase.IDLE;
        jumpTicks = 0;
    }

    // ---------------------------------------------------------------------
    // Mix

    private void receiveMix(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        boolean inWeb = mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB);
        if (inWeb && !mc.player.isInWater() && !mc.player.isUnderWater()) {
            clearRotation();
            mixKnockbackPacket = null;
            flushInbound(mixPacketQueue);
            mixSuspending = false;
            log("Ignore: Player in web or liquid!");
            return;
        }

        if (packet instanceof ClientboundSetEntityMotionPacket motion
                && motion.id() == mc.player.getId()) {
            clearRotation();
            mixKnockbackPacket = null;
            if (motion.movement().y > 0.0D) {
                mixSprintTick = 0;
                if (KillAura.INSTANCE.isEnabled()
                        && KillAura.INSTANCE.target != null
                        && tryAttack.get()) {
                    mixShouldAttack = true;
                } else if (rotate.get()) {
                    rotation = new Rot2f(knockbackYaw(motion.movement()), mc.player.getXRot());
                }
                event.setCancelled(true);
                mixPacketQueue.add(packet);
                mixSuspending = true;
                mixKnockbackPacket = motion;
            }
        }
        if (!mixSuspending) {
            return;
        }
        if (packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundPingPacket
                || packet instanceof ClientboundTeleportEntityPacket) {
            mixPacketQueue.add(packet);
            event.setCancelled(true);
        }
        if (packet instanceof ClientboundPlayerPositionPacket) {
            log("Flag Detected");
            resetMix(true);
        }
    }

    private void tickMix() {
        if (mc.player.onGround() || mixAirTicks >= 24) {
            flushInbound(mixPacketQueue);
            mixSuspending = false;
            mixShouldAttack = false;
            clearRotation();
        }
        if (mixKnockbackPacket == null) {
            return;
        }
        if (mixShouldAttack
                && mixKnockbackPacket.movement().y > 0.0D
                && mc.player.hurtTime > 0
                && KillAura.INSTANCE.isEnabled()
                && KillAura.INSTANCE.target != null) {
            mixShouldAttack = false;
            for (int i = 0; i < 2; i++) {
                mc.player.setSprinting(false);
                mc.gameMode.attack(mc.player, KillAura.INSTANCE.target);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }
        if (mixSprintTick >= 0) {
            mixSprintTick++;
            if (mixSprintTick >= 10) {
                mixSprintTick = -1;
            }
        }
    }

    private void inputMix(KeyboardInputEvent event) {
        if (mixKnockbackPacket == null) {
            return;
        }
        if (!movementOverride.get()) {
            if (mc.player.hurtTime > 6 && !physicalDown(mc.options.keyJump)) {
                event.setJump(true);
            }
            return;
        }
        if (mixSprintTick >= 1 && mixSprintTick <= 2 && mc.player.onGround()) {
            event.setJump(true);
        }
        if (mixSprintTick >= 1 && mixSprintTick <= 3) {
            float yawDelta = Mth.wrapDegrees(
                    knockbackYaw(mixKnockbackPacket.movement()) - mc.player.getYRot());
            double yawRadians = Math.toRadians(yawDelta);
            double sin = Math.sin(yawRadians);
            double cos = Math.cos(yawRadians);
            event.setForward(cos > 0.5D ? 1.0F : cos < -0.5D ? -1.0F : 0.0F);
            event.setStrafe(sin > 0.5D ? -1.0F : sin < -0.5D ? 1.0F : 0.0F);
        }
    }

    private void resetMix(boolean flush) {
        if (flush) {
            flushInbound(mixPacketQueue);
        } else {
            mixPacketQueue.clear();
        }
        mixKnockbackPacket = null;
        mixShouldAttack = false;
        mixSuspending = false;
        mixAirTicks = 0;
        mixSprintTick = -1;
        clearRotation();
    }

    // ---------------------------------------------------------------------
    // NoXZ

    private void receiveNoXz(PacketEvent.Receive event) {
        if (noXzFlushing || shouldIgnoreNoXz()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (noXzSuspending) {
                releaseNoXz();
            }
            resetNoXzSuspension();
            log("Flag Detected");
            noXzFlagCooldown = 2;
        }
        if (noXzFlagCooldown != 0) {
            return;
        }
        if (noXzSuspending) {
            if (packet instanceof ClientboundMoveEntityPacket
                    || packet instanceof ClientboundPingPacket
                    || packet instanceof ClientboundTeleportEntityPacket) {
                noXzPacketQueue.add(packet);
                event.setCancelled(true);
                return;
            }
            if (!isAllowedNoXzPacket(packet)) {
                noXzPacketQueue.add(packet);
                event.setCancelled(true);
            }
            return;
        }
        if (!(packet instanceof ClientboundSetEntityMotionPacket motion)
                || motion.id() != mc.player.getId()) {
            return;
        }
        Vec3 velocity = motion.movement();
        if (Math.abs(velocity.x) > 0.01D || Math.abs(velocity.z) > 0.01D) {
            noXzHitCounter = 1;
        }
        if (velocity.y <= 0.0D) {
            return;
        }
        noXzSprintBoostCounter = noXzSprintBoostCounter % 100 + 100;
        if (noXzSprintBoostCounter >= 100) {
            noXzShouldJump = true;
        }
        Entity target = getAttackTarget();
        boolean canAttack = isValidTarget(target) && mc.player.isSprinting();
        if (!mc.player.onGround()) {
            suspendNoXz(motion, event);
        } else if (canAttack) {
            noXzAttackTarget = target;
            noXzAttacksRemaining = attackCountFor(velocity, autoAttackCount.get(), attackAmount.get());
        } else {
            suspendNoXz(motion, event);
            log("Alink Wait");
        }
    }

    private void suspendNoXz(ClientboundSetEntityMotionPacket motion, PacketEvent.Receive event) {
        noXzSuspending = true;
        noXzSuspendTicks = 0;
        noXzKnockbackPacket = motion;
        event.setCancelled(true);
    }

    private void tickNoXz() {
        if (noXzAttackCooldown > 0 && --noXzAttackCooldown <= 0) {
            noXzAttacking = false;
            noXzAttackCount = 0;
        }
        if (noXzHitCounter > 0 && ++noXzHitCounter > 2) {
            noXzHitCounter = 0;
        }
        if (mc.player.isDeadOrDying() || !mc.player.isAlive() || shouldIgnoreNoXz()) {
            clearNoXzTarget();
            if (noXzSuspending) {
                releaseNoXz();
            }
            if (noXzInstantAttacking) {
                noXzInstantAttacking = false;
                noXzInstantAttackProgress = 0.0F;
                restoreTimerSpeed();
            }
            return;
        }
        if (noXzFlagCooldown > 0) {
            noXzFlagCooldown--;
            clearNoXzTarget();
        }
        if (noXzSuspending) {
            noXzSuspendTicks++;
            boolean instant = instantAttack.get();
            if (instant && noXzInstantAttackProgress < 3.0F) {
                setTimerSpeed(0.5F);
                noXzInstantAttackProgress = Math.min(3.0F, noXzInstantAttackProgress + 0.5F);
            }
            boolean onGround = mc.player.onGround();
            boolean timeout = noXzSuspendTicks >= 12;
            if (!onGround && !timeout) {
                return;
            }
            log(timeout ? "Alink Timeout" : "ground");
            if (instant) {
                setTimerSpeed(1.0F);
            }
            Entity target = getAttackTarget();
            boolean canAttack = isValidTarget(target);
            if (onGround && canAttack && mc.player.isSprinting()) {
                noXzFlushing = true;
                noXzAttackTarget = target;
                noXzAttacksRemaining = attackCountFor(
                        noXzKnockbackPacket == null ? Vec3.ZERO : noXzKnockbackPacket.movement(),
                        autoAttackCount.get(),
                        attackAmount.get());
                sendNoXzMovePackets();
                applyNoXzKnockbackPacket();
                if (instant && noXzInstantAttackProgress > 0.0F) {
                    noXzAttacksRemaining = (int) noXzInstantAttackProgress;
                    scheduleNoXzMotionFlush();
                    noXzSuspending = false;
                    noXzSuspendTicks = 0;
                    noXzFlushing = false;
                    noXzInstantAttacking = true;
                    setTimerSpeed(4.0F);
                } else {
                    doNoXzAttackSequence();
                    scheduleNoXzMotionFlush();
                    noXzSuspending = false;
                    noXzSuspendTicks = 0;
                    noXzFlushing = false;
                }
            } else {
                releaseNoXz();
                if (instant) {
                    noXzInstantAttackProgress = 0.0F;
                }
                if (onGround && mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }
            }
            return;
        }
        if (noXzInstantAttacking) {
            noXzInstantAttackProgress -= 1.0F;
            if (noXzInstantAttackProgress <= 0.0F) {
                noXzInstantAttackProgress = 0.0F;
                noXzInstantAttacking = false;
                restoreTimerSpeed();
                log("done");
            }
        }
        if (noXzAttacksRemaining > 0 && noXzAttackTarget != null) {
            doNoXzAttackSequence();
        }
    }

    private void inputNoXz(KeyboardInputEvent event) {
        if (noXzHitCounter > 0) {
            event.setForward(1.0F);
        }
        if (noXzShouldJump) {
            noXzShouldJump = false;
            if (mc.player.onGround()
                    && mc.player.isSprinting()
                    && !mc.player.hasEffect(MobEffects.JUMP_BOOST)
                    && !shouldIgnoreNoXz()) {
                event.setSprint(true);
            }
        }
    }

    private boolean shouldIgnoreNoXz() {
        if (mc.player == null || mc.level == null) {
            return true;
        }
        if (mc.player.isDeadOrDying()
                || !mc.player.isAlive()
                || mc.player.getHealth() <= 0.0F
                || mc.player.isSpectator()
                || mc.player.getAbilities().flying) {
            return true;
        }
        return mc.player.isInLava()
                || mc.player.isOnFire()
                || mc.player.isInWater()
                || mc.player.onClimbable()
                || mc.player.isSleeping()
                || mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB);
    }

    private Entity getAttackTarget() {
        if (KillAura.INSTANCE.target != null) {
            return KillAura.INSTANCE.target;
        }
        if (mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && living != mc.player
                && living.isAlive()
                && !living.isSpectator()) {
            return living;
        }
        return null;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof LivingEntity living
                && (living.isDeadOrDying() || living.getHealth() <= 0.0F)) {
            return false;
        }
        return aabbDistance(entity) <= 3.7D;
    }

    private double aabbDistance(Entity entity) {
        Vec3 eyes = mc.player.getEyePosition(1.0F);
        AABB box = entity.getBoundingBox();
        double x = Mth.clamp(eyes.x, box.minX, box.maxX);
        double y = Mth.clamp(eyes.y, box.minY, box.maxY);
        double z = Mth.clamp(eyes.z, box.minZ, box.maxZ);
        return eyes.distanceTo(new Vec3(x, y, z));
    }

    private void doNoXzAttackSequence() {
        if (!isValidTarget(noXzAttackTarget)) {
            clearNoXzTarget();
            return;
        }
        noXzAttacking = true;
        noXzAttackCount = noXzAttacksRemaining--;
        noXzAttackCooldown = 2;
        doNoXzAttack(noXzAttackTarget);
        if (noXzAttacksRemaining <= 0) {
            clearNoXzTarget();
            if (instantAttack.get()) {
                log("Attack (" + attackAmount.get() + ")");
            }
        }
    }

    private boolean doNoXzAttack(Entity entity) {
        if (mc.gameMode == null || sprintStateCheck.get() && !mc.player.isSprinting()) {
            log("not sprinting");
            return false;
        }
        boolean wasSprinting = mc.player.isSprinting();
        if (wasSprinting) {
            mc.player.setSprinting(false);
        }
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (wasSprinting) {
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x * 0.6D, velocity.y, velocity.z * 0.6D);
        }
        if (!instantAttack.get()) {
            log("Attack (" + noXzAttacksRemaining + ")");
        }
        return true;
    }

    private void sendNoXzMovePackets() {
        Packet<?> packet;
        while ((packet = noXzMovePacketQueue.poll()) != null) {
            PacketUtils.sendSilently(packet);
        }
    }

    private void applyNoXzKnockbackPacket() {
        if (noXzKnockbackPacket != null) {
            handleInbound(noXzKnockbackPacket);
            noXzKnockbackPacket = null;
        }
    }

    private void scheduleNoXzMotionFlush() {
        noXzShouldFlushMotion = true;
    }

    private boolean isAllowedNoXzPacket(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundPlayerCombatKillPacket
                || packet instanceof ClientboundContainerClosePacket
                || packet instanceof ClientboundHurtAnimationPacket
                || packet instanceof ClientboundSetTitleTextPacket
                || packet instanceof ClientboundSetPlayerTeamPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundAnimatePacket animate
                && animate.getId() != mc.player.getId();
    }

    private void releaseNoXz() {
        noXzFlushing = true;
        sendNoXzMovePackets();
        applyNoXzKnockbackPacket();
        scheduleNoXzMotionFlush();
        noXzFlushing = false;
        noXzSuspending = false;
        noXzSuspendTicks = 0;
        noXzInstantAttackProgress = 0.0F;
        noXzInstantAttacking = false;
        restoreTimerSpeed();
    }

    private void resetNoXz(boolean replay) {
        if (replay && noXzSuspending) {
            releaseNoXz();
            flushInbound(noXzPacketQueue);
        }
        clearNoXzTarget();
        noXzFlagCooldown = 0;
        noXzShouldJump = false;
        noXzSprintBoostCounter = 0;
        noXzHitCounter = 0;
        resetNoXzSuspension();
        noXzAttackCooldown = 0;
        noXzAttacking = false;
        noXzAttackCount = 0;
    }

    private void resetNoXzSuspension() {
        noXzSuspending = false;
        noXzSuspendTicks = 0;
        noXzKnockbackPacket = null;
        noXzPacketQueue.clear();
        noXzMovePacketQueue.clear();
        noXzFlushing = false;
        noXzInstantAttackProgress = 0.0F;
        noXzInstantAttacking = false;
        noXzShouldFlushMotion = false;
        restoreTimerSpeed();
    }

    private void clearNoXzTarget() {
        noXzAttackTarget = null;
        noXzAttacksRemaining = 0;
    }

    // ---------------------------------------------------------------------

    private void resetAll(boolean replay) {
        if (replay && jumpSuspending) {
            flushJumpQueue(false);
        }
        resetJumpReset();
        resetMix(replay);
        resetNoXz(replay);
        clearRotation();
        restoreTimerSpeed();
    }

    private void applyRotation() {
        if (rotation == null) {
            RotationManager.INSTANCE.releaseSilentRotation(this);
            return;
        }
        RotationManager.INSTANCE.claimSilentRotation(this, rotation, Priority.High);
    }

    private void clearRotation() {
        rotation = null;
        RotationManager.INSTANCE.releaseSilentRotation(this);
    }

    private void flushInbound(Queue<Packet<?>> queue) {
        Packet<?> packet;
        while ((packet = queue.poll()) != null) {
            handleInbound(packet);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void handleInbound(Packet<?> packet) {
        if (mc == null || mc.getConnection() == null) {
            return;
        }
        try {
            ((Packet) packet).handle((PacketListener) mc.getConnection());
        } catch (RuntimeException ignored) {
            // A buffered entity packet may become stale during a world transition.
        }
    }

    private void setTimerSpeed(float speed) {
        if (mc == null) {
            return;
        }
        DeltaTracker tracker = mc.getDeltaTracker();
        if (!(tracker instanceof DeltaTrackerTimerAccessor accessor)) {
            return;
        }
        if (Float.isNaN(originalMsPerTick)) {
            originalMsPerTick = accessor.setsuna$getMsPerTick();
        }
        accessor.setsuna$setMsPerTick(originalMsPerTick / Math.max(0.01F, speed));
    }

    private void restoreTimerSpeed() {
        if (mc == null) {
            originalMsPerTick = Float.NaN;
            return;
        }
        DeltaTracker tracker = mc.getDeltaTracker();
        if (!Float.isNaN(originalMsPerTick) && tracker instanceof DeltaTrackerTimerAccessor accessor) {
            accessor.setsuna$setMsPerTick(originalMsPerTick);
        }
        originalMsPerTick = Float.NaN;
    }

    private boolean physicalDown(KeyMapping mapping) {
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).setsuna$getKey();
        if (key == InputConstants.UNKNOWN) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }

    private void log(String message) {
        if (debugLog.get()) {
            ChatUtils.addChatMessage("[Velocity/Heypixel] " + message);
        }
    }

    static float knockbackYaw(Vec3 movement) {
        return (float) Math.toDegrees(Math.atan2(movement.x, -movement.z));
    }

    static int attackCountFor(Vec3 movement, boolean automatic, int configuredAmount) {
        if (!automatic || movement == null) {
            return configuredAmount;
        }
        double velocity = Math.hypot(movement.x, movement.y);
        if (velocity < 0.125D) {
            return 0;
        }
        if (velocity < 0.25D) {
            return 3;
        }
        if (velocity < 1.25D) {
            return 4;
        }
        return 5;
    }
}
