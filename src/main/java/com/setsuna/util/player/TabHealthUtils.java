package com.setsuna.util.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

public final class TabHealthUtils {

    private TabHealthUtils() {
    }

    public static Float getTabHealth(Player player) {
        Scoreboard scoreboard = player.level().getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
        if (objective == null) {
            return null;
        }

        ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(player.getGameProfile()), objective);
        if (scoreInfo == null) {
            return null;
        }
        return (float) scoreInfo.value();
    }

}
