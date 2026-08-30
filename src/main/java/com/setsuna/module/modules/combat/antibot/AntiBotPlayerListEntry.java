package com.setsuna.module.modules.combat.antibot;

import java.util.UUID;

public record AntiBotPlayerListEntry(
        UUID profileId,
        boolean displayNamePresent,
        int displayNameSiblingCount,
        CombatGameMode gameMode) {

    public AntiBotPlayerListEntry {
        if (displayNameSiblingCount < 0) {
            throw new IllegalArgumentException("displayNameSiblingCount must be non-negative");
        }
    }

    public boolean isSpawnCandidate() {
        return profileId != null
                && displayNamePresent
                && displayNameSiblingCount == 0
                && gameMode == CombatGameMode.SURVIVAL;
    }
}
