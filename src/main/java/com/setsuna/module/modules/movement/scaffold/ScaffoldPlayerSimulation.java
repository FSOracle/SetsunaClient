package com.setsuna.module.modules.movement.scaffold;

import com.setsuna.mixin.LivingEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** One-tick land-movement snapshot used by LiquidBounce Scaffold's edge checks. */
public final class ScaffoldPlayerSimulation {

    private static final double STEP_HEIGHT = 0.5D;
    private static final double INPUT_EPSILON = 0.003D;
    private static final List<VoxelShape> NO_ENTITY_COLLISIONS = List.of();

    private ScaffoldPlayerSimulation() {
    }

    public static Snapshot simulate(
            Minecraft mc,
            float forward,
            float strafe,
            boolean jumping,
            boolean sneaking,
            boolean forceSafeWalk) {
        if (mc == null || mc.player == null || mc.level == null) {
            return null;
        }

        LocalPlayer player = mc.player;
        Vec3 position = player.position();
        Vec3 velocity = trimSmallComponents(player.getDeltaMovement());
        boolean onGround = player.onGround();

        if (jumping && onGround && ((LivingEntityAccessor) player).setsuna$getNoJumpDelay() == 0) {
            double jumpPower = ((LivingEntityAccessor) player).setsuna$invokeGetJumpPower();
            velocity = new Vec3(velocity.x, Math.max(jumpPower, velocity.y), velocity.z);
            if (player.isSprinting()) {
                float radians = player.getYRot() * ((float) Math.PI / 180.0F);
                velocity = velocity.add(
                        -Mth.sin(radians) * 0.2F,
                        0.0D,
                        Mth.cos(radians) * 0.2F);
            }
        }

        if (player.isInWater() || player.isInLava() || player.isFallFlying()) {
            return simulateFallback(player, position, velocity, onGround, sneaking || forceSafeWalk);
        }

        double inputForward = Math.signum(forward);
        double inputStrafe = Math.signum(strafe);
        if (sneaking) {
            inputForward *= 0.3D;
            inputStrafe *= 0.3D;
        }
        Vec3 movementInput = new Vec3(inputStrafe * 0.98D, 0.0D, inputForward * 0.98D);

        BlockPos frictionPos = player.getBlockPosBelowThatAffectsMyMovement();
        float blockFriction = mc.level.getBlockState(frictionPos).getBlock().getFriction();
        float friction = onGround ? blockFriction * 0.91F : 0.91F;
        float movementSpeed = onGround
                ? 0.10000000149011612F * (0.21600002F
                / (blockFriction * blockFriction * blockFriction))
                : player.isSprinting() ? 0.026F : 0.02F;

        Vec3 movement = velocity.add(getInputVector(movementInput, movementSpeed, player.getYRot()));
        if (mc.level.getBlockState(player.blockPosition()).is(Blocks.COBWEB)) {
            Vec3 multiplier = player.hasEffect(MobEffects.WEAVING)
                    ? new Vec3(0.5D, 0.25D, 0.5D)
                    : new Vec3(0.25D, 0.05D, 0.25D);
            movement = movement.multiply(multiplier);
        }

        BackOffResult backOff = maybeBackOffFromEdge(
                player, position, movement, onGround, sneaking || forceSafeWalk);
        CollisionResult collision = collide(player, position, backOff.movement(), onGround);
        Vec3 nextPosition = collision.movement().lengthSqr() > 1.0E-7D
                ? position.add(collision.movement())
                : position;

        Vec3 movedVelocity = movement;
        if (collision.collidedX() || collision.collidedY() || collision.collidedZ()) {
            movedVelocity = new Vec3(
                    collision.collidedX() ? 0.0D : movedVelocity.x,
                    collision.onGround() ? 0.0D : movedVelocity.y,
                    collision.collidedZ() ? 0.0D : movedVelocity.z);
        }

        double gravity = 0.08D;
        if (movedVelocity.y <= 0.0D && player.hasEffect(MobEffects.SLOW_FALLING)) {
            gravity = 0.01D;
        }
        double verticalMovement = movedVelocity.y;
        MobEffectInstance levitation = player.getEffect(MobEffects.LEVITATION);
        if (levitation != null) {
            verticalMovement += (0.05D * (levitation.getAmplifier() + 1) - movedVelocity.y) * 0.2D;
        } else if (!player.isNoGravity()) {
            verticalMovement -= gravity;
        }

        Vec3 nextVelocity = player.shouldDiscardFriction()
                ? new Vec3(movedVelocity.x, verticalMovement, movedVelocity.z)
                : new Vec3(
                movedVelocity.x * friction,
                verticalMovement * 0.9800000190734863D,
                movedVelocity.z * friction);
        if (player.getAbilities().flying) {
            nextVelocity = new Vec3(nextVelocity.x, player.getDeltaMovement().y * 0.6D, nextVelocity.z);
        }

        return new Snapshot(nextPosition, nextVelocity, collision.onGround(), backOff.clipLedged());
    }

