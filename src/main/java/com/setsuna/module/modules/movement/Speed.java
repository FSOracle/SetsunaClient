package com.setsuna.module.modules.movement;

import com.setsuna.accessor.StrafeJumpPoseAccess;
import com.setsuna.event.Listen;
import com.setsuna.event.EventBus;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.MoveEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.StrafeEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.combat.KillAura;
import com.setsuna.module.modules.combat.KillAuraPlus;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.util.player.MoveUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Horizontal movement speed boost with several timing profiles. Rewrites the
 * per-move delta ({@link MoveEvent}) and forces a jump on ground for the
 * bunny-hop style modes.
 */
public final class Speed extends Module {

    public static final Speed INSTANCE = new Speed();

    public enum Mode {
        STRAFE,
        ON_GROUND,
        VANILLA,
        BHOP,
        FORTY_FIVE
    }

    public enum StrafeMode {
        DIRECTION_COMPENSATION,
        PACKET_SIMULATION,
        PREDICTION_SYNCHRONIZED
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.STRAFE));
    public final DoubleSetting speed = add(new DoubleSetting("Speed", 1.0, 0.1, 5.0, 0.1)
            .visibleWhen(() -> !mode.is(Mode.BHOP) && !mode.is(Mode.FORTY_FIVE)));
    public final BooleanSetting strafeVisual = add(new BooleanSetting("Third Person Visual", true)
            .visibleWhen(() -> mode.is(Mode.FORTY_FIVE)));
    public final EnumSetting<StrafeMode> strafeMode = add(new EnumSetting<>(
            "Strafe Mode", StrafeMode.PREDICTION_SYNCHRONIZED)
            .visibleWhen(() -> mode.is(Mode.FORTY_FIVE)));
    public final DoubleSetting strafeTurnSpeed = add(new DoubleSetting("Turn Speed", 22.5, 1.0, 45.0, 1.0)
            .visibleWhen(() -> mode.is(Mode.FORTY_FIVE)));

    private boolean scaffoldActive;

    private Speed() {
        super("Speed", Category.MOVEMENT);
    }

    public void setScaffoldActive(boolean active) {
        if (scaffoldActive == active) {
            return;
        }
        scaffoldActive = active;
        if (active && !isEnabled()) {
            EventBus.INSTANCE.subscribe(this);
        } else if (!active && !isEnabled()) {
            EventBus.INSTANCE.unsubscribe(this);
        }
    }

    @Override
    protected void beforeEnabledStateChange(boolean value, boolean wasEnabled) {
        if (scaffoldActive && !wasEnabled && value) {
            EventBus.INSTANCE.unsubscribe(this);
        }
    }

    @Override
    protected void afterEnabledStateChange(boolean value, boolean wasEnabled) {
        if (scaffoldActive && wasEnabled && !isEnabled()) {
            EventBus.INSTANCE.subscribe(this);
        }
    }

    @Listen
    private void onMove(MoveEvent event) {
        if (noPlayer() || !MoveUtils.isMoving()) {
            return;
        }
        switch (mode.get()) {
            case STRAFE -> applyStrafe(event);
            case ON_GROUND -> applyOnGround(event);
            case VANILLA -> applyVanilla(event);
            case BHOP, FORTY_FIVE -> {
                // Bhop follows vanilla momentum; 45-degree mode is handled at
                // input, travel-event, and packet time by its silent pipeline.
            }
        }
    }

    @Listen
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (noPlayer() || !MoveUtils.isMoving()) {
            return;
        }
        boolean fortyFiveAutoJump = mode.is(Mode.FORTY_FIVE) && !shouldPauseFortyFive();
        if ((mode.is(Mode.BHOP) || mode.is(Mode.STRAFE) || fortyFiveAutoJump)
                && mc.player.onGround()) {
            event.setJump(true);
        }
    }

    @Listen
    private void onStrafe(StrafeEvent event) {
        if (noPlayer() || !shouldRotateFortyFiveTravel(mc.player)) {
            return;
        }
        event.setYaw(event.getYaw() + 45.0F);
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            return;
        }
        if (mode.is(Mode.BHOP) || mode.is(Mode.FORTY_FIVE)) {
            return;
        }
        // Kill residual horizontal drift when not actively moving.
        if (!MoveUtils.isMoving()) {
            Vec3 motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(0.0, motion.y, 0.0);
        }
    }

    private void applyStrafe(MoveEvent event) {
        double base = getScaledSpeed(0.2873);
        double horizontal = mc.player.onGround() ? base * 1.18 : base;
        if (mc.player.onGround()) {
            event.setY(0.40123128);
        } else if (mc.player.getDeltaMovement().y < 0.0) {
            event.setY(event.getY() - 0.014);
        }
        setHorizontal(event, horizontal);
    }

    private void applyOnGround(MoveEvent event) {
        if (!mc.player.onGround()) {
            return;
        }
        double horizontal = getScaledSpeed(0.2873) * 1.08;
        event.setY(0.0);
        setHorizontal(event, horizontal);
    }

    private void applyVanilla(MoveEvent event) {
        setHorizontal(event, speed.get());
    }

    private double getScaledSpeed(double base) {
        return base * speed.get();
    }

    private void setHorizontal(MoveEvent event, double horizontal) {
        double[] dir = MoveUtils.forward(horizontal);
        event.setX(dir[0]);
        event.setZ(dir[1]);
        event.cancel();
    }

    /** True while this mode is enabled directly or through Scaffold Auto Speed. */
    public boolean isFortyFiveEnabled() {
        return mode.is(Mode.FORTY_FIVE) && (isEnabled() || scaffoldActive);
    }

    /** Matches the source StrafeJump activation predicate. */
    public boolean shouldApplyFortyFive(LocalPlayer player) {
        return player != null
                && isFortyFiveEnabled()
                && !player.onGround()
                && player.isSprinting()
                && !player.isPassenger()
                && !player.isInWater()
                && !player.isInLava()
                && !player.onClimbable()
                && !player.getAbilities().flying
                && !shouldPauseFortyFive();
    }

    /** Pauses 45-degree spoofing while another action needs an exact rotation. */
    public boolean shouldPauseFortyFive() {
        return Scaffold.INSTANCE.isEnabled()
                || KillAura.INSTANCE.hasLockedTarget()
                || KillAuraPlus.INSTANCE.hasLockedTarget()
                || RotationManager.INSTANCE.isActive()
                || mc.options.keyUse.isDown();
    }

    private boolean shouldRotateFortyFiveTravel(LocalPlayer player) {
        if (!shouldApplyFortyFive(player)
                || strafeMode.is(StrafeMode.DIRECTION_COMPENSATION)) {
            return false;
        }
        return !strafeMode.is(StrafeMode.PREDICTION_SYNCHRONIZED)
                || player instanceof StrafeJumpPoseAccess pose
                && pose.setsuna$isSynchronizedStrafeTick();
    }

    public boolean shouldRenderFortyFivePose() {
        return isFortyFiveEnabled() && strafeVisual.get();
    }
}
