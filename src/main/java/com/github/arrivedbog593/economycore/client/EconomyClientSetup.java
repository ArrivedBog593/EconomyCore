package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.registry.EconomyMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * The economy's client-only wiring.
 * <p>
 * Gated on {@link Dist#CLIENT}: a dedicated server has no screens on its
 * classpath, so loading this class there would crash before the world opened.
 * <p>
 * The exchange and the shop really are containers — they move items — so they
 * get menus, and their screens are bound to those menus here.
 */
@EventBusSubscriber(modid = EconomyCore.MOD_ID, value = Dist.CLIENT)
public final class EconomyClientSetup {

    private EconomyClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EconomyMenus.EXCHANGE.get(), ExchangeScreen::new);
        event.register(EconomyMenus.SHOP.get(), ShopScreen::new);
        event.register(EconomyMenus.ADMIN_SHOP.get(), AdminShopScreen::new);
        event.register(EconomyMenus.ADMIN_EXCHANGE.get(), AdminExchangeScreen::new);
    }
}
