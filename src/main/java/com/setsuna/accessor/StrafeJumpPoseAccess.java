package com.setsuna.accessor;

/** Render-only view of the virtual pose reported by Speed's 45-degree mode. */
public interface StrafeJumpPoseAccess {

    float setsuna$getVisualBodyOffset(float partialTick);

    float setsuna$getVisualHeadOffset(float partialTick);

    boolean setsuna$isSynchronizedStrafeTick();
}
