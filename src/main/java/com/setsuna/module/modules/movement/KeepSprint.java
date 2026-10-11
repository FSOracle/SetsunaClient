package com.setsuna.module.modules.movement;

import com.setsuna.event.Listen;
import com.setsuna.event.events.AttackEvent;
import com.setsuna.event.events.AttackSlowDownEvent;
import com.setsuna.event.events.GroundJumpEvent;
import com.setsuna.event.events.KeyboardInputEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.WorldChangeEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.EnumSetting;
import net.minecraft.world.entity.LivingEntity;

public final class KeepSprint extends Module {

    public static final KeepSprint INSTANCE = new KeepSprint();

    public enum Mode {
        Vanilla,
        Hypixel
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Vanilla)
            .legacyValue("Normal", Mode.Vanilla));

    private boolean sprintCancelled;
    private boolean holdSprintOff;

    private KeepSprint() {
        super("Keep Sprint", Category.MOVEMENT);
    }

    @Override
    public String getInfo() {
        return mode.displayValue();
    }

    public boolean isUniversalSprintCancelled() {
        return isEnabled() && mode.is(Mode.Hypixel) && holdSprintOff;
    }

    public boolean isVanillaMode() {
        return mode.is(Mode.Vanilla);
    }

    public boolean isHypixelMode() {
        return mode.is(Mode.Hypixel);
    }

    public boolean shouldSkipUniversalKeepSprint() {
        return Velocity.INSTANCE.isHypixelReducing();
    }

    public boolean stopSprintingForKeepSprint() {
        if (mc.player == null) {
            return false;
        }
        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
            if (mc.options != null) {
                mc.options.keySprint.setDown(false);
            }
            sprintCancelled = true;
            holdSprintOff = true;
            return true;
        }
        return false;
    }

    @Listen
    private void onAttack(AttackEvent event) {
        if (!isEnabled() || !mode.is(Mode.Hypixel) || mc.player == null) return;
        if (event.getAttacker() == mc.player && event.getTarget() instanceof LivingEntity) {
            if (!shouldSkipUniversalKeepSprint()) {
                stopSprintingForKeepSprint();
            }
        }
    }

    @Listen
    private void onAttackSlowDown(AttackSlowDownEvent event) {
        if (!isEnabled()) return;
        if (mode.is(Mode.Vanilla)) {
            event.setCancelled(true);
        } else if (mode.is(Mode.Hypixel)) {
            if (!shouldSkipUniversalKeepSprint() && sprintCancelled) {
                event.setCancelled(true);
            }
        }
    }

    @Listen
    private void onGroundJump(GroundJumpEvent.Before event) {
        if (!isEnabled() || !mode.is(Mode.Hypixel) || mc.player == null) return;
        if (sprintCancelled && !mc.player.isSprinting()) {
            event.setCancelled(true);
        }
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!isEnabled() || mc.player == null) return;
        if (mode.is(Mode.Hypixel)) {
            if (mc.player.isSprinting() || !holdSprintOff) {
                sprintCancelled = false;
            }
        }
    }

    @Listen
    private void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!isEnabled() || mc.player == null) return;
        if (mode.is(Mode.Hypixel)) {
            holdSprintOff = false;
        }
    }

    @Listen
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (!isEnabled()) return;
        if (mode.is(Mode.Vanilla)) {
            event.setSprint(true);
        }
    }

    @Listen
    private void onWorldChange(WorldChangeEvent event) {
        sprintCancelled = false;
        holdSprintOff = false;
    }

    @Override
    protected void onDisable() {
        sprintCancelled = false;
        holdSprintOff = false;
    }
}
