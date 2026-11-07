package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.platform.EconomyClientConfig;
import com.github.arrivedbog593.economycore.platform.PassWatch;
import com.github.arrivedbog593.economycore.platform.network.PursePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The purse and the bank at the right of the screen, where SkyBlock keeps
 * its sidebar.
 * <p>
 * Drawn the way vanilla draws a scoreboard sidebar, a dim box and plain
 * text, so the two read as one thing when a server shows both; and stacked
 * above that sidebar rather than over it when there is one.
 */
public final class PurseHud {

    private static final int MARGIN = 3;
    private static final int PADDING = 2;
    private static final int LINE = 9;
    /** Vanilla's sidebar shows no more lines than this. */
    private static final int SIDEBAR_MAX_LINES = 15;

    private PurseHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        PursePayload purse = ClientPurse.last();
        if (purse == null || minecraft.options.hideGui || minecraft.player == null
                || !EconomyClientConfig.SPEC.isLoaded() || !EconomyClientConfig.SHOW_PURSE.get()) {
            return;
        }
        Font font = minecraft.font;
        List<Component> lines = new ArrayList<>(List.of(
                line("economycore.hud.purse", purse.purse()),
                line("economycore.hud.bank", purse.savings())));
        // Counted down here from when it runs out, so it ticks without the server.
        long passLeft = ClientPurse.passLeft();
        if (passLeft > 0) {
            lines.add(Component.translatable("economycore.hud.pass", PassWatch.describe(passLeft)
                    .copy().withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int height = lines.size() * LINE;
        int right = graphics.guiWidth() - MARGIN;
        int left = right - width;
        int bottom = sidebarTop(minecraft, graphics.guiHeight()) - MARGIN;
        int top = bottom - height;

        int background = minecraft.options.getBackgroundColor(0.3F);
        graphics.fill(left - PADDING, top - PADDING, right + PADDING, bottom + PADDING - 1, background);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), left, top + i * LINE, 0xFFFFFFFF, false);
        }
    }

    private static Component line(String key, long credits) {
        return Component.translatable(key, Component.literal(CreditFormat.of(credits))
                .withStyle(ChatFormatting.GOLD));
    }

    /**
     * Where vanilla's sidebar begins, so this sits just above it; or a little
     * above the middle of the screen when there is none, where it would be.
     */
    private static int sidebarTop(Minecraft minecraft, int screenHeight) {
        Scoreboard scoreboard = minecraft.level == null ? null : minecraft.level.getScoreboard();
        Objective objective = scoreboard == null ? null : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return screenHeight / 2;
        }
        // Counted as vanilla counts them, hidden scores left out. With none
        // the sidebar is still there, its title alone.
        int entries = (int) Math.min(scoreboard.listPlayerScores(objective).stream()
                .filter(score -> !score.isHidden()).count(), SIDEBAR_MAX_LINES);
        // Vanilla's arithmetic: the entries end a third of their height below
        // the middle, and the title takes a line above them.
        int listed = entries * LINE;
        return screenHeight / 2 + listed / 3 - listed - LINE - 1;
    }
}
