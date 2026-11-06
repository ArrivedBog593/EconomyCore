package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.api.CreditMath;
import com.github.arrivedbog593.economycore.engine.BankMovement;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.engine.InterestTranches;
import com.github.arrivedbog593.economycore.platform.menu.BankMenu;
import com.github.arrivedbog593.economycore.platform.network.BankActionPayload;
import com.github.arrivedbog593.economycore.platform.network.BankStatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The banker's window: SkyBlock's bank, in a panel of our own.
 * <p>
 * Half and half on purpose. From SkyBlock comes what players who know it will
 * reach for: a row of items to click — a chest to deposit, a dispenser to
 * withdraw, a map for recent movements, a torch for how the bank works —
 * each with a tooltip saying what it will do and with how much, and behind
 * deposit and withdraw the same shortcuts, all, half, a fifth, an amount.
 * From the panel comes what a chest cannot show: both balances in plain view
 * the whole time, and a field to type an amount into instead of a sign.
 * <p>
 * The figures are the menu's, kept in step by the server every tick, so they
 * move the moment a deposit lands. Shares are sent as shares and worked out
 * on the server, from the balance as it is when the click arrives. What
 * would be refused is grayed out here, and the server checks again
 * regardless, saying why in the action bar if it refuses.
 */
public class BankScreen extends AbstractContainerScreen<BankMenu> {

    private enum Mode { MAIN, DEPOSIT, WITHDRAW }

    /** One of the row of items: what it shows, what it says, what clicking it does. */
    private record Tile(ItemStack icon, Component label, boolean enabled,
                        List<Component> tooltip, Runnable action) {
    }

    private static final int WIDTH = 200;
    private static final int HEIGHT = 120;
    private static final int MARGIN = 8;
    private static final int INNER_W = WIDTH - 2 * MARGIN;
    private static final int COLUMN_W = (INNER_W - MARGIN) / 2;
    private static final int RIGHT_X = MARGIN + COLUMN_W + MARGIN;

    private static final int POCKET_Y = 18;
    private static final int POCKET_H = 30;
    private static final int TILE_Y = 54;
    private static final int TILE = 26;
    private static final int TILE_LABEL_Y = TILE_Y + TILE + 2;
    private static final float TILE_LABEL_SCALE = 0.75F;
    private static final int ROW_Y = 96;
    private static final int ROW_H = 14;
    private static final int CONFIRM_W = 60;
    private static final int TEXT_Y = (ROW_H - 8) / 2 + 1;

    private static final int DEPOSIT_FACE = 0xFF2E7D32;
    private static final int WITHDRAW_FACE = 0xFF8D6E1A;
    private static final int DISABLED_FACE = 0xFF4A4A4A;
    private static final int PURSE_TEXT = 0xFF2E7D32;
    private static final int SAVINGS_TEXT = 0xFF1F4D2B;

    private static final int ALL = 100;
    private static final int HALF = 50;
    private static final int FIFTH = 20;

    private Mode mode = Mode.MAIN;
    private EditBox amount;

    public BankScreen(BankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        String carried = amount == null ? "" : amount.getValue();
        amount = new EditBox(font, leftPos + MARGIN + 4, topPos + ROW_Y + TEXT_Y,
                INNER_W - CONFIRM_W - 12, 8, Component.translatable("economycore.my_bank.amount"));
        amount.setBordered(false);
        amount.setHint(Component.translatable("economycore.my_bank.amount"));
        // Digits only, and never more than an account can hold: thirteen digits.
        amount.setFilter(text -> text.isEmpty() || text.matches("\\d{1,13}"));
        amount.setValue(carried);
        amount.visible = mode != Mode.MAIN;
        addRenderableWidget(amount);
    }

    private void setMode(Mode next) {
        mode = next;
        amount.visible = next != Mode.MAIN;
        amount.setFocused(false);
        if (next == Mode.MAIN) {
            amount.setValue("");
        }
    }

    // --- The tiles ---------------------------------------------------------------------------

