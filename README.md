# Economy Core

A [NeoForge](https://neoforged.net/) 1.21.1 mod that gives a server an economy in credits,
and a base other mods can build on: casinos, auction houses, bazaars.

[Español](README_ES.md)

> **Beta.** Everything below works and has been played on a dedicated server, but things
> may still change before 1.0. Back up your world before updating.

---

## What it adds

- **Personal accounts in credits** for every player, saved with the world. Every movement
  is written to a log, and whatever a crash interrupted is put back when the server starts.
- **The Exchange**: hand any item in for credits and buy it back later, at prices the
  server sets in game. Any mod's items can be currency, or plain vanilla ingots. An optional
  buyback surcharge keeps players from using it as free storage.
- **The Shop**: sells items for credits, including enchanted or renamed ones, exactly as they
  were listed. It never buys anything back, which makes it the place credits leave the
  economy.
- **Price checking**: before a price is accepted, it is checked against the real crafting
  recipes. A set of prices that would let players mint credits by crafting is refused, and
  the right figure is suggested.
- **A staff list** that runs the economy without operator rights. Moderators run the shop
  and the exchange; administrators also run the server bank and players' balances. Each
  gets a staff key of their own.

## Three ways in

The exchange and the shop open the same screen whichever way a player reaches them. Each
server chooses which ones it uses.

- **A block** to place behind a counter.
- **A clerk**: a shop or exchange NPC, placed with its spawn egg or `/summon` like any mob.
  Clerks stay where they are put, face the way they were placed, never take damage and
  never despawn, and turn their head to whoever is nearby. Each kind has three outfits of
  its own. Crouch and right-click one in creative with an empty hand to change its outfit;
  hit it in creative to remove it. Name one with a name tag.
- **A command**: `/exchange` and `/shop`, from anywhere. Both are on by default and can be
  switched off in the server config to make players walk to a counter.

A staff key opens the settings of any of them: prices, the shop's listings, the surcharge.

## Commands

| Command                          | Who                             | What it does                                   |
|----------------------------------|---------------------------------|------------------------------------------------|
| `/exchange`, `/shop`             | everyone, unless switched off   | Open the exchange or the shop                  |
| `/credits`                       | everyone                        | Your balance                                   |
| `/credits pay <player> <amount>` | everyone                        | Send credits to another player                 |
| `/credits convert`               | everyone                        | Turn the item in your hand into credits        |
| `/credits redeem <item> [count]` | everyone                        | Buy items back with credits                    |
| `/credits balance <player>`      | staff administrators, operators | Anybody's balance, online or not               |
| `/credits give / take / set`     | staff administrators, operators | Change a balance                               |
| `/economycore help`              | everyone                        | Every command you can use, with a line on each |

`/economycore` also covers prices (`values`), the buyback surcharge (`spread`), the shop
(`shop`), the server bank (`bank`) and the staff list (`staff`). `/economycore help` lists
them for whoever may use them.

## Configuration

`config/economycore-server.toml`. A copy in a world's `serverconfig` folder overrides it
for that world.

| Setting                  | Default | What it does                                                        |
|--------------------------|---------|---------------------------------------------------------------------|
| `access.exchangeCommand` | `true`  | Let players open the exchange with `/exchange`                      |
| `access.shopCommand`     | `true`  | Let players open the shop with `/shop`                              |
| `bank.alwaysOn`          | `false` | Keep the server bank on even when no mod asks for it                |
| `bank.incomeToBank`      | `true`  | Pay what the server takes in into the bank, rather than removing it |

## The server bank and the server's income

Shop sales, the exchange's buyback surcharge and any fee another mod charges are always
taken from players: that is how credits leave circulation. If a mod that pays out of a bank
is installed, such as a casino, that income goes into the server bank. Otherwise it simply
disappears.

---

## For mod developers

Economy Core knows nothing about the mods that use it: they depend on it, never the other
way round. Everything a mod needs is in the `com.github.arrivedbog593.economycore.api`
package, and only that package keeps its shape between releases.

- **`Credits`**: a player's balance, and what of it they can still spend.
- **`PlayerCommitments`** (from `Credits.commitments()`): record credits a player has
  promised but not yet paid, such as a bet or a bid, so the shop and the exchange cannot
  spend them twice.
- **`Settlement`**: move credits between any number of accounts at once. Every account is
  checked first, nothing moves if one cannot pay, and the whole movement is logged and
  recovered after a crash.
- **`Bank`**: the server's own account. A mod that pays out of it switches it on with
  `Bank.enable(modId)` while it loads, and declares what it owes with `Bank.promise(...)` so
  staff withdrawals never dip into it.
- **`TransactionType`**: register your own kinds of movement for the log with
  `TransactionType.register(code, recoverable)` from your mod's constructor.
- **`Staff`** and **`StaffRank`**: whether a player is staff, and whether an item is a staff
  key.
- **`CreditFormat`**: credits written the way the rest of the mod writes them.

All of it is server-thread only.

### Depending on it

```gradle
repositories {
    maven { url = "https://cursemaven.com" }
}

dependencies {
    implementation "curse.maven:economy-core-1775541:<fileId>"
}
```

Replace `<fileId>` with the file of the version you want: it is the number at the end of
that file's page on CurseForge.

## Building

```
./gradlew build
./gradlew publishToMavenLocal   # for mods that depend on it, during development
```

## License

[MIT](LICENSE).
