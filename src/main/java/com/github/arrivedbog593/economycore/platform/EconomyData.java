package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.api.StaffRank;
import com.github.arrivedbog593.economycore.engine.CreditValueTable;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Conversion values, shop prices and the staff list, edited in game and saved
 * with the world.
 * <p>
 * Kept apart from {@link CreditStorage} on purpose. Balances change many
 * times a minute while prices change a few times a month, and splitting them
 * means a corrupt balance file cannot take the whole price list with it.
 * <p>
 * Datapack JSON still works and acts as the default. Anything set with a
 * command overrides it because someone standing in the world with the item
 * in hand has more current intent than a file written once.
 * <p>
 * Server thread only.
 */
public final class EconomyData extends SavedData {

    private static final String DATA_NAME = "economycore_data";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The shape of the file this version writes, raised whenever that shape
     * changes. Older shapes are read where the data they hold is read, and
     * stay readable for good.
     * <ol start="0">
     *   <li>No version written. The shop may still be in its first format, item
     *       ids against prices, which {@link #readShop} brings forward.</li>
     *   <li>The version is written, and nothing else changed.</li>
     * </ol>
     */
    static final int DATA_VERSION = 1;

    private static final String KEY_DATA_VERSION = "data_version";
    private static final String KEY_CONVERSIONS = "conversions";
    private static final String KEY_SHOP = "shop";

    /** The catalog in the stack format. Its absence means the old one is live. */
    private static final String KEY_SHOP_ENTRIES = "shop_entries";

    private static final String KEY_SPREAD_PERCENT = "buyback_spread_percent";
    private static final String KEY_DEATH_LOSS_PERCENT = "death_loss_percent";
    private static final String KEY_DEATH_LOSS_COMBAT_ONLY = "death_loss_combat_only";
    private static final String KEY_NEXT_INTEREST = "next_interest_at";
    private static final String KEY_PASSES = "merchant_passes";
    private static final String KEY_PASS_PLAYER = "player";
    private static final String KEY_PASS_UNTIL = "until";
    private static final String KEY_PASS_LISTED = "merchant_pass_listed";
    private static final String KEY_PURCHASES = "shop_purchases";
    private static final String KEY_PURCHASE_DAY = "day";
    private static final String KEY_PURCHASE_LINES = "lines";
    private static final String KEY_PURCHASE_ENTRY = "entry";
    private static final String KEY_PURCHASE_BUYER = "buyer";
    private static final String KEY_PURCHASE_COUNT = "count";
    private static final String KEY_STAFF = "staff";
    private static final String KEY_STAFF_ID = "id";
    private static final String KEY_STAFF_RANK = "rank";

    public static final SavedData.Factory<EconomyData> FACTORY =
            new SavedData.Factory<>(EconomyData::new, EconomyData::load, null);

    private final Map<String, Long> conversions = new LinkedHashMap<>();
    /**
     * What the shop sells, in the order it was added.
     * <p>
     * A list of whole stacks rather than a map of item id to price, because
     * two entries can sell the same item — a plain sword and an enchanted one
     * — and an id-keyed map cannot hold both.
     */
    private final List<ShopEntry> shopEntries = new ArrayList<>();

    private int spreadPercent = CreditValueTable.NO_SPREAD;

    /** The share of a free purse a player loses on dying, as a percentage. Off by default. */
    private int deathLossPercent;

    /** Whether only a death at the hands of a mob or a player costs credits. */
    private boolean deathLossCombatOnly;

    /**
     * What each player bought of each entry today, by the entry's id, for
     * limits a day. Saved, so a restart is not a way round a limit.
     */
    private final Map<UUID, Map<UUID, Integer>> purchasesToday = new HashMap<>();

    /** The day {@link #purchasesToday} counts, in days since the epoch. */
    private long purchaseDay;

    /** When each player's merchant's pass runs out, in milliseconds since the epoch. */
    private final Map<UUID, Long> passes = new HashMap<>();

    /** Whether the pass has been put in the shop, switched off, for the owner to price. Done once per world. */
    private boolean passListed;

    /** When interest is next paid, in milliseconds since the epoch; zero until it is first scheduled. */
    private long nextInterestAt;

    /**
     * Who may configure the casino without being an operator, and how far.
     * <p>
     * The permission itself, not a record of who holds a card. Revoking is
     * therefore instant and complete: a card still in somebody's pocket, or
     * in a chest nobody can find, stops opening anything the moment its owner
     * leaves this map.
     */
    private final Map<UUID, StaffRank> staff = new LinkedHashMap<>();

    private EconomyData() {
    }

    private static EconomyData load(CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.getInt(KEY_DATA_VERSION);
        if (version > DATA_VERSION) {
            LOGGER.error("[Economy] {}.dat was written by a newer version of EconomyCore "
                            + "(data version {}, this one knows up to {}). What this version does "
                            + "not understand will be lost when the world saves.",
                    DATA_NAME, version, DATA_VERSION);
        }
        EconomyData data = new EconomyData();
        readInto(tag.getCompound(KEY_CONVERSIONS), data.conversions);
        readShop(tag, data, registries);
        ListTag listed = tag.getList(KEY_STAFF, Tag.TAG_COMPOUND);
        for (int i = 0; i < listed.size(); i++) {
            CompoundTag member = listed.getCompound(i);
            int[] raw = member.getIntArray(KEY_STAFF_ID);
            Optional<StaffRank> rank = StaffRank.byId(member.getString(KEY_STAFF_RANK));
            if (raw.length == 4 && rank.isPresent()) {
                data.staff.put(UUIDUtil.uuidFromIntArray(raw), rank.get());
            }
        }
        if (tag.contains(KEY_SPREAD_PERCENT)) {
            data.spreadPercent = Math.clamp(tag.getInt(KEY_SPREAD_PERCENT),
                    CreditValueTable.NO_SPREAD, CreditValueTable.MAX_SPREAD_PERCENT);
        }
        data.deathLossPercent = Math.clamp(tag.getInt(KEY_DEATH_LOSS_PERCENT), 0, DeathLoss.MAX_PERCENT);
        data.deathLossCombatOnly = tag.getBoolean(KEY_DEATH_LOSS_COMBAT_ONLY);
        data.nextInterestAt = Math.max(0, tag.getLong(KEY_NEXT_INTEREST));
        ListTag passList = tag.getList(KEY_PASSES, Tag.TAG_COMPOUND);
        for (int i = 0; i < passList.size(); i++) {
            CompoundTag pass = passList.getCompound(i);
            data.passes.put(pass.getUUID(KEY_PASS_PLAYER), pass.getLong(KEY_PASS_UNTIL));
        }
        data.passListed = tag.getBoolean(KEY_PASS_LISTED);
        CompoundTag purchases = tag.getCompound(KEY_PURCHASES);
        data.purchaseDay = purchases.getLong(KEY_PURCHASE_DAY);
        ListTag lines = purchases.getList(KEY_PURCHASE_LINES, Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag line = lines.getCompound(i);
            data.purchasesToday.computeIfAbsent(line.getUUID(KEY_PURCHASE_ENTRY), id -> new HashMap<>())
                    .put(line.getUUID(KEY_PURCHASE_BUYER), line.getInt(KEY_PURCHASE_COUNT));
        }
        return data;
    }

    private static void readInto(CompoundTag source, Map<String, Long> target) {
        for (String itemId : source.getAllKeys()) {
            long value = source.getLong(itemId);
            if (value > 0) {
                target.put(itemId, value);
            }
        }
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putInt(KEY_DATA_VERSION, DATA_VERSION);
        tag.put(KEY_CONVERSIONS, writeFrom(conversions));
        writeShop(tag, registries);
        tag.putInt(KEY_SPREAD_PERCENT, spreadPercent);
        tag.putInt(KEY_DEATH_LOSS_PERCENT, deathLossPercent);
        tag.putBoolean(KEY_DEATH_LOSS_COMBAT_ONLY, deathLossCombatOnly);
        tag.putLong(KEY_NEXT_INTEREST, nextInterestAt);
        ListTag passList = new ListTag();
        long now = System.currentTimeMillis();
        passes.forEach((player, until) -> {
            // Spent passes are not worth keeping.
            if (until > now) {
                CompoundTag pass = new CompoundTag();
                pass.putUUID(KEY_PASS_PLAYER, player);
                pass.putLong(KEY_PASS_UNTIL, until);
                passList.add(pass);
            }
        });
        tag.put(KEY_PASSES, passList);
        tag.putBoolean(KEY_PASS_LISTED, passListed);
        CompoundTag purchases = new CompoundTag();
        purchases.putLong(KEY_PURCHASE_DAY, purchaseDay);
        ListTag lines = new ListTag();
        purchasesToday.forEach((entry, buyers) -> buyers.forEach((buyer, count) -> {
            CompoundTag line = new CompoundTag();
            line.putUUID(KEY_PURCHASE_ENTRY, entry);
            line.putUUID(KEY_PURCHASE_BUYER, buyer);
            line.putInt(KEY_PURCHASE_COUNT, count);
            lines.add(line);
        }));
        purchases.put(KEY_PURCHASE_LINES, lines);
        tag.put(KEY_PURCHASES, purchases);

        ListTag listed = new ListTag();
        staff.forEach((member, rank) -> {
            CompoundTag entry = new CompoundTag();
            entry.putIntArray(KEY_STAFF_ID, UUIDUtil.uuidToIntArray(member));
            entry.putString(KEY_STAFF_RANK, rank.id());
            listed.add(entry);
        });
        tag.put(KEY_STAFF, listed);
        return tag;
    }

    /**
     * Reads the catalog, converting the old format when that is all there
     * is.
     * <p>
     * The previous format stored item IDs against prices, so every migrated
     * entry becomes a single plain item at the same price — which is exactly
     * what the old shop sold, since it could not express anything else. The
     * old tag is simply ignored once the new one exists; nothing is deleted,
     * so a server that rolls the update back still has its catalog.
     */
    private static void readShop(CompoundTag tag, EconomyData data,
                                 HolderLookup.Provider registries) {
        if (tag.contains(KEY_SHOP_ENTRIES)) {
            ListTag stored = tag.getList(KEY_SHOP_ENTRIES, Tag.TAG_COMPOUND);
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            for (int i = 0; i < stored.size(); i++) {
                ShopEntry.CODEC.parse(ops, stored.getCompound(i))
                        .resultOrPartial(error -> LOGGER.warn(
                                "[Economy] Dropped an unreadable shop entry: {}", error))
                        .ifPresent(data.shopEntries::add);
            }
            return;
        }

        CompoundTag legacy = tag.getCompound(KEY_SHOP);
        for (String itemId : legacy.getAllKeys()) {
            ItemIds.item(itemId).ifPresent(item -> data.shopEntries.add(
                    new ShopEntry(new ItemStack(item), legacy.getLong(itemId))));
        }
        if (!data.shopEntries.isEmpty()) {
            LOGGER.info("[Economy] Migrated {} shop entries to the stack format.",
                    data.shopEntries.size());
        }
    }

    private void writeShop(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag stored = new ListTag();
        for (ShopEntry entry : shopEntries) {
            ShopEntry.CODEC.encodeStart(ops, entry)
                    .resultOrPartial(error -> LOGGER.error(
                            "[Economy] Could not save a shop entry: {}", error))
                    .ifPresent(stored::add);
        }
        tag.put(KEY_SHOP_ENTRIES, stored);
    }

    private static CompoundTag writeFrom(Map<String, Long> source) {
        CompoundTag out = new CompoundTag();
        source.forEach(out::putLong);
        return out;
    }

    public static EconomyData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // --- Conversion values --------------------------------------------------

    /** The surcharge on buying items back, as a percentage. */
    public int spreadPercent() {
        return spreadPercent;
    }

    public void setSpreadPercent(int percent) {
        if (percent < 0 || percent > CreditValueTable.MAX_SPREAD_PERCENT) {
            throw new IllegalArgumentException("Spread out of range: " + percent);
        }
        this.spreadPercent = percent;
        setDirty();
    }

    /** The share of a free purse a player loses on dying, as a percentage. */
    public int deathLossPercent() {
        return deathLossPercent;
    }

    public void setDeathLossPercent(int percent) {
        if (percent < 0 || percent > DeathLoss.MAX_PERCENT) {
            throw new IllegalArgumentException("Death loss out of range: " + percent);
        }
        this.deathLossPercent = percent;
        setDirty();
    }

    public boolean deathLossCombatOnly() {
        return deathLossCombatOnly;
    }

    public void setDeathLossCombatOnly(boolean combatOnly) {
        this.deathLossCombatOnly = combatOnly;
        setDirty();
    }

    public long nextInterestAt() {
        return nextInterestAt;
    }

    public void setNextInterestAt(long time) {
        this.nextInterestAt = time;
        setDirty();
    }

    /**
     * This player's staff rank, if they have one.
     * <p>
     * Says nothing about operators, who need no listing. Callers that mean
     * "may configure the casino" should be asking
     * {@code AdminKeyItem.mayAdminister} instead, which covers both.
     */
    public Optional<StaffRank> rankOf(UUID playerId) {
        return Optional.ofNullable(staff.get(playerId));
    }

    /** Whether this player holds any staff rank at all. */
    public boolean isStaff(UUID playerId) {
        return staff.containsKey(playerId);
    }

    /** Every listed member and their rank, in the order they were listed. Unmodifiable. */
    public Map<UUID, StaffRank> staff() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(staff));
    }

    /**
     * Lists somebody at this rank, or moves them to it.
     *
     * @return the rank they held before, empty if they were not listed
     */
    public Optional<StaffRank> setRank(UUID playerId, StaffRank rank) {
        Objects.requireNonNull(rank, "rank");
        StaffRank previous = staff.put(playerId, rank);
        if (previous != rank) {
            setDirty();
        }
        return Optional.ofNullable(previous);
    }

    /** @return false if they were not listed to begin with */
    public boolean removeStaff(UUID playerId) {
        if (staff.remove(playerId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public Map<String, Long> conversions() {
        return Collections.unmodifiableMap(conversions);
    }

    public Optional<Long> conversionOf(String itemId) {
        return Optional.ofNullable(conversions.get(itemId));
    }

    /** Sets or replaces a value. The caller must have validated it first. */
    public void setConversion(String itemId, long credits) {
        if (credits <= 0) {
            throw new IllegalArgumentException("Value must be positive: " + credits);
        }
        conversions.put(itemId, credits);
        setDirty();
    }

    public Optional<Long> removeConversion(String itemId) {
        Long removed = conversions.remove(itemId);
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    // --- Shop prices --------------------------------------------------------

    /**
     * The catalog in order. An entry's number is its position here, counting
     * from one, so removing one renumbers everything after it.
     */
    public List<ShopEntry> shopEntries() {
        return List.copyOf(shopEntries);
    }

    /**
     * Looks an entry up by the number a player or a command sees.
     * <p>
     * One-based, because that is what the list shows. Out of range returns
     * empty rather than clamping: a number that no longer points anywhere has
     * to fail, not quietly hit a neighbor.
     */
    public Optional<ShopEntry> shopEntry(int number) {
        return number < 1 || number > shopEntries.size()
                ? Optional.empty()
                : Optional.of(shopEntries.get(number - 1));
    }

    /** Puts a stack on sale at the end of the catalog. */
    public int addShopEntry(ItemStack stack, long price) {
        shopEntries.add(new ShopEntry(stack.copy(), price));
        setDirty();
        return shopEntries.size();
    }

    /** Changes an entry's price, keeping its place and its stack. */
    public Optional<ShopEntry> repriceShopEntry(int number, long price) {
        if (number < 1 || number > shopEntries.size()) {
            return Optional.empty();
        }
        return updateShopEntry(number, entry -> entry.withPrice(price));
    }

    /**
     * Changes an entry in place: switched on or off, its stock, its restock,
     * its limit a day. Its number and its id stay what they were.
     */
    public Optional<ShopEntry> updateShopEntry(int number, UnaryOperator<ShopEntry> change) {
        if (number < 1 || number > shopEntries.size()) {
            return Optional.empty();
        }
        ShopEntry updated = change.apply(shopEntries.get(number - 1));
        shopEntries.set(number - 1, updated);
        setDirty();
        return Optional.of(updated);
    }

    /**
     * Fills back up every entry whose restock fell due by {@code now}.
     * <p>
     * Lazily, when the shop is looked at or bought from, rather than on a
     * timer: an entry nobody opens the shop to see does not need to be full.
     *
     * @return whether anything filled up
     */
    public boolean restockShop(long now) {
        boolean changed = false;
        for (int i = 0; i < shopEntries.size(); i++) {
            ShopEntry entry = shopEntries.get(i);
            ShopEntry restocked = entry.restockedBy(now);
            if (restocked != entry) {
                shopEntries.set(i, restocked);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Takes what was sold out of an entry's stock, found by id since numbers may have moved. */
    public void recordShopSale(UUID entryId, UUID buyer, long count, long now) {
        for (int i = 0; i < shopEntries.size(); i++) {
            if (shopEntries.get(i).id().equals(entryId)) {
                shopEntries.set(i, shopEntries.get(i).afterSale(count));
                break;
            }
        }
        rollPurchaseDay(now);
        purchasesToday.computeIfAbsent(entryId, id -> new HashMap<>())
                .merge(buyer, (int) count, Integer::sum);
        setDirty();
    }

    /** How many of an entry this player bought today. */
    public int boughtToday(UUID entryId, UUID buyer, long now) {
        if (purchaseDay != dayOf(now)) {
            return 0;
        }
        return purchasesToday.getOrDefault(entryId, Map.of()).getOrDefault(buyer, 0);
    }

    // --- Merchant's passes -------------------------------------------------

    /** When this player's pass runs out, or zero if they have never had one. */
    public long passUntil(UUID player) {
        return passes.getOrDefault(player, 0L);
    }

    /**
     * Adds {@code millis} to a player's pass: from now if it had run out, from
     * when it would have run out if it had not, so using two adds both.
     *
     * @return when it now runs out
     */
    public long extendPass(UUID player, long millis, long now) {
        long until = Math.max(now, passUntil(player)) + millis;
        passes.put(player, until);
        setDirty();
        return until;
    }

    public boolean passListed() {
        return passListed;
    }

    public void setPassListed() {
        this.passListed = true;
        setDirty();
    }

    /** Forgets yesterday's purchases when a new day has begun. */
    private void rollPurchaseDay(long now) {
        long today = dayOf(now);
        if (purchaseDay != today) {
            purchaseDay = today;
            purchasesToday.clear();
        }
    }

    /** Days count from midnight UTC, the same for every player wherever they are. */
    private static long dayOf(long millis) {
        return Math.floorDiv(millis, 86_400_000L);
    }

    /** Takes an entry off sale. Everything after it moves up one. */
    public Optional<ShopEntry> removeShopEntry(int number) {
        if (number < 1 || number > shopEntries.size()) {
            return Optional.empty();
        }
        ShopEntry removed = shopEntries.remove(number - 1);
        setDirty();
        return Optional.of(removed);
    }

    /**
     * The cheapest shop price for each item type, for the exploit validator.
     * <p>
     * Cheapest because that is the one that could open a loop: the exchange
     * values an item by type and ignores components, so if any entry sells it
     * below its conversion value, players mint credits — whatever the other
     * entries charge.
     */
    public Map<String, Long> lowestShopPrices() {
        Map<String, Long> lowest = new LinkedHashMap<>();
        for (ShopEntry entry : shopEntries) {
            // One that cannot be bought cannot open a loop. Switching it on
            // rebuilds the economy, which checks it again then.
            if (!entry.enabled()) {
                continue;
            }
            lowest.merge(entry.itemId(), entry.price(), Math::min);
        }
        return Collections.unmodifiableMap(lowest);
    }

    /**
     * Command values layered over datapack defaults.
     * <p>
     * Entries whose item is no longer registered are dropped here rather than
     * at load time, so uninstalling a mod temporarily does not erase its
     * prices from the save.
     */
    public CreditValueTable mergedWith(CreditValueTable defaults) {
        Map<String, Long> merged = new LinkedHashMap<>();
        for (String itemId : defaults.itemIds()) {
            defaults.valueOf(itemId).ifPresent(value -> merged.put(itemId, value));
        }
        merged.putAll(conversions);

        CreditValueTable.Builder builder = CreditValueTable.builder();
        merged.forEach((itemId, value) -> {
            if (ItemIds.exists(itemId)) {
                builder.put(itemId, value);
            }
        });
        return builder.build();
    }
}
