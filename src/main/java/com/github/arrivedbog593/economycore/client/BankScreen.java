package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.EconomyCore;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The banker's window: tabs down the left, the chosen page on the right, and
 * both balances always at the top of it.
 * <p>
 * Dark, unlike the vanilla-gray shop and exchange: a bank reads better as a
 * quiet room than as a crafting table, and it is what SkyBlock players coming
 * from a dark-mode client expect. Its icons are its own — a safe with money
 * going in, the same safe with money coming out, a ledger, an "i" — rather
 * than a chest and a dispenser standing in for them.
 * <p>
 * Nothing hides in a tooltip. Recent movements and how the bank works each
 * have a page, so they can be read without holding the mouse still.
 * <p>
 * The balances are the menu's, kept in step by the server every tick, so they
 * move the moment a deposit lands. Shares are sent as shares and worked out
 * on the server, from the balance as it is when the click arrives; an amount
 * may be typed with a k or an m, as in SkyBlock. What would be refused is
 * grayed out here, and the server checks again regardless, saying why in the
 * action bar if it refuses.
 */
public class BankScreen extends AbstractContainerScreen<BankMenu> {

    private enum Tab {
        DEPOSIT("deposit"), WITHDRAW("withdraw"), RECENT("recent"), INFO("info");

        final String key;
        final ResourceLocation icon;

