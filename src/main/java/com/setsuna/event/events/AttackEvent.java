package com.setsuna.event.events;

import com.setsuna.event.Event;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Fired immediately before the client sends an attack to the server. */
public final class AttackEvent extends Event {

    private final Player attacker;
    private final Entity target;

    public AttackEvent(Player attacker, Entity target) {
        this.attacker = attacker;
        this.target = target;
    }

    public Player getAttacker() {
        return attacker;
    }

    public Entity getTarget() {
        return target;
    }
}
