package com.setsuna.module.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.SlowdownEvent;
import com.setsuna.mixin.KeyMappingAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.util.network.PacketUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Queue;

/** Removes item-use movement slowdown, including OpenOpal's FastNoSlow swap flow. */
public final class NoSlow extends Module {

    public static final NoSlow INSTANCE = new NoSlow();

    private static final int SWAP_FALLBACK_TICKS = 2;
    private static final int WAIT_FALLBACK_TICKS = 4;
    private static final int IDLE_RESET_TICKS = 5;

    private enum Mode {
        Vanilla,
        NCP,
        FastNoSlow
    }

    private enum UseState {
        IDLE,
        WAITING,
        SWAPPING,
        USING
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.FastNoSlow)
            .onChange(this::onModeChanged));
    private final BooleanSetting keepSprinting = add(new BooleanSetting("Keep Sprinting", true));
    private final BooleanSetting food = add(new BooleanSetting("Food", true)
            .visibleWhen(() -> mode.is(Mode.NCP)));
    private final BooleanSetting bow = add(new BooleanSetting("Bow", true)
            .visibleWhen(() -> mode.is(Mode.NCP)));
    private final BooleanSetting crossbow = add(new BooleanSetting("Crossbow", true)
            .visibleWhen(() -> mode.is(Mode.NCP)));

    private final Queue<ServerboundPongPacket> pongQueue = new ArrayDeque<>();
    private UseState useState = UseState.IDLE;
    private boolean didSwapOffhand;
    private int waitTicks;
    private int swapTicks;
    private int idleTicks;

    private NoSlow() {
        super("No Slow", Category.MOVEMENT);
    }

    @Override
    public String getInfo() {
        return mode.get().name();
    }

    @Override
    protected void onDisable() {
        resetOffhandState();
    }

    private void onModeChanged(Mode ignored) {
        resetOffhandState();
    }

    @Listen
    private void onSlowdown(SlowdownEvent event) {
        if (event.getPlayer() != mc.player || noPlayer()) {
            return;
        }

        if (mode.is(Mode.Vanilla)) {
            event.cancel();
            if (keepSprinting.get()) {
                mc.player.setSprinting(true);
            }
            return;
        }

        if (mode.is(Mode.NCP)) {
            handleNcp(event);
            return;
        }

        if (!mc.player.isUsingItem()) {
            return;
        }

        ItemStack activeStack = mc.player.getUseItem();
        if (!isFoodOrPotion(activeStack) || mc.player.getUseItemRemainingTicks() <= 0) {
            resetOffhandState();
            return;
        }

        InteractionHand otherHand = mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        if (isUseAnimation(getStackInHand(otherHand).getUseAnimation())) {
            resetOffhandState();
            return;
        }

        if (useState != UseState.USING) {
            mc.options.keyUse.setDown(false);
        }

        if (useState == UseState.IDLE) {
            useState = UseState.WAITING;
            waitTicks = 0;
            swapTicks = 0;
            return;
        }

        if (useState == UseState.USING) {
            event.cancel();
            if (keepSprinting.get()) {
                mc.player.setSprinting(true);
            }
        }
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!mode.is(Mode.FastNoSlow)) {
            if (useState != UseState.IDLE || didSwapOffhand || !pongQueue.isEmpty()) {
                resetOffhandState();
            }
            return;
        }

        if (noPlayer() || mc.screen != null || mc.getOverlay() != null) {
            resetOffhandState();
            return;
        }

        if (useState == UseState.WAITING && ++waitTicks >= WAIT_FALLBACK_TICKS) {
            startSwap();
        }

        if (useState == UseState.SWAPPING && ++swapTicks >= SWAP_FALLBACK_TICKS) {
            beginUsing();
        }

        if (useState == UseState.USING) {
            if (mc.player.isUsingItem()) {
                idleTicks = 0;
            } else if (++idleTicks >= IDLE_RESET_TICKS) {
                resetOffhandState();
            }
        } else {
            idleTicks = 0;
        }
    }

    @Listen
    private void onPacketSend(PacketEvent.Send event) {
        if (!mode.is(Mode.FastNoSlow)) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundPongPacket pongPacket) {
            if (useState != UseState.IDLE) {
                event.cancel();
                pongQueue.add(pongPacket);
                if (useState == UseState.WAITING) {
                    startSwap();
                }
            }
            return;
        }

        if (packet instanceof ServerboundPlayerActionPacket actionPacket) {
            ServerboundPlayerActionPacket.Action action = actionPacket.getAction();
            if (action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                    && useState == UseState.USING) {
                resetOffhandState();
            } else if (action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                    && useState != UseState.IDLE) {
                resetOffhandState();
            }
        }
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!mode.is(Mode.FastNoSlow)) {
            return;
        }
        if (noPlayer()) {
            resetOffhandState();
            return;
        }

        Packet<?> packet = event.getPacket();
        if (useState == UseState.SWAPPING && isEquipmentChangePacket(packet)) {
            beginUsing();
            return;
        }

        if (packet instanceof ClientboundSetEntityMotionPacket motionPacket
                && motionPacket.id() == mc.player.getId()
                && useState == UseState.USING) {
            mc.options.keyUse.setDown(false);
        }
    }

    public boolean shouldAllowSprinting() {
        if (!isEnabled() || !keepSprinting.get()) {
            return false;
        }
        if (mode.is(Mode.Vanilla)) {
            return true;
        }
        if (mode.is(Mode.NCP)) {
            return !noPlayer()
                    && mc.player.isUsingItem()
                    && mc.player.getUseItemRemainingTicks() > 0
                    && isNcpItemEnabled(mc.player.getUseItem());
        }
        return useState == UseState.USING;
    }

    private void handleNcp(SlowdownEvent event) {
        if (!mc.player.isUsingItem() || mc.player.getUseItemRemainingTicks() <= 0) {
            return;
        }

        ItemStack activeStack = mc.player.getUseItem();
        if (!isNcpItemEnabled(activeStack)) {
            return;
        }

        event.cancel();
        if (keepSprinting.get() && mc.player.onGround()) {
            mc.player.setSprinting(true);
        }
    }

    private boolean isNcpItemEnabled(ItemStack activeStack) {
        if (!food.get() && activeStack.getComponents().has(DataComponents.FOOD)) {
            return false;
        }
        if (!bow.get() && activeStack.is(Items.BOW)) {
            return false;
        }
        if (!crossbow.get() && activeStack.is(Items.CROSSBOW)) {
            return false;
        }
        return true;
    }

    private void beginUsing() {
        if (noPlayer()) {
            resetOffhandState();
            return;
        }
        mc.options.keyUse.setDown(true);
        useState = UseState.USING;
        waitTicks = 0;
        swapTicks = 0;
        idleTicks = 0;
    }

    private void startSwap() {
        if (noPlayer()) {
            resetOffhandState();
            return;
        }
        useState = UseState.SWAPPING;
        didSwapOffhand = true;
        waitTicks = 0;
        swapTicks = 0;
        sendSwapOffhand();
    }

    private void resetOffhandState() {
        if (useState == UseState.IDLE && pongQueue.isEmpty() && !didSwapOffhand) {
            clearOffhandState();
            return;
        }

        while (!pongQueue.isEmpty()) {
            PacketUtils.sendSilently(pongQueue.poll());
        }
        if (didSwapOffhand) {
            sendSwapOffhand();
        }

        clearOffhandState();
        restoreUseKeyState();
    }

    private void clearOffhandState() {
        pongQueue.clear();
        useState = UseState.IDLE;
        didSwapOffhand = false;
        waitTicks = 0;
        swapTicks = 0;
        idleTicks = 0;
    }

    private void sendSwapOffhand() {
        PacketUtils.sendSilently(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN
        ));
    }

    private boolean isEquipmentChangePacket(Packet<?> packet) {
        return packet instanceof ClientboundContainerSetSlotPacket
                || packet instanceof ClientboundContainerSetContentPacket
                || packet instanceof ClientboundSetEquipmentPacket;
    }

    private ItemStack getStackInHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                ? mc.player.getMainHandItem()
                : mc.player.getOffhandItem();
    }

    private boolean isFoodOrPotion(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemUseAnimation animation = stack.getUseAnimation();
        boolean consumable = stack.getComponents().has(DataComponents.FOOD)
                || stack.getComponents().has(DataComponents.CONSUMABLE);
        return (consumable && (animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK))
                || stack.getItem() instanceof PotionItem;
    }

    private boolean isUseAnimation(ItemUseAnimation animation) {
        return animation == ItemUseAnimation.EAT
                || animation == ItemUseAnimation.DRINK
                || animation == ItemUseAnimation.BOW
                || animation == ItemUseAnimation.TRIDENT
                || animation == ItemUseAnimation.CROSSBOW
                || animation == ItemUseAnimation.SPEAR;
    }

    private void restoreUseKeyState() {
        if (mc.getWindow() == null) {
            return;
        }

        InputConstants.Key key = ((KeyMappingAccessor) (Object) mc.options.keyUse).setsuna$getKey();
        boolean pressed;
        if (key == InputConstants.UNKNOWN) {
            pressed = false;
        } else if (key.getType() == InputConstants.Type.MOUSE) {
            pressed = GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        } else {
            pressed = InputConstants.isKeyDown(mc.getWindow(), key.getValue());
        }
        mc.options.keyUse.setDown(pressed);
    }
}
