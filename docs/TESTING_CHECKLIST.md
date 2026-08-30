# Testing Checklist

Run through this on a staging server (never on a live economy first) before
trusting ShopPlugin with real player balances. Check off each item as you
confirm it.

## Setup sanity

- [ ] Server starts with Vault + EssentialsX (or your economy plugin)
      installed; console shows `Using economy provider: <name>`.
- [ ] Server starts with Vault present but **no** economy plugin registered;
      console shows the "no working Vault economy provider" warning and the
      plugin stays loaded (does not crash the server).
- [ ] Server starts with **no** Vault at all; same graceful-degradation
      behavior.
- [ ] `plugins/ShopPlugin/*.yml` files are all generated correctly on first
      run.
- [ ] `/shopadmin validateprices` reports no issues against the shipped
      `prices.yml`.

## Normal transaction tests

- [ ] Buy 1 of an item via left-click; balance decreases by exactly the
      listed price; inventory gains exactly 1.
- [ ] Buy a full stack via shift-click; balance and inventory match exactly.
- [ ] Sell 1 of an item via right-click; balance increases by exactly the
      listed sell price; inventory loses exactly 1.
- [ ] Sell all of an item via shift-right-click; every matching item in
      inventory is sold, balance reflects the correct total.
- [ ] `/sellall` with a mixed inventory: only sellable items are sold;
      non-sellable items (tools, non-catalog items) are untouched.
- [ ] `/sellhand` sells exactly the held stack and no more.
- [ ] Attempt to buy with insufficient balance: transaction is rejected,
      balance and inventory are both unchanged.
- [ ] Attempt to buy with a full inventory: transaction is rejected, balance
      is unchanged (verify no money was silently withdrawn).
- [ ] Attempt to sell with an empty/non-matching inventory: rejected
      cleanly, no error item is created.
- [ ] Toggle an item's `buy-price`/`sell-price` off in `prices.yml`, reload,
      confirm the GUI reflects "not available for purchase" / "cannot be
      sold here" and the corresponding click does nothing harmful.

## Exploit tests

- [ ] Macro/auto-clicker style rapid clicking on a single item: confirm only
      the rate-limited number of transactions actually process, and that
      total money/items exchanged exactly matches the number of successful
      transactions (no extra items or money appear).
- [ ] Rapid shift-clicking a buy button: same check as above.
- [ ] Double-click on a GUI item (attempting the "collect similar items"
      client behavior): confirm no item is extracted from the GUI into the
      player's inventory or cursor.
- [ ] Attempt to drag an item out of the shop GUI into the player inventory
      (click-drag across both inventories): confirm the drag is fully
      cancelled and no item moves.
- [ ] Attempt to use number keys (1–9) to swap a hotbar item into a GUI
      slot: confirm cancelled.
- [ ] Attempt to swap a GUI item to the off-hand via the off-hand swap key
      while the shop is open: confirm cancelled.
- [ ] Open the shop GUI in creative mode (on a test account with creative
      access) and attempt every click type above: confirm identical
      protection.
- [ ] Disconnect (or force-close the connection) while a transaction would
      be mid-flight if it were asynchronous: confirm on reconnect that
      balance/inventory reflect either a fully completed or fully
      not-attempted transaction, never a partial one. (Given the
      synchronous design this should be structurally guaranteed — this test
      is a regression check.)
- [ ] Two players attempt to buy the last unit of a limited-stock item at
      effectively the same time (script both clients to fire as close to
      simultaneously as possible): confirm exactly one succeeds and the
      other receives "out of stock," and that final stock is exactly 0, not
      negative.
- [ ] Attempt to request a negative quantity (if you have a debug/test hook
      into `TransactionService.buy`/`sell` directly): confirm
      `INVALID_QUANTITY` is returned and nothing happens.
- [ ] Attempt an extremely large quantity (e.g. `Long.MAX_VALUE` via a debug
      hook): confirm `INVALID_QUANTITY` (rejected by `NumericGuard`) rather
      than an overflowed price or a hang.
- [ ] Simulate an economy provider failure (e.g. temporarily unregister the
      Vault service, or use a test economy plugin that returns failure
      responses): confirm `ECONOMY_FAILURE`/`ECONOMY_UNAVAILABLE` is
      returned and, for sells, that removed items are restored to the
      player rather than lost.
- [ ] Simulate a database failure (e.g. point `database.mysql.host` at a
      nonexistent host with `database.type: mysql`): confirm the plugin
      logs the failure but transactions still complete (statistics are
      best-effort, not transaction-blocking) — verify this matches your
      intended behavior, since the current design treats statistics writes
      as non-blocking.
- [ ] Induce artificial server lag (e.g. a busy-loop plugin or `/tick
      freeze`-style tool if available) while performing transactions:
      confirm no double-spend or double-grant occurs once the server
      catches up.
- [ ] Stock race test at scale: script 10+ near-simultaneous buy attempts
      against an item with `max-stock: 5`; confirm exactly 5 succeed and the
      rest fail with `OUT_OF_STOCK`, with final stock at 0.

## Economy / profit-loop tests

For each pair below, buy the input(s) from the shop, perform the vanilla
conversion, and sell the output — confirm the result is break-even or a
loss, never a profit (and check the reverse direction for compression pairs):

- [ ] Buy `raw_iron`... wait, `raw_iron` has no buy price by design; confirm
      the GUI correctly shows it as sell-only and buying is blocked.
- [ ] Buy `iron_ingot` ×9, craft into `iron_block`, sell the block: confirm
      total sell value is below the ×9 purchase cost.
- [ ] Buy an `iron_block`, break it into 9 `iron_ingot`, sell all 9: confirm
      total sell value is below the block's purchase cost.
- [ ] Repeat both directions for `copper_ingot`/`copper_block`,
      `gold_ingot`/`gold_block`, and `redstone`/`redstone_block`.
- [ ] Buy `wheat` ×3, craft `bread`, sell the bread: confirm a loss/break-even.
- [ ] Buy `potato`, cook into `baked_potato` (`cooked_potato` in the
      catalog), sell it: confirm the smelting margin is modest, not a
      large profit relative to fuel/time cost (see `docs/ECONOMY.md`).
- [ ] Farm a renewable item at real, sustained scale for ~30–60 minutes
      (e.g. an automated wheat farm) and log the resulting income rate;
      confirm it's a reasonable "steady trickle," not enough to reach a
      large balance in that window (compare against your server's other
      income sources).
- [ ] Attempt the "buy hoppers in bulk to bootstrap a farm" scenario:
      confirm the per-player daily limit on hoppers actually triggers once
      exceeded.
- [ ] After running the above, run `/shopadmin stats` and manually inspect
      "Top Bought"/"Top Sold" — nothing should stand out as wildly
      disproportionate relative to the others once normal testing traffic
      is accounted for.
- [ ] Re-run `/shopadmin validateprices` after any pricing changes made
      during testing, before moving those changes to production.

## Sign-off

Only after every box above is checked (or consciously deferred with a
documented reason) should this plugin be pointed at a production server
with real player balances.
