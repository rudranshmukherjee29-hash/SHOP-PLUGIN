package net.shopplugin.service;

/**
 * Manages limited-stock items atomically. Only items explicitly configured
 * with limited stock go through here; unlimited-stock items (the default
 * for almost everything) never touch this service, which keeps the hot
 * path cheap.
 */
public interface StockService {

    /**
     * Attempts to atomically reserve (decrement) stock for a purchase.
     * Returns true only if the full requested quantity was available and
     * has now been deducted. Never partially deducts: it's all-or-nothing.
     */
    boolean tryReserve(String itemId, long quantity);

    /**
     * Returns stock to the pool, e.g. on a downstream failure after
     * reservation succeeded (rollback), capped at the item's max stock.
     */
    void refund(String itemId, long quantity);

    long getCurrentStock(String itemId);

    void setStock(String itemId, long amount);

    /** Per-player daily purchase count, used for perPlayerDailyLimit enforcement. */
    long getPlayerDailyPurchases(java.util.UUID playerId, String itemId);

    void recordPlayerPurchase(java.util.UUID playerId, String itemId, long quantity);
}
