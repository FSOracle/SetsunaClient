package com.setsuna.module.modules.movement.velocity;

import com.setsuna.event.events.PacketEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Queue;

/** OpenOpal's Heypixel3 velocity buffer adapted to the Mojmap packet API. */
public final class Heypixel3Velocity {

    private final Minecraft mc;
    private final Queue<Packet<? super ClientPacketListener>> delayedPackets = new ArrayDeque<>();

    private int delayTicks;
    private int attackCount;
    private boolean shouldFlush;
    private Vec3 pendingVelocity;
    private int hurtWindowTicks;

    public Heypixel3Velocity(Minecraft mc) {
        this.mc = mc;
        reset();
    }

    public void onReceive(PacketEvent.Receive event, int maxDelayTicks) {
        if (mc.player == null || mc.level == null) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundDamageEventPacket damage
                && damage.entityId() == mc.player.getId()) {
            hurtWindowTicks = 3;
            return;
        }

        if (packet instanceof ClientboundSetEntityMotionPacket velocity
                && velocity.id() == mc.player.getId()) {
            if (hurtWindowTicks <= 0) return;

            if (delayTicks > 0 || shouldFlush) {
                if (delayTicks > 0 && canTriggerAttackCountNow()) {
                    attackCount = 1;
                    attackCount--;
                    delayTicks = 0;
                    pendingVelocity = null;
                    shouldFlush = true;
                }
                hurtWindowTicks = 0;
                event.setCancelled(true);
                return;
            }

            if (attackCount > 0 && canTriggerAttackCountNow()) {
                attackCount--;
                hurtWindowTicks = 0;
                event.setCancelled(true);
                return;
            }

            delayTicks = maxDelayTicks;
            shouldFlush = false;
            delayedPackets.clear();
            pendingVelocity = velocity.movement();
            hurtWindowTicks = 0;
            event.setCancelled(true);
            return;
        }

        if (delayTicks <= 0) return;
        if (packet instanceof ClientboundPlayerPositionPacket) {
            delayTicks = 0;
            shouldFlush = true;
            return;
        }

        delayedPackets.add(asClientPacket(packet));
        event.setCancelled(true);
    }

    public void tick() {
        if (mc.player == null) return;

        if (delayTicks > 0) {
            delayTicks--;
            if (delayTicks == 0) shouldFlush = true;
        }
        if (hurtWindowTicks > 0) hurtWindowTicks--;

        if (shouldFlush) {
            flushDelayedPackets();
            shouldFlush = false;
        }
    }

    public void reset() {
        delayedPackets.clear();
        delayTicks = 0;
        attackCount = 1;
        shouldFlush = false;
        pendingVelocity = null;
        hurtWindowTicks = 0;
    }

    public void disable() {
        flushDelayedPackets();
        shouldFlush = false;
        attackCount = 1;
        pendingVelocity = null;
        hurtWindowTicks = 0;
    }

    public String getSuffix(int maxDelayTicks) {
        return delayTicks > 0
                ? "Buffer " + (maxDelayTicks - delayTicks) + "Ticks"
                : "Buffer";
    }

    public boolean isDelaying() {
        return delayTicks > 0;
    }

    public boolean hasQueuedPackets() {
        return !delayedPackets.isEmpty() || shouldFlush;
    }

    private void flushDelayedPackets() {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            delayedPackets.clear();
            delayTicks = 0;
            shouldFlush = false;
            pendingVelocity = null;
            hurtWindowTicks = 0;
            return;
        }

        if (pendingVelocity != null && mc.player != null) {
            mc.player.lerpMotion(pendingVelocity);
            pendingVelocity = null;
        }

        while (!delayedPackets.isEmpty()) {
            Packet<? super ClientPacketListener> packet = delayedPackets.poll();
            if (packet != null) packet.handle(connection);
        }
        delayTicks = 0;
    }

    private boolean canTriggerAttackCountNow() {
        return canTriggerAttackCountNow(
                mc.player != null && mc.player.isSprinting(),
                mc.player != null && mc.player.isShiftKeyDown(),
                mc.player == null ? 0.0F : mc.player.zza,
                mc.player != null && mc.player.isUsingItem()
        );
    }

    static boolean canTriggerAttackCountNow(
            boolean sprinting,
            boolean sneaking,
            float forwardSpeed,
            boolean usingItem) {
        return sprinting && !sneaking && forwardSpeed > 0.0F && !usingItem;
    }

    @SuppressWarnings("unchecked")
    private static Packet<? super ClientPacketListener> asClientPacket(Packet<?> packet) {
        return (Packet<? super ClientPacketListener>) packet;
    }
}
