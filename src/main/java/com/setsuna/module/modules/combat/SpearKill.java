package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.manager.target.TargetManager;
import com.setsuna.manager.target.TargetRequest;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpearKill extends Module {

    public static final SpearKill INSTANCE = new SpearKill();

    private enum LungeMode {
        DirectionBased,
        Above
    }

    private final EnumSetting<LungeMode> lungeMode = add(new EnumSetting<>("Lunge Mode", LungeMode.DirectionBased));
    private final DoubleSetting maxRange = add(new DoubleSetting("Max Range", 6.0, 1.0, 16.0, 0.1));
    private final DoubleSetting lungeStrength = add(new DoubleSetting("Lunge Strength", 3.7, 0.5, 15.0, 0.1));
    private final DoubleSetting aboveHeight = add(new DoubleSetting("Above Height", 10.0, 3.0, 30.0, 0.1)
            .visibleWhen(() -> lungeMode.is(LungeMode.Above)));
    private final DoubleSetting aboveHeightDistance = add(new DoubleSetting(
            "Above Height Distance", 3.0, 1.0, 10.0, 0.1)
            .visibleWhen(() -> lungeMode.is(LungeMode.Above)));
    private final DoubleSetting stopDistance = add(new DoubleSetting("Stop Distance", 0.6, 0.0, 10.0, 0.1));
    private final IntSetting chargeTimeModifier = add(new IntSetting("Charge Time", 100, 0, 100, 1));
    private final BooleanSetting stopOnTarget = add(new BooleanSetting("Stop On Target", true));
    private final BooleanSetting autoSwitch = add(new BooleanSetting("Auto Switch", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", false));
    private final BooleanSetting animals = add(new BooleanSetting("Animals", false));
    private final BooleanSetting villagers = add(new BooleanSetting("Villagers", false));
    private final BooleanSetting invisible = add(new BooleanSetting("Invisible", true));

    private LivingEntity target;
    private Vec3 aboveTargetPosition;
    private boolean approachingAbovePosition;

    private SpearKill() {
        super("Spear Kill", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        resetTarget();
    }

    @Override
    protected void onDisable() {
        resetTarget();
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            resetTarget();
            return;
        }

        updateTarget();
        if (!isUsingSpear()) {
            resetTarget();
            return;
        }

        if (target != null && !target.isAlive()) {
            stopMovementIfRequested();
            resetTarget();
        }
        if (target != null) {
            lunge();
        }
    }

    private void updateTarget() {
        if (target != null && !target.isAlive()) {
            target = null;
        }
        if (target != null && !autoSwitch.get()) {
            return;
        }

        var candidates = TargetManager.INSTANCE.acquireTargets(TargetRequest.of(
                maxRange.get(),
                360.0F,
                players.get(),
                mobs.get(),
                animals.get(),
                villagers.get(),
                invisible.get(),
                64));
        target = candidates.isEmpty() ? null : candidates.getFirst();
    }

    private boolean isUsingSpear() {
        Item item = mc.player.getUseItem().getItem();
        return item == Items.WOODEN_SPEAR
                || item == Items.STONE_SPEAR
                || item == Items.COPPER_SPEAR
                || item == Items.IRON_SPEAR
                || item == Items.GOLDEN_SPEAR
                || item == Items.DIAMOND_SPEAR
                || item == Items.NETHERITE_SPEAR;
    }

    private int readyTicks(Item item) {
        int ticks;
        if (item == Items.WOODEN_SPEAR) {
            ticks = 14;
        } else if (item == Items.STONE_SPEAR || item == Items.GOLDEN_SPEAR) {
            ticks = 13;
        } else if (item == Items.COPPER_SPEAR) {
            ticks = 12;
        } else if (item == Items.IRON_SPEAR) {
            ticks = 11;
        } else if (item == Items.DIAMOND_SPEAR) {
            ticks = 9;
        } else if (item == Items.NETHERITE_SPEAR) {
            ticks = 7;
        } else {
            ticks = 10;
        }
        return Math.round(ticks * (chargeTimeModifier.get() / 100.0F));
    }

    private void lunge() {
        if (target == null) {
            return;
        }
        if (rotate.get()) {
            rotateToTarget(target);
        }
        if (mc.player.getTicksUsingItem() <= readyTicks(mc.player.getUseItem().getItem())) {
            return;
        }

        AABB playerBox = mc.player.getBoundingBox().inflate(stopDistance.get());
        if (playerBox.intersects(target.getBoundingBox())) {
            stopMovementIfRequested();
            target = null;
            approachingAbovePosition = false;
            aboveTargetPosition = null;
            return;
        }

        Vec3 direction = switch (lungeMode.get()) {
            case DirectionBased -> target.getBoundingBox().getCenter()
                    .subtract(mc.player.position()).normalize();
            case Above -> directionForAboveMode();
        };
        if (direction.lengthSqr() < 1.0E-8) {
            return;
        }

        mc.player.setSprinting(true);
        mc.player.setDeltaMovement(direction.scale(lungeStrength.get()));
    }

    private Vec3 directionForAboveMode() {
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        if (!approachingAbovePosition || aboveTargetPosition == null) {
            aboveTargetPosition = targetCenter.add(0.0, aboveHeight.get(), 0.0);
            approachingAbovePosition = true;
        }

        Vec3 playerPosition = mc.player.position();
        if (playerPosition.distanceTo(aboveTargetPosition) < aboveHeightDistance.get()) {
            approachingAbovePosition = false;
            return targetCenter.subtract(playerPosition).normalize();
        }
        return aboveTargetPosition.subtract(playerPosition).normalize();
    }

    private void rotateToTarget(LivingEntity living) {
        var rotation = RotationUtils.getRotationsToEntity(living);
        mc.player.setYRot(rotation.getYaw());
        mc.player.setYHeadRot(rotation.getYaw());
        mc.player.setXRot(rotation.getPitch());
    }

    private void stopMovementIfRequested() {
        if (stopOnTarget.get()) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.setSprinting(false);
        }
    }

    private void resetTarget() {
        target = null;
        aboveTargetPosition = null;
        approachingAbovePosition = false;
    }
}
