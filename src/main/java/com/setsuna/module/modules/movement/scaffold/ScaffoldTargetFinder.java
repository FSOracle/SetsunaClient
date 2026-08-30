package com.setsuna.module.modules.movement.scaffold;

import com.setsuna.manager.RotationManager;
import com.setsuna.util.rotation.Rot2f;
import com.setsuna.util.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** LiquidBounce-style block-neighbor, face, and aim-point target search. */
public final class ScaffoldTargetFinder {

    private static final List<BlockPos> NO_OFFSET = List.of(BlockPos.ZERO);
    private static final List<BlockPos> NORMAL_OFFSETS = generateOffsets(0, -1, 1);
    private static final List<BlockPos> DOWN_OFFSETS = generateOffsets(0, -1, 1, -2, 2);

    private final Minecraft mc = Minecraft.getInstance();
    private Rot2f serverRotation;

    public enum AimMode {
        CENTER,
        RANDOM,
        STABILIZED,
        NEAREST_ROTATION,
        REVERSE_YAW,
        DIAGONAL_YAW,
        ANGLE_YAW,
        EDGE_POINT
    }

    public Target find(
            BlockPos targetPosition,
            List<BlockPos> offsets,
            Vec3 predictedPosition,
            Pose predictedPose,
            ScaffoldMovementPlanner.Line priorityLine,
            ItemStack stack,
            AimMode aimMode,
            boolean considerFacingAwayFaces,
            Rot2f serverRotation) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || targetPosition == null) {
            return null;
        }
        this.serverRotation = serverRotation == null
                ? RotationManager.INSTANCE.getLastRotation()
                : serverRotation;

        BlockState targetState = mc.level.getBlockState(targetPosition);
        if (isSolid(targetState, targetPosition)) {
            return null;
        }

        Vec3 eyePosition = predictedPosition.add(0.0D, player.getEyeHeight(predictedPose), 0.0D);
        List<BlockPos> sortedOffsets = new ArrayList<>(offsets);
        sortedOffsets.sort(Comparator.comparingDouble(offset -> priorityDistance(
                targetPosition.offset(offset), predictedPosition, priorityLine)));

        for (BlockPos offset : sortedOffsets) {
            BlockPos placementPos = targetPosition.offset(offset).immutable();
            BlockState placementState = mc.level.getBlockState(placementPos);
            if (isSolid(placementState, placementPos)) {
                continue;
            }

            boolean placeAtNeighbor = placementState.isAir() || !placementState.getFluidState().isEmpty();
            if (!placeAtNeighbor && !placementState.canBeReplaced(new BlockPlaceContext(
                    player,
                    InteractionHand.MAIN_HAND,
                    stack,
                    new BlockHitResult(Vec3.atLowerCornerOf(placementPos), Direction.UP, placementPos, false)))) {
                continue;
            }

            Plan plan = findBestPlan(placementPos, placeAtNeighbor, eyePosition, considerFacingAwayFaces);
            if (plan == null) {
                continue;
            }

            PointOnFace point = findPointOnFace(
                    plan.interactedPos(), plan.direction(), eyePosition, priorityLine, aimMode);
            if (point == null) {
                continue;
            }

            Vec3 absolutePoint = point.point().add(
                    plan.interactedPos().getX(),
                    plan.interactedPos().getY(),
                    plan.interactedPos().getZ());
            Rot2f rotation = RotationUtils.calculate(eyePosition, absolutePoint);
            return new Target(
                    plan.interactedPos(),
                    placementPos,
                    plan.direction(),
                    point.face().minY() + plan.interactedPos().getY(),
                    rotation,
                    absolutePoint);
        }
        return null;
    }

    private Plan findBestPlan(
            BlockPos placementPos,
            boolean placeAtNeighbor,
            Vec3 eyePosition,
            boolean considerFacingAwayFaces) {
        Rot2f currentRotation = serverRotation;
        Plan best = null;
        double bestDelta = Double.POSITIVE_INFINITY;

        for (Direction direction : Direction.values()) {
            BlockPos interactedPos;
            if (placeAtNeighbor) {
                interactedPos = placementPos.relative(direction.getOpposite());
                if (mc.level.getBlockState(interactedPos).canBeReplaced()) {
                    continue;
                }
            } else {
                interactedPos = placementPos;
            }

            Vec3 faceCenter = interactedPos.getCenter().add(direction.getUnitVec3().scale(0.5D));
            Vec3 toEye = eyePosition.subtract(faceCenter);
            if (!considerFacingAwayFaces && toEye.dot(direction.getUnitVec3()) < 0.0D) {
                continue;
            }

            Rot2f targetRotation = RotationUtils.calculate(eyePosition, faceCenter);
            double yawDelta = Mth.wrapDegrees(targetRotation.getYaw() - currentRotation.getYaw());
            double pitchDelta = targetRotation.getPitch() - currentRotation.getPitch();
            double delta = yawDelta * yawDelta + pitchDelta * pitchDelta;
            if (delta < bestDelta) {
                bestDelta = delta;
                best = new Plan(interactedPos.immutable(), direction);
            }
        }
        return best;
    }

    private PointOnFace findPointOnFace(
            BlockPos interactedPos,
            Direction direction,
            Vec3 eyePosition,
            ScaffoldMovementPlanner.Line optimalLine,
            AimMode aimMode) {
        BlockState state = mc.level.getBlockState(interactedPos);
        List<AABB> boxes = state.getShape(mc.level, interactedPos, CollisionContext.of(mc.player)).toAabbs();
        PointOnFace best = null;
        double bestNormalDistance = Double.NEGATIVE_INFINITY;
        double bestY = Double.NEGATIVE_INFINITY;

        for (AABB box : boxes) {
            Face originalFace = Face.from(box, direction);
            Face searchFace = originalFace;
            if (searchFace.maxY() >= 0.9D && searchFace.maxY() - searchFace.minY() > 1.0E-7D) {
                Face upper = searchFace.withMinY(Math.max(searchFace.minY(), 0.6D));
                if (!upper.isEmpty()) {
                    searchFace = upper;
                }
            }

            Vec3 point = producePoint(searchFace.trimmed(), interactedPos, eyePosition, optimalLine, aimMode);
            if (point == null) {
                continue;
            }

            Vec3 fromCenter = point.subtract(0.5D, 0.5D, 0.5D);
            Vec3 normal = direction.getUnitVec3();
            double normalDistance = fromCenter.multiply(normal).lengthSqr();
            if (best == null || normalDistance > bestNormalDistance
                    || (normalDistance == bestNormalDistance && point.y > bestY)) {
                bestNormalDistance = normalDistance;
                bestY = point.y;
                best = new PointOnFace(originalFace, point);
            }
        }
        return best;
    }

    private Vec3 producePoint(
            Face face,
            BlockPos interactedPos,
            Vec3 eyePosition,
            ScaffoldMovementPlanner.Line optimalLine,
            AimMode aimMode) {
        return switch (aimMode) {
            case CENTER -> face.center();
            case RANDOM -> face.randomPoint();
            case EDGE_POINT -> hasHorizontalInput()
                    ? face.farthestVertexFrom(mc.player.position().subtract(
                    interactedPos.getX(), interactedPos.getY(), interactedPos.getZ()))
                    : nearestPointToRotation(face, interactedPos, eyePosition);
            case NEAREST_ROTATION -> nearestPointToRotation(face, interactedPos, eyePosition);
            case STABILIZED -> {
                Face stabilized = stabilizeFace(face, interactedPos, eyePosition, optimalLine);
                yield nearestPointToRotation(stabilized, interactedPos, eyePosition);
            }
            case REVERSE_YAW -> nearestPointToYaw(face, interactedPos, eyePosition, 180.0F);
            case DIAGONAL_YAW -> nearestPointToYaw(face, interactedPos, eyePosition, 75.0F);
            case ANGLE_YAW -> nearestPointToYaw(face, interactedPos, eyePosition, 45.0F);
        };
    }

    private Vec3 nearestPointToRotation(Face face, BlockPos targetPos, Vec3 eyePosition) {
        if (Mth.equal(face.area(), 0.0D)) {
            return face.from();
        }
        Vec3 localEye = eyePosition.subtract(targetPos.getX(), targetPos.getY(), targetPos.getZ());
        Rot2f rotation = serverRotation;
        return nearestPointOnBoxToLine(
                localEye,
                Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw()),
                face.asBox());
    }

    private Vec3 nearestPointToYaw(
            Face face,
            BlockPos targetPos,
            Vec3 eyePosition,
            float angle) {
        if (mc.player == null || !hasHorizontalInput()) {
            return nearestPointToRotation(face, targetPos, eyePosition);
        }
        float highYaw = Mth.wrapDegrees(mc.player.getYRot() + angle);
        float lowYaw = Mth.wrapDegrees(mc.player.getYRot() - angle);
        Vec3 localEye = eyePosition.subtract(targetPos.getX(), targetPos.getY(), targetPos.getZ());
        Segment highSegment = face.intersectYawPlane(localEye, highYaw);
        Segment lowSegment = face.intersectYawPlane(localEye, lowYaw);
        Vec3 high = highSegment == null ? null : closestPointToYaw(highSegment, localEye, highYaw);
        Vec3 low = lowSegment == null ? null : closestPointToYaw(lowSegment, localEye, lowYaw);
        float highError = high == null ? Float.MAX_VALUE : yawError(localEye, high, highYaw);
        float lowError = low == null ? Float.MAX_VALUE : yawError(localEye, low, lowYaw);
        if (highError > 5.0F && lowError > 5.0F) {
            return nearestPointToRotation(face, targetPos, eyePosition);
        }
        return highError < lowError ? high : low;
    }

    private Face stabilizeFace(
            Face face,
            BlockPos targetPos,
            Vec3 eyePosition,
            ScaffoldMovementPlanner.Line optimalLine) {
        if (optimalLine == null || mc.player == null) {
            return face;
        }

        Vec3 nearest = optimalLine.nearestPoint(mc.player.position());
        Vec3 awayFromLine = mc.player.position().subtract(nearest).normalize();
        Vec3 intersection = face.intersection(
                eyePosition.subtract(targetPos.getX(), targetPos.getY(), targetPos.getZ()),
                optimalLine.direction());
        if (intersection == null) {
            return face;
        }

        Vec3 b = mc.player.position().add(awayFromLine.scale(2.0D))
                .subtract(targetPos.getX(), targetPos.getY(), targetPos.getZ());
        double localPlayerY = mc.player.getY() - targetPos.getY();
        Face clamped = face.clampToBox(
                Math.min(intersection.x, b.x), localPlayerY - 2.0D, Math.min(intersection.z, b.z),
                Math.max(intersection.x, b.x), localPlayerY + 1.0D, Math.max(intersection.z, b.z));
        return clamped.area() < 0.0001D ? face : clamped;
    }

    private double priorityDistance(
            BlockPos blockPos,
            Vec3 predictedPosition,
            ScaffoldMovementPlanner.Line priorityLine) {
        BlockState state = mc.level.getBlockState(blockPos);
        VoxelShape shape = state.getShape(mc.level, blockPos);
        if (shape.isEmpty()) {
            return priorityLine == null
                    ? blockPos.getCenter().distanceToSqr(predictedPosition)
                    : priorityLine.distanceToSqr(blockPos.getCenter());
        }

        double best = Double.POSITIVE_INFINITY;
        for (AABB localBox : shape.toAabbs()) {
            AABB worldBox = localBox.move(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            double distance = priorityLine == null
                    ? worldBox.distanceToSqr(predictedPosition)
                    : nearestLineToBox(priorityLine.point(), priorityLine.direction(), worldBox).distanceSquared();
            best = Math.min(best, distance);
        }
        return best;
    }

    private static Vec3 closestPointToYaw(Segment segment, Vec3 eye, float targetYaw) {
        float startYaw = yaw(eye, segment.start());
        float endYaw = yaw(eye, segment.end());
        float yawDifference = Mth.wrapDegrees(endYaw - startYaw);
        float targetDifference = Mth.wrapDegrees(targetYaw - startYaw);
        double factor = yawDifference == 0.0F
                ? 0.0D
                : Mth.clamp(targetDifference / yawDifference, 0.0F, 1.0F);
        return segment.start().lerp(segment.end(), factor);
    }

    private static float yaw(Vec3 eye, Vec3 point) {
        Vec3 delta = point.subtract(eye);
        return (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
    }

    static double distanceToLineSqr(ScaffoldMovementPlanner.Line line, AABB box) {
        return nearestLineToBox(line.point(), line.direction(), box).distanceSquared();
    }

    static Vec3 nearestPointOnBoxToLine(Vec3 anchor, Vec3 direction, AABB box) {
        LineBoxResult result = nearestLineToBox(anchor, direction, box);
        Vec3 pointOnLine = anchor.add(direction.scale(result.parameter()));
        return new Vec3(
                Mth.clamp(pointOnLine.x, box.minX, box.maxX),
                Mth.clamp(pointOnLine.y, box.minY, box.maxY),
                Mth.clamp(pointOnLine.z, box.minZ, box.maxZ));
    }

    /** Exact minimization of squared distance from an infinite line to an axis-aligned box. */
    private static LineBoxResult nearestLineToBox(Vec3 anchor, Vec3 direction, AABB box) {
        List<Double> breakpoints = new ArrayList<>(6);
        addBreakpoints(breakpoints, anchor.x, direction.x, box.minX, box.maxX);
        addBreakpoints(breakpoints, anchor.y, direction.y, box.minY, box.maxY);
        addBreakpoints(breakpoints, anchor.z, direction.z, box.minZ, box.maxZ);
        breakpoints.sort(Double::compare);

        List<Double> unique = new ArrayList<>(breakpoints.size());
        for (double value : breakpoints) {
            if (unique.isEmpty() || Math.abs(value - unique.getLast()) > 1.0E-9D) {
                unique.add(value);
            }
        }

        LineBoxResult best = evaluateLineParameter(anchor, direction, box, 0.0D, null);
        for (double breakpoint : unique) {
            best = evaluateLineParameter(anchor, direction, box, breakpoint, best);
        }

        for (int index = 0; index <= unique.size(); index++) {
            double lower = index == 0 ? Double.NEGATIVE_INFINITY : unique.get(index - 1);
            double upper = index == unique.size() ? Double.POSITIVE_INFINITY : unique.get(index);
            double sample;
            if (!Double.isFinite(lower)) {
                sample = Double.isFinite(upper) ? upper - 1.0D : 0.0D;
            } else if (!Double.isFinite(upper)) {
                sample = lower + 1.0D;
            } else {
                sample = (lower + upper) * 0.5D;
            }

            Vec3 samplePoint = anchor.add(direction.scale(sample));
            double quadraticA = 0.0D;
            double quadraticB = 0.0D;
            if (samplePoint.x < box.minX) {
                quadraticA += direction.x * direction.x;
                quadraticB += direction.x * (anchor.x - box.minX);
            } else if (samplePoint.x > box.maxX) {
                quadraticA += direction.x * direction.x;
                quadraticB += direction.x * (anchor.x - box.maxX);
            }
            if (samplePoint.y < box.minY) {
                quadraticA += direction.y * direction.y;
                quadraticB += direction.y * (anchor.y - box.minY);
            } else if (samplePoint.y > box.maxY) {
                quadraticA += direction.y * direction.y;
                quadraticB += direction.y * (anchor.y - box.maxY);
            }
            if (samplePoint.z < box.minZ) {
                quadraticA += direction.z * direction.z;
                quadraticB += direction.z * (anchor.z - box.minZ);
            } else if (samplePoint.z > box.maxZ) {
                quadraticA += direction.z * direction.z;
                quadraticB += direction.z * (anchor.z - box.maxZ);
            }

            if (quadraticA > 1.0E-9D) {
                double root = -quadraticB / quadraticA;
                if (root > lower && root < upper) {
                    best = evaluateLineParameter(anchor, direction, box, root, best);
                }
            } else {
                best = evaluateLineParameter(anchor, direction, box, sample, best);
            }
        }
        return best;
    }

    private static void addBreakpoints(
            List<Double> breakpoints, double anchor, double direction, double minimum, double maximum) {
        if (!Mth.equal(direction, 0.0D)) {
            double first = (minimum - anchor) / direction;
            double second = (maximum - anchor) / direction;
            if (Double.isFinite(first)) breakpoints.add(first);
            if (Double.isFinite(second)) breakpoints.add(second);
        }
    }

    private static LineBoxResult evaluateLineParameter(
            Vec3 anchor, Vec3 direction, AABB box, double parameter, LineBoxResult currentBest) {
        Vec3 point = anchor.add(direction.scale(parameter));
        double distance = box.distanceToSqr(point);
        if (currentBest == null || distance < currentBest.distanceSquared() - 1.0E-9D) {
            return new LineBoxResult(parameter, distance);
        }
        return currentBest;
    }

    private boolean hasHorizontalInput() {
        return mc.player != null && mc.player.input != null
                && (mc.player.input.getMoveVector().x != 0.0F || mc.player.input.getMoveVector().y != 0.0F);
    }

    private static float yawError(Vec3 eye, Vec3 point, float targetYaw) {
        return Math.abs(Mth.wrapDegrees(yaw(eye, point) - targetYaw));
    }

    private boolean isSolid(BlockState state, BlockPos pos) {
        return state.isFaceSturdy(mc.level, pos, Direction.UP, SupportType.CENTER);
    }

    public static List<BlockPos> noOffset() {
        return NO_OFFSET;
    }

    public static List<BlockPos> normalOffsets() {
        return NORMAL_OFFSETS;
    }

    public static List<BlockPos> downOffsets() {
        return DOWN_OFFSETS;
    }

    private static List<BlockPos> generateOffsets(int... horizontalValues) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (int x : horizontalValues) {
            for (int z : horizontalValues) {
                positions.add(new BlockPos(x, 0, z));
                positions.add(new BlockPos(x, -1, z));
            }
        }
        List<BlockPos> result = new ArrayList<>(positions);
        result.sort(Comparator
                .comparingLong((BlockPos pos) -> (long) pos.getX() * pos.getX()
                        + (long) pos.getY() * pos.getY() + (long) pos.getZ() * pos.getZ())
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return List.copyOf(result);
    }

    public record Target(
            BlockPos interactedBlockPos,
            BlockPos placedBlock,
            Direction direction,
            double minPlacementY,
            Rot2f rotation,
            Vec3 aimPoint) {

        public BlockHitResult fallbackHitResult() {
            return new BlockHitResult(interactedBlockPos.getCenter(), direction, interactedBlockPos, false);
        }

        public boolean matches(BlockHitResult hit) {
            return hit != null
                    && hit.getType() == HitResult.Type.BLOCK
                    && hit.getBlockPos().equals(interactedBlockPos)
                    && hit.getDirection() == direction
                    && hit.getLocation().y + 1.0E-7D >= minPlacementY;
        }
    }

    private record Plan(BlockPos interactedPos, Direction direction) {
    }

    private record PointOnFace(Face face, Vec3 point) {
    }

    private record Segment(Vec3 start, Vec3 end) {
    }

    private record LineBoxResult(double parameter, double distanceSquared) {
    }

    private record LineSegmentDistance(Vec3 pointOnSegment, double distanceSquared) {
    }

    private record Face(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        static Face from(AABB box, Direction direction) {
            return switch (direction) {
                case DOWN -> new Face(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
                case UP -> new Face(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
                case NORTH -> new Face(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
                case SOUTH -> new Face(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);
                case WEST -> new Face(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
                case EAST -> new Face(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            };
        }

        Face trimmed() {
            double xOffset = (maxX - minX) * 0.15D;
            double yOffset = (maxY - minY) * 0.15D;
            double zOffset = (maxZ - minZ) * 0.15D;
            return new Face(
                    minX + xOffset, minY + yOffset, minZ + zOffset,
                    maxX - xOffset, maxY - yOffset, maxZ - zOffset);
        }

        Face withMinY(double value) {
            return new Face(minX, value, minZ, maxX, maxY, maxZ);
        }

        Face clampToBox(double x1, double y1, double z1, double x2, double y2, double z2) {
            Vec3 first = new Vec3(
                    Mth.clamp(minX, x1, x2),
                    Mth.clamp(minY, y1, y2),
                    Mth.clamp(minZ, z1, z2));
            Vec3 second = new Vec3(
                    Mth.clamp(maxX, x1, x2),
                    Mth.clamp(maxY, y1, y2),
                    Mth.clamp(maxZ, z1, z2));
            return between(first, second);
        }

        boolean isEmpty() {
            return minX > maxX || minY > maxY || minZ > maxZ;
        }

        double area() {
            double x = Math.max(0.0D, maxX - minX);
            double y = Math.max(0.0D, maxY - minY);
            double z = Math.max(0.0D, maxZ - minZ);
            return Math.max(x * y, Math.max(x * z, y * z));
        }

        Vec3 center() {
            return new Vec3((minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D);
        }

        Vec3 randomPoint() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            return new Vec3(
                    minX == maxX ? minX : random.nextDouble(minX, maxX),
                    minY == maxY ? minY : random.nextDouble(minY, maxY),
                    minZ == maxZ ? minZ : random.nextDouble(minZ, maxZ));
        }

        Vec3 farthestVertexFrom(Vec3 position) {
            Vec3 best = center();
            double bestDistance = -1.0D;
            for (double x : new double[]{minX, maxX}) {
                for (double y : new double[]{minY, maxY}) {
                    for (double z : new double[]{minZ, maxZ}) {
                        Vec3 point = new Vec3(x, y, z);
                        double distance = point.distanceToSqr(position);
                        if (distance > bestDistance) {
                            bestDistance = distance;
                            best = point;
                        }
                    }
                }
            }
            return best;
        }

        Vec3 intersection(Vec3 origin, Vec3 direction) {
            int axis;
            double plane;
            if (Math.abs(maxX - minX) < 1.0E-9D) {
                axis = 0;
                plane = minX;
            } else if (Math.abs(maxY - minY) < 1.0E-9D) {
                axis = 1;
                plane = minY;
            } else {
                axis = 2;
                plane = minZ;
            }
            double component = axis == 0 ? direction.x : axis == 1 ? direction.y : direction.z;
            if (Math.abs(component) < 1.0E-9D) {
                return null;
            }
            double originComponent = axis == 0 ? origin.x : axis == 1 ? origin.y : origin.z;
            double t = (plane - originComponent) / component;
            return origin.add(direction.scale(t));
        }

        Segment intersectYawPlane(Vec3 eye, float yaw) {
            Vec3 horizontal = Vec3.directionFromRotation(0.0F, yaw);
            if (Math.abs(maxY - minY) < 1.0E-9D) {
                return coerceLineToFace(new Vec3(eye.x, minY, eye.z), horizontal);
            }

            if (Math.abs(maxX - minX) < 1.0E-9D) {
                if (Math.abs(horizontal.x) < 1.0E-9D) return null;
                double parameter = (minX - eye.x) / horizontal.x;
                double z = eye.z + horizontal.z * parameter;
                return coerceLineToFace(new Vec3(minX, eye.y, z), new Vec3(0.0D, 1.0D, 0.0D));
            }

            if (Math.abs(horizontal.z) < 1.0E-9D) return null;
            double parameter = (minZ - eye.z) / horizontal.z;
            double x = eye.x + horizontal.x * parameter;
            return coerceLineToFace(new Vec3(x, eye.y, minZ), new Vec3(0.0D, 1.0D, 0.0D));
        }

        private Segment coerceLineToFace(Vec3 lineAnchor, Vec3 lineDirection) {
            List<LineSegmentDistance> nearestToEdges = new ArrayList<>(4);
            for (Segment edge : edges()) {
                nearestToEdges.add(nearestPointOnSegmentToLine(lineAnchor, lineDirection, edge));
            }
            nearestToEdges.sort(Comparator.comparingDouble(LineSegmentDistance::distanceSquared));
            if (nearestToEdges.size() < 2) return null;
            Vec3 first = nearestToEdges.get(0).pointOnSegment();
            Vec3 second = nearestToEdges.get(1).pointOnSegment();
            return first.distanceToSqr(second) <= 1.0E-12D ? null : new Segment(first, second);
        }

        private List<Segment> edges() {
            if (Math.abs(maxX - minX) < 1.0E-9D) {
                return List.of(
                        new Segment(new Vec3(minX, minY, minZ), new Vec3(minX, maxY, minZ)),
                        new Segment(new Vec3(minX, minY, maxZ), new Vec3(minX, maxY, maxZ)),
                        new Segment(new Vec3(minX, minY, minZ), new Vec3(minX, minY, maxZ)),
                        new Segment(new Vec3(minX, maxY, minZ), new Vec3(minX, maxY, maxZ)));
            }
            if (Math.abs(maxY - minY) < 1.0E-9D) {
                return List.of(
                        new Segment(new Vec3(minX, minY, minZ), new Vec3(maxX, minY, minZ)),
                        new Segment(new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ)),
                        new Segment(new Vec3(minX, minY, minZ), new Vec3(minX, minY, maxZ)),
                        new Segment(new Vec3(maxX, minY, minZ), new Vec3(maxX, minY, maxZ)));
            }
            return List.of(
                    new Segment(new Vec3(minX, minY, minZ), new Vec3(maxX, minY, minZ)),
                    new Segment(new Vec3(minX, maxY, minZ), new Vec3(maxX, maxY, minZ)),
                    new Segment(new Vec3(minX, minY, minZ), new Vec3(minX, maxY, minZ)),
                    new Segment(new Vec3(maxX, minY, minZ), new Vec3(maxX, maxY, minZ)));
        }

        private static LineSegmentDistance nearestPointOnSegmentToLine(
                Vec3 lineAnchor, Vec3 lineDirection, Segment segment) {
            Vec3 segmentDirection = segment.end().subtract(segment.start());
            Vec3 delta = lineAnchor.subtract(segment.start());
            double a = lineDirection.lengthSqr();
            double b = lineDirection.dot(segmentDirection);
            double c = segmentDirection.lengthSqr();
            double d = lineDirection.dot(delta);
            double e = segmentDirection.dot(delta);
            double determinant = a * c - b * b;
            double segmentParameter;
            if (Math.abs(determinant) > 1.0E-9D) {
                segmentParameter = Mth.clamp((a * e - b * d) / determinant, 0.0D, 1.0D);
            } else {
                double startDistance = distanceFromPointToLineSqr(segment.start(), lineAnchor, lineDirection, a);
                double endDistance = distanceFromPointToLineSqr(segment.end(), lineAnchor, lineDirection, a);
                segmentParameter = startDistance <= endDistance ? 0.0D : 1.0D;
            }

            Vec3 pointOnSegment = segment.start().add(segmentDirection.scale(segmentParameter));
            double lineParameter = (b * segmentParameter - d) / a;
            Vec3 pointOnLine = lineAnchor.add(lineDirection.scale(lineParameter));
            return new LineSegmentDistance(pointOnSegment, pointOnSegment.distanceToSqr(pointOnLine));
        }

        private static double distanceFromPointToLineSqr(
                Vec3 point, Vec3 lineAnchor, Vec3 lineDirection, double directionLengthSquared) {
            double parameter = point.subtract(lineAnchor).dot(lineDirection) / directionLengthSquared;
            return point.distanceToSqr(lineAnchor.add(lineDirection.scale(parameter)));
        }

        AABB asBox() {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        Vec3 from() {
            return new Vec3(minX, minY, minZ);
        }

        private static Face between(Vec3 first, Vec3 second) {
            return new Face(
                    Math.min(first.x, second.x),
                    Math.min(first.y, second.y),
                    Math.min(first.z, second.z),
                    Math.max(first.x, second.x),
                    Math.max(first.y, second.y),
                    Math.max(first.z, second.z));
        }
    }
}
