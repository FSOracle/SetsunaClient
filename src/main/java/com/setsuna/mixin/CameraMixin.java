package com.setsuna.mixin;

import com.setsuna.Setsuna;
import com.setsuna.module.modules.render.CameraClip;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public class CameraMixin {

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void setsuna$cameraClipDistance(float cameraDistance, CallbackInfoReturnable<Float> cir) {
        CameraClip cameraClip = CameraClip.INSTANCE;
        if (cameraClip.isEnabled()) {
            cir.setReturnValue(cameraClip.distance.get().floatValue());
        }
    }

    @ModifyArgs(
            method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void setsuna$actionCameraPosition(Args args) {
        CameraClip cameraClip = CameraClip.INSTANCE;
        if (!cameraClip.isEnabled() || !cameraClip.action.get()) {
            return;
        }

        if (Setsuna.mc().options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            cameraClip.resetCameraPos();
            return;
        }

        Vec3 targetPos = new Vec3(args.get(0), args.get(1), args.get(2));
        cameraClip.updateActionCamera(targetPos);
        Vec3 cameraPos = cameraClip.getCameraPos();
        if (cameraPos != null) {
            args.set(0, cameraPos.x);
            args.set(1, cameraPos.y);
            args.set(2, cameraPos.z);
        }
    }
}
