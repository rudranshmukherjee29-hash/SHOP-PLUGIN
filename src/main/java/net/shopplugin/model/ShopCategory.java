package net.shopplugin.model;

/**
 * The curated top-level categories of the shop. Deliberately small and fixed
 * so the shop stays navigable rather than becoming a sprawling item dump.
 */
public enum ShopCategory {
    BLOCKS("Blocks"),
    ORES_AND_MINERALS("Ores & Minerals"),
    FARMING("Farming"),
    MOB_DROPS("Mob Drops"),
    FOOD("Food"),
    REDSTONE_AND_UTILITY("Redstone & Utility"),
    NETHER_AND_END("Nether & End");

    private final String displayName;

    ShopCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