        Tab(String key) {
            this.key = key;
            this.icon = ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID,
                    "textures/gui/bank/" + key + ".png");
        }
    }

    /** A share to move: all of it, half, a fifth. */
    private record Share(String key, int percent) {
    }

    private static final ResourceLocation COIN =
            ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "textures/gui/bank/coin.png");

    private static final int WIDTH = 256;
    private static final int HEIGHT = 186;

    private static final int TAB_X = 6;
    private static final int TAB_W = 44;
    private static final int TAB_Y = 20;
    private static final int TAB_H = 36;

    private static final int CONTENT_X = TAB_X + TAB_W;
    private static final int CONTENT_Y = TAB_Y;
    private static final int CONTENT_R = WIDTH - 6;
    private static final int CONTENT_B = HEIGHT - 6;
    private static final int PAD = 6;
    private static final int INNER_X = CONTENT_X + PAD;
    private static final int INNER_W = CONTENT_R - PAD - INNER_X;

    private static final int BALANCE_Y = CONTENT_Y + PAD;
    private static final int BALANCE_H = 28;
    private static final int BALANCE_W = (INNER_W - PAD) / 2;
    private static final int RULE_Y = BALANCE_Y + BALANCE_H + 5;
    private static final int PAGE_Y = RULE_Y + 6;
    private static final int BODY_Y = PAGE_Y + 13;

    private static final int CARD_H = 30;
    private static final int ROW_Y = BODY_Y + CARD_H + 8;
    private static final int ROW_H = 16;
    private static final int CONFIRM_W = 62;
    private static final int FOOTER_Y = ROW_Y + ROW_H + 8;
    private static final int LINE = 10;

    // The dark room.
    private static final int FRAME = 0xFF0E0E10;
    private static final int PANEL = 0xFF232327;
    private static final int PANEL_LIGHT = 0xFF34343A;
    private static final int PANEL_DARK = 0xFF16161A;
    private static final int PAGE = 0xFF2C2C31;
    private static final int WELL = 0xFF1B1B1F;
    private static final int CARD = 0xFF38383F;
    private static final int CARD_HOVER = 0xFF46464F;
    private static final int RULE = 0xFF3E3E46;
    private static final int TEXT = 0xFFE6E6EA;
    private static final int MUTED = 0xFF9A9AA4;
    private static final int GOLD = 0xFFFFC94A;
    private static final int ACCENT = 0xFFD4AF37;
    private static final int DEPOSIT = 0xFF2E8B3E;
    private static final int WITHDRAW = 0xFFB07418;
    private static final int DISABLED = 0xFF3A3A40;

    private static final List<Share> DEPOSIT_SHARES = List.of(
            new Share("share.all_purse", 100), new Share("share.half_purse", 50));
    private static final List<Share> WITHDRAW_SHARES = List.of(
            new Share("share.all", 100), new Share("share.half", 50), new Share("share.fifth", 20));

    /** The page chosen last, so the banker opens where the player left off. */
    private static Tab remembered = Tab.DEPOSIT;

    private Tab tab = remembered;
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
        amount = new EditBox(font, leftPos + INNER_X + 4, topPos + ROW_Y + (ROW_H - 8) / 2,
                INNER_W - CONFIRM_W - 12, 9, text("amount"));
        amount.setBordered(false);
        amount.setTextColor(TEXT);
        amount.setHint(text("amount_hint").withStyle(ChatFormatting.DARK_GRAY));
        amount.setFilter(text -> text.isEmpty() || text.matches("\\d{0,13}(\\.\\d{0,2})?[kKmM]?"));
        amount.setValue(carried);
        addRenderableWidget(amount);
        showAmountField();
    }

    private void select(Tab next) {
        tab = next;
        remembered = next;
        amount.setValue("");
        amount.setFocused(false);
        showAmountField();
    }

    private void showAmountField() {
        amount.visible = tab == Tab.DEPOSIT || tab == Tab.WITHDRAW;
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Frame and a one-pixel bevel, as vanilla's panel has, in the dark.
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, FRAME);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, PANEL_LIGHT);
        graphics.fill(x + 2, y + 2, x + WIDTH - 1, y + HEIGHT - 1, PANEL_DARK);
        graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + HEIGHT - 2, PANEL);

        graphics.fill(x + CONTENT_X, y + CONTENT_Y, x + CONTENT_R, y + CONTENT_B, PAGE);

        for (Tab each : Tab.values()) {
            int top = y + TAB_Y + each.ordinal() * TAB_H;
            boolean selected = each == tab;
            boolean hovered = isOver(mouseX, mouseY, x + TAB_X, top, TAB_W, TAB_H - 2);
            if (selected) {
                // Joined to the page, with a strip of brass where it opens onto it.
                graphics.fill(x + TAB_X, top, x + CONTENT_X, top + TAB_H - 2, PAGE);
                graphics.fill(x + TAB_X, top, x + TAB_X + 2, top + TAB_H - 2, ACCENT);
            } else if (hovered) {
                graphics.fill(x + TAB_X, top, x + CONTENT_X - 2, top + TAB_H - 2, PANEL_LIGHT);
            }
            graphics.blit(each.icon, x + TAB_X + (TAB_W - 16) / 2, top + 4, 0, 0, 16, 16, 16, 16);
        }

        // The two balances, in wells, and a rule under them.
        drawBalance(graphics, x + INNER_X, y + BALANCE_Y, text("purse"), menu.purse());
        drawBalance(graphics, x + INNER_X + BALANCE_W + PAD, y + BALANCE_Y, text("savings"), menu.savings());
        graphics.fill(x + INNER_X, y + RULE_Y, x + INNER_X + INNER_W, y + RULE_Y + 1, RULE);

        if (amount.visible) {
            graphics.fill(x + INNER_X, y + ROW_Y, x + INNER_X + INNER_W - CONFIRM_W - 4, y + ROW_Y + ROW_H, WELL);
        }
    }

    private void drawBalance(GuiGraphics graphics, int x, int y, Component label, long credits) {
        graphics.fill(x, y, x + BALANCE_W, y + BALANCE_H, WELL);
        graphics.drawString(font, label, x + 5, y + 4, MUTED, false);
        graphics.blit(COIN, x + 5, y + 15, 0, 0, 9, 9, 9, 9);
        String figure = CreditFormat.of(credits);
        int room = BALANCE_W - 22;
        // Shrinks rather than overflows: at the cap the figure is wider than the well.
        float scale = Math.min(1.0F, room / (float) font.width(figure));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 17, y + 16, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, figure, 0, 0, GOLD, false);
        graphics.pose().popPose();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        switch (tab) {
            case DEPOSIT -> renderMove(graphics, mouseX, mouseY, true);
            case WITHDRAW -> renderMove(graphics, mouseX, mouseY, false);
            case RECENT -> renderRecent(graphics);
            case INFO -> renderInfo(graphics);
        }
        renderTabTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        for (Tab each : Tab.values()) {
            Component label = text("tile." + each.key);
            int top = TAB_Y + each.ordinal() * TAB_H;
            graphics.pose().pushPose();
            graphics.pose().translate(TAB_X + TAB_W / 2.0F, top + 23, 0);
            float scale = Math.min(0.75F, (TAB_W - 4) / (float) font.width(label));
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, label, -font.width(label) / 2, 0, each == tab ? TEXT : MUTED, false);
            graphics.pose().popPose();
        }
    }

    /** Deposit or withdraw: a card per share with what it comes to, then a typed amount. */
    private void renderMove(GuiGraphics graphics, int mouseX, int mouseY, boolean deposit) {
        long of = deposit ? menu.free() : menu.savings();
        drawPageTitle(graphics, text(deposit ? "deposit_lore" : "withdraw_lore"));

        List<Share> shares = deposit ? DEPOSIT_SHARES : WITHDRAW_SHARES;
        int cardW = (INNER_W - PAD * (shares.size() - 1)) / shares.size();
        for (int i = 0; i < shares.size(); i++) {
            Share share = shares.get(i);
            long sum = CreditMath.percentOf(of, share.percent(), RoundingMode.FLOOR);
            int x = leftPos + INNER_X + i * (cardW + PAD);
            int y = topPos + BODY_Y;
            boolean enabled = sum > 0;
            boolean hovered = enabled && isOver(mouseX, mouseY, x, y, cardW, CARD_H);
            graphics.fill(x, y, x + cardW, y + CARD_H, enabled ? (hovered ? CARD_HOVER : CARD) : DISABLED);
            graphics.fill(x, y + CARD_H - 2, x + cardW, y + CARD_H,
                    enabled ? (deposit ? DEPOSIT : WITHDRAW) : DISABLED);
            Component name = text(share.key());
            graphics.drawString(font, name, x + (cardW - font.width(name)) / 2, y + 5,
                    enabled ? TEXT : MUTED, false);
            String figure = CreditFormat.of(sum);
            graphics.drawString(font, figure, x + (cardW - font.width(figure)) / 2, y + 16,
                    enabled ? GOLD : MUTED, false);
        }

        long typed = typed();
        boolean enabled = typed > 0 && typed <= of;
        int x = leftPos + INNER_X + INNER_W - CONFIRM_W;
        int y = topPos + ROW_Y;
        boolean hovered = enabled && isOver(mouseX, mouseY, x, y, CONFIRM_W, ROW_H);
        int face = enabled ? (deposit ? DEPOSIT : WITHDRAW) : DISABLED;
        graphics.fill(x, y, x + CONFIRM_W, y + ROW_H, hovered ? face + 0x00181818 : face);
        Component label = text(deposit ? "deposit" : "withdraw");
        graphics.drawString(font, label, x + (CONFIRM_W - font.width(label)) / 2, y + (ROW_H - 8) / 2,
                enabled ? TEXT : MUTED, false);
        if (typed > 0) {
            // What "1.5k" came to, so nobody deposits a thousand times what they meant.
            Component reads = text("amount_reads", CreditFormat.of(typed));
            graphics.drawString(font, reads, leftPos + INNER_X, topPos + ROW_Y + ROW_H + 3, MUTED, false);
        }

        List<Component> footer = new ArrayList<>();
        long promised = menu.purse() - menu.free();
        if (deposit && promised > 0) {
            footer.add(text("promised", CreditFormat.of(promised)));
        }
        BankStatePayload state = ClientBankState.state();
        if (deposit && state != null && !state.tranches().isEmpty()) {
            InterestTranches.Tranche first = state.tranches().getFirst();
            footer.add(text("deposit_interest", percent(first.basisPoints()), CreditFormat.of(first.upTo())));
            footer.add(text("interest_in", duration(ClientBankState.millisUntilInterest())));
        }
        drawLines(graphics, footer, FOOTER_Y + LINE);
    }

    private void renderRecent(GuiGraphics graphics) {
        drawPageTitle(graphics, text("tile.recent"));
        BankStatePayload state = ClientBankState.state();
        List<BankMovement> recent = state == null ? List.of() : state.recent();
        if (recent.isEmpty()) {
            graphics.drawString(font, text("recent.none"), leftPos + INNER_X, topPos + BODY_Y, MUTED, false);
            return;
        }
        long now = System.currentTimeMillis();
        int y = topPos + BODY_Y;
        // Newest first, the way a statement is read.
        for (int i = recent.size() - 1; i >= 0 && y + 8 <= topPos + CONTENT_B; i--) {
            BankMovement movement = recent.get(i);
            boolean out = movement.kind() == BankMovement.Kind.WITHDRAWAL;
            String sign = out ? "- " : "+ ";
            int color = out ? 0xFFE05555 : 0xFF55D06A;
            String figure = sign + CreditFormat.of(movement.amount());
            graphics.drawString(font, figure, leftPos + INNER_X, y, color, false);
            Component what = text("recent." + movement.kind().name().toLowerCase(Locale.ROOT),
                    duration(now - movement.time()));
            graphics.drawString(font, what, leftPos + INNER_X + INNER_W - font.width(what), y, MUTED, false);
            y += LINE;
        }
    }

    private void renderInfo(GuiGraphics graphics) {
        drawPageTitle(graphics, text("tile.info"));
        List<Component> lines = new ArrayList<>();
        lines.add(text("info.safe"));
        BankStatePayload state = ClientBankState.state();
        if (state != null) {
            lines.add(state.deathLossPercent() > 0
                    ? text(state.combatOnly() ? "info.death_combat" : "info.death", state.deathLossPercent())
                    : text("info.no_death"));
            if (state.tranches().isEmpty()) {
                lines.add(text("info.interest_off"));
            } else {
                lines.add(text("info.interest_every", duration(state.interestPeriod())));
                lines.add(text("interest_in", duration(ClientBankState.millisUntilInterest()))
                        .withStyle(ChatFormatting.AQUA));
                long from = 0;
                for (InterestTranches.Tranche tranche : state.tranches()) {
                    lines.add((from == 0
                            ? text("info.tranche_first", CreditFormat.of(tranche.upTo()), percent(tranche.basisPoints()))
                            : text("info.tranche_next", CreditFormat.of(from), CreditFormat.of(tranche.upTo()),
                            percent(tranche.basisPoints())))
                            .withStyle(ChatFormatting.YELLOW));
                    from = tranche.upTo();
                }
                lines.add(text("info.max_interest", CreditFormat.of(new InterestTranches(state.tranches()).maxInterest()),
                        CreditFormat.of(from)));
            }
        }
        drawLines(graphics, lines, BODY_Y);
    }

    private void drawPageTitle(GuiGraphics graphics, Component title) {
        graphics.drawString(font, title, leftPos + INNER_X, topPos + PAGE_Y, TEXT, false);
    }

    /** Lines wrapped to the page, from {@code fromY} down, stopping at its foot. */
    private void drawLines(GuiGraphics graphics, List<Component> lines, int fromY) {
        int y = topPos + fromY;
        for (Component line : lines) {
            for (FormattedCharSequence wrapped : font.split(line, INNER_W)) {
                if (y + 8 > topPos + CONTENT_B) {
                    return;
                }
                graphics.drawString(font, wrapped, leftPos + INNER_X, y, MUTED, false);
                y += LINE;
            }
        }
    }

    private void renderTabTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Tab each : Tab.values()) {
            int top = topPos + TAB_Y + each.ordinal() * TAB_H;
            if (each != tab && isOver(mouseX, mouseY, leftPos + TAB_X, top, TAB_W, TAB_H - 2)) {
                graphics.renderTooltip(font, text("tile." + each.key), mouseX, mouseY);
                return;
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    // --- Input -------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX;
        int y = (int) mouseY;
        for (Tab each : Tab.values()) {
            if (isOver(x, y, leftPos + TAB_X, topPos + TAB_Y + each.ordinal() * TAB_H, TAB_W, TAB_H - 2)) {
                if (each != tab) {
                    playClick();
                    select(each);
                }
                return true;
            }
        }
        if (tab == Tab.DEPOSIT || tab == Tab.WITHDRAW) {
            boolean deposit = tab == Tab.DEPOSIT;
            long of = deposit ? menu.free() : menu.savings();
            List<Share> shares = deposit ? DEPOSIT_SHARES : WITHDRAW_SHARES;
            int cardW = (INNER_W - PAD * (shares.size() - 1)) / shares.size();
            for (int i = 0; i < shares.size(); i++) {
                Share share = shares.get(i);
                if (isOver(x, y, leftPos + INNER_X + i * (cardW + PAD), topPos + BODY_Y, cardW, CARD_H)) {
                    if (CreditMath.percentOf(of, share.percent(), RoundingMode.FLOOR) > 0) {
                        playClick();
                        send(BankActionPayload.share(deposit, share.percent()));
                    }
                    return true;
                }
            }
            if (isOver(x, y, leftPos + INNER_X, topPos + ROW_Y, INNER_W - CONFIRM_W - 4, ROW_H)) {
                setFocused(amount);
                amount.setFocused(true);
                return true;
            }
            amount.setFocused(false);
            if (isOver(x, y, leftPos + INNER_X + INNER_W - CONFIRM_W, topPos + ROW_Y, CONFIRM_W, ROW_H)) {
                confirm();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void confirm() {
        long typed = typed();
        boolean deposit = tab == Tab.DEPOSIT;
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

    /** What the field says, "1.5k" and "2m" included, or zero for nothing usable. */
    private long typed() {
        long typed = amount == null ? 0 : CreditFormat.parse(amount.getValue()).orElse(0);
        return typed <= CreditAccount.MAX_BALANCE ? typed : 0;
    }

    // --- Text --------------------------------------------------------------------------------

    private static MutableComponent text(String key, Object... args) {
        return Component.translatable("economycore.my_bank." + key, args);
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
