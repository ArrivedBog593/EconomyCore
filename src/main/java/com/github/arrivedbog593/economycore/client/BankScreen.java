package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.api.CreditMath;
import com.github.arrivedbog593.economycore.engine.BankLevels;
import com.github.arrivedbog593.economycore.engine.BankMovement;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.engine.InterestTranches;
import com.github.arrivedbog593.economycore.platform.PassWatch;
import com.github.arrivedbog593.economycore.platform.SavingsLevels;
import com.github.arrivedbog593.economycore.platform.menu.BankMenu;
import com.github.arrivedbog593.economycore.platform.network.BankActionPayload;
import com.github.arrivedbog593.economycore.platform.network.BankStatePayload;
import com.github.arrivedbog593.economycore.platform.network.BankUpgradePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The banker's window: tabs down the left, the chosen page on the right, and
 * both balances always at the top of it.
 * <p>
 * Drawn the way the shop and the exchange are, from {@link Panels}: vanilla's
 * gray frame, sunken wells, dark outlined buttons with green or amber for the
 * one that acts, a black field to type into. So the three counters read as one
 * mod, sit beside a chest without looking borrowed from another game, and
 * follow the light or dark theme together. Its icons are its own — a safe with
 * money going in, the same safe with money coming out, a ledger, an "i" —
 * rather than a chest and a dispenser standing in for them.
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
        DEPOSIT("deposit"), WITHDRAW("withdraw"), RECENT("recent"), UPGRADE("upgrade"), INFO("info");

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

    private static final int WIDTH = 200;
    private static final int HEIGHT = 182;

    /** Tabs hang off the left edge, as the creative inventory's hang off its top. */
    private static final int TAB_OUT = 26;
    private static final int TAB_Y = 6;
    private static final int TAB_H = 26;
    private static final int TAB_GAP = 1;
    /** How far a tab not chosen sits back behind the panel. */
    private static final int TAB_BACK = 3;

    private static final int CONTENT_X = 8;
    private static final int CONTENT_R = WIDTH - 8;
    private static final int CONTENT_W = CONTENT_R - CONTENT_X;
    private static final int CONTENT_B = HEIGHT - 8;
    private static final int GAP = 4;

    private static final int BALANCE_Y = 18;
    private static final int BALANCE_H = 28;
    private static final int BALANCE_W = (CONTENT_W - GAP) / 2;
    private static final int PAGE_Y = BALANCE_Y + BALANCE_H + 6;
    private static final int BODY_Y = PAGE_Y + 12;

    private static final int CARD_H = 24;
    private static final int ROW_Y = BODY_Y + CARD_H + 7;
    private static final int ROW_H = 14;
    private static final int CONFIRM_W = 58;
    private static final int TEXT_Y = (ROW_H - 8) / 2 + 1;
    private static final int FOOTER_Y = ROW_Y + ROW_H + 15;
    private static final int LINE = 10;

    // The Upgrades page: the level now, the next, its cost in a well, and the button at the foot.
    private static final int UPGRADE_NEXT_Y = BODY_Y + 2 * LINE + 4;
    private static final int UPGRADE_COST_Y = UPGRADE_NEXT_Y + 3 * LINE + 2;
    private static final int COST_H = 18;
    private static final int UPGRADE_W = 80;
    private static final int UPGRADE_BUTTON_Y = CONTENT_B - ROW_H;

    // The shop's and the exchange's buttons, so the three counters match.
    private static final int CONTROL_FACE = 0xFF555555;
    private static final int DEPOSIT_FACE = 0xFF2E7D32;
    private static final int WITHDRAW_FACE = 0xFFB07B18;
    private static final int DISABLED_FACE = 0xFF4A4A4A;
    private static final int ON_FACE = 0xFFFFFFFF;
    private static final int ON_DISABLED = 0xFF9A9A9A;
    private static final int IN_WELL = 0xFFE8E8E8;
    private static final int SUM = 0xFFFFD54F;
    /** What the player is short of, as the shop marks a price they cannot pay. */
    private static final int SHORT = 0xFFFF6B6B;

    private static final List<Share> DEPOSIT_SHARES = List.of(
            new Share("share.all", 100), new Share("share.half", 50));
    private static final List<Share> WITHDRAW_SHARES = List.of(
            new Share("share.all", 100), new Share("share.half", 50), new Share("share.fifth", 20));

    /** The page chosen last, so the banker opens where the player left off. */
    private static Tab remembered = Tab.DEPOSIT;

    private Tab tab = remembered;

    /**
     * How many lines the information page is scrolled down. A bank with many
     * tranches, as a Palatial account has, says more than the page holds.
     */
    private int infoScroll;

    /** How many lines the information page had last drawn, to keep the scroll in range. */
    private int infoLines;
    private EditBox amount;

    public BankScreen(BankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // Centred with its tabs, which hang off the left of the panel.
        leftPos += TAB_OUT / 2;
        String carried = amount == null ? "" : amount.getValue();
        // Unbordered: the frame is drawn like the shop's search field.
        amount = new EditBox(font, leftPos + CONTENT_X + 4, topPos + ROW_Y + TEXT_Y,
                CONTENT_W - CONFIRM_W - 12, 8, text("amount"));
        amount.setBordered(false);
        amount.setHint(text("amount_hint").withStyle(ChatFormatting.GRAY));
        amount.setFilter(text -> text.isEmpty() || text.matches("\\d{0,13}(\\.\\d{0,2})?[kKmM]?"));
        amount.setValue(carried);
        addRenderableWidget(amount);
        showAmountField();
    }

    private void select(Tab next) {
        tab = next;
        remembered = next;
        infoScroll = 0;
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
        // As the creative inventory does it: the tabs not chosen go behind the
        // panel, which covers their inner edge; the chosen one goes on top and
        // runs into it, so tab and page read as one piece.
        for (Tab each : Tab.values()) {
            if (each != tab) {
                drawTab(graphics, each, false);
            }
        }
        Panels.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        drawTab(graphics, tab, true);

        drawBalance(graphics, leftPos + CONTENT_X, topPos + BALANCE_Y, text("purse"), menu.purse());
        drawBalance(graphics, leftPos + CONTENT_X + BALANCE_W + GAP, topPos + BALANCE_Y, text("savings"),
                menu.savings());
    }

    /**
     * One tab, in vanilla's frame: a black outline, a lit top and left, a
     * shaded bottom, and its two left corners cut the way the panel's own are.
     * The chosen one has no right edge and fills on into the panel, over the
     * panel's own border; the others sit a little further out, filled in a
     * darker grey.
     */
    private void drawTab(GuiGraphics graphics, Tab each, boolean chosen) {
        Palette palette = Palette.current();
        int left = leftPos - TAB_OUT + (chosen ? 0 : TAB_BACK);
        int top = topPos + tabY(each);
        int bottom = top + TAB_H;
        // The ones not chosen keep their lit edge and shade but are filled in
        // the slots' darker grey, as the creative inventory's are.
        int body = chosen ? palette.panel() : palette.slotFill();
        // Row by row, as Panels.panel draws its frame, with the same corner
        // cuts on the left and nothing on the right, which is open.
        for (int y = top; y < bottom; y++) {
            int fromTop = y - top;
            int fromBottom = bottom - 1 - y;
            // Where the chosen one meets the panel, its outline and shade stop
            // at the panel's outline, so the panel's lit edge turns into the
            // tab's, as the creative inventory's tabs join its window.
            int right = !chosen ? leftPos + 2 : fromTop == 0 || fromBottom <= 2 ? leftPos + 1 : leftPos + 3;
            int edge = left + Math.max(Math.max(0, 2 - fromTop), Math.max(0, 3 - fromBottom));
            graphics.fill(edge, y, edge + 1, y + 1, Panels.OUTLINE);
            int inside = edge + 1;
            if (fromTop == 0 || fromBottom == 0) {
                graphics.fill(inside, y, right, y + 1, Panels.OUTLINE);
            } else if (fromTop <= 2) {
                graphics.fill(inside, y, right, y + 1, palette.bevelLight());
            } else if (fromBottom == 1) {
                graphics.fill(inside, y, right, y + 1, palette.bevelDark());
            } else if (fromBottom == 2) {
                graphics.fill(inside, y, inside + 1, y + 1, body);
                graphics.fill(inside + 1, y, right, y + 1, palette.bevelDark());
            } else {
                int lit = fromTop == 3 ? 3 : 2;
                graphics.fill(inside, y, inside + lit, y + 1, palette.bevelLight());
                graphics.fill(inside + lit, y, right, y + 1, body);
            }
        }
        if (chosen) {
            // Carry the panel's fill across its border, so the tab opens into it.
            graphics.fill(leftPos, top + 3, leftPos + 3, bottom - 3, palette.panel());
        }
        int width = TAB_OUT + 3 - (chosen ? 0 : TAB_BACK);
        graphics.blit(each.icon, left + (width - 16) / 2 + 1, top + (TAB_H - 16) / 2, 0, 0, 16, 16, 16, 16);
    }

    /** A balance in a sunken well, written the way the shop writes inside its grid. */
    private void drawBalance(GuiGraphics graphics, int x, int y, Component label, long credits) {
        Panels.recess(graphics, x, y, BALANCE_W, BALANCE_H);
        graphics.drawString(font, label, x + 4, y + 4, IN_WELL, true);
        graphics.blit(COIN, x + 4, y + 15, 0, 0, 9, 9, 9, 9);
        String figure = CreditFormat.of(credits);
        int room = BALANCE_W - 20;
        // Shrinks rather than overflows: at the cap the figure is wider than the well.
        float scale = Math.min(1.0F, room / (float) font.width(figure));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 16, y + 16, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, figure, 0, 0, SUM, true);
        graphics.pose().popPose();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        switch (tab) {
            case DEPOSIT -> renderMove(graphics, mouseX, mouseY, true);
            case WITHDRAW -> renderMove(graphics, mouseX, mouseY, false);
            case RECENT -> renderRecent(graphics);
            case UPGRADE -> renderUpgrade(graphics, mouseX, mouseY);
            case INFO -> renderInfo(graphics);
        }
        if (tab == Tab.UPGRADE && renderCostTooltip(graphics, mouseX, mouseY)) {
            return;
        }
        renderTabTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, Panels.label(), false);
    }

    /** Deposit or withdraw: a button per share with what it comes to, then a typed amount. */
    private void renderMove(GuiGraphics graphics, int mouseX, int mouseY, boolean deposit) {
        long of = deposit ? depositable() : menu.savings();
        drawPageTitle(graphics, text(deposit ? "tile.deposit" : "tile.withdraw"));

        List<Share> shares = deposit ? DEPOSIT_SHARES : WITHDRAW_SHARES;
        int cardW = cardWidth(shares);
        for (int i = 0; i < shares.size(); i++) {
            Share share = shares.get(i);
            long sum = CreditMath.percentOf(of, share.percent(), RoundingMode.FLOOR);
            int x = leftPos + CONTENT_X + i * (cardW + GAP);
            int y = topPos + BODY_Y;
            boolean enabled = sum > 0;
            control(graphics, x, y, cardW, CARD_H, enabled ? CONTROL_FACE : DISABLED_FACE,
                    enabled && isOver(mouseX, mouseY, x, y, cardW, CARD_H));
            // Shrunk to fit, as a translation may run longer than the button is wide.
            Component name = text(share.key());
            centered(graphics, name, x + cardW / 2, y + 3, cardW - 4, enabled ? ON_FACE : ON_DISABLED);
            centered(graphics, Component.literal(CreditFormat.of(sum)), x + cardW / 2, y + 13, cardW - 4,
                    enabled ? SUM : ON_DISABLED);
        }

        // The field, framed like the shop's search box.
        int fieldR = leftPos + CONTENT_R - CONFIRM_W - 4;
        boolean typing = amount.isFocused();
        graphics.fill(leftPos + CONTENT_X - 1, topPos + ROW_Y - 1, fieldR + 1, topPos + ROW_Y + ROW_H + 1,
                typing ? 0xFFFFFFFF : 0xFFA0A0A0);
        graphics.fill(leftPos + CONTENT_X, topPos + ROW_Y, fieldR, topPos + ROW_Y + ROW_H, 0xFF000000);

        long typed = typed();
        boolean enabled = typed > 0 && typed <= of;
        int x = leftPos + CONTENT_R - CONFIRM_W;
        int y = topPos + ROW_Y;
        control(graphics, x, y, CONFIRM_W, ROW_H,
                enabled ? (deposit ? DEPOSIT_FACE : WITHDRAW_FACE) : DISABLED_FACE,
                enabled && isOver(mouseX, mouseY, x, y, CONFIRM_W, ROW_H));
        centered(graphics, text(deposit ? "deposit" : "withdraw"), x + CONFIRM_W / 2, y + TEXT_Y,
                CONFIRM_W - 4, enabled ? ON_FACE : ON_DISABLED);
        if (typed > 0) {
            // What "1.5k" came to, so nobody deposits a thousand times what they meant.
            graphics.drawString(font, text("amount_reads", CreditFormat.of(typed)),
                    leftPos + CONTENT_X, topPos + ROW_Y + ROW_H + 3, Panels.label(), false);
        }

        // What the page is for, wrapped to it.
        List<Component> footer = new ArrayList<>();
        footer.add(text(deposit ? "deposit_lore" : "withdraw_lore"));
        long promised = menu.purse() - menu.free();
        if (deposit && promised > 0) {
            footer.add(text("promised", CreditFormat.of(promised)));
        }
        BankStatePayload state = ClientBankState.state();
        if (deposit && state != null && state.level() != null) {
            footer.add(text("deposit_room", CreditFormat.of(room()), CreditFormat.of(state.level().cap())));
        }
        if (deposit && state != null && !state.tranches().isEmpty()) {
            InterestTranches.Tranche first = state.tranches().getFirst();
            footer.add(text("deposit_interest", InterestTranches.percent(first.rate()), CreditFormat.of(first.upTo())));
            footer.add(text("interest_in", PassWatch.describe(ClientBankState.millisUntilInterest())));
        }
        drawLines(graphics, footer, FOOTER_Y);
    }

    private void renderRecent(GuiGraphics graphics) {
        drawPageTitle(graphics, text("tile.recent"));
        BankStatePayload state = ClientBankState.state();
        List<BankMovement> recent = state == null ? List.of() : state.recent();
        if (recent.isEmpty()) {
            graphics.drawString(font, text("recent.none"), leftPos + CONTENT_X, topPos + BODY_Y,
                    Panels.label(), false);
            return;
        }
        long now = System.currentTimeMillis();
        int y = topPos + BODY_Y;
        // Newest first, the way a statement is read.
        for (int i = recent.size() - 1; i >= 0 && y + 8 <= topPos + CONTENT_B; i--) {
            BankMovement movement = recent.get(i);
            boolean out = movement.kind() == BankMovement.Kind.WITHDRAWAL
                    || movement.kind() == BankMovement.Kind.UPGRADE;
            String figure = (out ? "- " : "+ ") + CreditFormat.of(movement.amount());
            graphics.drawString(font, figure, leftPos + CONTENT_X, y, out ? Panels.negative() : Panels.positive(),
                    false);
            // Right-aligned, and shrunk into whatever the figure leaves.
            Component what = text("recent." + movement.kind().name().toLowerCase(Locale.ROOT),
                    ago(now - movement.time()));
            int room = CONTENT_W - font.width(figure) - 8;
            float fit = Math.min(1.0F, room / (float) font.width(what));
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + CONTENT_R, y + (1.0F - fit) * 4, 0);
            graphics.pose().scale(fit, fit, 1.0F);
            graphics.drawString(font, what, -font.width(what), 0, Panels.label(), false);
            graphics.pose().popPose();
            y += LINE;
        }
    }

    private void renderInfo(GuiGraphics graphics) {
        drawPageTitle(graphics, text("tile.info"));
        List<Component> lines = new ArrayList<>();
        lines.add(text("info.safe"));
        BankStatePayload state = ClientBankState.state();
        if (state != null) {
            if (state.level() != null) {
                lines.add(text("info.level", SavingsLevels.nameOf(state.level().name()),
                        CreditFormat.of(state.level().cap())));
            }
            lines.add(state.deathLossPercent() > 0
                    ? text(state.combatOnly() ? "info.death_combat" : "info.death", state.deathLossPercent())
                    : text("info.no_death"));
            if (state.tranches().isEmpty()) {
                lines.add(text("info.interest_off"));
            } else {
                lines.add(text("info.interest_every", duration(state.interestPeriod())));
                lines.add(text("interest_in", PassWatch.describe(ClientBankState.millisUntilInterest())));
                long from = 0;
                for (InterestTranches.Tranche tranche : state.tranches()) {
                    lines.add(from == 0
                            ? text("info.tranche_first", CreditFormat.of(tranche.upTo()), InterestTranches.percent(tranche.rate()))
                            : text("info.tranche_next", CreditFormat.of(from), CreditFormat.of(tranche.upTo()),
                            InterestTranches.percent(tranche.rate())));
                    from = tranche.upTo();
                }
                lines.add(text("info.max_interest",
                        CreditFormat.of(new InterestTranches(state.tranches()).maxInterest()), CreditFormat.of(from)));
            }
        }
        drawScrolled(graphics, lines);
    }

    /**
     * One line of the page, shrunk to the page's width when it is wider, as
     * a cap of a hundred billion and more is.
     */
    private void drawFitted(GuiGraphics graphics, Component line, int y) {
        int width = font.width(line);
        if (width <= CONTENT_W) {
            graphics.drawString(font, line, leftPos + CONTENT_X, topPos + y, Panels.label(), false);
            return;
        }
        float scale = (float) CONTENT_W / width;
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + CONTENT_X, topPos + y + (font.lineHeight * (1 - scale)) / 2, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, line, 0, 0, Panels.label(), false);
        graphics.pose().popPose();
    }

    /**
     * The account's level and the next: what it holds, what it pays, and
     * what reaching it costs, with the cost in red where the player is short.
     * The server checks again when the button is pressed.
     */
    private void renderUpgrade(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPageTitle(graphics, text("tile.upgrade"));
        BankStatePayload state = ClientBankState.state();
        BankStatePayload.Level level = state == null ? null : state.level();
        if (level == null) {
            drawLines(graphics, List.of(text("upgrade.no_levels")), BODY_Y);
            return;
        }
        int x = leftPos + CONTENT_X;
        drawFitted(graphics, text("upgrade.current", SavingsLevels.nameOf(level.name()),
                level.place() + 1, level.count()), BODY_Y);
        drawFitted(graphics, text("upgrade.holds", CreditFormat.of(level.cap())), BODY_Y + LINE);
        BankStatePayload.Next next = level.next();
        if (next == null) {
            drawLines(graphics, List.of(text("upgrade.top")), UPGRADE_NEXT_Y);
            return;
        }
        drawFitted(graphics, text("upgrade.next", SavingsLevels.nameOf(next.name())), UPGRADE_NEXT_Y);
        drawFitted(graphics, text("upgrade.holds", CreditFormat.of(next.cap())), UPGRADE_NEXT_Y + LINE);
        if (next.maxInterest() >= 0) {
            drawFitted(graphics, text("upgrade.max_interest", CreditFormat.of(next.maxInterest())),
                    UPGRADE_NEXT_Y + 2 * LINE);
        }

        graphics.drawString(font, text("upgrade.cost"), x, topPos + UPGRADE_COST_Y, Panels.label(), false);
        int costY = topPos + UPGRADE_COST_Y + LINE;
        Panels.recess(graphics, x, costY, CONTENT_W, COST_H);
        int at = x + 4;
        if (next.credits() <= 0 && next.items().isEmpty()) {
            graphics.drawString(font, text("upgrade.free"), at, costY + 5, IN_WELL, true);
        }
        if (next.credits() > 0) {
            graphics.blit(COIN, at, costY + 4, 0, 0, 9, 9, 9, 9);
            String figure = CreditFormat.of(next.credits());
            graphics.drawString(font, figure, at + 12, costY + 5,
                    menu.savings() >= next.credits() ? SUM : SHORT, true);
            at += 12 + font.width(figure) + 8;
        }
        for (BankLevels.ItemCost cost : next.items()) {
            ItemStack stack = SavingsLevels.item(cost).map(ItemStack::new).orElse(ItemStack.EMPTY);
            graphics.renderItem(stack, at, costY + 1);
            String count = "×" + cost.count();
            graphics.drawString(font, count, at + 17, costY + 5, carried(cost) >= cost.count() ? ON_FACE : SHORT,
                    true);
            at += 17 + font.width(count) + 6;
        }

        boolean enabled = canUpgrade(next);
        int buttonX = leftPos + CONTENT_R - UPGRADE_W;
        int buttonY = topPos + UPGRADE_BUTTON_Y;
        control(graphics, buttonX, buttonY, UPGRADE_W, ROW_H, enabled ? DEPOSIT_FACE : DISABLED_FACE,
                enabled && isOver(mouseX, mouseY, buttonX, buttonY, UPGRADE_W, ROW_H));
        centered(graphics, text("upgrade.button"), buttonX + UPGRADE_W / 2, buttonY + TEXT_Y, UPGRADE_W - 4,
                enabled ? ON_FACE : ON_DISABLED);
    }

    /** An item's name and how many of it the player has, over its icon in the cost. */
    private boolean renderCostTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        BankStatePayload state = ClientBankState.state();
        BankStatePayload.Next next = state == null || state.level() == null ? null : state.level().next();
        if (next == null) {
            return false;
        }
        int at = leftPos + CONTENT_X + 4;
        int costY = topPos + UPGRADE_COST_Y + LINE;
        if (next.credits() > 0) {
            at += 12 + font.width(CreditFormat.of(next.credits())) + 8;
        }
        for (BankLevels.ItemCost cost : next.items()) {
            if (isOver(mouseX, mouseY, at, costY + 1, 16, 16)) {
                Component name = SavingsLevels.item(cost).map(item -> item.getDescription())
                        .orElse(Component.literal(cost.item()));
                graphics.renderComponentTooltip(font, List.of(name,
                        text("upgrade.have", carried(cost), cost.count()).withStyle(ChatFormatting.GRAY)),
                        mouseX, mouseY);
                return true;
            }
            at += 17 + font.width("×" + cost.count()) + 6;
        }
        return false;
    }

    /** How many of a cost's item the player carries. */
    private int carried(BankLevels.ItemCost cost) {
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }
        return SavingsLevels.item(cost).map(item -> SavingsLevels.countOf(minecraft.player.getInventory(), item))
                .orElse(0);
    }

    private boolean canUpgrade(BankStatePayload.Next next) {
        if (menu.savings() < next.credits()) {
            return false;
        }
        for (BankLevels.ItemCost cost : next.items()) {
            if (carried(cost) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    /** What may go into the bank now: the free purse, but no more than the account's level has room for. */
    private long depositable() {
        return Math.min(menu.free(), room());
    }

    /** How much more the account holds, as the menu's balance and the level's cap say. */
    private long room() {
        BankStatePayload state = ClientBankState.state();
        if (state == null || state.level() == null) {
            return CreditAccount.MAX_BALANCE;
        }
        return Math.max(0, state.level().cap() - menu.savings());
    }

    private void drawPageTitle(GuiGraphics graphics, Component title) {
        graphics.drawString(font, title, leftPos + CONTENT_X, topPos + PAGE_Y, Panels.label(), false);
    }

    /**
     * The information page, wrapped and scrolled with the wheel, with an
     * arrow at the top or the foot while there is more that way, as the
     * shop marks its grid. Leaves room on the right for the arrows.
     */
    private void drawScrolled(GuiGraphics graphics, List<Component> lines) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, CONTENT_W - 8));
        }
        infoLines = wrapped.size();
        int rows = infoRows();
        infoScroll = Math.clamp(infoScroll, 0, Math.max(0, wrapped.size() - rows));
        int y = topPos + BODY_Y;
        for (int i = infoScroll; i < wrapped.size() && i < infoScroll + rows; i++) {
            graphics.drawString(font, wrapped.get(i), leftPos + CONTENT_X, y, Panels.label(), false);
            y += LINE;
        }
        int arrowX = leftPos + CONTENT_R - font.width("▲");
        if (infoScroll > 0) {
            graphics.drawString(font, "▲", arrowX, topPos + BODY_Y, Panels.label(), false);
        }
        if (infoScroll + rows < wrapped.size()) {
            graphics.drawString(font, "▼", arrowX, topPos + BODY_Y + (rows - 1) * LINE, Panels.label(), false);
        }
    }

    private static int infoRows() {
        return (CONTENT_B - BODY_Y) / LINE;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (tab == Tab.INFO && infoLines > infoRows()) {
            infoScroll = Math.clamp(infoScroll - (int) Math.signum(deltaY), 0, infoLines - infoRows());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    /** Lines wrapped to the page, from {@code fromY} down, stopping at its foot. */
    private void drawLines(GuiGraphics graphics, List<Component> lines, int fromY) {
        int y = topPos + fromY;
        for (Component line : lines) {
            for (FormattedCharSequence wrapped : font.split(line, CONTENT_W)) {
                if (y + 8 > topPos + CONTENT_B) {
                    return;
                }
                graphics.drawString(font, wrapped, leftPos + CONTENT_X, y, Panels.label(), false);
                y += LINE;
            }
        }
    }

    /** The outlined, flat button the shop and the exchange use. */
    private static void control(GuiGraphics graphics, int x, int y, int width, int height, int face,
                                boolean hovered) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, Panels.OUTLINE);
        graphics.fill(x, y, x + width, y + height, hovered ? face + 0x00191919 : face);
    }

    /** Text centred on {@code centerX}, shrunk to {@code room} if it would not fit. */
    private void centered(GuiGraphics graphics, Component text, int centerX, int y, int room, int color) {
        float fit = Math.min(1.0F, room / (float) font.width(text));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y + (1.0F - fit) * 4, 0);
        graphics.pose().scale(fit, fit, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private void renderTabTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Tab each : Tab.values()) {
            if (overTab(each, mouseX, mouseY)) {
                graphics.renderTooltip(font, text("tile." + each.key), mouseX, mouseY);
                return;
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** Whether the mouse is on a tab: the part outside the panel, which is all of it a player can reach. */
    private boolean overTab(Tab each, int mouseX, int mouseY) {
        int left = leftPos - TAB_OUT + (each == tab ? 0 : TAB_BACK);
        return isOver(mouseX, mouseY, left, topPos + tabY(each), leftPos - left, TAB_H);
    }

    private static int tabY(Tab tab) {
        return TAB_Y + tab.ordinal() * (TAB_H + TAB_GAP);
    }

    private static int cardWidth(List<Share> shares) {
        return (CONTENT_W - GAP * (shares.size() - 1)) / shares.size();
    }

    // --- Input -------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX;
        int y = (int) mouseY;
        for (Tab each : Tab.values()) {
            if (overTab(each, x, y)) {
                if (each != tab) {
                    playClick();
                    select(each);
                }
                return true;
            }
        }
        if (tab == Tab.UPGRADE) {
            BankStatePayload state = ClientBankState.state();
            BankStatePayload.Next next = state == null || state.level() == null ? null : state.level().next();
            if (next != null && isOver(x, y, leftPos + CONTENT_R - UPGRADE_W, topPos + UPGRADE_BUTTON_Y,
                    UPGRADE_W, ROW_H)) {
                if (canUpgrade(next)) {
                    playClick();
                    send(BankUpgradePayload.INSTANCE);
                }
                return true;
            }
        }
        if (tab == Tab.DEPOSIT || tab == Tab.WITHDRAW) {
            boolean deposit = tab == Tab.DEPOSIT;
            long of = deposit ? depositable() : menu.savings();
            List<Share> shares = deposit ? DEPOSIT_SHARES : WITHDRAW_SHARES;
            int cardW = cardWidth(shares);
            for (int i = 0; i < shares.size(); i++) {
                Share share = shares.get(i);
                if (isOver(x, y, leftPos + CONTENT_X + i * (cardW + GAP), topPos + BODY_Y, cardW, CARD_H)) {
                    if (CreditMath.percentOf(of, share.percent(), RoundingMode.FLOOR) > 0) {
                        playClick();
                        send(BankActionPayload.share(deposit, share.percent()));
                    }
                    return true;
                }
            }
            if (isOver(x, y, leftPos + CONTENT_X, topPos + ROW_Y, CONTENT_W - CONFIRM_W - 4, ROW_H)) {
                setFocused(amount);
                amount.setFocused(true);
                return true;
            }
            amount.setFocused(false);
            if (isOver(x, y, leftPos + CONTENT_R - CONFIRM_W, topPos + ROW_Y, CONFIRM_W, ROW_H)) {
                confirm();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void confirm() {
        long typed = typed();
        boolean deposit = tab == Tab.DEPOSIT;
        if (typed <= 0 || typed > (deposit ? depositable() : menu.savings())) {
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

    private void send(CustomPacketPayload payload) {
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


    /** How long ago, short enough for a statement line: now, 5 min, 7 h, 2 days. */
    private static Component ago(long millis) {
        return millis < 60_000L ? text("time.now") : duration(millis);
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
