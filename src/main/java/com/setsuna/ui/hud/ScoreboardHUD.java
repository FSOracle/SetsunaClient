package com.setsuna.ui.hud;

import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Comparator;
import java.util.List;

/** Keeps the vanilla scoreboard renderer while allowing background removal and movement. */
public final class ScoreboardHUD extends EpsilonHudModule {

    public static final ScoreboardHUD INSTANCE = new ScoreboardHUD();

    private static final int MAX_ROWS = 15;
    private static final float LINE_HEIGHT = 9.0F;
    private static final float HEADER_HEIGHT = 10.0F;

    public final BooleanSetting removeBackground = add(new BooleanSetting("Remove Background", true));

    private ScoreboardHUD() {
        super("Scoreboard HUD", 980, 330, 116.0F, 64.0F);
    }

    /** Moves the original scoreboard draw commands into the HUD Editor position. */
    public boolean positionVanilla(GuiGraphicsExtractor graphics, Objective objective) {
        if (!isEnabled() || objective == null) return false;

        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_ROWS)
                .toList();

        int contentWidth = mc.font.width(objective.getDisplayName());
        int colonWidth = mc.font.width(":");
        for (PlayerScoreEntry entry : entries) {
            Component name = PlayerTeam.formatNameForTeam(
                    scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
            int scoreWidth = mc.font.width(entry.formatValue(format));
            contentWidth = Math.max(contentWidth,
                    mc.font.width(name) + (scoreWidth > 0 ? colonWidth + scoreWidth : 0));
        }

        float width = contentWidth + 4.0F;
        float height = entries.size() * LINE_HEIGHT + HEADER_HEIGHT;
        float targetX = renderX(graphics.guiWidth(), width);
        float targetY = renderY(graphics.guiHeight(), height);
        updateBounds(width, height);

        int vanillaBottom = graphics.guiHeight() / 2
                + entries.size() * (int) LINE_HEIGHT / 3;
        float vanillaLeft = graphics.guiWidth() - contentWidth - 5.0F;
        float vanillaTop = vanillaBottom - entries.size() * LINE_HEIGHT - HEADER_HEIGHT;

        graphics.pose().pushMatrix();
        graphics.pose().translate(targetX - vanillaLeft, targetY - vanillaTop);
        return true;
    }

    public boolean shouldRemoveBackground() {
        return isEnabled() && removeBackground.get();
    }

    @Override
    public int editorColor() {
        return UiTheme.accent();
    }
}
