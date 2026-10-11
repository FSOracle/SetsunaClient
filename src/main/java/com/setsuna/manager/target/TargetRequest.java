package com.setsuna.manager.target;

import net.minecraft.world.entity.LivingEntity;

import java.util.function.Predicate;

public record TargetRequest(
        double range,
        float fov,
        boolean player,
        boolean mob,
        boolean animal,
        boolean villager,
        boolean invisible,
        Predicate<LivingEntity> extraFilter,
        int maxTargets
) {
    private static final Predicate<LivingEntity> ACCEPT_ANY = ignored -> true;

    public TargetRequest {
        range = nonNegative(range);
        fov = boundedFov(fov);
        extraFilter = extraFilter == null ? ACCEPT_ANY : extraFilter;
        maxTargets = Math.max(1, maxTargets);
    }

    public static TargetRequest of(
            double range,
            float fov,
            boolean player,
            boolean mob,
            boolean animal,
            boolean villager,
            boolean invisible,
            int maxTargets
    ) {
        return create(range, fov, player, mob, animal, villager, invisible, ACCEPT_ANY, maxTargets);
    }

    public static TargetRequest of(
            double range,
            float fov,
            boolean player,
            boolean mob,
            boolean animal,
            boolean villager,
            boolean invisible,
            Predicate<LivingEntity> extraFilter,
            int maxTargets
    ) {
        return create(range, fov, player, mob, animal, villager, invisible, extraFilter, maxTargets);
    }

    private static TargetRequest create(
            double range,
            float fov,
            boolean player,
            boolean mob,
            boolean animal,
            boolean villager,
            boolean invisible,
            Predicate<LivingEntity> filter,
            int limit
    ) {
        return new TargetRequest(range, fov, player, mob, animal, villager, invisible, filter, limit);
    }

    private static double nonNegative(double value) {
        return value < 0.0D ? 0.0D : value;
    }

    private static float boundedFov(float value) {
        return Math.max(0.0F, Math.min(360.0F, value));
    }
}
