package com.github.arrivedbog593.economycore.platform.menu;

import com.github.arrivedbog593.economycore.api.Pocket;
import com.github.arrivedbog593.economycore.platform.Commitments;
import com.github.arrivedbog593.economycore.platform.CreditStorage;
import com.github.arrivedbog593.economycore.platform.registry.EconomyMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The banker's window: a player's purse and savings, and the two ways
 * between them.
 * <p>
 * No slots at all. Nothing but numbers changes hands here, so the window is a
 * menu only for what menus give for free: closing when the player walks away
 * from the banker, and the numbers kept in step every tick. The moving itself
 * arrives as {@code BankActionPayload} and goes through {@code PersonalBank},
 * the same as {@code /credits deposit}.
 * <p>
 * Data slots are written on the server only, since the client's copy of this
 * class has no access to the credit store and would zero them.
 */
public class BankMenu extends AbstractContainerMenu {

    private final MenuAnchor anchor;
    private final Player player;

    /** Longs; data slots carry ints, so each travels in halves. */
    private final DataSlot[] purse = halves();
    private final DataSlot[] savings = halves();
    private final DataSlot[] committed = halves();

    /** Client-side constructor; only the server asks whether the window may stay open. */
    public BankMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, MenuAnchor.ANYWHERE);
    }

    public BankMenu(int containerId, Inventory inventory, MenuAnchor anchor) {
        super(EconomyMenus.BANK.get(), containerId);
        this.player = inventory.player;
        this.anchor = anchor;
        for (DataSlot[] pair : new DataSlot[][]{purse, savings, committed}) {
            addDataSlot(pair[0]);
            addDataSlot(pair[1]);
        }
        refresh();
    }

    public long purse() {
        return read(purse);
    }

    public long savings() {
        return read(savings);
    }

    /** What the purse holds that can go into savings: all of it but what is promised at a table. */
    public long free() {
        return Math.max(0L, purse() - read(committed));
    }

    /** Whether this is the server's copy, the one that may act. */
    public boolean isServerSide() {
        return !player.level().isClientSide && player.level().getServer() != null;
    }

    private void refresh() {
        if (!isServerSide()) {
            return;
        }
        CreditStorage storage = CreditStorage.get(player.level().getServer());
        write(purse, storage.balanceOf(player.getUUID()));
        write(savings, storage.balanceOf(player.getUUID(), Pocket.SAVINGS));
        write(committed, Commitments.players().committedBy(player.getUUID()));
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player who, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player who) {
        return anchor.stillValid(who);
    }

    private static DataSlot[] halves() {
        return new DataSlot[]{DataSlot.standalone(), DataSlot.standalone()};
    }

    private static long read(DataSlot[] pair) {
        return ((long) pair[1].get() << 32) | (pair[0].get() & 0xFFFFFFFFL);
    }

    private static void write(DataSlot[] pair, long value) {
        pair[0].set((int) (value & 0xFFFFFFFFL));
        pair[1].set((int) (value >> 32));
    }
}
