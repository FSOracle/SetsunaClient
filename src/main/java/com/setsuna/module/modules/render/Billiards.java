package com.setsuna.module.modules.render;

import com.setsuna.event.Listen;
import com.setsuna.event.events.Render2DEvent;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.TickEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.render.SkijaUi;
import com.setsuna.setting.settings.ButtonSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.util.client.KeybindUtils;
import com.setsuna.util.render.Render3DUtils;
import com.setsuna.util.render.WorldToScreen;
import io.github.humbleui.skija.Canvas;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector3f;

import java.awt.Color;

/** Render-only billiards. The Minecraft world is never changed by this module. */
public final class Billiards extends Module {

    public static final Billiards INSTANCE = new Billiards();

    private static final double TABLE_WIDTH = 10.0D;
    private static final double TABLE_LENGTH = 5.5D;
    private static final double BALL_RADIUS = 0.115D;
    private static final double POCKET_RADIUS = 0.25D;
    private static final double TABLE_Y = 0.04D;
    private static final double RAIL_HEIGHT = 0.25D;
    private static final double RAIL_WIDTH = 0.22D;
    private static final double FRICTION = 0.96D;
    private static final double DEFAULT_SHOT_STRENGTH = 1.15D;
    private static final double BOUNCE = 0.82D;
    private static final int CHARGE_TICKS = 40;

    public final KeybindSetting shootKey = add(new KeybindSetting("Shoot Key", GLFW.GLFW_KEY_G));
    public final ButtonSetting resetTable = add(new ButtonSetting("Reset Table", this::resetTable));
    public final ColorSetting clothColor = add(new ColorSetting("Table Color", new Color(28, 104, 76, 210)));
    public final ColorSetting railColor = add(new ColorSetting("Rail Color", new Color(92, 50, 28, 255)));
    public final ColorSetting cueColor = add(new ColorSetting("Cue Ball Color", new Color(245, 245, 235, 255)));

    private final double[] x = new double[16];
    private final double[] z = new double[16];
    private final double[] previousX = new double[16];
    private final double[] previousZ = new double[16];
    private final double[] vx = new double[16];
    private final double[] vz = new double[16];
    private final boolean[] pocketed = new boolean[16];

    private double originX;
    private double originY;
    private double originZ;
    private int charge;
    private int chargeDirection = 1;
    private boolean charging;
    private boolean shotCuePocket;
    private boolean shotInProgress;
    private PoseStackHolder pose;

