package com.setsuna.module.modules.player;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.util.network.PacketUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Selects the fastest safe hotbar tool before vanilla handles block breaking. */
public final class AutoTool extends Module {

    public static final AutoTool INSTANCE = new AutoTool();

    private static final long SWAP_BACK_DELAY_NANOS = 300_000_000L;
    private static final float SCORE_EPSILON = 0.0001F;

    private final BooleanSetting swapBack = add(new BooleanSetting("Swap Back", true));
    private final BooleanSetting saveItem = add(new BooleanSetting("Save Item", true));
    private final BooleanSetting silent = add(new BooleanSetting("Silent", false));
    private final BooleanSetting enderChestSilk = add(new BooleanSetting("Ender Chest Silk Touch", true));

    /** Last tool slot selected by this module, or {@code -1} while idle. */
    public static int itemIndex = -1;

    private int originalSlot = -1;
    private int activeToolSlot = -1;
    private long restoreAtNanos = -1L;
    private boolean silentThisTick;

    private AutoTool() {
        super("Auto Tool", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        clearState();
    }

    @Override
    protected void onDisable() {
        restoreOriginal(true);
        clearState();
    }

    /**
     * Called after Minecraft refreshes {@code hitResult}, but before vanilla syncs
     * the carried slot and processes attack input.
     */
    public void prepareMiningTick() {
        silentThisTick = false;
        if (!isEnabled()) {
            return;
        }
        if (noPlayer() || mc.gameMode == null) {
            clearState();
            return;
        }

        BlockState targetState = activeTargetState();
        if (targetState == null) {
            handleMiningStopped();
            return;
        }

        int bestSlot = findBestTool(targetState);
        if (bestSlot == -1) {
            handleMiningStopped();
            return;
        }

        restoreAtNanos = -1L;
        equipForMining(bestSlot);
    }

    /** Restores the visible slot after vanilla has finished this tick's mining logic. */
    public void finishMiningTick() {
        if (!silentThisTick) {
            return;
        }
        silentThisTick = false;
        if (!isEnabled() || mc.player == null || !isHotbarSlot(originalSlot)) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(originalSlot);
    }

    public int getTool(BlockPos pos) {
        if (noPlayer() || pos == null) {
            return -1;
        }
        return findBestTool(mc.level.getBlockState(pos));
    }

    private BlockState activeTargetState() {
        if (mc.screen != null || !mc.options.keyAttack.isDown()
                || !(mc.hitResult instanceof BlockHitResult blockHit)) {
            return null;
        }

        BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
        return state.isAir() ? null : state;
    }

    private void equipForMining(int toolSlot) {
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (originalSlot == -1 && selectedSlot == toolSlot) {
            activeToolSlot = toolSlot;
            itemIndex = toolSlot;
            return;
        }

        if (originalSlot == -1) {
            originalSlot = selectedSlot;
        } else if (silent.get() && selectedSlot != originalSlot && selectedSlot != activeToolSlot) {
            // Preserve a manual scroll made while the previous tick was visually restored.
            originalSlot = selectedSlot;
        }

        if (selectedSlot != toolSlot) {
            mc.player.getInventory().setSelectedSlot(toolSlot);
        }
        activeToolSlot = toolSlot;
        itemIndex = toolSlot;
        silentThisTick = silent.get();
    }

    private void handleMiningStopped() {
        if (originalSlot == -1) {
            activeToolSlot = -1;
            itemIndex = mc.player.getInventory().getSelectedSlot();
            restoreAtNanos = -1L;
            return;
        }

        if (!swapBack.get()) {
            if (silent.get() && isHotbarSlot(activeToolSlot)) {
                mc.player.getInventory().setSelectedSlot(activeToolSlot);
            }
            clearStateKeepingSelectedSlot();
            return;
        }

        // Silent mode is already visually restored at tick end; let vanilla sync
        // that original slot immediately when mining stops.
        if (silent.get()) {
            restoreOriginal(false);
            clearState();
            return;
        }

        long now = System.nanoTime();
        if (restoreAtNanos == -1L) {
            restoreAtNanos = now + SWAP_BACK_DELAY_NANOS;
            return;
        }
        if (now >= restoreAtNanos) {
            restoreOriginal(false);
            clearState();
        }
    }

    private void restoreOriginal(boolean sendImmediately) {
        if (mc.player == null || !isHotbarSlot(originalSlot)) {
            return;
        }

        mc.player.getInventory().setSelectedSlot(originalSlot);
        itemIndex = originalSlot;
        if (sendImmediately && mc.getConnection() != null) {
            PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(originalSlot));
        }
    }

    private int findBestTool(BlockState state) {
        if (state == null || state.isAir()) {
            return -1;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        int bestSlot = -1;
        float bestScore = 1.0F / 100.0F;
        boolean requireSilk = state.is(Blocks.ENDER_CHEST) && enderChestSilk.get();

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty() || shouldPreserve(stack)) {
                continue;
            }
            if (requireSilk && enchantmentLevel(stack, Enchantments.SILK_TOUCH) == 0) {
                continue;
            }

            float score = miningScore(stack, state);
            boolean preferredTie = slot == selectedSlot && bestSlot != selectedSlot;
            if (score > bestScore + SCORE_EPSILON
                    || (Math.abs(score - bestScore) <= SCORE_EPSILON && preferredTie)) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private boolean shouldPreserve(ItemStack stack) {
        return saveItem.get() && stack.isDamageableItem()
                && stack.getMaxDamage() - stack.getDamageValue() <= 10;
    }

    private static float miningScore(ItemStack stack, BlockState state) {
        float speed = stack.getDestroySpeed(state);
        int efficiency = enchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (speed > 1.0F && efficiency > 0) {
            speed += efficiency * efficiency + 1.0F;
        }

        boolean correctTool = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
        return speed / (correctTool ? 30.0F : 100.0F);
    }

    private static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(enchantment)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    private void clearStateKeepingSelectedSlot() {
        itemIndex = mc.player.getInventory().getSelectedSlot();
        originalSlot = -1;
        activeToolSlot = -1;
        restoreAtNanos = -1L;
        silentThisTick = false;
    }

    private void clearState() {
        itemIndex = -1;
        originalSlot = -1;
        activeToolSlot = -1;
        restoreAtNanos = -1L;
        silentThisTick = false;
    }
}
