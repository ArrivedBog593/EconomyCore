package com.github.arrivedbog593.economycore.platform.registry;

import com.github.arrivedbog593.economycore.EconomyCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EconomyCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EconomyCore.MOD_ID);

    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.economycore"))
                    .icon(() -> new ItemStack(EconomyItems.EXCHANGE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(EconomyItems.EXCHANGE.get());
                        output.accept(EconomyItems.SHOP.get());
                        output.accept(EconomyItems.ATM.get());
                        output.accept(EconomyItems.EXCHANGE_CLERK_SPAWN_EGG.get());
                        output.accept(EconomyItems.SHOP_CLERK_SPAWN_EGG.get());
                        output.accept(EconomyItems.BANKER_SPAWN_EGG.get());
                        output.accept(EconomyItems.MERCHANT_PASS.get());
                        // Unbound, so it works for operators and nobody else.
                        // Anyone administering without operator rights gets a
                        // bound one from the command instead.
                        output.accept(EconomyItems.ADMIN_KEY.get());
                    })
                    .build());

    private EconomyCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
