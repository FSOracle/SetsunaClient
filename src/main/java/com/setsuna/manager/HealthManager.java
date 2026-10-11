package com.setsuna.manager;

import com.setsuna.Setsuna;
import com.setsuna.event.EventBus;
import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.RespawnEvent;
import com.setsuna.util.player.HeypixelHealthSource;
import com.setsuna.util.player.HealthDetectionUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Compatibility facade for the shared health source used by combat modules. */
public final class HealthManager {

    public static final HealthManager INSTANCE = new HealthManager();

    private final HeypixelHealthSource heypixelHealthSource = new HeypixelHealthSource();

    private HealthManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    @Listen(priority = Priority.HIGHEST)
    private void onPacket(PacketEvent.Receive event) {
        String localPlayerName = Setsuna.mc().player == null
                ? null : Setsuna.mc().player.getGameProfile().name();
        heypixelHealthSource.accept(event.getPacket(), localPlayerName);
    }

    @Listen
    private void onRespawn(RespawnEvent event) {
        heypixelHealthSource.clear();
    }

    public float getHealth(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            return HealthDetectionUtils.getHealth(livingEntity);
        }
        return 0f;
    }

    public Float getHeypixelHealth(Player player) {
        return heypixelHealthSource.getHealth(player);
    }

    public void clearHeypixelHealth() {
        heypixelHealthSource.clear();
    }
}
