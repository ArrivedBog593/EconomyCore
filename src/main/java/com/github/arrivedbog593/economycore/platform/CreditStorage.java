package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.api.Pocket;
import com.github.arrivedbog593.economycore.engine.BankMovement;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent credit balances: one account per player, and any a mod keeps for
 * itself under an id of its own.
 * <p>
 * A player's account has two pockets, a purse and savings (see
 * {@link Pocket}). Each is a {@link CreditAccount} of its own, with its own
 * cap, and the methods that name no pocket mean the purse, which is what
 * everything that spends uses. Only players have savings; the accounts mods
 * keep, the server bank's among them, live in the purse alone.
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

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DATA_NAME = "economycore_credits";

    /**
     * The shape of the file this version writes. Raised whenever that shape
     * changes, with a step in {@link #migrate} that brings the one before up
     * to it.
     * <ol start="0">
     *   <li>No version written: one balance per account.</li>
     *   <li>Players' savings beside the purse. The purse keeps the key the
     *       balance had, so a version from before savings still reads every
     *       purse, and loses only what was saved.</li>
     * </ol>
     */
    static final int DATA_VERSION = 1;

    private static final String KEY_DATA_VERSION = "data_version";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_SAVINGS = "savings";
    private static final String KEY_SEQUENCE = "last_sequence";
    private static final String KEY_RECENT = "recent";
    private static final String KEY_TIME = "t";
    private static final String KEY_KIND = "k";
    private static final String KEY_AMOUNT = "a";

    public static final SavedData.Factory<CreditStorage> FACTORY =
            new SavedData.Factory<>(CreditStorage::new, CreditStorage::load, null);

    private final Map<UUID, CreditAccount> accounts = new HashMap<>();
    private final Map<UUID, CreditAccount> savings = new HashMap<>();

    /** Each player's last few bank movements, newest last, for the banker to show. */
    private final Map<UUID, Deque<BankMovement>> recent = new HashMap<>();

    /**
     * Accounts that moved since anyone last asked, so the purse on players'
     * screens can be refreshed for them alone. Not saved: a restart sends
     * everybody theirs when they join.
     */
    private final Set<UUID> touched = new LinkedHashSet<>();

    /** The highest transaction sequence reflected in these balances. */
    private long lastSequence;

    private CreditStorage() {
    }

    private static CreditStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.getInt(KEY_DATA_VERSION);
        if (version > DATA_VERSION) {
            // Written by a newer EconomyCore. Read what this one understands
            // rather than refuse to boot, but say so: whatever only the newer
            // version knows about is dropped at the next save.
            LOGGER.error("[Economy] {}.dat was written by a newer version of EconomyCore "
                            + "(data version {}, this one knows up to {}). What this version does "
                            + "not understand will be lost when the world saves.",
                    DATA_NAME, version, DATA_VERSION);
        }
        migrate(tag, version);

        CreditStorage storage = new CreditStorage();
        ListTag list = tag.getList(KEY_ACCOUNTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID owner = entry.getUUID(KEY_OWNER);
            // Clamp rather than reject: a corrupted file should not stop the
            // server booting, and a clamped balance is visible and fixable.
            long balance = Math.clamp(entry.getLong(KEY_BALANCE), 0, CreditAccount.MAX_BALANCE);
            storage.accounts.put(owner, new CreditAccount(owner, balance));
            long saved = Math.clamp(entry.getLong(KEY_SAVINGS), 0, CreditAccount.MAX_BALANCE);
            if (saved > 0) {
                storage.savings.put(owner, new CreditAccount(owner, saved));
            }
            ListTag history = entry.getList(KEY_RECENT, Tag.TAG_COMPOUND);
            for (int j = 0; j < history.size(); j++) {
                CompoundTag line = history.getCompound(j);
                storage.remember(owner, new BankMovement(line.getLong(KEY_TIME),
                        BankMovement.Kind.byOrdinal(line.getInt(KEY_KIND)), line.getLong(KEY_AMOUNT)));
            }
        }
        storage.lastSequence = Math.max(0, tag.getLong(KEY_SEQUENCE));
        return storage;
    }

    /**
     * Brings a file written at an older data version up to this one, step by
     * step, in place.
     * <p>
     * Every step stays here for good: a world can skip any number of
     * releases, and a step that is gone leaves it unreadable.
     */
    private static void migrate(CompoundTag tag, int from) {
        if (from < 1 && tag.contains(KEY_ACCOUNTS)) {
            // Savings arrived. Nobody had any, which an absent key already
            // says: nothing to rewrite.
            LOGGER.info("[Economy] Bringing {}.dat up to data version 1: every balance stays "
                    + "in the purse, and savings start empty.", DATA_NAME);
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        Set<UUID> owners = new LinkedHashSet<>(accounts.keySet());
        owners.addAll(savings.keySet());
        ListTag list = new ListTag();
        for (UUID owner : owners) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_OWNER, owner);
            entry.putLong(KEY_BALANCE, balanceOf(owner));
            long saved = balanceOf(owner, Pocket.SAVINGS);
            if (saved > 0) {
                entry.putLong(KEY_SAVINGS, saved);
            }
            Deque<BankMovement> history = recent.get(owner);
            if (history != null && !history.isEmpty()) {
                ListTag lines = new ListTag();
                for (BankMovement movement : history) {
                    CompoundTag line = new CompoundTag();
                    line.putLong(KEY_TIME, movement.time());
                    line.putInt(KEY_KIND, movement.kind().ordinal());
                    line.putLong(KEY_AMOUNT, movement.amount());
                    lines.add(line);
                }
                entry.put(KEY_RECENT, lines);
            }
            list.add(entry);
        }
        tag.putInt(KEY_DATA_VERSION, DATA_VERSION);
        tag.put(KEY_ACCOUNTS, list);
        tag.putLong(KEY_SEQUENCE, lastSequence);
        return tag;
    }

    public static CreditStorage get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // --- Player accounts ---------------------------------------------------

    /** The purse. */
    public CreditAccount accountOf(UUID playerId) {
        return accountOf(playerId, Pocket.PURSE);
    }

    public CreditAccount accountOf(UUID playerId, Pocket pocket) {
        return pocketOf(pocket).computeIfAbsent(playerId, CreditAccount::empty);
    }

    /**
     * Whether the economy has ever touched this player's account here: a
     * deposit, a withdrawal or a transfer, even one that failed, since trying
     * opens the account.
     */
    public boolean hasAccount(UUID playerId) {
        return accounts.containsKey(playerId) || savings.containsKey(playerId);
    }

    /** What the purse holds. */
    public long balanceOf(UUID playerId) {
        return balanceOf(playerId, Pocket.PURSE);
    }

    public long balanceOf(UUID playerId, Pocket pocket) {
        CreditAccount account = pocketOf(pocket).get(playerId);
        return account == null ? 0 : account.balance();
    }

    /** Into the purse. */
    public boolean deposit(UUID playerId, long amount) {
        return deposit(playerId, Pocket.PURSE, amount);
    }

    /** @return false if the balance cap would be breached; nothing changes */
    public boolean deposit(UUID playerId, Pocket pocket, long amount) {
        boolean done = accountOf(playerId, pocket).deposit(amount);
        if (done) {
            touched.add(playerId);
            setDirty();
        }
        return done;
    }

    /** Out of the purse. */
    public boolean withdraw(UUID playerId, long amount) {
        return withdraw(playerId, Pocket.PURSE, amount);
    }

    /** @return false if they cannot afford it; nothing changes */
    public boolean withdraw(UUID playerId, Pocket pocket, long amount) {
        boolean done = accountOf(playerId, pocket).withdraw(amount);
        if (done) {
            touched.add(playerId);
            setDirty();
        }
        return done;
    }

    /** Between two purses. */
    public boolean transfer(UUID from, UUID to, long amount) {
        boolean done = CreditAccount.transfer(accountOf(from), accountOf(to), amount);
        if (done) {
            touched.add(from);
            touched.add(to);
            setDirty();
        }
        return done;
    }

    /**
     * Overwrites a purse outright.
     * <p>
     * Only for {@link TransactionRecovery} and operator commands. Ordinary
     * code must go through deposit and withdraw, so the cap and overdraft
     * checks apply.
     */
    void setBalance(UUID playerId, long balance) {
        setBalance(playerId, Pocket.PURSE, balance);
    }

    /** Overwrites either pocket outright, on the same terms as {@link #setBalance(UUID, long)}. */
    void setBalance(UUID playerId, Pocket pocket, long balance) {
        long clamped = Math.clamp(balance, 0, CreditAccount.MAX_BALANCE);
        pocketOf(pocket).put(playerId, new CreditAccount(playerId, clamped));
        touched.add(playerId);
        setDirty();
    }

    /** Every player with anything in savings, for paying interest. */
    public List<UUID> savers() {
        List<UUID> savers = new ArrayList<>();
        savings.forEach((owner, account) -> {
            if (account.balance() > 0) {
                savers.add(owner);
            }
        });
        return savers;
    }

    /** The accounts that moved since this was last asked, and forgets them. */
    public Set<UUID> drainTouched() {
        Set<UUID> drained = Set.copyOf(touched);
        touched.clear();
        return drained;
    }

    // --- Recent bank movements ------------------------------------------------

    /** Notes a deposit, withdrawal or interest payment for the banker to show. */
    public void recordBankMovement(UUID playerId, BankMovement movement) {
        remember(playerId, movement);
        setDirty();
    }

    /** This player's last few bank movements, oldest first. */
    public List<BankMovement> recentBankMovements(UUID playerId) {
        Deque<BankMovement> history = recent.get(playerId);
        return history == null ? List.of() : List.copyOf(history);
    }

    private void remember(UUID playerId, BankMovement movement) {
        Deque<BankMovement> history = recent.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        history.addLast(movement);
        while (history.size() > BankMovement.KEPT) {
            history.removeFirst();
        }
    }

    private Map<UUID, CreditAccount> pocketOf(Pocket pocket) {
        return pocket == Pocket.SAVINGS ? savings : accounts;
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
