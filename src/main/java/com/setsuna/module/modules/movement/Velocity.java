package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.event.events.WorldChangeEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.combat.KillAura;
import com.setsuna.module.modules.movement.velocity.Heypixel3Velocity;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.player.MoveUtils;
import com.setsuna.util.player.ChatUtils;
import com.setsuna.util.player.PlayerUtils;
import com.setsuna.util.rotation.RotationUtils;
import com.setsuna.util.timer.TimerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Velocity extends Module {

    public static final Velocity INSTANCE = new Velocity();

    private Velocity() {
        super("Velocity", Category.MOVEMENT);
    }

    private enum Mode {
        Modify,
        Grim,
        Wall,
        None,
        Cancel,
        Legit,
        JumpReset,
        HeypixelLegacy,
        HeypixelLegacy2,
        Hypixel,
        Heypixel
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Cancel)
            .legacyValue("Nilore", Mode.Heypixel));
    private final BooleanSetting serverMotion = add(new BooleanSetting("Server Motion", true).visibleWhen( () -> mode.is(Mode.Cancel)));
    private final BooleanSetting explosion = add(new BooleanSetting("Explosion", true).visibleWhen( () -> mode.is(Mode.Cancel)));
    private final BooleanSetting explosionOnlyBlock = add(new BooleanSetting("Explosion Only Block", false).visibleWhen(
            () -> mode.is(Mode.Cancel) && explosion.get()));
    public final BooleanSetting waterPush = add(new BooleanSetting("No Water Push", true).visibleWhen( () -> !mode.is(Mode.None)));
    public final BooleanSetting entityPush = add(new BooleanSetting("No Entity Push", true).visibleWhen( () -> !mode.is(Mode.None)));
    public final BooleanSetting blockPush = add(new BooleanSetting("No Block Push", true).visibleWhen( () -> !mode.is(Mode.None)));

    private final BooleanSetting cancelAll = add(new BooleanSetting("Cancel All", false).visibleWhen( this::isOpenTrollMode));
    private final IntSetting horizontal = add(new IntSetting("Horizontal", 0, 0, 100, 1).visibleWhen( this::isOpenTrollMode));
    private final IntSetting vertical = add(new IntSetting("Vertical", 0, 0, 100, 1).visibleWhen( this::isOpenTrollMode));
    private final IntSetting lagPause = add(new IntSetting("Lag Pause", 50, 0, 500, 10).visibleWhen(
            () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)));
    private final BooleanSetting ignorePearlLag = add(new BooleanSetting("Ignore Pearl Lag", true).visibleWhen(
            () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)));
    private final IntSetting phaseTime = add(new IntSetting("Phase Time", 250, 0, 1000, 50).visibleWhen(
            () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)));
    private final BooleanSetting noRotation = add(new BooleanSetting("No Rotation", false).visibleWhen(
            () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)));
    private final BooleanSetting flagInWall = add(new BooleanSetting("Flag In Wall", false).visibleWhen(
            () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)));
    private final BooleanSetting whilePushOut = add(new BooleanSetting("While Push Out", false).visibleWhen(
            () -> (mode.is(Mode.Grim) || mode.is(Mode.Wall)) && flagInWall.get()));
    private final BooleanSetting whileLiquid = add(new BooleanSetting("While Liquid", false).visibleWhen( this::isOpenTrollMode));
    private final BooleanSetting fallFlying = add(new BooleanSetting("Fall Flying", false).visibleWhen( this::isOpenTrollMode));
    private final BooleanSetting noFishBob = add(new BooleanSetting("No Fish Bob", true));

    private final BooleanSetting sprintOnly = add(new BooleanSetting("Sprint Only", true).visibleWhen( () -> mode.is(Mode.JumpReset)));
    private final BooleanSetting disableInWater = add(new BooleanSetting("Disable In Water", true).visibleWhen( () -> mode.is(Mode.JumpReset)));
    private final IntSetting jumpDelay = add(new IntSetting("Jump Delay", 0, 0, 10, 1).visibleWhen( () -> mode.is(Mode.JumpReset)));
    private final IntSetting jumpChance = add(new IntSetting("Jump Chance", 100, 1, 100, 1).visibleWhen( () -> mode.is(Mode.JumpReset)));

    private final IntSetting heypixelAttacks = add(new IntSetting("Attack Counts", 4, 1, 20, 1).visibleWhen(
            () -> mode.is(Mode.HeypixelLegacy)));
    private final IntSetting heypixelMaxDelay = add(new IntSetting("Max Alink Time", 5000, 50, 10000, 50).visibleWhen(
            () -> mode.is(Mode.HeypixelLegacy)));
    private final BooleanSetting heypixelJumpReset = add(new BooleanSetting("Jump Reset", false).visibleWhen(
            () -> mode.is(Mode.HeypixelLegacy)));
    private final BooleanSetting heypixelRotations = add(new BooleanSetting("Rotations", false).visibleWhen(
            () -> mode.is(Mode.HeypixelLegacy)));
    private final BooleanSetting heypixelDebug = add(new BooleanSetting("Debug", false).visibleWhen(
            () -> mode.is(Mode.HeypixelLegacy)));
    private final BooleanSetting heypixelDelayTillGround = add(new BooleanSetting("Delay Till Ground", true).visibleWhen(
            () -> mode.is(Mode.HeypixelLegacy)));
    private final IntSetting heypixel2DelayTicks = add(new IntSetting("Delay SPacket Ticks", 5, 1, 60, 1).visibleWhen(
            () -> mode.is(Mode.HeypixelLegacy2)));

    private final NiloreVelocityEngine niloreEngine = new NiloreVelocityEngine(this);

    private boolean jump;
    private int pendingJumpDelay;
    private int tickSinceVelocity = 10;
    private int reduceTicks;
    private boolean hypixelStartSequence;
    private boolean velocityFlag;
    private boolean inWallFlag;
    private final TimerUtils lagTimer = new TimerUtils();
    private final TimerUtils phaseTimer = new TimerUtils();

    private final Queue<Packet<? super ClientPacketListener>> heypixelPackets = new ConcurrentLinkedQueue<>();
    private final Queue<Packet<? super ClientPacketListener>> heypixelAttackBuffer = new ConcurrentLinkedQueue<>();
    private boolean heypixelLag;
    private long heypixelVelocityTime;
    private Player heypixelTarget;
    private HeypixelStage heypixelStage = HeypixelStage.NONE;
    private boolean heypixelCanAttack;
    private int heypixelRemainingAttacks;
    private boolean heypixelJustHurt;
    private boolean heypixelHasPendingMotion;
    private boolean heypixelReducedThisTick;
    private final Heypixel3Velocity heypixel2 = new Heypixel3Velocity(mc);
    private boolean heypixel2RuntimeActive;

    @Override
    protected void onEnable() {
        resetState();
        if (mode.is(Mode.Heypixel)) {
            niloreEngine.enable();
        }
        heypixel2RuntimeActive = mode.is(Mode.HeypixelLegacy2);
        lagTimer.setMs(917813L);
        phaseTimer.setMs(917813L);
    }

    @Override
    protected void onDisable() {
        niloreEngine.disable();
        heypixel2.disable();
        heypixel2RuntimeActive = false;
        releaseHeypixelPackets(true);
        resetState();
    }

    @Override
    public String getInfo() {
        if (mode.is(Mode.None)) return null;
        if (mode.is(Mode.Heypixel)) return "Heypixel/" + niloreEngine.info();
        if (mode.is(Mode.HeypixelLegacy2)) return heypixel2.getSuffix(heypixel2DelayTicks.get());
        if (isOpenTrollMode()) {
            return cancelAll.get() ? mode.get() + ", Cancel" : mode.get() + ", " + horizontal.get() + "%, " + vertical.get() + "%";
        }
        return mode.get().name();
    }

    /** Used by OpenOpal-style KillAura hit select to avoid duplicate attack bursts. */
    public boolean shouldSuppressKillAuraAttack() {
        return isEnabled()
                && mode.is(Mode.HeypixelLegacy)
                && heypixelStage == HeypixelStage.ATTACK;
    }

    /** True while Exhibobo's Hypixel attack-reduction window is active. */
    public boolean isHypixelReducing() {
        return isEnabled()
                && mode.is(Mode.Hypixel)
                && tickSinceVelocity < reduceTicks;
    }

    /** Nilore KillAura lowers APS while the Nilore NoXZ burst is active. */
    public int getNiloreAttackReduction() {
        return isEnabled() && mode.is(Mode.Heypixel) ? niloreEngine.attackReduction() : 0;
    }

    <T extends Setting<?>> T registerNiloreSetting(T setting) {
        if (setting.name().startsWith("Heypixel/")) {
            setting.legacyName("Nilore/" + setting.name().substring("Heypixel/".length()));
        }
        return add(setting);
    }

    boolean isNiloreMode() {
        return mode.is(Mode.Heypixel);
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        syncHeypixel2Runtime();
        if (noPlayer()) return;

        if (event.getPacket() instanceof ClientboundPlayerPositionPacket
                && (!ignorePearlLag.get() || phaseTimer.passedMillis(phaseTime.get()))) {
            lagTimer.reset();
        }

        if (shouldCancelFishBob(event)) {
            event.setCancelled(true);
            return;
        }

        switch (mode.get()) {
            case None -> {
            }
            case Modify, Grim, Wall -> handleOpenTrollVelocity(event);
            case Cancel -> handleCancel(event);
            case Legit -> handleLegit(event);
            case JumpReset -> handleJumpReset(event);
            case HeypixelLegacy -> handleHeypixel(event);
            case HeypixelLegacy2 -> heypixel2.onReceive(event, heypixel2DelayTicks.get());
            case Hypixel -> handleHypixel(event);
            case Heypixel -> niloreEngine.onPacketReceive(event);
        }
    }

    @Listen
    private void onPacketSend(PacketEvent.Send event) {
        if (mode.is(Mode.Heypixel)) {
            niloreEngine.onPacketSend(event);
        }
    }

    @Listen
    private void onTick(TickEvent.Pre event) {
        if (noPlayer()) {
            resetHypixel();
            return;
        }
        if (!mode.is(Mode.Hypixel)) {
            if (hypixelStartSequence || tickSinceVelocity < 10 || reduceTicks != 0) {
                resetHypixel();
            }
            return;
        }
        if (tickSinceVelocity < 10) {
            tickSinceVelocity++;
        }
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        syncHeypixel2Runtime();
        if (noPlayer()) return;

        if (!mode.is(Mode.HeypixelLegacy)
                && (heypixelStage != HeypixelStage.NONE
                || heypixelLag
                || heypixelJustHurt
                || !heypixelPackets.isEmpty()
                || !heypixelAttackBuffer.isEmpty())) {
            releaseHeypixelPackets(true);
            resetHeypixel();
        }

        if (mode.is(Mode.Heypixel)) {
            niloreEngine.tick();
            return;
        }

        if (mode.is(Mode.Grim) || mode.is(Mode.Wall)) {
            tickOpenTrollFlag();
        }

        if (mode.is(Mode.HeypixelLegacy)) {
            tickHeypixel();
        }
        if (mode.is(Mode.HeypixelLegacy2)) {
            heypixel2.tick();
        }

        if (mode.is(Mode.JumpReset) && jump) {
            if (disableInWater.get() && mc.player.isInWater()) {
                resetJumpReset();
            } else if (pendingJumpDelay > 0) {
                pendingJumpDelay--;
            } else {
                boolean hurtWindow = jumpDelay.get() == 0 ? mc.player.hurtTime == 9 : mc.player.hurtTime > 0;
                if (hurtWindow
                        && mc.player.onGround()
                        && !mc.options.keyJump.isDown()
                        && (!sprintOnly.get() || mc.player.isSprinting())
                        && (jumpChance.get() >= 100 || ThreadLocalRandom.current().nextInt(100) < jumpChance.get())) {
                    mc.player.jumpFromGround();
                }
                resetJumpReset();
            }
        }

        if (mode.is(Mode.Hypixel)) {
            tickHypixelReduction();
        }

    }

    @Listen
    private void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (mode.is(Mode.Heypixel)) {
            niloreEngine.tickPost();
        }
    }

    @Listen
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (noPlayer()) return;

        if (mode.is(Mode.Heypixel)) {
            niloreEngine.onKeyboardInput(event);
            return;
        }

        if (mode.is(Mode.Legit) && jump) {
            if (mc.player.onGround() && MoveUtils.isMoving()) {
                event.setJump(true);
            }
            jump = false;
        }

        if (mode.is(Mode.HeypixelLegacy)) {
            handleHeypixelInput(event);
        }
    }

    private void handleOpenTrollVelocity(PacketEvent.Receive event) {
        if (!canModifyVelocity()) return;

        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
            Vec3 movement = packet.movement();
            if (!canProcessGrimWall(movement)) return;

            if (cancelAll.get()) {
                event.setCancelled(true);
            } else {
                event.setPacket(new ClientboundSetEntityMotionPacket(packet.id(), scale(movement)));
            }
            markVelocityFlag(movement);
            return;
        }

        if (event.getPacket() instanceof ClientboundExplodePacket packet) {
            if (cancelAll.get()) {
                event.setPacket(new ClientboundExplodePacket(
                        packet.center(),
                        packet.radius(),
                        packet.blockCount(),
                        Optional.empty(),
                        packet.explosionParticle(),
                        packet.explosionSound(),
                        packet.blockParticles()
                ));
            } else {
                Optional<Vec3> knockback = packet.playerKnockback().map(this::scale);
                event.setPacket(new ClientboundExplodePacket(
                        packet.center(),
                        packet.radius(),
                        packet.blockCount(),
                        knockback,
                        packet.explosionParticle(),
                        packet.explosionSound(),
                        packet.blockParticles()
                ));
            }
            packet.playerKnockback().ifPresent(this::markVelocityFlag);
        }
    }

    private void handleCancel(PacketEvent.Receive event) {
        if (serverMotion.get() && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
                && packet.id() == mc.player.getId()) {
            event.setCancelled(true);
            return;
        }

        if (explosion.get() && event.getPacket() instanceof ClientboundExplodePacket packet
                && (!explosionOnlyBlock.get() || PlayerUtils.isInBlock())) {
            event.setPacket(new ClientboundExplodePacket(
                    packet.center(),
                    packet.radius(),
                    packet.blockCount(),
                    Optional.empty(),
                    packet.explosionParticle(),
                    packet.explosionSound(),
                    packet.blockParticles()
            ));
        }
    }

    private void handleLegit(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
            jump = true;
        }
    }

    private void handleJumpReset(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
            jump = true;
            pendingJumpDelay = jumpDelay.get();
        }
    }

    /**
     * Heypixel keeps the server's velocity packet until the client has a valid
     * attack window, then replays it while applying a small horizontal reduction.
     * Position-sync packets are buffered as well so the delayed packet sequence
     * cannot leave the connection in a half-applied state.
     */
    private void handleHeypixel(PacketEvent.Receive event) {
        int selfId = mc.player.getId();

        if (event.getPacket() instanceof ClientboundHurtAnimationPacket packet && packet.id() == selfId) {
            heypixelJustHurt = true;
        }
        if (event.getPacket() instanceof ClientboundEntityEventPacket packet && mc.level != null) {
            try {
                if (packet.getEntity(mc.level) == mc.player && packet.getEventId() == 2) {
                    heypixelJustHurt = true;
                }
            } catch (RuntimeException ignored) {
                // The entity can disappear while a disconnect/respawn packet is processed.
            }
        }

        if (heypixelStage == HeypixelStage.NONE) {
            if (event.getPacket() instanceof ClientboundExplodePacket) {
                heypixelLag = true;
                heyPixelLog("Explosion flagged");
                return;
            }
            if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
                heypixelLag = true;
                heyPixelLog("Position sync flagged");
                return;
            }
            if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == selfId) {
                if (!heypixelJustHurt) {
                    return;
                }
                heypixelJustHurt = false;
                if (heypixelLag) {
                    heypixelLag = false;
                    heyPixelLog("Skipping delayed velocity after a position sync");
                    return;
                }

                heypixelStage = HeypixelStage.DELAY;
                heypixelVelocityTime = System.currentTimeMillis();
                heypixelPackets.add(asClientPacket(event.getPacket()));
                event.setCancelled(true);
                heyPixelLog("Velocity delayed for " + heypixelMaxDelay.get() + "ms");
            }
            return;
        }

        if (heypixelStage == HeypixelStage.DELAY) {
            Packet<?> incoming = event.getPacket();
            if (incoming instanceof ClientboundExplodePacket
                    || incoming instanceof ClientboundPlayerPositionPacket
                    || incoming instanceof ClientboundPlayerLookAtPacket) {
                heypixelStage = HeypixelStage.LAG;
                heypixelPackets.add(asClientPacket(incoming));
                event.setCancelled(true);
                heyPixelLog("Delayed sequence paused by a position packet");
                return;
            }
            if (incoming instanceof ClientboundDisconnectPacket || incoming instanceof ClientboundRespawnPacket) {
                releaseHeypixelPackets(true);
                return;
            }
            if (incoming instanceof ClientboundPingPacket
                    || incoming instanceof ClientboundSetEntityMotionPacket packet && packet.id() == selfId) {
                heypixelPackets.add(asClientPacket(incoming));
                event.setCancelled(true);
            }
            return;
        }

        if (heypixelStage == HeypixelStage.ATTACK
                && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
                && packet.id() == selfId
                && heypixelJustHurt) {
            heypixelJustHurt = false;
            heypixelAttackBuffer.add(asClientPacket(packet));
            event.setCancelled(true);
            heypixelHasPendingMotion = true;
        }
    }

    private void tickHeypixel() {
        heypixelReducedThisTick = false;

        if (heypixelRotations.get() && heypixelTarget != null) {
            RotationManager.INSTANCE.setRotations(RotationUtils.calculate(heypixelTarget), 10.0);
        }

        if (heypixelStage == HeypixelStage.DELAY) {
            if (heypixelCanAttack) {
                HitResult hitResult = mc.hitResult;
                boolean aimedAtTarget = hitResult instanceof EntityHitResult entityHitResult
                        && entityHitResult.getEntity() == heypixelTarget;
                boolean grounded = !heypixelDelayTillGround.get() || mc.player.onGround();
                if (aimedAtTarget && grounded) {
                    heypixelStage = HeypixelStage.ATTACK;
                    heypixelRemainingAttacks = heypixelAttacks.get();
                    releaseHeypixelPackets(false);
                    heypixelCanAttack = false;
                    heyPixelLog("Velocity released; attacks: " + heypixelRemainingAttacks);
                }
            }

            if (heypixelStage == HeypixelStage.DELAY
                    && System.currentTimeMillis() - heypixelVelocityTime >= getHeypixelDelay()) {
                heypixelStage = HeypixelStage.TIMEOUT;
                heyPixelLog("Velocity delay timed out");
            }
        }

        if (heypixelStage == HeypixelStage.LAG || heypixelStage == HeypixelStage.TIMEOUT) {
            HeypixelStage completedStage = heypixelStage;
            releaseHeypixelPackets(true);
            heyPixelLog("Released packets after " + completedStage.name().toLowerCase(java.util.Locale.ROOT));
        }

        if (heypixelStage == HeypixelStage.ATTACK) {
            // Sprint is deliberately cleared for the attack burst; do not treat
            // that expected state change as a lost target on the next tick.
            if (!isValidHeypixelTarget(heypixelTarget)) {
                heyPixelLog("Attack sequence stopped: target invalid");
                heypixelStage = HeypixelStage.DELAY;
                heypixelVelocityTime = System.currentTimeMillis();
                heypixelCanAttack = false;
                heypixelTarget = null;
                heypixelRemainingAttacks = 0;
                return;
            }

            if (heypixelHasPendingMotion) {
                releaseHeypixelAttackBuffer();
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                heypixelHasPendingMotion = false;
                heypixelReducedThisTick = true;
                heyPixelLog("Applied horizontal reduction to queued velocity");
            }

            if (!KillAura.INSTANCE.isEnabled() || KillAura.INSTANCE.target != heypixelTarget) {
                heyPixelLog("Attack sequence stopped: KillAura target lost");
                heypixelStage = HeypixelStage.DELAY;
                heypixelVelocityTime = System.currentTimeMillis();
                heypixelTarget = null;
                heypixelRemainingAttacks = 0;
                return;
            }

            if (heypixelRemainingAttacks > 0) {
                mc.player.setSprinting(false);
                if (mc.gameMode != null) {
                    mc.gameMode.attack(mc.player, heypixelTarget);
                }
                mc.player.swing(InteractionHand.MAIN_HAND);
                if (!heypixelReducedThisTick) {
                    mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                    heypixelReducedThisTick = true;
                }
                heypixelRemainingAttacks--;
            }

            if (heypixelRemainingAttacks == 0) {
                heypixelStage = HeypixelStage.NONE;
                heypixelTarget = null;
                heyPixelLog("Attack sequence finished");
            }
        }

        if (heypixelLag && mc.player.hurtTime == 0) {
            heypixelLag = false;
        }
    }

    private void handleHeypixelInput(KeyboardInputEvent event) {
        if (heypixelStage != HeypixelStage.DELAY || !KillAura.INSTANCE.isEnabled()) {
            return;
        }

        LivingEntity candidate = KillAura.INSTANCE.target;
        if (!(candidate instanceof Player player) || !isValidHeypixelTarget(player)) {
            return;
        }
        if (heypixelDelayTillGround.get() && !mc.player.onGround()) {
            return;
        }

        event.setForward(1.0f);
        event.setStrafe(0.0f);
        event.setSprint(true);
        mc.player.setSprinting(true);
        if (heypixelJumpReset.get() && mc.player.onGround()) {
            event.setJump(true);
        }
        heypixelCanAttack = true;
        heypixelTarget = player;
    }

    private boolean isValidHeypixelTarget(Player player) {
        return player != null
                && mc.player != null
                && player != mc.player
                && !FriendManager.INSTANCE.isFriend(player)
                && !mc.player.isAlliedTo(player)
                && !player.isCreative()
                && !player.isSpectator()
                && !player.isRemoved()
                && !player.isDeadOrDying()
                && player.isAlive();
    }

    private long getHeypixelDelay() {
        if (heypixelTarget != null && mc.player != null) {
            double distance = mc.player.distanceTo(heypixelTarget);
            double speed = mc.player.isSprinting() ? 5.612 : 4.317;
            if (distance > 0.0) {
                long calculated = (long) (distance / speed * 1000.0) + 50L;
                return Math.max(50L, Math.min(heypixelMaxDelay.get(), calculated));
            }
        }
        return heypixelMaxDelay.get();
    }

    private void releaseHeypixelAttackBuffer() {
        if (mc.getConnection() == null) {
            heypixelAttackBuffer.clear();
            return;
        }
        while (!heypixelAttackBuffer.isEmpty()) {
            Packet<? super ClientPacketListener> packet = heypixelAttackBuffer.poll();
            if (packet != null) {
                packet.handle(mc.getConnection());
            }
        }
    }

    private void releaseHeypixelPackets(boolean clearStage) {
        if (clearStage) {
            heypixelStage = HeypixelStage.NONE;
            heypixelTarget = null;
            heypixelCanAttack = false;
            heypixelRemainingAttacks = 0;
            heypixelLag = false;
            heypixelJustHurt = false;
            heypixelHasPendingMotion = false;
            heypixelReducedThisTick = false;
            heypixelAttackBuffer.clear();
        }
        if (mc.getConnection() == null || mc.level == null) {
            heypixelPackets.clear();
            return;
        }
        while (!heypixelPackets.isEmpty()) {
            Packet<? super ClientPacketListener> packet = heypixelPackets.poll();
            if (packet != null) {
                packet.handle(mc.getConnection());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Packet<? super ClientPacketListener> asClientPacket(Packet<?> packet) {
        return (Packet<? super ClientPacketListener>) packet;
    }

    private void heyPixelLog(String message) {
        if (heypixelDebug.get()) {
            ChatUtils.addChatMessage("[Velocity/Heypixel] " + message);
        }
    }

    private void handleHypixel(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket packet)
                || packet.id() != mc.player.getId()) {
            return;
        }

        Vec3 motion = packet.movement();
        int motionX = (int) Math.round(motion.x * 8000.0D);
        int motionZ = (int) Math.round(motion.z * 8000.0D);
        reduceTicks = computeHypixelReduceTicks(motionX, motionZ);
        if (reduceTicks > 5) reduceTicks = 5;
        if (motion.y > 0.0D) {
            tickSinceVelocity = 0;
        }
    }

    private void tickHypixelReduction() {
        if (noPlayer() || mc.level == null) return;

        if (tickSinceVelocity < reduceTicks) {
            if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
                return;
            }

            LivingEntity auraTarget = KillAura.INSTANCE != null ? KillAura.INSTANCE.target : null;
            LivingEntity target = auraTarget;

            if (target == null || RotationUtils.getEyeDistanceToEntity(target) > 3.0D) {
                target = getFarthestLivingEntity(true);
            }

            if (target == null || target == mc.player) return;

            if (mc.gameMode != null) {
                mc.gameMode.attack(mc.player, target);
            }

            if (auraTarget != null) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                }
            }

            hypixelStartSequence = true;
        } else {
            hypixelStartSequence = false;
        }
    }

    private LivingEntity getFarthestLivingEntity(boolean playersOnly) {
        if (mc.level == null || mc.player == null) return null;

        LivingEntity target = null;
        double farthestDistance = 0.0D;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || living instanceof ArmorStand) continue;
            if (!isValidEntity(living, playersOnly)) continue;

            double dist = RotationUtils.getEyeDistanceToEntity(living);
            if (dist < 11.0D) continue;

            if (dist > farthestDistance) {
                farthestDistance = dist;
                target = living;
            }
        }
        return target;
    }

    private boolean isValidEntity(LivingEntity living, boolean playersOnly) {
        if (playersOnly) return living instanceof Player;
        return living instanceof Player || living instanceof Mob || living instanceof Villager;
    }

    static int computeHypixelReduceTicks(int motionX, int motionZ) {
        double kb = Math.hypot(motionX, motionZ);
        int ticks = (int) Math.round(0.000643153527D * kb + 2.9419087136D);
        return Math.max(1, Math.min(ticks, 10));
    }

    @Listen
    private void onRespawn(RespawnEvent event) {
        resetHypixel();
    }

    @Listen
    private void onWorldChange(WorldChangeEvent event) {
        resetHypixel();
    }

    private boolean canModifyVelocity() {
        if (mc.player.isInWater() || mc.player.isInLava()) {
            return whileLiquid.get();
        }
        if (mc.player.isFallFlying()) {
            return fallFlying.get();
        }
        return true;
    }

    private boolean canProcessGrimWall(Vec3 movement) {
        if (!mode.is(Mode.Grim) && !mode.is(Mode.Wall)) return true;
        if (!lagTimer.passedMillis(lagPause.get())) return false;

        boolean hasHorizontal = movement.x != 0.0 || movement.z != 0.0;
        if (!hasHorizontal) return true;

        return mode.is(Mode.Wall) || mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0 || getObsidianBelow() != null;
    }

    private void markVelocityFlag(Vec3 movement) {
        if ((mode.is(Mode.Grim) || mode.is(Mode.Wall)) && (movement.x != 0.0 || movement.z != 0.0)) {
            velocityFlag = true;
        }
    }

    private Vec3 scale(Vec3 movement) {
        double h = horizontal.get() / 100.0;
        double v = vertical.get() / 100.0;
        return new Vec3(movement.x * h, movement.y * v, movement.z * h);
    }

    private void tickOpenTrollFlag() {
        if (flagInWall.get()) {
            inWallFlag = PlayerUtils.isInBlock();
        } else {
            inWallFlag = false;
        }

        if (!velocityFlag) return;
        boolean allowed = !flagInWall.get() || !inWallFlag || whilePushOut.get() || getObsidianBelow() == null;
        if (!lagTimer.passedMillis(lagPause.get()) || !allowed) return;

        float pitch = noRotation.get() ? 89.0f : mc.player.getXRot();
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.getYRot(),
                pitch,
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));

        BlockPos obsidian = getObsidianBelow();
        if (obsidian != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    obsidian,
                    RotationUtils.getClickSide(obsidian)
            ));
        }
        velocityFlag = false;
    }

    private BlockPos getObsidianBelow() {
        BlockPos pos = mc.player.blockPosition().below();
        return mc.level.getBlockState(pos).is(Blocks.OBSIDIAN) ? pos : null;
    }

    private boolean shouldCancelFishBob(PacketEvent.Receive event) {
        if (!noFishBob.get() || mc.level == null) return false;
        if (!(event.getPacket() instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != 31) return false;
        Entity entity = packet.getEntity(mc.level);
        return entity instanceof FishingHook && entity.distanceToSqr(mc.player) < 16.0;
    }

    private boolean isOpenTrollMode() {
        return mode.is(Mode.Modify) || mode.is(Mode.Grim) || mode.is(Mode.Wall);
    }

    private void resetState() {
        resetJumpReset();
        resetHeypixel();
        heypixel2.reset();
        heypixel2RuntimeActive = false;
        resetHypixel();
        velocityFlag = false;
        inWallFlag = false;
    }

    private void syncHeypixel2Runtime() {
        boolean selected = mode.is(Mode.HeypixelLegacy2);
        if (selected && !heypixel2RuntimeActive) {
            heypixel2.reset();
            heypixel2RuntimeActive = true;
        } else if (!selected && heypixel2RuntimeActive) {
            heypixel2.disable();
            heypixel2RuntimeActive = false;
        }
    }

    private void resetJumpReset() {
        jump = false;
        pendingJumpDelay = 0;
    }

    private void resetHeypixel() {
        heypixelLag = false;
        heypixelVelocityTime = 0L;
        heypixelTarget = null;
        heypixelStage = HeypixelStage.NONE;
        heypixelCanAttack = false;
        heypixelRemainingAttacks = 0;
        heypixelJustHurt = false;
        heypixelHasPendingMotion = false;
        heypixelReducedThisTick = false;
        heypixelPackets.clear();
        heypixelAttackBuffer.clear();
    }

    private void resetHypixel() {
        hypixelStartSequence = false;
        tickSinceVelocity = 10;
        reduceTicks = 0;
    }


    private enum HeypixelStage {
        NONE, DELAY, ATTACK, LAG, TIMEOUT
    }
}