    public static boolean isCloseToEdge(
            Minecraft mc,
            float forward,
            float strafe,
            double distance,
            boolean forceSafeWalk) {
        Snapshot snapshot = simulate(mc, forward, strafe, false, false, forceSafeWalk);
        if (snapshot == null || mc.player == null || mc.level == null) {
            return false;
        }

        Vec3 horizontalVelocity = snapshot.velocity().multiply(1.0D, 0.0D, 1.0D);
        Vec3 direction;
        if (horizontalVelocity.horizontalDistanceSqr() > INPUT_EPSILON * INPUT_EPSILON) {
            direction = horizontalVelocity.normalize();
        } else if (forward != 0.0F || strafe != 0.0F) {
            float movementYaw = ScaffoldMovementPlanner.movementAngle(mc.player.getYRot(), forward, strafe);
            direction = Vec3.directionFromRotation(0.0F, movementYaw);
        } else {
            direction = Vec3.ZERO;
        }

        Vec3 position = mc.player.position();
        Vec3 from = position.add(0.0D, -0.1D, 0.0D);
        Vec3 to = from.add(direction.scale(distance));
        if (distance > 0.0D && direction.lengthSqr() > 1.0E-12D
                && ScaffoldEdgeCollision.find(mc, from, to, 0.5D) != null) {
            return true;
        }

        Vec3 positionInTwoTicks = snapshot.position().add(horizontalVelocity);
        return wouldBeCloseToFallOff(mc, position) || wouldBeCloseToFallOff(mc, positionInTwoTicks);
    }

    private static Snapshot simulateFallback(
            LocalPlayer player,
            Vec3 position,
            Vec3 velocity,
            boolean onGround,
            boolean safeWalk) {
        BackOffResult backOff = maybeBackOffFromEdge(player, position, velocity, onGround, safeWalk);
        CollisionResult collision = collide(player, position, backOff.movement(), onGround);
        Vec3 nextPosition = collision.movement().lengthSqr() > 1.0E-7D
                ? position.add(collision.movement())
                : position;
        return new Snapshot(nextPosition, velocity, collision.onGround(), backOff.clipLedged());
    }

    private static Vec3 trimSmallComponents(Vec3 movement) {
        return new Vec3(
                Math.abs(movement.x) < INPUT_EPSILON ? 0.0D : movement.x,
                Math.abs(movement.y) < INPUT_EPSILON ? 0.0D : movement.y,
                Math.abs(movement.z) < INPUT_EPSILON ? 0.0D : movement.z);
    }

    private static Vec3 getInputVector(Vec3 input, float speed, float yaw) {
        double lengthSquared = input.lengthSqr();
        if (lengthSquared < 1.0E-7D) {
            return Vec3.ZERO;
        }
        Vec3 scaled = (lengthSquared > 1.0D ? input.normalize() : input).scale(speed);
        float sine = Mth.sin(yaw * 0.017453292F);
        float cosine = Mth.cos(yaw * 0.017453292F);
        return new Vec3(
                scaled.x * cosine - scaled.z * sine,
                scaled.y,
                scaled.z * cosine + scaled.x * sine);
    }

    private static BackOffResult maybeBackOffFromEdge(
            LocalPlayer player,
            Vec3 position,
            Vec3 movement,
            boolean onGround,
            boolean applyClippedMovement) {
        if (movement.y > 0.0D || !isAboveGround(player, onGround)) {
            return new BackOffResult(movement, false);
        }

        double adjustedX = movement.x;
        double adjustedZ = movement.z;
        double step = 0.05D;
        while (adjustedX != 0.0D
                && player.level().noCollision(player, player.getBoundingBox().move(adjustedX, -STEP_HEIGHT, 0.0D))) {
            adjustedX = reduceTowardsZero(adjustedX, step);
        }
        while (adjustedZ != 0.0D
                && player.level().noCollision(player, player.getBoundingBox().move(0.0D, -STEP_HEIGHT, adjustedZ))) {
            adjustedZ = reduceTowardsZero(adjustedZ, step);
        }
        while (adjustedX != 0.0D && adjustedZ != 0.0D
                && player.level().noCollision(
                player, player.getBoundingBox().move(adjustedX, -STEP_HEIGHT, adjustedZ))) {
            adjustedX = reduceTowardsZero(adjustedX, step);
            adjustedZ = reduceTowardsZero(adjustedZ, step);
        }

        boolean clipLedged = adjustedX != movement.x || adjustedZ != movement.z;
        Vec3 adjusted = applyClippedMovement
                ? new Vec3(adjustedX, movement.y, adjustedZ)
                : movement;
        return new BackOffResult(adjusted, clipLedged);
    }

