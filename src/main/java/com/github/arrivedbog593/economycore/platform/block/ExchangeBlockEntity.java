package com.github.arrivedbog593.economycore.platform.block;

import com.github.arrivedbog593.economycore.platform.menu.ExchangeMenu;
import com.github.arrivedbog593.economycore.platform.registry.EconomyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The exchange's presence in the world.
 * <p>
 * Holds no state at all. Deposited items live in a per-player container
 * created with the menu and returned when it closes, so two people using
 * neighboring exchanges cannot see or take each other's stack, and a server
 * restart cannot strand items inside a block.
 */
public class ExchangeBlockEntity extends BlockEntity implements MenuProvider {

    public ExchangeBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyBlockEntities.EXCHANGE.get(), pos, state);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.economycore.exchange");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player player) {
        return new ExchangeMenu(containerId, inventory, getBlockPos());
    }
}
