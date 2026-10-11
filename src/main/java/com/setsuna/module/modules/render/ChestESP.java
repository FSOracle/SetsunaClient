package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.Priority;
import com.setsuna.event.events.PacketEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.render.Render3DUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Renders unopened normal and ender chests with independent visual colours. */
public final class ChestESP extends Module {

    public static final ChestESP INSTANCE = new ChestESP();

    private static final long RESCAN_INTERVAL_MS = 400L;
    private static final long OPEN_CONFIRM_TIMEOUT_MS = 3_000L;

    public final BooleanSetting chests = add(new BooleanSetting("Chests", true));
    public final BooleanSetting enderChests = add(new BooleanSetting("Ender Chests", true));
    public final BooleanSetting minecartChests = add(new BooleanSetting("Minecart Chests", true)
            .visibleWhen(chests::get));
    public final DoubleSetting range = add(new DoubleSetting("Range", 64.0, 1.0, 256.0, 1.0));
    public final ColorSetting chestColor = add(new ColorSetting("Chest Color",
            new Color(160, 210, 255, 30)).visibleWhen(chests::get));
    public final ColorSetting enderChestColor = add(new ColorSetting("Ender Chest Color",
            new Color(184, 112, 255, 42)).visibleWhen(enderChests::get));
    public final BooleanSetting outline = add(new BooleanSetting("Outline", true)
            .visibleWhen(() -> chests.get() || enderChests.get()));
    public final ColorSetting outlineColor = add(new ColorSetting("Outline Color",
            new Color(160, 210, 255, 200)).visibleWhen(outline::get));
    public final DoubleSetting outlineThickness = add(new DoubleSetting("Outline Thickness",
            1.5, 0.5, 5.0, 0.5).visibleWhen(outline::get));

    private final List<ContainerBox> cachedContainers = new ArrayList<>();
    private final Set<BlockPos> openedContainers = new HashSet<>();
    private final Set<UUID> openedMinecartChests = new HashSet<>();
    private long lastScanTime;
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkZ = Integer.MIN_VALUE;
    private ClientLevel trackedLevel;
    private BlockPos pendingContainer;
    private UUID pendingMinecartChest;
    private long pendingOpenTime;

    private record ContainerBox(AABB box, boolean ender) {
    }

