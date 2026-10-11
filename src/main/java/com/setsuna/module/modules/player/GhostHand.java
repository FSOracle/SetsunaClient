package com.setsuna.module.modules.player;

import com.setsuna.event.Listen;
import com.setsuna.event.events.StartUseItemEvent;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.util.rotation.RotationUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public final class GhostHand extends Module {

    public static final GhostHand INSTANCE = new GhostHand();

    private final Set<BlockPos> visitedPositions = new ObjectOpenHashSet<>();

    private GhostHand() {
        super("Ghost Hand", Category.PLAYER);
    }

    @Listen
    private void onStartUseItem(StartUseItemEvent event) {
        if (!mc.options.keyUse.isDown()) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        HitResult normalHit = mc.player.pick(mc.player.blockInteractionRange(), partialTick, false);
        if (hasBlockEntity(at(normalHit.getLocation()))) return;

        visitedPositions.clear();
        RayMarch march = new RayMarch(
                mc.player.getEyePosition(partialTick),
                viewStep(),
                mc.player.blockInteractionRange() * 10);
        while (march.hasNext()) {
            BlockPos candidate = march.next();
            if (!visitedPositions.add(candidate) || !hasBlockEntity(candidate)) continue;
            if (activate(candidate, event)) return;
        }
    }

    private Vec3 viewStep() {
        float pitch = -(float) Math.toRadians(mc.player.getXRot());
        float yaw = -(float) Math.toRadians(mc.player.getYRot());
        return new Vec3(0.0D, 0.0D, 0.1D).xRot(pitch).yRot(yaw);
    }

    private static BlockPos at(Vec3 position) {
        return BlockPos.containing(position);
    }

    private boolean hasBlockEntity(BlockPos pos) {
        return mc.level.getBlockState(pos).hasBlockEntity();
    }

    private boolean activate(BlockPos pos, StartUseItemEvent event) {
        return activateWith(InteractionHand.MAIN_HAND, pos, event)
                || activateWith(InteractionHand.OFF_HAND, pos, event);
    }

    private boolean activateWith(InteractionHand hand, BlockPos pos, StartUseItemEvent event) {
        BlockHitResult hit = new BlockHitResult(
                pos.getCenter(),
                RotationUtils.getDirection(pos),
                pos,
                true);
        InteractionResult outcome = mc.gameMode.useItemOn(mc.player, hand, hit);
        if (!(outcome instanceof InteractionResult.Success)
                && !(outcome instanceof InteractionResult.Fail)) {
            return false;
        }

        mc.player.swing(hand);
        event.cancel();
        return true;
    }

    private static final class RayMarch {
        private final Vec3 origin;
        private final Vec3 step;
        private final double endExclusive;
        private int distance = 1;

        private RayMarch(Vec3 origin, Vec3 step, double endExclusive) {
            this.origin = origin;
            this.step = step;
            this.endExclusive = endExclusive;
        }

        private boolean hasNext() {
            return distance < endExclusive;
        }

        private BlockPos next() {
            Vec3 sample = origin.add(step.scale(distance++));
            return at(sample);
        }
    }
}
