package com.setsuna.util.player;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves Heypixel player health from belowHealth/health scoreboard updates. */
public final class HeypixelHealthSource {

    private static final String BELOW_HEALTH = "belowHealth";
    private static final String HEALTH = "health";

    private final Map<String, Integer> healths = new ConcurrentHashMap<>();

    public void accept(Packet<?> packet, String localPlayerName) {
        if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundDisconnectPacket) {
            clear();
            return;
        }
        if (packet instanceof ClientboundSetScorePacket scorePacket) {
            String owner = scorePacket.owner();
            if (isHealthObjective(scorePacket.objectiveName())
                    && owner != null
                    && !owner.equals(localPlayerName)) {
                healths.put(owner, scorePacket.score());
            }
            return;
        }
        if (packet instanceof ClientboundResetScorePacket resetPacket
                && (resetPacket.objectiveName() == null
                || isHealthObjective(resetPacket.objectiveName()))) {
            healths.remove(resetPacket.owner());
        }
    }

    public Float getHealth(Player player) {
        if (player == null) {
            return null;
        }
        String playerName = player.getGameProfile().name();
        Integer cached = healths.get(playerName);
        if (cached != null) {
            return normalize(cached);
        }
        return readScoreboard(player);
    }

    public void clear() {
        healths.clear();
    }

    static boolean isHealthObjective(String objectiveName) {
        return BELOW_HEALTH.equalsIgnoreCase(objectiveName)
                || HEALTH.equalsIgnoreCase(objectiveName);
    }

    static Float normalize(int health) {
        return (float) Math.max(1, health);
    }

    Float cachedHealth(String playerName) {
        Integer health = healths.get(playerName);
        return health == null ? null : normalize(health);
    }

    public static boolean shouldCancelLocalHealth(float health) {
        return health > 20.0F;
    }

    private static Float readScoreboard(Player player) {
        Scoreboard scoreboard = player.level().getScoreboard();
        ScoreHolder scoreHolder = ScoreHolder.fromGameProfile(player.getGameProfile());
        Float belowHealth = readObjective(scoreboard, scoreHolder, BELOW_HEALTH);
        return belowHealth != null ? belowHealth : readObjective(scoreboard, scoreHolder, HEALTH);
    }

    private static Float readObjective(Scoreboard scoreboard, ScoreHolder scoreHolder, String expectedName) {
        Objective exactObjective = scoreboard.getObjective(expectedName);
        if (exactObjective != null) {
            Float exactScore = readScore(scoreboard, scoreHolder, exactObjective);
            if (exactScore != null) {
                return exactScore;
            }
        }
        for (Objective objective : scoreboard.getObjectives()) {
            if (objective.getName().equalsIgnoreCase(expectedName)) {
                Float score = readScore(scoreboard, scoreHolder, objective);
                if (score != null) {
                    return score;
                }
            }
        }
        return null;
    }

    private static Float readScore(Scoreboard scoreboard, ScoreHolder scoreHolder, Objective objective) {
        ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
        return score == null ? null : normalize(score.value());
    }
}
