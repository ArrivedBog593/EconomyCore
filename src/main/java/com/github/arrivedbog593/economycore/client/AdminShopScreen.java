package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.platform.ShopEntry;
import com.github.arrivedbog593.economycore.platform.menu.AdminShopMenu;
import com.github.arrivedbog593.economycore.platform.network.AdminShopActionPayload;
import com.github.arrivedbog593.economycore.platform.network.ShopCatalogPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuring what the shop sells.
 * <p>
 * The same grid the players see, plus a slot and a price field. Deliberately
 * the same: an admin arranging a shop should be looking at what a customer
 * will, not at a different rendering of the same list.
 * <p>
 * The top button does one thing at a time. With nothing selected it lists
 * whatever is in the slot; with an entry selected it reprices that entry.
 * <p>
 * Removing used to be a shift click on the entry itself, on the argument that
 * a button and a selection are two places to look for the answer to "which
 * one". The selection turned out to answer that on its own — the chosen entry
 * is the highlighted one, and it is highlighted while the button is on screen
 * — and a modifier nobody is told about is not much of a safeguard anyway.
 * The button asks for a second click instead, which guards the same accident
 * out loud.
 */
public class AdminShopScreen extends AbstractContainerScreen<AdminShopMenu> {

    private static final int COLUMNS = 3;
    private static final int ROWS = 4;
    private static final int CELL_WIDTH = 67;
    private static final int CELL_HEIGHT = 20;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 35;

    private static final int SEARCH_Y = 17;
    private static final int SEARCH_H = 14;
    private static final int SEARCH_W = 118;
    private static final int SORT_W = 62;
    private static final int REMEMBER_W = 11;
    private static final int TEXT_INSET = 4;
    private static final int TEXT_Y = (SEARCH_H - 8) / 2 + 1;

    /** The price field and the action button, beside the listing slot. */
    private static final int ACTION_Y = AdminShopMenu.INPUT_Y + 2;
    private static final int PRICE_X = 32;
    private static final int PRICE_W = 90;
    private static final int ACTION_X = 128;
    private static final int ACTION_W = 84;

    /** The remove button, directly under the action button. */
    private static final int REMOVE_Y = ACTION_Y + 18;

    /** On sale or not, beside the remove button, for the chosen entry. */
    private static final int TOGGLE_X = 8;
    private static final int TOGGLE_W = ACTION_X - TOGGLE_X - 6;

    /**
     * The chosen entry's stock, restock and limit a day: three fields under
     * small labels, and a button that sends whichever of them changed.
     */
    private static final int TERMS_LABEL_Y = REMOVE_Y + 19;
    private static final int TERMS_Y = TERMS_LABEL_Y + 7;
    private static final int TERM_W = 48;
    private static final int TERM_GAP = 4;
    private static final int APPLY_X = 8 + 3 * (TERM_W + TERM_GAP);
    private static final int APPLY_W = 212 - APPLY_X;
    private static final float TERM_LABEL_SCALE = 0.75F;

    /**
     * How long the remove button stays armed after the first click.
     * <p>
     * Long enough to move the mouse a few pixels and click again, short
     * enough that a button left armed and forgotten disarms itself before it
     * can be hit by accident.
     */
    private static final long CONFIRM_MILLIS = 3_000;

    /** Nothing selected. The button lists whatever is in the slot. */
    private static final int NO_SELECTION = 0;

    private int scroll;

    /** The catalog number being edited, or {@link #NO_SELECTION}. */
    private int selected = NO_SELECTION;

    /**
     * The catalog this screen last drew, compared by identity.
     * <p>
     * The holder hands out a new list for every packet the server sends, so a
     * different instance means the shop changed underneath. That matters
     * because a selection is a catalog number and numbers move: with entry
     * three gone, a selection made on entry four now points at what used to
     * be five, and the reprice button would quietly change the wrong item.
     */
    private List<ShopCatalogPayload.Entry> lastSeen = List.of();

    /** When the armed remove button goes back to being harmless. */
    private long confirmUntil;

    private final CatalogView<ShopCatalogPayload.Entry> catalog =
            new CatalogView<>(ShopCatalogPayload.Entry::stack,
                    ShopCatalogPayload.Entry::price);

