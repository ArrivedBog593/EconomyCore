# EconomyCore

A [NeoForge](https://neoforged.net/) 1.21.1 mod that gives a server an economy other
mods can build on.

- **Personal accounts in credits** for every player, saved with the world, with every
  movement written to a log that restores what a crash lost.
- **The Exchange**: hands any item in for credits and back out again, at prices the
  server sets in game. Items are addressed by id as plain text, so any mod's items can
  be currency — or plain vanilla ingots.
- **Anti-exploit validation**: proposed prices are checked against the real crafting
  recipes before they are accepted, so a set of prices that would let players mint
  credits by crafting is refused.
- **The Shop**: sells items, enchanted or custom ones included, for credits, and never
  buys them back.
- **A staff list** of moderators and administrators who can run the shop and the
  exchange without being operators.

## For mods built on it

EconomyCore knows nothing about the mods that use it. They depend on it, never the other
way round.

- **Spending safely**: `Commitments.players()` records credits a player has promised
  but not yet paid — a bet, a bid — and `Commitments.spendable(...)` is what they can
  still spend.
- **Your own reasons for moving credits**: register them with
  `TransactionType.register(code, recoverable)` from your mod's constructor, before
  the server starts. Only movements where no item changed hands are recoverable.
- **A server bank**, off unless something needs it. A mod that pays out of it — a
  casino, a lottery — switches it on with `ServerBank.enable(modId)` while it loads, and
  tells it what it owes with `ServerBank.promise(...)` so staff withdrawals never dip
  into that. A server owner can also keep it on in the config.
- **The server's income** — shop sales, the buyback surcharge, any fee a mod charges —
  is always charged: it is how credits leave players' hands. While the bank is on it is
  paid into it (unless the config says otherwise); with the bank off it leaves
  circulation.

## Commands

`/credits` for players; `/economycore help` lists the rest. `/economycore bank` runs the server
bank, for operators and staff administrators.

## Building

```
./gradlew build
./gradlew publishToMavenLocal   # for mods that depend on it, during development
```
