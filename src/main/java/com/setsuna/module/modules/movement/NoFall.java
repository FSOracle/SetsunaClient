package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.SendPositionEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;

public final class NoFall extends Module {

    public static final NoFall INSTANCE = new NoFall();

    private enum Mode {
        NCP,
        GroundSpoof,
        GrimSimulation
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.NCP));
    private final DoubleSetting fallDistance = add(new DoubleSetting(
            "Fall Distance", 3.0, 3.0, 16.0, 1.0));

    private boolean falling;
    private boolean simulateJump;

    private NoFall() {
        super("No Fall", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @Override
    public String getInfo() {
        return mode.get().name();
    }

    @Listen
    private void onSendPosition(SendPositionEvent event) {
        if (noPlayer()) {
            resetState();
            return;
        }

        if (mc.player.fallDistance > fallDistance.get()) {
            falling = true;
        }

        if (falling && mc.player.onGround()) {
            switch (mode.get()) {
                case NCP, GroundSpoof -> event.setOnGround(true);
                case GrimSimulation -> {
                    event.setY(event.getY() + 0.1);
                    simulateJump = true;
                }
            }
            falling = false;
        }

        if (mode.is(Mode.NCP) && mc.player.fallDistance > fallDistance.get()) {
            event.setOnGround(true);
        }
    }

    @Listen
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (simulateJump) {
            event.setJump(true);
            simulateJump = false;
        }
    }

    private void resetState() {
        falling = false;
        simulateJump = false;
    }
}
