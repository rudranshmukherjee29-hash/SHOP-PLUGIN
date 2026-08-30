# ShopPlugin

A focused, secure GUI shop and vanilla-item economy plugin for Paper (and
Spigot-compatible) Minecraft servers, built on top of Vault.

This is not a general-purpose shop framework — it ships with one curated,
balanced vanilla item catalog and is designed to be usable immediately after
install with sensible defaults.

---

## Contents

- `README.md` — this file: installation, configuration overview, commands, permissions
- `docs/ECONOMY.md` — full reasoning behind the default prices
- `docs/SECURITY.md` — anti-exploit design and what protects against what
- `docs/TESTING_CHECKLIST.md` — the checklist to run through before trusting this on a live economy

---

## Requirements

- Paper (recommended) 1.21.x, or a Spigot-compatible fork. Folia is supported
  for the plugin's own scheduling (see `docs/SECURITY.md` for the one caveat
  around inventory-touching code always running on the correct region thread).
- Java 21+
- **Vault**, plus an economy plugin that registers with it (e.g. EssentialsX).
  ShopPlugin does not implement its own economy — it will not start
  processing transactions without a working Vault economy provider.
- Optional: PlaceholderAPI, LuckPerms (standard Bukkit permissions work fine
  without LuckPerms specifically installed), Geyser/Floodgate.

## Building

This includes a Gradle wrapper, so no local Gradle install is required:

```
./gradlew shadowJar
```

The output jar will be in `build/libs/ShopPlugin-1.0.0.jar`. Copy that
single jar into your server's `plugins/` folder — the shadow (fat) jar
bundles SQLite/HikariCP/MySQL-connector so you don't need to install them
separately.

A GitHub Actions workflow (`.github/workflows/build.yml`) is included and
will build this automatically on every push — check the **Actions** tab of
your repository after pushing, and download the built jar from the
workflow's run artifacts (`ShopPlugin` artifact) once it goes green.

> **Note on this deliverable:** this source was written and reviewed in an
> environment without access to Maven Central or the PaperMC repository, so
> it has **not** been compiled locally against the real Paper/Vault/Adventure
> jars. It has been carefully hand-reviewed line-by-line, cross-checked for
> constructor/signature consistency across every call site, and partially
> verified against hand-written API stubs. The GitHub Actions workflow above
> is the first point where this actually compiles against the real
> dependencies — treat a red build there as expected-possible on first push,
> and check the compiler output for the specific line if so. Run through
> `docs/TESTING_CHECKLIST.md` on a staging server before using this on a
> server with real player balances, regardless of build outcome.

## Installation

1. Install Vault and an economy plugin (EssentialsX is the most common
   choice) if you haven't already.
2. Drop `ShopPlugin-1.0.0.jar` into `plugins/`.
3. Start the server once to generate the default config files in
   `plugins/ShopPlugin/`.
4. Check the console for a line like `Using economy provider: Essentials
   Economy` — if instead you see a `Vault Economy provider is not
   registered` error, ShopPlugin will stay loaded but every transaction will
   safely fail (no money or items exchanged) until you fix the Vault setup
   and run `/shopadmin reload` or restart.
5. Open the shop in-game with `/shop`.
6. Review `plugins/ShopPlugin/prices.yml` against your server's economy —
   the shipped prices are tuned for a typical single-server vanilla-ish
   survival economy (see `docs/ECONOMY.md`), but every server's economy is
   different, especially if you already have other money sinks/faucets.
7. Run `/shopadmin validateprices` after any pricing changes.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/shop` | Opens the main shop GUI | `shopplugin.shop` (default: true) |
| `/shop <category>` | Opens a specific category directly | `shopplugin.shop` |
| `/shop search <query>` | Opens search results for a query | `shopplugin.shop` |
| `/sellall` | Sells everything sellable in your inventory | `shopplugin.sellall` (default: true) |
| `/sellinventory` | Alias of `/sellall` | `shopplugin.sellall` |
| `/sellhand` | Sells the exact stack in your main hand | `shopplugin.sell` (default: true) |
| `/shopadmin reload` | Reloads all config files and the catalog | `shopplugin.admin.reload` (default: op) |
| `/shopadmin stats [item]` | Shows shop-wide or per-item statistics | `shopplugin.admin.stats` (default: op) |
| `/shopadmin givebalance <player> <amount>` | Grants balance via Vault | `shopplugin.admin.givebalance` (default: op) |
| `/shopadmin audit <item>` | Shows pricing + transaction history for one item | `shopplugin.admin.audit` (default: op) |
| `/shopadmin validateprices` | Scans `prices.yml` for arbitrage loops and misconfigurations | `shopplugin.admin.validateprices` (default: op) |

## Permissions

See `plugin.yml` for the full list; the important ones:

- `shopplugin.shop`, `shopplugin.buy`, `shopplugin.sell`, `shopplugin.sellall`
  — default true, the basic player permissions.
- `shopplugin.bypasslimits` — default op, skips per-player daily purchase
  limits.
- `shopplugin.admin` — default op, umbrella permission; individual
  `shopplugin.admin.*` nodes exist if you want finer-grained delegation
  through LuckPerms.

## GUI usage (for players)

- **Left click** a buyable item: buy 1 (or the configured default).
- **Shift + Left click**: buy 64 (bounded by max transaction size, stock,
  and balance).
- **Right click** a sellable item: sell 1.
- **Shift + Right click**: sell all of that item currently in your
  inventory.
- Transactions above the configured value threshold (`gui.yml` →
  `confirmation-threshold` in `config.yml`, default $5000) open a
  confirmation screen instead of executing immediately.

## Configuration files

- `config.yml` — database type, global limits, rate limiting, GUI
  confirmation threshold, sounds.
- `prices.yml` — the curated item catalog. See `docs/ECONOMY.md` before
  editing.
- `gui.yml` — titles, layout slots, decorative items, sounds.
- `messages.yml` — every player-facing string, MiniMessage-formatted.
- `limits.yml` — global stock-system behavior (per-item stock settings live
  directly on the item in `prices.yml`).
- `shops.yml` — shop identity/branding metadata.

All files are re-read from disk on `/shopadmin reload`, applied atomically
(no player sees a half-reloaded catalog mid-transaction).

## Database

SQLite is the default and requires no setup. For MySQL/MariaDB, set
`database.type: mysql` in `config.yml` and fill in `database.mysql.*`. All
database writes happen off the main thread via Bukkit's async scheduler;
statistics reads served to commands come from an in-memory cache that's
refreshed asynchronously, so `/shop stats` never blocks the server on I/O.

## Known intentional limitations

- This plugin intentionally does not include every vanilla item — see the
  exclusion note at the bottom of `prices.yml` and `docs/ECONOMY.md` for
  which items were left out and why.
- There is one unified shop, not a multi-shop framework. If you need
  region-based or player-owned shops, this plugin is not the right base for
  that — it was scoped deliberately narrow per the design brief.
- Folia: the transaction service assumes it's on "the" main thread the way
  Paper/Spigot single-threaded servers work. On Folia, make sure GUI-related
  events for a given player are always handled on that player's region
  thread (Folia does this automatically for player-sourced inventory
  events), and avoid triggering shop transactions from cross-region contexts
  (e.g. a command block or plugin API call from a different region) without
  scheduling onto the right region first.
