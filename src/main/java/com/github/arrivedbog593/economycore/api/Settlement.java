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
 * Takings are made before payments, so a winner is never paid from credits a
 * loser turns out not to have. {@link Bank#ACCOUNT} is an account like any
 * other here, except that a payment into the bank is never refused: what would
 * take it past the balance cap is not kept.
 * <p>
 * Server thread only.
 */
public final class Settlement {

    /** One account's change: positive is paid in, negative taken out. */
    public record Movement(UUID account, long delta, TransactionType type) {

        public Movement {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(type, "type");
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
     */
    public record Result(Refusal refusal, UUID account, long shortfall) {

        private static final Result APPLIED = new Result(null, null, 0);

        public boolean applied() {
            return refusal == null;
        }
    }

    private final List<Movement> movements = new ArrayList<>();

    private Settlement() {
    }

    public static Settlement create() {
        return new Settlement();
    }

    /** Adds a movement. A zero one is left out: nothing moves, and nothing is logged. */
    public Settlement move(UUID account, long delta, TransactionType type) {
        if (delta != 0) {
            movements.add(new Movement(account, delta, type));
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

        // Checked by account rather than by movement, so that two takings
        // from one account are judged against its balance together.
        Map<UUID, Long> net = new LinkedHashMap<>();
        for (Movement movement : movements) {
            net.merge(movement.account(), movement.delta(), Math::addExact);
        }
        for (Map.Entry<UUID, Long> entry : net.entrySet()) {
            UUID account = entry.getKey();
            long change = entry.getValue();
            long balance = storage.balanceOf(account);
            if (change < 0 && balance < -change) {
                return new Result(Refusal.CANNOT_COVER, account, -change - balance);
            }
            if (change > 0 && !account.equals(Bank.ACCOUNT) && !CreditAccount.canHold(balance, change)) {
                return new Result(Refusal.BALANCE_CAP, account, 0);
            }
        }

        // Take before giving. Every taking was checked above, so a refusal
        // here means a balance moved underneath us; undo and refuse.
        List<Movement> taken = new ArrayList<>();
        for (Movement movement : movements) {
            if (movement.delta() >= 0) {
                continue;
            }
            if (!storage.withdraw(movement.account(), -movement.delta())) {
                for (Movement undo : taken) {
                    storage.deposit(undo.account(), -undo.delta());
                }
                return new Result(Refusal.CANNOT_COVER, movement.account(), 0);
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
                storage.deposit(movement.account(), movement.delta());
            }
        }

        // Everything has moved. Only now does any of it reach the log.
        for (Movement movement : movements) {
            EconomyEvents.record(storage, movement.type(), movement.account(), movement.delta(),
                    storage.balanceOf(movement.account()), detail);
        }
        return Result.APPLIED;
    }
}
