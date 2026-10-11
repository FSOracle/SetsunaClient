package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.entity.EntityUtils;
import com.setsuna.util.math.RandomUtils;
import com.setsuna.util.network.PacketSnapshot;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.time.Chronometer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Direct port of FurrySense BackTrack and its independent inbound packet queue. */
public final class Backtrack extends Module {

    public static final Backtrack INSTANCE = new Backtrack();

    private enum TargetMode {
        Attack,
        Range
    }

    private final DoubleSetting rangeMin = add(
            new DoubleSetting("Min Range", 1.0, 0.0, 10.0, 0.1));
    private final DoubleSetting rangeMax = add(
            new DoubleSetting("Max Range", 3.0, 0.0, 10.0, 0.1));
    private final IntSetting delayMin = add(
            new IntSetting("Min Delay", 100, 0, 1000, 10));
    private final IntSetting delayMax = add(
            new IntSetting("Max Delay", 150, 0, 1000, 10));
    private final IntSetting nextBacktrackDelay = add(
            new IntSetting("Next Delay", 10, 0, 2000, 5));
    private final IntSetting trackingBuffer = add(
            new IntSetting("Tracking Buffer", 500, 0, 2000, 50));
    private final DoubleSetting chance = add(
            new DoubleSetting("Chance", 50.0, 0.0, 100.0, 1.0));
    private final IntSetting lastAttackTime = add(
            new IntSetting("Attack Timeout", 1000, 0, 5000, 100));
    private final EnumSetting<TargetMode> targetMode = add(
            new EnumSetting<>("Target Mode", TargetMode.Attack));
    private final BooleanSetting pauseOnHurt = add(
            new BooleanSetting("Pause On Hurt", false));
    private final IntSetting hurtTimeThreshold = add(
            new IntSetting("Hurt Time Threshold", 3, 0, 10, 1));

    private final ConcurrentLinkedQueue<PacketSnapshot> packetQueue =
            new ConcurrentLinkedQueue<>();
    private final Chronometer chronometer = new Chronometer();
    private final Chronometer trackingBufferChrono = new Chronometer();
    private final Chronometer attackChrono = new Chronometer();

    private Entity target;
    private int currentDelay;
    private boolean shouldPause;
    private int currentChance = RandomUtils.inclusivePercentRoll();

    private Backtrack() {
        super("BackTrack", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        clear(false);
    }

    @Override
    protected void onDisable() {
        clear(true);
    }

    @Listen
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            clear(true);
            return;
        }
        if (targetMode.is(TargetMode.Range)) {
            findRangeTarget();
        }

        boolean hadQueued = !packetQueue.isEmpty();
        if (shouldCancelPackets()) {
            long now = System.currentTimeMillis();
            packetQueue.removeIf(snapshot -> {
                if (snapshot.timestamp() <= now - currentDelay) {
                    PacketUtils.dispatch(connection, snapshot.packet());
                    return true;
                }
                return false;
            });
        } else if (hadQueued) {
            flushAll(connection);
            clear();
        }
        if (packetQueue.isEmpty()) {
            currentDelay = randomDelay();
        }
    }

    /** Runs after BlinkManager, matching the source ConnectionMixin interception order. */
    @Listen(priority = Priority.LOWEST - 1)
    private void onIncomingPacket(PacketEvent.Receive event) {
        if (interceptIncoming(event.getPacket())) {
            event.cancel();
        }
    }

    public boolean interceptIncoming(Packet<?> packet) {
        if (!isEnabled()) {
            return false;
        }
        String className = packet.getClass().getSimpleName();
        if (className.contains("PlayerPosition") || className.contains("Respawn")) {
            if (isLagging()) {
                forceFlush();
            }
            return false;
        }
        if (className.contains("Disconnect")) {
            if (isLagging()) {
                forceFlush();
            }
            return false;
        }
        if (!shouldCancelPackets() && packetQueue.isEmpty()) {
            return false;
        }
        packetQueue.add(new PacketSnapshot(packet));
        return true;
    }

    /** Called from Minecraft.startAttack only when vanilla reports a successful attack. */
    public void onAttack(Entity attacked) {
        attackChrono.reset();
        currentChance = RandomUtils.inclusivePercentRoll();
        if (targetMode.is(TargetMode.Attack)) {
            processTarget(attacked);
        }
    }

    public boolean isLagging() {
        return isEnabled() && !packetQueue.isEmpty();
    }

    public int queuedPackets() {
        return packetQueue.size();
    }

    public void forceFlush() {
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            flushAll(connection);
        }
        clear(true);
    }

    private void findRangeTarget() {
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        double minimum = rangeMin.get();
        double maximum = rangeMax.get();
        for (Player candidate : mc.level.players()) {
            if (candidate == player
                    || !candidate.isAlive()
                    || candidate.isRemoved()
                    || candidate.isInvisible()
                    || player.isAlliedTo(candidate)) {
                continue;
            }
            double distance = player.distanceTo(candidate);
            if (distance >= minimum && distance <= maximum && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }

        if (best == null) {
            clear();
        } else {
            processTarget(best);
        }
    }

    private void processTarget(Entity enemy) {
        shouldPause = enemy instanceof LivingEntity living
                && living.hurtTime >= hurtTimeThreshold.get();
        if (!shouldBacktrack(enemy)) {
            return;
        }
        if (enemy != target) {
            clear(false);
        }
        target = enemy;
    }

    private boolean shouldBacktrack(Entity candidate) {
        if (mc.player == null || mc.level == null) {
            return false;
        }
        if (candidate == null || !candidate.isAlive() || candidate.isRemoved()) {
            return false;
        }

        double distance = mc.player.distanceTo(candidate);
        boolean inRange = distance >= rangeMin.get() && distance <= rangeMax.get();
        if (inRange) {
            trackingBufferChrono.reset();
        }
        boolean withinBuffer = inRange
                || !trackingBufferChrono.hasElapsed(trackingBuffer.get());
        return withinBuffer
                && candidate instanceof Player
                && mc.player.tickCount > 10
                && currentChance < chance.get().intValue()
                && chronometer.hasElapsed()
                && !shouldPause()
                && !attackChrono.hasElapsed(lastAttackTime.get());
    }

    private boolean shouldPause() {
        return pauseOnHurt.get() && shouldPause;
    }

    boolean shouldCancelPackets() {
        return target != null && target.isAlive() && shouldBacktrack(target);
    }

    private int randomDelay() {
        int minimum = delayMin.get();
        int maximum = delayMax.get();
        if (maximum <= minimum) {
            return minimum;
        }
        return RandomUtils.nextIntInclusive(minimum, maximum);
    }

    private void clear(boolean flush) {
        if (flush) {
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) {
                flushAll(connection);
            }
        } else {
            packetQueue.clear();
        }
        if (target != null) {
            chronometer.waitForAtLeast(nextBacktrackDelay.get());
        }
        target = null;
    }

    private void clear() {
        clear(true);
    }

    private void flushAll(ClientPacketListener connection) {
        PacketSnapshot snapshot;
        while ((snapshot = packetQueue.poll()) != null) {
            PacketUtils.dispatch(connection, snapshot.packet());
        }
    }

    @Override
    public String getInfo() {
        if (!isEnabled()) {
            return "";
        }
        if (target == null) {
            return "[No target]";
        }
        return "[" + EntityUtils.safeName(target) + " | " + packetQueue.size() + "p]";
    }
}
