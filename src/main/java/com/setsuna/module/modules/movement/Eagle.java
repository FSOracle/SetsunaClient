package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.movement.scaffold.ScaffoldPlayerSimulation;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;

import java.util.concurrent.ThreadLocalRandom;

/** Automatically holds sneak near block edges, based on Lite's Eagle behavior. */
public final class Eagle extends Module {
    public static final Eagle INSTANCE = new Eagle();

    public final IntSetting blocksToEagleMin = add(new IntSetting("Blocks To Eagle Min", 0, 0, 10, 1));
    public final IntSetting blocksToEagleMax = add(new IntSetting("Blocks To Eagle Max", 0, 0, 10, 1));
    public final DoubleSetting edgeDistanceMin = add(new DoubleSetting("Edge Distance Min", 0.01D, 0.01D, 1.3D, 0.01D));
    public final DoubleSetting edgeDistanceMax = add(new DoubleSetting("Edge Distance Max", 0.05D, 0.01D, 1.3D, 0.01D));
    public final BooleanSetting onlyOnGround = add(new BooleanSetting("Only On Ground", true));
    public final BooleanSetting onlyWhenMovingBackward = add(new BooleanSetting("Only When Moving Backward", false));

    private int placedBlocks;
    private int currentBlocks;
    private double currentDistance;
    private boolean holdingEagle;

    private Eagle() {
        super("Eagle", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        resetCycle();
    }

    @Override
    protected void onDisable() {
        resetCycle();
    }

    @Listen(priority = -50)
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (noPlayer() || mc.player.getAbilities().flying
                || (onlyOnGround.get() && !mc.player.onGround()) || currentBlocks < 0) {
            holdingEagle = false;
            return;
        }
        if (placedBlocks > 0) {
            holdingEagle = false;
            return;
        }

        double maxDistance = Math.max(0.01D, edgeDistanceMax.get());
        double distance = Math.min(currentDistance, maxDistance);
        boolean moving = isMoving(event.getForward(), event.getStrafe());
        boolean movingBackward = event.getForward() < 0.0F;
        if (onlyWhenMovingBackward.get() && moving && !movingBackward) {
            holdingEagle = false;
            return;
        }

        if (!moving && holdingEagle) {
            event.setSneak(true);
            return;
        }

        boolean closeToEdge = ScaffoldPlayerSimulation.isCloseToEdge(
                mc, event.getForward(), event.getStrafe(), distance, false);
        if (closeToEdge && (!onlyWhenMovingBackward.get() || !moving || movingBackward)) {
            holdingEagle = true;
            event.setSneak(true);
        } else if (moving) {
            holdingEagle = false;
        }
    }

    /** Called by block placing hooks when Eagle should delay its next edge hold. */
    public void onBlockPlaced() {
        if (!isEnabled()) return;
        placedBlocks++;
        if (placedBlocks > currentBlocks) {
            resetCycle();
        }
    }

    private void resetCycle() {
        placedBlocks = 0;
        holdingEagle = false;
        currentBlocks = randomInt(blocksToEagleMin.get(), blocksToEagleMax.get());
        currentDistance = randomDouble(edgeDistanceMin.get(), edgeDistanceMax.get());
    }

    private static int randomInt(int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low == high ? low : ThreadLocalRandom.current().nextInt(low, high + 1);
    }

    private static double randomDouble(double min, double max) {
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        return low == high ? low : ThreadLocalRandom.current().nextDouble(low, high);
    }

    private static boolean isMoving(float forward, float strafe) {
        return forward != 0.0F || strafe != 0.0F;
    }
}
