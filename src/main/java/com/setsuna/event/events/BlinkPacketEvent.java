package com.setsuna.event.events;

import com.setsuna.event.Event;
import net.minecraft.network.protocol.Packet;

public final class BlinkPacketEvent extends Event {
    public enum Origin { INCOMING, OUTGOING }
    public enum Action { FLUSH(0), PASS(1), QUEUE(2); private final int priority; Action(int p) { priority = p; } }
    private final Packet<?> packet;
    private final Origin origin;
    private Action action = Action.FLUSH;
    public BlinkPacketEvent(Packet<?> packet, Origin origin) { this.packet = packet; this.origin = origin; }
    public Packet<?> packet() { return packet; }
    public Origin origin() { return origin; }
    public Action action() { return action; }
    public void setAction(Action value) { if (value.priority > action.priority) action = value; }
}
