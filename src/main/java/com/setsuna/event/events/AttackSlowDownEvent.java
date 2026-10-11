package com.setsuna.event.events;

import com.setsuna.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

public final class AttackSlowDownEvent extends CancellableEvent {

    private final AttackData attack;

    public AttackSlowDownEvent(Entity entity, float knockbackAmount) {
        attack = new AttackData(entity, knockbackAmount);
    }

    public Entity getEntity() {
        return attack.target();
    }

    public float getKnockbackAmount() {
        return attack.slowdown();
    }

    private record AttackData(Entity target, float slowdown) {
    }
}