    private EditBox search;
    private EditBox price;
    private EditBox stock;
    private EditBox restock;
    private EditBox limit;

    /** The chosen entry's stock, restock and limit as they were shown, so apply sends only what changed. */
    private long shownStock = ShopEntry.UNLIMITED;
    private long shownRestock;
    private long shownLimit;

    public AdminShopScreen(AdminShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = AdminShopMenu.PANEL_WIDTH;
        this.imageHeight = AdminShopMenu.PANEL_HEIGHT;
        this.inventoryLabelY = AdminShopMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();

        String carriedSearch = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + GRID_X + TEXT_INSET,
                topPos + SEARCH_Y + TEXT_Y, SEARCH_W - TEXT_INSET * 2, 8,
                Component.translatable("economycore.shop.search"));
        search.setBordered(false);
        search.setHint(Component.translatable("economycore.shop.search"));
        search.setResponder(text -> {
            catalog.setQuery(text);
            scroll = 0;
        });
        search.setValue(carriedSearch);
        addRenderableWidget(search);

        String carriedPrice = price == null ? "" : price.getValue();
        price = new EditBox(font, leftPos + PRICE_X + TEXT_INSET,
                topPos + ACTION_Y + TEXT_Y, PRICE_W - TEXT_INSET * 2, 8,
                Component.translatable("economycore.admin.shop.price"));
        price.setBordered(false);
        price.setHint(Component.translatable("economycore.admin.shop.price"));
        price.setMaxLength(12);
        price.setFilter(text -> text.chars().allMatch(Character::isDigit));
        price.setValue(carriedPrice);
        addRenderableWidget(price);

        stock = termField(stock, 0, "∞");
        restock = termField(restock, 1, "—");
        limit = termField(limit, 2, "—");

