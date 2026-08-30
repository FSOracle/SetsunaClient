package com.setsuna.ui.hud;

import com.setsuna.event.Listen;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Compact 9x3 inventory grid rendered with Minecraft's real item models. */
public final class InventoryHUD extends EpsilonHudModule {

    public static final InventoryHUD INSTANCE = new InventoryHUD();

    private static final int PANEL_COLOR = 0xD20A0B0D;
    private static final int SLOT_COLOR = 0x401D2025;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 1;
    private static final int PADDING = 3;
    private static final int COLUMNS = 9;
    private static final int ROWS = 3;
    private static final int BASE_WIDTH = PADDING * 2 + COLUMNS * SLOT_SIZE + (COLUMNS - 1) * SLOT_GAP;
    private static final int BASE_HEIGHT = PADDING * 2 + ROWS * SLOT_SIZE + (ROWS - 1) * SLOT_GAP;

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.7, 1.8, 0.05));
    public final BooleanSetting showCount = add(new BooleanSetting("Show Count", true));

    private InventoryHUD() {
        super("Inventory HUD", 410, 810, BASE_WIDTH, BASE_HEIGHT);
    }

    @Listen
    private void onVanillaHud(VanillaHudRenderEvent event) {
        if (noPlayer()) return;

        float panelScale = scale.get().floatValue();
        float width = BASE_WIDTH * panelScale;
        float height = BASE_HEIGHT * panelScale;
        float x = Math.round(renderX(event.width(), width));
        float y = Math.round(renderY(event.height(), height));
        updateBounds(width, height);

        GuiGraphicsExtractor graphics = event.graphics();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(panelScale, panelScale);

        int panelColor = applyOpacity(PANEL_COLOR);
        int slotColor = applyOpacity(SLOT_COLOR);
        graphics.fill(0, 0, BASE_WIDTH, BASE_HEIGHT, panelColor);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int slotX = PADDING + column * (SLOT_SIZE + SLOT_GAP);
                int slotY = PADDING + row * (SLOT_SIZE + SLOT_GAP);
                graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, slotColor);

                ItemStack stack = mc.player.getInventory().getItem(9 + row * COLUMNS + column);
                if (stack.isEmpty()) continue;
                int itemX = slotX + 1;
                int itemY = slotY + 1;
                graphics.item(stack, itemX, itemY);
                graphics.itemDecorations(mc.font, stack, itemX, itemY,
                        showCount.get() ? null : "");
            }
        }

        graphics.pose().popMatrix();
    }

    @Override
    protected boolean usesSharedOpacityLayer() {
        return false;
    }

    @Override
    public int editorColor() {
        return 0xFF82B4FF;
    }
}
