package com.setsuna.util.player;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

import java.awt.*;

public final class TeamColorUtils {

    private static final int NAME_ALPHA = 235;
    private static final int GREEN = ChatFormatting.GREEN.getColor();
    private static final int DARK_GREEN = ChatFormatting.DARK_GREEN.getColor();
    private static final int YELLOW = ChatFormatting.YELLOW.getColor();
    private static final int GOLD = ChatFormatting.GOLD.getColor();
    private static final int RED = ChatFormatting.RED.getColor();
    private static final int DARK_RED = ChatFormatting.DARK_RED.getColor();

    private TeamColorUtils() {
    }

    public static boolean isFriendlyColor(Player player) {
        Integer color = getTeamTextColor(player);
        return color != null && isFriendlyColor(color);
    }

    public static boolean isEnemyColor(Player player) {
        Integer color = getTeamTextColor(player);
        return color != null && isEnemyColor(color);
    }

    public static Color getNameColor(Player player, Color fallback) {
        Integer color = getTeamTextColor(player);
        return color == null ? fallback : new Color(color | (NAME_ALPHA << 24), true);
    }

    public static Integer getTeamTextColor(Player player) {
        Integer displayColor = findColor(player.getDisplayName());
        if (displayColor != null) {
            return displayColor;
        }

        Team team = player.getTeam();
        if (team == null || team.getColor().getColor() == null) {
            return null;
        }
        return team.getColor().getColor();
    }

    public static Integer getTextColor(Component component) {
        return component == null ? null : findColor(component);
    }

    public static boolean sameColor(Integer first, Integer second) {
        return first != null && second != null && sameColor(first.intValue(), second.intValue());
    }

    private static Integer findColor(Component component) {
        TextColor color = component.getStyle().getColor();
        if (color != null) {
            return color.getValue();
        }

        for (Component sibling : component.getSiblings()) {
            Integer siblingColor = findColor(sibling);
            if (siblingColor != null) {
                return siblingColor;
            }
        }
        return null;
    }

    private static boolean isFriendlyColor(int color) {
        return sameColor(color, GREEN) || sameColor(color, DARK_GREEN) || sameColor(color, YELLOW) || sameColor(color, GOLD);
    }

    private static boolean isEnemyColor(int color) {
        return sameColor(color, RED) || sameColor(color, DARK_RED);
    }

    private static boolean sameColor(int first, int second) {
        return (first & 0xFFFFFF) == (second & 0xFFFFFF);
    }

}
