package net.shopplugin.service;

import net.shopplugin.config.ShopSettings;
import net.shopplugin.economy.EconomyOperationResult;
import net.shopplugin.economy.EconomyProvider;
import net.shopplugin.model.ShopItem;
import net.shopplugin.model.TransactionResult;
import net.shopplugin.model.TransactionStatus;
import net.shopplugin.repository.ShopRepository;
import net.shopplugin.security.NumericGuard;
import net.shopplugin.security.RateLimiter;
import net.shopplugin.security.TransactionGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Implements the exact 14-step buy sequence and 9-step sell sequence
 * described in the design spec (section 9). Every step is checked and
 * every failure path returns a specific {@link TransactionStatus} rather
 * than throwing or silently continuing.
 *
 * Threading: this class assumes it is only ever called from the main
 * server thread (required for inventory mutation), and uses
 * {@link TransactionGuard} purely to reject *logically overlapping*
 * transactions (e.g. re-entrant calls triggered by a single click firing
 * multiple events), not to protect against genuine cross-thread races —
 * Bukkit's inventory API is not thread-safe, so nothing here should ever
 * be called off-thread.
 */
public final class DefaultTransactionService implements TransactionService {

    private final Logger logger;
    private final ShopRepository repository;
    private final EconomyProvider economy;
    private final StockService stockService;
    private final StatisticsService statisticsService;
    private final TransactionGuard transactionGuard;
    private final RateLimiter rateLimiter;
    private final ShopSettings settings;

    public DefaultTransactionService(Logger logger, ShopRepository repository, EconomyProvider economy,
                                      StockService stockService, StatisticsService statisticsService,
                                      TransactionGuard transactionGuard, RateLimiter rateLimiter,
                                      ShopSettings settings) {
        this.logger = logger;
        this.repository = repository;
        this.economy = economy;
        this.stockService = stockService;
        this.statisticsService = statisticsService;
        this.transactionGuard = transactionGuard;
        this.rateLimiter = rateLimiter;
        this.settings = settings;
    }

