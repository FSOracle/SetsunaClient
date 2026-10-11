package com.setsuna.module.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.SlowdownEvent;
import com.setsuna.mixin.KeyMappingAccessor;
import com.setsuna.mixin.MultiPlayerGameModeAccessor;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.network.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Runtime adaptation of Nilore's two NoSlow implementations. */
final class NiloreNoSlowEngine {

    enum SlowMode {
        GrimV3,
        NoSlow
    }

    private enum Step {
        NONE,
        ARMED,
        EATING
    }

    private static final Minecraft mc = Minecraft.getInstance();

    private final NoSlow parent;
    private final Set<Setting<?>> settings = Collections.newSetFromMap(new IdentityHashMap<>());
    private final EnumSetting<SlowMode> mode;
    private final BooleanSetting bowNoSlow;
    private final BooleanSetting keepSprinting;
    private final BooleanSetting crossbowNoSlow;
    private final BooleanSetting foodNoSlow;
    private final BooleanSetting potionNoSlow;
    private final BooleanSetting shieldNoSlow;
    private final IntSetting useItemTicks;

    private final Queue<Packet<?>> inboundQueue = new ArrayDeque<>();
    private final Queue<Packet<?>> cached = new ConcurrentLinkedQueue<>();
    private InteractionHand useHand = InteractionHand.MAIN_HAND;
    private InteractionHand lastUseHand = InteractionHand.MAIN_HAND;
    private InteractionHand pendingUseHand;
    private boolean didSwapHand;
    private boolean shouldReleaseItem;
    private int swapInitSlot;
    private int releaseTicksRemaining;
    private int pendingUseCount;
    private boolean blinking;
    private int blinkTicks;
    private int blinkDuration;
    private Step step = Step.NONE;
    private boolean hasSwapped;
    private boolean swapInArmed;
    private int noUseTicks;
    private long timerResetAt;

    NiloreNoSlowEngine(NoSlow parent) {
        this.parent = parent;
        mode = setting(new EnumSetting<>("Heypixel/Mode", SlowMode.GrimV3).displayAs("Mode"));
        bowNoSlow = setting(new BooleanSetting("Heypixel/Bow", false)
                .visibleWhen(this::isGrimV3).displayAs("Bow"));
        keepSprinting = setting(new BooleanSetting("Heypixel/Keep Sprinting", true)
                .displayAs("Keep Sprinting"));
        crossbowNoSlow = setting(new BooleanSetting("Heypixel/Crossbow", false).displayAs("Crossbow"));
        foodNoSlow = setting(new BooleanSetting("Heypixel/Food", true).displayAs("Food"));
        potionNoSlow = setting(new BooleanSetting("Heypixel/Potion", true).displayAs("Potion"));
        shieldNoSlow = setting(new BooleanSetting("Heypixel/Shield NoSlow", true).displayAs("Shield NoSlow"));
        useItemTicks = setting(new IntSetting("Heypixel/Use Item Ticks", 1, 1, 20, 1)
                .visibleWhen(() -> isGrimV3() && bowNoSlow.get()).displayAs("Use Item Ticks"));
    }

    private <T extends Setting<?>> T setting(T setting) {
        settings.add(setting);
        setting.andVisibleWhen(parent::isNiloreMode);
        return parent.registerNiloreSetting(setting);
    }

    boolean owns(Setting<?> setting) {
        return settings.contains(setting);
    }

    String info() {
        return isGrimV3() ? "Grim V3" : "NoSlow";
    }

    void enable() {
        releaseTicksRemaining = 0;
        timerResetAt = 0L;
        resetStep();
        if (mc == null) {
            inboundQueue.clear();
            blinking = false;
            blinkTicks = 0;
            blinkDuration = 0;
        } else {
            stopBlink();
        }
    }

