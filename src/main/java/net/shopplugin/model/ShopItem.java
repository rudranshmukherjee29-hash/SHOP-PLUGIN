package net.shopplugin.model;

import org.bukkit.Material;

import java.math.BigDecimal;

/**
 * Immutable definition of a single shop entry, loaded from prices.yml.
 * The internal {@link #id} (not display name, lore, or Material alone) is
 * the authoritative identifier used everywhere in transactions and storage,
 * so renaming an item's lore/display text in the GUI config can never change
 * what is actually bought/sold.
 */
public final class ShopItem {

    private final String id;
    private final Material material;
    private final ShopCategory category;
    private final BigDecimal buyPrice;   // null => buying disabled
    private final BigDecimal sellPrice;  // null => selling disabled
    private final int maxTransactionSize;
    private final boolean limitedStock;
    private final long maxStock;         // -1 = unlimited
    private final long restockAmount;
    private final long restockIntervalSeconds;
    private final long perPlayerDailyLimit; // -1 = unlimited
    private final int guiSlot;
    private final String displayNameOverride; // nullable, MiniMessage string

    private ShopItem(Builder b) {
        this.id = b.id;
        this.material = b.material;
        this.category = b.category;
        this.buyPrice = b.buyPrice;
        this.sellPrice = b.sellPrice;
        this.maxTransactionSize = b.maxTransactionSize;
        this.limitedStock = b.limitedStock;
        this.maxStock = b.maxStock;
        this.restockAmount = b.restockAmount;
        this.restockIntervalSeconds = b.restockIntervalSeconds;
        this.perPlayerDailyLimit = b.perPlayerDailyLimit;
        this.guiSlot = b.guiSlot;
        this.displayNameOverride = b.displayNameOverride;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public ShopCategory getCategory() {
        return category;
    }

    public boolean isBuyable() {
        return buyPrice != null && buyPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isSellable() {
        return sellPrice != null && sellPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getBuyPrice() {
        return buyPrice;
    }

    public BigDecimal getSellPrice() {
        return sellPrice;
    }

    public int getMaxTransactionSize() {
        return maxTransactionSize;
    }

    public boolean isLimitedStock() {
        return limitedStock;
    }

    public long getMaxStock() {
        return maxStock;
    }

    public long getRestockAmount() {
        return restockAmount;
    }

    public long getRestockIntervalSeconds() {
        return restockIntervalSeconds;
    }

    public long getPerPlayerDailyLimit() {
        return perPlayerDailyLimit;
    }

    public int getGuiSlot() {
        return guiSlot;
    }

    public String getDisplayNameOverride() {
        return displayNameOverride;
    }

    public static Builder builder(String id, Material material, ShopCategory category) {
        return new Builder(id, material, category);
    }

    public static final class Builder {
        private final String id;
        private final Material material;
        private final ShopCategory category;
        private BigDecimal buyPrice;
        private BigDecimal sellPrice;
        private int maxTransactionSize = 6400;
        private boolean limitedStock = false;
        private long maxStock = -1;
        private long restockAmount = 0;
        private long restockIntervalSeconds = 0;
        private long perPlayerDailyLimit = -1;
        private int guiSlot = -1;
        private String displayNameOverride;

        private Builder(String id, Material material, ShopCategory category) {
            this.id = id;
            this.material = material;
            this.category = category;
        }

        public Builder buyPrice(BigDecimal v) { this.buyPrice = v; return this; }
        public Builder sellPrice(BigDecimal v) { this.sellPrice = v; return this; }
        public Builder maxTransactionSize(int v) { this.maxTransactionSize = v; return this; }
        public Builder limitedStock(boolean v) { this.limitedStock = v; return this; }
        public Builder maxStock(long v) { this.maxStock = v; return this; }
        public Builder restockAmount(long v) { this.restockAmount = v; return this; }
        public Builder restockIntervalSeconds(long v) { this.restockIntervalSeconds = v; return this; }
        public Builder perPlayerDailyLimit(long v) { this.perPlayerDailyLimit = v; return this; }
        public Builder guiSlot(int v) { this.guiSlot = v; return this; }
        public Builder displayNameOverride(String v) { this.displayNameOverride = v; return this; }

        public ShopItem build() {
            return new ShopItem(this);
        }
    }
}
