package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaRenderer;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.BooleanSetting;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.StringJoiner;

/** Delta Force-inspired player HUD with a native, borderless hotbar. */
public final class DeltaForceStyle extends Module {

    public static final DeltaForceStyle INSTANCE = new DeltaForceStyle();

    public final BooleanSetting slotCount = add(new BooleanSetting("Slot Count", false));

    private static final float AVATAR = 32.0F;
    private static final float BAR_WIDTH = 92.0F;
    private static final float BAR_HEIGHT = 5.0F;
    private static final float HOTBAR_ITEM = 16.0F;
    private static final float HOTBAR_GAP = 2.0F;
    private static final float LEFT = 8.0F;
    private static final float BOTTOM = 8.0F;
    private static final float HELD_ITEM_SIZE = 72.0F;
    private static final float HUD_ENTRY_OFFSET = 112.0F;
    private static final float VANILLA_HOTBAR_EXIT_OFFSET = 40.0F;
    private static final long TRANSITION_DURATION_NANOS = 550_000_000L;
    private static final int ABSORPTION_COLOR = 0xFFFFD45A;
    private static final Paint AVATAR_PAINT = new Paint().setAntiAlias(true);

    private float displayedHealth = 1.0F;
    private float displayedAbsorption;
    private float displayedFood = 1.0F;
    private long transitionStartedNanos;

