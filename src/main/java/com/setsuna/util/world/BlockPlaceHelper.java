package com.setsuna.util.world;

import com.setsuna.Setsuna;
import com.setsuna.util.player.FindItemResult;
import com.setsuna.util.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockPlaceHelper {

    private static final Minecraft mc = Setsuna.mc();

    private BlockPlaceHelper() {
    }

    public static FindItemResult findHotbar(Item... items) {
        return InvUtils.findInHotbar(items);
    }

    public static PlaceInfo findPlaceInfo(BlockPos placePosition, boolean strictRaycast) {
        if (mc.player == null || mc.level == null || !BlockUtils.canPlaceAt(placePosition)) {
            return null;
        }

        for (Direction face : Direction.values()) {
            BlockPos support = placePosition.relative(face.getOpposite());
            if (!isValidSupport(support)) {
                continue;
            }

            Vec3 hitPosition = Vec3.atCenterOf(support).add(
                    face.getStepX() * 0.5,
                    face.getStepY() * 0.5,
                    face.getStepZ() * 0.5);
            if (isReachable(hitPosition)
                    && (!strictRaycast || canRaycastHit(support, hitPosition))) {
                return new PlaceInfo(placePosition, support, face, hitPosition);
            }
        }
        return null;
    }

    public static boolean place(PlaceInfo info, FindItemResult item, boolean silent, boolean swing) {
        return place(info, item, silent, swing, true);
    }

    public static boolean place(PlaceInfo info, FindItemResult item, boolean silent,
                                boolean swing, boolean checkEntities) {
        if (info == null || item == null || !item.found()
                || mc.player == null || mc.level == null || mc.gameMode == null) {
            return false;
        }
        if (checkEntities) {
            if (!BlockUtils.canPlaceAt(info.placePosition())) {
                return false;
            }
        } else if (!mc.level.getBlockState(info.placePosition()).canBeReplaced()) {
            return false;
        }

        InteractionHand hand = item.getHand();
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        boolean swapped = hand == InteractionHand.MAIN_HAND && oldSlot != item.slot();
        if (swapped) {
            InvUtils.swap(item.slot(), silent);
        }

        try {
            BlockHitResult hit = new BlockHitResult(
                    info.hitPosition(), info.face(), info.support(), false);
            InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
            if (!result.consumesAction()) {
                return false;
            }
            if (swing) {
                mc.player.swing(hand);
            } else if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundSwingPacket(hand));
            }
            return true;
        } finally {
            if (swapped) {
                InvUtils.swapBack();
            }
        }
    }

    private static boolean isValidSupport(BlockPos position) {
        return !mc.level.getBlockState(position).canBeReplaced()
                && !mc.level.getBlockState(position).getCollisionShape(mc.level, position).isEmpty();
    }

    private static boolean isReachable(Vec3 hitPosition) {
        double range = mc.player.blockInteractionRange();
        return mc.player.getEyePosition().distanceToSqr(hitPosition) <= range * range;
    }

    private static boolean canRaycastHit(BlockPos support, Vec3 hitPosition) {
        HitResult result = mc.level.clip(new ClipContext(
                mc.player.getEyePosition(),
                hitPosition,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player));
        return result instanceof BlockHitResult blockHit
                && result.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(support);
    }

    public record PlaceInfo(
            BlockPos placePosition,
            BlockPos support,
            Direction face,
            Vec3 hitPosition) {
    }
}
