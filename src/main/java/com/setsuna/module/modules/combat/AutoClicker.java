package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.CrosshairUpdateEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.client.KeybindUtils;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.EntityHitResult;

import java.util.concurrent.ThreadLocalRandom;

/** Queues randomized attack clicks while the physical attack key is held. */
public final class AutoClicker extends Module {

    private static final int MAX_CATCH_UP_CLICKS = 4;

    public static final AutoClicker INSTANCE = new AutoClicker();

    private final IntSetting minCps = add(new IntSetting("Min CPS", 8, 1, 20, 1)
            .onChange(ignored -> resetSchedule()));
    private final IntSetting maxCps = add(new IntSetting("Max CPS", 12, 1, 20, 1)
            .onChange(ignored -> resetSchedule()));
    private final BooleanSetting attackCooldown = add(new BooleanSetting("Attack Cooldown", false));
    private final BooleanSetting weaponOnly = add(new BooleanSetting("Weapon Only", false));
    private final BooleanSetting clickWhileUsing = add(new BooleanSetting("Click While Using", false));
    private final BooleanSetting entityOnly = add(new BooleanSetting("Entity Only", false));

    private long nextClickNanos;

    private AutoClicker() {
        super("AutoClicker", Category.COMBAT);
    }

    @Listen(priority = Priority.HIGHEST)
    private void onCrosshairUpdate(CrosshairUpdateEvent event) {
        if (!canClick()) {
            resetSchedule();
            return;
        }

        long now = System.nanoTime();
        if (nextClickNanos == 0L) {
            nextClickNanos = now;
        }

        int limit = attackCooldown.get() ? 1 : MAX_CATCH_UP_CLICKS;
        int queued = 0;
        while (now >= nextClickNanos && queued < limit) {
            if (attackCooldown.get() && mc.player.getAttackStrengthScale(0.0F) < 1.0F) {
                nextClickNanos = now + nextDelayNanos();
                return;
            }
            if (!KeybindUtils.click(mc.options.keyAttack)) {
                resetSchedule();
                return;
            }
            queued++;
            nextClickNanos += nextDelayNanos();
        }

        if (queued == limit && now >= nextClickNanos) {
            nextClickNanos = now + nextDelayNanos();
        }
    }

    private boolean canClick() {
        if (noPlayer() || mc.gameMode == null || mc.screen != null
                || !KeybindUtils.isPressed(mc.options.keyAttack)) {
            return false;
        }
        if (!clickWhileUsing.get() && mc.player.isUsingItem()) {
            return false;
        }
        if (entityOnly.get() && !(mc.hitResult instanceof EntityHitResult)) {
            return false;
        }
        return !weaponOnly.get() || isWeapon(mc.player.getMainHandItem());
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.getItem() instanceof MaceItem
                || stack.getItem().getClass().getSimpleName().contains("Spear");
    }

    private long nextDelayNanos() {
        int minimum = Math.min(minCps.get(), maxCps.get());
        int maximum = Math.max(minCps.get(), maxCps.get());
        double cps = minimum == maximum
                ? minimum
                : ThreadLocalRandom.current().nextDouble(minimum, maximum + 1.0D);
        return Math.max(1L, Math.round(1_000_000_000.0D / cps));
    }

    private void resetSchedule() {
        nextClickNanos = 0L;
    }

    @Override
    protected void onEnable() {
        resetSchedule();
    }

    @Override
    protected void onDisable() {
        resetSchedule();
    }

    @Override
    public String getInfo() {
        return Math.min(minCps.get(), maxCps.get()) + "-"
                + Math.max(minCps.get(), maxCps.get()) + " CPS";
    }
}
