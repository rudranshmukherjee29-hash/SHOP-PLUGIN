package net.shopplugin.service;

import net.shopplugin.model.TransactionResult;

import java.util.List;
import java.util.UUID;

public interface StatisticsService {

    /** Records a completed (successful) transaction. Safe to call from the main thread; it queues async work internally. */
    void recordTransaction(UUID playerId, String itemId, boolean isBuy, long quantity, java.math.BigDecimal amount);

    /** Records a failed transaction attempt for diagnostics (not shown to normal players). */
    void recordFailure(UUID playerId, String itemId, String reasonCode);

    ItemStats getItemStats(String itemId);

    List<ItemStats> getTopBought(int limit);

    List<ItemStats> getTopSold(int limit);

    ShopSummary getSummary();

    /** Flushes any buffered stats to the database. Called on shutdown to avoid losing recent data. */
    void flush();

    final class ItemStats {
        public final String itemId;
        public final long totalBought;
        public final long totalSold;
        public final java.math.BigDecimal moneySpent;
        public final java.math.BigDecimal moneyEarned;

        public ItemStats(String itemId, long totalBought, long totalSold,
                          java.math.BigDecimal moneySpent, java.math.BigDecimal moneyEarned) {
            this.itemId = itemId;
            this.totalBought = totalBought;
            this.totalSold = totalSold;
            this.moneySpent = moneySpent;
            this.moneyEarned = moneyEarned;
        }
    }

    final class ShopSummary {
        public final long totalTransactions;
        public final java.math.BigDecimal totalMoneySpent;
        public final java.math.BigDecimal totalMoneyEarned;

        public ShopSummary(long totalTransactions, java.math.BigDecimal totalMoneySpent, java.math.BigDecimal totalMoneyEarned) {
            this.totalTransactions = totalTransactions;
            this.totalMoneySpent = totalMoneySpent;
            this.totalMoneyEarned = totalMoneyEarned;
        }
    }
}
