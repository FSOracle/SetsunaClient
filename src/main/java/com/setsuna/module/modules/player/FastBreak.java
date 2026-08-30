package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.mixin.MultiPlayerGameModeAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.player.InvUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Accelerates vanilla breaking and provides an Epsilon-style packet mining mode. */
public final class FastBreak extends Module {

    public static final FastBreak INSTANCE = new FastBreak();

    private static final long ROLLBACK_WINDOW_NANOS = 500_000_000L;

    public enum Mode {
        NORMAL,
        COOLDOWN,
        MIX,
        PACKET
    }

    private enum SwitchMode {
        NONE,
        DELAY,
        SILENT
    }

    private enum RenderMode {
        BOX,
        NORMAL,
        SHRINK,
        GROW
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.NORMAL));
    private final DoubleSetting threshold = add(new DoubleSetting("Threshold", 0.70D, 0.0D, 1.0D, 0.05D)
            .visibleWhen(() -> mode.is(Mode.NORMAL) || mode.is(Mode.MIX)));
    private final BooleanSetting cooldownBypass = add(new BooleanSetting("Cooldown Bypass", true)
            .visibleWhen(() -> !mode.is(Mode.PACKET)));
    private final IntSetting flagCooldown = add(new IntSetting("Flag Cooldown", 3000, 0, 300_000, 1000)
            .visibleWhen(() -> !mode.is(Mode.PACKET)));
    private final IntSetting mixOnBlocks = add(new IntSetting("Mix On Blocks", 3, 1, 20, 1)
            .visibleWhen(() -> mode.is(Mode.MIX)));
    private final IntSetting mixOffBlocks = add(new IntSetting("Mix Off Blocks", 2, 1, 20, 1)
            .visibleWhen(() -> mode.is(Mode.MIX)));

    private final BooleanSetting pauseOnUse = add(new BooleanSetting("Pause On Use", true)
            .visibleWhen(this::packetMode));
    private final BooleanSetting onlyMain = add(new BooleanSetting("Only Main", true)
            .visibleWhen(() -> packetMode() && pauseOnUse.get()));
    private final EnumSetting<SwitchMode> switchMode = add(new EnumSetting<>("Switch Mode", SwitchMode.SILENT)
            .visibleWhen(this::packetMode));
    private final IntSetting range = add(new IntSetting("Range", 6, 0, 12, 1)
            .visibleWhen(this::packetMode));
    private final IntSetting maxBreaks = add(new IntSetting("Try Break Time", 6, 0, 10, 1)
            .visibleWhen(this::packetMode));
    private final BooleanSetting farCancel = add(new BooleanSetting("Far Cancel", true)
            .visibleWhen(this::packetMode));
    private final BooleanSetting swing = add(new BooleanSetting("Swing Hand", true)
            .visibleWhen(this::packetMode));
    private final BooleanSetting instantMine = add(new BooleanSetting("Instant Mine", true)
            .visibleWhen(this::packetMode));
    private final IntSetting instantDelay = add(new IntSetting("Instant Delay", 10, 0, 1000, 10)
            .visibleWhen(() -> packetMode() && instantMine.get()));
    private final BooleanSetting fastBypass = add(new BooleanSetting("Fast Bypass", true)
            .visibleWhen(this::packetMode));
    private final BooleanSetting doubleBreak = add(new BooleanSetting("Double Break", false)
            .visibleWhen(this::packetMode));
    private final BooleanSetting checkGround = add(new BooleanSetting("Check Ground", true)
            .visibleWhen(this::packetMode));
    private final BooleanSetting bypassGround = add(new BooleanSetting("Bypass Ground", false)
            .visibleWhen(this::packetMode));
    private final BooleanSetting clientRemove = add(new BooleanSetting("Client Remove", true)
            .visibleWhen(this::packetMode));
    private final IntSetting switchDamage = add(new IntSetting("Switch Damage", 95, 0, 100, 1)
            .visibleWhen(() -> packetMode() && doubleBreak.get()));
    private final IntSetting switchTime = add(new IntSetting("Switch Time", 100, 0, 1000, 10)
            .visibleWhen(() -> packetMode() && !switchMode.is(SwitchMode.NONE)));
    private final IntSetting mineDelay = add(new IntSetting("Mine Delay", 300, 0, 1000, 10)
            .visibleWhen(this::packetMode));
    private final IntSetting packetDelay = add(new IntSetting("Packet Delay", 200, 0, 1000, 10)
            .visibleWhen(() -> packetMode() && doubleBreak.get()));
    private final DoubleSetting mineDamage = add(new DoubleSetting("Damage", 0.8D, 0.0D, 2.0D, 0.05D)
            .visibleWhen(this::packetMode));

    private final EnumSetting<RenderMode> renderMode = add(new EnumSetting<>("Render Mode", RenderMode.SHRINK)
            .visibleWhen(this::packetMode));
    private final BooleanSetting fading = add(new BooleanSetting("Fading", true)
            .visibleWhen(this::packetMode));
    private final DoubleSetting renderTime = add(new DoubleSetting("Render Time", 0.1D, 0.0D, 5.0D, 0.1D)
            .visibleWhen(() -> packetMode() && fading.get()));
    private final DoubleSetting fadeTime = add(new DoubleSetting("Fade Time", 0.2D, 0.0D, 5.0D, 0.1D)
            .visibleWhen(() -> packetMode() && fading.get()));
    private final ColorSetting fadeSideColor = add(new ColorSetting("Fade Side Color", new Color(70, 200, 155, 31))
            .visibleWhen(() -> packetMode() && fading.get()));
    private final ColorSetting fadeLineColor = add(new ColorSetting("Fade Line Color", new Color(70, 200, 155, 233))
            .visibleWhen(() -> packetMode() && fading.get()));
    private final ColorSetting sideStartColor = add(new ColorSetting("Side Start", new Color(255, 0, 0, 31))
            .visibleWhen(this::packetMode));
    private final ColorSetting sideEndColor = add(new ColorSetting("Side End", new Color(0, 150, 10, 31))
            .visibleWhen(this::packetMode));
    private final ColorSetting lineStartColor = add(new ColorSetting("Line Start", new Color(255, 0, 0, 233))
            .visibleWhen(this::packetMode));
    private final ColorSetting lineEndColor = add(new ColorSetting("Line End", new Color(5, 160, 0, 233))
            .visibleWhen(this::packetMode));
    private final ColorSetting secondSideStartColor = add(new ColorSetting("Second Side Start", new Color(255, 0, 0, 31))
            .visibleWhen(() -> packetMode() && doubleBreak.get()));
    private final ColorSetting secondSideEndColor = add(new ColorSetting("Second Side End", new Color(0, 150, 10, 31))
            .visibleWhen(() -> packetMode() && doubleBreak.get()));
    private final ColorSetting secondLineStartColor = add(new ColorSetting("Second Line Start", new Color(255, 0, 0, 233))
            .visibleWhen(() -> packetMode() && doubleBreak.get()));
    private final ColorSetting secondLineEndColor = add(new ColorSetting("Second Line End", new Color(5, 160, 0, 233))
            .visibleWhen(() -> packetMode() && doubleBreak.get()));

    private BlockPos lastBrokenPos;
    private long lastBrokenAtNanos;
    private long flaggedUntilNanos;
    private int mixBreakCount;
    private boolean mixPhaseOn = true;

    private BlockPos targetPos;
    private BlockPos secondPos;
    private Direction targetDirection = Direction.UP;
    private Direction secondDirection = Direction.UP;
    private float progress;
    private float secondProgress;
    private int mainProgressPercent;
    private int secondProgressPercent;
    private boolean started;
    private boolean secondStarted;
    private boolean completed;
    private long lastUpdateMillis;
    private long secondLastUpdateMillis;
    private long lastMineRequestMillis;
    private long lastInstantMineMillis;
    private int breakAttempts;

    private int oldSlot = -1;
    private boolean hasSwitched;
    private long restoreSlotAtMillis;

    private BlockPos renderPos;
    private BlockPos secondRenderPos;
    private double renderProgress;
    private double secondRenderProgress;
    private long lastRenderMillis;

    private final List<ScheduledStop> scheduledStops = new ArrayList<>();

    private FastBreak() {
        super("FastBreak", Category.PLAYER);
        setDefaultKeyBind(GLFW.GLFW_KEY_H);
        mode.onChange(ignored -> {
            restoreToolSlot();
            resetPacketState();
            resetVanillaState();
        });
    }

    @Override
    protected void onEnable() {
        resetVanillaState();
        resetPacketState();
    }

    @Override
    protected void onDisable() {
        restoreToolSlot();
        resetPacketState();
        resetVanillaState();
    }

    @Listen
    private void onTick(TickEvent.Pre event) {
        if (noPlayer() || mc.gameMode == null || mc.getConnection() == null) {
            restoreToolSlot();
            resetPacketState();
            resetVanillaState();
            return;
        }

        if (packetMode()) {
            updatePacketMining();
            return;
        }

        long now = System.nanoTime();
        if (flaggedUntilNanos > now) {
            return;
        }
        flaggedUntilNanos = 0L;

        if (mode.is(Mode.MIX) && !mixPhaseOn) {
            return;
        }

        MultiPlayerGameModeAccessor accessor = (MultiPlayerGameModeAccessor) mc.gameMode;
        if (cooldownBypass.get()) {
            accessor.setsuna$setDestroyDelay(0);
        }
        if (!mode.is(Mode.COOLDOWN) && mc.gameMode.isDestroying()
                && threshold.get() < 1.0D
                && accessor.setsuna$getDestroyProgress() > threshold.get().floatValue()) {
            accessor.setsuna$setDestroyProgress(1.0F);
        }
    }

    @Listen
    private void onRender(Render3DEvent event) {
        if (!packetMode() || noPlayer()) {
            return;
        }

        long now = System.currentTimeMillis();
        double deltaSeconds = Math.max(0L, now - lastRenderMillis) / 1000.0D;
        lastRenderMillis = now;

        if (targetPos != null) {
            renderPos = targetPos;
        }
        if (secondPos != null) {
            secondRenderPos = secondPos;
        }

        updateFadeProgress(deltaSeconds);
        renderFadeBoxes(event);
        renderMiningBox(event, targetPos, progressRatio(targetPos, progress), false);
        if (doubleBreak.get()) {
            renderMiningBox(event, secondPos, progressRatio(secondPos, secondProgress), true);
        }
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundBlockUpdatePacket packet)) {
            return;
        }

        BlockPos pos = packet.getPos().immutable();
        boolean air = packet.getBlockState().isAir();
        mc.execute(() -> handleBlockUpdate(pos, air));
    }

    /** Called at the head of vanilla's startDestroyBlock method. */
    public boolean handleStartDestroyBlock(BlockPos pos, Direction direction) {
        if (!isEnabled() || !packetMode() || noPlayer() || mc.getConnection() == null
                || pos == null || direction == null || !canBreak(pos)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastMineRequestMillis < mineDelay.get()) {
            return true;
        }

        lastMineRequestMillis = now;
        mine(pos.immutable(), direction, now);
        return true;
    }

    /** Whether Packet mode is keeping this completed target ready for instant re-mining. */
    public boolean isInstantMining(BlockPos pos) {
        if (!isEnabled() || !packetMode() || !instantMine.get() || pos == null
                || !completed || targetPos == null || !targetPos.equals(pos) || noPlayer()) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced();
    }

    /** Called by the game-mode mixin after vanilla successfully removes a block locally. */
    public void onBlockDestroyed(BlockPos pos) {
        if (!isEnabled() || packetMode() || pos == null) {
            return;
        }

        lastBrokenPos = pos.immutable();
        lastBrokenAtNanos = System.nanoTime();
        if (!mode.is(Mode.MIX)) {
            return;
        }

        mixBreakCount++;
        int limit = mixPhaseOn ? mixOnBlocks.get() : mixOffBlocks.get();
        if (mixBreakCount >= limit) {
            mixBreakCount = 0;
            mixPhaseOn = !mixPhaseOn;
        }
    }

    @Override
    public String getInfo() {
        if (packetMode()) {
            if (targetPos == null) {
                return mode.displayValue();
            }
            int percent = completed ? 100 : Mth.clamp(mainProgressPercent, 0, 100);
            return mode.displayValue() + " " + percent + "%";
        }

        long remaining = flaggedUntilNanos - System.nanoTime();
        if (remaining > 0L) {
            return "Flagged " + ((remaining + 999_999_999L) / 1_000_000_000L) + "s";
        }
        if (mode.is(Mode.MIX)) {
            return mixPhaseOn ? "Mix On" : "Mix Off";
        }
        return mode.displayValue();
    }

    private void mine(BlockPos pos, Direction direction, long now) {
        breakAttempts = 0;
        if (doubleBreak.get()) {
            if (targetPos != null && secondPos == null && !targetPos.equals(pos)) {
                if (completed) {
                    setMainTarget(pos, direction, now);
                    clearSecondTarget();
                } else {
                    secondPos = targetPos;
                    secondDirection = targetDirection;
                    secondStarted = false;
                    secondProgress = progress;
                    secondProgressPercent = mainProgressPercent;
                    secondLastUpdateMillis = now;
                    setMainTarget(pos, direction, now);
                }
            } else if (targetPos == null || !targetPos.equals(pos)) {
                setMainTarget(pos, direction, now);
            } else {
                targetDirection = direction;
            }
        } else if (targetPos == null || !targetPos.equals(pos)) {
            clearSecondTarget();
            setMainTarget(pos, direction, now);
        } else {
            targetDirection = direction;
        }
    }

    private void setMainTarget(BlockPos pos, Direction direction, long now) {
        targetPos = pos;
        targetDirection = direction;
        started = false;
        progress = 0.0F;
        mainProgressPercent = 0;
        completed = false;
        lastUpdateMillis = now;
        lastInstantMineMillis = now;
    }

    private void updatePacketMining() {
        long now = System.currentTimeMillis();
        processScheduledStops(now);
        restoreToolSlotIfReady(now);

        if (targetPos == null && secondPos == null) {
            return;
        }

        if (secondPos != null && (!doubleBreak.get() || isOutsideRange(secondPos))) {
            clearSecondTarget();
        }
        if (targetPos != null && isOutsideRange(targetPos)) {
            clearMainTarget();
        }

        updateSecondTarget(now);
        updateMainTarget(now);

        if (doubleBreak.get() && secondPos != null && !hasSwitched
                && !isPaused()
                && (mainProgressPercent >= switchDamage.get()
                || secondProgressPercent >= switchDamage.get())) {
            switchToBestTool(secondPos, now);
        }
    }

    private void updateSecondTarget(long now) {
        if (secondPos == null) {
            return;
        }
        if (isReplaceable(secondPos)) {
            clearSecondTarget();
            return;
        }
        if (!secondStarted) {
            sendStart(secondPos, secondDirection, true, now);
            secondStarted = true;
            secondProgress = 0.0F;
            secondLastUpdateMillis = now;
            return;
        }

        secondProgress += progressDelta(now, secondLastUpdateMillis);
        secondLastUpdateMillis = now;
        float required = requiredTicks(secondPos);
        secondProgressPercent = progressPercent(secondProgress, required);
        if (secondProgress >= required) {
            sendGroundBypass(secondPos);
            if (swing.get()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            if (clientRemove.get() && !isAir(secondPos)) {
                mc.gameMode.destroyBlock(secondPos);
            }
            clearSecondTarget();
        }
    }

    private void updateMainTarget(long now) {
        if (targetPos == null) {
            return;
        }

        if (!started) {
            sendStart(targetPos, targetDirection, false, now);
            started = true;
            progress = 0.0F;
            lastUpdateMillis = now;
            return;
        }

        if (!completed) {
            progress += progressDelta(now, lastUpdateMillis);
            lastUpdateMillis = now;
            float required = requiredTicks(targetPos);
            mainProgressPercent = progressPercent(progress, required);
            if (progress >= required) {
                sendStop(targetPos, targetDirection, now);
                completed = true;
                mainProgressPercent = 100;
                if (!instantMine.get() && secondPos == null) {
                    clearMainTarget();
                }
            }
            return;
        }

        if (isReplaceable(targetPos)) {
            breakAttempts = 0;
            return;
        }
        if (!instantMine.get() || isPaused() || now - lastInstantMineMillis < instantDelay.get()) {
            return;
        }

        if (maxBreaks.get() > 0 && breakAttempts >= maxBreaks.get()) {
            clearMainTarget();
            return;
        }
        sendStop(targetPos, targetDirection, now);
        breakAttempts++;
    }

    private float progressDelta(long now, long previous) {
        if (isPaused()) {
            return 0.0F;
        }
        double seconds = Mth.clamp((now - previous) / 1000.0D, 0.0D, 0.25D);
        double multiplier = !checkGround.get() || mc.player.onGround() ? 20.0D : 4.0D;
        return (float) (seconds * multiplier);
    }

    private float requiredTicks(BlockPos pos) {
        float ticks = getMineTicks(pos, getTool(pos));
        double multiplier = Math.max(0.0001D, mineDamage.get());
        double result = ticks * multiplier;
        return (float) Math.min(Float.MAX_VALUE, result);
    }

    private int progressPercent(float currentProgress, float requiredTicks) {
        if (!Float.isFinite(requiredTicks) || requiredTicks <= 0.0F) {
            return requiredTicks <= 0.0F ? 100 : 0;
        }
        return (int) Mth.clamp(currentProgress / requiredTicks * 100.0F, 0.0F, 100.0F);
    }

    private double progressRatio(BlockPos pos, float currentProgress) {
        if (pos == null) {
            return 0.0D;
        }
        float required = requiredTicks(pos);
        if (!Float.isFinite(required) || required <= 0.0F) {
            return required <= 0.0F ? 1.0D : 0.0D;
        }
        return Mth.clamp(currentProgress / required, 0.0F, 1.0F);
    }

    private void sendStart(BlockPos pos, Direction direction, boolean secondary, long now) {
        if (mc.getConnection() == null) {
            return;
        }
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction));

        if (fastBypass.get()) {
            BlockPos bypassPos = BlockPos.containing(mc.player.getX(), 321.0D, mc.player.getZ());
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    bypassPos,
                    Direction.DOWN,
                    nextSequence()));
        }

        if (doubleBreak.get()) {
            long dueAt = now + packetDelay.get();
            if (packetDelay.get() == 0) {
                sendScheduledStop(pos, direction);
            } else {
                scheduledStops.add(new ScheduledStop(pos.immutable(), direction, dueAt));
            }
        }

        if (swing.get()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        if (secondary) {
            secondLastUpdateMillis = now;
        } else {
            lastUpdateMillis = now;
        }
    }

    private void sendStop(BlockPos pos, Direction direction, long now) {
        if (pos == null || mc.getConnection() == null || isPaused()) {
            return;
        }

        if (!doubleBreak.get() || secondPos == null) {
            switchToBestTool(pos, now);
        }
        sendGroundBypass(pos);
        if (swing.get()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                direction != null ? direction : RotationUtils.getClickSide(pos),
                nextSequence()));
        lastInstantMineMillis = now;

        if (clientRemove.get() && !isAir(pos)) {
            mc.gameMode.destroyBlock(pos);
        }
    }

    private void sendScheduledStop(BlockPos pos, Direction direction) {
        if (mc.getConnection() == null) {
            return;
        }
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction));
    }

    private void processScheduledStops(long now) {
        Iterator<ScheduledStop> iterator = scheduledStops.iterator();
        while (iterator.hasNext()) {
            ScheduledStop stop = iterator.next();
            if (now >= stop.dueAtMillis()) {
                sendScheduledStop(stop.pos(), stop.direction());
                iterator.remove();
            }
        }
    }

    private void sendGroundBypass(BlockPos pos) {
        if (!bypassGround.get() || pos == null || isAir(pos)
                || mc.player.isFallFlying() || mc.player.onGround()
                || mc.getConnection() == null) {
            return;
        }
        mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                mc.player.position().add(0.0D, 1.0E-9D, 0.0D),
                mc.player.getYRot(),
                mc.player.getXRot(),
                true,
                mc.player.horizontalCollision));
        mc.player.resetFallDistance();
    }

    private int nextSequence() {
        try (var prediction = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            return prediction.currentSequence();
        }
    }

    private void switchToBestTool(BlockPos pos, long now) {
        if (hasSwitched || switchMode.is(SwitchMode.NONE) || pos == null) {
            return;
        }
        int bestSlot = getTool(pos);
        if (bestSlot < 0 || bestSlot == mc.player.getInventory().getSelectedSlot()) {
            return;
        }

        oldSlot = mc.player.getInventory().getSelectedSlot();
        if (switchMode.is(SwitchMode.DELAY)) {
            InvUtils.swap(bestSlot, false);
        }
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
        hasSwitched = true;
        restoreSlotAtMillis = now + switchTime.get();
    }

    private void restoreToolSlotIfReady(long now) {
        if (hasSwitched && now >= restoreSlotAtMillis) {
            restoreToolSlot();
        }
    }

    private void restoreToolSlot() {
        if (!hasSwitched) {
            oldSlot = -1;
            restoreSlotAtMillis = 0L;
            return;
        }
        if (mc.player != null && oldSlot >= 0 && oldSlot < 9) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundSetCarriedItemPacket(oldSlot));
            }
        }
        hasSwitched = false;
        oldSlot = -1;
        restoreSlotAtMillis = 0L;
    }

    private int getTool(BlockPos pos) {
        if (pos == null) {
            return -1;
        }
        BlockState state = mc.level.getBlockState(pos);
        int bestSlot = -1;
        float bestScore = 1.0F;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            float score = stack.getDestroySpeed(state) + enchantmentLevel(stack, Enchantments.EFFICIENCY);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private float getMineTicks(BlockPos pos, int slot) {
        if (pos == null) {
            return 20.0F;
        }
        BlockState state = mc.level.getBlockState(pos);
        float hardness = state.getDestroySpeed(mc.level, pos);
        if (hardness < 0.0F) {
            return Float.MAX_VALUE;
        }
        if (hardness == 0.0F) {
            return 1.0F;
        }

        ItemStack stack = slot < 0 ? ItemStack.EMPTY : mc.player.getInventory().getItem(slot);
        boolean canHarvest = stack.isCorrectToolForDrops(state);
        float speed = stack.getDestroySpeed(state);
        int efficiency = enchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0 && speed > 1.0F) {
            speed += efficiency * efficiency + 1.0F;
        }
        if (mc.player.hasEffect(MobEffects.HASTE)) {
            int amplifier = mc.player.getEffect(MobEffects.HASTE).getAmplifier();
            speed *= 1.0F + (amplifier + 1) * 0.2F;
        }
        if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
            int amplifier = mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier();
            speed *= switch (amplifier) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 0.00081F;
            };
        }

        float damage = speed / hardness / (canHarvest ? 30.0F : 100.0F);
        return damage <= 0.0F ? Float.MAX_VALUE : 1.0F / damage;
    }

    private static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(enchantment)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private boolean canBreak(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!mc.player.isCreative() && state.getDestroySpeed(mc.level, pos) < 0.0F) {
            return false;
        }
        return !state.isAir() && !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    private boolean isPaused() {
        return pauseOnUse.get() && mc.options.keyUse.isDown()
                && (!onlyMain.get() || mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND);
    }

    private boolean isOutsideRange(BlockPos pos) {
        return farCancel.get() && mc.player.getEyePosition().distanceTo(pos.getCenter()) > range.get();
    }

    private boolean isReplaceable(BlockPos pos) {
        if (pos == null) {
            return true;
        }
        BlockState state = mc.level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private boolean isAir(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.FIRE) && hasCrystal(pos);
    }

    private boolean hasCrystal(BlockPos pos) {
        for (Entity entity : mc.level.getEntities(null, new AABB(pos))) {
            if (entity instanceof EndCrystal crystal && crystal.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private void updateFadeProgress(double deltaSeconds) {
        if (!fading.get()) {
            renderProgress = 0.0D;
            secondRenderProgress = 0.0D;
            return;
        }

        boolean paused = isPaused();
        if (isSolidForFade(renderPos) && !paused) {
            renderProgress = fadeTime.get() + renderTime.get();
        } else {
            renderProgress = Math.max(0.0D, renderProgress - deltaSeconds);
        }
        if (isSolidForFade(secondRenderPos) && !paused) {
            secondRenderProgress = fadeTime.get() + renderTime.get();
        } else {
            secondRenderProgress = Math.max(0.0D, secondRenderProgress - deltaSeconds);
        }
    }

    private boolean isSolidForFade(BlockPos pos) {
        return pos != null && !isAir(pos) && !mc.level.getBlockState(pos).canBeReplaced();
    }

    private void renderFadeBoxes(Render3DEvent event) {
        if (!fading.get()) {
            return;
        }
        renderFadeBox(event, renderPos, renderProgress);
        renderFadeBox(event, secondRenderPos, secondRenderProgress);
    }

    private void renderFadeBox(Render3DEvent event, BlockPos pos, double remaining) {
        if (pos == null || remaining <= 0.0D || isSolidForFade(pos)) {
            return;
        }
        double alpha = fadeTime.get() <= 0.0D ? 1.0D : Math.min(1.0D, remaining / fadeTime.get());
        Color side = withAlpha(fadeSideColor.get(), alpha);
        Color line = withAlpha(fadeLineColor.get(), alpha);
        Render3DUtils.drawFilledBox(pos, side);
        Render3DUtils.drawOutlineBox(event.getPoseStack(), pos, line);
    }

    private void renderMiningBox(Render3DEvent event, BlockPos pos, double ratio, boolean secondary) {
        if (pos == null || isReplaceable(pos)) {
            return;
        }

        Color sideStart = secondary ? secondSideStartColor.get() : sideStartColor.get();
        Color sideEnd = secondary ? secondSideEndColor.get() : sideEndColor.get();
        Color lineStart = secondary ? secondLineStartColor.get() : lineStartColor.get();
        Color lineEnd = secondary ? secondLineEndColor.get() : lineEndColor.get();
        Color side = ratio >= 0.95D ? sideEnd : sideStart;
        Color line = ratio >= 0.95D ? lineEnd : lineStart;

        AABB box = switch (renderMode.get()) {
            case BOX -> new AABB(pos);
            case NORMAL -> AABB.ofSize(pos.getCenter(), ratio, ratio, ratio);
            case GROW -> new AABB(pos).setMaxY(pos.getY() + ratio);
            case SHRINK -> new AABB(pos).deflate((1.0D - ratio) * 0.5D);
        };
        Render3DUtils.drawFilledBox(box, side);
        Render3DUtils.drawOutlineBox(event.getPoseStack(), box, line);
    }

    private static Color withAlpha(Color color, double multiplier) {
        int alpha = Mth.clamp((int) Math.round(color.getAlpha() * multiplier), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private void handleBlockUpdate(BlockPos pos, boolean air) {
        if (!isEnabled() || packetMode() || lastBrokenPos == null || !lastBrokenPos.equals(pos)) {
            return;
        }

        long elapsed = System.nanoTime() - lastBrokenAtNanos;
        if (!air && elapsed >= 0L && elapsed < ROLLBACK_WINDOW_NANOS) {
            flaggedUntilNanos = System.nanoTime() + flagCooldown.get() * 1_000_000L;
            mixBreakCount = 0;
            mixPhaseOn = true;
        }
        lastBrokenPos = null;
        lastBrokenAtNanos = 0L;
    }

    private boolean packetMode() {
        return mode.is(Mode.PACKET);
    }

    private void clearMainTarget() {
        targetPos = null;
        targetDirection = Direction.UP;
        progress = 0.0F;
        mainProgressPercent = 0;
        started = false;
        completed = false;
        breakAttempts = 0;
    }

    private void clearSecondTarget() {
        secondPos = null;
        secondDirection = Direction.UP;
        secondProgress = 0.0F;
        secondProgressPercent = 0;
        secondStarted = false;
    }

    private void resetPacketState() {
        clearMainTarget();
        clearSecondTarget();
        scheduledStops.clear();
        renderPos = null;
        secondRenderPos = null;
        renderProgress = 0.0D;
        secondRenderProgress = 0.0D;
        long now = System.currentTimeMillis();
        lastUpdateMillis = now;
        secondLastUpdateMillis = now;
        lastMineRequestMillis = Long.MIN_VALUE / 2L;
        lastInstantMineMillis = now;
        lastRenderMillis = now;
    }

    private void resetVanillaState() {
        lastBrokenPos = null;
        lastBrokenAtNanos = 0L;
        flaggedUntilNanos = 0L;
        mixBreakCount = 0;
        mixPhaseOn = true;
    }

    private record ScheduledStop(BlockPos pos, Direction direction, long dueAtMillis) {
    }
}