    void disable() {
        if (mc == null) {
            resetStep();
            inboundQueue.clear();
            didSwapHand = false;
            shouldReleaseItem = false;
            pendingUseHand = null;
            releaseTicksRemaining = 0;
            blinking = false;
            blinkTicks = 0;
            blinkDuration = 0;
            return;
        }
        release();
        stopBlink();
        didSwapHand = false;
        shouldReleaseItem = false;
        pendingUseHand = null;
        releaseTicksRemaining = 0;
        restoreUseKeyState();
    }

    void onSlowdown(SlowdownEvent event) {
        if (mc.player == null || event.getPlayer() != mc.player || !mc.player.isUsingItem()) {
            return;
        }
        ItemStack stack = mc.player.getUseItem();
        if (stack.isEmpty()) {
            return;
        }
        if (mode.is(SlowMode.NoSlow)) {
            handleNoSlow(event, stack);
            return;
        }
        if (step == Step.EATING) {
            event.cancel();
            keepSprint();
            return;
        }
        if (!bowNoSlow.get() || !canSwapHands()) {
            return;
        }
        ItemUseAnimation animation = stack.getUseAnimation();
        if (animation == ItemUseAnimation.BOW && crossbowNoSlow.get()
                || animation == ItemUseAnimation.CROSSBOW && foodNoSlow.get()
                || isEatOrDrink(stack)
                || animation == ItemUseAnimation.BLOCK) {
            event.cancel();
        }
        keepSprint();
    }

    void tick() {
        if (mc.player == null) {
            release();
            stopBlink();
            return;
        }
        if (blinking) {
            blinkTicks++;
        }

        if (!isGrimV3() && step != Step.NONE) {
            release();
        }
        if (isGrimV3()) {
            tickGrimState();
        }

        if (releaseTicksRemaining > 0) {
            releaseUseKey();
            releaseTicksRemaining--;
            if (releaseTicksRemaining == 0) {
                restoreUseKeyState();
            }
        }

        if (pendingUseHand != null) {
            startUseItem(pendingUseHand, pendingUseCount);
            pendingUseHand = null;
            pendingUseCount = 0;
        }
        if (blinking && blinkTicks >= blinkDuration) {
            finishBlink();
            return;
        }
        if (isGrimV3() && bowNoSlow.get() && didSwapHand && !blinking) {
            if (useHand != lastUseHand) {
                sendSwapOffhand();
            }
            didSwapHand = false;
            shouldReleaseItem = false;
            timerResetAt = System.currentTimeMillis();
            releaseTicksRemaining = useItemTicks.get();
            releaseUseKey();
            sendReleaseUseItem();
            return;
        }
        if (isGrimV3() && bowNoSlow.get() && shouldReleaseItem
                && mc.player.isUsingItem() && canSwapHands()) {
            shouldReleaseItem = false;
            startUseItem(mc.player.getUsedItemHand(), 0);
        }
    }

    private void tickGrimState() {
        if (step != Step.NONE && step != Step.EATING) {
            mc.options.keyUse.setDown(false);
        }
        if (step == Step.NONE) {
            if (mc.player.isUsingItem()
                    && mc.options.keyUse.isDown()
                    && isUsable(mc.player.getUseItem().getUseAnimation())) {
                if (isLookingAtInteractableBlock()) {
                    return;
                }
                InteractionHand hand = mc.player.getUsedItemHand();
                if (hand == InteractionHand.OFF_HAND
                        || hand == InteractionHand.MAIN_HAND && mc.player.getOffhandItem().isEmpty()) {
                    step = Step.ARMED;
                    mc.options.keyUse.setDown(false);
                    if (mc.player.containerMenu != mc.player.inventoryMenu && mc.getConnection() != null) {
                        mc.getConnection().send(new ServerboundContainerClosePacket(
                                mc.player.containerMenu.containerId));
                    }
                }
            }
        } else if (step == Step.EATING) {
            if (mc.player.isUsingItem()) {
                noUseTicks = 0;
            } else if (++noUseTicks >= 5) {
                release();
            }
        } else {
            noUseTicks = 0;
        }
    }

