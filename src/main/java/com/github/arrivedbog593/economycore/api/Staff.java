package com.github.arrivedbog593.economycore.api;

import com.github.arrivedbog593.economycore.platform.EconomyData;
import com.github.arrivedbog593.economycore.platform.item.AdminKeyItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * The economy's staff, for a mod that lets them do more than other players.
 * <p>
 * What each rank allows in another mod is that mod's to decide: a rank here
 * is who somebody is, not what they may do.
 */
public final class Staff {

    private Staff() {
    }

    /** This player's rank, or empty for a player who is not staff. */
    public static Optional<StaffRank> rankOf(MinecraftServer server, UUID playerId) {
        return EconomyData.get(server).rankOf(playerId);
    }

    /** Whether this is the economy's admin key, which staff use on the economy's blocks. */
    public static boolean isAdminKey(ItemStack stack) {
        return stack.getItem() instanceof AdminKeyItem;
    }
}
