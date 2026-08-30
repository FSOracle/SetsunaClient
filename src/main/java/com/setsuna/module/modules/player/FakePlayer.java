package com.setsuna.module.modules.player;

import com.mojang.authlib.GameProfile;
import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FakePlayer extends Module {

    public static final FakePlayer INSTANCE = new FakePlayer();
    public static final String FAKE_PLAYER_NAME = "Fakeplayer1";

    private static final int FAKE_PLAYER_ID = -66_123_666;

    private final BooleanSetting copyInventory = add(new BooleanSetting("Copy Inventory", true));
    private final BooleanSetting record = add(new BooleanSetting("Record", false));
    private final BooleanSetting playback = add(new BooleanSetting("Playback", false));

    private final List<PlayerState> positions = new ArrayList<>();
    private RemotePlayer fakePlayer;
    private int movementTick;
    private int deathTicks;

    private FakePlayer() {
        super("Fake Player", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        if (noPlayer()) {
            setEnabled(false);
            return;
        }

        movementTick = 0;
        deathTicks = 0;
        if (!record.get()) {
            positions.clear();
        }

        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("setsuna-fake-player:" + FAKE_PLAYER_NAME)
                        .getBytes(StandardCharsets.UTF_8)),
                FAKE_PLAYER_NAME);
        fakePlayer = new RemotePlayer(mc.level, profile);
        fakePlayer.setId(FAKE_PLAYER_ID);
        fakePlayer.copyPosition(mc.player);
        fakePlayer.setYRot(mc.player.getYRot());
        fakePlayer.setXRot(mc.player.getXRot());
        fakePlayer.setYHeadRot(mc.player.getYHeadRot());
        fakePlayer.setHealth(mc.player.getHealth());
        fakePlayer.setAbsorptionAmount(mc.player.getAbsorptionAmount());

        if (copyInventory.get()) {
            fakePlayer.getInventory().replaceWith(mc.player.getInventory());
        }
        mc.level.addEntity(fakePlayer);
    }

    @Override
    protected void onDisable() {
        removeFakePlayer();
        positions.clear();
        movementTick = 0;
        deathTicks = 0;
    }

    @Listen
    private void onTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            removeFakePlayer();
            return;
        }

        if (record.get()) {
            positions.add(new PlayerState(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    mc.player.getYRot(), mc.player.getXRot()));
            return;
        }
        if (fakePlayer == null) {
            return;
        }

        if (playback.get() && !positions.isEmpty()) {
            if (movementTick >= positions.size()) {
                movementTick = 0;
            }
            PlayerState state = positions.get(movementTick++);
            fakePlayer.snapTo(state.x(), state.y(), state.z(), state.yaw(), state.pitch());
            fakePlayer.setYHeadRot(state.yaw());
        } else {
            movementTick = 0;
        }

        if (fakePlayer.isDeadOrDying()) {
            if (++deathTicks > 10) {
                setEnabled(false);
            }
        } else {
            deathTicks = 0;
        }
    }

    private void removeFakePlayer() {
        if (mc.level != null && fakePlayer != null) {
            mc.level.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakePlayer = null;
    }

    private record PlayerState(double x, double y, double z, float yaw, float pitch) {
    }
}
