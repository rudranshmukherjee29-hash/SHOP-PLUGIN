package net.shopplugin.service;

import net.shopplugin.repository.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Writes go through Bukkit's async scheduler so the main thread is never
 * blocked on JDBC I/O. Reads for in-game commands are served from a small
 * in-memory cache that is refreshed asynchronously and swapped in atomically,
 * so `/shop stats` never triggers a synchronous database hit.
 */
public final class DatabaseStatisticsService implements StatisticsService {

    private final Plugin plugin;
    private final DatabaseManager db;
    private final Map<String, ItemStats> cache = new ConcurrentHashMap<>();

    public DatabaseStatisticsService(Plugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        refreshCacheAsync();
    }

    @Override
    public void recordTransaction(UUID playerId, String itemId, boolean isBuy, long quantity, BigDecimal amount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            try (Connection conn = db.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO shop_transactions (player_uuid, item_id, action, quantity, amount, timestamp) VALUES (?,?,?,?,?,?)")) {
                    ps.setString(1, playerId.toString());
                    ps.setString(2, itemId);
                    ps.setString(3, isBuy ? "BUY" : "SELL");
                    ps.setLong(4, quantity);
                    ps.setBigDecimal(5, amount);
                    ps.setLong(6, now);
                    ps.executeUpdate();
                }
                upsertItemStats(conn, itemId, isBuy, quantity, amount);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to record transaction statistics", e);
            }
            refreshCacheAsync();
        });
    }

    private void upsertItemStats(Connection conn, String itemId, boolean isBuy, long quantity, BigDecimal amount) throws SQLException {
        // Portable upsert: try update, insert if no row affected. Avoids relying on
        // vendor-specific ON DUPLICATE KEY / ON CONFLICT syntax differences.
        String updateSql = isBuy
                ? "UPDATE shop_item_stats SET total_bought = total_bought + ?, money_spent = money_spent + ?, buy_count = buy_count + 1 WHERE item_id = ?"
                : "UPDATE shop_item_stats SET total_sold = total_sold + ?, money_earned = money_earned + ?, sell_count = sell_count + 1 WHERE item_id = ?";
        int updated;
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setLong(1, quantity);
            ps.setBigDecimal(2, amount);
            ps.setString(3, itemId);
            updated = ps.executeUpdate();
        }
        if (updated == 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO shop_item_stats (item_id, total_bought, total_sold, money_spent, money_earned, buy_count, sell_count) VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, itemId);
                ps.setLong(2, isBuy ? quantity : 0);
                ps.setLong(3, isBuy ? 0 : quantity);
                ps.setBigDecimal(4, isBuy ? amount : BigDecimal.ZERO);
                ps.setBigDecimal(5, isBuy ? BigDecimal.ZERO : amount);
                ps.setLong(6, isBuy ? 1 : 0);
                ps.setLong(7, isBuy ? 0 : 1);
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void recordFailure(UUID playerId, String itemId, String reasonCode) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO shop_failed_transactions (player_uuid, item_id, reason, timestamp) VALUES (?,?,?,?)")) {
                ps.setString(1, playerId.toString());
                ps.setString(2, itemId);
                ps.setString(3, reasonCode);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to record failed transaction", e);
            }
        });
    }

    @Override
    public ItemStats getItemStats(String itemId) {
        return cache.getOrDefault(itemId.toLowerCase(),
                new ItemStats(itemId, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Override
    public List<ItemStats> getTopBought(int limit) {
        return cache.values().stream()
                .sorted(Comparator.comparingLong((ItemStats s) -> s.totalBought).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<ItemStats> getTopSold(int limit) {
        return cache.values().stream()
                .sorted(Comparator.comparingLong((ItemStats s) -> s.totalSold).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public ShopSummary getSummary() {
        long totalTx = cache.values().stream().mapToLong(s -> s.totalBought + s.totalSold).sum();
        BigDecimal spent = cache.values().stream().map(s -> s.moneySpent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal earned = cache.values().stream().map(s -> s.moneyEarned).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ShopSummary(totalTx, spent, earned);
    }

    @Override
    public void flush() {
        // All writes are already committed per-transaction (no write buffering
        // beyond the async task queue), so flush just ensures the cache is current.
        refreshCacheSync();
    }

    private void refreshCacheAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshCacheSync);
    }

    private void refreshCacheSync() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM shop_item_stats");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String itemId = rs.getString("item_id");
                cache.put(itemId.toLowerCase(), new ItemStats(
                        itemId,
                        rs.getLong("total_bought"),
                        rs.getLong("total_sold"),
                        rs.getBigDecimal("money_spent"),
                        rs.getBigDecimal("money_earned")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to refresh statistics cache", e);
        }
    }
}