    private static boolean isAboveGround(LocalPlayer player, boolean onGround) {
        return onGround || player.fallDistance < STEP_HEIGHT
                && !player.level().noCollision(
                player,
                player.getBoundingBox().move(0.0D, player.fallDistance - STEP_HEIGHT, 0.0D));
    }

    private static double reduceTowardsZero(double value, double step) {
        if (value < step && value >= -step) {
            return 0.0D;
        }
        return value > 0.0D ? value - step : value + step;
    }

    private static CollisionResult collide(
            LocalPlayer player,
            Vec3 position,
            Vec3 movement,
            boolean onGround) {
        AABB collisionBox = new AABB(-0.3D, 0.0D, -0.3D, 0.3D, 1.8D, 0.3D).move(position);
        Vec3 adjusted = movement.lengthSqr() == 0.0D
                ? movement
                : Entity.collideBoundingBox(player, movement, collisionBox, player.level(), NO_ENTITY_COLLISIONS);
        boolean collidedX = movement.x != adjusted.x;
        boolean collidedY = movement.y != adjusted.y;
        boolean collidedZ = movement.z != adjusted.z;
        boolean onGroundOrFalling = onGround || collidedY && movement.y < 0.0D;

        if (player.maxUpStep() > 0.0F && onGroundOrFalling && (collidedX || collidedZ)) {
            double stepHeight = player.maxUpStep();
            Vec3 stepped = Entity.collideBoundingBox(
                    player,
                    new Vec3(movement.x, stepHeight, movement.z),
                    collisionBox,
                    player.level(),
                    NO_ENTITY_COLLISIONS);
            Vec3 stepUp = Entity.collideBoundingBox(
                    player,
                    new Vec3(0.0D, stepHeight, 0.0D),
                    collisionBox.expandTowards(movement.x, 0.0D, movement.z),
                    player.level(),
                    NO_ENTITY_COLLISIONS);
            Vec3 stepDown = Entity.collideBoundingBox(
                    player,
                    new Vec3(movement.x, 0.0D, movement.z),
                    collisionBox.move(stepUp),
                    player.level(),
                    NO_ENTITY_COLLISIONS).add(stepUp);
            if (stepUp.y < stepHeight && stepDown.horizontalDistanceSqr() > stepped.horizontalDistanceSqr()) {
                stepped = stepDown;
            }
            if (stepped.horizontalDistanceSqr() > adjusted.horizontalDistanceSqr()) {
                adjusted = stepped.add(Entity.collideBoundingBox(
                        player,
                        new Vec3(0.0D, -stepped.y + movement.y, 0.0D),
                        collisionBox.move(stepped),
                        player.level(),
                        NO_ENTITY_COLLISIONS));
            }
        }

        collidedX = movement.x != adjusted.x;
        collidedY = movement.y != adjusted.y;
        collidedZ = movement.z != adjusted.z;
        return new CollisionResult(
                adjusted,
                collidedX,
                collidedY,
                collidedZ,
                collidedY && movement.y < 0.0D);
    }

    private static boolean wouldBeCloseToFallOff(Minecraft mc, Vec3 position) {
        LocalPlayer player = mc.player;
        AABB hitbox = player.getDimensions(player.getPose())
                .makeBoundingBox(position)
                .inflate(-0.05D, 0.0D, -0.05D)
                .move(0.0D, player.fallDistance - player.maxUpStep(), 0.0D);
        return mc.level.noCollision(player, hitbox);
    }

    public record Snapshot(Vec3 position, Vec3 velocity, boolean onGround, boolean clipLedged) {
    }

    private record BackOffResult(Vec3 movement, boolean clipLedged) {
    }

    private record CollisionResult(
            Vec3 movement,
            boolean collidedX,
            boolean collidedY,
            boolean collidedZ,
            boolean onGround) {
    }
}