    private List<Tile> tiles() {
        long free = menu.free();
        long saved = menu.savings();
        return switch (mode) {
            case MAIN -> List.of(
                    new Tile(new ItemStack(Items.CHEST), text("tile.deposit"), true, depositLore(), () -> setMode(Mode.DEPOSIT)),
                    new Tile(new ItemStack(Items.DISPENSER), text("tile.withdraw"), true, withdrawLore(), () -> setMode(Mode.WITHDRAW)),
                    new Tile(new ItemStack(Items.FILLED_MAP), text("tile.recent"), true, recentLore(), () -> {
                    }),
                    new Tile(new ItemStack(Items.REDSTONE_TORCH), text("tile.info"), true, infoLore(), () -> {
                    }));
            case DEPOSIT -> List.of(
                    shareTile(Items.CHEST, 64, "tile.all", true, ALL, free),
                    shareTile(Items.CHEST, 32, "tile.half", true, HALF, free),
                    amountTile(),
                    backTile());
            case WITHDRAW -> List.of(
                    shareTile(Items.DISPENSER, 64, "tile.all", false, ALL, saved),
                    shareTile(Items.DISPENSER, 32, "tile.half", false, HALF, saved),
                    shareTile(Items.DISPENSER, 20, "tile.fifth", false, FIFTH, saved),
                    amountTile(),
                    backTile());
        };
    }

    /** All, half or a fifth of what there is to move, with the figure it comes to. */
    private Tile shareTile(net.minecraft.world.item.Item item, int count, String key, boolean deposit,
                           int percent, long of) {
        long share = CreditMath.percentOf(of, percent, RoundingMode.FLOOR);
        List<Component> lore = new ArrayList<>();
        lore.add(text(key).withStyle(ChatFormatting.GREEN));
        lore.add(text(deposit ? "deposit" : "withdraw").withStyle(ChatFormatting.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(text("bank_balance", gold(menu.savings())).withStyle(ChatFormatting.GRAY));
        lore.add(text(deposit ? "to_deposit" : "to_withdraw", gold(share)).withStyle(ChatFormatting.GRAY));
        lore.add(Component.empty());
        lore.add(share > 0
                ? text(deposit ? "click_deposit" : "click_withdraw").withStyle(ChatFormatting.YELLOW)
                : text("nothing").withStyle(ChatFormatting.RED));
        return new Tile(new ItemStack(item, count), text(key), share > 0, lore,
                () -> send(BankActionPayload.share(deposit, percent)));
    }

    private Tile amountTile() {
        List<Component> lore = List.of(
                text("tile.amount").withStyle(ChatFormatting.GREEN),
                text("bank_balance", gold(menu.savings())).withStyle(ChatFormatting.GRAY),
                Component.empty(),
                text("type_amount").withStyle(ChatFormatting.YELLOW));
        return new Tile(new ItemStack(Items.OAK_SIGN), text("tile.amount"), true, lore, () -> {
            setFocused(amount);
            amount.setFocused(true);
        });
    }

    private Tile backTile() {
        return new Tile(new ItemStack(Items.ARROW), text("tile.back"), true,
                List.of(text("tile.back").withStyle(ChatFormatting.GREEN)), () -> setMode(Mode.MAIN));
    }

    private List<Component> depositLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(text("tile.deposit_title").withStyle(ChatFormatting.GREEN));
        lore.add(text("bank_balance", gold(menu.savings())).withStyle(ChatFormatting.GRAY));
        lore.add(Component.empty());
        lore.add(text("deposit_lore").withStyle(ChatFormatting.GRAY));
        BankStatePayload state = ClientBankState.state();
        if (state != null && !state.tranches().isEmpty()) {
            InterestTranches.Tranche first = state.tranches().getFirst();
            lore.add(Component.empty());
            lore.add(text("deposit_interest", percent(first.basisPoints()), gold(first.upTo()))
                    .withStyle(ChatFormatting.GRAY));
            lore.add(text("interest_in", duration(ClientBankState.millisUntilInterest()))
                    .withStyle(ChatFormatting.AQUA));
        }
        lore.add(Component.empty());
        lore.add(text("click_open_deposit").withStyle(ChatFormatting.YELLOW));
        return lore;
    }

    private List<Component> withdrawLore() {
        return List.of(
                text("tile.withdraw_title").withStyle(ChatFormatting.GREEN),
                text("bank_balance", gold(menu.savings())).withStyle(ChatFormatting.GRAY),
                Component.empty(),
                text("withdraw_lore").withStyle(ChatFormatting.GRAY),
                Component.empty(),
                text("click_open_withdraw").withStyle(ChatFormatting.YELLOW));
    }

    private List<Component> recentLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(text("tile.recent").withStyle(ChatFormatting.GREEN));
        BankStatePayload state = ClientBankState.state();
        List<BankMovement> recent = state == null ? List.of() : state.recent();
        if (recent.isEmpty()) {
            lore.add(text("recent.none").withStyle(ChatFormatting.GRAY));
            return lore;
        }
        long now = System.currentTimeMillis();
        // Newest first, the way a statement is read.
        for (int i = recent.size() - 1; i >= 0; i--) {
            BankMovement movement = recent.get(i);
            String key = switch (movement.kind()) {
                case DEPOSIT -> "recent.deposit";
                case WITHDRAWAL -> "recent.withdrawal";
                case INTEREST -> "recent.interest";
            };
            ChatFormatting sign = movement.kind() == BankMovement.Kind.WITHDRAWAL
                    ? ChatFormatting.RED : ChatFormatting.GREEN;
            lore.add(text(key, Component.literal(CreditFormat.of(movement.amount())).withStyle(sign),
                    duration(now - movement.time())).withStyle(ChatFormatting.GRAY));
        }
        return lore;
    }

