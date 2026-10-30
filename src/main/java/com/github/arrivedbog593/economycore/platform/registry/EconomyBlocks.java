package com.github.arrivedbog593.economycore.platform.registry;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.block.ExchangeBlock;
import com.github.arrivedbog593.economycore.platform.block.ShopBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EconomyBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(EconomyCore.MOD_ID);

    /** Exchanges items for credits and back, at the same rate both ways. */
    public static final DeferredBlock<ExchangeBlock> EXCHANGE = BLOCKS.registerBlock(
            "exchange",
            ExchangeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(3.5F, 9.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /** Sells goods for credits and never buys them back. */
    public static final DeferredBlock<ShopBlock> SHOP = BLOCKS.registerBlock(
            "shop",
            ShopBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_CYAN)
                    .strength(3.5F, 9.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    private EconomyBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    public static Block exchange() {
        return EXCHANGE.get();
    }

    public static Block shop() {
        return SHOP.get();
    }
}
