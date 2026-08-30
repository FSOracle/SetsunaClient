package com.setsuna.util.player;

import com.setsuna.Setsuna;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;

/** Helpers for reasoning about and driving horizontal player movement. */
public final class MoveUtils {

    private static final Minecraft mc = Setsuna.mc();

    private MoveUtils() {
    }

    /** @return true when the player has any horizontal movement input this tick. */
    public static boolean isMoving() {
        if (mc.player == null || mc.player.input == null) {
            return false;
        }
        Vec2 move = mc.player.input.getMoveVector();
        return move.x != 0 || move.y != 0;
    }

    /**
     * Motion components for moving straight along the player's facing at
     * {@code speed}, ignoring strafe input. Returns {@code [dx, dz]}.
     */
    public static double[] forwardWithoutStrafe(double speed) {
        if (mc.player == null) {
            return new double[]{0.0, 0.0};
        }
        double rad = Math.toRadians(mc.player.getYRot() + 90.0F);
        return new double[]{speed * Math.cos(rad), speed * Math.sin(rad)};
    }

    /**
     * Motion components honoring the current directional input at {@code speed},
     * normalized so diagonals don't move faster. Returns {@code [dx, dz]}.
     */
    public static double[] forward(double speed) {
        if (mc.player == null || mc.player.input == null) {
            return new double[]{0.0, 0.0};
        }
        float yaw = mc.player.getYRot();
        Vec2 move = mc.player.input.getMoveVector();
        float forward = move.y;
        float left = move.x;

        if (forward != 0.0F) {
            if (left > 0.0F) {
                yaw += forward > 0.0F ? -45 : 45;
            } else if (left < 0.0F) {
                yaw += forward > 0.0F ? 45 : -45;
            }
            left = 0.0F;
            forward = forward > 0.0F ? 1.0F : -1.0F;
        }

        double rad = Math.toRadians(yaw + 90.0F);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double dx = forward * speed * cos + left * speed * sin;
        double dz = forward * speed * sin - left * speed * cos;
        return new double[]{dx, dz};
    }
}