    private DeltaForceStyle() {
        super("DeltaForce Style", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        transitionStartedNanos = System.nanoTime();
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (noPlayer()) return;

        float maximum = Math.max(1.0F, mc.player.getMaxHealth());
        float health = clamp(mc.player.getHealth() / maximum, 0.0F, 1.0F);
        float absorption = Math.max(0.0F, mc.player.getAbsorptionAmount() / maximum);
        float food = clamp(mc.player.getFoodData().getFoodLevel() / 20.0F, 0.0F, 1.0F);
        float frame = Math.min(0.12F, Math.max(0.016F,
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(true) / 20.0F));
        displayedHealth = smooth(displayedHealth, health, frame);
        displayedAbsorption = smooth(displayedAbsorption, absorption, frame);
        displayedFood = smooth(displayedFood, food, frame);

        Canvas canvas = event.canvas();
        float transitionOffset = HUD_ENTRY_OFFSET * (1.0F - transitionProgress());
        int save = canvas.save();
        canvas.translate(0.0F, transitionOffset);
        try {
            float x = LEFT;
            float y = event.height() - BOTTOM - AVATAR;

            drawAvatar(canvas, mc.player, x, y);

            float infoX = x + AVATAR + 7.0F;
            float barY = y + 17.0F;
            String healthText = Math.round((displayedHealth + displayedAbsorption) * 100.0F) + "/100";
            SkijaUi.boldText(canvas, healthText, infoX, y + 1.0F, 11.0F,
                    0xFFF5F7FA, 10.0F);
            SkijaUi.fill(canvas, infoX, barY, BAR_WIDTH, BAR_HEIGHT, 0x6621262D);
            if (displayedHealth > 0.0F) {
                SkijaUi.fill(canvas, infoX, barY, BAR_WIDTH * clamp(displayedHealth, 0.0F, 1.0F),
                        BAR_HEIGHT, 0xFFFFFFFF);
            }
            float absorptionWidth = clamp(displayedAbsorption, 0.0F, 1.0F);
            float absorptionEnd = clamp(displayedHealth + displayedAbsorption, 0.0F, 1.0F);
            float absorptionStart = Math.max(0.0F, absorptionEnd - absorptionWidth);
            if (absorptionEnd > absorptionStart) {
                SkijaUi.fill(canvas, infoX + BAR_WIDTH * absorptionStart, barY,
                        BAR_WIDTH * (absorptionEnd - absorptionStart), BAR_HEIGHT, ABSORPTION_COLOR);
            }
            SkijaUi.text(canvas, mc.player.getName().getString(), infoX, y + 24.0F,
                    9.0F, 0xCFFFFFFF, 8.0F);

            float foodWidth = Math.min(160.0F, Math.max(80.0F, event.width() * 0.16F));
            float foodX = (event.width() - foodWidth) * 0.5F;
            float foodY = event.height() - 7.0F;
            SkijaUi.fill(canvas, foodX, foodY, foodWidth, 3.0F, 0x5521262D);
            SkijaUi.fill(canvas, foodX, foodY, foodWidth * displayedFood, 3.0F, 0xFFFFFFFF);

            ItemStack held = heldTool();
            if (!held.isEmpty()) {
                float itemX = event.width() - 12.0F - HELD_ITEM_SIZE;
                float itemY = event.height() - 28.0F - HELD_ITEM_SIZE;
                String itemLabel = heldItemLabel(held);
                float textSize = 8.0F;
                float labelWidth = SkijaUi.textWidth(itemLabel, textSize);
                float labelX = Math.min(itemX, event.width() - 12.0F - labelWidth);
                SkijaUi.text(canvas, itemLabel, labelX,
                        itemY + HELD_ITEM_SIZE + 2.0F, 10.0F, 0xDFFFFFFF, textSize);
            }

            if (slotCount.get()) {
                float hotbarX = LEFT + AVATAR + 7.0F + BAR_WIDTH + 12.0F;
                float hotbarY = event.height() - BOTTOM - 20.0F;
                for (int slot = 0; slot < 9; slot++) {
                    float countX = hotbarX + slot * (HOTBAR_ITEM + HOTBAR_GAP) - 2.0F;
                    SkijaUi.textShadow(canvas, Integer.toString(slot + 1),
                            countX, hotbarY + 11.0F, 8.0F, 0xDFFFFFFF, 6.5F);
                }
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    @Listen
    private void onVanillaHud(VanillaHudRenderEvent event) {
        if (noPlayer()) return;

        GuiGraphicsExtractor graphics = event.graphics();
        float x = LEFT + AVATAR + 7.0F + BAR_WIDTH + 12.0F;
        float transitionOffset = HUD_ENTRY_OFFSET * (1.0F - transitionProgress());
        float y = event.height() - BOTTOM - 20.0F + transitionOffset;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            float itemX = slot * (HOTBAR_ITEM + HOTBAR_GAP);
            if (!stack.isEmpty()) {
                graphics.item(stack, Math.round(itemX), 0);
                graphics.itemDecorations(mc.font, stack, Math.round(itemX), 0);
            }
        }
        graphics.pose().popMatrix();

        ItemStack held = heldTool();
        if (held.isEmpty()) return;

        float itemX = event.width() - 12.0F - HELD_ITEM_SIZE;
        float itemY = event.height() - 28.0F - HELD_ITEM_SIZE + transitionOffset;
        graphics.pose().pushMatrix();
        graphics.pose().translate(itemX + HELD_ITEM_SIZE * 0.5F,
                itemY + HELD_ITEM_SIZE * 0.5F);
        graphics.pose().rotate(0.7853982F);
        graphics.pose().scale(4.0F, 4.0F);
        graphics.item(held, -8, -8);
        graphics.pose().popMatrix();
    }

    private ItemStack heldTool() {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.contains("sword") || path.contains("pickaxe") || path.contains("axe")
                || path.contains("shovel") || path.contains("hoe")
                ? stack : ItemStack.EMPTY;
    }

    private static String heldItemLabel(ItemStack stack) {
        String enchantments = enchantmentSummary(stack);
        String itemName = stack.getHoverName().getString();
        return enchantments.isEmpty() ? itemName : itemName + "  " + enchantments;
    }

    private static String enchantmentSummary(ItemStack stack) {
        ItemEnchantments enchantments = stack.getEnchantments();
        if (enchantments.isEmpty()) return "";

        StringJoiner names = new StringJoiner(" / ");
        int shown = 0;
        for (var entry : enchantments.entrySet()) {
            names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
            if (++shown >= 3) break;
        }
        if (enchantments.size() > shown) names.add("+" + (enchantments.size() - shown));
        return names.toString();
    }

    private static void drawAvatar(Canvas canvas, AbstractClientPlayer player, float x, float y) {
        Identifier skin = player.getSkin().body().texturePath();
        try (SkijaRenderer.BorrowedImage borrowed = SkijaRenderer.borrowTexture(skin)) {
            if (borrowed == null) return;
            float textureWidth = borrowed.image().getWidth();
            float textureHeight = borrowed.image().getHeight();
            canvas.save();
            try {
                canvas.clipRRect(RRect.makeXYWH(x, y, AVATAR, AVATAR, 8.0F),
                        ClipMode.INTERSECT, true);
                AVATAR_PAINT.setMode(PaintMode.FILL).setColor(0xFFFFFFFF);
                Rect destination = Rect.makeXYWH(x, y, AVATAR, AVATAR);
                drawSkinLayer(canvas, borrowed, destination, textureWidth, textureHeight, 8.0F, 8.0F);
                drawSkinLayer(canvas, borrowed, destination, textureWidth, textureHeight, 40.0F, 8.0F);
            } finally {
                canvas.restore();
            }
        } catch (Throwable ignored) {
            SkijaUi.rounded(canvas, x, y, AVATAR, AVATAR, 8.0F, 0xFF27313D);
        }
    }

    private static void drawSkinLayer(Canvas canvas, SkijaRenderer.BorrowedImage borrowed,
                                      Rect destination, float width, float height,
                                      float sourceX, float sourceY) {
        Rect source = Rect.makeLTRB(sourceX / 64.0F * width, sourceY / 64.0F * height,
                (sourceX + 8.0F) / 64.0F * width, (sourceY + 8.0F) / 64.0F * height);
        canvas.drawImageRect(borrowed.image(), source, destination, SamplingMode.DEFAULT,
                AVATAR_PAINT, true);
    }

    private static float smooth(float current, float target, float frame) {
        float factor = 1.0F - (float) Math.pow(0.001F, frame);
        return current + (target - current) * factor;
    }

    /** Cubic ease-out: most of the movement happens early and settles softly. */
    public float transitionProgress() {
        if (!isEnabled()) return 0.0F;
        if (transitionStartedNanos == 0L) return 1.0F;
        float linear = clamp((System.nanoTime() - transitionStartedNanos)
                / (float) TRANSITION_DURATION_NANOS, 0.0F, 1.0F);
        float remaining = 1.0F - linear;
        return 1.0F - remaining * remaining * remaining;
    }

    public float vanillaHotbarOffset() {
        return VANILLA_HOTBAR_EXIT_OFFSET * transitionProgress();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
