package com.github.arrivedbog593.economycore.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * One thing the shop sells, and on what terms.
 * <p>
 * A whole {@link ItemStack} rather than an item id, so what the shop hands
 * over is what the admin put on sale. The shop used to store an id and a
 * price, which meant an enchanted sword added to the catalog came back out
 * as a plain one — the enchantments were never lost, they were never stored.
 * <p>
 * The stack also carries a count, so an entry can be "a stack of sixteen
 * arrows for 200" rather than only ever one of something.
 * <p>
 * An entry's number is its position in the catalog, so removing the third of
 * ten renumbers everything below it, and the list an admin reads never has
 * gaps in it. Numbers are for people. What has to stay put while the list
 * moves — how many of an entry each player bought today — is kept against
 * its {@link #id}, which never changes.
 * <p>
 * Beyond its price, an entry can be switched off, so it stays in the window
 * but cannot be bought; it can have a stock, which runs out; that stock can
 * fill back up on its own every so many hours; and each player can be held
 * to so many purchases a day. Left alone, it is on sale, unlimited, as every
 * entry was before these existed.
 *
 * @param id           what the entry is known by while its number moves
 * @param stack        what the buyer receives, components and all
 * @param price        credits per purchase of this entry
 * @param enabled      whether it can be bought; off, it shows but is not for sale
 * @param stock        purchases left before it runs out, or {@link #UNLIMITED}
 * @param capacity     what a restock fills the stock back up to, or {@link #UNLIMITED}
 * @param restockHours how many real hours apart it fills back up; zero for never
 * @param restockedAt  when it last filled up, in milliseconds since the epoch
 * @param dailyLimit   purchases each player may make a day; zero for no limit
 */
public record ShopEntry(UUID id, ItemStack stack, long price, boolean enabled, long stock, long capacity,
                        int restockHours, long restockedAt, int dailyLimit) {

    /** Stock that never runs out. */
    public static final long UNLIMITED = -1;

    public static final Codec<ShopEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    // Entries saved before ids existed get one the first time
                    // they are read, and keep it from the next save on.
                    UUIDUtil.CODEC.optionalFieldOf("id").forGetter(entry -> Optional.of(entry.id())),
                    ItemStack.CODEC.fieldOf("stack").forGetter(ShopEntry::stack),
                    Codec.LONG.fieldOf("price").forGetter(ShopEntry::price),
                    Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ShopEntry::enabled),
                    Codec.LONG.optionalFieldOf("stock", UNLIMITED).forGetter(ShopEntry::stock),
                    Codec.LONG.optionalFieldOf("capacity", UNLIMITED).forGetter(ShopEntry::capacity),
                    Codec.INT.optionalFieldOf("restock_hours", 0).forGetter(ShopEntry::restockHours),
                    Codec.LONG.optionalFieldOf("restocked_at", 0L).forGetter(ShopEntry::restockedAt),
                    Codec.INT.optionalFieldOf("daily_limit", 0).forGetter(ShopEntry::dailyLimit)
            ).apply(instance, (id, stack, price, enabled, stock, capacity, hours, at, limit) ->
                    new ShopEntry(id.orElseGet(UUID::randomUUID), stack, price, enabled, stock, capacity,
                            hours, at, limit)));

    public ShopEntry {
        if (price <= 0) {
            throw new IllegalArgumentException("Shop price must be positive: " + price);
        }
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Shop entry has no item");
        }
        if (id == null) {
            id = UUID.randomUUID();
        }
        stock = stock < 0 ? UNLIMITED : stock;
        capacity = capacity < 0 ? UNLIMITED : capacity;
        restockHours = Math.max(0, restockHours);
        dailyLimit = Math.max(0, dailyLimit);
    }

    /** A new entry: on sale, unlimited, no limit a day. */
    public ShopEntry(ItemStack stack, long price) {
        this(UUID.randomUUID(), stack, price, true, UNLIMITED, UNLIMITED, 0, 0, 0);
    }

    /**
     * The registry id of the item, for the checks that reason about items
     * rather than stacks.
     * <p>
     * The exploit validator works at this level on purpose: the exchange
     * values an item by type and ignores components, so an enchanted sword
     * and a plain one convert back for the same credits. Whether a shop price
     * opens a loop therefore depends on the item, not on what is written on
     * it.
     */
    public String itemId() {
        return ItemIds.idOf(stack.getItem());
    }

    /** How many items one purchase delivers. */
    public int count() {
        return stack.getCount();
    }

    /** Whether this entry carries anything beyond a plain item. */
    public boolean hasComponents() {
        return !stack.getComponentsPatch().isEmpty();
    }

    /** A fresh copy of what the buyer receives, safe to hand out. */
    public ItemStack prototype() {
        return stack.copy();
    }

    public boolean unlimited() {
        return stock == UNLIMITED;
    }

    /** Whether it can be bought at all right now: switched on, and not sold out. */
    public boolean onSale() {
        return enabled && (unlimited() || stock > 0);
    }

    public ShopEntry withPrice(long newPrice) {
        return new ShopEntry(id, stack, newPrice, enabled, stock, capacity, restockHours, restockedAt, dailyLimit);
    }

    public ShopEntry withEnabled(boolean on) {
        return new ShopEntry(id, stack, price, on, stock, capacity, restockHours, restockedAt, dailyLimit);
    }

    /**
     * Sets how many are left, and what a restock fills back up to; or
     * {@link #UNLIMITED} for both. Restarts the restock clock from now.
     */
    public ShopEntry withStock(long amount, long now) {
        long kept = amount < 0 ? UNLIMITED : amount;
        return new ShopEntry(id, stack, price, enabled, kept, kept, restockHours, now, dailyLimit);
    }

    /** Fills back up every so many hours, from now; zero to stop. */
    public ShopEntry withRestock(int hours, long now) {
        return new ShopEntry(id, stack, price, enabled, stock, capacity, hours, now, dailyLimit);
    }

    public ShopEntry withDailyLimit(int limit) {
        return new ShopEntry(id, stack, price, enabled, stock, capacity, restockHours, restockedAt, limit);
    }

    /** What is left once {@code count} purchases are made. */
    public ShopEntry afterSale(long count) {
        if (unlimited()) {
            return this;
        }
        return new ShopEntry(id, stack, price, enabled, Math.max(0, stock - count), capacity,
                restockHours, restockedAt, dailyLimit);
    }

    /**
     * Filled back up if a restock fell due by {@code now}, or this entry as it
     * is. However many periods were missed, it fills once, to its capacity,
     * and the clock carries on from the last period that fell due rather than
     * from now, so restocks keep to the hour they were set on.
     */
    public ShopEntry restockedBy(long now) {
        if (restockHours <= 0 || capacity == UNLIMITED) {
            return this;
        }
        long period = restockHours * 3_600_000L;
        if (now < restockedAt + period) {
            return this;
        }
        long due = restockedAt + (now - restockedAt) / period * period;
        return new ShopEntry(id, stack, price, enabled, capacity, capacity, restockHours, due, dailyLimit);
    }

    /** When it next fills back up, or zero if it never does. */
    public long nextRestock() {
        return restockHours <= 0 || capacity == UNLIMITED ? 0 : restockedAt + restockHours * 3_600_000L;
    }
}
