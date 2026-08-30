package com.setsuna.ui.hud;

import com.setsuna.event.Listen;
import com.setsuna.event.events.VanillaHudRenderEvent;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Compact potion list using the active resource pack's real effect textures. */
public final class PotionHUD extends EpsilonHudModule {

    public static final PotionHUD INSTANCE = new PotionHUD();

    private static final int PANEL_COLOR = 0xD20A0B0D;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_GAP = 2;
    private static final int ICON_SIZE = 16;
    private static final int ICON_X = 5;
    private static final int TEXT_X = 25;
    private static final int MAX_TEXT_WIDTH = 108;
    private static final int MIN_WIDTH = 118;
    private static final int RIGHT_PADDING = 6;

    public final DoubleSetting scale = add(new DoubleSetting("Scale", 1.0, 0.7, 1.8, 0.05));
    public final BooleanSetting hideAmbient = add(new BooleanSetting("Hide Ambient", false));

    private PotionHUD() {
        super("Potion HUD", 1000, 300, MIN_WIDTH, ROW_HEIGHT);
    }

    @Listen
    private void onVanillaHud(VanillaHudRenderEvent event) {
        if (noPlayer()) return;

        List<EffectRow> rows = collectRows();
        if (rows.isEmpty()) {
            float panelScale = scale.get().floatValue();
            updateBounds(MIN_WIDTH * panelScale, ROW_HEIGHT * panelScale);
            return;
        }

        int textWidth = 0;
        for (EffectRow row : rows) {
            textWidth = Math.max(textWidth, mc.font.width(row.name()));
            textWidth = Math.max(textWidth, mc.font.width(row.duration()));
        }
        int baseWidth = Math.max(MIN_WIDTH, TEXT_X + Math.min(MAX_TEXT_WIDTH, textWidth) + RIGHT_PADDING);
        int baseHeight = rows.size() * ROW_HEIGHT + (rows.size() - 1) * ROW_GAP;
        float panelScale = scale.get().floatValue();
        float width = baseWidth * panelScale;
        float height = baseHeight * panelScale;
        float x = Math.round(renderX(event.width(), width));
        float y = Math.round(renderY(event.height(), height));
        updateBounds(width, height);

        GuiGraphicsExtractor graphics = event.graphics();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(panelScale, panelScale);

        for (int index = 0; index < rows.size(); index++) {
            EffectRow row = rows.get(index);
            int rowY = index * (ROW_HEIGHT + ROW_GAP);
            graphics.fill(0, rowY, baseWidth, rowY + ROW_HEIGHT, applyOpacity(PANEL_COLOR));
            graphics.fill(0, rowY, 2, rowY + ROW_HEIGHT, applyOpacity(row.color()));
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, row.icon(),
                    ICON_X, rowY + 3, ICON_SIZE, ICON_SIZE);

            String name = fit(row.name(), MAX_TEXT_WIDTH);
            String duration = fit(row.duration(), MAX_TEXT_WIDTH);
            graphics.text(mc.font, name, TEXT_X, rowY + 2, applyOpacity(0xFFF0F1F3), false);
            graphics.text(mc.font, duration, TEXT_X, rowY + 11, applyOpacity(0xFF9DA3AA), false);
        }

        graphics.pose().popMatrix();
    }

    private List<EffectRow> collectRows() {
        List<EffectRow> rows = new ArrayList<>();
        for (MobEffectInstance instance : mc.player.getActiveEffects()) {
            if (hideAmbient.get() && instance.isAmbient()) continue;
            Holder<MobEffect> holder = instance.getEffect();
            String name = holder.value().getDisplayName().getString() + roman(instance.getAmplifier() + 1);
            String duration = instance.isInfiniteDuration() ? "Infinite" : formatDuration(instance.getDuration());
            int color = 0xFF000000 | (holder.value().getColor() & 0x00FFFFFF);
            rows.add(new EffectRow(name, duration, color, icon(holder)));
        }
        rows.sort(Comparator.comparing(EffectRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static Identifier icon(Holder<MobEffect> effect) {
        return Gui.getMobEffectSprite(effect);
    }

    private String fit(String text, int maximumWidth) {
        if (mc.font.width(text) <= maximumWidth) return text;
        String ellipsis = "...";
        int available = Math.max(0, maximumWidth - mc.font.width(ellipsis));
        return mc.font.plainSubstrByWidth(text, available) + ellipsis;
    }

    private static String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String roman(int level) {
        if (level <= 1) return "";
        return " " + switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }

    @Override
    protected boolean usesSharedOpacityLayer() {
        return false;
    }

    @Override
    public int editorColor() {
        return 0xFF8CD9FF;
    }

    private record EffectRow(String name, String duration, int color, Identifier icon) {
    }
}
