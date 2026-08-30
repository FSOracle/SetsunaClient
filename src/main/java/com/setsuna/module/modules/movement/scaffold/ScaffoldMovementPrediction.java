package com.setsuna.module.modules.movement.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

/** Placement-position learning and bootstrap prediction used by Scaffold. */
public final class ScaffoldMovementPrediction {

    private static final int MAX_PLACEMENT_OFFSETS = 4;

    private final Minecraft mc = Minecraft.getInstance();
    private final ScaffoldMovementPlanner planner;
    private final ArrayDeque<Vec3> lastPlacementOffsets = new ArrayDeque<>(MAX_PLACEMENT_OFFSETS + 1);

    public ScaffoldMovementPrediction(ScaffoldMovementPlanner planner) {
        this.planner = planner;
    }

    public Vec3 getPredictedPlacementPos(
            ScaffoldMovementPlanner.Line optimalLine,
            boolean enabled,
            double bootstrapBackoff,
            double cutoffDistance,
            int warmupPlacements,
            float forward,
            float strafe,
            boolean forceSafeWalk) {
        LocalPlayer player = mc.player;
        if (!enabled || optimalLine == null || player == null || mc.level == null) {
            return null;
        }
        if (ScaffoldPlayerSimulation.isCloseToEdge(
                mc, forward, strafe, cutoffDistance, forceSafeWalk)) {
            return null;
        }

        Vec3 fallOffPoint = getFallOffPositionOnLine(optimalLine);
        if (fallOffPoint == null) {
            return null;
        }

        Vec3 playerPos = player.position();
        Vec3 fromPlayerToEdge = fallOffPoint.subtract(playerPos);
        Vec3 bootstrap = fallOffPoint;
        if (bootstrapBackoff > 0.0D && fromPlayerToEdge.lengthSqr() > 1.0E-12D) {
            bootstrap = fallOffPoint.subtract(fromPlayerToEdge.normalize().scale(bootstrapBackoff));
        }

        Vec3 average = averagePlacementOffset();
        if (average == null) {
            ScaffoldMovementPlanner.SupportReference support = planner.getCurrentSupportReference();
            return support == null ? bootstrap : bootstrap.add(support.offsetX(), 0.0D, support.offsetZ());
        }

        float lineAngle = (float) Math.atan2(optimalLine.direction().z, optimalLine.direction().x);
        Vec3 learned = fallOffPoint.add(average.yRot(-lineAngle));
        double blend = warmupPlacements <= 0
                ? 1.0D
                : Math.min(1.0D, (double) lastPlacementOffsets.size() / warmupPlacements);
        return bootstrap.lerp(learned, blend);
    }

    public void onPlace(ScaffoldMovementPlanner.Line optimalLine, Vec3 previousFallOffPosition, boolean enabled) {
        if (!enabled || optimalLine == null || previousFallOffPosition == null || mc.player == null) {
            return;
        }
        float lineAngle = (float) Math.atan2(optimalLine.direction().z, optimalLine.direction().x);
        Vec3 unrotatedOffset = mc.player.position().subtract(previousFallOffPosition).yRot(lineAngle);
        lastPlacementOffsets.addLast(unrotatedOffset);
        while (lastPlacementOffsets.size() > MAX_PLACEMENT_OFFSETS) {
            lastPlacementOffsets.removeFirst();
        }
    }

    public Vec3 getFallOffPositionOnLine(ScaffoldMovementPlanner.Line line) {
        LocalPlayer player = mc.player;
        if (line == null || player == null || mc.level == null) {
            return null;
        }

        Vec3 start = line.nearestPoint(player.position()).add(0.0D, -0.1D, 0.0D);
        Vec3 direction = new Vec3(line.direction().x, 0.0D, line.direction().z).normalize();
        if (direction.lengthSqr() < 1.0E-12D) {
            return null;
        }
        Vec3 edge = ScaffoldEdgeCollision.find(mc, start, start.add(direction.scale(3.0D)), 0.5D);
        return edge == null ? null : new Vec3(edge.x, player.getY(), edge.z);
    }

    private Vec3 averagePlacementOffset() {
        if (lastPlacementOffsets.isEmpty()) {
            return null;
        }
        Vec3 sum = Vec3.ZERO;
        for (Vec3 offset : lastPlacementOffsets) {
            sum = sum.add(offset);
        }
        return sum.scale(1.0D / lastPlacementOffsets.size());
    }

    public void reset() {
        lastPlacementOffsets.clear();
    }
}