    private List<Component> infoLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(text("tile.info").withStyle(ChatFormatting.GREEN));
        lore.add(text("info.safe").withStyle(ChatFormatting.GRAY));
        BankStatePayload state = ClientBankState.state();
        if (state == null) {
            return lore;
        }
        if (state.deathLossPercent() > 0) {
            lore.add(text(state.combatOnly() ? "info.death_combat" : "info.death", state.deathLossPercent())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lore.add(text("info.no_death").withStyle(ChatFormatting.GRAY));
        }
        lore.add(Component.empty());
        if (state.tranches().isEmpty()) {
            lore.add(text("info.interest_off").withStyle(ChatFormatting.GRAY));
            return lore;
        }
        lore.add(text("info.interest_every", duration(state.interestPeriod())).withStyle(ChatFormatting.GRAY));
        lore.add(text("interest_in", duration(ClientBankState.millisUntilInterest()))
                .withStyle(ChatFormatting.AQUA));
        lore.add(Component.empty());
        lore.add(text("info.tranches").withStyle(ChatFormatting.GOLD));
        long from = 0;
        for (InterestTranches.Tranche tranche : state.tranches()) {
            lore.add((from == 0
                    ? text("info.tranche_first", gold(tranche.upTo()), percent(tranche.basisPoints()))
                    : text("info.tranche_next", gold(from), gold(tranche.upTo()), percent(tranche.basisPoints())))
                    .withStyle(ChatFormatting.YELLOW));
            from = tranche.upTo();
        }
        long max = new InterestTranches(state.tranches()).maxInterest();
        lore.add(Component.empty());
        lore.add(text("info.max_interest", gold(max), gold(from)).withStyle(ChatFormatting.GRAY));
        return lore;
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panels.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        Panels.recess(graphics, leftPos + MARGIN, topPos + POCKET_Y, COLUMN_W, POCKET_H);
        Panels.recess(graphics, leftPos + RIGHT_X, topPos + POCKET_Y, COLUMN_W, POCKET_H);
        if (mode != Mode.MAIN) {
            int fieldRight = leftPos + MARGIN + INNER_W - CONFIRM_W - 4;
            graphics.fill(leftPos + MARGIN - 1, topPos + ROW_Y - 1, fieldRight + 1, topPos + ROW_Y + ROW_H + 1,
                    Panels.OUTLINE);
            graphics.fill(leftPos + MARGIN, topPos + ROW_Y, fieldRight, topPos + ROW_Y + ROW_H, 0xFF000000);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Tile> tiles = tiles();
        Tile hovered = null;
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            int x = leftPos + tileX(i, tiles.size());
            int y = topPos + TILE_Y;
            boolean over = isOver(mouseX, mouseY, x, y, TILE, TILE);
            Panels.recess(graphics, x, y, TILE, TILE);
            if (over && tile.enabled()) {
                graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, Panels.HOVER_WASH);
            }
            graphics.renderItem(tile.icon(), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
            graphics.renderItemDecorations(font, tile.icon(), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
            if (!tile.enabled()) {
                graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0x80555555);
            }
            drawTileLabel(graphics, tile.label(), x + TILE / 2, topPos + TILE_LABEL_Y);
            if (over) {
                hovered = tile;
            }
        }
        if (mode != Mode.MAIN) {
            long typed = typed();
            boolean deposit = mode == Mode.DEPOSIT;
            boolean enabled = typed > 0 && typed <= (deposit ? menu.free() : menu.savings());
            drawConfirm(graphics, deposit, enabled, mouseX, mouseY);
        } else {
            drawHint(graphics);
        }
        if (hovered != null) {
            graphics.renderComponentTooltip(font, hovered.tooltip(), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        Component heading = switch (mode) {
            case MAIN -> title;
            case DEPOSIT -> title.copy().append(" · ").append(text("deposit"));
            case WITHDRAW -> title.copy().append(" · ").append(text("withdraw"));
        };
        graphics.drawString(font, heading, titleLabelX, titleLabelY, Panels.LABEL_TEXT, false);
        drawPocket(graphics, MARGIN, text("purse"), menu.purse(), PURSE_TEXT);
        drawPocket(graphics, RIGHT_X, text("savings"), menu.savings(), SAVINGS_TEXT);
        // Only when it explains something: a purse whose "all" moves less than
        // it shows, because part of it is riding on a table.
        long promised = menu.purse() - menu.free();
        if (promised > 0) {
            graphics.drawString(font, text("promised", CreditFormat.of(promised)),
                    MARGIN + 3, POCKET_Y + POCKET_H - 9, Panels.LABEL_TEXT, false);
        }
    }

    private void drawPocket(GuiGraphics graphics, int x, Component label, long credits, int color) {
        graphics.drawString(font, label, x + 3, POCKET_Y + 3, Panels.LABEL_TEXT, false);
        String figure = CreditFormat.of(credits);
        // Shrinks rather than overflows: at the cap the figure is wider than the well.
        float scale = Math.min(1.0F, (COLUMN_W - 6) / (float) font.width(figure));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 3, POCKET_Y + 13, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, figure, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawTileLabel(GuiGraphics graphics, Component label, int centerX, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0);
        graphics.pose().scale(TILE_LABEL_SCALE, TILE_LABEL_SCALE, 1.0F);
        graphics.drawString(font, label, -font.width(label) / 2, 0, Panels.LABEL_TEXT, false);
        graphics.pose().popPose();
    }

    private void drawConfirm(GuiGraphics graphics, boolean deposit, boolean enabled, int mouseX, int mouseY) {
        int x = leftPos + MARGIN + INNER_W - CONFIRM_W;
        int y = topPos + ROW_Y;
        boolean hovered = enabled && isOver(mouseX, mouseY, x, y, CONFIRM_W, ROW_H);
        graphics.fill(x - 1, y - 1, x + CONFIRM_W + 1, y + ROW_H + 1, Panels.OUTLINE);
        int face = enabled ? (deposit ? DEPOSIT_FACE : WITHDRAW_FACE) : DISABLED_FACE;
        graphics.fill(x, y, x + CONFIRM_W, y + ROW_H, hovered ? face + 0x00191919 : face);
        Component label = text(deposit ? "deposit" : "withdraw");
        graphics.drawString(font, label, x + (CONFIRM_W - font.width(label)) / 2, y + TEXT_Y,
                enabled ? 0xFFFFFFFF : 0xFF9A9A9A, false);
    }

    /** On the main page the row under the tiles says when interest is next paid, if it is. */
    private void drawHint(GuiGraphics graphics) {
        long until = ClientBankState.millisUntilInterest();
        BankStatePayload state = ClientBankState.state();
        if (until < 0 || state == null || state.tranches().isEmpty()) {
            return;
        }
        Component hint = text("interest_in", duration(until));
        graphics.drawString(font, hint, leftPos + (WIDTH - font.width(hint)) / 2, topPos + ROW_Y + TEXT_Y,
                Panels.LABEL_TEXT, false);
    }

    private static int tileX(int index, int count) {
        int pitch = INNER_W / count;
        return MARGIN + index * pitch + (pitch - TILE) / 2;
    }

    // --- Input -------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX;
        int y = (int) mouseY;
        List<Tile> tiles = tiles();
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            if (isOver(x, y, leftPos + tileX(i, tiles.size()), topPos + TILE_Y, TILE, TILE)) {
                if (tile.enabled()) {
                    playClick();
                    tile.action().run();
                }
                return true;
            }
        }
        if (mode != Mode.MAIN) {
            if (isOver(x, y, leftPos + MARGIN, topPos + ROW_Y, INNER_W - CONFIRM_W - 4, ROW_H)) {
                setFocused(amount);
                amount.setFocused(true);
                return true;
            }
            amount.setFocused(false);
            if (isOver(x, y, leftPos + MARGIN + INNER_W - CONFIRM_W, topPos + ROW_Y, CONFIRM_W, ROW_H)) {
                confirm();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void confirm() {
        long typed = typed();
        boolean deposit = mode == Mode.DEPOSIT;
        if (typed <= 0 || typed > (deposit ? menu.free() : menu.savings())) {
            return;
        }
        playClick();
        send(BankActionPayload.amount(deposit, typed));
        amount.setValue("");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (amount != null && amount.isFocused()) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                amount.setFocused(false);
                setFocused(null);
                return true;
            }
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                confirm();
                return true;
            }
            // Typing a number must not close the window on the inventory key.
            if (amount.keyPressed(keyCode, scanCode, modifiers) || amount.canConsumeInput()) {
                return true;
            }
        }
        if (keyCode == InputConstants.KEY_ESCAPE && mode != Mode.MAIN) {
            // Back a page first, the way SkyBlock's arrow does; Escape again closes.
            setMode(Mode.MAIN);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(BankActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /** What the field says, or zero for nothing usable. */
    private long typed() {
        if (amount == null || amount.getValue().isEmpty()) {
            return 0;
        }
        try {
            long value = Long.parseLong(amount.getValue());
            return value <= CreditAccount.MAX_BALANCE ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- Text --------------------------------------------------------------------------------

    private static MutableComponent text(String key, Object... args) {
        return Component.translatable("economycore.my_bank." + key, args);
    }

    private static Component gold(long credits) {
        return Component.literal(CreditFormat.of(credits)).withStyle(ChatFormatting.GOLD);
    }

    /** A rate in basis points as a percentage: 200 is "2", 125 is "1.25". */
    private static String percent(long basisPoints) {
        return BigDecimal.valueOf(basisPoints).movePointLeft(2).stripTrailingZeros().toPlainString();
    }

    /** A span of time as a person would say it: a few seconds, 5 min, 7 h, 2 d. */
    private static Component duration(long millis) {
        long minutes = millis / 60_000L;
        if (minutes < 1) {
            return text("time.seconds");
        }
        if (minutes < 60) {
            return text("time.minutes", minutes);
        }
        long hours = (minutes + 59) / 60;
        if (hours < 48) {
            return text("time.hours", hours);
        }
        return text("time.days", hours / 24);
    }

    private static boolean isOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
