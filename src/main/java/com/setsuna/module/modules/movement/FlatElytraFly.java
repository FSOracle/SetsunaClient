package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.events.MoveEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.player.FindItemResult;
import com.setsuna.util.player.InvUtils;
import com.setsuna.util.player.MoveUtils;
import com.setsuna.util.timer.TimerUtils;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

public final class FlatElytraFly extends Module {

    public static final FlatElytraFly INSTANCE = new FlatElytraFly();

    private final BooleanSetting autoStop = add(new BooleanSetting("Auto Stop", true));
    private final BooleanSetting autoStart = add(new BooleanSetting("Auto Start", true));
    private final BooleanSetting firework = add(new BooleanSetting("Firework", false));
    private final IntSetting fireworkDelay = add(new IntSetting("Firework Delay", 1000, 0, 20000, 50)
            .visibleWhen(firework::get));
    private final DoubleSetting startTimeout = add(new DoubleSetting("Start Timeout", 0.5, 0.1, 1.0, 0.1)
            .visibleWhen(autoStart::get));
    private final DoubleSetting upPitch = add(new DoubleSetting("Up Pitch", 0.0, 0.0, 90.0, 0.1));
    private final DoubleSetting upFactor = add(new DoubleSetting("Up Factor", 1.0, 0.0, 10.0, 0.1));
    private final DoubleSetting fallSpeed = add(new DoubleSetting("Fall Speed", 1.0, 0.0, 10.0, 0.1));
    private final DoubleSetting speed = add(new DoubleSetting("Speed", 1.0, 0.1, 10.0, 0.1));
    private final BooleanSetting speedLimit = add(new BooleanSetting("Speed Limit", true));
    private final DoubleSetting maximumSpeed = add(new DoubleSetting("Maximum Speed", 2.5, 0.1, 10.0, 0.1)
            .visibleWhen(speedLimit::get));
    private final BooleanSetting noDrag = add(new BooleanSetting("No Drag", false));
    private final DoubleSetting downSpeed = add(new DoubleSetting("Down Speed", 1.0, 0.1, 10.0, 0.1));

    private final TimerUtils fireworkTimer = new TimerUtils();
    private final TimerUtils instantFlyTimer = new TimerUtils();
    private boolean hasUsableElytra;

