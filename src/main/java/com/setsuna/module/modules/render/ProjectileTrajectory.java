package com.setsuna.module.modules.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.setsuna.event.Listen;
import com.setsuna.event.events.Render3DEvent;
import com.setsuna.event.events.WorldItemSubmitEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.util.render.Render3DUtils;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Predicts the future path of currently flying projectiles. This deliberately
 * does not render a trail behind the entity: every segment is calculated from
 * the current velocity and vanilla-like gravity/drag until a block is hit.
 */
public final class ProjectileTrajectory extends Module {

    public static final ProjectileTrajectory INSTANCE = new ProjectileTrajectory();

    private static final int MAX_TICKS = 512;
    private static final double MAX_SPEED_EPSILON = 0.001D;
    private static final long TRAIL_EMIT_INTERVAL_NANOS = 32_000_000L;
    private static final Color TARGET_COLOR = new Color(240, 48, 56, 235);
    private static final double TARGET_HIT_EXPAND = 0.25D;

    public enum TrailMode {
        SPIRAL,
        TRAIL,
        DNA
    }

    public enum TrailParticle {
        END_ROD,
        CRIT,
        ELECTRIC_SPARK,
        ENCHANTED_HIT,
        GLOW
    }

    public final IntSetting range = add(new IntSetting("Range", 64, 1, 256, 1));
    public final ColorSetting color = add(new ColorSetting(
            "Color", new Color(255, 190, 70, 225)));
    public final BooleanSetting ownProjectiles = add(
            new BooleanSetting("Own Projectiles", true));
    public final BooleanSetting trailEffects = add(new BooleanSetting(
            "Trail Effects", false).visibleWhen(ownProjectiles::get));
    public final EnumSetting<TrailMode> trailMode = add(new EnumSetting<>(
            "Trail Mode", TrailMode.TRAIL)
            .visibleWhen(() -> ownProjectiles.get() && trailEffects.get()));
    public final EnumSetting<TrailParticle> trailParticle = add(new EnumSetting<>(
            "Trail Particle", TrailParticle.END_ROD)
            .visibleWhen(() -> ownProjectiles.get() && trailEffects.get()));
    public final IntSetting trailLength = add(new IntSetting(
            "Trail Length", 8, 1, 32, 1)
            .visibleWhen(() -> ownProjectiles.get() && trailEffects.get()));
    public final IntSetting trailParticleCount = add(new IntSetting(
            "Particle Count", 8, 1, 32, 1)
            .visibleWhen(() -> ownProjectiles.get() && trailEffects.get()));
    public final EnumSetting<TrailParticle> dnaParticle = add(new EnumSetting<>(
            "DNA Particle", TrailParticle.ELECTRIC_SPARK)
            .visibleWhen(() -> ownProjectiles.get() && trailEffects.get()
                    && trailMode.is(TrailMode.DNA)));

