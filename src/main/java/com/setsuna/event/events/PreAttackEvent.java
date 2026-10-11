package com.setsuna.event.events;

import com.setsuna.event.Event;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Fired immediately before the client sends an entity attack. */
public final class PreAttackEvent extends Event {

    private final Player attacker;
    private final Entity target;

    public PreAttackEvent(Player attacker, Entity target) {
        this.attacker = attacker;
        this.target = target;
    }

    public Player attacker() {
        return attacker;
    }

    public Entity target() {
        return target;
    }
}
