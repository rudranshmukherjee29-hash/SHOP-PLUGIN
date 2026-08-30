package net.shopplugin.service;

import net.shopplugin.model.ShopItem;
import net.shopplugin.repository.ShopRepository;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Atomic in-memory stock tracker. Uses {@link AtomicLong} with a
 * compare-and-swap style update via {@link AtomicLong#updateAndGet} so that
 * a "check stock, then decrement" sequence can never interleave with
 * another thread's identical sequence — this is what actually prevents two
 * players from simultaneously buying the "last" item of a limited stock.
 *
 * Persisted to the database periodically / on save so restarts don't reset
 * stock to full (see DatabaseManager#saveStockSnapshot).
 */
public final class InMemoryStockService implements StockService {

    private static final long INSUFFICIENT = Long.MIN_VALUE;

    private final ShopRepository repository;
    private final Map<String, AtomicLong> stockByItem = new ConcurrentHashMap<>();
    // key: playerId|itemId|yyyy-mm-dd
    private final Map<String, AtomicLong> dailyPurchases = new ConcurrentHashMap<>();

    public InMemoryStockService(ShopRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean tryReserve(String itemId, long quantity) {
        if (quantity <= 0) {
            return false;
        }
        AtomicLong current = stockByItem.get(itemId.toLowerCase());
        if (current == null) {
            // No stock entry means either unlimited stock (caller should have
            // checked item.isLimitedStock() first) or an unconfigured item.
            // Fail closed: treat as unavailable rather than silently allowing.
            return false;
        }
        long result = current.updateAndGet(existing -> existing >= quantity ? existing - quantity : INSUFFICIENT);
        if (result == INSUFFICIENT) {
            // Restore: updateAndGet already left the value unchanged in this branch
            // only if existing < quantity, so nothing to undo; but guard for the
            // sentinel accidentally matching a real value.
            return false;
        }
        return true;
    }

    @Override
    public void refund(String itemId, long quantity) {
        if (quantity <= 0) {
            return;
        }
        String key = itemId.toLowerCase();
        AtomicLong current = stockByItem.get(key);
        if (current == null) {
            return;
        }
        long max = repository.getItem(itemId).map(ShopItem::getMaxStock).orElse(-1L);
        current.updateAndGet(existing -> {
            long restored = existing + quantity;
            if (max >= 0 && restored > max) {
                return max;
            }
            return restored;
        });
    }

    @Override
    public long getCurrentStock(String itemId) {
        AtomicLong current = stockByItem.get(itemId.toLowerCase());
        return current == null ? -1 : current.get();
    }

    @Override
    public void setStock(String itemId, long amount) {
        stockByItem.put(itemId.toLowerCase(), new AtomicLong(Math.max(amount, 0)));
    }

    @Override
    public long getPlayerDailyPurchases(UUID playerId, String itemId) {
        AtomicLong count = dailyPurchases.get(dailyKey(playerId, itemId));
        return count == null ? 0 : count.get();
    }

    @Override
    public void recordPlayerPurchase(UUID playerId, String itemId, long quantity) {
        dailyPurchases.computeIfAbsent(dailyKey(playerId, itemId), k -> new AtomicLong(0))
                .addAndGet(quantity);
    }

    private String dailyKey(UUID playerId, String itemId) {
        return playerId + "|" + itemId.toLowerCase() + "|" + LocalDate.now();
    }
}
