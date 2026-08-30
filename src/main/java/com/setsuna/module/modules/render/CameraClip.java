package com.setsuna.module.modules.render;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Removes third-person camera collision and optionally smooths camera movement. */
public final class CameraClip extends Module {

    public static final CameraClip INSTANCE = new CameraClip();

    public final DoubleSetting distance = add(new DoubleSetting(
            "Distance", 3.5D, 1.0D, 20.0D, 0.5D));
    public final BooleanSetting action = add(new BooleanSetting("Action", true)
            .onChange(ignored -> resetCameraPos()));
    public final DoubleSetting actionSmoothness = add(new DoubleSetting(
            "Action Smoothness", 0.3D, 0.1D, 0.95D, 0.01D)
            .visibleWhen(action::get));
    public final DoubleSetting actionMaxDistance = add(new DoubleSetting(
            "Action Max Distance", 20.0D, 1.0D, 50.0D, 0.5D)
            .visibleWhen(action::get));

    private Vec3 cameraPos;

    private CameraClip() {
        super("Camera Clip", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        resetCameraPos();
    }

    @Override
    protected void onDisable() {
        resetCameraPos();
    }

    public void updateActionCamera(Vec3 targetPos) {
        if (cameraPos == null) {
            cameraPos = targetPos;
            return;
        }

        double distanceToTarget = cameraPos.distanceTo(targetPos);
        double maxDistance = actionMaxDistance.get();
        if (distanceToTarget > maxDistance) {
            cameraPos = targetPos;
            return;
        }

        double smoothFactor = actionSmoothness.get()
                * (1.0D - Math.exp(-distanceToTarget / maxDistance));
        cameraPos = new Vec3(
                Mth.lerp(smoothFactor, cameraPos.x, targetPos.x),
                Mth.lerp(smoothFactor, cameraPos.y, targetPos.y),
                Mth.lerp(smoothFactor, cameraPos.z, targetPos.z)
        );
    }

    public void resetCameraPos() {
        cameraPos = null;
    }

    public Vec3 getCameraPos() {
        return cameraPos;
    }
}