        catalog.restore(ScreenPreferences.shopSort(),
                ScreenPreferences.shopSortDescending());
    }

    /** One of the three fields of the terms row: digits only, unbordered, framed when drawn. */
    private EditBox termField(EditBox old, int index, String hint) {
        String carried = old == null ? "" : old.getValue();
        EditBox field = new EditBox(font, leftPos + termX(index) + TEXT_INSET, topPos + TERMS_Y + TEXT_Y,
                TERM_W - TEXT_INSET * 2, 8, Component.literal(hint));
        field.setBordered(false);
        field.setHint(Component.literal(hint).withStyle(ChatFormatting.GRAY));
        field.setMaxLength(9);
        field.setFilter(text -> text.chars().allMatch(Character::isDigit));
        field.setValue(carried);
        field.visible = termsVisible();
        addRenderableWidget(field);
        return field;
    }

    private static int termX(int index) {
        return 8 + index * (TERM_W + TERM_GAP);
    }

    /** The entry chosen, as the catalog last sent it, or null. */
    private ShopCatalogPayload.Entry selectedEntry() {
        for (ShopCatalogPayload.Entry entry : ClientShopState.entries()) {
            if (entry.number() == selected) {
                return entry;
            }
        }
        return null;
    }

    /** Fills the terms row with the chosen entry's, or empties it. */
    private void showTerms(ShopCatalogPayload.Entry entry) {
        shownStock = entry == null ? ShopEntry.UNLIMITED : entry.capacity();
        shownRestock = entry == null ? 0 : entry.restockHours();
        shownLimit = entry == null ? 0 : entry.dailyLimit();
        stock.setValue(shownStock == ShopEntry.UNLIMITED ? "" : String.valueOf(shownStock));
        restock.setValue(shownRestock == 0 ? "" : String.valueOf(shownRestock));
        limit.setValue(shownLimit == 0 ? "" : String.valueOf(shownLimit));
    }

    private boolean termsVisible() {
        return selected != NO_SELECTION;
    }

    /** What a terms field says, or {@code empty} when it says nothing. */
    private static long typed(EditBox field, long empty) {
        if (field == null || field.getValue().isEmpty()) {
            return empty;
        }
        try {
            return Long.parseLong(field.getValue());
        } catch (NumberFormatException tooLong) {
            return empty;
        }
    }

    /** Whether any of the three fields differs from what was shown. */
    private boolean termsChanged() {
        return typed(stock, ShopEntry.UNLIMITED) != shownStock
                || typed(restock, 0) != shownRestock
                || typed(limit, 0) != shownLimit;
    }

    /**
     * Sends whichever of stock, restock and limit changed, and only those.
     * Setting a stock fills it up, so sending it unchanged alongside a new
     * limit would have restocked an entry nobody meant to.
     */
    private void applyTerms() {
        if (!termsVisible() || !termsChanged()) {
            return;
        }
        long newStock = typed(stock, ShopEntry.UNLIMITED);
        long newRestock = typed(restock, 0);
        long newLimit = typed(limit, 0);
        if (newStock != shownStock) {
            PacketDistributor.sendToServer(AdminShopActionPayload.terms(
                    AdminShopActionPayload.KIND_STOCK, selected, newStock));
        }
        if (newRestock != shownRestock) {
            PacketDistributor.sendToServer(AdminShopActionPayload.terms(
                    AdminShopActionPayload.KIND_RESTOCK, selected, newRestock));
        }
        if (newLimit != shownLimit) {
            PacketDistributor.sendToServer(AdminShopActionPayload.terms(
                    AdminShopActionPayload.KIND_LIMIT, selected, newLimit));
        }
        playClick();
    }

    /** Puts the chosen entry on sale or takes it off, at once. */
    private void toggleSale() {
        ShopCatalogPayload.Entry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        PacketDistributor.sendToServer(AdminShopActionPayload.terms(entry.enabled()
                ? AdminShopActionPayload.KIND_DISABLE : AdminShopActionPayload.KIND_ENABLE, selected, 0));
        playClick();
    }

    /** The on-sale button and the terms row, or a line saying to choose an entry first. */
    private void drawTerms(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean visible = termsVisible();
        for (EditBox field : new EditBox[]{stock, restock, limit}) {
            field.visible = visible;
        }
        if (!visible) {
            // Wrapped to the panel, which it ran out of in Spanish.
            Component hint = Component.translatable("economycore.admin.shop.terms_choose");
            List<FormattedCharSequence> lines = font.split(hint, (int) ((imageWidth - 16) / TERM_LABEL_SCALE));
            for (int i = 0; i < lines.size(); i++) {
                graphics.pose().pushPose();
                graphics.pose().translate(leftPos + 8, topPos + TERMS_Y + i * 7, 0);
                graphics.pose().scale(TERM_LABEL_SCALE, TERM_LABEL_SCALE, 1.0F);
                graphics.drawString(font, lines.get(i), 0, 0, Panels.label(), false);
                graphics.pose().popPose();
            }
            return;
        }
        ShopCatalogPayload.Entry entry = selectedEntry();
        boolean onSale = entry == null || entry.enabled();
        int toggleY = topPos + REMOVE_Y;
        drawControl(graphics, leftPos + TOGGLE_X, toggleY, TOGGLE_W, Panels.OUTLINE,
                onSale ? 0xFF2E7D32 : 0xFF6D4C41,
                isOver(mouseX, mouseY, leftPos + TOGGLE_X, toggleY, TOGGLE_W, SEARCH_H));
        Component state = Component.translatable(onSale
                ? "economycore.admin.shop.on_sale" : "economycore.admin.shop.off_sale");
        graphics.drawString(font, state, leftPos + TOGGLE_X + (TOGGLE_W - font.width(state)) / 2,
                toggleY + TEXT_Y, 0xFFFFFFFF, false);

        String[] labels = {"economycore.admin.shop.stock", "economycore.admin.shop.restock",
                "economycore.admin.shop.limit"};
        EditBox[] fields = {stock, restock, limit};
        for (int i = 0; i < 3; i++) {
            int x = leftPos + termX(i);
            drawSmall(graphics, Component.translatable(labels[i]), x + TERM_W / 2, topPos + TERMS_LABEL_Y, true);
            boolean typing = fields[i].isFocused();
            drawControl(graphics, x, topPos + TERMS_Y, TERM_W,
                    typing ? 0xFFFFFFFF : 0xFFA0A0A0, 0xFF000000, typing);
            fields[i].render(graphics, mouseX, mouseY, 0f);
        }

        boolean ready = termsChanged();
        int applyX = leftPos + APPLY_X;
        drawControl(graphics, applyX, topPos + TERMS_Y, APPLY_W, Panels.OUTLINE,
                ready ? 0xFF2E7D32 : 0xFF4A4A4A,
                ready && isOver(mouseX, mouseY, applyX, topPos + TERMS_Y, APPLY_W, SEARCH_H));
        Component apply = Component.translatable("economycore.admin.shop.apply");
        graphics.drawString(font, apply, applyX + (APPLY_W - font.width(apply)) / 2,
                topPos + TERMS_Y + TEXT_Y, ready ? 0xFFFFFFFF : 0xFF9A9A9A, false);
    }

    /** Small text for the labels over the terms fields, centred or from the left. */
    private void drawSmall(GuiGraphics graphics, Component text, int x, int y, boolean centred) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(TERM_LABEL_SCALE, TERM_LABEL_SCALE, 1.0F);
        graphics.drawString(font, text, centred ? -font.width(text) / 2 : 0, 0, Panels.label(), false);
        graphics.pose().popPose();
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panels.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        Panels.recess(graphics, leftPos + GRID_X - 1, topPos + GRID_Y - 1,
                COLUMNS * CELL_WIDTH + 2, ROWS * CELL_HEIGHT + 2);
        Panels.slot(graphics, leftPos + AdminShopMenu.INPUT_X, topPos + AdminShopMenu.INPUT_Y);

        int inventoryLeft = leftPos + (imageWidth - 9 * 18) / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Panels.slot(graphics, inventoryLeft + column * 18,
                        topPos + AdminShopMenu.INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            Panels.slot(graphics, inventoryLeft + column * 18,
                    topPos + AdminShopMenu.HOTBAR_Y);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCatalog(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, Panels.label(), false);
        graphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, Panels.label(), false);
    }

    private void renderCatalog(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ShopCatalogPayload.Entry> incoming = ClientShopState.entries();
        if (incoming != lastSeen) {
            boolean samePlaces = stillInPlace(lastSeen, incoming);
            lastSeen = incoming;
            if (samePlaces) {
                // Only its terms changed — switched on or off, a new stock —
                // so the numbers still mean what they did: keep the choice and
                // show the terms as they now are.
                showTerms(selectedEntry());
            } else {
                // Cheaper to lose a selection than to reprice the wrong item.
                // It costs this administrator a click when somebody else edits
                // mid-typing, and nothing when the change was their own, since
                // adding, repricing and removing clear the selection anyway.
                selected = NO_SELECTION;
                price.setValue("");
                showTerms(null);
                confirmUntil = 0;
            }
        }
        catalog.accept(incoming);
        List<ShopCatalogPayload.Entry> entries = catalog.entries();

        if (entries.isEmpty()) {
            Component message = Component.translatable(catalog.isFiltered()
                    ? "economycore.shop.no_matches"
                    : "economycore.shop.empty");
            graphics.drawString(font, message,
                    leftPos + GRID_X + (COLUMNS * CELL_WIDTH - font.width(message)) / 2,
                    topPos + GRID_Y + 10, 0xFFE8E8E8, true);
        } else {
            int first = scroll * COLUMNS;
            for (int cell = 0; cell < COLUMNS * ROWS; cell++) {
                int index = first + cell;
                if (index >= entries.size()) {
                    break;
                }
                ShopCatalogPayload.Entry entry = entries.get(index);
                int x = leftPos + GRID_X + (cell % COLUMNS) * CELL_WIDTH;
                int y = topPos + GRID_Y + (cell / COLUMNS) * CELL_HEIGHT;

                boolean hovered = isOver(mouseX, mouseY, x, y,
                        CELL_WIDTH - 2, CELL_HEIGHT - 2);
                boolean chosen = entry.number() == selected;

                graphics.fill(x, y, x + CELL_WIDTH - 2, y + CELL_HEIGHT - 2,
                        hovered ? 0x50FFFFFF : 0x20000000);
                if (chosen) {
                    // An outline rather than a wash, so the selection is still
                    // visible under the cursor's own highlight.
                    graphics.renderOutline(x, y, CELL_WIDTH - 2, CELL_HEIGHT - 2, 0xFFE0B33A);
                }

                graphics.renderItem(entry.stack(), x + 2, y + 2);
                graphics.renderItemDecorations(font, entry.stack(), x + 2, y + 2);
                // The number small and above, the price below. Both on one
                // line ran past the cell as soon as a price hit five figures,
                // and the number is what an admin scans for.
                graphics.drawString(font, "#" + entry.number(),
                        x + 21, y + 2, 0xFFB0B0B0, false);
                graphics.drawString(font, compact(entry.price()),
                        x + 21, y + 11, 0xFFFFFFFF, false);
                if (!entry.onSale()) {
                    // Dimmed as the customers see it: off, or sold out.
                    graphics.fill(x, y, x + CELL_WIDTH - 2, y + CELL_HEIGHT - 2, 0x90303030);
                }
            }
        }

        drawControls(graphics, mouseX, mouseY);
        drawActionRow(graphics, mouseX, mouseY);
        drawTerms(graphics, mouseX, mouseY);

        int totalRows = (catalog.size() + COLUMNS - 1) / COLUMNS;
        if (scroll > 0) {
            graphics.drawString(font, "▲",
                    leftPos + GRID_X + COLUMNS * CELL_WIDTH + 2, topPos + GRID_Y,
                    Panels.label(), false);
        }
        if (scroll + ROWS < totalRows) {
            graphics.drawString(font, "▼",
                    leftPos + GRID_X + COLUMNS * CELL_WIDTH + 2,
                    // The bottom of the recess, not the top of the last row.
                    // The glyph is 8 tall, so it sits inside the well the same
                    // way the up arrow does at the other end.
                    topPos + GRID_Y + ROWS * CELL_HEIGHT - 9, Panels.label(), false);
        }
    }

    private void drawControls(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean typing = search != null && search.isFocused();
        drawControl(graphics, leftPos + GRID_X, topPos + SEARCH_Y, SEARCH_W,
                typing ? 0xFFFFFFFF : 0xFFA0A0A0, 0xFF000000, typing);

        int sortX = sortButtonX();
        boolean sortHovered = isOver(mouseX, mouseY, sortX, topPos + SEARCH_Y,
                SORT_W, SEARCH_H);
        drawControl(graphics, sortX, topPos + SEARCH_Y, SORT_W,
                Panels.OUTLINE, 0xFF555555, sortHovered);
        Component sortLabel = Component.literal(catalog.descending() ? "▼ " : "▲ ")
                .append(catalog.sortBy().label());
        graphics.drawString(font, sortLabel,
                sortX + (SORT_W - font.width(sortLabel)) / 2,
                topPos + SEARCH_Y + TEXT_Y, 0xFFFFFFFF, false);

        int pinX = rememberButtonX();
        boolean pinHovered = isOver(mouseX, mouseY, pinX, topPos + SEARCH_Y,
                REMEMBER_W, SEARCH_H);
        drawControl(graphics, pinX, topPos + SEARCH_Y, REMEMBER_W,
                Panels.OUTLINE, 0xFF555555, pinHovered);
        graphics.drawString(font, "P", pinX + (REMEMBER_W - font.width("P")) / 2,
                topPos + SEARCH_Y + TEXT_Y,
                ScreenPreferences.rememberSearch() ? 0xFFE0B33A : 0xFF9A9A9A, false);

        if (search != null) {
            search.render(graphics, mouseX, mouseY, 0f);
        }
    }

    private void drawActionRow(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean typing = price != null && price.isFocused();
        drawControl(graphics, leftPos + PRICE_X, topPos + ACTION_Y, PRICE_W,
                typing ? 0xFFFFFFFF : 0xFFA0A0A0, 0xFF000000, typing);

        int x = leftPos + ACTION_X;
        int y = topPos + ACTION_Y;
        boolean hovered = isOver(mouseX, mouseY, x, y, ACTION_W, SEARCH_H);
        boolean ready = canAct();
        drawControl(graphics, x, y, ACTION_W, Panels.OUTLINE,
                ready ? 0xFF2E7D32 : 0xFF4A4A4A, hovered && ready);

        Component label = Component.translatable(selected == NO_SELECTION
                ? "economycore.admin.shop.add"
                : "economycore.admin.shop.set_price");
        graphics.drawString(font, label, x + (ACTION_W - font.width(label)) / 2,
                y + TEXT_Y, ready ? 0xFFFFFFFF : 0xFF9A9A9A, false);

        if (price != null) {
            price.render(graphics, mouseX, mouseY, 0f);
        }

        drawRemoveButton(graphics, mouseX, mouseY);
    }

    /**
     * Drawn only with an entry selected, since there is nothing else it could
     * remove, and red once armed so that the second click is plainly different
     * from the first.
     */
    private void drawRemoveButton(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selected == NO_SELECTION) {
            return;
        }
        int x = leftPos + ACTION_X;
        int y = topPos + REMOVE_Y;
        boolean armed = isArmed();
        boolean hovered = isOver(mouseX, mouseY, x, y, ACTION_W, SEARCH_H);
        drawControl(graphics, x, y, ACTION_W, Panels.OUTLINE,
                armed ? 0xFFB71C1C : 0xFF6D4C41, hovered);

        Component label = Component.translatable(armed
                ? "economycore.admin.shop.remove_confirm"
                : "economycore.admin.shop.remove");
        graphics.drawString(font, label, x + (ACTION_W - font.width(label)) / 2,
                y + TEXT_Y, 0xFFFFFFFF, false);
    }

    /**
     * Whether only the terms of entries changed between two catalogs: the same
     * number of entries, the same item in each place. Then every number still
     * points where it did, and a choice can stay made.
     */
    private static boolean stillInPlace(List<ShopCatalogPayload.Entry> before, List<ShopCatalogPayload.Entry> after) {
        if (before.size() != after.size()) {
            return false;
        }
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i).number() != after.get(i).number()
                    || !ItemStack.isSameItemSameComponents(before.get(i).stack(), after.get(i).stack())) {
                return false;
            }
        }
        return true;
    }

    /** Whether the remove button is waiting for its second click. */
    private boolean isArmed() {
        return System.currentTimeMillis() < confirmUntil;
    }

    /** Whether the action button would do anything if pressed. */
    private boolean canAct() {
        if (typedPrice() < 1) {
            return false;
        }
        return selected != NO_SELECTION || !menu.held().isEmpty();
    }

    private long typedPrice() {
        if (price == null || price.getValue().isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(price.getValue());
        } catch (NumberFormatException tooLong) {
            return 0;
        }
    }

    private void drawControl(GuiGraphics graphics, int x, int y, int width,
                             int outline, int face, boolean hovered) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + SEARCH_H + 1, outline);
        graphics.fill(x, y, x + width, y + SEARCH_H, hovered ? face + 0x00191919 : face);
    }

    private int sortButtonX() {
        return leftPos + GRID_X + SEARCH_W + 4;
    }

    private int rememberButtonX() {
        return sortButtonX() + SORT_W + 3;
    }

    @Override
    protected void renderTooltip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);

        if (isOver(mouseX, mouseY, leftPos + ACTION_X, topPos + ACTION_Y,
                ACTION_W, SEARCH_H)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(selected == NO_SELECTION
                    ? "economycore.admin.shop.add"
                    : "economycore.admin.shop.set_price"));
            lines.add(Component.translatable(selected == NO_SELECTION
                            ? "economycore.admin.shop.add_hint"
                            : "economycore.admin.shop.set_price_hint")
                    .withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }

        if (selected != NO_SELECTION && isOver(mouseX, mouseY,
                leftPos + ACTION_X, topPos + REMOVE_Y, ACTION_W, SEARCH_H)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(isArmed()
                    ? "economycore.admin.shop.remove_confirm"
                    : "economycore.admin.shop.remove"));
            lines.add(Component.translatable("economycore.admin.shop.remove_hint")
                    .withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }

        if (termsVisible()) {
            String hint = null;
            if (isOver(mouseX, mouseY, leftPos + TOGGLE_X, topPos + REMOVE_Y, TOGGLE_W, SEARCH_H)) {
                hint = "economycore.admin.shop.toggle_hint";
            } else if (isOver(mouseX, mouseY, leftPos + termX(0), topPos + TERMS_Y, TERM_W, SEARCH_H)) {
                hint = "economycore.admin.shop.stock_hint";
            } else if (isOver(mouseX, mouseY, leftPos + termX(1), topPos + TERMS_Y, TERM_W, SEARCH_H)) {
                hint = "economycore.admin.shop.restock_hint";
            } else if (isOver(mouseX, mouseY, leftPos + termX(2), topPos + TERMS_Y, TERM_W, SEARCH_H)) {
                hint = "economycore.admin.shop.limit_hint";
            } else if (isOver(mouseX, mouseY, leftPos + APPLY_X, topPos + TERMS_Y, APPLY_W, SEARCH_H)) {
                hint = "economycore.admin.shop.apply_hint";
            }
            if (hint != null) {
                // Wrapped: these say more than a line's worth.
                graphics.renderTooltip(font, font.split(
                        Component.translatable(hint).withStyle(ChatFormatting.GRAY), 180), mouseX, mouseY);
                return;
            }
        }

        List<ShopCatalogPayload.Entry> entries = catalog.entries();
        int first = scroll * COLUMNS;
        for (int cell = 0; cell < COLUMNS * ROWS; cell++) {
            int index = first + cell;
            if (index >= entries.size()) {
                break;
            }
            int x = leftPos + GRID_X + (cell % COLUMNS) * CELL_WIDTH;
            int y = topPos + GRID_Y + (cell / COLUMNS) * CELL_HEIGHT;
            if (!isOver(mouseX, mouseY, x, y, CELL_WIDTH - 2, CELL_HEIGHT - 2)) {
                continue;
            }
            ShopCatalogPayload.Entry entry = entries.get(index);
            List<Component> lines = new ArrayList<>(
                    getTooltipFromItem(Minecraft.getInstance(), entry.stack()));
            lines.add(Component.translatable("economycore.admin.shop.entry_number",
                    entry.number(), format(entry.price())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(entry.enabled()
                            ? "economycore.command.shop.info_on" : "economycore.command.shop.info_off")
                    .withStyle(entry.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (entry.stock() != ShopEntry.UNLIMITED) {
                lines.add(Component.translatable("economycore.admin.shop.stock_of",
                        format(entry.stock()), format(entry.capacity())).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("economycore.admin.shop.entry_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox field : new EditBox[]{search, price, stock, restock, limit}) {
            if (field != null && field.isFocused()) {
                if (keyCode == InputConstants.KEY_ESCAPE) {
                    field.setFocused(false);
                    setFocused(null);
                    return true;
                }
                if (field.keyPressed(keyCode, scanCode, modifiers)
                        || field.canConsumeInput()) {
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (focusField(search, leftPos + GRID_X, topPos + SEARCH_Y, SEARCH_W, mx, my, button)
                || focusField(price, leftPos + PRICE_X, topPos + ACTION_Y,
                PRICE_W, mx, my, button)) {
            return true;
        }

        if (isOver(mx, my, sortButtonX(), topPos + SEARCH_Y, SORT_W, SEARCH_H)) {
            if (button == 1) {
                catalog.toggleDirection();
            } else {
                catalog.cycleSort();
            }
            ScreenPreferences.setShopSort(catalog.sortBy(), catalog.descending());
            playClick();
            scroll = 0;
            return true;
        }
        if (isOver(mx, my, rememberButtonX(), topPos + SEARCH_Y, REMEMBER_W, SEARCH_H)) {
            ScreenPreferences.setRememberSearch(!ScreenPreferences.rememberSearch());
            playClick();
            return true;
        }
        if (termsVisible()) {
            if (isOver(mx, my, leftPos + TOGGLE_X, topPos + REMOVE_Y, TOGGLE_W, SEARCH_H)) {
                toggleSale();
                return true;
            }
            if (focusField(stock, leftPos + termX(0), topPos + TERMS_Y, TERM_W, mx, my, button)
                    || focusField(restock, leftPos + termX(1), topPos + TERMS_Y, TERM_W, mx, my, button)
                    || focusField(limit, leftPos + termX(2), topPos + TERMS_Y, TERM_W, mx, my, button)) {
                return true;
            }
            if (isOver(mx, my, leftPos + APPLY_X, topPos + TERMS_Y, APPLY_W, SEARCH_H)) {
                applyTerms();
                return true;
            }
        }
        if (isOver(mx, my, leftPos + ACTION_X, topPos + ACTION_Y, ACTION_W, SEARCH_H)) {
            act();
            return true;
        }
        if (selected != NO_SELECTION
                && isOver(mx, my, leftPos + ACTION_X, topPos + REMOVE_Y, ACTION_W, SEARCH_H)) {
            remove();
            return true;
        }

        if (button == 0 && clickedEntry(mx, my)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean focusField(EditBox field, int x, int y, int width,
                               int mouseX, int mouseY, int button) {
        if (field == null || !isOver(mouseX, mouseY, x, y, width, SEARCH_H)) {
            return false;
        }
        if (button == 1) {
            field.setValue("");
            return true;
        }
        setFocused(field);
        field.setFocused(true);
        return true;
    }

    /** Selects an entry or deselects it when it was already chosen. */
    private boolean clickedEntry(int mouseX, int mouseY) {
        List<ShopCatalogPayload.Entry> entries = catalog.entries();
        int first = scroll * COLUMNS;
        for (int cell = 0; cell < COLUMNS * ROWS; cell++) {
            int index = first + cell;
            if (index >= entries.size()) {
                break;
            }
            int x = leftPos + GRID_X + (cell % COLUMNS) * CELL_WIDTH;
            int y = topPos + GRID_Y + (cell / COLUMNS) * CELL_HEIGHT;
            if (!isOver(mouseX, mouseY, x, y, CELL_WIDTH - 2, CELL_HEIGHT - 2)) {
                continue;
            }
            ShopCatalogPayload.Entry entry = entries.get(index);
            selected = entry.number() == selected ? NO_SELECTION : entry.number();
            price.setValue(selected == NO_SELECTION ? "" : String.valueOf(entry.price()));
            showTerms(selected == NO_SELECTION ? null : entry);
            // Changing the subject disarms the button. Otherwise, a click
            // meant for one entry could land, armed, on the next.
            confirmUntil = 0;
            playClick();
            return true;
        }
        return false;
    }

    /**
     * Arms the remove button, then removes on the second click.
     * <p>
     * The removal is deliberately two clicks. Entry numbers are positions in
     * a list, so deleting one moves every entry after it, and an accidental
     * deletion is not undone by adding the item back.
     */
    private void remove() {
        if (selected == NO_SELECTION) {
            return;
        }
        if (!isArmed()) {
            confirmUntil = System.currentTimeMillis() + CONFIRM_MILLIS;
            playClick();
            return;
        }
        PacketDistributor.sendToServer(AdminShopActionPayload.remove(selected));
        selected = NO_SELECTION;
        price.setValue("");
        showTerms(null);
        confirmUntil = 0;
        playClick();
    }

    private void act() {
        if (!canAct()) {
            return;
        }
        PacketDistributor.sendToServer(selected == NO_SELECTION
                ? AdminShopActionPayload.add(typedPrice())
                : AdminShopActionPayload.reprice(selected, typedPrice()));
        selected = NO_SELECTION;
        price.setValue("");
        showTerms(null);
        playClick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int totalRows = (catalog.size() + COLUMNS - 1) / COLUMNS;
        if (totalRows > ROWS) {
            scroll = Math.clamp(scroll - (int) Math.signum(deltaY), 0, totalRows - ROWS);
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean isOver(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static String format(long credits) {
        return com.github.arrivedbog593.economycore.api.CreditFormat.of(credits);
    }

    /** Short enough for a grid cell; the tooltip carries the exact figure. */
    private static String compact(long credits) {
        if (credits >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", credits / 1_000_000.0);
        }
        return format(credits);
    }
}