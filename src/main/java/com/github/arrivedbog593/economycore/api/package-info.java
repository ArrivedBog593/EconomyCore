/**
 * What other mods may use of EconomyCore, and the only part that keeps its
 * shape from one release to the next.
 * <p>
 * Everything a mod needs to take part in the economy is here: reading a
 * balance and what of it is spendable ({@link com.github.arrivedbog593.economycore.api.Credits}),
 * promising credits that are not yet paid ({@link com.github.arrivedbog593.economycore.api.PlayerCommitments}),
 * moving credits all at once, logged and recoverable
 * ({@link com.github.arrivedbog593.economycore.api.Settlement}), the server's own
 * account ({@link com.github.arrivedbog593.economycore.api.Bank}), its own kinds of
 * movement for the log ({@link com.github.arrivedbog593.economycore.api.TransactionType}),
 * staff ranks ({@link com.github.arrivedbog593.economycore.api.Staff}) and how
 * credits are written ({@link com.github.arrivedbog593.economycore.api.CreditFormat}).
 * <p>
 * Everything outside this package — {@code engine}, {@code platform},
 * {@code client} — is EconomyCore's own and may change in any release,
 * however public its classes are. A mod that reaches past this package may
 * break on the next update; ask for what is missing here instead.
 * <p>
 * Server thread only, like the economy itself.
 */
package com.github.arrivedbog593.economycore.api;