    private Billiards() {
        super("Billiards", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        if (noPlayer()) return;
        if (Football.INSTANCE.isEnabled()) Football.INSTANCE.setEnabled(false);
        if (Basketball.INSTANCE.isEnabled()) Basketball.INSTANCE.setEnabled(false);
        originX = mc.player.getX();
        originY = mc.player.getY() + 0.05D;
        originZ = mc.player.getZ();
        resetTableState();
    }

    @Override
    protected void onDisable() {
        charging = false;
        charge = 0;
        chargeDirection = 1;
    }

    @Listen
    private void onTick(TickEvent.Post event) {
        if (noPlayer()) return;
        if (allStopped()) {
            if (shotInProgress) {
                resolveShot();
            }
            updateCharge();
        } else {
            simulate();
        }
    }

    private void updateCharge() {
        boolean held = mc.screen == null && shootKey.isBound() && KeybindUtils.isPressed(shootKey.get());
        boolean canShoot = cueAvailable();
        if (held && canShoot) {
            charging = true;
            charge += chargeDirection;
            if (charge >= CHARGE_TICKS) {
                charge = CHARGE_TICKS;
                chargeDirection = -1;
            } else if (charge <= 0) {
                charge = 0;
                chargeDirection = 1;
            }
        } else if (charging) {
            if (charge > 0 && canShoot) {
                double power = Math.max(0.08D, charge / (double) CHARGE_TICKS) * DEFAULT_SHOT_STRENGTH;
                strike(horizontalLook(), power);
            }
            charging = false;
            charge = 0;
            chargeDirection = 1;
        }
    }

    private void strike(Vec3 direction, double power) {
        if (!cueAvailable() || !allStopped()) return;
        vx[0] = direction.x * power;
        vz[0] = direction.z * power;
        previousX[0] = x[0];
        previousZ[0] = z[0];
        shotCuePocket = false;
        shotInProgress = true;
    }

    private void simulate() {
        double maximumSpeed = 0.0D;
        for (int i = 0; i < 16; i++) {
            if (pocketed[i]) continue;
            previousX[i] = x[i];
            previousZ[i] = z[i];
            maximumSpeed = Math.max(maximumSpeed, Math.hypot(vx[i], vz[i]));
        }

        int substeps = Mth.clamp((int) Math.ceil(maximumSpeed / (BALL_RADIUS * 0.65D)), 1, 24);
        double substepFriction = Math.pow(FRICTION, 1.0D / substeps);
        for (int step = 0; step < substeps; step++) {
            for (int i = 0; i < 16; i++) {
                if (pocketed[i]) continue;
                x[i] += vx[i] / substeps;
                z[i] += vz[i] / substeps;
                vx[i] *= substepFriction;
                vz[i] *= substepFriction;
                resolveRailCollision(i);
            }
            collideBalls();
            detectPockets();
        }

        for (int i = 0; i < 16; i++) {
            if (pocketed[i]) continue;
            if (Math.abs(vx[i]) < 0.00035D) vx[i] = 0.0D;
            if (Math.abs(vz[i]) < 0.00035D) vz[i] = 0.0D;
        }
    }

    private void resolveRailCollision(int ball) {
        double limitX = TABLE_WIDTH * 0.5D - BALL_RADIUS;
        double limitZ = TABLE_LENGTH * 0.5D - BALL_RADIUS;
        if (x[ball] < -limitX || x[ball] > limitX) {
            x[ball] = Mth.clamp(x[ball], -limitX, limitX);
            vx[ball] = -vx[ball] * BOUNCE;
        }
        if (z[ball] < -limitZ || z[ball] > limitZ) {
            z[ball] = Mth.clamp(z[ball], -limitZ, limitZ);
            vz[ball] = -vz[ball] * BOUNCE;
        }
    }

    private void collideBalls() {
        double diameter = BALL_RADIUS * 2.0D;
        for (int i = 0; i < 16; i++) {
            if (pocketed[i]) continue;
            for (int j = i + 1; j < 16; j++) {
                if (pocketed[j]) continue;
                double dx = x[j] - x[i];
                double dz = z[j] - z[i];
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared <= 1.0E-8D || distanceSquared > diameter * diameter) continue;
                double distance = Math.sqrt(distanceSquared);
                double nx = dx / distance;
                double nz = dz / distance;
                double relative = (vx[j] - vx[i]) * nx + (vz[j] - vz[i]) * nz;
                if (relative >= 0.0D) continue;
                vx[i] += relative * nx;
                vz[i] += relative * nz;
                vx[j] -= relative * nx;
                vz[j] -= relative * nz;
                double correction = (diameter - distance) * 0.51D;
                x[i] -= nx * correction;
                z[i] -= nz * correction;
                x[j] += nx * correction;
                z[j] += nz * correction;
            }
        }
    }

    private void detectPockets() {
        double halfX = TABLE_WIDTH * 0.5D;
        double halfZ = TABLE_LENGTH * 0.5D;
        for (int i = 0; i < 16; i++) {
            if (pocketed[i]) continue;
            boolean nearCorner = (Math.abs(Math.abs(x[i]) - halfX) < POCKET_RADIUS
                    && Math.abs(Math.abs(z[i]) - halfZ) < POCKET_RADIUS);
            boolean nearSide = Math.abs(x[i]) < POCKET_RADIUS
                    && Math.abs(Math.abs(z[i]) - halfZ) < POCKET_RADIUS;
            if (!nearCorner && !nearSide) continue;
            pocketed[i] = true;
            vx[i] = vz[i] = 0.0D;
            if (i == 0) {
                shotCuePocket = true;
                pocketed[0] = false;
                x[0] = 0.0D;
                z[0] = 0.0D;
            }
        }
    }

    private void resolveShot() {
        if (!allStopped()) return;
        if (shotCuePocket) {
            x[0] = 0.0D;
            z[0] = 0.0D;
            previousX[0] = 0.0D;
            previousZ[0] = 0.0D;
            vx[0] = 0.0D;
            vz[0] = 0.0D;
        }
        shotCuePocket = false;
        shotInProgress = false;
    }

    private boolean allStopped() {
        for (int i = 0; i < 16; i++) {
            if (!pocketed[i] && (Math.abs(vx[i]) > 0.00035D || Math.abs(vz[i]) > 0.00035D)) return false;
        }
        return true;
    }

