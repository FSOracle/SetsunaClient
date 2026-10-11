package com.setsuna.manager;

import com.setsuna.event.EventBus;
import com.setsuna.event.Listen;
import com.setsuna.event.events.AfterRotationEvent;
import com.setsuna.event.events.AttackYawEvent;
import com.setsuna.event.events.FallFlyingEvent;
import com.setsuna.event.events.JumpEvent;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.RaytraceEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.event.events.RotationAnimationEvent;
import com.setsuna.event.events.SendPositionEvent;
import com.setsuna.event.events.StrafeEvent;
import com.setsuna.module.modules.movement.MovementFix;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;

import java.util.function.Function;

/**
 * Server-side (silent) rotation engine. Modules request rotations via
 * {@link #setRotations}; the manager smooths them each player tick and rewrites
 * outgoing {@link SendPositionEvent}s so the server sees the aimed rotation while
 * the client camera stays put.
 */
public final class RotationManager {

    public static final RotationManager INSTANCE = new RotationManager();

    private static final Minecraft mc = Minecraft.getInstance();

    private final Rot2f offset = new Rot2f(0, 0);
    public Rot2f rotations = new Rot2f(0, 0);
    public Rot2f lastRotations = new Rot2f(0, 0);
    private Rot2f lastSentRotations = new Rot2f(0, 0);
    private boolean hasSentRotations;
    public Rot2f targetRotations;
    public Rot2f animationRotation;
    public Rot2f lastAnimationRotation;

    private boolean active;
    private boolean smoothed;
    private double rotationSpeed;
    private Function<Rot2f, Boolean> raytrace;
    private float randomAngle;
    private boolean s08;
    private boolean renderAnimation = true;

    private int priority;
    private Runnable callback;
    private Object rotationOwner;

    public Object getRotationOwner() {
        return rotationOwner;
    }