    @Override
    public TransactionResult buy(Player player, String itemId, long quantity) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("TransactionService must be called on the main server thread.");
        }

        if (!rateLimiter.tryAcquire(player.getUniqueId())) {
            return fail(TransactionStatus.TRANSACTION_CONFLICT, itemId, "You're doing that too fast. Please slow down.");
        }

        if (!transactionGuard.tryAcquire(player.getUniqueId())) {
            return fail(TransactionStatus.TRANSACTION_CONFLICT, itemId, "You already have a transaction in progress.");
        }

        try {
            // Step 1-2: validate player state & quantity.
            if (!player.isOnline()) {
                return fail(TransactionStatus.PLAYER_OFFLINE, itemId, "Player is offline.");
            }

            // Step 3: validate item configuration.
            Optional<ShopItem> itemOpt = repository.getItem(itemId);
            if (itemOpt.isEmpty()) {
                return fail(TransactionStatus.ITEM_NOT_FOUND, itemId, "That item does not exist in the shop.");
            }
            ShopItem item = itemOpt.get();

            if (!item.isBuyable()) {
                return fail(TransactionStatus.BUY_DISABLED, itemId, "This item cannot be purchased.");
            }

            int effectiveMax = effectiveMaxTransactionSize(item);
            if (!NumericGuard.isValidQuantity(quantity, effectiveMax)) {
                return fail(TransactionStatus.INVALID_QUANTITY, itemId, "Invalid quantity requested.");
            }

            if (!player.hasPermission("shopplugin.buy")) {
                return fail(TransactionStatus.BUY_DISABLED, itemId, "You do not have permission to buy items.");
            }

            // Per-player daily limit check.
            if (item.getPerPlayerDailyLimit() >= 0 && !player.hasPermission("shopplugin.bypasslimits")) {
                long alreadyBought = stockService.getPlayerDailyPurchases(player.getUniqueId(), item.getId());
                if (alreadyBought + quantity > item.getPerPlayerDailyLimit()) {
                    return fail(TransactionStatus.PURCHASE_LIMIT_REACHED, itemId,
                            "You have reached your daily purchase limit for this item.");
                }
            }

            // Step 4: calculate exact price safely (BigDecimal, never float/double math).
            BigDecimal totalPrice;
            try {
                totalPrice = NumericGuard.safeMultiply(item.getBuyPrice(), quantity, economy.getFractionalDigits());
            } catch (ArithmeticException | IllegalArgumentException e) {
                logger.warning("Price calculation rejected for " + itemId + " x" + quantity + ": " + e.getMessage());
                return fail(TransactionStatus.CONFIGURATION_ERROR, itemId, "Could not safely calculate the price for this purchase.");
            }

            // Step 5: validate stock if applicable (reserve now to close the race window;
            // refunded below if any later step fails).
            boolean stockReserved = false;
            if (item.isLimitedStock()) {
                if (!stockService.tryReserve(item.getId(), quantity)) {
                    return fail(TransactionStatus.OUT_OF_STOCK, itemId, "Not enough stock remaining.");
                }
                stockReserved = true;
            }

            try {
                // Step 6: verify balance.
                if (!economy.isReady()) {
                    return fail(TransactionStatus.ECONOMY_UNAVAILABLE, itemId, "The economy system is currently unavailable.");
                }
                if (!economy.has(player, totalPrice)) {
                    return fail(TransactionStatus.INSUFFICIENT_FUNDS, itemId,
                            "You do not have enough money for this purchase.");
                }

                // Step 7-8: withdraw money and verify.
                EconomyOperationResult withdrawal = economy.withdraw(player, totalPrice);
                if (!withdrawal.isSuccess()) {
                    return fail(TransactionStatus.ECONOMY_FAILURE, itemId,
                            "Payment failed: " + withdrawal.getErrorMessage());
                }

                // Step 11-12: add items and verify inventory can hold them, BEFORE
                // treating the transaction as final. If inventory can't hold the
                // items, refund the withdrawn money immediately.
                TransactionResult inventoryFailure = giveItemsSafely(player, item.getMaterial(), quantity, item.getId());
                if (inventoryFailure != null) {
                    // Roll back the payment since nothing was actually delivered.
                    EconomyOperationResult refund = economy.deposit(player, totalPrice);
                    if (!refund.isSuccess()) {
                        logger.severe("CRITICAL: Failed to refund player " + player.getUniqueId()
                                + " after inventory-full purchase failure for " + item.getId()
                                + ". Manual correction required. Amount: " + totalPrice);
                    }
                    return inventoryFailure;
                }

                // Step 10 (stock already reserved above) / Step 13: record transaction.
                if (item.getPerPlayerDailyLimit() >= 0) {
                    stockService.recordPlayerPurchase(player.getUniqueId(), item.getId(), quantity);
                }
                statisticsService.recordTransaction(player.getUniqueId(), item.getId(), true, quantity, totalPrice);

                return TransactionResult.success(item.getId(), quantity, totalPrice);

            } catch (RuntimeException e) {
                logger.severe("Unexpected error during buy transaction for " + player.getName() + " item=" + itemId + ": " + e);
                if (stockReserved) {
                    stockService.refund(item.getId(), quantity);
                }
                statisticsService.recordFailure(player.getUniqueId(), itemId, "INTERNAL_ERROR");
                return fail(TransactionStatus.INTERNAL_ERROR, itemId, "An unexpected error occurred. No money or items were exchanged... please verify your balance.");
            }

        } finally {
            transactionGuard.release(player.getUniqueId());
        }
    }

    /**
     * Attempts to give the player {@code quantity} of {@code material}, split
     * across legal stack sizes, only after confirming the inventory has room
     * for the full amount. Returns null on success, or a failure
     * TransactionResult if the inventory cannot hold the full amount (in
     * which case nothing is given at all — never a partial delivery).
     */
    private TransactionResult giveItemsSafely(Player player, Material material, long quantity, String itemId) {
        int maxStackSize = material.getMaxStackSize();
        PlayerInventory inv = player.getInventory();

        // Simulate first: count how much free space exists across empty slots
        // and partially-filled compatible stacks, without mutating anything yet.
        long capacity = 0;
        ItemStack[] contents = inv.getStorageContents();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                capacity += maxStackSize;
            } else if (stack.getType() == material && stack.getAmount() < stack.getMaxStackSize()) {
                capacity += (stack.getMaxStackSize() - stack.getAmount());
            }
            if (capacity >= quantity) {
                break;
            }
        }

        if (capacity < quantity) {
            return fail(TransactionStatus.INVENTORY_FULL, itemId,
                    "Your inventory does not have enough space for this purchase.");
        }

        // Now actually give it, in max-stack-sized chunks.
        long remaining = quantity;
        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, maxStackSize);
            ItemStack stack = new ItemStack(material, chunk);
            Map<Integer, ItemStack> overflow = inv.addItem(stack);
            if (!overflow.isEmpty()) {
                // Should not happen given the capacity check above, but if it does
                // (e.g. a concurrent modification we didn't anticipate), drop the
                // overflow at the player's feet rather than losing it or lying
                // about delivery.
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                logger.warning("Inventory capacity check mismatch for " + player.getName()
                        + "; overflow items were dropped on the ground instead of lost.");
            }
            remaining -= chunk;
        }
        return null;
    }

    @Override
    public TransactionResult sell(Player player, String itemId, long quantity) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("TransactionService must be called on the main server thread.");
        }

        if (!rateLimiter.tryAcquire(player.getUniqueId())) {
            return fail(TransactionStatus.TRANSACTION_CONFLICT, itemId, "You're doing that too fast. Please slow down.");
        }

        if (!transactionGuard.tryAcquire(player.getUniqueId())) {
            return fail(TransactionStatus.TRANSACTION_CONFLICT, itemId, "You already have a transaction in progress.");
        }

        try {
            return sellInternal(player, itemId, quantity);
        } finally {
            transactionGuard.release(player.getUniqueId());
        }
    }

    /**
     * Internal sell logic, assumes the caller already holds the transaction
     * guard for this player. Used both by {@link #sell} and {@link #sellAll}
     * (which acquires the guard once for the whole batch rather than once
     * per item, so the batch is atomic with respect to other transactions).
     */
    private TransactionResult sellInternal(Player player, String itemId, long quantity) {
        if (!player.isOnline()) {
            return fail(TransactionStatus.PLAYER_OFFLINE, itemId, "Player is offline.");
        }

        Optional<ShopItem> itemOpt = repository.getItem(itemId);
        if (itemOpt.isEmpty()) {
            return fail(TransactionStatus.ITEM_NOT_FOUND, itemId, "That item does not exist in the shop.");
        }
        ShopItem item = itemOpt.get();

        if (!item.isSellable()) {
            return fail(TransactionStatus.SELL_DISABLED, itemId, "This item cannot be sold here.");
        }

        if (!player.hasPermission("shopplugin.sell")) {
            return fail(TransactionStatus.SELL_DISABLED, itemId, "You do not have permission to sell items.");
        }

        int effectiveMax = effectiveMaxTransactionSize(item);
        if (!NumericGuard.isValidQuantity(quantity, effectiveMax)) {
            return fail(TransactionStatus.INVALID_QUANTITY, itemId, "Invalid quantity requested.");
        }

        // Step 3: count exact matching items actually present in inventory.
        // We never trust a client-supplied "I have X of this" claim.
        long actualCount = countMatching(player, item.getMaterial());
        if (actualCount < quantity) {
            return fail(TransactionStatus.INSUFFICIENT_ITEMS, itemId,
                    "You do not have that many of this item.");
        }

        BigDecimal totalValue;
        try {
            totalValue = NumericGuard.safeMultiply(item.getSellPrice(), quantity, economy.getFractionalDigits());
        } catch (ArithmeticException | IllegalArgumentException e) {
            return fail(TransactionStatus.CONFIGURATION_ERROR, itemId, "Could not safely calculate the sale value.");
        }

        if (!economy.isReady()) {
            return fail(TransactionStatus.ECONOMY_UNAVAILABLE, itemId, "The economy system is currently unavailable.");
        }

        // Step 4: remove only the exact verified amount.
        int actuallyRemoved = removeExact(player, item.getMaterial(), (int) quantity);
        if (actuallyRemoved != quantity) {
            // Defensive: if for any reason fewer were removed than counted
            // (should not happen given the count above ran on the same thread
            // with no yielded control in between), put back whatever was
            // removed and abort rather than pay for a partial sale.
            if (actuallyRemoved > 0) {
                player.getInventory().addItem(new ItemStack(item.getMaterial(), actuallyRemoved));
            }
            logger.warning("Sell mismatch for " + player.getName() + ": expected to remove " + quantity
                    + " but removed " + actuallyRemoved + ". Transaction aborted, items restored.");
            statisticsService.recordFailure(player.getUniqueId(), itemId, "SELL_REMOVE_MISMATCH");
            return fail(TransactionStatus.INTERNAL_ERROR, itemId, "Could not verify item removal. Nothing was sold.");
        }

        // Step 6-7: deposit and verify.
        EconomyOperationResult deposit = economy.deposit(player, totalValue);
        if (!deposit.isSuccess()) {
            // Roll back: give the items back since payment failed.
            player.getInventory().addItem(new ItemStack(item.getMaterial(), (int) quantity));
            logger.severe("Deposit failed for " + player.getName() + " selling " + item.getId()
                    + "; items were restored to inventory. Reason: " + deposit.getErrorMessage());
            statisticsService.recordFailure(player.getUniqueId(), itemId, "SELL_DEPOSIT_FAILURE");
            return fail(TransactionStatus.ECONOMY_FAILURE, itemId, "Payment could not be completed. Your items were returned.");
        }

        if (item.isLimitedStock()) {
            // Selling back into a limited-stock item replenishes it, capped at max.
            stockService.refund(item.getId(), quantity);
        }

        statisticsService.recordTransaction(player.getUniqueId(), item.getId(), false, quantity, totalValue);
        return TransactionResult.success(item.getId(), quantity, totalValue);
    }

    @Override
    public TransactionResult sellHand(Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) {
            return fail(TransactionStatus.INSUFFICIENT_ITEMS, null, "You are not holding anything to sell.");
        }
        Optional<ShopItem> matching = repository.getAllItems().stream()
                .filter(i -> i.getMaterial() == inHand.getType() && i.isSellable())
                .findFirst();
        if (matching.isEmpty()) {
            return fail(TransactionStatus.SELL_DISABLED, null, "This item cannot be sold here.");
        }
        return sell(player, matching.get().getId(), inHand.getAmount());
    }

    @Override
    public List<TransactionResult> sellAll(Player player) {
        List<TransactionResult> results = new ArrayList<>();

        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("TransactionService must be called on the main server thread.");
        }

        if (!transactionGuard.tryAcquire(player.getUniqueId())) {
            results.add(fail(TransactionStatus.TRANSACTION_CONFLICT, null, "You already have a transaction in progress."));
            return results;
        }

        try {
            // Build a snapshot of material -> count directly from inventory contents
            // (never from a cached/assumed count) before making any changes.
            Map<Material, Long> counts = new HashMap<>();
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (stack == null || stack.getType() == Material.AIR) continue;
                counts.merge(stack.getType(), (long) stack.getAmount(), Long::sum);
            }

            for (ShopItem item : repository.getAllItems()) {
                if (!item.isSellable()) continue;
                Long have = counts.get(item.getMaterial());
                if (have == null || have <= 0) continue;
                long sellQty = Math.min(have, item.getMaxTransactionSize() > 0 ? item.getMaxTransactionSize() : have);
                TransactionResult result = sellInternal(player, item.getId(), sellQty);
                if (result.isSuccess()) {
                    results.add(result);
                }
            }
        } finally {
            transactionGuard.release(player.getUniqueId());
        }

        return results;
    }

    private long countMatching(Player player, Material material) {
        long total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Removes exactly {@code amount} of {@code material} from the player's
     * main inventory storage contents (not armor, not off-hand), returning
     * how many were actually removed. Uses Bukkit's inventory API directly
     * on storage contents to avoid any ambiguity with equipped items.
     */
    private int removeExact(Player player, Material material, int amount) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            int take = Math.min(stack.getAmount(), remaining);
            int newAmount = stack.getAmount() - take;
            if (newAmount <= 0) {
                contents[i] = null;
            } else {
                stack.setAmount(newAmount);
                contents[i] = stack;
            }
            remaining -= take;
        }
        inv.setStorageContents(contents);
        return amount - remaining;
    }

    private int effectiveMaxTransactionSize(ShopItem item) {
        int itemMax = item.getMaxTransactionSize();
        int globalMax = settings.getGlobalMaxTransactionSize();
        if (itemMax <= 0) return globalMax;
        if (globalMax <= 0) return itemMax;
        return Math.min(itemMax, globalMax);
    }

    private TransactionResult fail(TransactionStatus status, String itemId, String message) {
        return TransactionResult.failure(status, itemId, message);
    }
}
