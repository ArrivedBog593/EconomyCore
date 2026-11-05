package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The economy's network channel: the exchange, the shop and the bank.
 * <p>
 * Versioned on its own, apart from the games, because it will ship apart from
 * them. Bump it whenever one of these payloads changes shape, so an
 * out-of-date client is refused at login rather than misreading a catalog.
 */
@EventBusSubscriber(modid = EconomyCore.MOD_ID)
public final class EconomyPayloads {

    private static final String VERSION = "2";

    private EconomyPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToClient(
                ExchangeCatalogPayload.TYPE,
                ExchangeCatalogPayload.STREAM_CODEC,
                ExchangeCatalogPayload::handleOnClient);

        registrar.playToClient(
                ShopCatalogPayload.TYPE,
                ShopCatalogPayload.STREAM_CODEC,
                ShopCatalogPayload::handleOnClient);

        registrar.playToServer(
                ShopPurchasePayload.TYPE,
                ShopPurchasePayload.STREAM_CODEC,
                ShopPurchasePayload::handleOnServer);

        registrar.playToServer(
                ExchangeRedeemPayload.TYPE,
                ExchangeRedeemPayload.STREAM_CODEC,
                ExchangeRedeemPayload::handleOnServer);

        registrar.playToServer(
                BankActionPayload.TYPE,
                BankActionPayload.STREAM_CODEC,
                BankActionPayload::handleOnServer);

        registrar.playToServer(
                ExchangeConvertPayload.TYPE,
                ExchangeConvertPayload.STREAM_CODEC,
                ExchangeConvertPayload::handleOnServer);

        registrar.playToServer(
                AdminShopActionPayload.TYPE,
                AdminShopActionPayload.STREAM_CODEC,
                AdminShopActionPayload::handleOnServer);

        registrar.playToServer(
                AdminExchangeActionPayload.TYPE,
                AdminExchangeActionPayload.STREAM_CODEC,
                AdminExchangeActionPayload::handleOnServer);

        registrar.playToServer(
                AdminExchangeBatchPayload.TYPE,
                AdminExchangeBatchPayload.STREAM_CODEC,
                AdminExchangeBatchPayload::handleOnServer);
    }
}
