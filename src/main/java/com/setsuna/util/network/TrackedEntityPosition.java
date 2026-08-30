package com.setsuna.util.network;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
public final class TrackedEntityPosition {
    private final VecDeltaCodec codec = new VecDeltaCodec();
    public TrackedEntityPosition() { codec.setBase(Vec3.ZERO); }
    public Vec3 base() { return codec.getBase(); }
    public void setBase(Vec3 v) { codec.setBase(v); }
    public void setBaseFrom(Entity e) { codec.setBase(e.getPositionCodec().getBase()); }
    public Vec3 handle(Packet<?> p, ClientLevel level, Entity target) {
        Vec3 v;
        if (p instanceof ClientboundMoveEntityPacket x && x.getEntity(level) == target) v = codec.decode(x.getXa(), x.getYa(), x.getZa());
        else if (p instanceof ClientboundTeleportEntityPacket x && x.id() == target.getId()) v = x.change().position();
        else if (p instanceof ClientboundEntityPositionSyncPacket x && x.id() == target.getId()) v = x.values().position();
        else return null;
        codec.setBase(v); return v;
    }
}
