package com.setsuna.event.events;

import com.setsuna.event.Event;
import net.minecraft.world.entity.player.Input;

/**
 * Fired after {@code KeyboardInput#tick} resolves raw key state into movement
 * impulses. Handlers may rewrite the impulses/flags; the mixin rebuilds the
 * player's {@link Input} and move vector from the (possibly modified) values.
 */
public final class KeyboardInputEvent extends Event {

    private final float originalForward;
    private final float originalStrafe;
    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    public KeyboardInputEvent(boolean forward, boolean backward, boolean left, boolean right,
                              boolean jump, boolean sneak, boolean sprint) {
        // Inlined vanilla KeyboardInput#calculateImpulse (it is private): forward - backward.
        this.originalForward = impulse(forward, backward);
        this.originalStrafe = impulse(left, right);
        this.forward = originalForward;
        this.strafe = originalStrafe;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;
    }

    private static float impulse(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
    }

    /** Rebuilds a vanilla {@link Input} from the current impulse/flag state. */
    public Input toInput() {
        return new Input(
                forward > 0,
                forward < 0,
                strafe > 0,
                strafe < 0,
                jump,
                sneak,
                sprint
        );
    }

    public float getForward() {
        return forward;
    }

    public float getStrafe() {
        return strafe;
    }

    public float getOriginalForward() {
        return originalForward;
    }

    public float getOriginalStrafe() {
        return originalStrafe;
    }

    public boolean isJump() {
        return jump;
    }

    public boolean isSneak() {
        return sneak;
    }

    public boolean isSprint() {
        return sprint;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }
}
