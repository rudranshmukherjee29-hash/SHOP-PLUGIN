package net.shopplugin.service;

import net.shopplugin.model.ShopItem;
import net.shopplugin.repository.ShopRepository;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Periodically restocks limited-stock items according to their configured
 * restock-amount / restock-interval-seconds. Runs on the main thread since
 * it only touches the in-memory StockService (no I/O), keeping this cheap
 * and simple rather than needing async coordination.
 */
public final class RestockTask extends BukkitRunnable {

    private final ShopRepository repository;
    private final StockService stockService;
    private final Logger logger;
    private final Map<String, Long> lastRestockMillis = new HashMap<>();
    private final boolean stockSystemEnabled;

    public RestockTask(ShopRepository repository, StockService stockService, Logger logger, boolean stockSystemEnabled) {
        this.repository = repository;
        this.stockService = stockService;
        this.logger = logger;
        this.stockSystemEnabled = stockSystemEnabled;
    }

    @Override
    public void run() {
        if (!stockSystemEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ShopItem item : repository.getAllItems()) {
            if (!item.isLimitedStock() || item.getRestockAmount() <= 0 || item.getRestockIntervalSeconds() <= 0) {
                continue;
            }
            long last = lastRestockMillis.getOrDefault(item.getId(), 0L);
            long intervalMillis = item.getRestockIntervalSeconds() * 1000L;
            if (now - last >= intervalMillis) {
                stockService.refund(item.getId(), item.getRestockAmount());
                lastRestockMillis.put(item.getId(), now);
                if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    logger.fine("Restocked " + item.getId() + " by " + item.getRestockAmount());
                }
            }
        }
    }
}
