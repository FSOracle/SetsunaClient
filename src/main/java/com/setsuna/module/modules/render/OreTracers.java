package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.PlayerTickEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.client.ViewBobbingSuppressor;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.timer.TimerUtils;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Draws lines from the camera to nearby ores of selected types. The ore
 * positions are cached and rebuilt on a timer to avoid scanning the box every
 * frame.
 */
public final class OreTracers extends Module {

    public static final OreTracers INSTANCE = new OreTracers();

    private static final String SUPPRESSOR_KEY = "ore_tracers";

    public final DoubleSetting tracerWidth = add(new DoubleSetting("Tracer Width", 1.5, 0.5, 5.0, 0.5));
    public final IntSetting tracerRange = add(new IntSetting("Range", 16, 8, 64, 8));

    public final BooleanSetting diamond = add(new BooleanSetting("Diamond", true));
    public final ColorSetting diamondColor = add(new ColorSetting("Diamond Color", new Color(0, 255, 255, 220))
            .visibleWhen(diamond::get));
    public final BooleanSetting gold = add(new BooleanSetting("Gold", true));
    public final ColorSetting goldColor = add(new ColorSetting("Gold Color", new Color(255, 215, 0, 220))
            .visibleWhen(gold::get));
    public final BooleanSetting iron = add(new BooleanSetting("Iron", true));
    public final ColorSetting ironColor = add(new ColorSetting("Iron Color", new Color(213, 213, 213, 220))
            .visibleWhen(iron::get));
    public final BooleanSetting emerald = add(new BooleanSetting("Emerald", false));
    public final ColorSetting emeraldColor = add(new ColorSetting("Emerald Color", new Color(0, 255, 77, 220))
            .visibleWhen(emerald::get));
    public final BooleanSetting redstone = add(new BooleanSetting("Redstone", false));
    public final ColorSetting redstoneColor = add(new ColorSetting("Redstone Color", new Color(255, 0, 0, 220))
            .visibleWhen(redstone::get));
    public final BooleanSetting lapis = add(new BooleanSetting("Lapis", false));
    public final ColorSetting lapisColor = add(new ColorSetting("Lapis Color", new Color(38, 97, 156, 220))
            .visibleWhen(lapis::get));
    public final BooleanSetting coal = add(new BooleanSetting("Coal", false));
    public final ColorSetting coalColor = add(new ColorSetting("Coal Color", new Color(40, 40, 40, 220))
            .visibleWhen(coal::get));
    public final BooleanSetting netherite = add(new BooleanSetting("Netherite", false));
    public final ColorSetting netheriteColor = add(new ColorSetting("Netherite Color", new Color(255, 255, 255, 220))
            .visibleWhen(netherite::get));
    public final BooleanSetting quartz = add(new BooleanSetting("Quartz", false));
    public final ColorSetting quartzColor = add(new ColorSetting("Quartz Color", new Color(170, 170, 170, 220))
            .visibleWhen(quartz::get));

    private final TimerUtils cacheTimer = new TimerUtils();
    private final List<BlockPos> oreCache = new CopyOnWriteArrayList<>();

    private record OreTarget(BlockPos pos, Color color) {
    }

    private OreTracers() {
        super("Ore Tracers", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        ViewBobbingSuppressor.acquire(SUPPRESSOR_KEY);
        rebuildCache();
    }

    @Override
    protected void onDisable() {
        ViewBobbingSuppressor.release(SUPPRESSOR_KEY);
        oreCache.clear();
    }

    @Listen
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (noPlayer()) {
            return;
        }
        if (cacheTimer.every(500)) {
            rebuildCache();
        }
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer() || oreCache.isEmpty()) {
            return;
        }

        List<OreTarget> targets = new ArrayList<>();
        double rangeSq = tracerRange.get() * (double) tracerRange.get();
        BlockPos playerPos = mc.player.blockPosition();