    void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (isGrimV3()
                && step == Step.EATING
                && packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            release();
        }
        if (packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            blinkTicks = Math.max(blinkTicks, 1);
        }
        if (packet instanceof ServerboundUseItemOnPacket useOn
                && didSwapHand
                && useOn.getHand() == useHand
                && mc.player.getInventory().getSelectedSlot() == swapInitSlot) {
            InteractionHand other = useOn.getHand() == InteractionHand.MAIN_HAND
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            PacketUtils.sendSilently(new ServerboundUseItemOnPacket(
                    other, useOn.getHitResult(), useOn.getSequence()));
        }
        if (packet instanceof ServerboundUseItemPacket usePacket) {
            if (didSwapHand || releaseTicksRemaining > 0) {
                event.setCancelled(true);
            } else if (isGrimV3() && bowNoSlow.get()) {
                if (System.currentTimeMillis() - timerResetAt < 150L && releaseTicksRemaining <= 0) {
                    event.setCancelled(true);
                } else if (!canSwapHands()) {
                    shouldReleaseItem = true;
                } else {
                    ItemStack stack = mc.player.getItemInHand(usePacket.getHand());
                    ItemUseAnimation animation = stack.getUseAnimation();
                    if (animation == ItemUseAnimation.BOW && crossbowNoSlow.get()
                            || animation == ItemUseAnimation.CROSSBOW
                            && !CrossbowItem.isCharged(stack) && foodNoSlow.get()) {
                        shouldReleaseItem = false;
                        startBlink(1);
                    } else if (isEatOrDrink(stack)) {
                        shouldReleaseItem = false;
                        event.setCancelled(true);
                        pendingUseHand = usePacket.getHand();
                        pendingUseCount = usePacket.getSequence();
                    }
                }
            }
        }
    }

    void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (shouldQueuePacket(packet)) {
            event.setCancelled(true);
            return;
        }
        if (!isGrimV3()) {
            return;
        }
        if (step != Step.NONE && packet instanceof ClientboundPingPacket) {
            event.setCancelled(true);
            queueInboundPacket(packet);
            if (step == Step.ARMED && !swapInArmed) {
                swapInArmed = true;
                hasSwapped = true;
                sendSwapOffhand();
            }
        }
        if (step != Step.NONE
                && packet instanceof ClientboundSetEntityMotionPacket motion
                && motion.id() == mc.player.getId()) {
            event.setCancelled(true);
            queueInboundPacket(packet);
        }
        if (step == Step.ARMED && swapInArmed && packet instanceof ClientboundContainerSetSlotPacket) {
            swapInArmed = false;
            mc.options.keyUse.setDown(true);
            step = Step.EATING;
        }
        if (step != Step.NONE && packet instanceof ClientboundPlayerPositionPacket) {
            release();
        }
    }

    boolean shouldAllowSprinting() {
        if (!keepSprinting.get() || mc.player == null) {
            return false;
        }
        if (mode.is(SlowMode.NoSlow)) {
            return mc.player.isUsingItem();
        }
        return step == Step.EATING || bowNoSlow.get() && mc.player.isUsingItem();
    }

    boolean isProcessing() {
        return step != Step.NONE;
    }

    private void handleNoSlow(SlowdownEvent event, ItemStack stack) {
        Item item = stack.getItem();
        boolean slowDown = true;
        if (item instanceof BowItem && crossbowNoSlow.get()) {
            slowDown = mc.player.tickCount % 3 != 0;
        } else if (item instanceof CrossbowItem && foodNoSlow.get()) {
            slowDown = mc.player.tickCount % 3 != 0;
        } else if (stack.getComponents().has(DataComponents.FOOD) && potionNoSlow.get()
                || item instanceof PotionItem && shieldNoSlow.get()) {
            slowDown = mc.player.getUseItemRemainingTicks() >= 1
                    || mc.player.tickCount % 3 != 0;
        }
        if (!slowDown) {
            event.cancel();
        }
        keepSprint();
    }

    private void keepSprint() {
        if (keepSprinting.get() && mc.player != null) {
            mc.player.setSprinting(true);
        }
    }

    private void startUseItem(InteractionHand hand, int sequence) {
        if (mc.player == null || isLookingAtInteractableBlock()) {
            return;
        }
        didSwapHand = true;
        lastUseHand = hand;
        swapInitSlot = mc.player.getInventory().getSelectedSlot();
        useHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        sendSwapOffhand();
        if (sequence > 0) {
            sendUseItem(useHand, sequence);
        } else if (mc.gameMode != null && mc.level != null) {
            ((MultiPlayerGameModeAccessor) mc.gameMode).setsuna$startPrediction(
                    mc.level, predicted -> createUseItemPacket(useHand, predicted));
        }
        startBlink(2);
    }

    private void finishBlink() {
        shouldReleaseItem = false;
        if (!blinking || !didSwapHand || mc.player == null) {
            return;
        }
        if (useHand != lastUseHand) {
            sendSwapOffhand();
        }
        didSwapHand = false;
        timerResetAt = System.currentTimeMillis();
        releaseTicksRemaining = useItemTicks.get();
        releaseUseKey();
        sendReleaseUseItem();
        stopBlink();
    }

    private boolean canSwapHands() {
        if (mc.player == null) {
            return false;
        }
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        if (mainHand.isEmpty() || offHand.isEmpty()) {
            return true;
        }
        if (mainHand.is(Items.ENCHANTED_GOLDEN_APPLE) && offHand.is(Items.GOLDEN_APPLE)
                || offHand.is(Items.ENCHANTED_GOLDEN_APPLE) && mainHand.is(Items.GOLDEN_APPLE)) {
            return false;
        }
        return mainHand.getItem() != offHand.getItem();
    }

    private void resetStep() {
        step = Step.NONE;
        hasSwapped = false;
        swapInArmed = false;
        noUseTicks = 0;
        cached.clear();
    }

    private void release() {
        if (step == Step.NONE && cached.isEmpty() && !hasSwapped) {
            return;
        }
        step = Step.NONE;
        noUseTicks = 0;
        swapInArmed = false;
        if (mc == null || mc.player == null || mc.getConnection() == null) {
            cached.clear();
            inboundQueue.clear();
            return;
        }
        Packet<?> packet;
        while ((packet = cached.poll()) != null) {
            PacketUtils.sendSilently(packet);
        }
        flushInboundQueue();
        if (hasSwapped) {
            sendSwapOffhand();
            hasSwapped = false;
        }
    }

    private boolean isUsable(ItemUseAnimation animation) {
        return animation == ItemUseAnimation.EAT
                || animation == ItemUseAnimation.DRINK
                || animation == ItemUseAnimation.SPEAR;
    }

    private boolean isLookingAtInteractableBlock() {
        if (mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)) {
            return false;
        }
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (state.is(BlockTags.DOORS)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.SHULKER_BOXES)
                || state.is(BlockTags.ANVIL)
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.CAMPFIRES)) {
            return true;
        }
        Block block = state.getBlock();
        return block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER
                || block == Blocks.BARREL
                || block == Blocks.ENDER_CHEST
                || block == Blocks.BREWING_STAND
                || block == Blocks.HOPPER
                || block == Blocks.DISPENSER
                || block == Blocks.DROPPER
                || block == Blocks.JUKEBOX
                || block == Blocks.NOTE_BLOCK
                || block == Blocks.LEVER
                || block == Blocks.REPEATER
                || block == Blocks.COMPARATOR
                || block == Blocks.DAYLIGHT_DETECTOR
                || block == Blocks.CAKE
                || block == Blocks.COMPOSTER
                || block == Blocks.BEEHIVE
                || block == Blocks.BEE_NEST
                || block == Blocks.RESPAWN_ANCHOR
                || block == Blocks.GRINDSTONE
                || block == Blocks.STONECUTTER
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.LOOM
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.LECTERN
                || block == Blocks.BELL
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.CRAFTING_TABLE
                || block == Blocks.ENCHANTING_TABLE;
    }

    private boolean isEatOrDrink(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemUseAnimation animation = stack.getUseAnimation();
        Item item = stack.getItem();
        return animation == ItemUseAnimation.EAT && potionNoSlow.get()
                || animation == ItemUseAnimation.DRINK && shieldNoSlow.get()
                || item instanceof PotionItem && shieldNoSlow.get();
    }

    private void sendSwapOffhand() {
        PacketUtils.sendSilently(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN));
    }

    private void sendReleaseUseItem() {
        PacketUtils.sendSilently(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN));
    }

    private void sendUseItem(InteractionHand hand, int sequence) {
        PacketUtils.sendSilently(createUseItemPacket(hand, sequence));
    }

    private ServerboundUseItemPacket createUseItemPacket(InteractionHand hand, int sequence) {
        return new ServerboundUseItemPacket(
                hand,
                sequence,
                mc.player == null ? 0.0F : mc.player.getYRot(),
                mc.player == null ? 0.0F : mc.player.getXRot());
    }

    private void startBlink(int duration) {
        if (!blinking) {
            blinkTicks = 0;
        }
        blinking = true;
        blinkDuration = duration;
    }

    private void stopBlink() {
        flushInboundQueue();
        blinking = false;
        blinkTicks = 0;
        blinkDuration = 0;
    }

    private boolean shouldQueuePacket(Packet<?> packet) {
        if (!blinking || packet == null || mc.level == null || mc.getConnection() == null) {
            return false;
        }
        if (packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundLoginPacket
                || packet instanceof ClientboundRespawnPacket) {
            stopBlink();
            return false;
        }
        if (packet instanceof ClientboundEntityEventPacket entityEvent) {
            Entity entity = entityEvent.getEntity(mc.level);
            if (entity != null && (entity != mc.player || entityEvent.getEventId() != 2)) {
                return false;
            }
        }
        if (!isBlinkablePacket(packet)) {
            return false;
        }
        queueInboundPacket(packet);
        return true;
    }

    private void queueInboundPacket(Packet<?> packet) {
        inboundQueue.add(packet);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void flushInboundQueue() {
        if (mc == null || mc.getConnection() == null) {
            inboundQueue.clear();
            return;
        }
        Packet<?> packet;
        while ((packet = inboundQueue.poll()) != null) {
            try {
                ((Packet) packet).handle((PacketListener) mc.getConnection());
            } catch (RuntimeException ignored) {
                inboundQueue.clear();
                return;
            }
        }
    }

    private boolean isBlinkablePacket(Packet<?> packet) {
        if (packet instanceof ClientboundKeepAlivePacket || packet instanceof ClientboundPingPacket) {
            return true;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            return mc.player != null && motion.id() == mc.player.getId();
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            return slotPacket.getSlot() == 45 || slotPacket.getContainerId() == 0;
        }
        if (packet instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            for (Pair<EquipmentSlot, ItemStack> slot : equipmentPacket.getSlots()) {
                if (slot.getFirst() == EquipmentSlot.OFFHAND) {
                    return true;
                }
            }
        }
        return false;
    }

    private void releaseUseKey() {
        mc.options.keyUse.setDown(false);
        while (mc.options.keyUse.consumeClick()) {
            // Consume queued presses while Nilore deliberately releases use.
        }
    }

    private void restoreUseKeyState() {
        if (mc == null || mc.getWindow() == null || mc.options == null) {
            return;
        }
        InputConstants.Key key = ((KeyMappingAccessor) (Object) mc.options.keyUse).setsuna$getKey();
        boolean down;
        if (key == InputConstants.UNKNOWN) {
            down = false;
        } else if (key.getType() == InputConstants.Type.MOUSE) {
            down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        } else {
            down = InputConstants.isKeyDown(mc.getWindow(), key.getValue());
        }
        mc.options.keyUse.setDown(down);
    }

    private boolean isGrimV3() {
        return mode.is(SlowMode.GrimV3);
    }
}
