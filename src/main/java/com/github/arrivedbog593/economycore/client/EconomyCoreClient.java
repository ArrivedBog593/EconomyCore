package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.EconomyCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.Set;

/**
 * The mod's client-only entry point.
 * <p>
 * Gives the mod a Config button in the Mods list: NeoForge's own screen, which
 * edits {@code economycore-client.toml} anywhere, and the server's settings
 * too while a single-player world is open.
 */
@Mod(value = EconomyCore.MOD_ID, dist = Dist.CLIENT)
public final class EconomyCoreClient {

    /**
     * Left out of the screen: the bank levels, whose lines are too long for its
     * fields to show, and which the bank's own settings window edits a level
     * at a time, for the world that is open.
     */
    private static final Set<String> HIDDEN = Set.of("bankLevels", "levels");

    public EconomyCoreClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ConfigurationScreen(
                mod, parent, (context, key, element) -> HIDDEN.contains(key) ? null : element));
    }
}
