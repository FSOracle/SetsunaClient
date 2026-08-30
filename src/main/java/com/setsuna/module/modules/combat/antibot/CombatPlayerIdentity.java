package com.setsuna.module.modules.combat.antibot;

import java.util.Objects;
import java.util.UUID;

public record CombatPlayerIdentity(int entityId, UUID profileId) {
    public CombatPlayerIdentity {
        Objects.requireNonNull(profileId, "profileId");
    }
}
