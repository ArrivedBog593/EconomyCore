package com.github.arrivedbog593.economycore.platform.block;

import com.github.arrivedbog593.economycore.platform.registry.EconomyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shop's presence in the world.
 * <p>
 * Stateless. Stock is unlimited on purpose: the shop exists to absorb credits
 * rather than to simulate a supply chain, and a shop that runs out stops
 * doing its one job at exactly the moment the economy needs it most.
 */
public class ShopBlockEntity extends BlockEntity {

    public ShopBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyBlockEntities.SHOP.get(), pos, state);
    }
}
