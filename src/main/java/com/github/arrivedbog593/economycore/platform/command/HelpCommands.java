package com.github.arrivedbog593.economycore.platform.command;

import com.github.arrivedbog593.economycore.platform.menu.Counter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A directory of EconomyCore's commands.
 * <p>
 * Written out by hand rather than walked from the dispatcher. A generated list
 * gives usage strings and no explanations, and explanations are the point:
 * {@code /economycore spread} says nothing about where the surcharge goes. The
 * cost is that this file has to be edited when a command is added.
 */
public final class HelpCommands {

    /**
     * One line of the directory.
     *
     * @param usage      what to type, arguments and all
     * @param key        translation key for the one-line explanation
     * @param visibleTo  who is shown it: whoever could run it
     */
    private record Entry(String usage, String key, Predicate<CommandSourceStack> visibleTo) {
    }

    private static final Predicate<CommandSourceStack> EVERYONE = source -> true;
    private static final Predicate<CommandSourceStack> OPERATORS = source -> source.hasPermission(2);
    /** Operators and staff administrators: whoever may move money that is not their own. */
    private static final Predicate<CommandSourceStack> BANK = StaffAccess::adminOrOperator;

    /**
     * Grouped the way the commands are, and in the order somebody meets them:
     * the counters and their own credits first, then the machinery behind the
     * exchange and the shop.
     */
    private static final List<Entry> ENTRIES = List.of(
            new Entry("/exchange", "economycore.help.exchange",
                    source -> CounterCommands.mayOpen(source, Counter.EXCHANGE)),
            new Entry("/shop", "economycore.help.shop",
                    source -> CounterCommands.mayOpen(source, Counter.SHOP)),

            new Entry("/credits", "economycore.help.credits", EVERYONE),
            new Entry("/credits balance <player>", "economycore.help.credits_balance_of", BANK),
            new Entry("/credits pay <player> <amount>", "economycore.help.credits_pay", EVERYONE),
            new Entry("/credits give <player> <amount>", "economycore.help.credits_give", BANK),
            new Entry("/credits take <player> <amount>", "economycore.help.credits_take", BANK),
            new Entry("/credits set <player> <amount>", "economycore.help.credits_set", BANK),

            new Entry("/economycore values list [page]", "economycore.help.values_list", OPERATORS),
            new Entry("/economycore values set <credits>", "economycore.help.values_set", OPERATORS),
            new Entry("/economycore values remove <item>",
                    "economycore.help.values_remove", OPERATORS),
            new Entry("/economycore values check", "economycore.help.values_check", OPERATORS),
            new Entry("/economycore spread [percent]", "economycore.help.spread", OPERATORS),

            new Entry("/economycore bank", "economycore.help.bank", BANK),
            new Entry("/economycore bank add <amount>", "economycore.help.bank_add", BANK),
            new Entry("/economycore bank take <amount>", "economycore.help.bank_take", BANK),
            new Entry("/economycore bank set <amount>", "economycore.help.bank_set", BANK),

            new Entry("/economycore shop list [page]", "economycore.help.shop_list", OPERATORS),
            new Entry("/economycore shop add <price>", "economycore.help.shop_add", OPERATORS),
            new Entry("/economycore shop price <entry> <price>",
                    "economycore.help.shop_price", OPERATORS),
            new Entry("/economycore shop remove <entry>", "economycore.help.shop_remove", OPERATORS),

            new Entry("/economycore staff key", "economycore.help.staff_key", EVERYONE),
            new Entry("/economycore staff list", "economycore.help.staff_list", OPERATORS),
            new Entry("/economycore staff add <player> moderator",
                    "economycore.help.staff_add_moderator", OPERATORS),
            new Entry("/economycore staff add <player> admin",
                    "economycore.help.staff_add_admin", OPERATORS),
            new Entry("/economycore staff remove <player>",
                    "economycore.help.staff_remove", OPERATORS));

    private HelpCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("economycore");

        root.executes(context -> help(context.getSource(), 1));

        root.then(Commands.literal("help")
                .executes(context -> help(context.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> help(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "page")))));

        dispatcher.register(root);
    }

    private static int help(CommandSourceStack source, int requestedPage) {
        // Filtered by what this sender could actually run. A player reading
        // about /economycore values remove and being refused by it learns only
        // that the directory lies.
        List<Entry> visible = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.visibleTo().test(source)) {
                visible.add(entry);
            }
        }

        int page = Pagination.clampPage(requestedPage, visible.size());
        source.sendSuccess(() -> Pagination.header(
                "economycore.help.title", "/economycore help", page, visible.size()), false);

        for (Entry entry : Pagination.slice(visible, page)) {
            source.sendSuccess(() -> line(entry), false);
        }
        return visible.size();
    }

    /**
     * One entry, with the usage clickable.
     * <p>
     * Suggested rather than run, since most of these take arguments and
     * running half a command only produces an error. Clicking puts it in the
     * chat box with the cursor after it, which is where somebody reading a
     * directory wants to end up.
     */
    private static Component line(Entry entry) {
        return Component.literal(entry.usage())
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.SUGGEST_COMMAND, entry.usage()))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("economycore.help.click_to_type"))))
                .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable(entry.key()).withStyle(ChatFormatting.GRAY));
    }
}