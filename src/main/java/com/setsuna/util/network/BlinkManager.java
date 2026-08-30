package com.setsuna.util.network;

import com.setsuna.Setsuna;
import com.setsuna.event.EventBus;
import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.CancellableEvent;
import com.setsuna.event.events.BlinkPacketEvent;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.TickEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

/** Shared LiquidBounce-style queue and replay system. */
public final class BlinkManager {
    public static final BlinkManager INSTANCE = new BlinkManager();
    public record Snapshot(Packet<?> packet, BlinkPacketEvent.Origin origin, long timestamp) {}
    private final ConcurrentLinkedQueue<Snapshot> queue = new ConcurrentLinkedQueue<>();
    private BlinkManager() {}
    public List<Snapshot> snapshots() { return List.copyOf(queue); }
    public boolean has(BlinkPacketEvent.Origin origin) { return queue.stream().anyMatch(s -> s.origin == origin); }
    public boolean isAboveTime(long delay) { Snapshot s = queue.peek(); return s != null && System.currentTimeMillis() - s.timestamp >= delay; }
    public Vec3 firstOutgoingPosition() {
        if (Setsuna.mc().player == null) return null;
        for (Snapshot s : queue) if (s.origin == BlinkPacketEvent.Origin.OUTGOING && s.packet instanceof ServerboundMovePlayerPacket p && p.hasPosition())
            return new Vec3(p.getX(Setsuna.mc().player.getX()), p.getY(Setsuna.mc().player.getY()), p.getZ(Setsuna.mc().player.getZ()));
        return null;
    }
    @Listen(priority = Priority.LOWEST) private void send(PacketEvent.Send e) { process(e.getPacket(), BlinkPacketEvent.Origin.OUTGOING, e); }
    @Listen(priority = Priority.LOWEST) private void receive(PacketEvent.Receive e) { process(e.getPacket(), BlinkPacketEvent.Origin.INCOMING, e); }
    private void process(Packet<?> packet, BlinkPacketEvent.Origin origin, CancellableEvent event) {
        BlinkPacketEvent.Action action = decide(packet, origin);
        if (action == BlinkPacketEvent.Action.FLUSH) { flush(origin); return; }
        if (action == BlinkPacketEvent.Action.PASS || alwaysPass(packet)) return;
        if (flushPacket(packet)) { flush(origin); return; }
        event.setCancelled(true); queue.add(new Snapshot(packet, origin, System.currentTimeMillis()));
    }
    @Listen(priority = Priority.LOWEST) private void tick(TickEvent.Pre e) {
        if (Setsuna.mc().getConnection() == null) { queue.clear(); return; }
        if (decide(null, BlinkPacketEvent.Origin.INCOMING) == BlinkPacketEvent.Action.FLUSH) flush(BlinkPacketEvent.Origin.INCOMING);
        if (decide(null, BlinkPacketEvent.Origin.OUTGOING) == BlinkPacketEvent.Action.FLUSH) flush(BlinkPacketEvent.Origin.OUTGOING);
    }
    private BlinkPacketEvent.Action decide(Packet<?> p, BlinkPacketEvent.Origin o) { return EventBus.INSTANCE.post(new BlinkPacketEvent(p, o)).action(); }
    public void flush(BlinkPacketEvent.Origin origin) { flush(s -> s.origin == origin); }
    public void flush(Predicate<Snapshot> predicate) { for (Snapshot s : new ArrayList<>(queue)) if (predicate.test(s) && queue.remove(s)) replay(s); }
    public void remove(BlinkPacketEvent.Origin origin) { queue.removeIf(s -> s.origin == origin); }
    @SuppressWarnings("unchecked") private void replay(Snapshot s) {
        if (s.origin == BlinkPacketEvent.Origin.OUTGOING) PacketUtils.sendSilently(s.packet);
        else if (Setsuna.mc().getConnection() != null) ((Packet<ClientGamePacketListener>) s.packet).handle(Setsuna.mc().getConnection());
    }
    private static boolean alwaysPass(Packet<?> p) { String n = p.getClass().getSimpleName(); return n.contains("Chat") || n.contains("KeepAlive") || n.contains("Pong") || p instanceof ClientboundSoundPacket x && x.getSound().value() == SoundEvents.PLAYER_HURT; }
    private static boolean flushPacket(Packet<?> p) { return p instanceof ClientboundPlayerPositionPacket || p instanceof ClientboundDisconnectPacket || p instanceof ClientboundRespawnPacket || p instanceof ClientboundLoginPacket || p instanceof ClientboundSetHealthPacket h && h.getHealth() <= 0; }
}
