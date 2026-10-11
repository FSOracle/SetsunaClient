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
    private static final int AXIS_ANGLE = 45;
    private static final float ANGLE_MARGIN = 22.5F;

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
        return switch (mode.get()) {
            case Setting, Silent -> true;
            case Packet -> !packetOnly.get();
            default -> false;
        };
    }

    public boolean shouldFixRotationYaw() {
        return mode.get() != Mode.Off;
    }

    public boolean shouldChangeLook() {
        return mode.is(Mode.ChangeLook);
    }

    public boolean shouldFixPacketOnly() {
        return mode.get() == Mode.Packet && packetOnly.get();
    }

    public boolean shouldPreventLocalSprint() {
        return suppressesSprint() && !packetOnly.get() && hasLargeRotationDelta();
    }

    public boolean shouldSuppressSprintPacket() {
        return suppressesSprint() && hasLargeRotationDelta();
    }

    public boolean hasLargeRotationDelta() {
        if (!isEnabled() || mode.is(Mode.Off) || noPlayer() || !RotationManager.INSTANCE.isActive()) {
            return false;
        }
        float forward = currentForward();
        float strafe = currentStrafe();
        float currentMoveYaw = getMoveYaw(mc.player.getYRot(), forward, strafe);
        float packetMoveYaw = getMoveYaw(RotationManager.INSTANCE.getYaw(), forward, strafe);
        return Math.abs(Mth.wrapDegrees(packetMoveYaw - currentMoveYaw)) > 45.005F;
    }

    private float getDirection(float forward, float strafe) {
        float heading = mc.player.getYRot();

        boolean isMovingForward = forward > 0.0F;
        boolean isMovingBack = forward < 0.0F;
        boolean isMovingRight = strafe > 0.0F;
        boolean isMovingLeft = strafe < 0.0F;
        boolean isMovingSideways = isMovingRight || isMovingLeft;
        boolean isMovingStraight = isMovingForward || isMovingBack;

        if (forward != 0.0F || strafe != 0.0F) {
            if (isMovingBack && !isMovingSideways) {
                return heading + 180.0F;
            }
            if (isMovingForward && isMovingLeft) {
                return heading + AXIS_ANGLE;
            }
            if (isMovingForward && isMovingRight) {
                return heading - AXIS_ANGLE;
            }
            if (!isMovingStraight && isMovingLeft) {
                return heading + 90.0F;
            }
            if (!isMovingStraight && isMovingRight) {
                return heading - 90.0F;
            }
            if (isMovingBack && isMovingLeft) {
                return heading + 135.0F;
            }
            if (isMovingBack) {
                return heading - 135.0F;
            }
        }

        return heading;
    }

    public static float getMoveYaw(float yaw, float forward, float strafe) {
        boolean backwards = forward < 0.0F;
        if (backwards) yaw += 180.0F;

        float strafeScale = backwards ? -0.5F : forward > 0.0F ? 0.5F : 1.0F;
        if (strafe > 0.0F) return yaw - 90.0F * strafeScale;
        if (strafe < 0.0F) return yaw + 90.0F * strafeScale;
        return yaw;
    }

    public void fixMovement(KeyboardInputEvent event, float yaw) {
        if (!shouldFixInput()) return;

        float forward = event.getForward();
        float strafe = event.getStrafe();

        float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
        double angleDifference = Mth.wrapDegrees(getDirection(forward, strafe) - yaw);
        double angleDistance = Math.abs(angleDifference);

        float[] corrected = quantize(angleDifference, angleDistance, directionFactor);
        event.setForward(corrected[0]);
        event.setStrafe(corrected[1]);
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
        return mode.get() == Mode.SprintOnly;
    }

    private boolean suppressesSprint() {
        return isSprintOnlyMode() || mode.get() == Mode.Strict || mode.get() == Mode.Silent;
    }

    private static float[] quantize(double difference, double distance, float scale) {
        float forward = 0.0F;
        float strafe = 0.0F;
        if (distance <= (double) (AXIS_ANGLE + ANGLE_MARGIN)) {
            forward++;
        } else if (distance >= (double) (180.0F - AXIS_ANGLE - ANGLE_MARGIN)) {
            forward--;
        }

        if (difference >= (double) (AXIS_ANGLE - ANGLE_MARGIN)
                && difference <= (double) (180.0F - AXIS_ANGLE + ANGLE_MARGIN)) {
            strafe--;
        } else if (difference <= (double) (-AXIS_ANGLE + ANGLE_MARGIN)
                && difference >= (double) (-180.0F + AXIS_ANGLE - ANGLE_MARGIN)) {
            strafe++;
        }
        return new float[]{forward * scale, strafe * scale};
    }
}
