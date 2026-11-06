package com.github.arrivedbog593.economycore.platform;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Each player's own choices, in {@code config/economycore-client.toml}.
 * <p>
 * Kept out of the {@code client} package, which is never loaded on a
 * dedicated server: the mod registers this spec on both sides, and it holds
 * nothing but values.
 */
public final class EconomyClientConfig {

    public static final ModConfigSpec SPEC;

    /** Whether the purse shows at the side of the screen. */
    public static final ModConfigSpec.BooleanValue SHOW_PURSE;

    /** Light or dark windows, or whichever matches the resource packs. */
    public static final ModConfigSpec.EnumValue<UiTheme> THEME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("hud");
        SHOW_PURSE = builder
                .comment("Show your purse and your bank at the right of the screen, as SkyBlock's sidebar does.")
                .define("showPurse", true);
        builder.pop();
        builder.push("ui");
        THEME = builder
                .comment("How the mod's windows look. AUTO follows your resource packs: dark when they make",
                        "Minecraft's own windows dark, as a dark-mode pack does, and light otherwise.",
                        "The rest of Minecraft is darkened by a resource pack, not by this mod.")
                .defineEnum("theme", UiTheme.AUTO);
        builder.pop();
        SPEC = builder.build();
    }

    private EconomyClientConfig() {
    }
}
