package com.github.arrivedbog593.economycore.platform;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * What a server owner decides about the economy, per world.
 * <p>
 * A server config: {@code config/economycore-server.toml} on the server,
 * loaded when a world starts. A copy in a world's {@code serverconfig} folder
 * overrides it for that world alone. Read only while a server is running;
 * asking before a world has loaded it throws.
 */
public final class EconomyConfig {

    public static final ModConfigSpec SPEC;

    /** Whether the server bank exists even when no installed mod asks for one. */
    public static final ModConfigSpec.BooleanValue BANK_ALWAYS_ON;

    /** Whether the server's income goes to the bank while it exists, or leaves circulation anyway. */
    public static final ModConfigSpec.BooleanValue INCOME_TO_BANK;

    /** Whether players may open the exchange from anywhere with {@code /exchange}. */
    public static final ModConfigSpec.BooleanValue EXCHANGE_COMMAND;

    /** Whether players may open the shop from anywhere with {@code /shop}. */
    public static final ModConfigSpec.BooleanValue SHOP_COMMAND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("bank");
        BANK_ALWAYS_ON = builder
                .comment("Keep the server bank switched on even when no installed mod asks for it.",
                        "Mods that pay out of the bank, such as a casino, switch it on by themselves.")
                .define("alwaysOn", false);
        INCOME_TO_BANK = builder
                .comment("While the bank is on, what the server takes in - shop sales, the exchange's",
                        "buyback surcharge, fees other mods charge - is paid into it. Set this to false",
                        "to have it leave circulation instead, as it does when there is no bank.")
                .define("incomeToBank", true);
        builder.pop();
        builder.push("access");
        EXCHANGE_COMMAND = builder
                .comment("Let players open the exchange from wherever they are with /exchange.",
                        "Exchange blocks and clerks work either way: turn this off to make players",
                        "go to one.")
                .define("exchangeCommand", true);
        SHOP_COMMAND = builder
                .comment("Let players open the shop from wherever they are with /shop.",
                        "Shop blocks and clerks work either way: turn this off to make players go to one.")
                .define("shopCommand", true);
        builder.pop();
        SPEC = builder.build();
    }

    private EconomyConfig() {
    }
}
