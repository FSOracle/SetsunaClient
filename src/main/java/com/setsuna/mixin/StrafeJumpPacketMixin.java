package com.setsuna.mixin;

import com.setsuna.accessor.StrafeJumpPoseAccess;
import com.setsuna.manager.RotationManager;
import com.setsuna.module.modules.movement.Speed;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.Rot2f;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ports StrafeJump's input, packet yaw, and third-person pose state without
 * leaving the virtual yaw on the local camera.
 */
@Mixin(LocalPlayer.class)
public class StrafeJumpPacketMixin implements StrafeJumpPoseAccess {

    @Unique private boolean setsuna$silentRotationClaimed;
    @Unique private float setsuna$lastVisualBodyOffset;
    @Unique private float setsuna$visualBodyOffset;
    @Unique private float setsuna$lastVisualHeadOffset;
    @Unique private float setsuna$visualHeadOffset;
    @Unique private boolean setsuna$synchronizedStrafeTick;
    @Unique private Input setsuna$physicalPlayerInput;

    @Inject(method = "applyInput", at = @At("TAIL"))
    private void setsuna$afterMovementInput(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        Speed speed = Speed.INSTANCE;
        boolean active = speed.shouldApplyFortyFive(self);

        float side = self.xxa;
        float forward = self.zza;
        float length = (float) Math.sqrt(side * side + forward * forward);
        Speed.StrafeMode strafeMode = speed.strafeMode.get();

        boolean synchronizedEligible = active
                && forward > 1.0E-4F
                && Math.abs(side) < 1.0E-4F;
        setsuna$synchronizedStrafeTick = strafeMode == Speed.StrafeMode.PREDICTION_SYNCHRONIZED
                && synchronizedEligible;

        if (!active || length < 1.0E-4F
                || (strafeMode == Speed.StrafeMode.PREDICTION_SYNCHRONIZED
                && !synchronizedEligible)) {
            return;
        }

        if (strafeMode == Speed.StrafeMode.PREDICTION_SYNCHRONIZED) {
            float scale = 0.70710677F / length;
            self.xxa = (side + forward) * scale;
            self.zza = (forward - side) * scale;

            setsuna$physicalPlayerInput = self.input.keyPresses;
            float virtualSide = side + forward;
            float virtualForward = forward - side;
            Input physical = setsuna$physicalPlayerInput;
            self.input.keyPresses = new Input(
                    virtualForward > 1.0E-4F,
                    virtualForward < -1.0E-4F,
                    virtualSide > 1.0E-4F,
                    virtualSide < -1.0E-4F,
                    physical.jump(),
                    physical.shift(),
                    physical.sprint());
        } else if (strafeMode == Speed.StrafeMode.PACKET_SIMULATION) {
            self.xxa = side + forward;
            self.zza = forward - side;
        } else {
            float targetLength = Math.min(1.0F, length / 0.98F);
            float scale = targetLength / length;
            self.xxa = side * scale;
            self.zza = forward * scale;
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V",
            shift = At.Shift.AFTER))
    private void setsuna$updatePoseAfterMovement(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        setsuna$updateVisualPose(self, setsuna$isPacketStrafeActive(self));
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;sendPosition()V"))
    private void setsuna$beforeMovementPacket(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (!setsuna$isPacketStrafeActive(self)) {
            return;
        }

        setsuna$silentRotationClaimed = RotationManager.INSTANCE.claimSilentRotation(
                Speed.INSTANCE,
                new Rot2f(self.getYRot() + 45.0F, self.getXRot()),
                Priority.Lowest
        );
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;sendPosition()V",
            shift = At.Shift.AFTER))
    private void setsuna$afterMovementPacket(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        setsuna$releaseSilentRotation();
        setsuna$restorePhysicalInput(self);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void setsuna$finishMovementPacketTick(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        setsuna$releaseSilentRotation();
        setsuna$restorePhysicalInput(self);
    }

    @Unique
    private void setsuna$releaseSilentRotation() {
        if (!setsuna$silentRotationClaimed) {
            return;
        }
        RotationManager.INSTANCE.releaseSilentRotation(Speed.INSTANCE);
        setsuna$silentRotationClaimed = false;
    }

    @Unique
    private boolean setsuna$isPacketStrafeActive(LocalPlayer player) {
        Speed speed = Speed.INSTANCE;
        if (!speed.shouldApplyFortyFive(player)) {
            return false;
        }
        return !speed.strafeMode.is(Speed.StrafeMode.PREDICTION_SYNCHRONIZED)
                || setsuna$synchronizedStrafeTick;
    }

    @Unique
    private void setsuna$restorePhysicalInput(LocalPlayer player) {
        if (setsuna$physicalPlayerInput != null) {
            player.input.keyPresses = setsuna$physicalPlayerInput;
            setsuna$physicalPlayerInput = null;
        }
    }

    @Unique
    private void setsuna$updateVisualPose(LocalPlayer player, boolean active) {
        setsuna$lastVisualBodyOffset = setsuna$visualBodyOffset;
        setsuna$lastVisualHeadOffset = setsuna$visualHeadOffset;

        float targetHeadOffset = active ? 45.0F : 0.0F;
        float headDelta = Mth.wrapDegrees(targetHeadOffset - setsuna$visualHeadOffset);
        float turnSpeed = Speed.INSTANCE.strafeTurnSpeed.get().floatValue();
        setsuna$visualHeadOffset += Mth.clamp(headDelta, -turnSpeed, turnSpeed);

        float targetBodyOffset = active && player.attackAnim > 0.0F
                ? setsuna$visualHeadOffset : 0.0F;
        setsuna$visualBodyOffset += Mth.wrapDegrees(
                targetBodyOffset - setsuna$visualBodyOffset) * 0.3F;

        float relative = Mth.wrapDegrees(setsuna$visualHeadOffset - setsuna$visualBodyOffset);
        if (Math.abs(relative) > 50.0F) {
            setsuna$visualBodyOffset += relative - Math.copySign(50.0F, relative);
        }
    }

    @Override
    public float setsuna$getVisualBodyOffset(float partialTick) {
        return Mth.rotLerp(partialTick, setsuna$lastVisualBodyOffset, setsuna$visualBodyOffset);
    }

    @Override
    public float setsuna$getVisualHeadOffset(float partialTick) {
        return Mth.rotLerp(partialTick, setsuna$lastVisualHeadOffset, setsuna$visualHeadOffset);
    }

    @Override
    public boolean setsuna$isSynchronizedStrafeTick() {
        return setsuna$synchronizedStrafeTick;
    }
}
