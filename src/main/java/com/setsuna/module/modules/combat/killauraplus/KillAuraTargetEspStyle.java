package com.setsuna.module.modules.combat.killauraplus;

import java.awt.Color;
import java.util.Objects;

public record KillAuraTargetEspStyle(
        float radius,
        float alpha,
        Color sideColor,
        Color lineColor) {

    public KillAuraTargetEspStyle {
        if (!Float.isFinite(radius) || radius < 0.0f) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
        if (!Float.isFinite(alpha) || alpha < 0.0f) {
            throw new IllegalArgumentException("alpha must be finite and non-negative");
        }
        Objects.requireNonNull(sideColor, "sideColor");
        Objects.requireNonNull(lineColor, "lineColor");
    }
}
