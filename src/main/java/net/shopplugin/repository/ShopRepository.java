package net.shopplugin.repository;

import net.shopplugin.model.ShopCategory;
import net.shopplugin.model.ShopItem;

import java.util.List;
import java.util.Optional;

/**
 * Read-mostly, in-memory catalog of shop items, cached from prices.yml.
 * Reloaded atomically on /shopadmin reload; never mutated piecemeal while
 * players might be reading it mid-transaction (see {@link #reload(List)}).
 */
public interface ShopRepository {

    Optional<ShopItem> getItem(String id);

    List<ShopItem> getItemsByCategory(ShopCategory category);

    List<ShopItem> getAllItems();

    List<ShopItem> search(String query);

    void reload(List<ShopItem> newItems);
}
