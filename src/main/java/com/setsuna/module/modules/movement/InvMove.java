package com.setsuna.module.modules.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.setsuna.event.Listen;
import com.setsuna.event.events.KeyInputEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.mixin.KeyMappingAccessor;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.movement.invmove.InvMoveContext;
import com.setsuna.module.modules.movement.invmove.InvMoveEngine;
import com.setsuna.module.modules.movement.invmove.InvMoveKey;
import com.setsuna.module.modules.movement.invmove.InvMoveMode;
import com.setsuna.module.modules.movement.invmove.InvMoveScreen;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Input;
import org.lwjgl.glfw.GLFW;

/** LiquidBounce-style inventory movement adapted from the D:\1 implementation. */
public final class InvMove extends Module {
    public static final InvMove INSTANCE = new InvMove();

    private static final InvMoveKey[] TRACKED_KEYS = InvMoveKey.values();

    private final EnumSetting<InvMoveMode> mode = add(new EnumSetting<>("Mode", InvMoveMode.LEGIT));
    private final BooleanSetting sneak = add(new BooleanSetting("Sneak", false));
    private final MinecraftContext context = new MinecraftContext();
    private final InvMoveEngine engine = new InvMoveEngine(context);

    private InvMove() {
        super("InvMove", Category.MOVEMENT);
    }

    @Override
    public String getInfo() {
        return mode.get() == InvMoveMode.LEGIT ? "Legit" : "Normal";
    }

    @Override
    protected void onEnable() {
        applySettings();
        engine.setEnabled(true);
    }

    @Override
    protected void onDisable() {
        engine.setEnabled(false);
    }

    @Listen
    private void onKey(KeyInputEvent event) {
        InvMoveKey key = context.keyForEvent(event.key(), event.scancode());
        if (key != null) {
            engine.onKeyEvent(key, event.action() != GLFW.GLFW_RELEASE);
        }
    }

    /** Runs after vanilla player input and sprint state have been synchronized. */
    @Listen
    private void onTick(TickEvent.Post event) {
        applySettings();
        engine.tick();
    }

    /** Called from KeyboardInput while vanilla reads its seven movement mappings. */
    public boolean suppliesInput(KeyMapping mapping) {
        applySettings();
        InvMoveKey key = context.keyForMapping(mapping);
        return key != null && engine.suppliesInput(key);
    }

    /** Called at the common slot-action boundary for mouse and keyboard inventory actions. */
    public boolean blocksInventoryClick() {
        applySettings();
        return engine.blocksInventoryClick();
    }

    /** Delays a Legit-mode close until stopped input has reached vanilla state. */
    public boolean interceptInventoryClose() {
        applySettings();
        return engine.interceptInventoryClose();
    }

    public boolean isLegit() {
        return mode.get() == InvMoveMode.LEGIT;
    }

    private void applySettings() {
        engine.setMode(mode.get());
        engine.setSneakAllowed(sneak.get());
    }

    private final class MinecraftContext implements InvMoveContext {
        @Override
        public boolean isAvailable() {
            return mc.player != null;
        }

        @Override
        public InvMoveScreen screen() {
            Screen screen = mc.screen;
            if (screen == null) return InvMoveScreen.NONE;
            if (screen instanceof ChatScreen) return InvMoveScreen.CHAT;
            if (screen instanceof CreativeModeInventoryScreen) return InvMoveScreen.CREATIVE_INVENTORY;
            if (screen instanceof InventoryScreen) return InvMoveScreen.INVENTORY;
            if (screen instanceof AbstractContainerScreen<?>) return InvMoveScreen.HANDLED_OTHER;
            return InvMoveScreen.OTHER;
        }

        @Override
        public Object screenIdentity() {
            return mc.screen;
        }

        @Override
        public boolean isPlayerMoving() {
            if (mc.player == null) return false;
            Input input = mc.player.input.keyPresses;
            return input.forward() || input.backward() || input.left() || input.right();
        }

        @Override
        public boolean isPlayerSprinting() {
            return mc.player != null && mc.player.isSprinting();
        }

        @Override
        public void closeInventory() {
            if (mc.screen instanceof InventoryScreen inventory) {
                inventory.onClose();
            }
        }

        private InvMoveKey keyForEvent(int keyCode, int scanCode) {
            for (InvMoveKey key : TRACKED_KEYS) {
                InputConstants.Key bound = boundKey(mapping(key));
                boolean matches = switch (bound.getType()) {
                    case KEYSYM -> bound.getValue() == keyCode;
                    case SCANCODE -> bound.getValue() == scanCode;
                    default -> false;
                };
                if (matches) return key;
            }
            return null;
        }

        private InvMoveKey keyForMapping(KeyMapping mapping) {
            for (InvMoveKey key : TRACKED_KEYS) {
                if (mapping(key) == mapping) return key;
            }
            return null;
        }

        private InputConstants.Key boundKey(KeyMapping mapping) {
            return ((KeyMappingAccessor) (Object) mapping).setsuna$getKey();
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
