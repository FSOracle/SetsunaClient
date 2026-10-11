package com.setsuna.module.modules.combat;

import com.setsuna.event.CancellableEvent;
import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyInputEvent;
import com.setsuna.event.events.MouseButtonEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.client.AttackKeyController;
import com.setsuna.util.client.KeybindUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Minecraft 26.1.2 port of Vape 4.21's Triggerbot module. */
public final class TriggerBot extends Module {

    private static final long ACTIVATION_DELAY_MILLIS = 50L;
    private static final long SELECT_GRACE_MILLIS = 1_000L;

    public static final TriggerBot INSTANCE = new TriggerBot();

    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", false));
    private final BooleanSetting peaceful = add(new BooleanSetting("Peaceful", false));
    private final BooleanSetting neutral = add(new BooleanSetting("Neutral", false));
    private final BooleanSetting ignoreNaked = add(new BooleanSetting("Ignore Naked", false));
    private final BooleanSetting ignoreInvisible = add(new BooleanSetting("Ignore Invisible", false));
    private final BooleanSetting ignoreBehindWalls = add(new BooleanSetting("Ignore Behind Walls", false));

    private final DoubleSetting extraDelayMin = add(new DoubleSetting("Extra Delay Min", 0.0D, -20.0D, 20.0D, 0.1D));
    private final DoubleSetting extraDelayMax = add(new DoubleSetting("Extra Delay Max", 0.0D, -20.0D, 20.0D, 0.1D));
    private final IntSetting mouseOverDelayMin = add(new IntSetting("Mouse Over Delay Min", 0, 0, 200, 10));
    private final IntSetting mouseOverDelayMax = add(new IntSetting("Mouse Over Delay Max", 0, 0, 200, 10));
    private final BooleanSetting requireMouseDown = add(new BooleanSetting("Require Mouse Down", false));
    private final BooleanSetting ignoreActivationClick = add(new BooleanSetting("Ignore Activation Click", false)
            .visibleWhen(requireMouseDown::get));
    private final BooleanSetting airCrits = add(new BooleanSetting("Air Crits", false));
    private final BooleanSetting shieldCheck = add(new BooleanSetting("Shield Check", false));
    private final BooleanSetting selectFirstHit = add(new BooleanSetting("Select First Hit", false));
    private final IntSetting targetMissChance = add(new IntSetting("Target Miss Chance", 0, 0, 100, 1));
    private final IntSetting earlyHitChance = add(new IntSetting("Early Hit Chance", 0, 0, 100, 1));

    private final BooleanSetting limitToItems = add(new BooleanSetting("Limit To Items", false));
    private final BooleanSetting swords = add(itemSetting("Swords"));
    private final BooleanSetting axes = add(itemSetting("Axes"));
    private final BooleanSetting maces = add(itemSetting("Maces"));
    private final BooleanSetting spears = add(itemSetting("Spears"));
    private final BooleanSetting pickaxes = add(itemSetting("Pickaxes"));
    private final BooleanSetting shovels = add(itemSetting("Shovels"));
    private final BooleanSetting hoes = add(itemSetting("Hoes"));
    private final BooleanSetting otherItems = add(itemSetting("Other Items"));

    private long attackReadyAfter;
    private int lastHurtTime;
    private boolean mouseDown;
    private boolean releasePending;
    private boolean missAttackPending;
    private int earlyHitTicks;

    private int mouseOverTargetId = -1;
    private long mouseOverStartedAt;
    private long mouseOverDelayMillis;

    private int selectTargetId = -1;
    private boolean waitingForFirstHit;
    private boolean graceActive;
    private long selectGraceStartedAt;

    private TriggerBot() {
        super("Triggerbot", Category.COMBAT);
    }

    private BooleanSetting itemSetting(String name) {
        return new BooleanSetting(name, false).visibleWhen(limitToItems::get);
    }

