package com.github.arrivedbog593.economycore.api;

import com.github.arrivedbog593.economycore.platform.EconomyData;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Whether a player holds an active merchant's pass, for a mod that rewards it.
 * <p>
 * A pass is an item a player uses up for so many real hours of privileges,
 * counting while they are offline. EconomyCore's own are its counters from
 * anywhere — {@code /bank}, {@code /exchange}, {@code /shop}, even switched
 * off — and keeping the purse on dying. A mod built on it can add its own,
 * the way SkyBlock's cookie opens the auction house and the bazaar from
 * anywhere: ask {@link #isActive} where the privilege is granted.
 * <p>
 * Reading only. A pass is given by using the item, and by nothing else.
 */
public final class MerchantPass {

    private MerchantPass() {
    }

    /** Whether this player's pass is running now. */
    public static boolean isActive(MinecraftServer server, UUID playerId) {
        return expiresAt(server, playerId) > System.currentTimeMillis();
    }

    /**
     * When this player's pass runs out, in milliseconds since the epoch; in
     * the past, or zero, if it is not running.
     */
    public static long expiresAt(MinecraftServer server, UUID playerId) {
        return EconomyData.get(server).passUntil(playerId);
    }
}
