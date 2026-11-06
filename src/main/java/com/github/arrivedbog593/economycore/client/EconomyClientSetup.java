package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.registry.EconomyEntities;
import com.github.arrivedbog593.economycore.platform.registry.EconomyMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The economy's client-only wiring.
 * <p>
 * Gated on {@link Dist#CLIENT}: a dedicated server has no screens on its
 * classpath, so loading this class there would crash before the world opened.
 * <p>
 * The exchange and the shop really are containers — they move items — so they
 * get menus, and their screens are bound to those menus here. So is the
 * renderer that draws the clerks who stand behind them.
 */
@EventBusSubscriber(modid = EconomyCore.MOD_ID, value = Dist.CLIENT)
public final class EconomyClientSetup {

    private EconomyClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EconomyMenus.EXCHANGE.get(), ExchangeScreen::new);
        event.register(EconomyMenus.SHOP.get(), ShopScreen::new);
        event.register(EconomyMenus.BANK.get(), BankScreen::new);
        event.register(EconomyMenus.ADMIN_SHOP.get(), AdminShopScreen::new);
        event.register(EconomyMenus.ADMIN_EXCHANGE.get(), AdminExchangeScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EconomyEntities.EXCHANGE_CLERK.get(), ClerkRenderer::new);
        event.registerEntityRenderer(EconomyEntities.SHOP_CLERK.get(), ClerkRenderer::new);
        event.registerEntityRenderer(EconomyEntities.BANKER.get(), ClerkRenderer::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.SCOREBOARD_SIDEBAR,
                ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "purse"), PurseHud::render);
    }

    /** A change of resource packs may darken or lighten vanilla's windows, which AUTO follows. */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> Palette.forgetPacks());
    }

    /** Forgets the last server's purse, so it is not shown on the next one before that one says. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPurse.forget();
    }
}
