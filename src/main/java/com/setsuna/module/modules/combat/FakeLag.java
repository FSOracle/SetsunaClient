package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.TickEvent;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.math.RandomUtils;
import com.setsuna.util.network.BlinkManager;
import com.setsuna.util.network.TransferOrigin;
import com.setsuna.util.player.TeamColorUtils;
import com.setsuna.util.time.Chronometer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Direct port of FurrySense FakeLag and its Constant/Dynamic packet decisions. */
public final class FakeLag extends Module {

    public static final FakeLag INSTANCE = new FakeLag();

    private enum Mode {
        Constant,
        Dynamic
    }

    private final DoubleSetting rangeMin = add(
            new DoubleSetting("Min Range", 2.0, 0.0, 10.0, 0.1));
    private final DoubleSetting rangeMax = add(
            new DoubleSetting("Max Range", 5.0, 0.0, 10.0, 0.1));
    private final IntSetting delayMin = add(
            new IntSetting("Min Delay", 300, 0, 1000, 10));
    private final IntSetting delayMax = add(
            new IntSetting("Max Delay", 600, 0, 1000, 10));
    private final IntSetting recoilTime = add(
            new IntSetting("Recoil Time", 250, 0, 1000, 10));
    private final EnumSetting<Mode> mode = add(
            new EnumSetting<>("Mode", Mode.Dynamic));
    private final BooleanSetting entityInteract = add(
            new BooleanSetting("Entity Interact", true));
    private final BooleanSetting blockInteract = add(
            new BooleanSetting("Block Interact", true));
    private final BooleanSetting action = add(
            new BooleanSetting("Action", true));

    private final Chronometer chronometer = new Chronometer();
    private int nextDelay;
    private boolean enemyNearby;

    private FakeLag() {
        super("FakeLag", Category.COMBAT);
        nextDelay = randomDelay();
    }