    private final List<PathPrediction> predictions = new ArrayList<>();
    private final Map<Integer, Long> trailEmitTimes = new HashMap<>();
    private final Set<PathPrediction> targetedPaths = new HashSet<>();
    private ProjectileTrajectory() {
        super("Projectile Trajectory", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        predictions.clear();
        trailEmitTimes.clear();
        targetedPaths.clear();
    }

    @Listen
    private void onRender3D(Render3DEvent event) {
        if (noPlayer()) {
            predictions.clear();
            trailEmitTimes.clear();
            targetedPaths.clear();
            return;
        }

        updatePredictions();
        updateTargetedPaths();
        drawPredictions(event, predictions);
    }

    private void updatePredictions() {
        predictions.clear();
        targetedPaths.clear();
        if (noPlayer()) {
            return;
        }
        double maxDistance = range.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Projectile projectile) || !shouldTrack(projectile)) {
                continue;
            }
            boolean own = isOwnProjectile(projectile);
            if (own && !ownProjectiles.get()) {
                continue;
            }
            Vec3 start = projectile.position();
            if (mc.player.distanceToSqr(start) > maxDistance * maxDistance) {
                continue;
            }
            if (own && trailEffects.get()) {
                emitTrail(projectile);
            }
            PathPrediction prediction = predict(projectile, start, projectile.getDeltaMovement(), physicsFor(projectile));
            if (!prediction.segments().isEmpty()) {
                predictions.add(prediction);
            }
        }

        if (ownProjectiles.get()) {
            HeldProjectile heldProjectile = heldProjectile();
            if (heldProjectile != null) {
                Vec3 direction = projectileDirection();
                Vec3 start = projectileOrigin();
                PredictionPhysics physics = physicsFor(heldProjectile.stack());
                double initialVelocity = physics.initialVelocity();
                boolean shouldPredict = true;
                if (heldProjectile.stack().getItem() == Items.BOW) {
                    int used = mc.player.isUsingItem()
                            && mc.player.getUseItem().getItem() == Items.BOW
                            ? mc.player.getTicksUsingItem() : BowItem.MAX_DRAW_DURATION;
                    float power = BowItem.getPowerForTime(Math.min(BowItem.MAX_DRAW_DURATION, used));
                    shouldPredict = power >= 0.1F;
                    if (power >= 0.1F) {
                        initialVelocity = power * PredictionPhysics.BOW.initialVelocity();
                        physics = physics.withInitialVelocity(initialVelocity);
                    }
                }
                if (shouldPredict) {
                    Vec3 velocity = direction.scale(initialVelocity);
                    if (physics.copiesPlayerVelocity()) {
                        velocity = velocity.add(mc.player.getDeltaMovement().x,
                                mc.player.onGround() ? 0.0D : mc.player.getDeltaMovement().y,
                                mc.player.getDeltaMovement().z);
                    }
                    PathPrediction heldPrediction = predict(null, start, velocity, physics)
                            .withItem(heldProjectile.stack(), mc.player)
                            .withRenderOffset(heldItemRenderOffset());
                    if (!heldPrediction.segments().isEmpty()) {
                        predictions.add(heldPrediction);
                    }
                }
            }
        }

    }

    private void updateTargetedPaths() {
        targetedPaths.clear();
        if (noPlayer() || predictions.isEmpty()) {
            return;
        }
        for (PathPrediction prediction : predictions) {
            if (pathHitsTarget(prediction)) {
                targetedPaths.add(prediction);
            }
        }
    }

    private boolean pathHitsTarget(PathPrediction prediction) {
        Entity source = prediction.source();
        Entity owner = source instanceof Projectile projectile ? projectile.getOwner() : null;
        boolean skipPlayer = source == null
                || source == mc.player
                || owner == mc.player;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity target)
                    || entity == source
                    || entity == owner
                    || skipPlayer && entity == mc.player) {
                continue;
            }
            AABB hitBox = target.getBoundingBox().inflate(TARGET_HIT_EXPAND);
            for (Segment segment : prediction.segments()) {
                if (segmentIntersectsBox(segment.start(), segment.end(), hitBox)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentIntersectsBox(Vec3 start, Vec3 end, AABB box) {
        double tMin = 0.0D;
        double tMax = 1.0D;
        Vec3 delta = end.subtract(start);
        for (int axis = 0; axis < 3; axis++) {
            double origin = axis == 0 ? start.x : axis == 1 ? start.y : start.z;
            double direction = axis == 0 ? delta.x : axis == 1 ? delta.y : delta.z;
            double min = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
            double max = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
            if (Math.abs(direction) < 1.0E-9D) {
                if (origin < min || origin > max) {
                    return false;
                }
                continue;
            }
            double inverse = 1.0D / direction;
            double first = (min - origin) * inverse;
            double second = (max - origin) * inverse;
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            tMin = Math.max(tMin, first);
            tMax = Math.min(tMax, second);
            if (tMin > tMax) {
                return false;
            }
        }
        return true;
    }

    private HeldProjectile heldProjectile() {
        if (mc.player.isUsingItem()) {
            InteractionHand usedHand = mc.player.getUsedItemHand();
            ItemStack usedStack = mc.player.getItemInHand(usedHand);
            if (isHeldProjectile(usedStack)) {
                return new HeldProjectile(usedStack.copy());
            }
        }
        ItemStack main = mc.player.getMainHandItem();
        if (isHeldProjectile(main)) {
            return new HeldProjectile(main.copy());
        }
        ItemStack offhand = mc.player.getOffhandItem();
        return isHeldProjectile(offhand)
                ? new HeldProjectile(offhand.copy()) : null;
    }

    /** Matches the vanilla throwable/arrow entity spawn point. */
    private Vec3 projectileOrigin() {
        return new Vec3(mc.player.getX(), mc.player.getEyeY() - 0.10000000149011612D, mc.player.getZ());
    }

    /** Same rotation-to-direction calculation used by vanilla projectile shooting. */
    private Vec3 projectileDirection() {
        double yaw = Math.toRadians(mc.player.getYRot());
        double pitch = Math.toRadians(mc.player.getXRot());
        double cosPitch = Math.cos(pitch);
        return new Vec3(
                -Math.sin(yaw) * cosPitch,
                -Math.sin(pitch),
                Math.cos(yaw) * cosPitch).normalize();
    }

    /**
     * Matches LiquidBounce's held-item renderer. The simulation starts at the
     * vanilla eye-height origin, then the whole predicted path is moved to the
     * interpolated hand-side render position. Without this offset the preview
     * is visually anchored to the crosshair instead of the held item.
     */
    private Vec3 heldItemRenderOffset() {
        float partialTick = Mth.clamp(
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        Vec3 interpolatedPosition = new Vec3(
                Mth.lerp(partialTick, mc.player.xOld, mc.player.getX()),
                Mth.lerp(partialTick, mc.player.yOld, mc.player.getY()),
                Mth.lerp(partialTick, mc.player.zOld, mc.player.getZ()));
        Vec3 interpolatedOffset = interpolatedPosition.subtract(mc.player.position());
        double yawRadians = Math.toRadians(mc.player.getYRot(partialTick));
        return interpolatedOffset.add(
                -Math.cos(yawRadians) * 0.16D,
                0.0D,
                -Math.sin(yawRadians) * 0.16D);
    }

    private boolean isHeldProjectile(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return item == Items.SNOWBALL
                || item == Items.EGG
                || item == Items.ENDER_PEARL
                || item == Items.EXPERIENCE_BOTTLE
                || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION
                || item == Items.BOW
                || item == Items.CROSSBOW
                || item == Items.FISHING_ROD
                || item == Items.FIRE_CHARGE
                || item == Items.FIREWORK_ROCKET
                || item == Items.WIND_CHARGE
                || item == Items.TRIDENT;
    }

    @Listen
    private void onWorldItemSubmit(WorldItemSubmitEvent event) {
        if (noPlayer()) {
            return;
        }
        // submitEntities fires before renderLevel's Render3DEvent, so refresh
        // here as well to keep the landing icon in the current frame.
        updatePredictions();
        if (predictions.isEmpty()) return;
        for (PathPrediction prediction : predictions) {
            Landing landing = prediction.landing();
            if (landing == null || prediction.item().isEmpty()) {
                continue;
            }
            Vec3 position = landing.position()
                    .add(landing.normal().getStepX() * 0.08D,
                            landing.normal().getStepY() * 0.08D,
                            landing.normal().getStepZ() * 0.08D)
                    .add(0.0D, 0.34D, 0.0D);
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(position.subtract(event.getCameraPosition()));
            pose.scale(0.48F, 0.48F, 0.48F);
            ItemStackRenderState state = new ItemStackRenderState();
            mc.getItemModelResolver().updateForNonLiving(
                    state, prediction.item(), ItemDisplayContext.FIXED, prediction.source());
            state.submit(pose, event.getCollector(),
                    LevelRenderer.getLightCoords(mc.level, BlockPos.containing(landing.position())),
                    OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    private boolean shouldTrack(Projectile projectile) {
        return projectile instanceof ItemSupplier
                || projectile instanceof AbstractArrow
                || projectile instanceof ThrownTrident
                || projectile instanceof AbstractHurtingProjectile
                || projectile instanceof FireworkRocketEntity;
    }

    private boolean isOwnProjectile(Projectile projectile) {
        Entity owner = projectile.getOwner();
        return owner == mc.player || owner != null
                && owner.getUUID().equals(mc.player.getUUID());
    }

    private void emitTrail(Projectile projectile) {
        long now = System.nanoTime();
        long previous = trailEmitTimes.getOrDefault(projectile.getId(), 0L);
        if (now - previous < TRAIL_EMIT_INTERVAL_NANOS) {
            return;
        }
        trailEmitTimes.put(projectile.getId(), now);

        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity.lengthSqr() < MAX_SPEED_EPSILON) {
            return;
        }
        Vec3 direction = velocity.normalize();
        ParticleOptions particle = trailParticle();
        int samples = Math.max(1, trailParticleCount.get());
        Vec3 first = projectile.position().subtract(direction.scale(0.30D));
        double spacing = Math.max(0.05D, trailLength.get() * 0.18D / samples);

        if (trailMode.is(TrailMode.TRAIL)) {
            // Keep the original local trail behaviour: particles follow the
            // projectile's current direction instead of the whole prediction.
            for (int index = 0; index < samples; index++) {
                addParticle(particle, first.subtract(direction.scale(spacing * index)));
            }
            return;
        }

        Vec3 reference = Math.abs(direction.y) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 up = side.cross(direction).normalize();
        double phase = now / 90_000_000.0D + projectile.getId() * 0.47D;
        double radius = trailMode.is(TrailMode.DNA) ? 0.36D : 0.42D;
        if (trailMode.is(TrailMode.DNA)) {
            ParticleOptions secondParticle = dnaParticle();
            for (int index = 0; index < samples; index++) {
                Vec3 center = first.subtract(direction.scale(spacing * index));
                double samplePhase = phase - index * 0.82D;
                addHelixParticle(particle, center, side, up, samplePhase, radius);
                addHelixParticle(secondParticle, center, side, up,
                        samplePhase + Math.PI, radius);
            }
            return;
        }
        for (int index = 0; index < samples; index++) {
            addHelixParticle(particle,
                    first.subtract(direction.scale(spacing * index)), side, up,
                    phase - index * 0.82D, radius);
        }
    }

    private void addHelixParticle(ParticleOptions particle, Vec3 center,
                                  Vec3 side, Vec3 up, double phase, double radius) {
        Vec3 offset = side.scale(Math.cos(phase) * radius)
                .add(up.scale(Math.sin(phase) * radius));
        addParticle(particle, center.add(offset));
    }

    private void addParticle(ParticleOptions particle, Vec3 position) {
        mc.level.addParticle(particle, position.x, position.y, position.z,
                0.0D, 0.0D, 0.0D);
    }

    private ParticleOptions trailParticle() {
        return particle(trailParticle.get());
    }

    private ParticleOptions dnaParticle() {
        return particle(dnaParticle.get());
    }

    private ParticleOptions particle(TrailParticle selected) {
        return switch (selected) {
            case END_ROD -> ParticleTypes.END_ROD;
            case CRIT -> ParticleTypes.CRIT;
            case ELECTRIC_SPARK -> ParticleTypes.ELECTRIC_SPARK;
            case ENCHANTED_HIT -> ParticleTypes.ENCHANTED_HIT;
            case GLOW -> ParticleTypes.GLOW;
        };
    }

    private PathPrediction predict(Projectile projectile, Vec3 start, Vec3 initialVelocity,
                                   PredictionPhysics physics) {
        List<Segment> segments = new ArrayList<>();
        Vec3 current = start;
        Vec3 velocity = initialVelocity;
        double travelled = 0.0D;
        Landing landing = null;

        // ThrowableProjectile applies one physics step before its first move.
        if (physics.initialTickCorrection()) {
            velocity = tickVelocity(current, velocity, physics);
        }

        for (int tick = 0; tick < MAX_TICKS && travelled < range.get(); tick++) {
            if (velocity.lengthSqr() < MAX_SPEED_EPSILON && tick > 0) {
                break;
            }
            Vec3 next = current.add(velocity);
            if (next.distanceTo(start) > range.get()) {
                next = start.add(next.subtract(start).normalize().scale(range.get()));
            }
            BlockHitResult hit = mc.level.clip(new ClipContext(
                    current, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    projectile == null ? mc.player : projectile));
            Vec3 end = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : next;
            if (end.distanceToSqr(current) > 1.0E-8D) {
                segments.add(new Segment(current, end));
                travelled += current.distanceTo(end);
            }
            if (hit.getType() == HitResult.Type.BLOCK) {
                landing = new Landing(end, hit.getDirection(), hit.getBlockPos());
                break;
            }
            current = next;
            velocity = tickVelocity(current, velocity, physics);
        }
        return new PathPrediction(List.copyOf(segments), landing,
                projectile == null ? ItemStack.EMPTY : projectileItem(projectile), projectile);
    }

    private Vec3 tickVelocity(Vec3 position, Vec3 velocity, PredictionPhysics physics) {
        boolean inWater = !mc.level.getFluidState(BlockPos.containing(position)).isEmpty();
        double drag = inWater ? physics.dragInWater() : physics.drag();
        return velocity.scale(drag).add(0.0D, -physics.gravity(), 0.0D);
    }

    private PredictionPhysics physicsFor(Projectile projectile) {
        if (projectile instanceof FireworkRocketEntity) {
            return PredictionPhysics.FIREWORK;
        }
        if (projectile instanceof ThrownTrident) {
            return PredictionPhysics.TRIDENT;
        }
        if (projectile instanceof AbstractArrow) {
            return PredictionPhysics.ARROW;
        }
        if (projectile instanceof AbstractHurtingProjectile) {
            return PredictionPhysics.FIREBALL;
        }
        if (projectile instanceof ItemSupplier supplier) {
            return physicsFor(supplier.getItem());
        }
        return PredictionPhysics.GENERIC;
    }

    private PredictionPhysics physicsFor(ItemStack stack) {
        if (stack.isEmpty()) return PredictionPhysics.GENERIC;
        if (stack.getItem() == Items.EXPERIENCE_BOTTLE) {
            return PredictionPhysics.EXPERIENCE_BOTTLE;
        }
        if (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
            return PredictionPhysics.POTION;
        }
        if (stack.getItem() == Items.TRIDENT) {
            return PredictionPhysics.TRIDENT;
        }
        if (stack.getItem() == Items.CROSSBOW) {
            return PredictionPhysics.CROSSBOW;
        }
        if (stack.getItem() == Items.FISHING_ROD) {
            return PredictionPhysics.FISHING_ROD;
        }
        if (stack.getItem() == Items.FIRE_CHARGE) {
            return PredictionPhysics.FIREBALL;
        }
        if (stack.getItem() == Items.WIND_CHARGE) {
            return PredictionPhysics.WIND_CHARGE;
        }
        if (stack.getItem() == Items.FIREWORK_ROCKET) {
            return PredictionPhysics.FIREWORK;
        }
        return PredictionPhysics.GENERIC;
    }

    private ItemStack projectileItem(Projectile projectile) {
        if (projectile instanceof ItemSupplier supplier) {
            ItemStack item = supplier.getItem();
            if (!item.isEmpty()) {
                return item.copy();
            }
        }
        if (projectile instanceof ThrownTrident) return new ItemStack(Items.TRIDENT);
        if (projectile instanceof AbstractArrow) return new ItemStack(Items.ARROW);
        if (projectile instanceof AbstractHurtingProjectile) return new ItemStack(Items.FIRE_CHARGE);
        return ItemStack.EMPTY;
    }

    private void drawPredictions(Render3DEvent event, List<PathPrediction> paths) {
        if (paths.isEmpty()) return;
        PoseStack.Pose entry = event.getPoseStack().last();
        Matrix4f matrix = entry.pose();
        Vec3 camera = mc.getEntityRenderDispatcher().camera.position();
        Color selected = color.get();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
        for (PathPrediction path : paths) {
            Color lineColor = targetedPaths.contains(path) ? TARGET_COLOR : selected;
            Vec3 renderOffset = path.renderOffset();
            for (Segment segment : path.segments()) {
                Vec3 direction = segment.end().subtract(segment.start()).normalize();
                addLine(buffer, matrix, entry,
                        (float) (segment.start().x + renderOffset.x - camera.x),
                        (float) (segment.start().y + renderOffset.y - camera.y),
                        (float) (segment.start().z + renderOffset.z - camera.z),
                        (float) (segment.end().x + renderOffset.x - camera.x),
                        (float) (segment.end().y + renderOffset.y - camera.y),
                        (float) (segment.end().z + renderOffset.z - camera.z), direction, lineColor);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) Render3DUtils.LINES.draw(mesh);
        for (PathPrediction path : paths) {
            Landing landing = path.landing();
            if (landing == null) continue;
            Color markerColor = targetedPaths.contains(path) ? TARGET_COLOR : selected;
            AABB marker = markerBox(landing);
            Render3DUtils.drawFilledSide(marker, new Color(
                    markerColor.getRed(), markerColor.getGreen(), markerColor.getBlue(), 70), landing.normal());
            Render3DUtils.drawSideOutline(event.getPoseStack(), marker,
                    new Color(markerColor.getRed(), markerColor.getGreen(), markerColor.getBlue(), 230),
                    1.4F, landing.normal());
        }
    }

    private AABB markerBox(Landing landing) {
        Vec3 p = landing.position();
        double half = 0.16D;
        double thickness = 0.012D;
        return switch (landing.normal()) {
            case UP -> new AABB(p.x - half, p.y - thickness, p.z - half,
                    p.x + half, p.y + thickness, p.z + half);
            case DOWN -> new AABB(p.x - half, p.y - thickness, p.z - half,
                    p.x + half, p.y + thickness, p.z + half);
            case NORTH, SOUTH -> new AABB(p.x - half, p.y - half, p.z - thickness,
                    p.x + half, p.y + half, p.z + thickness);
            case EAST, WEST -> new AABB(p.x - thickness, p.y - half, p.z - half,
                    p.x + thickness, p.y + half, p.z + half);
        };
    }

    private static void addLine(BufferBuilder buffer, Matrix4f matrix, PoseStack.Pose entry,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                Vec3 direction, Color color) {
        buffer.addVertex(matrix, x1, y1, z1)
                .setColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .setNormal(entry, (float) direction.x, (float) direction.y, (float) direction.z)
                .setLineWidth(2.0F);
        buffer.addVertex(matrix, x2, y2, z2)
                .setColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .setNormal(entry, (float) direction.x, (float) direction.y, (float) direction.z)
                .setLineWidth(2.0F);
    }

    private record Segment(Vec3 start, Vec3 end) {
    }

    private record PredictionPhysics(double gravity, double initialVelocity, double drag,
                                     double dragInWater, boolean initialTickCorrection,
                                     boolean copiesPlayerVelocity) {
        private static final PredictionPhysics GENERIC = new PredictionPhysics(0.03D, 1.5D, 0.99D, 0.8D, true, true);
        private static final PredictionPhysics POTION = new PredictionPhysics(0.05D, 0.5D, 0.99D, 0.8D, true, true);
        private static final PredictionPhysics EXPERIENCE_BOTTLE = new PredictionPhysics(0.07D, 0.7D, 0.99D, 0.8D, true, true);
        private static final PredictionPhysics ARROW = new PredictionPhysics(0.05D, 3.0D, 0.99D, 0.6D, false, true);
        private static final PredictionPhysics BOW = ARROW;
        private static final PredictionPhysics CROSSBOW = new PredictionPhysics(0.05D, 3.15D, 0.99D, 0.6D, false, false);
        private static final PredictionPhysics FISHING_ROD = new PredictionPhysics(0.04D, 1.5D, 0.92D, 0.92D, true, true);
        private static final PredictionPhysics TRIDENT = new PredictionPhysics(0.05D, 2.5D, 0.99D, 0.99D, false, true);
        private static final PredictionPhysics FIREWORK = new PredictionPhysics(0.0D, 1.6D, 1.0D, 1.0D, false, false);
        private static final PredictionPhysics FIREBALL = new PredictionPhysics(0.0D, 1.5D, 1.0D, 1.0D, true, true);
        private static final PredictionPhysics WIND_CHARGE = new PredictionPhysics(0.0D, 1.5D, 1.0D, 1.0D, true, true);

        private PredictionPhysics withInitialVelocity(double velocity) {
            return new PredictionPhysics(gravity, velocity, drag, dragInWater,
                    initialTickCorrection, copiesPlayerVelocity);
        }
    }

    private record HeldProjectile(ItemStack stack) {
    }

    private record Landing(Vec3 position, Direction normal, BlockPos block) {
    }

    private record PathPrediction(List<Segment> segments, Landing landing,
                                  ItemStack item, Entity source, Vec3 renderOffset) {
        private PathPrediction(List<Segment> segments, Landing landing,
                               ItemStack item, Entity source) {
            this(segments, landing, item, source, Vec3.ZERO);
        }

        private PathPrediction withItem(ItemStack replacement, Entity replacementSource) {
            return new PathPrediction(segments, landing, replacement, replacementSource,
                    renderOffset);
        }

        private PathPrediction withRenderOffset(Vec3 replacementRenderOffset) {
            return new PathPrediction(segments, landing, item, source,
                    replacementRenderOffset);
        }
    }
}
