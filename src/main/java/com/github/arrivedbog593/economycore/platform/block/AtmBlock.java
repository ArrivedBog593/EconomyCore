package com.github.arrivedbog593.economycore.platform.block;

import com.github.arrivedbog593.economycore.platform.menu.Counter;
import com.github.arrivedbog593.economycore.platform.menu.MenuAnchor;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * A cash machine: a player's own bank, opened where it stands.
 * <p>
 * The bank's third door, beside the banker and {@code /bank}, as the exchange
 * and the shop each have a block, a clerk and a command. It opens the same
 * window the banker does, and stays open only while the player is in reach,
 * so a server that switches {@code /bank} off can scatter these instead of
 * staffing a bank.
 * <p>
 * No block entity: unlike the exchange and the shop it holds nothing and
 * remembers nothing, and the window it opens keeps its own state. A staff key
 * does nothing different here, as the bank has no settings.
 */
public class AtmBlock extends Block {

    public static final MapCodec<AtmBlock> CODEC = simpleCodec(AtmBlock::new);

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    /**
     * The pedestal and the hood over the screen, as the model draws them.
     * Both are centred front to back, so the outline is the same whichever
     * way the machine faces.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 3, 14, 10, 13),
            Block.box(1, 10, 2, 15, 16, 14));

    public AtmBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Screen towards whoever put it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
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
        Counter.BANK.open(serverPlayer, MenuAnchor.block(level, pos, this));
        return InteractionResult.CONSUME;
    }
}
