package com.setsuna.util.player;

import com.setsuna.util.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

public final class PlayerUtils {

    private static final double COLLISION_INSET = 1.0E-6D;
    private static final Minecraft mc = Minecraft.getInstance();

    private PlayerUtils() {
    }

    public static boolean isEating() {
        boolean foodInHands = carriesFood(mc.player.getMainHandItem())
                || carriesFood(mc.player.getOffhandItem());
        return foodInHands && mc.player.isUsingItem();
    }

    public static boolean isInWeb() {
        return touchesBlock(position ->
                mc.level.getBlockState(position).getBlock() instanceof WebBlock);
    }

    public static boolean isInBlock() {
        return touchesBlock(BlockUtils::isSolidBlock);
    }

    private static boolean carriesFood(ItemStack stack) {
        return stack.getComponents().has(DataComponents.FOOD);
    }

    private static boolean touchesBlock(Predicate<BlockPos> blockMatcher) {
        BlockVolume occupied = BlockVolume.from(mc.player.getBoundingBox().deflate(COLLISION_INSET));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int blockX = occupied.minX(); blockX <= occupied.maxX(); blockX++) {
            for (int blockY = occupied.minY(); blockY <= occupied.maxY(); blockY++) {
                for (int blockZ = occupied.minZ(); blockZ <= occupied.maxZ(); blockZ++) {
                    cursor.set(blockX, blockY, blockZ);
                    if (blockMatcher.test(cursor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private record BlockVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        private static BlockVolume from(AABB box) {
            return new BlockVolume(
                    Mth.floor(box.minX),
                    Mth.floor(box.minY),
                    Mth.floor(box.minZ),
                    Mth.floor(box.maxX),
                    Mth.floor(box.maxY),
                    Mth.floor(box.maxZ)
            );
        }
    }
}
