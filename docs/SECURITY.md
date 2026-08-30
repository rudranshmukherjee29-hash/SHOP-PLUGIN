# Security & Anti-Exploit Design

This document maps each exploit category from the design brief to the
specific mechanism in the codebase that defends against it, so a reviewer
can verify coverage without having to reverse-engineer the code first.

## Threat → defense map

| Threat | Defense | Where |
|---|---|---|
| Fast/double clicking, packet spam | Per-player `RateLimiter` (min interval between actions) + `TransactionGuard` (one in-flight transaction per player) | `security/RateLimiter.java`, `security/TransactionGuard.java` |
| Shift-click extraction from shop GUI | `InventoryClickEvent` cancelled unconditionally for any click landing on the shop's top inventory | `listener/ShopGuiProtectionListener.java#onClick`, `listener/ShopClickListener.java#onClick` (also cancels) |
| Double-click collection | `ClickType.DOUBLE_CLICK` in the dangerous-click-types set, cancelled | `ShopGuiProtectionListener.java` |
| Drag exploits | `InventoryDragEvent` cancelled if any dragged raw slot falls within the top inventory | `ShopGuiProtectionListener.java#onDrag` |
| Number-key swap | `ClickType.NUMBER_KEY` cancelled | `ShopGuiProtectionListener.java#onClick` |
| Offhand swapping | `PlayerSwapHandItemsEvent` cancelled outright while any shop GUI is open | `ShopGuiProtectionListener.java#onSwapHands` |
| Cursor-item exploits | Any click on the top inventory is cancelled before Bukkit can move a cursor item into/out of it; cursor is also swept clean on inventory close as defense-in-depth | `ShopGuiProtectionListener.java#onClick`, `#onClose` |
| Creative mode interactions | `ClickType.CREATIVE` included in dangerous-click-types | `ShopGuiProtectionListener.java` |
| Inventory automation (hoppers pulling from/pushing to the GUI) | `InventoryMoveItemEvent` cancelled if either side is a shop GUI holder | `ShopGuiProtectionListener.java#onHopperMove` |
| Item duplication via GUI item extraction | GUI buttons are tagged via `PersistentDataContainer`, never identified by name/lore/CMD; the GUI inventory itself is a read-only display — every click on it is cancelled, full stop, regardless of what the click "means" for shop logic | `security/ShopItemTagger.java`, `ShopGuiProtectionListener.java` |
| Disconnecting mid-transaction | Transactions are synchronous (single main-thread method call) — there is no "mid-transaction" state that spans a tick, so disconnect can't interrupt one partway. `PlayerCleanupListener` clears the (already-released) lock/rate-limit entries to prevent memory growth | `service/DefaultTransactionService.java` (synchronous design), `listener/PlayerCleanupListener.java` |
| Simultaneous transactions (race for last stock item) | `InMemoryStockService#tryReserve` uses `AtomicLong.updateAndGet`, an atomic compare-and-swap-style update, so two "check then decrement" sequences can never interleave | `service/InMemoryStockService.java` |
| Negative / zero / absurd quantities | `NumericGuard.isValidQuantity` rejects `<= 0` and anything above `ABSOLUTE_MAX_QUANTITY` (1,000,000) or the configured per-item/global max | `security/NumericGuard.java` |
| Integer overflow | All price math uses `BigDecimal`, never `int`/`long`/`double` arithmetic for money; `safeMultiply` additionally rejects any computed total above a sanity ceiling | `security/NumericGuard.java#safeMultiply` |
| Floating-point precision exploits | No `float`/`double` is used anywhere in price calculation; `BigDecimal` throughout, rounded once at the end to the economy's actual fractional-digit precision | `NumericGuard.java`, `VaultEconomyProvider.java` |
| Balance desync / blind trust in economy response | Every Vault call's `EconomyResponse.transactionSuccess()` is checked; nothing is assumed to have succeeded just because the call didn't throw | `economy/VaultEconomyProvider.java` |
| Database sync issues | All writes are async (`Bukkit.getScheduler().runTaskAsynchronously`); reads for commands come from an in-memory cache refreshed asynchronously, never a synchronous query on the main thread | `service/DatabaseStatisticsService.java` |
| Repeated packet/event triggering the same logical action | `TransactionGuard` + `RateLimiter` together — a burst of clicks/packets collapses to at most one transaction per rate-limit window, and never more than one in-flight at a time | `security/TransactionGuard.java`, `RateLimiter.java` |

