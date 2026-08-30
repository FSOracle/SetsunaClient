/*
 * This file is part of SetsunaVia - https://github.com/ViaVersion/SetsunaVia
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.setsunavia.injection.mixin.features.block.shape;

import com.viaversion.setsunavia.protocoltranslator.ProtocolTranslator;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonHeadBlock.class)
public abstract class MixinPistonHeadBlock extends DirectionalBlock {

    @Unique
    private static final VoxelShape setsunaVia$east_head_shape = Block.box(12.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape setsunaVia$west_head_shape = Block.box(0.0, 0.0, 0.0, 4.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape setsunaVia$south_head_shape = Block.box(0.0, 0.0, 12.0, 16.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape setsunaVia$north_head_shape = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 4.0);

    @Unique
    private static final VoxelShape setsunaVia$up_head_shape = Block.box(0.0, 12.0, 0.0, 16.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape setsunaVia$down_head_shape = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);

    @Unique
    private static final VoxelShape setsunaVia$up_arm_shape_r1_8_x = Block.box(6.0, 0.0, 6.0, 10.0, 12.0, 10.0);

    @Unique
    private static final VoxelShape setsunaVia$down_arm_shape_r1_8_x = Block.box(6.0, 4.0, 6.0, 10.0, 16.0, 10.0);

    @Unique
    private static final VoxelShape setsunaVia$south_arm_shape_r1_8_x = Block.box(4.0, 6.0, 0.0, 12.0, 10.0, 12.0);

    @Unique
    private static final VoxelShape setsunaVia$north_arm_shape_r1_8_x = Block.box(4.0, 6.0, 4.0, 12.0, 10.0, 16.0);

    @Unique
    private static final VoxelShape setsunaVia$east_arm_shape_r1_8_x = Block.box(0.0, 6.0, 4.0, 12.0, 10.0, 12.0);

    @Unique
    private static final VoxelShape setsunaVia$west_arm_shape_r1_8_x = Block.box(6.0, 4.0, 4.0, 10.0, 12.0, 16.0);

    @Unique
    private boolean setsunaVia$selfInflicted = false;

    protected MixinPistonHeadBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (setsunaVia$selfInflicted) {
            setsunaVia$selfInflicted = false;
            return;
        }
        // Outline shape for piston head doesn't exist in <= 1.12.2
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(switch (state.getValue(PistonHeadBlock.FACING)) {
                case DOWN -> setsunaVia$down_head_shape;
                case UP -> setsunaVia$up_head_shape;
                case NORTH -> setsunaVia$north_head_shape;
                case SOUTH -> setsunaVia$south_head_shape;
                case WEST -> setsunaVia$west_head_shape;
                case EAST -> setsunaVia$east_head_shape;
            });
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return switch (state.getValue(PistonHeadBlock.FACING)) {
                case DOWN -> Shapes.or(setsunaVia$down_head_shape, setsunaVia$down_arm_shape_r1_8_x);
                case UP -> Shapes.or(setsunaVia$up_head_shape, setsunaVia$up_arm_shape_r1_8_x);
                case NORTH -> Shapes.or(setsunaVia$north_head_shape, setsunaVia$north_arm_shape_r1_8_x);
                case SOUTH -> Shapes.or(setsunaVia$south_head_shape, setsunaVia$south_arm_shape_r1_8_x);
                case WEST -> Shapes.or(setsunaVia$west_head_shape, setsunaVia$west_arm_shape_r1_8_x);
                case EAST -> Shapes.or(setsunaVia$east_head_shape, setsunaVia$east_arm_shape_r1_8_x);
            };
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            // Collision shape for piston head in <= 1.12.2 needs to be the 1.13+ outline shape
            setsunaVia$selfInflicted = true;
            return getShape(state, world, pos, context);
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

}
