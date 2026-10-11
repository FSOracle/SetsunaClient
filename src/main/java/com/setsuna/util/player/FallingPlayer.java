package com.setsuna.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Mutable prediction state for the local player's airborne trajectory. */
public final class FallingPlayer {

    private static final double GRAVITY = 0.08D;
    private static final double AIR_DRAG = 0.9800000190734863D;
    private static final double HORIZONTAL_DRAG = 0.91D;
    private static final float INPUT_SCALE = 0.9800000190734863F;
    private static final float MINIMUM_INPUT = 1.0E-4F;
    private static final float SPRINT_ACCELERATION = 1.3F;
    private static final Minecraft mc = Minecraft.getInstance();

    private double positionX;
    private double positionY;
    private double positionZ;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private final float headingDegrees;
    private final float sidewaysInput;
    private final float forwardInput;
    private final float acceleration;

    public FallingPlayer(
            double x,
            double y,
            double z,
            double motionX,
            double motionY,
            double motionZ,
            float yaw,
            float strafe,
            float forward,
            float jumpMovementFactor
    ) {
        positionX = x;
        positionY = y;
        positionZ = z;
        velocityX = motionX;
        velocityY = motionY;
        velocityZ = motionZ;
        headingDegrees = yaw;
        sidewaysInput = strafe;
        forwardInput = forward;
        acceleration = jumpMovementFactor;
    }

    public FallingPlayer(Player player) {
        this(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getDeltaMovement().x,
                player.getDeltaMovement().y,
                player.getDeltaMovement().z,
                player.getYRot(),
                0.0F,
                0.0F,
                player.getSpeed()
        );
    }

    public void calculate(int ticks) {
        for (int remaining = ticks; remaining > 0; remaining--) {
            advanceOneTick();
        }
    }

    public BlockPos findCollision(int ticks) {
        for (int remaining = ticks; remaining > 0; remaining--) {
            Vec3 tickStart = position();
            advanceOneTick();
            Vec3 tickEnd = position();

            BlockPos collision = traceFootprint(tickStart, tickEnd, mc.player.getBbWidth() * 0.5F);
            if (collision != null) {
                return collision;
            }
        }
        return null;
    }

    public double getX() {
        return positionX;
    }

    public double getY() {
        return positionY;
    }

    public double getZ() {
        return positionZ;
    }

    private void advanceOneTick() {
        applyMovementInput();

        velocityY = (velocityY - GRAVITY) * AIR_DRAG;
        positionX += velocityX;
        positionY += velocityY;
        positionZ += velocityZ;
        velocityX *= HORIZONTAL_DRAG;
        velocityZ *= HORIZONTAL_DRAG;
    }

    private void applyMovementInput() {
        float sideways = sidewaysInput * INPUT_SCALE;
        float forward = forwardInput * INPUT_SCALE;
        float inputLengthSquared = sideways * sideways + forward * forward;
        if (inputLengthSquared < MINIMUM_INPUT) {
            return;
        }

        float inputLength = Mth.sqrt(inputLengthSquared);
        inputLength = Math.max(inputLength, 1.0F);

        float movementScale = acceleration;
        if (mc.player != null && mc.player.isSprinting()) {
            movementScale *= SPRINT_ACCELERATION;
        }
        movementScale /= inputLength;
        sideways *= movementScale;
        forward *= movementScale;

        float headingRadians = headingDegrees * ((float) Math.PI / 180.0F);
        float headingSin = Mth.sin(headingRadians);
        float headingCos = Mth.cos(headingRadians);
        velocityX += sideways * headingCos - forward * headingSin;
        velocityZ += forward * headingCos + sideways * headingSin;
    }

    private BlockPos traceFootprint(Vec3 tickStart, Vec3 tickEnd, float halfWidth) {
        float halfRadius = halfWidth * 0.5F;
        Vec3[] sampleOffsets = {
                Vec3.ZERO,
                new Vec3(halfWidth, 0.0D, halfWidth),
                new Vec3(-halfWidth, 0.0D, halfWidth),
                new Vec3(halfWidth, 0.0D, -halfWidth),
                new Vec3(-halfWidth, 0.0D, -halfWidth),
                new Vec3(halfWidth, 0.0D, halfRadius),
                new Vec3(-halfWidth, 0.0D, halfRadius),
                new Vec3(halfRadius, 0.0D, halfWidth),
                new Vec3(halfRadius, 0.0D, -halfWidth)
        };

        for (Vec3 offset : sampleOffsets) {
            BlockPos hit = traceTopFace(tickStart.add(offset), tickEnd);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private BlockPos traceTopFace(Vec3 start, Vec3 end) {
        BlockHitResult hit = mc.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        boolean landedOnTop = hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP;
        return landedOnTop ? hit.getBlockPos() : null;
    }

    private Vec3 position() {
        return new Vec3(positionX, positionY, positionZ);
    }
}
