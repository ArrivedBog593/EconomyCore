package com.github.arrivedbog593.economycore.platform.registry;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.block.ExchangeBlockEntity;
import com.github.arrivedbog593.economycore.platform.block.ShopBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EconomyBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EconomyCore.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExchangeBlockEntity>> EXCHANGE =
            BLOCK_ENTITIES.register("exchange", () -> BlockEntityType.Builder
                    .of(ExchangeBlockEntity::new, EconomyBlocks.exchange())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopBlockEntity>> SHOP =
            BLOCK_ENTITIES.register("shop", () -> BlockEntityType.Builder
                    .of(ShopBlockEntity::new, EconomyBlocks.shop())
                    .build(null));

    private EconomyBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