        for (BlockPos pos : new ArrayList<>(oreCache)) {
            if (pos.distSqr(playerPos) > rangeSq) {
                continue;
            }
            Block block = mc.level.getBlockState(pos).getBlock();
            Color c = getTracerColor(block);
            if (c != null) {
                targets.add(new OreTarget(pos, c));
            }
        }

        if (!targets.isEmpty()) {
            drawTracers(event.getPoseStack(), targets);
        }
    }

    private void rebuildCache() {
        if (mc.level == null || mc.player == null) {
            return;
        }

        List<BlockPos> fresh = new ArrayList<>();
        int r = tracerRange.get();
        BlockPos center = mc.player.blockPosition();
        int minY = Math.max(mc.level.getMinY(), center.getY() - r);
        int maxY = Math.min(mc.level.getMaxY(), center.getY() + r);

        for (int x = center.getX() - r; x <= center.getX() + r; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = center.getZ() - r; z <= center.getZ() + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (getTracerColor(mc.level.getBlockState(pos).getBlock()) != null) {
                        fresh.add(pos);
                    }
                }
            }
        }

        oreCache.clear();
        oreCache.addAll(fresh);
    }

    private void drawTracers(PoseStack stack, List<OreTarget> targets) {
        var camera = mc.getEntityRenderDispatcher().camera;
        Vec3 cameraPos = camera.position();
        PoseStack.Pose entry = stack.last();
        Matrix4f matrix = entry.pose();
        float thickness = tracerWidth.get().floatValue();

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

        var forward = camera.forwardVector();
        float ox = forward.x() * 0.05f;
        float oy = forward.y() * 0.05f;
        float oz = forward.z() * 0.05f;

        for (OreTarget target : targets) {
            BlockPos pos = target.pos();
            Color color = target.color();
            float ex = (float) (pos.getX() + 0.5 - cameraPos.x);
            float ey = (float) (pos.getY() + 0.5 - cameraPos.y);
            float ez = (float) (pos.getZ() + 0.5 - cameraPos.z);

            Vector3f normal = new Vector3f(ex - ox, ey - oy, ez - oz).normalize();

            buffer.addVertex(matrix, ox, oy, oz)
                    .setColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                    .setNormal(entry, normal.x, normal.y, normal.z)
                    .setLineWidth(thickness);
            buffer.addVertex(matrix, ex, ey, ez)
                    .setColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                    .setNormal(entry, normal.x, normal.y, normal.z)
                    .setLineWidth(thickness);
        }

        MeshData meshData = buffer.build();
        if (meshData != null) {
            Render3DUtils.LINES.draw(meshData);
        }
    }

    private Color getTracerColor(Block block) {
        if (diamond.get() && isDiamondOre(block)) return diamondColor.get();
        if (gold.get() && isGoldOre(block)) return goldColor.get();
        if (iron.get() && isIronOre(block)) return ironColor.get();
        if (emerald.get() && isEmeraldOre(block)) return emeraldColor.get();
        if (redstone.get() && isRedstoneOre(block)) return redstoneColor.get();
        if (lapis.get() && isLapisOre(block)) return lapisColor.get();
        if (coal.get() && isCoalOre(block)) return coalColor.get();
        if (netherite.get() && block == Blocks.ANCIENT_DEBRIS) return netheriteColor.get();
        if (quartz.get() && block == Blocks.NETHER_QUARTZ_ORE) return quartzColor.get();
        return null;
    }

    private boolean isDiamondOre(Block b) {
        return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE;
    }

    private boolean isGoldOre(Block b) {
        return b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE;
    }

    private boolean isIronOre(Block b) {
        return b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE;
    }

    private boolean isEmeraldOre(Block b) {
        return b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE;
    }

    private boolean isRedstoneOre(Block b) {
        return b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE;
    }

    private boolean isLapisOre(Block b) {
        return b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE;
    }

    private boolean isCoalOre(Block b) {
        return b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE;
    }
}