    @Listen
    private void onTick(TickEvent.Pre event) {
        if (noPlayer()) {
            return;
        }

        int hurtTime = mc.player.hurtTime;
        boolean wasJustHurt = lastHurtTime <= 0 && hurtTime > 0;
        lastHurtTime = hurtTime;

        if (requireMouseDown.get() && mouseDown && !KeybindUtils.isPressed(mc.options.keyAttack)) {
            mouseDown = false;
            resetSelectState();
        }
        if (selectFirstHit.get() && waitingForFirstHit && selectTargetId != -1 && wasJustHurt) {
            waitingForFirstHit = false;
        }

        long now = System.currentTimeMillis();
        if (now < attackReadyAfter) {
            return;
        }
        if (releasePending) {
            AttackKeyController.releaseAttackKey(this);
            releasePending = false;
            return;
        }
        if (!canAttack(true)) {
            return;
        }

        float cooldownOffset = (float) -randomRange(extraDelayMin.get(), extraDelayMax.get()) + earlyHitTicks;
        if (mc.hitResult instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            if (isValidTarget(entity)) {
                if (updateSelectFirstHit(entity, wasJustHurt, now)
                        && hasMouseOverDelayElapsed(entity, now)
                        && isAttackReady(cooldownOffset)) {
                    AttackKeyController.releaseAttackKey(this);
                    releasePending = AttackKeyController.requestSyntheticAttack(this);
                }
            } else {
                resetMouseOverState();
                updateSelectGrace(now);
            }
        } else {
            resetMouseOverState();
            updateSelectGrace(now);
            if (missAttackPending && isSelectFirstHitSatisfied() && isAttackReady(cooldownOffset)) {
                AttackKeyController.releaseAttackKey(this);
                releasePending = AttackKeyController.requestSyntheticAttack(this);
                missAttackPending = false;
            }
        }
    }

    @Listen
    private void onKeyPress(KeyInputEvent event) {
        if (noPlayer() || !KeybindUtils.matchesKey(mc.options.keyAttack, event.key(), event.scancode())) {
            return;
        }
        handleInput(event.action() == GLFW.GLFW_PRESS, event);
    }

    @Listen
    private void onMouseButton(MouseButtonEvent event) {
        if (noPlayer() || !KeybindUtils.matchesMouse(mc.options.keyAttack, event.button())) {
            return;
        }
        handleInput(event.action() == GLFW.GLFW_PRESS, event);
    }

    private void handleInput(boolean pressed, CancellableEvent event) {
        if (requireMouseDown.get() && pressed && !mouseDown) {
            mouseDown = true;
            if (ignoreActivationClick.get() && canAttack(false)) {
                boolean cancelActivationClick = !isAttackReady(-(int) Math.min(extraDelayMin.get(), extraDelayMax.get()));
                if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.MISS) {
                    cancelActivationClick = true;
                }
                if (cancelActivationClick) {
                    event.cancel();
                }
            }
            return;
        }
        if (!pressed) {
            return;
        }

