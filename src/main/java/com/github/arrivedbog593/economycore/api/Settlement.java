package com.github.arrivedbog593.economycore.api;

import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.platform.CreditStorage;
import com.github.arrivedbog593.economycore.platform.EconomyEvents;
import com.github.arrivedbog593.economycore.platform.ServerBank;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Credits moving between accounts, all at once or not at all.
 * <p>
 * The one way another mod moves credits. A hand of cards that takes from two
 * players and pays a third is one settlement: every account is checked
 * before anything moves, so it never ends half paid, and only when all of it
 * has moved is any of it written to the log. A log line for a movement that
 * was then rolled back would be replayed by crash recovery as if it had
 * happened.
 * <pre>{@code
 * Settlement.Result result = Settlement.create()
 *         .move(loser, -100, MyTypes.BET)
 *         .move(winner, 100, MyTypes.PAYOUT)
 *         .apply(server, "table at 10, 64, -3");
 * }</pre>
 * A movement names a {@link Pocket}; one that names none is the purse, which
 * is what anything that spends or pays a player should use. Savings are moved
 * by naming them: an auction paying a seller who is away, say, or a deposit,
 * which is the same player's purse paying their own savings:
 * <pre>{@code
 * Settlement.create()
 *         .move(player, Pocket.PURSE, -500, TransactionType.SAVINGS_DEPOSIT)
 *         .move(player, Pocket.SAVINGS, 500, TransactionType.SAVINGS_DEPOSIT)
 *         .apply(server, "deposit");
 * }</pre>
 * Takings are made before payments, so a winner is never paid from credits a
 * loser turns out not to have. {@link Bank#ACCOUNT} is an account like any
 * other here, except that it has no savings, and a payment into the bank is
 * never refused: what would take it past the balance cap is not kept.
 * <p>
 * Server thread only.
 */
public final class Settlement {

    /** One pocket's change: positive is paid in, negative taken out. */
    public record Movement(UUID account, Pocket pocket, long delta, TransactionType type) {

        public Movement {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(pocket, "pocket");
            Objects.requireNonNull(type, "type");
            if (pocket == Pocket.SAVINGS && account.equals(Bank.ACCOUNT)) {
                throw new IllegalArgumentException("The server bank has no savings");
            }
        }

        /** A movement of a purse. */
        public Movement(UUID account, long delta, TransactionType type) {
            this(account, Pocket.PURSE, delta, type);
        }
    }

    /** Why a settlement was refused. */
    public enum Refusal {
        /** An account would have to pay more than it holds. */
        CANNOT_COVER,
        /** A player would be paid past the most an account may hold. */
        BALANCE_CAP
    }

    /**
     * What happened.
     *
     * @param refusal   why nothing moved, or null when everything did
     * @param account   the account that stopped it, or null
     * @param shortfall how much it was short of covering, for {@link Refusal#CANNOT_COVER}
     * @param pocket    which of that account's pockets stopped it, or null
     */
    public record Result(Refusal refusal, UUID account, long shortfall, Pocket pocket) {

        private static final Result APPLIED = new Result(null, null, 0, null);

        public boolean applied() {
            return refusal == null;
        }
    }

    /** One pocket of one account: what is checked and moved as a unit. */
    private record Holding(UUID account, Pocket pocket) {
    }

    private final List<Movement> movements = new ArrayList<>();

    private Settlement() {
    }

    public static Settlement create() {
        return new Settlement();
    }

    /** Adds a movement of a purse. A zero one is left out: nothing moves, and nothing is logged. */
    public Settlement move(UUID account, long delta, TransactionType type) {
        return move(account, Pocket.PURSE, delta, type);
    }

    /**
     * Adds a movement of either pocket. A zero one is left out.
     *
     * @throws IllegalArgumentException for the savings of {@link Bank#ACCOUNT}, which has none
     */
    public Settlement move(UUID account, Pocket pocket, long delta, TransactionType type) {
        if (delta != 0) {
            movements.add(new Movement(account, pocket, delta, type));
        }
        return this;
    }

    public List<Movement> movements() {
        return List.copyOf(movements);
    }

    /**
     * Checks every account, then moves and logs everything, or nothing.
     *
     * @param detail free text for the log: what this was, and where
     */
    public Result apply(MinecraftServer server, String detail) {
        CreditStorage storage = CreditStorage.get(server);

        // Checked by pocket rather than by movement, so that two takings
        // from one purse are judged against its balance together.
        Map<Holding, Long> net = new LinkedHashMap<>();
        for (Movement movement : movements) {
            net.merge(new Holding(movement.account(), movement.pocket()), movement.delta(), Math::addExact);
        }
        for (Map.Entry<Holding, Long> entry : net.entrySet()) {
            Holding holding = entry.getKey();
            long change = entry.getValue();
            long balance = storage.balanceOf(holding.account(), holding.pocket());
            if (change < 0 && balance < -change) {
                return new Result(Refusal.CANNOT_COVER, holding.account(), -change - balance, holding.pocket());
            }
            if (change > 0 && !holding.account().equals(Bank.ACCOUNT) && !CreditAccount.canHold(balance, change)) {
                return new Result(Refusal.BALANCE_CAP, holding.account(), 0, holding.pocket());
            }
        }

        // Take before giving. Every taking was checked above, so a refusal
        // here means a balance moved underneath us; undo and refuse.
        List<Movement> taken = new ArrayList<>();
        for (Movement movement : movements) {
            if (movement.delta() >= 0) {
                continue;
            }
            if (!storage.withdraw(movement.account(), movement.pocket(), -movement.delta())) {
                for (Movement undo : taken) {
                    storage.deposit(undo.account(), undo.pocket(), -undo.delta());
                }
                return new Result(Refusal.CANNOT_COVER, movement.account(), 0, movement.pocket());
            }
            taken.add(movement);
        }
        for (Movement movement : movements) {
            if (movement.delta() <= 0) {
                continue;
            }
            if (movement.account().equals(Bank.ACCOUNT)) {
                ServerBank.credit(storage, movement.delta());
            } else {
                storage.deposit(movement.account(), movement.pocket(), movement.delta());
            }
        }

        // Everything has moved. Only now does any of it reach the log.
        for (Movement movement : movements) {
            EconomyEvents.record(storage, movement.type(), movement.account(), movement.pocket(),
                    movement.delta(), storage.balanceOf(movement.account(), movement.pocket()), detail);
        }
        return Result.APPLIED;
    }
}
