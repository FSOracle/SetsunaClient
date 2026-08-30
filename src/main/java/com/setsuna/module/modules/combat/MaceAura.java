package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.manager.RotationManager;
import com.setsuna.manager.target.TargetManager;
import com.setsuna.manager.target.TargetRequest;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.player.FindItemResult;
import com.setsuna.util.player.InvUtils;
import com.setsuna.util.rotation.Priority;
import com.setsuna.util.rotation.RotationUtils;
import com.setsuna.util.timer.TimerUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class MaceAura extends Module {

    public static final MaceAura INSTANCE = new MaceAura();

    private enum AttackMode {
        Normal,
        Mace
    }

    private enum TargetPriority {
        Distance,
        Angle,
        Health
    }

    private final EnumSetting<AttackMode> mode = add(new EnumSetting<>("Mode", AttackMode.Mace));
    private final EnumSetting<TargetPriority> priority = add(new EnumSetting<>("Priority", TargetPriority.Distance));
    private final DoubleSetting range = add(new DoubleSetting("Range", 3.0, 1.0, 6.0, 0.1));
    private final DoubleSetting moveDistance = add(new DoubleSetting("Move Distance", 8.0, 1.0, 20.0, 0.1));
    private final BooleanSetting paperServer = add(new BooleanSetting("Paper Server", true));
    private final DoubleSetting vclip = add(new DoubleSetting("VClip", 10.0, 1.0, 512.0, 1.0));
    private final BooleanSetting damageOverride = add(new BooleanSetting("Damage VClip", true));
    private final DoubleSetting overrideVClip = add(new DoubleSetting("Override VClip", 30.0, 1.0, 512.0, 1.0)
            .visibleWhen(damageOverride::get));
    private final BooleanSetting swingHand = add(new BooleanSetting("Swing Hand", false));
    private final BooleanSetting cooldown = add(new BooleanSetting("Cooldown", true));
    private final DoubleSetting cooldownBase = add(new DoubleSetting("Cooldown Base", 0.75, 0.1, 1.0, 0.05)
            .visibleWhen(cooldown::get));
    private final IntSetting attackDelay = add(new IntSetting("Attack Delay", 50, 1, 2000, 1)
            .visibleWhen(() -> !cooldown.get()));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting animals = add(new BooleanSetting("Animals", false));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", false));
    private final BooleanSetting villagers = add(new BooleanSetting("Villagers", false));
    private final BooleanSetting slimes = add(new BooleanSetting("Slimes", false));

    private final TimerUtils attackTimer = new TimerUtils();
    private LivingEntity target;

    private MaceAura() {
        super("Mace Aura", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        target = null;
        attackTimer.reset();
    }

    @Override
    protected void onDisable() {
        target = null;
        InvUtils.swapBack();
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer() || mc.gameMode == null || mc.getConnection() == null) {
            target = null;
            return;
        }

        updateTarget();
        if (target == null) {
            return;
        }

        RotationManager.INSTANCE.setRotations(
                RotationUtils.getRotationsToEntity(target), 10.0, Priority.Medium);
        if (isReadyToAttack()) {
            attackTarget();
        }
    }

    private void updateTarget() {
        List<LivingEntity> candidates = new ArrayList<>(TargetManager.INSTANCE.acquireTargets(
                TargetRequest.of(
                        range.get(),
                        360.0F,
                        players.get(),
                        mobs.get() || slimes.get(),
                        animals.get(),
                        villagers.get(),
                        true,
                        living -> !(living instanceof Slime) || slimes.get(),
                        64
                )));

        switch (priority.get()) {
            case Distance -> candidates.sort((a, b) -> Double.compare(
                    RotationUtils.getEyeDistanceToEntity(a), RotationUtils.getEyeDistanceToEntity(b)));
            case Angle -> candidates.sort((a, b) -> Double.compare(getAngleScore(a), getAngleScore(b)));
            case Health -> candidates.sort((a, b) -> Float.compare(a.getHealth(), b.getHealth()));
        }
        target = candidates.isEmpty() ? null : candidates.getFirst();
    }

    private double getAngleScore(LivingEntity entity) {
        Vec3 hitVec = closestPoint(entity.getBoundingBox(), mc.player.getEyePosition());
        var rotation = RotationUtils.calculate(hitVec);
        float yawDifference = Math.abs(Mth.wrapDegrees(rotation.getYaw() - mc.player.getYRot()));
        float pitchDifference = Math.abs(Mth.wrapDegrees(rotation.getPitch() - mc.player.getXRot()));
        return yawDifference * yawDifference + pitchDifference * pitchDifference;
    }

    private static Vec3 closestPoint(AABB box, Vec3 point) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    private boolean isReadyToAttack() {
        return cooldown.get()
                ? mc.player.getAttackStrengthScale(0.5F) >= cooldownBase.get()
                : attackTimer.passedMillis(attackDelay.get());
    }

    private void attackTarget() {
        if (target == null || RotationUtils.getEyeDistanceToEntity(target) > range.get()) {
            return;
        }

        if (mode.is(AttackMode.Normal)) {
            attack();
        } else {
            doMaceAttack(vclip.get());
            if (damageOverride.get()) {
                doMaceAttack(overrideVClip.get());
            }
        }
        attackTimer.reset();
    }

    private void doMaceAttack(double verticalClip) {
        int currentSlot = mc.player.getInventory().getSelectedSlot();
        boolean hotbarSwap = false;
        boolean inventorySwap = false;

        FindItemResult hotbar = InvUtils.findInHotbar(Items.MACE);
        if (hotbar.found() && hotbar.slot() >= 0 && hotbar.slot() < 9) {
            if (hotbar.slot() != currentSlot) {
                InvUtils.swap(hotbar.slot(), true);
                hotbarSwap = true;
            }
        } else {
            FindItemResult inventory = InvUtils.find(Items.MACE);
            if (!inventory.found()) {
                return;
            }
            InvUtils.invSwap(inventory.slot());
            inventorySwap = true;
        }

        try {
            Vec3 start = mc.player.position();
            Vec3 destination = start.add(0.0, verticalClip, 0.0);
            if (paperServer.get()) {
                for (int i = 0; i < 4; i++) {
                    mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(false, false));
                }
            }

            sendSegmentedMove(start, destination, moveDistance.get(), false, 20);
            sendMovePacket(start, false);
            attack();
            sendMovePacket(mc.player.getX(), mc.player.getY() + 1.0E-4, mc.player.getZ(), false);
        } finally {
            if (inventorySwap) {
                InvUtils.invSwapBack();
            }
            if (hotbarSwap) {
                InvUtils.swapBack();
            }
        }
    }

    private void sendSegmentedMove(Vec3 from, Vec3 to, double maximumDistance,
                                   boolean onGround, int maximumPackets) {
        double distance = from.distanceTo(to);
        if (distance <= 0.0 || maximumDistance <= 0.0) {
            sendMovePacket(to, onGround);
            return;
        }

        int steps = (int) Math.ceil(distance / maximumDistance);
        if (maximumPackets > 0) {
            steps = Math.min(steps, maximumPackets);
        }
        Vec3 delta = to.subtract(from);
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            sendMovePacket(from.add(delta.scale(progress)), onGround);
        }
    }

    private void sendMovePacket(Vec3 position, boolean onGround) {
        sendMovePacket(position.x, position.y, position.z, onGround);
    }

    private void sendMovePacket(double x, double y, double z, boolean onGround) {
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, onGround, false));
    }

    private void attack() {
        if (target == null) {
            return;
        }
        mc.gameMode.attack(mc.player, target);
        if (swingHand.get()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }
}
