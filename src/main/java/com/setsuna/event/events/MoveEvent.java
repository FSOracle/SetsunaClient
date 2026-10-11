package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;

/**
 * Fired for each {@code LocalPlayer#move} with the intended motion delta.
 * Handlers may rewrite {@link #x}/{@link #y}/{@link #z} and {@link #cancel()} to
 * have the mixin re-issue the move with the modified vector.
 */
public final class MoveEvent extends CancellableEvent {

    private final Motion motion;

    public MoveEvent(double x, double y, double z) {
        motion = new Motion(x, y, z);
    }

    public double getX() {
        return motion.axis(Axis.X);
    }

    public double getY() {
        return motion.axis(Axis.Y);
    }

    public double getZ() {
        return motion.axis(Axis.Z);
    }

    public void setX(double x) {
        motion.axis(Axis.X, x);
    }

    public void setY(double y) {
        motion.axis(Axis.Y, y);
    }

    public void setZ(double z) {
        motion.axis(Axis.Z, z);
    }

    private enum Axis {
        X, Y, Z
    }

    private static final class Motion {
        private final double[] components;

        private Motion(double x, double y, double z) {
            components = new double[]{x, y, z};
        }

        private double axis(Axis axis) {
            return components[axis.ordinal()];
        }

        private void axis(Axis axis, double value) {
            components[axis.ordinal()] = value;
        }
    }
}
