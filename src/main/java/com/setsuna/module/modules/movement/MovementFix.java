package com.setsuna.module.modules.movement;

import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import net.minecraft.util.Mth;

public final class MovementFix extends Module {

    public static final MovementFix INSTANCE = new MovementFix();

    public enum Mode {
        Setting,
        Packet,
        SprintOnly,
        Off,
        Strict,
        Silent,
        ChangeLook
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Silent));
    private final BooleanSetting packetOnly = add(new BooleanSetting("Packet Only", false)
            .visibleWhen(() -> mode.is(Mode.Packet) || isSprintOnlyMode()));

    private MovementFix() {
        super("Movement Fix", Category.MOVEMENT);
        setEnabled(true);
    }

    public boolean shouldFixInput() {
        return mode.is(Mode.Setting) || mode.is(Mode.Silent) || (mode.is(Mode.Packet) && !packetOnly.get());
    }

    public boolean shouldFixRotationYaw() {
        return mode.is(Mode.Setting)
                || mode.is(Mode.Packet)
                || mode.is(Mode.SprintOnly)
                || mode.is(Mode.Strict)
                || mode.is(Mode.Silent)
                || mode.is(Mode.ChangeLook);
    }

    public boolean shouldChangeLook() {
        return mode.is(Mode.ChangeLook);
    }

    public boolean shouldFixPacketOnly() {
        return mode.is(Mode.Packet) && packetOnly.get();
    }

    public boolean shouldPreventLocalSprint() {
        return (isSprintOnlyMode() || mode.is(Mode.Strict) || mode.is(Mode.Silent))
                && !packetOnly.get() && hasLargeRotationDelta();
    }

    public boolean shouldSuppressSprintPacket() {
        return (isSprintOnlyMode() || mode.is(Mode.Strict) || mode.is(Mode.Silent)) && hasLargeRotationDelta();
    }

    public boolean hasLargeRotationDelta() {
        if (!isEnabled() || mode.is(Mode.Off) || noPlayer() || !RotationManager.INSTANCE.isActive()) {
            return false;
        }
        float currentMoveYaw = getMoveYaw(mc.player.getYRot(), currentForward(), currentStrafe());
        float packetMoveYaw = getMoveYaw(RotationManager.INSTANCE.getYaw(), currentForward(), currentStrafe());
        return Math.abs(Mth.wrapDegrees(packetMoveYaw - currentMoveYaw)) > 45.005F;
    }

    private float getDirection(float forward, float strafe) {
        float direction = mc.player.getYRot();

        boolean isMovingForward = forward > 0.0F;
        boolean isMovingBack = forward < 0.0F;
        boolean isMovingRight = strafe > 0.0F;
        boolean isMovingLeft = strafe < 0.0F;
        boolean isMovingSideways = isMovingRight || isMovingLeft;
        boolean isMovingStraight = isMovingForward || isMovingBack;

        if (forward != 0.0F || strafe != 0.0F) {
            if (isMovingBack && !isMovingSideways) {
                return direction + 180.0F;
            }
            if (isMovingForward && isMovingLeft) {
                return direction + 45.0F;
            }
            if (isMovingForward && isMovingRight) {
                return direction - 45.0F;
            }
            if (!isMovingStraight && isMovingLeft) {
                return direction + 90.0F;
            }
            if (!isMovingStraight && isMovingRight) {
                return direction - 90.0F;
            }
            if (isMovingBack && isMovingLeft) {
                return direction + 135.0F;
            }
            if (isMovingBack) {
                return direction - 135.0F;
            }
        }

        return direction;
    }

    public static float getMoveYaw(float yaw, float forward, float strafe) {
        if (forward < 0.0F) {
            yaw += 180.0F;
        }

        float factor = 1.0F;
        if (forward < 0.0F) {
            factor = -0.5F;
        } else if (forward > 0.0F) {
            factor = 0.5F;
        }

        if (strafe > 0.0F) {
            yaw -= 90.0F * factor;
        } else if (strafe < 0.0F) {
            yaw += 90.0F * factor;
        }

        return yaw;
    }

    public void fixMovement(KeyboardInputEvent event, float yaw) {
        if (!shouldFixInput()) return;

        float forward = event.getForward();
        float strafe = event.getStrafe();

        int angleUnit = 45;
        float angleTolerance = 22.5F;
        float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
        double angleDifference = Mth.wrapDegrees(getDirection(forward, strafe) - yaw);
        double angleDistance = Math.abs(angleDifference);

        forward = 0.0F;
        strafe = 0.0F;

        if (angleDistance <= (double) ((float) angleUnit + angleTolerance)) {
            forward++;
        } else if (angleDistance >= (double) (180.0F - (float) angleUnit - angleTolerance)) {
            forward--;
        }

        if (angleDifference >= (double) ((float) angleUnit - angleTolerance)
                && angleDifference <= (double) (180.0F - (float) angleUnit + angleTolerance)) {
            strafe--;
        } else if (angleDifference <= (double) ((float) (-angleUnit) + angleTolerance)
                && angleDifference >= (double) (-180.0F + (float) angleUnit - angleTolerance)) {
            strafe++;
        }

        forward *= directionFactor;
        strafe *= directionFactor;

        event.setForward(forward);
        event.setStrafe(strafe);
    }

    private float currentForward() {
        if (mc.player.input.keyPresses.forward()) return 1.0F;
        if (mc.player.input.keyPresses.backward()) return -1.0F;
        return 0.0F;
    }

    private float currentStrafe() {
        if (mc.player.input.keyPresses.left()) return 1.0F;
        if (mc.player.input.keyPresses.right()) return -1.0F;
        return 0.0F;
    }

    private boolean isSprintOnlyMode() {
        return mode.is(Mode.SprintOnly);
    }
}
