package net.shopplugin.repository;

import net.shopplugin.model.ShopCategory;
import net.shopplugin.model.ShopItem;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Thread-safe catalog backed by an AtomicReference to an immutable snapshot.
 * Readers always see a fully-formed catalog, either the old one or the new
 * one, never a partially-reloaded state — this matters because transactions
 * can be validated from any thread reading the catalog concurrently with
 * an admin reload.
 */
public final class InMemoryShopRepository implements ShopRepository {

    private static final class Snapshot {
        final Map<String, ShopItem> byId;
        final Map<ShopCategory, List<ShopItem>> byCategory;

        Snapshot(List<ShopItem> items) {
            this.byId = items.stream()
                    .collect(Collectors.toUnmodifiableMap(ShopItem::getId, i -> i, (a, b) -> a));
            this.byCategory = items.stream()
                    .collect(Collectors.groupingBy(ShopItem::getCategory));
        }
    }

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(new Snapshot(List.of()));

    @Override
    public Optional<ShopItem> getItem(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.get().byId.get(id.toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<ShopItem> getItemsByCategory(ShopCategory category) {
        return snapshot.get().byCategory.getOrDefault(category, Collections.emptyList());
    }

    @Override
    public List<ShopItem> getAllItems() {
        return List.copyOf(snapshot.get().byId.values());
    }

    @Override
    public List<ShopItem> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return snapshot.get().byId.values().stream()
                .filter(item -> item.getId().toLowerCase(Locale.ROOT).contains(needle)
                        || item.getMaterial().name().toLowerCase(Locale.ROOT).contains(needle))
                .collect(Collectors.toList());
    }

    @Override
    public void reload(List<ShopItem> newItems) {
        // Normalize ids to lowercase on load so lookups are case-insensitive and
        // consistent between the catalog and the stock/statistics keys derived from it.
        List<ShopItem> normalized = newItems.stream()
                .collect(Collectors.toMap(i -> i.getId().toLowerCase(Locale.ROOT), i -> i, (a, b) -> a))
                .values().stream().collect(Collectors.toList());
        snapshot.set(new Snapshot(normalized));
    }
}
