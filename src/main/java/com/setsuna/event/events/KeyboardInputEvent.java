package com.setsuna.event.events;

import com.setsuna.event.Event;
import net.minecraft.world.entity.player.Input;

/**
 * Fired after {@code KeyboardInput#tick} resolves raw key state into movement
 * impulses. Handlers may rewrite the impulses/flags; the mixin rebuilds the
 * player's {@link Input} and move vector from the (possibly modified) values.
 */
public final class KeyboardInputEvent extends Event {

    private final InputState state;

    public KeyboardInputEvent(boolean forward, boolean backward, boolean left, boolean right,
                              boolean jump, boolean sneak, boolean sprint) {
        // Inlined vanilla KeyboardInput#calculateImpulse (it is private): forward - backward.
        float resolvedForward = impulse(forward, backward);
        float resolvedStrafe = impulse(left, right);
        state = new InputState(resolvedForward, resolvedStrafe, jump, sneak, sprint);
    }

    private static float impulse(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
    }

    /** Rebuilds a vanilla {@link Input} from the current impulse/flag state. */
    public Input toInput() {
        return new Input(
                state.forward() > 0,
                state.forward() < 0,
                state.strafe() > 0,
                state.strafe() < 0,
                state.jump(),
                state.sneak(),
                state.sprint()
        );
    }

    public float getForward() {
        return state.forward();
    }

    public float getStrafe() {
        return state.strafe();
    }

    public float getOriginalForward() {
        return state.originalForward;
    }

    public float getOriginalStrafe() {
        return state.originalStrafe;
    }

    public boolean isJump() {
        return state.jump();
    }

    public boolean isSneak() {
        return state.sneak();
    }

    public boolean isSprint() {
        return state.sprint();
    }

    public void setForward(float forward) {
        state.forward(forward);
    }

    public void setStrafe(float strafe) {
        state.strafe(strafe);
    }

    public void setJump(boolean jump) {
        state.jump(jump);
    }

    public void setSneak(boolean sneak) {
        state.sneak(sneak);
    }

    public void setSprint(boolean sprint) {
        state.sprint(sprint);
    }

    private static final class InputState {
        private final float originalForward;
        private final float originalStrafe;
        private float forward;
        private float strafe;
        private boolean jump;
        private boolean sneak;
        private boolean sprint;

        private InputState(float forward, float strafe, boolean jump, boolean sneak, boolean sprint) {
            originalForward = forward;
            originalStrafe = strafe;
            this.forward = forward;
            this.strafe = strafe;
            this.jump = jump;
            this.sneak = sneak;
            this.sprint = sprint;
        }

        private float forward() {
            return forward;
        }

        private void forward(float value) {
            forward = value;
        }

        private float strafe() {
            return strafe;
        }

        private void strafe(float value) {
            strafe = value;
        }

        private boolean jump() {
            return jump;
        }

        private void jump(boolean value) {
            jump = value;
        }

        private boolean sneak() {
            return sneak;
        }

        private void sneak(boolean value) {
            sneak = value;
        }

        private boolean sprint() {
            return sprint;
        }

        private void sprint(boolean value) {
            sprint = value;
        }
    }
}