    @Listen
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            enemyNearby = false;
            return;
        }
        enemyNearby = findEnemy(mc.player) != null;
    }

    public BlinkManager.Action decide(Packet<?> packet, TransferOrigin origin) {
        if (!isEnabled()) {
            return BlinkManager.Action.FLUSH;
        }

        Player player = mc.player;
        if (origin == TransferOrigin.INCOMING) {
            if (shouldFlushOutgoing(packet, player)) {
                chronometer.reset();
                return BlinkManager.Action.FLUSH;
            }
            return BlinkManager.Action.PASS;
        }

        if (player == null
                || mc.level == null
                || player.isDeadOrDying()
                || player.isInWater()
                || mc.screen != null) {
            return BlinkManager.Action.FLUSH;
        }
        if (!chronometer.hasAtLeastElapsed(recoilTime.get())) {
            return BlinkManager.Action.FLUSH;
        }
        if (BlinkManager.INSTANCE.isAboveTime(nextDelay)) {
            nextDelay = randomDelay();
            return BlinkManager.Action.FLUSH;
        }
        // The client consumes its attack cooldown before this packet reaches
        // the server, so an attack must never wait behind fake lag.
        if (packet instanceof ServerboundAttackPacket) {
            chronometer.reset();
            return BlinkManager.Action.FLUSH;
        }
        if (matchesConfiguredFlush(packet)) {
            chronometer.reset();
            return BlinkManager.Action.FLUSH;
        }
        if (packet instanceof ServerboundResourcePackPacket) {
            chronometer.reset();
            return BlinkManager.Action.FLUSH;
        }
        if (player.isUsingItem()
                && player.getUseItem().getComponents().has(DataComponents.CONSUMABLE)) {
            return BlinkManager.Action.FLUSH;
        }
        if (mode.is(Mode.Constant)) {
            return BlinkManager.Action.QUEUE;
        }
        return dynamicDecision(player);
    }

    private boolean shouldFlushOutgoing(Packet<?> packet, Player player) {
        return shouldFlushOutgoing(packet, player == null ? null : player.getId());
    }

    static boolean shouldFlushOutgoing(Packet<?> packet, Integer playerId) {
        if (packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundSetHealthPacket) {
            return true;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            return playerId != null
                    && motion.id() == playerId
                    && !motion.movement().equals(Vec3.ZERO);
        }
        return packet instanceof ClientboundExplodePacket explosion
                && explosion.playerKnockback().isPresent()
                && !explosion.playerKnockback().orElseThrow().equals(Vec3.ZERO);
    }

    @Override
    protected void onDisable() {
        enemyNearby = false;
        BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
    }

    private BlinkManager.Action dynamicDecision(Player player) {
        if (!enemyNearby) {
            return BlinkManager.Action.FLUSH;
        }
        Vec3 serverPosition = BlinkManager.INSTANCE.firstPosition();
        if (serverPosition == null) {
            return BlinkManager.Action.QUEUE;
        }

        AABB serverPlayerBox = player.getBoundingBox()
                .move(serverPosition.subtract(player.position()));
        List<LivingEntity> entities = enemiesAround(player, serverPosition, rangeMax.get());
        if (entities.isEmpty()) {
            return BlinkManager.Action.FLUSH;
        }

        boolean intersects = entities.stream()
                .anyMatch(entity -> entity.getBoundingBox().intersects(serverPlayerBox));
        double serverDistance = entities.stream()
                .mapToDouble(entity -> entity.position().distanceTo(serverPosition))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
        double clientDistance = entities.stream()
                .mapToDouble(entity -> entity.position().distanceTo(player.position()))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
        if (serverDistance < clientDistance || intersects) {
            return BlinkManager.Action.FLUSH;
        }
        return BlinkManager.Action.QUEUE;
    }

    boolean matchesConfiguredFlush(Packet<?> packet) {
        if (packet == null) {
            return false;
        }
        if (packet instanceof ServerboundAttackPacket) {
            return true;
        }
        if (entityInteract.get()
                && (packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundTeleportToEntityPacket
                || packet instanceof ServerboundSwingPacket)) {
            return true;
        }
        if (blockInteract.get()
                && (packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundSignUpdatePacket)) {
            return true;
        }
        return action.get() && packet instanceof ServerboundPlayerActionPacket;
    }

    private LivingEntity findEnemy(Player player) {
        double minimum = Math.min(rangeMin.get(), rangeMax.get());
        double maximum = Math.max(rangeMin.get(), rangeMax.get());
        LivingEntity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        double minimumSquared = minimum * minimum;
        double maximumSquared = maximum * maximum;
        for (LivingEntity entity : enemiesAround(player, player.position(), maximum)) {
            double distance = squaredBoxDistance(entity.getBoundingBox(), player.position());
            if (distance >= minimumSquared
                    && distance <= maximumSquared
                    && distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private List<LivingEntity> enemiesAround(Player player, Vec3 center, double radius) {
        AABB search = player.getBoundingBox()
                .move(center.subtract(player.position()))
                .inflate(radius);
        ArrayList<LivingEntity> result = new ArrayList<>();
        for (Entity entity : mc.level.getEntities(
                player, search, candidate -> candidate instanceof LivingEntity)) {
            if (entity instanceof LivingEntity living
                    && shouldBeAttacked(player, living)
                    && squaredBoxDistance(living.getBoundingBox(), center) <= radius * radius) {
                result.add(living);
            }
        }
        return result;
    }

    private boolean shouldBeAttacked(Player player, LivingEntity entity) {
        if (!(entity instanceof Attackable)
                || entity == player
                || !entity.isAlive()
                || entity.isRemoved()
                || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof Player other) {
            if (other.isCreative() || player.isAlliedTo(other)) {
                return false;
            }
            if (FriendManager.INSTANCE.isFriend(other)
                    || TeamColorUtils.isFriendlyColor(other)
                    || AntiBot.INSTANCE.isBot(other)) {
                return false;
            }
        }
        return true;
    }

    private int randomDelay() {
        return RandomUtils.nextIntInclusive(delayMin.get(), delayMax.get());
    }

    static double squaredBoxDistance(AABB box, Vec3 point) {
        double x = Math.max(box.minX - point.x, Math.max(0.0, point.x - box.maxX));
        double y = Math.max(box.minY - point.y, Math.max(0.0, point.y - box.maxY));
        double z = Math.max(box.minZ - point.z, Math.max(0.0, point.z - box.maxZ));
        return x * x + y * y + z * z;
    }

    @Override
    public String getInfo() {
        if (!isEnabled()) {
            return "";
        }
        return "[" + mode.get().name() + " | "
                + BlinkManager.INSTANCE.queuedPackets() + "p]";
    }
}
