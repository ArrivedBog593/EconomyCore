package com.github.arrivedbog593.economycore;

import com.github.arrivedbog593.economycore.platform.EconomyClientConfig;
import com.github.arrivedbog593.economycore.platform.EconomyConfig;
import com.github.arrivedbog593.economycore.platform.command.CounterCommands;
import com.github.arrivedbog593.economycore.platform.registry.EconomyBlockEntities;
import com.github.arrivedbog593.economycore.platform.registry.EconomyBlocks;
import com.github.arrivedbog593.economycore.platform.registry.EconomyCreativeTabs;
import com.github.arrivedbog593.economycore.platform.registry.EconomyEntities;
import com.github.arrivedbog593.economycore.platform.registry.EconomyItems;
import com.github.arrivedbog593.economycore.platform.registry.EconomyMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;

/**
 * Mod entry point.
 * <p>
 * EconomyCore is the money other mods build on: every player's account in
 * credits, the exchange that turns any item into credits and back, the shop,
 * and the staff list that may run them. It knows nothing about what those
 * mods do with it — a casino, an auction house — and takes nothing from
 * them; they depend on it, never the other way round.
 * <p>
 * Deliberately thin. Lifecycle wiring lives with the systems it belongs to —
 * see {@code platform.EconomyEvents} and {@code platform.network.EconomyPayloads}.
 */
@Mod(EconomyCore.MOD_ID)
public final class EconomyCore {

    /**
     * Registry namespace and assets folder. Changing it breaks saved worlds
     * and every mod and datapack written against this one.
     */
    public static final String MOD_ID = "economycore";

    @SuppressWarnings("unused")
    public EconomyCore(IEventBus modEventBus, ModContainer container) {
        EconomyBlocks.register(modEventBus);
        EconomyItems.register(modEventBus);
        EconomyEntities.register(modEventBus);
        EconomyBlockEntities.register(modEventBus);
        EconomyMenus.register(modEventBus);
        EconomyCreativeTabs.register(modEventBus);
        container.registerConfig(ModConfig.Type.SERVER, EconomyConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, EconomyClientConfig.SPEC);
        modEventBus.addListener(CounterCommands::onConfigReloaded);
    }
}
