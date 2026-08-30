# Economy Balancing

## Compatibility with other economy tools (EconomyGUI, etc.)

ShopPlugin never talks to a specific economy plugin directly — it only talks
to Vault's `Economy` service (see `VaultEconomyProvider`). Any other plugin
that is *also* Vault-based and points at the same underlying economy
(typically EssentialsX) will automatically share the same player balances
with no configuration needed. This includes admin-balance tools like
**EconomyGUI**, which itself is Vault-integrated and requires
Vault + EssentialsX to function. As long as ShopPlugin, EconomyGUI, and
EssentialsX are all installed together, a balance change made through one
is immediately visible through the others, because they're all reading and
writing the same Vault-registered economy — there's no separate
integration step required.


This document explains the reasoning behind the default prices shipped in
`prices.yml`, so a server admin can adjust them intelligently instead of
guessing.

## The core rule

For every item, two directions of arbitrage were checked and closed:

1. **Buy raw materials → convert → sell result.** For every crafting,
   smelting, or compression relationship where both sides exist in the shop,
   the sell price of the output is kept below the buy cost of the inputs
   (with margin — see `PriceValidationService`'s `MIN_LOOP_MARGIN`), so
   converting shop-bought materials and selling the result is never
   profitable.
2. **Buy the result → break it down → sell the parts.** For compression
   pairs (ingot ↔ block), the reverse is also closed: buying a block and
   decompressing it into ingots to sell is never better than selling the
   block whole, and vice versa.

Run `/shopadmin validateprices` any time you edit `prices.yml` — it checks
both directions automatically for the conversions listed in
`DefaultConversions.java` and prints anything that looks like a loop.

## Why some items have no buy price

A number of items are **sell-only** by design:

- **`raw_iron`, `raw_copper`, `raw_gold`** — these have no buy price. If they
  did, a player could buy raw ore, smelt it (a free, near-instant action),
  and sell the ingot for a profit that has nothing to do with actually
  mining. Selling remains enabled as a reward for the mining itself.
- **`netherite_scrap`, `ancient_debris`, `shulker_shell`, `dragon_breath`** —
  these gate significant, deliberately-difficult vanilla progression
  (netherite gear, shulker boxes, lingering potions of harming). Making them
  purchasable would let money substitute for progression that's supposed to
  require exploration/risk. They remain sellable so players are still
  rewarded for going and getting them.
- **`golden_carrot`** — craftable cheaply from (cheap) carrots + gold
  nuggets; selling is disabled so "buy gold, craft golden carrots, sell
  them" isn't a laundering path for shop gold.

## Why some items aren't in the shop at all

`nether_star`, `elytra`, `totem_of_undying`, `dragon_egg`, and enchanted
golden apples are intentionally excluded entirely (not even sell-only).
These are singular, boss-or-structure-gated rewards. Making them sellable
for a flat price turns a unique achievement into a commodity, and making
them buyable would trivialize the corresponding boss fight or structure
entirely. If you want to include any of these on your server, do so
deliberately and think hard about whether to allow buying at all — see the
comment block at the bottom of `prices.yml`.

## Category-by-category notes

**Blocks** — priced very low (most under $1 sell). These are meant to be a
convenience/backfill, not an income source. Logs are priced above planks by
more than the 1:4 crafting ratio would imply is profitable (a log sells for
3, four planks would only fetch $2 total), so log→plank conversion for
resale is a loss, not a gain.

**Ores & Minerals** — the main "hard work pays off" category. Iron, copper,
and gold follow the raw-ore-sell-only pattern above. Redstone and lapis are
kept cheap because they're highly renewable (villager trading, allays,
Nether farming). Diamonds and emeralds anchor the top of the "normal"
tier — expensive enough to matter, not so expensive players give up.
Netherite materials are sell-only per above.

Gold deserves a special note: gold is AFK-farmable at scale via
zombified-piglin or piglin-bartering farms in the Nether, which is a
well-known vanilla mechanic. Its sell price ($21/ingot) is set lower
relative to its "rarity" than you might otherwise expect, specifically to
account for this — a naive rarity-only pricing model would overpay gold
farmers.

**Farming** — deliberately low per-unit prices ($0.3–$2). Crops are
infinitely renewable and farmable in bulk, so the design intent is "steady
trickle income from a real farm," not "get rich from one wheat farm." A
player running a large, actively-tended crop farm should make solid money
over time; a small starter farm should not.

**Mob drops** — priced by danger and farmability. Rotten flesh is nearly
worthless (matches vanilla — zombie XP farms shouldn't be a cash printer).
Ender pearls and blaze rods are priced higher and reflect that
enderman/blaze farms exist but require real end-game investment and carry
real risk to set up.

**Food** — low sell prices throughout, specifically to avoid "mob farm for
food, sell the food" being a meaningful income path; food's job in this
shop is convenience (buying food when you don't feel like cooking), not
income.

**Redstone & Utility** — hoppers get special treatment: buy price is high
($60) and there's a per-player daily purchase limit (64/day by default) on
top of the normal max-transaction-size, specifically because hoppers are
the single item most associated with automated farms and item-sorting
systems that could otherwise be bootstrapped by simply buying a stack of 64
hoppers from the shop on day one. This doesn't stop players from crafting
hoppers normally — it only rate-limits *buying* them from the shop.

**Nether & End** — priced to reflect that reaching the Nether/End at all is
a progression gate; quartz/glowstone/magma cream are moderate, chorus fruit
and end stone are cheap utility items, and the truly rare items
(shulker shells, dragon breath) are sell-only as described above.

## Tuning for your server

The shipped prices assume:

- A single-server (not cross-server) economy.
- No other major money faucets (quest plugins, daily rewards, etc.) already
  in play. If you have those, you likely want to lower buy prices and/or
  raise sell prices are **not** the fix — instead consider lowering the
  *other* faucets, or scale this shop's prices down proportionally to match
  your server's existing money supply.
- Vault's default 2 fractional digits. If your economy provider uses a
  different precision, `EconomyProvider.getFractionalDigits()` is already
  read dynamically by the transaction service, so no code changes are
  needed — just be aware very small prices (e.g. $0.2 rotten flesh) will
  round differently on a 0-fractional-digit economy.

Always run `/shopadmin validateprices` and skim `/shopadmin stats` weekly
for the first month after launch — the "Top Bought"/"Top Sold" lists and the
per-item audit command are there specifically to catch a price that turns
out to be exploitable in practice, even if it passed the automated loop
checker (e.g. a mechanic the checker doesn't model, like a new villager
trade added by a datapack).
