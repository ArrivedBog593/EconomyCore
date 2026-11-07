package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.engine.BankLevels;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.platform.SavingsLevels;
import com.github.arrivedbog593.economycore.platform.menu.AdminBankMenu;
import com.github.arrivedbog593.economycore.platform.network.AdminBankActionPayload;
import com.github.arrivedbog593.economycore.platform.network.AdminBankStatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The bank's settings: interest and what dying costs along the top, the
 * account levels in a list, and the chosen level's terms under it.
 * <p>
 * Drawn as the shop's settings are, from {@link Panels}, with the same
 * framed fields and flat buttons, so the three counters' settings read as one
 * mod. A level's items are given the way the shop is given what it sells:
 * put them in the slot, count and all, and add them; a click on one in the
 * cost takes it out again.
 * <p>
 * Nothing is changed here. Every button asks the server, which checks the
 * level against the others and refuses what would leave the config
 * unreadable, saying why in the action bar; the window redraws from the
 * settings the server sends back, the same ones every other admin sees.
 */
public class AdminBankScreen extends AbstractContainerScreen<AdminBankMenu> {

    private static final int H = 14;
    private static final int TEXT_INSET = 4;
    private static final int TEXT_Y = (H - 8) / 2 + 1;
    private static final float LABEL_SCALE = 0.75F;

    // The top row: interest on or off, its hours, what dying costs, combat only, and apply.
    private static final int GENERAL_LABEL_Y = 17;
    private static final int GENERAL_Y = 24;
    private static final int INTEREST_X = 8;
    private static final int INTEREST_W = 62;
    private static final int HOURS_X = 74;
    private static final int HOURS_W = 36;
    private static final int DEATH_X = 114;
    private static final int DEATH_W = 30;
    private static final int COMBAT_X = 148;
    private static final int COMBAT_W = 44;
    private static final int APPLY_X = 196;
    private static final int APPLY_W = 26;

    // The levels, five at a time, scrolled with the wheel.
    private static final int LEVELS_LABEL_Y = 42;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 51;
    private static final int LIST_W = 214;
    private static final int LIST_ROWS = 5;
    private static final int ROW_H = 10;

    // The chosen level: name, cap and cost, then its tranches, then its items, then the buttons.
    private static final int EDIT_LABEL_Y = 107;
    private static final int EDIT_Y = 114;
    private static final int NAME_X = 8;
    private static final int NAME_W = 70;
    private static final int CAP_X = 82;
    private static final int CAP_W = 70;
    private static final int COST_X = 156;
    private static final int COST_W = 66;
    private static final int TRANCHES_LABEL_Y = 131;
    private static final int TRANCHES_Y = 138;
    private static final int TRANCHES_W = 214;
    private static final int ADD_X = 30;
    private static final int ADD_Y = AdminBankMenu.INPUT_Y + 2;
    private static final int ADD_W = 40;
    private static final int ITEMS_X = 76;
    private static final int ITEMS_R = 222;
    private static final int BUTTONS_Y = 179;
    private static final int SAVE_X = 8;
    private static final int SAVE_W = 70;
    private static final int NEW_X = 82;
    private static final int NEW_W = 66;
    private static final int REMOVE_X = 152;
    private static final int REMOVE_W = 70;

    private static final int CONTROL_FACE = 0xFF555555;
    private static final int GO_FACE = 0xFF2E7D32;
    private static final int OFF_FACE = 0xFF6D4C41;
    private static final int DANGER_FACE = 0xFF8E2A2A;
    private static final int DISABLED_FACE = 0xFF4A4A4A;
    private static final int ON_FACE = 0xFFFFFFFF;
    private static final int ON_DISABLED = 0xFF9A9A9A;

    /** How long the remove button stays armed after the first click, as the shop's does. */
    private static final long CONFIRM_MILLIS = 3_000;

    /** No level chosen: saving adds a new one at the top. */
    private static final int NEW_LEVEL = -1;

    private EditBox hours;
    private EditBox death;
    private EditBox name;
    private EditBox cap;
    private EditBox cost;
    private EditBox tranches;

    /** The chosen level's items as they are being edited. */
    private final List<BankLevels.ItemCost> items = new ArrayList<>();

