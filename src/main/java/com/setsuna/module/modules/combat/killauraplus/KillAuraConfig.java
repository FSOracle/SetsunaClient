package com.setsuna.module.modules.combat.killauraplus;

import java.util.Objects;

public record KillAuraConfig(
        KillAuraMode mode,
        KillAuraPriority priority,
        KillAuraRotationMode rotationMode,
        KillAuraCriticalMode criticalMode,
        double rotationRange,
        int rotationSpeed,
        double range,
        int fov,
        boolean onlyWeapon,
        boolean swing,
        boolean fakeBlock,
        boolean players,
        boolean mobs,
        boolean animals,
        boolean invisibles,
        boolean ignoreTeam,
        boolean ignoreFriends) {

    public KillAuraConfig {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(rotationMode, "rotationMode");
        Objects.requireNonNull(criticalMode, "criticalMode");
    }
}
