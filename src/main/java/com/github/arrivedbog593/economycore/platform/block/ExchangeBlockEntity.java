package com.github.arrivedbog593.economycore.platform.block;

import com.github.arrivedbog593.economycore.platform.registry.EconomyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The exchange's presence in the world.
 * <p>
 * Holds no state at all. Deposited items live in a per-player container
 * created with the menu and returned when it closes, so two people using
 * neighboring exchanges cannot see or take each other's stack, and a server
 * restart cannot strand items inside a block.
 */
public class ExchangeBlockEntity extends BlockEntity {

    public ExchangeBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyBlockEntities.EXCHANGE.get(), pos, state);
    }
}
