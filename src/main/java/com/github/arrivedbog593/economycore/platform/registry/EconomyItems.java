package com.github.arrivedbog593.economycore.platform.registry;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.item.AdminKeyItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EconomyItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(EconomyCore.MOD_ID);

    public static final DeferredItem<BlockItem> EXCHANGE =
            ITEMS.registerSimpleBlockItem("exchange", EconomyBlocks.EXCHANGE, new Item.Properties());

    public static final DeferredItem<BlockItem> SHOP =
            ITEMS.registerSimpleBlockItem("shop", EconomyBlocks.SHOP, new Item.Properties());

    /**
     * Clerks, put down like any mob. No staff rank asked: an egg in survival
     * only ever comes out of creative, and whoever has creative can already
     * place the blocks they stand in for.
     */
    public static final DeferredItem<DeferredSpawnEggItem> EXCHANGE_CLERK_SPAWN_EGG =
            ITEMS.register("exchange_clerk_spawn_egg", () -> new DeferredSpawnEggItem(
                    EconomyEntities.EXCHANGE_CLERK, 0xC9A227, 0x2B2B2B, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> SHOP_CLERK_SPAWN_EGG =
            ITEMS.register("shop_clerk_spawn_egg", () -> new DeferredSpawnEggItem(
                    EconomyEntities.SHOP_CLERK, 0x3C8D4F, 0xE8E0C8, new Item.Properties()));

    /**
     * Opens a casino block's settings instead of using it.
     * <p>
     * Deliberately not craftable. It is issued with a command, bound to the
     * player it is issued to, and useless to anybody else — a recipe would
     * make all of that decoration.
     * <p>
     * Here rather than with the tables because what it proves is a staff
     * rank, and the staff list is the economy's. The tables accept it too:
     * they depend on the economy, so they may.
     */
    public static final DeferredItem<AdminKeyItem> ADMIN_KEY =
            ITEMS.register("admin_key", () -> new AdminKeyItem(new Item.Properties()));

    private EconomyItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
