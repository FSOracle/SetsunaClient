package com.setsuna.ui.inventory;

import com.setsuna.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Short-lived circular slot flash used by inventory automations. */
public final class InventorySlotHighlight {
    private static final long DURATION_NANOS = 220_000_000L;
    private static final int SLOT_SIZE = 16;
    private static final int MAX_HIGHLIGHTS = 24;
    private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();

    private InventorySlotHighlight() {
    }

    public static void mark(AbstractContainerMenu menu, int containerSlot) {
        if (menu == null || containerSlot < 0 || containerSlot >= menu.slots.size()) return;
        mark(menu, menu.getSlot(containerSlot));
    }

    public static void mark(AbstractContainerMenu menu, Slot slot) {
        if (menu == null || slot == null) return;
        long now = System.nanoTime();
        prune(now);
        for (Highlight highlight : HIGHLIGHTS) {
            if (highlight.menu == menu && highlight.slotIndex == slot.index) {
                highlight.startNanos = now;
                highlight.slotX = slot.x;
                highlight.slotY = slot.y;
                return;
            }
        }
        if (HIGHLIGHTS.size() >= MAX_HIGHLIGHTS) HIGHLIGHTS.removeFirst();
        HIGHLIGHTS.add(new Highlight(menu, slot.index, slot.x, slot.y, now));
    }

    public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        if (HIGHLIGHTS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerMenu menu = screen.getMenu();
        if (minecraft.player == null || menu != minecraft.player.containerMenu) {
            HIGHLIGHTS.clear();
            return;
        }

        long now = System.nanoTime();
        prune(now);
        if (HIGHLIGHTS.isEmpty()) return;

        AbstractContainerScreenAccessor layout = (AbstractContainerScreenAccessor) screen;
        int left = layout.setsuna$getLeftPos();
        int top = layout.setsuna$getTopPos();
        for (Highlight highlight : HIGHLIGHTS) {
            if (highlight.menu != menu || highlight.slotIndex < 0 || highlight.slotIndex >= menu.slots.size()) {
                continue;
            }
            Slot slot = menu.getSlot(highlight.slotIndex);
            int slotX = slot.x;
            int slotY = slot.y;
            if (slotX == 0 && slotY == 0) {
                slotX = highlight.slotX;
                slotY = highlight.slotY;
            }

            float progress = highlight.progress(now);
            float fill = Math.min(1.0F, progress / 0.72F);
            float alpha = progress < 0.72F ? 0.45F : 0.45F * (1.0F - (progress - 0.72F) / 0.28F);
            if (alpha <= 0.01F || fill <= 0.01F) continue;

            int x = left + slotX;
            int y = top + slotY;
            float centerX = x + SLOT_SIZE * 0.5F;
            float centerY = y + SLOT_SIZE * 0.5F;
            float radius = SLOT_SIZE * 0.5F * (float) Math.sqrt(2.0D) * fill;
            int color = ((Math.round(alpha * 255.0F) & 0xFF) << 24) | 0x00FFFFFF;
            graphics.enableScissor(x, y, x + SLOT_SIZE, y + SLOT_SIZE);
            int minY = Math.max(y, (int) Math.floor(centerY - radius));
            int maxY = Math.min(y + SLOT_SIZE, (int) Math.ceil(centerY + radius));
            for (int row = minY; row < maxY; row++) {
                float dy = row + 0.5F - centerY;
                float half = (float) Math.sqrt(Math.max(0.0D, radius * radius - dy * dy));
                int start = Math.max(x, (int) Math.floor(centerX - half));
                int end = Math.min(x + SLOT_SIZE, (int) Math.ceil(centerX + half));
                if (end > start) graphics.fill(start, row, end, row + 1, color);
            }
            graphics.disableScissor();
        }
    }

    private static void prune(long now) {
        Iterator<Highlight> iterator = HIGHLIGHTS.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().progress(now) >= 1.0F) iterator.remove();
        }
    }

    private static final class Highlight {
        private final AbstractContainerMenu menu;
        private final int slotIndex;
        private int slotX;
        private int slotY;
        private long startNanos;

        private Highlight(AbstractContainerMenu menu, int slotIndex, int slotX, int slotY, long startNanos) {
            this.menu = menu;
            this.slotIndex = slotIndex;
            this.slotX = slotX;
            this.slotY = slotY;
            this.startNanos = startNanos;
        }

        private float progress(long now) {
            return Math.min(1.0F, (now - startNanos) / (float) DURATION_NANOS);
        }
    }
}