## The transaction sequence (buy)

Implemented exactly as specified, in `DefaultTransactionService#buy`:

1. Validate player/transaction state (online check, rate limit, guard).
2. Validate requested quantity (`NumericGuard`).
3. Validate item configuration (exists, buyable).
4. Calculate exact price safely (`BigDecimal`, `safeMultiply`).
5. Validate stock if applicable (atomic reserve).
6. Verify player balance (`economy.has(...)`).
7. Withdraw money through Vault.
8. Verify withdrawal success (`EconomyOperationResult`).
9. *(Stock already atomically reserved in step 5 — reserving early, before
   payment, closes the race window on the scarce resource; if a later step
   fails, the reservation is never released back because by that point
   payment succeeded and the transaction is being completed, EXCEPT for the
   inventory-full case below, which does roll back.)*
10. Add the exact configured item amount — but only after confirming
    inventory capacity; if capacity is insufficient, the withdrawal from
    step 7 is refunded and the transaction fails cleanly with no money or
    items exchanged.
11. Verify inventory handling (capacity pre-check before mutating; if
    Bukkit's `addItem` unexpectedly returns overflow despite the pre-check,
    overflow items are dropped at the player's feet rather than lost or
    silently discarded).
12. Record transaction (async, non-blocking).
13. Release transaction guard (in a `finally` block, so it always runs).

## The transaction sequence (sell)

Implemented exactly as specified, in `DefaultTransactionService#sell` /
`#sellInternal`:

1. Validate transaction state (online check, rate limit, guard, permission).
2. Validate item and quantity.
3. Count exact matching items actually present in inventory (never trust a
   claimed count).
4. Remove only the exact verified amount, via a manual scan of
   `getStorageContents()` (armor and off-hand are never touched).
5. Verify removal succeeded (compare actual-removed count to
   requested); if it doesn't match — which should be structurally
   impossible given the same-thread, no-yield design, but is checked
   anyway — whatever was removed is restored and the transaction aborts
   with nothing sold.
6. Deposit exact configured money through Vault.
7. Verify deposit success; if it fails, the removed items are given back
   to the player rather than being lost.
8. Record transaction.
9. Release transaction guard (`finally`).

## Why the item-identity system matters

Shop GUI buttons carry two `PersistentDataContainer` keys: a boolean marker
identifying "this is a shop GUI button" and (separately) the internal shop
item id. The transaction system never parses display names or lore to
figure out what a clicked item "is" — it reads the PDC-stored id. This
means:

- Editing `gui.yml` display names/lore/icons cannot change what an item
  actually buys or sells as, and a player cannot trick the plugin by naming
  a real item to visually resemble a shop button, since real inventory items
  never carry the plugin's PDC marker (Bukkit's persistent data containers
  are plugin-namespaced and cannot be forged by another plugin or by
  in-game commands available to normal players).

## What this design does *not* claim to prevent

- **Server-side plugin conflicts.** If another plugin on the server also
  freely grants items or money (e.g. a misconfigured `/give` permission
  available to non-ops, or another economy-adjacent plugin with its own
  bugs), that's outside this plugin's control. ShopPlugin only guarantees
  the integrity of transactions that go through its own GUI/commands.
- **Vault provider bugs.** If the underlying economy plugin itself has a
  duplication bug in its balance storage, ShopPlugin's careful
  `EconomyResponse` checking can't detect or fix that — it can only ensure
  ShopPlugin itself always checks the response rather than assuming
  success.
- **Server-authoritative but client-desynced display.** The GUI a player
  sees can, in principle, be stale for a moment (e.g. stock displayed before
  a very recent purchase by another player is reflected). This is a display
  issue only — actual stock/balance checks always re-validate against
  live server state at the moment of the click, never against what's
  rendered on screen.

## Recommended operational practices

- Keep `shopplugin.bypasslimits` restricted to trusted staff only — it skips
  daily purchase limits, which are one of the economy's abuse guards, not
  just a UX limit.
- Review `/shopadmin stats` and `/shopadmin audit <item>` periodically (see
  `docs/ECONOMY.md`'s tuning section) — the code prevents *known* exploit
  *categories*, but new vanilla mechanics (future MC versions, datapacks) can
  introduce conversions this plugin's curated conversion list doesn't know
  about yet. Add new entries to `DefaultConversions.java` if you introduce
  new items with crafting/smelting relationships to existing catalog items.
