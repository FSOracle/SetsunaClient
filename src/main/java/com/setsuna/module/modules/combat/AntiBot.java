package com.setsuna.module.modules.combat;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.modules.combat.antibot.AntiBotEngine;
import com.setsuna.module.modules.combat.antibot.AntiBotPlayerListEntry;
import com.setsuna.module.modules.combat.antibot.CombatGameMode;
import com.setsuna.module.modules.combat.antibot.CombatPlayerIdentity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.HashSet;
import java.util.List;

/** Clap AntiBot adapted to Mojmap packet types. */
public final class AntiBot extends Module {
    public static final AntiBot INSTANCE = new AntiBot();

    private final AntiBotEngine engine = new AntiBotEngine();

    private AntiBot() {
        super("AntiBot", Category.COMBAT);
    }

    public boolean isBot(Player player) {
        if (!isEnabled() || player == null || mc.getConnection() == null) {
            return false;
        }
        return engine.isBot(
                new CombatPlayerIdentity(player.getId(), player.getUUID()),
                new HashSet<>(mc.getConnection().getOnlinePlayerIds()));
    }

    @Listen
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerInfoUpdatePacket update
                && update.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
            List<AntiBotPlayerListEntry> entries = update.entries().stream()
                    .map(AntiBot::toEntry)
                    .toList();
            engine.handlePlayerListAdd(entries, System.currentTimeMillis());
        } else if (packet instanceof ClientboundAddEntityPacket spawn
                && spawn.getType() == EntityType.PLAYER) {
            engine.handlePlayerSpawn(spawn.getUUID(), spawn.getId());
        } else if (packet instanceof ClientboundRemoveEntitiesPacket destroyed) {
            engine.handleEntitiesDestroyed(destroyed.getEntityIds().intStream().boxed().toList());
        }
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        engine.tick(
                System.currentTimeMillis(),
                mc.player != null && mc.level != null && mc.getConnection() != null,
                mc.player == null ? 0 : mc.player.tickCount);
    }

    @Override
    protected void onEnable() {
        engine.clearTracking();
    }

    @Override
    protected void onDisable() {
        engine.clearTracking();
    }

    private static AntiBotPlayerListEntry toEntry(ClientboundPlayerInfoUpdatePacket.Entry entry) {
        Component displayName = entry.displayName();
        return new AntiBotPlayerListEntry(
                entry.profileId(),
                displayName != null,
                displayName == null ? 0 : displayName.getSiblings().size(),
                toGameMode(entry.gameMode()));
    }

    private static CombatGameMode toGameMode(GameType gameType) {
        if (gameType == null) return null;
        if (gameType == GameType.CREATIVE) return CombatGameMode.CREATIVE;
        if (gameType == GameType.ADVENTURE) return CombatGameMode.ADVENTURE;
        if (gameType == GameType.SPECTATOR) return CombatGameMode.SPECTATOR;
        return CombatGameMode.SURVIVAL;
    }
}
