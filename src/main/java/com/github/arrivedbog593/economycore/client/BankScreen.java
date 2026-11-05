package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.platform.PersonalBank;
import com.github.arrivedbog593.economycore.platform.menu.BankMenu;
import com.github.arrivedbog593.economycore.platform.network.BankActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * The banker's window: purse on the left, savings on the right, an amount,
 * and a button for each way between them.
 * <p>
 * Drawn the way the shop is, from {@link Panels} rather than a texture, and
 * with no inventory: nothing here takes or gives items. The figures are the
 * menu's, kept in step by the server every tick, so they move the moment a
 * deposit lands. Buttons that would be refused are grayed out from those
 * same figures; the server checks again regardless, and says why in the
 * action bar if it refuses.
 */
public class BankScreen extends AbstractContainerScreen<BankMenu> {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 124;
    private static final int MARGIN = 8;
    private static final int COLUMN_W = (WIDTH - 2 * MARGIN - MARGIN) / 2;
    private static final int RIGHT_X = MARGIN + COLUMN_W + MARGIN;

    private static final int POCKET_Y = 20;
    private static final int POCKET_H = 34;
    private static final int AMOUNT_Y = 62;
    private static final int ROW_H = 14;
    private static final int ONE_Y = 82;
    private static final int ALL_Y = 100;
    private static final int TEXT_Y = (ROW_H - 8) / 2 + 1;

    private static final int DEPOSIT_FACE = 0xFF2E7D32;
    private static final int WITHDRAW_FACE = 0xFF8D6E1A;
    private static final int DISABLED_FACE = 0xFF4A4A4A;
    private static final int FIELD_FACE = 0xFF000000;
    private static final int PURSE_TEXT = 0xFF2E7D32;
    private static final int SAVINGS_TEXT = 0xFF1F4D2B;

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
        amount = new EditBox(font, leftPos + MARGIN + 4, topPos + AMOUNT_Y + TEXT_Y,
                WIDTH - 2 * MARGIN - 8, 8, Component.translatable("economycore.my_bank.amount"));
        amount.setBordered(false);
        amount.setHint(Component.translatable("economycore.my_bank.amount"));
        // Digits only, and never more than an account can hold: the most a
        // balance can be is thirteen digits long.
        amount.setFilter(text -> text.isEmpty() || text.matches("\\d{1,13}"));
        amount.setValue(carried);
        addRenderableWidget(amount);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Panels.panel(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        Panels.recess(graphics, leftPos + MARGIN, topPos + POCKET_Y, COLUMN_W, POCKET_H);
        Panels.recess(graphics, leftPos + RIGHT_X, topPos + POCKET_Y, COLUMN_W, POCKET_H);
        graphics.fill(leftPos + MARGIN - 1, topPos + AMOUNT_Y - 1,
                leftPos + WIDTH - MARGIN + 1, topPos + AMOUNT_Y + ROW_H + 1, Panels.OUTLINE);
        graphics.fill(leftPos + MARGIN, topPos + AMOUNT_Y,
                leftPos + WIDTH - MARGIN, topPos + AMOUNT_Y + ROW_H, FIELD_FACE);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        long typed = typed();
        drawButton(graphics, MARGIN, ONE_Y, "economycore.my_bank.deposit",
                typed > 0 && typed <= menu.free(), DEPOSIT_FACE, mouseX, mouseY);
        drawButton(graphics, RIGHT_X, ONE_Y, "economycore.my_bank.withdraw",
                typed > 0 && typed <= menu.savings(), WITHDRAW_FACE, mouseX, mouseY);
        drawButton(graphics, MARGIN, ALL_Y, "economycore.my_bank.deposit_all",
                menu.free() > 0, DEPOSIT_FACE, mouseX, mouseY);
        drawButton(graphics, RIGHT_X, ALL_Y, "economycore.my_bank.withdraw_all",
                menu.savings() > 0, WITHDRAW_FACE, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, Panels.LABEL_TEXT, false);
        drawPocket(graphics, MARGIN, Component.translatable("economycore.my_bank.purse"),
                menu.purse(), PURSE_TEXT);
        drawPocket(graphics, RIGHT_X, Component.translatable("economycore.my_bank.savings"),
                menu.savings(), SAVINGS_TEXT);
        // Only when it explains something: a purse whose deposit-all moves
        // less than it shows, because part of it is riding on a table.
        long promised = menu.purse() - menu.free();
        if (promised > 0) {
            Component note = Component.translatable("economycore.my_bank.promised", CreditFormat.of(promised));
            graphics.drawString(font, note, MARGIN + 3, POCKET_Y + POCKET_H - 10, Panels.LABEL_TEXT, false);
        }
    }

    private void drawPocket(GuiGraphics graphics, int x, Component label, long credits, int color) {
        graphics.drawString(font, label, x + 3, POCKET_Y + 3, Panels.LABEL_TEXT, false);
        String figure = CreditFormat.of(credits);
        // Shrinks rather than overflows: at the cap the figure is wider than the well.
        float scale = Math.min(1.0F, (COLUMN_W - 6) / (float) font.width(figure));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 3, POCKET_Y + 14, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, figure, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** A labeled button, grayed out when it would be refused. */
    private void drawButton(GuiGraphics graphics, int x, int y, String key, boolean enabled, int face,
                            int mouseX, int mouseY) {
        int left = leftPos + x;
        int top = topPos + y;
        boolean hovered = enabled && isOver(mouseX, mouseY, left, top, COLUMN_W, ROW_H);
        graphics.fill(left - 1, top - 1, left + COLUMN_W + 1, top + ROW_H + 1, Panels.OUTLINE);
        int fill = enabled ? face : DISABLED_FACE;
        graphics.fill(left, top, left + COLUMN_W, top + ROW_H, hovered ? fill + 0x00191919 : fill);
        Component label = Component.translatable(key);
        graphics.drawString(font, label, left + (COLUMN_W - font.width(label)) / 2, top + TEXT_Y,
                enabled ? 0xFFFFFFFF : 0xFF9A9A9A, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX;
        int y = (int) mouseY;
        if (isOver(x, y, leftPos + MARGIN, topPos + AMOUNT_Y, WIDTH - 2 * MARGIN, ROW_H)) {
            setFocused(amount);
            amount.setFocused(true);
            return true;
        }
        amount.setFocused(false);
        long typed = typed();
        if (click(x, y, MARGIN, ONE_Y, typed > 0 && typed <= menu.free(), true, typed)
                || click(x, y, RIGHT_X, ONE_Y, typed > 0 && typed <= menu.savings(), false, typed)
                || click(x, y, MARGIN, ALL_Y, menu.free() > 0, true, PersonalBank.ALL)
                || click(x, y, RIGHT_X, ALL_Y, menu.savings() > 0, false, PersonalBank.ALL)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean click(int x, int y, int buttonX, int buttonY, boolean enabled, boolean deposit, long sum) {
        if (!enabled || !isOver(x, y, leftPos + buttonX, topPos + buttonY, COLUMN_W, ROW_H)) {
            return false;
        }
        PacketDistributor.sendToServer(new BankActionPayload(deposit, sum));
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (amount != null && amount.isFocused()) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                amount.setFocused(false);
                setFocused(null);
                return true;
            }
            // Typing a number must not close the window on the inventory key.
            if (amount.keyPressed(keyCode, scanCode, modifiers) || amount.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private static boolean isOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