    private int selected = NEW_LEVEL;
    private int scroll;
    private long confirmUntil;

    /**
     * The level just saved, to choose once the server sends it back: its cap
     * may have put it anywhere in the list. Null when none is awaited.
     */
    private String choose;

    /** The settings last drawn, by identity, to notice a new version arriving. */
    private AdminBankStatePayload lastSeen;

    public AdminBankScreen(AdminBankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = AdminBankMenu.PANEL_WIDTH;
        this.imageHeight = AdminBankMenu.PANEL_HEIGHT;
        this.inventoryLabelY = AdminBankMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        Predicate<String> digits = text -> text.chars().allMatch(Character::isDigit);
        Predicate<String> credits = text -> text.isEmpty() || text.matches("\\d{0,13}(\\.\\d{0,2})?[kKmM]?");
        hours = field(hours, HOURS_X, GENERAL_Y, HOURS_W, digits, 4, "31");
        death = field(death, DEATH_X, GENERAL_Y, DEATH_W, digits, 3, "0");
        name = field(name, NAME_X, EDIT_Y, NAME_W, text -> text.matches("[a-z0-9_]*"), 32, "gold");
        cap = field(cap, CAP_X, EDIT_Y, CAP_W, credits, 16, "100m");
        cost = field(cost, COST_X, EDIT_Y, COST_W, credits, 16, "0");
        tranches = field(tranches, 8, TRANCHES_Y, TRANCHES_W,
                text -> text.matches("[0-9:.,_ ]*"), 512, "10000000:2,20000000:1");
        if (lastSeen == null) {
            sync(ClientAdminBankState.state());
        }
    }

    /** One framed field, unbordered so it is drawn the way the shop's are. */
    private EditBox field(EditBox old, int x, int y, int width, Predicate<String> filter, int max, String hint) {
        String carried = old == null ? "" : old.getValue();
        EditBox field = new EditBox(font, leftPos + x + TEXT_INSET, topPos + y + TEXT_Y,
                width - TEXT_INSET * 2, 8, Component.literal(hint));
        field.setBordered(false);
        field.setMaxLength(max);
        field.setFilter(filter);
        field.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        field.setValue(carried);
        addRenderableWidget(field);
        return field;
    }

    // --- Keeping up with the server ----------------------------------------------------------

