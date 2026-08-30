package net.shopplugin.config;

import net.shopplugin.model.ShopCategory;
import net.shopplugin.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Parses prices.yml into validated {@link ShopItem} instances. Invalid
 * entries (bad material name, missing category, etc.) are logged and
 * skipped rather than crashing plugin startup — a single typo in the
 * config should not take down the whole shop.
 */
public final class ItemDataLoader {

    private final Logger logger;

    public ItemDataLoader(Logger logger) {
        this.logger = logger;
    }

    public List<ShopItem> load(FileConfiguration prices) {
        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection itemsSection = prices.getConfigurationSection("items");
        if (itemsSection == null) {
            logger.warning("prices.yml has no 'items' section; the shop will be empty.");
            return items;
        }

        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(id);
            if (section == null) continue;
            try {
                ShopItem item = parseItem(id, section);
                if (item != null) {
                    items.add(item);
                }
            } catch (RuntimeException e) {
                logger.warning("Skipping invalid shop item '" + id + "': " + e.getMessage());
            }
        }
        return items;
    }

    private ShopItem parseItem(String id, ConfigurationSection section) {
        String materialName = section.getString("material");
        if (materialName == null) {
            logger.warning("Item '" + id + "' has no material configured; skipping.");
            return null;
        }
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Item '" + id + "' has unknown material '" + materialName + "'; skipping.");
            return null;
        }

        String categoryName = section.getString("category");
        if (categoryName == null) {
            logger.warning("Item '" + id + "' has no category configured; skipping.");
            return null;
        }
        ShopCategory category;
        try {
            category = ShopCategory.valueOf(categoryName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Item '" + id + "' has unknown category '" + categoryName + "'; skipping.");
            return null;
        }

        ShopItem.Builder builder = ShopItem.builder(id.toLowerCase(Locale.ROOT), material, category);

        if (section.contains("buy-price")) {
            builder.buyPrice(toBigDecimal(section.getString("buy-price")));
        }
        if (section.contains("sell-price")) {
            builder.sellPrice(toBigDecimal(section.getString("sell-price")));
        }
        builder.maxTransactionSize(section.getInt("max-transaction-size", 6400));
        builder.limitedStock(section.getBoolean("limited-stock", false));
        builder.maxStock(section.getLong("max-stock", -1));
        builder.restockAmount(section.getLong("restock-amount", 0));
        builder.restockIntervalSeconds(section.getLong("restock-interval-seconds", 0));
        builder.perPlayerDailyLimit(section.getLong("per-player-daily-limit", -1));
        builder.guiSlot(section.getInt("gui-slot", -1));
        if (section.contains("display-name")) {
            builder.displayNameOverride(section.getString("display-name"));
        }

        return builder.build();
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid decimal value: " + value);
        }
    }
}
