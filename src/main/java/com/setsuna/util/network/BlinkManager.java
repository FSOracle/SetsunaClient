package com.setsuna.util.network;

import com.setsuna.Setsuna;
import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.AttackPacketPreparationEvent;
import com.setsuna.event.events.BlinkPacketEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.module.modules.combat.FakeLag;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Shared bidirectional packet queue ported from FurrySense BlinkManager. */
public final class BlinkManager {

    public static final BlinkManager INSTANCE = new BlinkManager();

    private final ConcurrentLinkedQueue<BlinkPacketSnapshot> packetQueue =
            new ConcurrentLinkedQueue<>();
    private final ThreadLocal<Integer> replayDepth = ThreadLocal.withInitial(() -> 0);

    private BlinkManager() {
    }

    public boolean isReplaying() {
        return replayDepth.get() > 0;
    }

    public boolean isLagging() {
        return !packetQueue.isEmpty();
    }

    public int queuedPackets() {
        return packetQueue.size();
    }

    public List<BlinkPacketSnapshot> snapshots() {
        return List.copyOf(packetQueue);
    }

    public boolean has(TransferOrigin origin) {
        return packetQueue.stream().anyMatch(snapshot -> snapshot.origin() == origin);
    }

    /** Compatibility overload used by the existing movement engines. */
    public boolean has(BlinkPacketEvent.Origin origin) {
        return has(toTransferOrigin(origin));
    }

    @Listen(priority = Priority.LOWEST)
    private void send(PacketEvent.Send event) {
        if (interceptOutgoing(event.getPacket(), event.listener(), event.flush())) {
            event.cancel();
        }
    }

    @Listen(priority = Priority.LOWEST)
    private void receive(PacketEvent.Receive event) {
        if (interceptIncoming(event.getPacket())) {
            event.cancel();
        }
    }

    @Listen(priority = Priority.LOWEST)
    private void prepareAttack(AttackPacketPreparationEvent event) {
        flush(TransferOrigin.OUTGOING);
    }

    public boolean interceptOutgoing(
            Packet<?> packet,
            @Nullable ChannelFutureListener listener,
            boolean flush) {
        if (isReplaying()) {
            return false;
        }
        if (packet instanceof ServerboundAttackPacket) {
            flush(TransferOrigin.OUTGOING);
            return false;
        }
        Action action = fireDecision(packet, TransferOrigin.OUTGOING);
        if (action == Action.FLUSH) {
            flush(TransferOrigin.OUTGOING);
            return false;
        }
        if (action == Action.PASS || isAlwaysPassed(packet)) {
            return false;
        }
        packetQueue.offer(BlinkPacketSnapshot.outgoing(packet, listener, flush));
        return true;
    }

    public boolean interceptIncoming(Packet<?> packet) {
        if (isReplaying()) {
            return false;
        }
        Action action = fireDecision(packet, TransferOrigin.INCOMING);
        if (action == Action.FLUSH) {
            flush(TransferOrigin.OUTGOING);
            flush(TransferOrigin.INCOMING);
            return false;
        }
        if (action == Action.PASS || isAlwaysPassed(packet)) {
            return false;
        }
        packetQueue.offer(BlinkPacketSnapshot.incoming(packet));
        return true;
    }

    /** Called once per rendered client frame from MinecraftMixin. */
    public void onFrame() {
        ClientPacketListener listener = Setsuna.mc().getConnection();
        if (listener == null || !listener.getConnection().isConnected()) {
            packetQueue.clear();
            return;
        }
        if (fireDecision(null, TransferOrigin.OUTGOING) == Action.FLUSH) {
            flush(TransferOrigin.OUTGOING);
        }
        if (fireDecision(null, TransferOrigin.INCOMING) == Action.FLUSH) {
            flush(TransferOrigin.INCOMING);
        }
    }

    public List<Vec3> positions() {
        ArrayList<Vec3> result = new ArrayList<>();
        for (BlinkPacketSnapshot snapshot : packetQueue) {
            if (snapshot.packet() instanceof ServerboundMovePlayerPacket movement
                    && movement.hasPosition()) {
                result.add(new Vec3(
                        movement.getX(0.0), movement.getY(0.0), movement.getZ(0.0)));
            }
        }
        return result;
    }

    public Vec3 firstPosition() {
        for (BlinkPacketSnapshot snapshot : packetQueue) {
            if (snapshot.packet() instanceof ServerboundMovePlayerPacket movement
                    && movement.hasPosition()) {
                return new Vec3(
                        movement.getX(0.0), movement.getY(0.0), movement.getZ(0.0));
            }
        }
        return null;
    }

    /** Compatibility alias retained for existing callers. */
    public Vec3 firstOutgoingPosition() {
        return firstPosition();
    }

