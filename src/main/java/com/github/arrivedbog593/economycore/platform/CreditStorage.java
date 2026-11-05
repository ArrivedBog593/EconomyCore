package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.engine.CreditAccount;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent credit balances: one account per player, and any a mod keeps for
 * itself under an id of its own.
 * <p>
 * Stored as level data rather than on the player entity. Player NBT rides on
 * an entity that can be killed, replaced, or restored from a backup, and
 * balances traveling with it go missing in ways that are impossible to
 * explain to whoever lost them.
 * <p>
 * Every mutation calls {@link #setDirty()}. Dirty only marks data for
 * writing; the file lands when the world saves, every five minutes by
 * default. That gap is what {@link TransactionRecovery} exists to close.
 * <p>
 * Server thread only.
 */
public final class CreditStorage extends SavedData {

    private static final String DATA_NAME = "economycore_credits";

    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_SEQUENCE = "last_sequence";

    public static final SavedData.Factory<CreditStorage> FACTORY =
            new SavedData.Factory<>(CreditStorage::new, CreditStorage::load, null);

    private final Map<UUID, CreditAccount> accounts = new HashMap<>();

    /** The highest transaction sequence reflected in these balances. */
    private long lastSequence;

    private CreditStorage() {
    }

    private static CreditStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        CreditStorage storage = new CreditStorage();
        ListTag list = tag.getList(KEY_ACCOUNTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID owner = entry.getUUID(KEY_OWNER);
            long balance = entry.getLong(KEY_BALANCE);
            // Clamp rather than reject: a corrupted file should not stop the
            // server booting, and a clamped balance is visible and fixable.
            balance = Math.clamp(balance, 0, CreditAccount.MAX_BALANCE);
            storage.accounts.put(owner, new CreditAccount(owner, balance));
        }
        storage.lastSequence = Math.max(0, tag.getLong(KEY_SEQUENCE));
        return storage;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        for (CreditAccount account : accounts.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_OWNER, account.owner());
            entry.putLong(KEY_BALANCE, account.balance());
            list.add(entry);
        }
        tag.put(KEY_ACCOUNTS, list);
        tag.putLong(KEY_SEQUENCE, lastSequence);
        return tag;
    }

    public static CreditStorage get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // --- Player accounts ---------------------------------------------------

    public CreditAccount accountOf(UUID playerId) {
        return accounts.computeIfAbsent(playerId, CreditAccount::empty);
    }

    /**
     * Whether the economy has ever touched this player's account here: a
     * deposit, a withdrawal or a transfer, even one that failed, since trying
     * opens the account.
     */
    public boolean hasAccount(UUID playerId) {
        return accounts.containsKey(playerId);
    }

    public long balanceOf(UUID playerId) {
        CreditAccount account = accounts.get(playerId);
        return account == null ? 0 : account.balance();
    }

    /** @return false if the balance cap would be breached; nothing changes */
    public boolean deposit(UUID playerId, long amount) {
        boolean done = accountOf(playerId).deposit(amount);
        if (done) {
            setDirty();
        }
        return done;
    }

    /** @return false if they cannot afford it; nothing changes */
    public boolean withdraw(UUID playerId, long amount) {
        boolean done = accountOf(playerId).withdraw(amount);
        if (done) {
            setDirty();
        }
        return done;
    }

    public boolean transfer(UUID from, UUID to, long amount) {
        boolean done = CreditAccount.transfer(accountOf(from), accountOf(to), amount);
        if (done) {
            setDirty();
        }
        return done;
    }

    /**
     * Overwrites a balance outright.
     * <p>
     * Only for {@link TransactionRecovery} and operator commands. Ordinary
     * code must go through deposit and withdraw, so the cap and overdraft
     * checks apply.
     */
    void setBalance(UUID playerId, long balance) {
        long clamped = Math.clamp(balance, 0, CreditAccount.MAX_BALANCE);
        accounts.put(playerId, new CreditAccount(playerId, clamped));
        setDirty();
    }

    // --- Transaction sequence -------------------------------------------------

    public long lastSequence() {
        return lastSequence;
    }

    public void setLastSequence(long sequence) {
        if (sequence <= lastSequence) {
            return;
        }
        this.lastSequence = sequence;
        setDirty();
    }

}