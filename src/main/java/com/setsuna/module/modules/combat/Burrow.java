package com.setsuna.module.modules.combat;

import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.network.PacketUtils;
import com.setsuna.util.player.FindItemResult;
import com.setsuna.util.world.BlockPlaceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.Items;

/** Places a blast-resistant block inside the player after a packet jump. */
public final class Burrow extends Module {

    public static final Burrow INSTANCE = new Burrow();

    private enum Mode {
        NCP,
        Strict
    }

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.NCP));
    private final BooleanSetting enderChest = add(new BooleanSetting("Ender Chest", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", false));
    private final BooleanSetting autoToggle = add(new BooleanSetting("Toggle", true));
    private final IntSetting tries = add(new IntSetting("Tries", 2, 1, 5, 1));

    private Burrow() {
        super("Burrow", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        if (noPlayer() || mc.gameMode == null || mc.getConnection() == null) {
            disableIfRequested();
            return;
        }

        BlockPos burrowPos = mc.player.blockPosition();
        FindItemResult block = enderChest.get()
                ? BlockPlaceHelper.findHotbar(Items.OBSIDIAN, Items.ENDER_CHEST)
                : BlockPlaceHelper.findHotbar(Items.OBSIDIAN);
        if (!block.found()) {
            disableIfRequested();
            return;
        }

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        sendJumpPackets(x, y, z);

        double lift = mode.is(Mode.NCP) ? 1.16610926093821D : 1.25D;
        mc.player.setPos(x, y + lift, z);

        boolean placed = false;
        try {
            for (int attempt = 0; attempt < tries.get(); attempt++) {
                BlockPlaceHelper.PlaceInfo info = BlockPlaceHelper.findPlaceInfo(
                        burrowPos, !rotate.get());
                if (BlockPlaceHelper.place(info, block, true, true, false)) {
                    placed = true;
                    break;
                }
            }
        } finally {
            PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Pos(
                    x, y, z, false, mc.player.horizontalCollision));
            mc.player.setPos(x, y, z);
        }

        if (placed || autoToggle.get()) {
            disableIfRequested();
        }
    }

    private void sendJumpPackets(double x, double y, double z) {
        if (mode.is(Mode.NCP)) {
            sendPosition(x, y + 0.4199999868869781D, z);
            sendPosition(x, y + 0.7531999805212017D, z);
            sendPosition(x, y + 1.00133597911214D, z);
            sendPosition(x, y + 1.16610926093821D, z);
        } else {
            sendPosition(x, y + 0.42D, z);
            sendPosition(x, y + 0.75D, z);
            sendPosition(x, y + 1.01D, z);
            sendPosition(x, y + 1.25D, z);
        }
    }

    private void sendPosition(double x, double y, double z) {
        PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Pos(
                x, y, z, false, mc.player.horizontalCollision));
    }

    private void disableIfRequested() {
        if (autoToggle.get()) {
            setEnabled(false);
        }
    }
}
