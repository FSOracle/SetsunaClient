package com.setsuna.module.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.KeyInputEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.mixin.KeyMappingAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.movement.invmove.InvMoveContext;
import com.setsuna.module.modules.movement.invmove.InvMoveEngine;
import com.setsuna.module.modules.movement.invmove.InvMoveKey;
import com.setsuna.module.modules.movement.invmove.InvMovePacketAction;
import com.setsuna.module.modules.movement.invmove.InvMoveScreen;
import com.setsuna.util.network.PacketUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import org.lwjgl.glfw.GLFW;

/** Clap InvMove with a narrow Mojmap adapter. */
public final class InvMove extends Module {
    public static final InvMove INSTANCE = new InvMove();

    private final MinecraftContext context = new MinecraftContext();
    private final InvMoveEngine engine = new InvMoveEngine(context);

    private InvMove() {
        super("InvMove", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        engine.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        engine.setEnabled(false);
    }

    @Listen
    private void onKey(KeyInputEvent event) {
        InvMoveKey key = context.keyForCode(event.key());
        if (key != null) {
            engine.onKeyEvent(key, event.action() != GLFW.GLFW_RELEASE);
        }
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        engine.tick();
    }

    @Listen(priority = Priority.HIGHEST)
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundContainerClickPacket click) {
            engine.onClickSlotPacket(click.containerId());
        } else if (packet instanceof ServerboundContainerClosePacket close
                && engine.onCloseInventoryPacket(close.getContainerId()) == InvMovePacketAction.CANCEL) {
            event.setCancelled(true);
        }
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundOpenScreenPacket
                || event.getPacket() instanceof ClientboundContainerClosePacket) {
            engine.onServerScreenChange();
        }
    }

    private final class MinecraftContext implements InvMoveContext {
        @Override
        public boolean isAvailable() {
            return mc.player != null && mc.level != null;
        }

        @Override
        public InvMoveScreen screen() {
            Screen screen = mc.screen;
            if (screen == null) return InvMoveScreen.NONE;
            if (screen instanceof InventoryScreen) return InvMoveScreen.INVENTORY;
            if (screen instanceof ChatScreen) return InvMoveScreen.CHAT;
            if (screen instanceof AbstractContainerScreen<?>) return InvMoveScreen.HANDLED_OTHER;
            return InvMoveScreen.OTHER;
        }

        @Override
        public boolean isPhysicalKeyPressed(InvMoveKey key) {
            KeyMapping mapping = mapping(key);
            InputConstants.Key bound = ((KeyMappingAccessor) (Object) mapping).setsuna$getKey();
            return bound.getType() == InputConstants.Type.KEYSYM
                    && InputConstants.isKeyDown(mc.getWindow(), bound.getValue());
        }

        @Override
        public void setLogicalKeyPressed(InvMoveKey key, boolean pressed) {
            mapping(key).setDown(pressed);
        }

        @Override
        public void sendCloseInventoryPacket() {
            PacketUtils.sendSilently(new ServerboundContainerClosePacket(0));
        }

        private InvMoveKey keyForCode(int keyCode) {
            for (InvMoveKey key : new InvMoveKey[]{
                    InvMoveKey.FORWARD, InvMoveKey.LEFT, InvMoveKey.BACK,
                    InvMoveKey.RIGHT, InvMoveKey.JUMP, InvMoveKey.SNEAK}) {
                InputConstants.Key bound = ((KeyMappingAccessor) (Object) mapping(key)).setsuna$getKey();
                if (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() == keyCode) {
                    return key;
                }
            }
            return null;
        }

        private KeyMapping mapping(InvMoveKey key) {
            return switch (key) {
                case FORWARD -> mc.options.keyUp;
                case LEFT -> mc.options.keyLeft;
                case BACK -> mc.options.keyDown;
                case RIGHT -> mc.options.keyRight;
                case JUMP -> mc.options.keyJump;
                case SNEAK -> mc.options.keyShift;
                case SPRINT -> mc.options.keySprint;
            };
        }
    }
}