    public boolean isAboveTime(long delayMillis) {
        BlinkPacketSnapshot first = packetQueue.peek();
        return first != null && System.currentTimeMillis() - first.timestamp() >= delayMillis;
    }

    public void flush(Predicate<BlinkPacketSnapshot> predicate) {
        packetQueue.removeIf(snapshot -> {
            if (!predicate.test(snapshot)) {
                return false;
            }
            flushSnapshot(snapshot);
            return true;
        });
    }

    public void flush(TransferOrigin origin) {
        flush(snapshot -> snapshot.origin() == origin);
    }

    /** Compatibility overload retained for existing callers. */
    public void flush(BlinkPacketEvent.Origin origin) {
        flush(toTransferOrigin(origin));
    }

    public void remove(TransferOrigin origin) {
        packetQueue.removeIf(snapshot -> snapshot.origin() == origin);
    }

    /** Compatibility overload retained for existing callers. */
    public void remove(BlinkPacketEvent.Origin origin) {
        remove(toTransferOrigin(origin));
    }

    public void flushMovementCount(int count) {
        if (count <= 0) {
            return;
        }
        int movements = 0;
        Iterator<BlinkPacketSnapshot> iterator = packetQueue.iterator();
        while (iterator.hasNext()) {
            BlinkPacketSnapshot snapshot = iterator.next();
            if (snapshot.packet() instanceof ServerboundMovePlayerPacket movement
                    && movement.hasPosition()) {
                movements++;
            }
            flushSnapshot(snapshot);
            iterator.remove();
            if (movements >= count) {
                break;
            }
        }
    }

    public void cancel() {
        Vec3 position = firstPosition();
        if (position != null && Setsuna.mc().player != null) {
            Setsuna.mc().player.setPos(position.x, position.y, position.z);
        }
        for (BlinkPacketSnapshot snapshot : packetQueue) {
            if (!(snapshot.packet() instanceof ServerboundMovePlayerPacket)) {
                flushSnapshot(snapshot);
            }
        }
        packetQueue.clear();
    }

    public <T extends Packet<?>> void rewrite(Class<T> type, Consumer<T> action) {
        for (BlinkPacketSnapshot snapshot : packetQueue) {
            if (type.isInstance(snapshot.packet())) {
                action.accept(type.cast(snapshot.packet()));
            }
        }
    }

    public void clear() {
        packetQueue.clear();
    }

    private Action fireDecision(Packet<?> packet, TransferOrigin origin) {
        return FakeLag.INSTANCE.decide(packet, origin);
    }

    private boolean isAlwaysPassed(Packet<?> packet) {
        if (packet instanceof ClientIntentionPacket
                || packet instanceof ServerboundStatusRequestPacket
                || packet instanceof ServerboundPingRequestPacket) {
            return true;
        }
        if (packet instanceof ServerboundChatPacket
                || packet instanceof ServerboundChatCommandPacket
                || packet instanceof ServerboundChatCommandSignedPacket
                || packet instanceof ServerboundChatSessionUpdatePacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundDisguisedChatPacket) {
            return true;
        }
        if (packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundRespawnPacket
                || packet instanceof ClientboundLoginPacket) {
            flush(TransferOrigin.INCOMING);
            return true;
        }
        if (packet instanceof ClientboundSoundPacket sound
                && sound.getSound().value() == SoundEvents.PLAYER_HURT) {
            return true;
        }
        if (packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0.0F) {
            flush(TransferOrigin.INCOMING);
            return true;
        }
        return false;
    }

    private void flushSnapshot(BlinkPacketSnapshot snapshot) {
        ClientPacketListener listener = Setsuna.mc().getConnection();
        if (listener == null) {
            return;
        }
        if (snapshot.origin() == TransferOrigin.OUTGOING) {
            runReplaying(() -> PacketUtils.sendSilently(
                    snapshot.packet(), snapshot.listener(), snapshot.flush()));
        } else {
            runReplaying(() -> PacketUtils.dispatch(listener, snapshot.packet()));
        }
    }

    private void runReplaying(Runnable action) {
        int depth = replayDepth.get();
        replayDepth.set(depth + 1);
        try {
            action.run();
        } finally {
            if (depth == 0) {
                replayDepth.remove();
            } else {
                replayDepth.set(depth);
            }
        }
    }

    private static TransferOrigin toTransferOrigin(BlinkPacketEvent.Origin origin) {
        return origin == BlinkPacketEvent.Origin.INCOMING
                ? TransferOrigin.INCOMING : TransferOrigin.OUTGOING;
    }

    public enum Action {
        FLUSH(0),
        PASS(1),
        QUEUE(2);

        private final int priority;

        Action(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }
    }
}