    private RotationManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed) {
        setRotations(rotations, rotationSpeed, null, Priority.Medium, null);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Priority priority) {
        setRotations(rotations, rotationSpeed, null, priority, null);
    }

    public void setRotations(Object owner, Rot2f rotations, double rotationSpeed, Priority priority) {
        setRotations(rotations, rotationSpeed, null, priority, null, true, owner);
    }

    public void setRotations(
            Object owner,
            Rot2f rotations,
            double rotationSpeed,
            Priority priority,
            boolean renderAnimation) {
        setRotations(rotations, rotationSpeed, null, priority, null, renderAnimation, owner);
    }

    public void setRotations(
            Rot2f rotations,
            double rotationSpeed,
            Priority priority,
            boolean renderAnimation) {
        setRotations(rotations, rotationSpeed, null, priority, null, renderAnimation);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Function<Rot2f, Boolean> raytrace) {
        setRotations(rotations, rotationSpeed, raytrace, Priority.Medium, null);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Function<Rot2f, Boolean> raytrace, Priority priority) {
        setRotations(rotations, rotationSpeed, raytrace, priority, null);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Function<Rot2f, Boolean> raytrace, Priority priority, Runnable callback) {
        setRotations(rotations, rotationSpeed, raytrace, priority, callback, true);
    }

    /** Applies a rotation model's already-stepped result without smoothing it a second time. */
    public void setRotationsDirect(Rot2f rotations, Priority priority) {
        setRotationsDirect(null, rotations, priority);
    }

    /** Applies an owned direct rotation that can later be released without affecting another module. */
    public void setRotationsDirect(Object owner, Rot2f rotations, Priority priority) {
        if (rotations == null || mc.player == null) return;
        if (this.active && priority.priority < this.priority) return;
        if (consumeServerCorrection()) return;

        installDirectRotation(owner, rotations, priority, true);
        syncClientLookAndPick();
    }

    /**
     * Claims a packet-scoped silent rotation without changing the camera,
     * animation, ray trace, or movement input. Existing rotations of equal or
     * higher priority keep ownership.
     */
    public boolean claimSilentRotation(Object owner, Rot2f rotations, Priority priority) {
        if (owner == null || rotations == null || mc.player == null) return false;
        if (this.active && this.rotationOwner != owner && priority.priority <= this.priority) {
            return false;
        }

        if (consumeServerCorrection()) return false;

        installDirectRotation(owner, rotations, priority, false);
        return true;
    }

    /** Releases only the rotation claimed by the same owner. */
    public void releaseSilentRotation(Object owner) {
        releaseRotations(owner);
    }

    public boolean ownsSilentRotation(Object owner) {
        return owner != null && active && rotationOwner == owner;
    }

    public boolean releaseRotations(Object owner) {
        if (owner == null || this.rotationOwner != owner) return false;
        deactivateRotation();
        return true;
    }

    private void deactivateRotation() {
        this.active = false;
        this.priority = 0;
        this.callback = null;
        this.rotationOwner = null;
        this.raytrace = null;
        this.smoothed = false;
        this.renderAnimation = true;
        if (mc != null && mc.player != null) {
            Rot2f playerRotation = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            this.rotations = playerRotation;
            this.lastRotations = playerRotation.copy();
            this.targetRotations = playerRotation.copy();
            this.animationRotation = playerRotation.copy();
            this.lastAnimationRotation = playerRotation.copy();
            mc.player.yHeadRot = playerRotation.getYaw();
            mc.player.yHeadRotO = playerRotation.getYaw();
            mc.player.yBodyRot = playerRotation.getYaw();
            mc.player.yBodyRotO = playerRotation.getYaw();
        }
    }

    private void setRotations(
            Rot2f rotations,
            double rotationSpeed,
            Function<Rot2f, Boolean> raytrace,
            Priority priority,
            Runnable callback,
            boolean renderAnimation) {
        setRotations(rotations, rotationSpeed, raytrace, priority, callback, renderAnimation, null);
    }

    private void setRotations(
            Rot2f rotations,
            double rotationSpeed,
            Function<Rot2f, Boolean> raytrace,
            Priority priority,
            Runnable callback,
            boolean renderAnimation,
            Object owner) {
        if (rotations == null || mc.player == null) return;

        if (this.active && priority.priority < this.priority) {
            return;
        }

        if (consumeServerCorrection()) return;

        this.targetRotations = rotations;
        this.rotationSpeed = rotationSpeed * 18.0;
        this.raytrace = raytrace;
        this.priority = priority.priority;
        this.callback = callback;
        this.rotationOwner = owner;
        this.renderAnimation = renderAnimation;
        this.active = true;

        smooth();
    }

    private boolean consumeServerCorrection() {
        if (!s08) return false;

        Rot2f playerRotation = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        rotations = lastRotations = targetRotations = playerRotation;
        callback = null;
        rotationOwner = null;
        s08 = false;
        return true;
    }

    private void installDirectRotation(Object owner, Rot2f requested, Priority requestedPriority, boolean animate) {
        rotations = requested;
        targetRotations = requested;
        raytrace = null;
        priority = requestedPriority.priority;
        callback = null;
        rotationOwner = owner;
        renderAnimation = animate;
        active = true;
        smoothed = true;
    }

    private void smooth() {
        if (mc.player == null) {
            resetState();
            return;
        }

        if (!smoothed) {
            Rot2f nextTarget = adjustedRaytraceTarget();
            rotations = RotationUtils.smooth(nextTarget, Math.ceil(rotationSpeed) + Math.random());
        }

        smoothed = true;
        syncClientLookAndPick();
    }

    private Rot2f adjustedRaytraceTarget() {
        float targetYaw = targetRotations.getYaw();
        float targetPitch = targetRotations.getPitch();
        boolean needsOffset = raytrace != null
                && (Math.abs(targetYaw - rotations.getYaw()) > 5
                || Math.abs(targetPitch - rotations.getPitch()) > 5);
        if (!needsOffset) return new Rot2f(targetYaw, targetPitch);

        Rot2f trueTarget = new Rot2f(targetRotations.getYaw(), targetRotations.getPitch());
        double speed = Math.random() * Math.random() * Math.random() * 20;
        randomAngle += (float) ((20
                + (float) (Math.random() - 0.5)
                * (Math.random() * Math.random() * Math.random() * 360))
                * (mc.player.tickCount / 10 % 2 == 0 ? -1 : 1));

        advanceOffset(speed);
        targetYaw += offset.getYaw();
        targetPitch += offset.getPitch();

        if (!raytrace.apply(new Rot2f(targetYaw, targetPitch))) {
            randomAngle = (float) Math.toDegrees(
                    Math.atan2(trueTarget.getYaw() - targetYaw, targetPitch - trueTarget.getPitch())) - 180;
            targetYaw -= offset.getYaw();
            targetPitch -= offset.getPitch();
            advanceOffset(speed);
            targetYaw += offset.getYaw();
            targetPitch += offset.getPitch();
        }

        if (!raytrace.apply(new Rot2f(targetYaw, targetPitch))) {
            offset.set(0, 0);
            targetYaw = (float) (targetRotations.getYaw() + Math.random() * 2);
            targetPitch = (float) (targetRotations.getPitch() + Math.random() * 2);
        }
        return new Rot2f(targetYaw, targetPitch);
    }

    private void advanceOffset(double speed) {
        offset.set(
                (float) (offset.getYaw() + -Mth.sin((float) Math.toRadians(randomAngle)) * speed),
                (float) (offset.getPitch() + Mth.cos((float) Math.toRadians(randomAngle)) * speed)
        );
    }

    private void syncClientLookAndPick() {
        MovementFix movementFix = MovementFix.INSTANCE;
        if (movementFix.isEnabled() && movementFix.shouldChangeLook()) {
            mc.player.setYRot(rotations.getYaw());
            mc.player.setXRot(rotations.getPitch());
            mc.player.yHeadRot = rotations.getYaw();
            mc.player.yBodyRot = rotations.getYaw();
        }
        mc.pick(1.0F);
    }

    private void correctDisabledRotations() {
        if (mc.player == null) return;
        Rot2f rotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        Rot2f fixedRotations = RotationUtils.resetRotation(RotationUtils.applySensitivityPatch(rotations, lastRotations));
        mc.player.setYRot(fixedRotations.getYaw());
        mc.player.setXRot(fixedRotations.getPitch());
    }

    public float getYaw() {
        return getRotation().getYaw();
    }

    public float getPitch() {
        return getRotation().getPitch();
    }

    public Rot2f getRotation() {
        if (mc.player == null) return rotations != null ? rotations : new Rot2f(0, 0);
        return active ? rotations : new Rot2f(mc.player.getYRot(), mc.player.getXRot());
    }

    public Rot2f getLastRotation() {
        if (mc.player == null) return lastRotations != null ? lastRotations : new Rot2f(0, 0);
        return lastRotations != null ? lastRotations : new Rot2f(mc.player.yRotO, mc.player.xRotO);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (active) {
            this.active = true;
        } else {
            deactivateRotation();
        }
    }

    public boolean isSmoothed() {
        return smoothed;
    }

    public void setSmoothed(boolean smoothed) {
        this.smoothed = smoothed;
    }

    @Listen
    private void onRespawn(RespawnEvent event) {
        resetState();
    }

    private void resetState() {
        offset.set(0, 0);
        rotations = new Rot2f(0, 0);
        lastRotations = new Rot2f(0, 0);
        lastSentRotations = new Rot2f(0, 0);
        hasSentRotations = false;
        targetRotations = null;
        animationRotation = null;
        lastAnimationRotation = null;
        active = false;
        priority = 0;
        callback = null;
        rotationOwner = null;
        smoothed = false;
        raytrace = null;
        randomAngle = 0;
        s08 = false;
        renderAnimation = true;
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket || event.getPacket() instanceof ClientboundPlayerRotationPacket) {
            s08 = true;
            hasSentRotations = false;
        }
    }

    @Listen
    private void onRaytrace(RaytraceEvent event) {
        if (active && rotations != null) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @Listen
    private void onAnimation(RotationAnimationEvent event) {
        if (renderAnimation
                && active
                && animationRotation != null
                && lastAnimationRotation != null) {
            event.setYaw(animationRotation.getYaw());
            event.setLastYaw(lastAnimationRotation.getYaw());
            event.setPitch(animationRotation.getPitch());
            event.setLastPitch(lastAnimationRotation.getPitch());
        }
    }

    @Listen(priority = -1000)
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (mc.player == null) {
            resetState();
            return;
        }

        if (!active || rotations == null || lastRotations == null || targetRotations == null) {
            rotations = lastRotations = targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }

        if (active) {
            smooth();
            EventBus.INSTANCE.post(new AfterRotationEvent());

            if (callback != null) {
                callback.run();
                callback = null;
            }
        }
    }

    @Listen(priority = com.setsuna.event.Priority.LOWEST)
    private void onSendPosition(SendPositionEvent event) {
        if (mc.player == null) {
            resetState();
            return;
        }

        if (active && rotations != null) {
            float yaw = rotations.getYaw();
            float pitch = rotations.getPitch();

            if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
                event.setYaw(yaw);
                event.setPitch(pitch);
            }

            if (Math.abs((rotations.getYaw() - mc.player.getYRot()) % 360) < 1 && Math.abs((rotations.getPitch() - mc.player.getXRot())) < 1) {
                active = false;
                priority = 0;
                callback = null;
                rotationOwner = null;
                this.correctDisabledRotations();
            }

            lastRotations = rotations;
        } else {
            lastRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }

        lastAnimationRotation = animationRotation;
        animationRotation = new Rot2f(event.getYaw(), event.getPitch());
        targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        raytrace = null;
        smoothed = false;
    }

    /**
     * Commits a rotation only after the corresponding movement packet passed
     * the final outbound event gate and was handed to the connection.
     */
    public void onMovementPacketSent(Packet<?> packet) {
        if (mc.player == null
                || !(packet instanceof ServerboundMovePlayerPacket movement)
                || !movement.hasRotation()) {
            return;
        }
        Rot2f fallback = lastSentRotations != null
                ? lastSentRotations
                : new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        lastSentRotations = new Rot2f(
                movement.getYRot(fallback.getYaw()),
                movement.getXRot(fallback.getPitch()));
        hasSentRotations = true;
    }

    public boolean hasSentRotations() {
        return hasSentRotations;
    }

    public Rot2f getLastSentRotation() {
        return lastSentRotations != null
                ? lastSentRotations
                : new Rot2f(0.0F, 0.0F);
    }

    @Listen(priority = com.setsuna.event.Priority.HIGH)
    private void onMoveInput(KeyboardInputEvent event) {
        if (shouldRewriteMovement(false)) {
            MovementFix movementFix = MovementFix.INSTANCE;
            movementFix.fixMovement(event, rotations.getYaw());
        }
    }

    @Listen
    private void onStrafe(StrafeEvent event) {
        if (shouldRewriteMovement(false)) {
            event.setYaw(rotations.getYaw());
        }
    }

    @Listen
    private void onJump(JumpEvent event) {
        if (shouldRewriteMovement(false)) {
            event.setYaw(rotations.getYaw());
        }
    }

    private boolean shouldRewriteMovement(boolean input) {
        if (mc.player == null || !active || rotations == null || mc.player.isFallFlying()) return false;
        if (rotationOwner instanceof com.setsuna.module.modules.movement.HypixelTellyEngine) return false;
        MovementFix movementFix = MovementFix.INSTANCE;
        return movementFix.isEnabled() && (input ? movementFix.shouldFixInput() : movementFix.shouldFixRotationYaw());
    }

    @Listen
    private void onFallFlying(FallFlyingEvent event) {
        MovementFix movementFix = MovementFix.INSTANCE;
        if (movementFix.isEnabled()
                && movementFix.shouldFixRotationYaw()
                && active
                && rotations != null) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @Listen
    private void onAttack(AttackYawEvent event) {
        if (rotations != null) {
            event.setYaw(rotations.getYaw());
        }
    }
}