        attackReadyAfter = System.currentTimeMillis() + ACTIVATION_DELAY_MILLIS;
        earlyHitTicks = earlyHitChance.get() > 0
                && ThreadLocalRandom.current().nextInt(100) < earlyHitChance.get()
                ? 2 + ThreadLocalRandom.current().nextInt(2)
                : 0;
        boolean hoveringValidTarget = mc.hitResult instanceof EntityHitResult hit
                && isValidTarget(hit.getEntity());
        missAttackPending = hoveringValidTarget && targetMissChance.get() > 0
                && ThreadLocalRandom.current().nextInt(100) < targetMissChance.get();
    }

    private boolean canAttack(boolean requireActivation) {
        if (noPlayer() || mc.gameMode == null || mc.screen != null
                || mc.isPaused() || !mc.isWindowActive()) {
            return false;
        }
        if (requireActivation && requireMouseDown.get() && !mouseDown) {
            return false;
        }
        if (limitToItems.get() && !VapeTargeting.isAllowedItem(
                mc.player.getMainHandItem(), swords.get(), axes.get(), maces.get(), spears.get(),
                pickaxes.get(), shovels.get(), hoes.get(), otherItems.get())) {
            return false;
        }
        if (airCrits.get() && !mc.player.onGround() && mc.player.getY() - mc.player.yo >= 0.0D) {
            return false;
        }
        return true;
    }

    private boolean isValidTarget(Entity entity) {
        if (shieldCheck.get() && entity instanceof Player player && player.isBlocking()) {
            return false;
        }
        return VapeTargeting.isValidTarget(
                mc, entity, players.get(), mobs.get(), peaceful.get(), neutral.get(),
                ignoreNaked.get(), ignoreInvisible.get(), ignoreBehindWalls.get());
    }

    private boolean isAttackReady(float cooldownOffset) {
        if (mc.player.getY() - mc.player.yo < 0.0D
                && mc.player.fallDistance >= 1.5F
                && mc.player.getMainHandItem().getItem() instanceof MaceItem) {
            return true;
        }
        return mc.player.getAttackStrengthScale(cooldownOffset) == 1.0F;
    }

    private boolean hasMouseOverDelayElapsed(Entity entity, long now) {
        if (mouseOverTargetId != entity.getId()) {
            mouseOverTargetId = entity.getId();
            mouseOverDelayMillis = Math.round(randomRange(mouseOverDelayMin.get(), mouseOverDelayMax.get()));
            mouseOverStartedAt = now;
        }
        return now - mouseOverStartedAt >= mouseOverDelayMillis;
    }

    private boolean updateSelectFirstHit(Entity entity, boolean wasJustHurt, long now) {
        if (!selectFirstHit.get()) {
            resetSelectState();
            return true;
        }
        if (selectTargetId != entity.getId()) {
            selectTargetId = entity.getId();
            waitingForFirstHit = true;
        }
        graceActive = false;
        selectGraceStartedAt = now;
        if (waitingForFirstHit && wasJustHurt) {
            waitingForFirstHit = false;
        }
        return !waitingForFirstHit;
    }

    private boolean isSelectFirstHitSatisfied() {
        return !selectFirstHit.get() || selectTargetId == -1 || !waitingForFirstHit;
    }

    private void updateSelectGrace(long now) {
        if (!selectFirstHit.get() || selectTargetId == -1) {
            return;
        }
        if (!graceActive) {
            graceActive = true;
            selectGraceStartedAt = now;
        } else if (now - selectGraceStartedAt >= SELECT_GRACE_MILLIS) {
            resetSelectState();
        }
    }

    private void resetMouseOverState() {
        mouseOverTargetId = -1;
        mouseOverDelayMillis = 0L;
        mouseOverStartedAt = System.currentTimeMillis();
    }

    private void resetSelectState() {
        selectTargetId = -1;
        waitingForFirstHit = false;
        graceActive = false;
        selectGraceStartedAt = System.currentTimeMillis();
    }

    private static double randomRange(double first, double second) {
        double minimum = Math.min(first, second);
        double maximum = Math.max(first, second);
        return minimum == maximum
                ? minimum
                : ThreadLocalRandom.current().nextDouble(minimum, maximum);
    }

    private void resetState() {
        AttackKeyController.releaseAttackKey(this);
        mouseDown = false;
        releasePending = false;
        missAttackPending = false;
        earlyHitTicks = 0;
        lastHurtTime = noPlayer() ? 0 : mc.player.hurtTime;
        attackReadyAfter = System.currentTimeMillis() + ACTIVATION_DELAY_MILLIS;
        resetMouseOverState();
        resetSelectState();
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
        return String.format(Locale.ROOT, "%.1f-%.1f ticks",
                Math.min(extraDelayMin.get(), extraDelayMax.get()),
                Math.max(extraDelayMin.get(), extraDelayMax.get()));
    }
}
