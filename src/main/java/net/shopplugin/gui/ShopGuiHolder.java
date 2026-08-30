package net.shopplugin.gui;

import net.shopplugin.model.ShopCategory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder identifying an inventory as a plugin-owned shop GUI, plus
 * the minimal view-state needed to interpret clicks (which category/page is
 * currently shown, and whether it's a category list vs. the main menu).
 *
 * This is the authoritative way the plugin recognizes "this inventory is
 * mine" — never inferred from title text, which is player-visible and
 * configurable (and therefore untrustworthy as an identity check).
 */
public final class ShopGuiHolder implements InventoryHolder {

    public enum ViewType {
        MAIN_MENU,
        CATEGORY,
        SEARCH_RESULTS,
        CONFIRMATION
    }

    private Inventory inventory;
    private final ViewType viewType;
    private final ShopCategory category; // nullable for MAIN_MENU/SEARCH_RESULTS
    private final int page;
    private final String searchQuery; // nullable unless SEARCH_RESULTS

    // Only populated for CONFIRMATION views: the pending action details.
    private final String pendingItemId;
    private final long pendingQuantity;
    private final boolean pendingIsBuy;

    public ShopGuiHolder(ViewType viewType, ShopCategory category, int page, String searchQuery,
                          String pendingItemId, long pendingQuantity, boolean pendingIsBuy) {
        this.viewType = viewType;
        this.category = category;
        this.page = page;
        this.searchQuery = searchQuery;
        this.pendingItemId = pendingItemId;
        this.pendingQuantity = pendingQuantity;
        this.pendingIsBuy = pendingIsBuy;
    }

    public static ShopGuiHolder mainMenu() {
        return new ShopGuiHolder(ViewType.MAIN_MENU, null, 0, null, null, 0, false);
    }

    public static ShopGuiHolder category(ShopCategory category, int page) {
        return new ShopGuiHolder(ViewType.CATEGORY, category, page, null, null, 0, false);
    }

    public static ShopGuiHolder search(String query, int page) {
        return new ShopGuiHolder(ViewType.SEARCH_RESULTS, null, page, query, null, 0, false);
    }

    public static ShopGuiHolder confirmation(String itemId, long quantity, boolean isBuy) {
        return new ShopGuiHolder(ViewType.CONFIRMATION, null, 0, null, itemId, quantity, isBuy);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public ViewType getViewType() {
        return viewType;
    }

    public ShopCategory getCategory() {
        return category;
    }

    public int getPage() {
        return page;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public String getPendingItemId() {
        return pendingItemId;
    }

    public long getPendingQuantity() {
        return pendingQuantity;
    }

    public boolean isPendingIsBuy() {
        return pendingIsBuy;
    }
}