    /** Takes in a new version of the settings: the top row, and the chosen level if it still exists. */
    private void sync(AdminBankStatePayload state) {
        lastSeen = state;
        if (state == null) {
            return;
        }
        if (!hours.isFocused()) {
            hours.setValue(String.valueOf(state.periodHours()));
        }
        if (!death.isFocused()) {
            death.setValue(String.valueOf(state.deathLoss()));
        }
        List<BankLevels.Level> levels = state.levels();
        if (choose != null) {
            for (int i = 0; i < levels.size(); i++) {
                if (levels.get(i).name().equals(choose)) {
                    selected = i;
                    choose = null;
                    scroll = Math.clamp(scroll, i - LIST_ROWS + 1, i);
                }
            }
        }
        if (selected >= levels.size()) {
            selected = NEW_LEVEL;
            clearEditor();
        } else if (selected != NEW_LEVEL) {
            fill(levels.get(selected));
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, levels.size() - LIST_ROWS));
    }

    private void fill(BankLevels.Level level) {
        name.setValue(level.name());
        cap.setValue(String.valueOf(level.cap()));
        cost.setValue(level.credits() == 0 ? "" : String.valueOf(level.credits()));
        tranches.setValue(level.tranches() == null ? "" : level.tranches().toText());
        items.clear();
        items.addAll(level.items());
    }

    private void clearEditor() {
        name.setValue("");
        cap.setValue("");
        cost.setValue("");
        tranches.setValue("");
        items.clear();
    }

    /**
     * Readies a new level above the last: twice what it holds, a tenth of
     * that to reach, the tranches it pays. Only a suggestion until saved.
     */
    private void suggestNew() {
        selected = NEW_LEVEL;
        confirmUntil = 0;
        List<BankLevels.Level> levels = levels();
        clearEditor();
        if (levels.isEmpty()) {
            name.setValue("starter");
            cap.setValue("50000000");
            return;
        }
        BankLevels.Level last = levels.getLast();
        long holds = Math.min(CreditAccount.MAX_BALANCE, last.cap() * 2);
        name.setValue("level_" + (levels.size() + 1));
        cap.setValue(String.valueOf(holds));
        cost.setValue(String.valueOf(last.cap() / 10));
        tranches.setValue(last.tranches() == null ? "" : last.tranches().toText());
    }

    private List<BankLevels.Level> levels() {
        AdminBankStatePayload state = ClientAdminBankState.state();
        return state == null ? List.of() : state.levels();
    }

    private boolean editable() {
        AdminBankStatePayload state = ClientAdminBankState.state();
        return state != null && state.readable();
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panels.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        Panels.recess(graphics, leftPos + LIST_X - 1, topPos + LIST_Y - 1, LIST_W + 2, LIST_ROWS * ROW_H + 2);
        Panels.slot(graphics, leftPos + AdminBankMenu.INPUT_X, topPos + AdminBankMenu.INPUT_Y);
        int inventoryLeft = leftPos + (imageWidth - 9 * 18) / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Panels.slot(graphics, inventoryLeft + column * 18, topPos + AdminBankMenu.INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            Panels.slot(graphics, inventoryLeft + column * 18, topPos + AdminBankMenu.HOTBAR_Y);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (ClientAdminBankState.state() != lastSeen) {
            sync(ClientAdminBankState.state());
        }
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        // Over the panel, then the fields again on top, as the shop's settings draw theirs.
        drawFrames(graphics, mouseX, mouseY, partialTick);
        AdminBankStatePayload state = ClientAdminBankState.state();
        if (state != null) {
            drawGeneral(graphics, state, mouseX, mouseY);
            drawLevels(graphics, state, mouseX, mouseY);
            drawEditor(graphics, mouseX, mouseY);
        }
        if (!renderOwnTooltip(graphics, mouseX, mouseY)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, Panels.label(), false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Panels.label(), false);
    }

    private void drawFrames(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean editing = editable();
        for (EditBox field : new EditBox[]{name, cap, cost, tranches}) {
            field.visible = editing;
        }
        frame(graphics, hours, HOURS_X, GENERAL_Y, HOURS_W, mouseX, mouseY, partialTick);
        frame(graphics, death, DEATH_X, GENERAL_Y, DEATH_W, mouseX, mouseY, partialTick);
        if (editing) {
            frame(graphics, name, NAME_X, EDIT_Y, NAME_W, mouseX, mouseY, partialTick);
            frame(graphics, cap, CAP_X, EDIT_Y, CAP_W, mouseX, mouseY, partialTick);
            frame(graphics, cost, COST_X, EDIT_Y, COST_W, mouseX, mouseY, partialTick);
            frame(graphics, tranches, 8, TRANCHES_Y, TRANCHES_W, mouseX, mouseY, partialTick);
        }
    }

    private void frame(GuiGraphics graphics, EditBox field, int x, int y, int width, int mouseX, int mouseY,
                       float partialTick) {
        boolean typing = field.isFocused();
        control(graphics, leftPos + x, topPos + y, width, typing ? 0xFFFFFFFF : 0xFFA0A0A0, 0xFF000000, false);
        field.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawGeneral(GuiGraphics graphics, AdminBankStatePayload state, int mouseX, int mouseY) {
        small(graphics, text("interest"), INTEREST_X, GENERAL_LABEL_Y);
        small(graphics, text("hours"), HOURS_X, GENERAL_LABEL_Y);
        small(graphics, text("death"), DEATH_X, GENERAL_LABEL_Y);
        small(graphics, text("combat"), COMBAT_X, GENERAL_LABEL_Y);
        toggle(graphics, INTEREST_X, INTEREST_W, state.interestOn(), mouseX, mouseY);
        toggle(graphics, COMBAT_X, COMBAT_W, state.combatOnly(), mouseX, mouseY);

        boolean ready = generalChanged(state);
        button(graphics, APPLY_X, GENERAL_Y, APPLY_W, Component.literal("✔"), ready ? GO_FACE : DISABLED_FACE,
                ready, mouseX, mouseY);
    }

    private void toggle(GuiGraphics graphics, int x, int width, boolean on, int mouseX, int mouseY) {
        button(graphics, x, GENERAL_Y, width, text(on ? "yes" : "no"), on ? GO_FACE : OFF_FACE, true, mouseX, mouseY);
    }

    private void drawLevels(GuiGraphics graphics, AdminBankStatePayload state, int mouseX, int mouseY) {
        graphics.drawString(font, text("levels"), leftPos + LIST_X, topPos + LEVELS_LABEL_Y, Panels.label(), false);
        int x = leftPos + LIST_X;
        if (!state.readable()) {
            wrapped(graphics, text("unreadable_hint"), x + 3, topPos + LIST_Y + 2, LIST_W - 6, 0xFFFF8A65);
            return;
        }
        List<BankLevels.Level> levels = state.levels();
        if (levels.isEmpty()) {
            wrapped(graphics, text("no_levels"), x + 3, topPos + LIST_Y + 2, LIST_W - 6, 0xFFE8E8E8);
            return;
        }
        for (int row = 0; row < LIST_ROWS && scroll + row < levels.size(); row++) {
            int index = scroll + row;
            BankLevels.Level level = levels.get(index);
            int y = topPos + LIST_Y + row * ROW_H;
            if (index == selected) {
                graphics.fill(x, y, x + LIST_W, y + ROW_H, 0x60FFFFFF);
            } else if (isOver(mouseX, mouseY, x, y, LIST_W, ROW_H)) {
                graphics.fill(x, y, x + LIST_W, y + ROW_H, Panels.HOVER_WASH);
            }
            graphics.drawString(font, Component.literal((index + 1) + ". ").append(SavingsLevels.nameOf(level.name())),
                    x + 3, y + 1, 0xFFFFFFFF, true);
            String holds = CreditFormat.of(level.cap());
            graphics.drawString(font, holds, x + LIST_W - 3 - font.width(holds), y + 1, 0xFFFFD54F, true);
        }
        if (levels.size() > LIST_ROWS) {
            // Where in the list the five shown are, as a thin bar on the right.
            int track = LIST_ROWS * ROW_H;
            int thumb = Math.max(6, track * LIST_ROWS / levels.size());
            int top = (track - thumb) * scroll / (levels.size() - LIST_ROWS);
            graphics.fill(x + LIST_W - 1, topPos + LIST_Y + top, x + LIST_W, topPos + LIST_Y + top + thumb,
                    0xFFC0C0C0);
        }
    }

    private void drawEditor(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!editable()) {
            return;
        }
        small(graphics, text(selected == NEW_LEVEL ? "name_new" : "name"), NAME_X, EDIT_LABEL_Y);
        small(graphics, text("cap"), CAP_X, EDIT_LABEL_Y);
        small(graphics, text(selected == 0 ? "cost_first" : "cost"), COST_X, EDIT_LABEL_Y);
        small(graphics, text("tranches"), 8, TRANCHES_LABEL_Y);

        boolean canAdd = !menu.held().isEmpty();
        button(graphics, ADD_X, ADD_Y, ADD_W, text("add_item"), canAdd ? CONTROL_FACE : DISABLED_FACE, canAdd,
                mouseX, mouseY);
        int at = leftPos + ITEMS_X;
        int y = topPos + AdminBankMenu.INPUT_Y + 1;
        if (items.isEmpty()) {
            // Wrapped to what is left beside the slot, which it ran past in Spanish.
            List<FormattedCharSequence> lines = font.split(text("no_items"),
                    (int) ((ITEMS_R - ITEMS_X) / LABEL_SCALE));
            int lineY = AdminBankMenu.INPUT_Y + 9 - lines.size() * 4;
            for (FormattedCharSequence line : lines) {
                graphics.pose().pushPose();
                graphics.pose().translate(leftPos + ITEMS_X, topPos + lineY, 0);
                graphics.pose().scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
                graphics.drawString(font, line, 0, 0, Panels.label(), false);
                graphics.pose().popPose();
                lineY += 8;
            }
        }
        for (BankLevels.ItemCost item : items) {
            String count = "×" + item.count();
            if (at + 17 + font.width(count) > leftPos + ITEMS_R) {
                graphics.drawString(font, "…", at, y + 4, Panels.label(), false);
                break;
            }
            graphics.renderItem(stackOf(item), at, y);
            graphics.drawString(font, count, at + 17, y + 4, Panels.label(), false);
            at += 17 + font.width(count) + 5;
        }

        boolean canSave = typedName() != null && typedCredits(cap) > 0;
        button(graphics, SAVE_X, BUTTONS_Y, SAVE_W, text(selected == NEW_LEVEL ? "add_level" : "save"),
                canSave ? GO_FACE : DISABLED_FACE, canSave, mouseX, mouseY);
        button(graphics, NEW_X, BUTTONS_Y, NEW_W, text("new"), CONTROL_FACE, true, mouseX, mouseY);
        boolean canRemove = selected != NEW_LEVEL;
        button(graphics, REMOVE_X, BUTTONS_Y, REMOVE_W, text(isArmed() ? "remove_confirm" : "remove"),
                canRemove ? DANGER_FACE : DISABLED_FACE, canRemove, mouseX, mouseY);
    }

    /**
     * A level's details over its row, and an item's name over it in the cost.
     *
     * @return whether one was drawn
     */
    private boolean renderOwnTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int row = rowAt(mouseX, mouseY);
        List<BankLevels.Level> levels = levels();
        if (row >= 0 && editable()) {
            BankLevels.Level level = levels.get(row);
            List<Component> lines = new ArrayList<>();
            lines.add(SavingsLevels.nameOf(level.name()).withStyle(ChatFormatting.YELLOW));
            lines.add(text("tip.cap", CreditFormat.of(level.cap())));
            lines.add(row == 0 ? text("tip.first") : text("tip.cost", CreditFormat.of(level.credits())));
            for (BankLevels.ItemCost item : level.items()) {
                lines.add(Component.literal(" " + item.count() + " × ").append(stackOf(item).getHoverName())
                        .withStyle(ChatFormatting.GRAY));
            }
            lines.add(level.tranches() == null
                    ? text("tip.server_tranches").withStyle(ChatFormatting.GRAY)
                    : text("tip.max_interest", CreditFormat.of(level.tranches().maxInterest())));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return true;
        }
        int item = itemAt(mouseX, mouseY);
        if (item >= 0) {
            graphics.renderComponentTooltip(font, List.of(stackOf(items.get(item)).getHoverName(),
                    text("tip.remove_item").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return true;
        }
        if (isOver(mouseX, mouseY, leftPos + 8, topPos + TRANCHES_Y, TRANCHES_W, H) && editable()) {
            graphics.renderTooltip(font, font.split(text("tip.tranches"), 200), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void button(GuiGraphics graphics, int x, int y, int width, Component label, int face, boolean enabled,
                        int mouseX, int mouseY) {
        int left = leftPos + x;
        int top = topPos + y;
        control(graphics, left, top, width, Panels.OUTLINE, face, enabled && isOver(mouseX, mouseY, left, top, width, H));
        float fit = Math.min(1.0F, (width - 4) / (float) font.width(label));
        graphics.pose().pushPose();
        graphics.pose().translate(left + width / 2.0F, top + TEXT_Y + (1.0F - fit) * 4, 0);
        graphics.pose().scale(fit, fit, 1.0F);
        graphics.drawString(font, label, -font.width(label) / 2, 0, enabled ? ON_FACE : ON_DISABLED, false);
        graphics.pose().popPose();
    }

    /** The outlined, flat control the shop's settings use. */
    private static void control(GuiGraphics graphics, int x, int y, int width, int outline, int face, boolean hovered) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + H + 1, outline);
        graphics.fill(x, y, x + width, y + H, hovered ? face + 0x00191919 : face);
    }

    /** A small label, as the shop writes over its terms fields. */
    private void small(GuiGraphics graphics, Component text, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + x, topPos + y, 0);
        graphics.pose().scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
        graphics.drawString(font, text, 0, 0, Panels.label(), false);
        graphics.pose().popPose();
    }

    private void wrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, true);
            y += 10;
        }
    }

    // --- Input -------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        AdminBankStatePayload state = ClientAdminBankState.state();
        if (state == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (focus(hours, HOURS_X, GENERAL_Y, HOURS_W, mx, my, button)
                || focus(death, DEATH_X, GENERAL_Y, DEATH_W, mx, my, button)) {
            return true;
        }
        if (over(mx, my, INTEREST_X, GENERAL_Y, INTEREST_W)) {
            send(AdminBankActionPayload.of(AdminBankActionPayload.KIND_TOGGLE_INTEREST, 0));
            return true;
        }
        if (over(mx, my, COMBAT_X, GENERAL_Y, COMBAT_W)) {
            send(AdminBankActionPayload.of(AdminBankActionPayload.KIND_TOGGLE_COMBAT, 0));
            return true;
        }
        if (over(mx, my, APPLY_X, GENERAL_Y, APPLY_W)) {
            applyGeneral(state);
            return true;
        }
        if (!editable()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int row = rowAt(mx, my);
        if (row >= 0) {
            selected = row == selected ? NEW_LEVEL : row;
            confirmUntil = 0;
            if (selected == NEW_LEVEL) {
                clearEditor();
            } else {
                fill(state.levels().get(selected));
            }
            playClick();
            return true;
        }
        if (focus(name, NAME_X, EDIT_Y, NAME_W, mx, my, button)
                || focus(cap, CAP_X, EDIT_Y, CAP_W, mx, my, button)
                || focus(cost, COST_X, EDIT_Y, COST_W, mx, my, button)
                || focus(tranches, 8, TRANCHES_Y, TRANCHES_W, mx, my, button)) {
            return true;
        }
        if (over(mx, my, ADD_X, ADD_Y, ADD_W)) {
            addHeld();
            return true;
        }
        int item = itemAt(mx, my);
        if (item >= 0) {
            items.remove(item);
            playClick();
            return true;
        }
        if (over(mx, my, SAVE_X, BUTTONS_Y, SAVE_W)) {
            save();
            return true;
        }
        if (over(mx, my, NEW_X, BUTTONS_Y, NEW_W)) {
            suggestNew();
            playClick();
            return true;
        }
        if (over(mx, my, REMOVE_X, BUTTONS_Y, REMOVE_W)) {
            remove();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Sends the hours and the percent, whichever changed. */
    private void applyGeneral(AdminBankStatePayload state) {
        if (!generalChanged(state)) {
            return;
        }
        long typedHours = number(hours);
        long typedDeath = number(death);
        if (typedHours > 0 && typedHours != state.periodHours()) {
            send(AdminBankActionPayload.of(AdminBankActionPayload.KIND_PERIOD, typedHours));
        }
        if (typedDeath >= 0 && typedDeath != state.deathLoss()) {
            send(AdminBankActionPayload.of(AdminBankActionPayload.KIND_DEATH_LOSS, typedDeath));
        }
        hours.setFocused(false);
        death.setFocused(false);
    }

    private boolean generalChanged(AdminBankStatePayload state) {
        long typedHours = number(hours);
        long typedDeath = number(death);
        return (typedHours > 0 && typedHours != state.periodHours())
                || (typedDeath >= 0 && typedDeath <= 100 && typedDeath != state.deathLoss());
    }

    /** Adds what is in the slot to the cost: its item, as many as the stack holds, joined to any already there. */
    private void addHeld() {
        ItemStack held = menu.held();
        if (held.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).item().equals(id)) {
                items.set(i, new BankLevels.ItemCost(id, items.get(i).count() + held.getCount()));
                playClick();
                return;
            }
        }
        items.add(new BankLevels.ItemCost(id, held.getCount()));
        playClick();
    }

    private void save() {
        String typedName = typedName();
        long typedCap = typedCredits(cap);
        if (typedName == null || typedCap <= 0) {
            return;
        }
        long typedCost = Math.max(0, typedCredits(cost));
        String line = BankLevels.line(typedName, typedCap, typedCost, items, tranches.getValue());
        choose = typedName;
        send(AdminBankActionPayload.saveLevel(selected, line));
    }

    /** Arms the button, then removes on the second click, as the shop's does. */
    private void remove() {
        if (selected == NEW_LEVEL) {
            return;
        }
        if (!isArmed()) {
            confirmUntil = System.currentTimeMillis() + CONFIRM_MILLIS;
            playClick();
            return;
        }
        send(AdminBankActionPayload.removeLevel(selected));
        selected = NEW_LEVEL;
        clearEditor();
        confirmUntil = 0;
    }

    private boolean isArmed() {
        return System.currentTimeMillis() < confirmUntil;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int size = levels().size();
        if (size > LIST_ROWS) {
            scroll = Math.clamp(scroll - (int) Math.signum(deltaY), 0, size - LIST_ROWS);
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox field : new EditBox[]{hours, death, name, cap, cost, tranches}) {
            if (field != null && field.isFocused()) {
                if (keyCode == InputConstants.KEY_ESCAPE) {
                    field.setFocused(false);
                    setFocused(null);
                    return true;
                }
                if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                    if (field == hours || field == death) {
                        applyGeneral(ClientAdminBankState.state());
                    } else {
                        save();
                    }
                    return true;
                }
                // Typing must not close the window on the inventory key.
                if (field.keyPressed(keyCode, scanCode, modifiers) || field.canConsumeInput()) {
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean focus(EditBox field, int x, int y, int width, int mouseX, int mouseY, int button) {
        if (!field.visible || !over(mouseX, mouseY, x, y, width)) {
            return false;
        }
        for (EditBox other : new EditBox[]{hours, death, name, cap, cost, tranches}) {
            other.setFocused(false);
        }
        if (button == 1) {
            field.setValue("");
        }
        setFocused(field);
        field.setFocused(true);
        return true;
    }

    /** The level row under the mouse, or -1. */
    private int rowAt(int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;
        if (!isOver(mouseX, mouseY, x, y, LIST_W, LIST_ROWS * ROW_H)) {
            return -1;
        }
        int index = scroll + (mouseY - y) / ROW_H;
        return index < levels().size() ? index : -1;
    }

    /** The item in the cost under the mouse, or -1. */
    private int itemAt(int mouseX, int mouseY) {
        if (!editable()) {
            return -1;
        }
        int at = leftPos + ITEMS_X;
        int y = topPos + AdminBankMenu.INPUT_Y + 1;
        for (int i = 0; i < items.size(); i++) {
            String count = "×" + items.get(i).count();
            if (at + 17 + font.width(count) > leftPos + ITEMS_R) {
                return -1;
            }
            if (isOver(mouseX, mouseY, at, y, 17 + font.width(count), 16)) {
                return i;
            }
            at += 17 + font.width(count) + 5;
        }
        return -1;
    }

    private boolean over(int mouseX, int mouseY, int x, int y, int width) {
        return isOver(mouseX, mouseY, leftPos + x, topPos + y, width, H);
    }

    private void send(AdminBankActionPayload payload) {
        PacketDistributor.sendToServer(payload);
        playClick();
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // --- Reading the fields ------------------------------------------------------------------

    /** The name typed, or null if there is none. */
    private String typedName() {
        String typed = name.getValue().trim();
        return typed.isEmpty() ? null : typed;
    }

    /** A figure typed with or without a k or an m, or -1 for nothing usable. */
    private static long typedCredits(EditBox field) {
        if (field.getValue().isEmpty()) {
            return -1;
        }
        long typed = CreditFormat.parse(field.getValue()).orElse(-1);
        return typed <= CreditAccount.MAX_BALANCE ? typed : -1;
    }

    /** Digits typed, or -1 for nothing. */
    private static long number(EditBox field) {
        try {
            return field.getValue().isEmpty() ? -1 : Long.parseLong(field.getValue());
        } catch (NumberFormatException tooLong) {
            return -1;
        }
    }

    private static ItemStack stackOf(BankLevels.ItemCost item) {
        return SavingsLevels.item(item).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static MutableComponent text(String key, Object... args) {
        return Component.translatable("economycore.admin.bank." + key, args);
    }

    private static boolean isOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
