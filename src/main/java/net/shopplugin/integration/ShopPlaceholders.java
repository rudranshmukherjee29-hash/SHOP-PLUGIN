package net.shopplugin.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.shopplugin.economy.EconomyProvider;
import net.shopplugin.service.StatisticsService;
import org.bukkit.OfflinePlayer;

/**
 * Optional PlaceholderAPI expansion. Only registered if PlaceholderAPI is
 * present on the server (see ShopPlugin#setupPlaceholderApi); this class is
 * never loaded/referenced unless that check passes, so servers without
 * PlaceholderAPI are unaffected even though this class technically imports it.
 */
public final class ShopPlaceholders extends PlaceholderExpansion {

    private final EconomyProvider economy;
    private final StatisticsService statisticsService;

    public ShopPlaceholders(EconomyProvider economy, StatisticsService statisticsService) {
        this.economy = economy;
        this.statisticsService = statisticsService;
    }

    @Override
    public String getIdentifier() {
        return "shopplugin";
    }

    @Override
    public String getAuthor() {
        return "ShopPlugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params.equalsIgnoreCase("balance") && player != null) {
            return economy.getBalance(player).toPlainString();
        }
        if (params.equalsIgnoreCase("total_transactions")) {
            return String.valueOf(statisticsService.getSummary().totalTransactions);
        }
        if (params.equalsIgnoreCase("total_spent")) {
            return statisticsService.getSummary().totalMoneySpent.toPlainString();
        }
        if (params.equalsIgnoreCase("total_earned")) {
            return statisticsService.getSummary().totalMoneyEarned.toPlainString();
        }
        if (params.equalsIgnoreCase("top_bought_item")) {
            var top = statisticsService.getTopBought(1);
            return top.isEmpty() ? "none" : top.get(0).itemId;
        }
        if (params.equalsIgnoreCase("top_sold_item")) {
            var top = statisticsService.getTopSold(1);
            return top.isEmpty() ? "none" : top.get(0).itemId;
        }
        if (params.startsWith("item_bought_")) {
            String itemId = params.substring("item_bought_".length());
            return String.valueOf(statisticsService.getItemStats(itemId).totalBought);
        }
        if (params.startsWith("item_sold_")) {
            String itemId = params.substring("item_sold_".length());
            return String.valueOf(statisticsService.getItemStats(itemId).totalSold);
        }
        return null;
    }
}
