package com.github.arrivedbog593.economycore.platform.block;

import com.github.arrivedbog593.economycore.platform.item.AdminKeyItem;
import com.github.arrivedbog593.economycore.platform.menu.Counter;
import com.github.arrivedbog593.economycore.platform.menu.MenuAnchor;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * The exchange: items in, credits out, and back again, at the prices the
 * server has set.
 */
public class ExchangeBlock extends BaseEntityBlock {

    public static final MapCodec<ExchangeBlock> CODEC = simpleCodec(ExchangeBlock::new);

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    /**
     * The counter and the cage behind it, as the model draws them, turned to
     * face each way. The cage sits at the back, so unlike the shop the
     * outline depends on which way the block faces.
     */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES.put(facing, Shapes.or(
                    turned(facing, 0, 0, 1, 16, 10, 16),
                    turned(facing, 1, 10, 8, 15, 16, 16)));
        }
    }

    /** A box drawn facing north, turned the way the blockstate turns the model. */
    private static VoxelShape turned(Direction facing, double x1, double y1, double z1,
                                     double x2, double y2, double z2) {
        return switch (facing) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public ExchangeBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ExchangeBlockEntity(pos, state);
    }

    /**
     * The administration card opens the settings screen instead of the till.
     * <p>
     * Mirrors the shop block exactly, through {@link Counter#administer}.
     */
    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, @NotNull BlockState state,
                                                       @NotNull Level level, @NotNull BlockPos pos,
                                                       @NotNull Player player,
                                                       @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        if (!(stack.getItem() instanceof AdminKeyItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        Counter.EXCHANGE.administer(serverPlayer, stack, MenuAnchor.block(level, pos, this));
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        Counter.EXCHANGE.open(serverPlayer, MenuAnchor.block(level, pos, this));
        return InteractionResult.CONSUME;
    }
}
