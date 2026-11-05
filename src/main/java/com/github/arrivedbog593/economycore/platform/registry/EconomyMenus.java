package com.github.arrivedbog593.economycore.platform.registry;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.menu.AdminExchangeMenu;
import com.github.arrivedbog593.economycore.platform.menu.AdminShopMenu;
import com.github.arrivedbog593.economycore.platform.menu.BankMenu;
import com.github.arrivedbog593.economycore.platform.menu.ExchangeMenu;
import com.github.arrivedbog593.economycore.platform.menu.ShopMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EconomyMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, EconomyCore.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ExchangeMenu>> EXCHANGE =
            MENUS.register("exchange", () -> IMenuTypeExtension.create(ExchangeMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> SHOP =
            MENUS.register("shop", () -> IMenuTypeExtension.create(ShopMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<BankMenu>> BANK =
            MENUS.register("bank", () -> IMenuTypeExtension.create(BankMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AdminShopMenu>> ADMIN_SHOP =
            MENUS.register("admin_shop", () -> IMenuTypeExtension.create(AdminShopMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AdminExchangeMenu>> ADMIN_EXCHANGE =
            MENUS.register("admin_exchange", () -> IMenuTypeExtension.create(AdminExchangeMenu::new));

    private EconomyMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