    private boolean cueAvailable() { return !pocketed[0]; }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer()) return;
        pose = new PoseStackHolder(event.getPoseStack());
        Vec3 table = worldPosition(0.0D, TABLE_Y, 0.0D);
        Render3DUtils.drawFilledBox(new AABB(table.x - TABLE_WIDTH * 0.5D,
                table.y, table.z - TABLE_LENGTH * 0.5D, table.x + TABLE_WIDTH * 0.5D,
                table.y + 0.08D, table.z + TABLE_LENGTH * 0.5D), clothColor.get());
        drawRails();
        for (Vec3 pocket : pocketPositions()) Render3DUtils.drawFilledSphere(pocket, POCKET_RADIUS,
                new Color(12, 12, 16, 255), 12, 18);
        float partial = Mth.clamp(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        for (int i : renderBallOrder()) {
            if (pocketed[i]) continue;
            double px = Mth.lerp(partial, previousX[i], x[i]);
            double pz = Mth.lerp(partial, previousZ[i], z[i]);
            Render3DUtils.drawFilledSphere(worldPosition(px, TABLE_Y + 0.13D, pz), BALL_RADIUS,
                    ballColor(i), 12, 18);
        }
        if (allStopped() && cueAvailable()) {
            Vec3 cue = worldPosition(x[0], TABLE_Y + 0.13D, z[0]);
            Vec3 direction = horizontalLook();
            Render3DUtils.drawLine(pose.stack, cue, cue.add(direction.scale(2.5D)),
                    0xAAFFFFFF, 1.2F);
        }
        pose = null;
    }

    /** The sphere helper is depth-independent, so draw far balls before near balls. */
    private int[] renderBallOrder() {
        Integer[] sorted = new Integer[16];
        Vec3 camera = mc.getEntityRenderDispatcher().camera.position();
        for (int i = 0; i < sorted.length; i++) sorted[i] = i;
        java.util.Arrays.sort(sorted, (first, second) -> {
            Vec3 firstPosition = worldPosition(x[first], TABLE_Y + 0.13D, z[first]);
            Vec3 secondPosition = worldPosition(x[second], TABLE_Y + 0.13D, z[second]);
            return Double.compare(secondPosition.distanceToSqr(camera), firstPosition.distanceToSqr(camera));
        });
        int[] order = new int[sorted.length];
        for (int i = 0; i < sorted.length; i++) order[i] = sorted[i];
        return order;
    }

    @Listen
    private void onRender2D(Render2DEvent event) {
        if (noPlayer()) return;
        Canvas canvas = event.canvas();
        float width = 124.0F;
        float xPos = event.width() * 0.5F - width * 0.5F;
        SkijaUi.rounded(canvas, xPos, 14.0F, width, 36.0F, 5.0F, 0xB810141C);
        String title = "BILLIARDS";
        SkijaUi.boldText(canvas, title, xPos + 9.0F, 17.0F, 11.0F, 0xFFFFFFFF, 8.0F);
        SkijaUi.text(canvas, "PRACTICE", xPos + 9.0F, 31.0F, 12.0F, 0xFFD5DCE5, 7.0F);
        if (charging) {
            float barWidth = 122.0F;
            float barX = event.width() * 0.5F - barWidth * 0.5F;
            float barY = event.height() - 36.0F;
            SkijaUi.text(canvas, "CHARGE " + Math.round(charge * 100.0F / CHARGE_TICKS) + "%",
                    barX, barY - 13.0F, 11.0F, 0xFFFFFFFF, 7.0F);
            SkijaUi.rounded(canvas, barX, barY, barWidth, 4.0F, 2.0F, 0x663A424D);
            SkijaUi.rounded(canvas, barX, barY, barWidth * charge / CHARGE_TICKS,
                    4.0F, 2.0F, 0xFFE7B66A);
        }
        drawBallNumbers(canvas, event.width(), event.height());
    }

    private void drawRails() {
        double halfX = TABLE_WIDTH * 0.5D + RAIL_WIDTH * 0.5D;
        double halfZ = TABLE_LENGTH * 0.5D + RAIL_WIDTH * 0.5D;
        Vec3 center = worldPosition(0.0D, TABLE_Y + RAIL_HEIGHT * 0.5D, 0.0D);
        Color color = railColor.get();
        Render3DUtils.drawFilledBox(new AABB(center.x - halfX, center.y - RAIL_HEIGHT * 0.5D,
                center.z - halfZ, center.x + halfX, center.y + RAIL_HEIGHT * 0.5D,
                center.z - halfZ + RAIL_WIDTH), color);
        Render3DUtils.drawFilledBox(new AABB(center.x - halfX, center.y - RAIL_HEIGHT * 0.5D,
                center.z + halfZ - RAIL_WIDTH, center.x + halfX, center.y + RAIL_HEIGHT * 0.5D,
                center.z + halfZ), color);
        Render3DUtils.drawFilledBox(new AABB(center.x - halfX, center.y - RAIL_HEIGHT * 0.5D,
                center.z - halfZ, center.x - halfX + RAIL_WIDTH, center.y + RAIL_HEIGHT * 0.5D,
                center.z + halfZ), color);
        Render3DUtils.drawFilledBox(new AABB(center.x + halfX - RAIL_WIDTH, center.y - RAIL_HEIGHT * 0.5D,
                center.z - halfZ, center.x + halfX, center.y + RAIL_HEIGHT * 0.5D,
                center.z + halfZ), color);
    }

    private Vec3[] pocketPositions() {
        double hx = TABLE_WIDTH * 0.5D;
        double hz = TABLE_LENGTH * 0.5D;
        return new Vec3[]{worldPosition(-hx, TABLE_Y + 0.12D, -hz), worldPosition(hx, TABLE_Y + 0.12D, -hz),
                worldPosition(-hx, TABLE_Y + 0.12D, hz), worldPosition(hx, TABLE_Y + 0.12D, hz),
                worldPosition(0.0D, TABLE_Y + 0.12D, -hz), worldPosition(0.0D, TABLE_Y + 0.12D, hz)};
    }

    private Color ballColor(int index) {
        if (index == 0) return cueColor.get();
        if (index == 8) return new Color(22, 22, 28, 255);
        int[][] colors = {{220, 55, 48}, {48, 86, 220}, {226, 192, 44}, {104, 45, 145},
                {238, 120, 35}, {37, 154, 94}, {170, 44, 65}};
        int[] c = colors[(index - 1) % colors.length];
        return new Color(c[0], c[1], c[2], 255);
    }

    private Vec3 horizontalLook() {
        Vec3 look = mc.player.getLookAngle();
        double length = Math.sqrt(look.x * look.x + look.z * look.z);
        return length < 1.0E-5D ? new Vec3(0.0D, 0.0D, 1.0D)
                : new Vec3(look.x / length, 0.0D, look.z / length);
    }

    private Vec3 worldPosition(double localX, double localY, double localZ) {
        return new Vec3(originX + localX, originY + localY, originZ + localZ);
    }

    private void resetTable() { resetTableState(); }

    private void resetTableState() {
        java.util.Arrays.fill(vx, 0.0D);
        java.util.Arrays.fill(vz, 0.0D);
        java.util.Arrays.fill(pocketed, false);
        shotCuePocket = false;
        shotInProgress = false;
        charge = 0;
        chargeDirection = 1;
        charging = false;
        // The rack runs down the table's long axis; the cue ball starts opposite it.
        x[0] = 3.35D; z[0] = 0.0D;
        int index = 1;
        double rowSpacing = BALL_RADIUS * 2.1D;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col <= row; col++) {
                if (index >= 16) break;
                x[index] = -0.95D - row * rowSpacing * 0.86D;
                z[index] = (col - row * 0.5D) * rowSpacing;
                index++;
            }
        }
        for (int i = 0; i < 16; i++) { previousX[i] = x[i]; previousZ[i] = z[i]; }
    }

    private void drawBallNumbers(Canvas canvas, float screenWidth, float screenHeight) {
        float guiScale = (float) mc.getWindow().getGuiScale();
        float partial = Mth.clamp(
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        for (int i = 0; i < 16; i++) {
            if (i == 0) continue;
            if (pocketed[i]) continue;
            Vec3 center = worldPosition(Mth.lerp(partial, previousX[i], x[i]),
                    TABLE_Y + 0.13D, Mth.lerp(partial, previousZ[i], z[i]));
            Vector3f projected = WorldToScreen.getWorldPositionToScreen(center);
            if (!Float.isFinite(projected.x) || !Float.isFinite(projected.y)
                    || projected.z < 0.0F || projected.z > 1.0F) continue;
            String label = Integer.toString(i);
            float fontSize = 4.5F;
            float labelWidth = SkijaUi.boldTextWidth(label, fontSize);
            float px = projected.x / guiScale - labelWidth * 0.5F;
            float py = projected.y / guiScale - 2.5F;
            int color = i == 0 ? 0xFF22252A : 0xFFF7F8FA;
            SkijaUi.boldText(canvas, label, px, py, 5.5F, color, fontSize);
        }
    }

    private record PoseStackHolder(com.mojang.blaze3d.vertex.PoseStack stack) {}
}
