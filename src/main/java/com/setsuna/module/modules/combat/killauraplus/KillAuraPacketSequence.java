package com.setsuna.module.modules.combat.killauraplus;

/** Keeps use-item and entity-interaction packets coherent until the next movement packet. */
public final class KillAuraPacketSequence {
    private UseRotation useRotation;
    private boolean suppressEntityInteraction;

    public UseRotation beginUseAction(
            float yaw,
            float pitch,
            boolean suppressEntityInteraction) {
        if (useRotation == null) {
            useRotation = new UseRotation(yaw, pitch);
        }
        this.suppressEntityInteraction |= suppressEntityInteraction;
        return useRotation;
    }

    public boolean shouldSuppressEntityInteraction() {
        return suppressEntityInteraction;
    }

    public boolean isUseActionPending() {
        return useRotation != null;
    }

    public void onMovementPacket() {
        reset();
    }

    public void reset() {
        useRotation = null;
        suppressEntityInteraction = false;
    }

    public record UseRotation(float yaw, float pitch) {
    }
}
