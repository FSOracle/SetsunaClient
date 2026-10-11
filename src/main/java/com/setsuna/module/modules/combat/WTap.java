package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PreAttackEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.client.KeybindUtils;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/** Minecraft 26.1.2 port of Vape 4.21's WTap state machine. */
public final class WTap extends Module {

    public static final WTap INSTANCE = new WTap();

    private final IntSetting chance = add(new IntSetting("Chance", 90, 0, 100, 1));
    private final IntSetting releaseDelay = add(new IntSetting("Release Delay", 0, 0, 500, 50));
    private final IntSetting rePressDelay = add(new IntSetting("Re-press Delay", 0, 0, 500, 50));
    private final BooleanSetting selectHits = add(new BooleanSetting("Select Hits", true));

    private boolean releasePending;
    private boolean rePressPending;
    private long releaseStartedAt;
    private long rePressStartedAt;

    private WTap() {
        super("WTap", Category.COMBAT);
    }

    @Listen
    private void onPreAttack(PreAttackEvent event) {
        if (!(event.target() instanceof LivingEntity target)
                || releasePending || rePressPending) {
            return;
        }
        if (selectHits.get() && target.invulnerableTime > 14) {
            return;
        }
        if (chance.get() < ThreadLocalRandom.current().nextDouble(100.0D)) {
            return;
        }

        releasePending = true;
        releaseStartedAt = System.currentTimeMillis();
        handleRelease(releaseStartedAt);
    }

    @Listen
    private void onTick(TickEvent.Pre event) {
        if (mc.screen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (releasePending) {
            handleRelease(now);
        } else if (rePressPending) {
            handleRePress(now);
        }
    }

    private void handleRelease(long now) {
        if (now - releaseStartedAt < releaseDelay.get()) {
            return;
        }
        mc.options.keyUp.setDown(false);
        releasePending = false;
        rePressPending = true;
        rePressStartedAt = now;
    }

    private void handleRePress(long now) {
        if (now - rePressStartedAt < rePressDelay.get()) {
            return;
        }
        if (KeybindUtils.isPressed(mc.options.keyUp)) {
            mc.options.keyUp.setDown(true);
        }
        rePressPending = false;
    }

    private void resetState(boolean restoreForward) {
        releasePending = false;
        rePressPending = false;
        releaseStartedAt = 0L;
        rePressStartedAt = 0L;
        if (restoreForward && mc.options != null && KeybindUtils.isPressed(mc.options.keyUp)) {
            mc.options.keyUp.setDown(true);
        }
    }

    @Override
    protected void onEnable() {
        resetState(false);
    }

    @Override
    protected void onDisable() {
        resetState(true);
    }

    @Override
    public String getInfo() {
        return releaseDelay.get() + " ms";
    }
}