    private FlatElytraFly() {
        super("Flat Elytra Fly", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        hasUsableElytra = false;
        fireworkTimer.setMs(Long.MAX_VALUE / 4);
        instantFlyTimer.setMs(Long.MAX_VALUE / 4);
    }

    @Override
    protected void onDisable() {
        hasUsableElytra = false;
        InvUtils.swapBack();
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer() || mc.getConnection() == null) {
            hasUsableElytra = false;
            return;
        }

        hasUsableElytra = isWearingUsableElytra();
        if (!hasUsableElytra) {
            return;
        }
        if (mc.player.isFallFlying()) {
            if (firework.get()
                    && hasHorizontalInput()
                    && fireworkTimer.passedMillis(fireworkDelay.get())
                    && useFirework()) {
                fireworkTimer.reset();
            }
            return;
        }
        if (!autoStart.get()
                || mc.player.onGround()
                || mc.options.keyShift.isDown()
                || mc.player.getDeltaMovement().y >= 0.0
                || !instantFlyTimer.passedSecond(startTimeout.get())) {
            return;
        }

        instantFlyTimer.reset();
        mc.getConnection().send(new ServerboundPlayerCommandPacket(
                mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        mc.player.startFallFlying();
    }

    @Listen
    private void onMove(MoveEvent event) {
        if (noPlayer() || !hasUsableElytra || !mc.player.isFallFlying()) {
            return;
        }

        Vec3 motion = firework.get() ? fireworkMotion() : controlledMotion();
        motion = stopAtUnloadedChunk(motion);
        mc.player.setDeltaMovement(motion);
        event.setX(motion.x);
        event.setY(motion.y);
        event.setZ(motion.z);
        event.cancel();
    }

    private Vec3 fireworkMotion() {
        double vertical;
        if (mc.options.keyShift.isDown() && mc.options.keyJump.isDown()) {
            vertical = 0.0;
        } else if (mc.options.keyShift.isDown()) {
            vertical = -downSpeed.get();
        } else if (mc.options.keyJump.isDown()) {
            vertical = upFactor.get();
        } else {
            vertical = -3.0E-11 * fallSpeed.get();
        }
        double[] horizontal = MoveUtils.forward(speed.get());
        return new Vec3(horizontal[0], vertical, horizontal[1]);
    }

    private Vec3 controlledMotion() {
        Vec3 current = mc.player.getDeltaMovement();
        Vec3 look = rotationVector((float) -upPitch.get(), mc.player.getYRot());
        double lookDistance = Math.hypot(look.x, look.z);
        double motionDistance = Math.hypot(current.x, current.z);

        double x = current.x;
        double y = current.y;
        double z = current.z;
        boolean jump = mc.options.keyJump.isDown();
        boolean sneak = mc.options.keyShift.isDown();

        if (sneak) {
            y = -downSpeed.get();
        } else if (!jump) {
            y = -3.0E-11 * fallSpeed.get();
        }

        if (jump) {
            double threshold = upFactor.get() / 10.0;
            if (motionDistance > threshold && lookDistance > 0.0) {
                double rawUpSpeed = motionDistance * 0.01325;
                y += rawUpSpeed * 3.2;
                x -= look.x * rawUpSpeed / lookDistance;
                z -= look.z * rawUpSpeed / lookDistance;
            } else {
                double[] horizontal = MoveUtils.forward(speed.get());
                x = horizontal[0];
                z = horizontal[1];
            }
        }

        if (lookDistance > 0.0) {
            x += (look.x / lookDistance * motionDistance - x) * 0.1;
            z += (look.z / lookDistance * motionDistance - z) * 0.1;
        }
        if (!jump) {
            double[] horizontal = MoveUtils.forward(speed.get());
            x = horizontal[0];
            z = horizontal[1];
        }
        if (!noDrag.get()) {
            x *= 0.98;
            y *= 0.99;
            z *= 0.98;
        }
        if (speedLimit.get()) {
            double horizontalSpeed = Math.hypot(x, z);
            if (horizontalSpeed > maximumSpeed.get()) {
                double factor = maximumSpeed.get() / horizontalSpeed;
                x *= factor;
                z *= factor;
            }
        }
        return new Vec3(x, y, z);
    }

    private Vec3 stopAtUnloadedChunk(Vec3 motion) {
        if (!autoStop.get()) {
            return motion;
        }
        int chunkX = Mth.floor((mc.player.getX() + motion.x) / 16.0);
        int chunkZ = Mth.floor((mc.player.getZ() + motion.z) / 16.0);
        if (mc.level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null) {
            return motion;
        }
        return new Vec3(0.0, motion.y, 0.0);
    }

    private boolean useFirework() {
        if (mc.gameMode == null) {
            return false;
        }
        FindItemResult rocket = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!rocket.found()) {
            return false;
        }

        InteractionHand hand = rocket.getHand();
        boolean swapped = hand == InteractionHand.MAIN_HAND
                && rocket.slot() != mc.player.getInventory().getSelectedSlot();
        if (swapped) {
            InvUtils.swap(rocket.slot(), true);
        }
        try {
            InteractionResult result = mc.gameMode.useItem(mc.player, hand);
            if (result.consumesAction()) {
                mc.player.swing(hand);
                return true;
            }
            return false;
        } finally {
            if (swapped) {
                InvUtils.swapBack();
            }
        }
    }

    private Vec3 rotationVector(float pitch, float yaw) {
        float pitchRadians = pitch * ((float) Math.PI / 180.0F);
        float yawRadians = -yaw * ((float) Math.PI / 180.0F);
        float yawCosine = Mth.cos(yawRadians);
        float yawSine = Mth.sin(yawRadians);
        float pitchCosine = Mth.cos(pitchRadians);
        float pitchSine = Mth.sin(pitchRadians);
        return new Vec3(yawSine * pitchCosine, -pitchSine, yawCosine * pitchCosine);
    }

    private boolean hasHorizontalInput() {
        return mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown();
    }

    private boolean isWearingUsableElytra() {
        return LivingEntity.canGlideUsing(
                mc.player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST);
    }
}
