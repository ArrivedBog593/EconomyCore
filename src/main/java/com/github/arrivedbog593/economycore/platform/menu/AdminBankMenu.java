package com.github.arrivedbog593.economycore.platform.menu;

import com.github.arrivedbog593.economycore.platform.item.AdminKeyItem;
import com.github.arrivedbog593.economycore.platform.registry.EconomyMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Configuring the bank: interest, what dying costs, and the levels of
 * players' accounts.
 * <p>
 * A menu with a slot for the same reason as {@link AdminShopMenu}: what a
 * level costs in items is said by putting them there, the count included,
 * rather than by typing ids. The slot holds the admin's own items and hands
 * them back when the screen closes; adding them to a cost copies them.
 * <p>
 * The settings themselves travel as {@code AdminBankStatePayload}, since
 * they are text and lists that data slots cannot carry.
 */
public class AdminBankMenu extends AbstractContainerMenu {

    /** Geometry, shared with the screen. Slots are positioned here. */
    public static final int PANEL_WIDTH = 230;
    public static final int PANEL_HEIGHT = 289;
    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 157;
    public static final int INVENTORY_Y = 207;
    public static final int HOTBAR_Y = 265;

    private final MenuAnchor anchor;

    /** What is about to be added to a cost. Never persisted; emptied back to the player. */
    private final SimpleContainer input = new SimpleContainer(1);

    /** Client-side constructor; only the server asks whether the screen may stay open. */
    public AdminBankMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, MenuAnchor.ANYWHERE);
    }

    public AdminBankMenu(int containerId, Inventory inventory, MenuAnchor anchor) {
        super(EconomyMenus.ADMIN_BANK.get(), containerId);
        this.anchor = anchor;

        addSlot(new Slot(input, 0, INPUT_X, INPUT_Y));

        int inventoryLeft = (PANEL_WIDTH - 9 * 18) / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        inventoryLeft + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, inventoryLeft + column * 18, HOTBAR_Y));
        }
    }

    /** What is in the slot. */
    public ItemStack held() {
        return input.getItem(0);
    }

    /** Thrown out the moment the key is gone, as the shop's settings are. */
    @Override
    public boolean stillValid(@NotNull Player who) {
        if (who instanceof ServerPlayer server
                && !AdminKeyItem.mayAdminister(server, server.getMainHandItem())) {
            return false;
        }
        return anchor.stillValid(who);
    }

    @Override
    public void removed(@NotNull Player who) {
        super.removed(who);
        // Only ever borrowed to say what a level costs.
        clearContainer(who, input);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player who, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
