package com.setsuna.module.modules.combat.killauraplus;

/** Last gate that prevented an attack, exposed for local diagnostics. */
public enum KillAuraBlockReason {
    NONE,
    INACTIVE,
    NO_TARGET,
    UNSUPPORTED_WEAPON,
    COOLDOWN,
    CRITICAL,
    RAYTRACE,
    ROTATION_NOT_SENT,
    INTERACTION,
    ATTACKED
}