    private ChestESP() {
        super("Chest ESP", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        ensureWorld();
        lastScanTime = 0L;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        cachedContainers.clear();
    }

    @Override
    protected void onDisable() {
        cachedContainers.clear();
        clearPending();
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer() || (!chests.get() && !enderChests.get())) return;
        ensureWorld();

        double maxRange = range.get();
        double maxRangeSq = maxRange * maxRange;
        long now = System.currentTimeMillis();
        ChunkPos playerChunk = mc.player.chunkPosition();
        if (playerChunk.x() != lastChunkX || playerChunk.z() != lastChunkZ
                || now - lastScanTime >= RESCAN_INTERVAL_MS) {
            rebuildCache(maxRangeSq, maxRange);
            lastScanTime = now;
            lastChunkX = playerChunk.x();
            lastChunkZ = playerChunk.z();
        }

        boolean drawOutline = outline.get();
        Color lineColor = outlineColor.get();
        float thickness = outlineThickness.get().floatValue();
        for (ContainerBox container : cachedContainers) {
            renderContainer(event, container.box(), container.ender() ? enderChestColor.get() : chestColor.get(),
                    drawOutline, lineColor, thickness);
        }

        if (!minecartChests.get()) return;
        Color fillColor = chestColor.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof MinecartChest minecart)
                    || openedMinecartChests.contains(minecart.getUUID())
                    || entity.distanceToSqr(mc.player) > maxRangeSq) continue;
            renderContainer(event, minecart.getBoundingBox(), fillColor,
                    drawOutline, lineColor, thickness);
        }
    }

    private void renderContainer(Render3DEvent event, AABB box, Color fillColor,
                                 boolean drawOutline, Color lineColor, float thickness) {
        Render3DUtils.drawFilledBox(box, fillColor);
        if (drawOutline) {
            Render3DUtils.drawOutlineBox(event.getPoseStack(), box, lineColor, thickness);
        }
    }

    @Listen(priority = Priority.LOWEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (noPlayer()) {
            clearPending();
            return;
        }
        ensureWorld();
        if (event.getPacket() instanceof ServerboundUseItemOnPacket packet) {
            BlockPos pos = packet.getHitResult().getBlockPos();
            if (isTrackedState(mc.level.getBlockState(pos))) {
                pendingContainer = pos.immutable();
                pendingMinecartChest = null;
                pendingOpenTime = System.currentTimeMillis();
            } else {
                clearPending();
            }
            return;
        }
        if (event.getPacket() instanceof ServerboundInteractPacket packet) {
            Entity entity = mc.level.getEntity(packet.entityId());
            if (entity instanceof MinecartChest minecart) {
                pendingContainer = null;
                pendingMinecartChest = minecart.getUUID();
                pendingOpenTime = System.currentTimeMillis();
            } else {
                clearPending();
            }
        }
    }

    public void confirmContainerOpened(ClientboundOpenScreenPacket packet) {
        if (!isEnabled() || noPlayer() || !isContainerMenu(packet.getType())) {
            clearPending();
            return;
        }
        ensureWorld();
        if (pendingOpenTime == 0L
                || System.currentTimeMillis() - pendingOpenTime > OPEN_CONFIRM_TIMEOUT_MS) {
            clearPending();
            return;
        }
        if (pendingContainer != null) {
            markOpened(pendingContainer);
        } else if (pendingMinecartChest != null) {
            openedMinecartChests.add(pendingMinecartChest);
        }
        clearPending();
        invalidateScan();
    }

    public void handleContainerBlockEvent(ClientboundBlockEventPacket packet) {
        if (noPlayer() || packet.getB0() != 1 || packet.getB1() <= 0) return;
        ensureWorld();
        if (!isTrackedState(mc.level.getBlockState(packet.getPos()))) return;
        markOpened(packet.getPos());
        invalidateScan();
    }

    private void rebuildCache(double maxRangeSq, double maxRange) {
        cachedContainers.clear();
        int chunkRadius = (int) Math.ceil(maxRange / 16.0) + 1;
        BlockPos playerPos = mc.player.blockPosition();
        ChunkPos playerChunk = mc.player.chunkPosition();
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                var chunk = mc.level.getChunk(playerChunk.x() + x, playerChunk.z() + z);
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockState state = entity.getBlockState();
                    boolean ender = state.is(Blocks.ENDER_CHEST);
                    boolean normal = state.getBlock() instanceof ChestBlock;
                    if ((!ender && !normal) || openedContainers.contains(entity.getBlockPos())
                            || ender && !enderChests.get() || normal && !chests.get()) continue;
                    BlockPos pos = entity.getBlockPos();
                    if (pos.distSqr(playerPos) <= maxRangeSq) {
                        cachedContainers.add(new ContainerBox(getBlockAABB(pos), ender));
                    }
                }
            }
        }
    }

    private void markOpened(BlockPos pos) {
        openedContainers.add(pos.immutable());
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            openedContainers.add(ChestBlock.getConnectedBlockPos(pos, state).immutable());
        }
    }

    private void ensureWorld() {
        if (trackedLevel == mc.level) return;
        trackedLevel = mc.level;
        openedContainers.clear();
        openedMinecartChests.clear();
        clearPending();
        cachedContainers.clear();
    }

    private void invalidateScan() {
        lastScanTime = 0L;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
    }

    private void clearPending() {
        pendingContainer = null;
        pendingMinecartChest = null;
        pendingOpenTime = 0L;
    }

    private static boolean isTrackedState(BlockState state) {
        return state.is(Blocks.ENDER_CHEST) || state.getBlock() instanceof ChestBlock;
    }

    private static boolean isContainerMenu(MenuType<?> type) {
        return type == MenuType.GENERIC_9x1 || type == MenuType.GENERIC_9x2
                || type == MenuType.GENERIC_9x3 || type == MenuType.GENERIC_9x4
                || type == MenuType.GENERIC_9x5 || type == MenuType.GENERIC_9x6
                || type == MenuType.GENERIC_3x3 || type == MenuType.HOPPER
                || type == MenuType.SHULKER_BOX;
    }

    private AABB getBlockAABB(BlockPos blockPos) {
        BlockState state = mc.level.getBlockState(blockPos);
        var shape = state.getShape(mc.level, blockPos);
        return shape.isEmpty() ? new AABB(blockPos) : shape.bounds().move(blockPos);
    }
}
