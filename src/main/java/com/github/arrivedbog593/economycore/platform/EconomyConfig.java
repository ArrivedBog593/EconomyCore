package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.engine.CreditAccount;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

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

    /** Whether players may use their bank from anywhere, with {@code /bank} and {@code /credits deposit}. */
    public static final ModConfigSpec.BooleanValue BANK_COMMAND;

    /** What a player finds in their account the first time they join this world. */
    public static final ModConfigSpec.LongValue WELCOME_BALANCE;

    /** Whether savings earn interest. Off by default: every credit it pays is new money. */
    public static final ModConfigSpec.BooleanValue INTEREST_ENABLED;

    /** How many real hours apart interest is paid. */
    public static final ModConfigSpec.IntValue INTEREST_PERIOD_HOURS;

    /** Each slice of a balance and its rate, as {@code "upTo:percent"}. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> INTEREST_TRANCHES;

    /** How many real hours one merchant's pass lasts. */
    public static final ModConfigSpec.IntValue PASS_HOURS;

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
        BANK_COMMAND = builder
                .comment("Let players use their bank from wherever they are, with /bank and with",
                        "/credits deposit and /credits withdraw. Bankers work either way: turn this off",
                        "to make players go to one.")
                .define("bankCommand", true);
        builder.pop();
        builder.push("players");
        WELCOME_BALANCE = builder
                .comment("Credits paid into a player's account the first time they join this world,",
                        "to start them off. 0 gives nothing. Players who had already joined are not paid",
                        "when it is set or raised.")
                .defineInRange("welcomeBalance", 0L, 0L, CreditAccount.MAX_BALANCE);
        builder.pop();
        builder.push("interest");
        INTEREST_ENABLED = builder
                .comment("Pay interest on what players keep in their bank. Off by default: interest is new",
                        "money, and a server that pays it watches its prices climb faster.")
                .define("enabled", false);
        INTEREST_PERIOD_HOURS = builder
                .comment("How many real hours apart it is paid, to everyone with savings, online or not.",
                        "A server that was off when it fell due pays once when it starts, not once for",
                        "every period missed.")
                .defineInRange("periodHours", 31, 1, 24 * 365);
        INTEREST_TRANCHES = builder
                .comment("Each slice of a bank balance and the rate it earns, as \"upTo:percent\". The default",
                        "pays 2% on the first 10,000,000 and 1% on what lies between that and 15,000,000;",
                        "anything above the last slice earns nothing, which caps every payment. Rates may",
                        "have decimals, such as \"1000000:0.5\". If any line cannot be read, nothing is paid.")
                .defineListAllowEmpty("tranches", List.of("10000000:2", "15000000:1"), () -> "1000:1",
                        line -> line instanceof String);
        builder.pop();
        builder.push("pass");
        PASS_HOURS = builder
                .comment("How many real hours a merchant's pass lasts once used, counting while the player is",
                        "offline. Using another adds the same again. While it lasts, the player may use /bank,",
                        "/exchange and /shop from anywhere even with them switched off, loses nothing on dying,",
                        "and other mods may give them more.")
                .defineInRange("durationHours", 96, 1, 24 * 365);
        builder.pop();
        SPEC = builder.build();
    }

    private EconomyConfig() {
    }
}
