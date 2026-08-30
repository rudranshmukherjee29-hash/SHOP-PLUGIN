package net.shopplugin.config;

import net.shopplugin.model.ConversionRelation;

import java.util.List;

import static net.shopplugin.model.ConversionRelation.Type.*;

/**
 * Hand-curated list of vanilla crafting/smelting/compression relationships
 * between items that exist in the default shop catalog. This is
 * deliberately not derived from Bukkit's live recipe iterator: only
 * relationships that are economically meaningful (i.e. both sides are
 * actually in the shop) are worth checking, and hand-curation avoids noise
 * from recipes that share an ingredient without being a real arbitrage path.
 *
 * If a server admin adds new items to prices.yml that have a conversion
 * relationship with existing items, they should add a matching entry here
 * (or in a future config-driven version of this list) so
 * /shopadmin validateprices can catch loops involving the new item.
 */
public final class DefaultConversions {

    private DefaultConversions() {
    }

    public static List<ConversionRelation> get() {
        return List.of(
                // Smelting: ore -> ingot
                new ConversionRelation("raw_iron", 1, "iron_ingot", 1, SMELTING),
                new ConversionRelation("raw_copper", 1, "copper_ingot", 1, SMELTING),
                new ConversionRelation("raw_gold", 1, "gold_ingot", 1, SMELTING),

                // Compression: 9 ingots/items -> 1 block
                new ConversionRelation("iron_ingot", 9, "iron_block", 1, COMPRESSION),
                new ConversionRelation("copper_ingot", 9, "copper_block", 1, COMPRESSION),
                new ConversionRelation("gold_ingot", 9, "gold_block", 1, COMPRESSION),
                new ConversionRelation("redstone", 9, "redstone_block", 1, COMPRESSION),

                // Decompression: 1 block -> 9 ingots/items (same pairs, reverse direction
                // is checked automatically by PriceValidationService for COMPRESSION/DECOMPRESSION types)
                new ConversionRelation("iron_ingot", 9, "iron_block", 1, DECOMPRESSION),
                new ConversionRelation("copper_ingot", 9, "copper_block", 1, DECOMPRESSION),
                new ConversionRelation("gold_ingot", 9, "gold_block", 1, DECOMPRESSION),
                new ConversionRelation("redstone", 9, "redstone_block", 1, DECOMPRESSION),

                // Crafting: wheat -> bread (3 wheat -> 1 bread)
                new ConversionRelation("wheat", 3, "bread", 1, CRAFTING),

                // Crafting: raw food -> cooked food (1:1 via furnace, not smelting technically
                // but same "shop mats in, sell furnace output" shape)
                new ConversionRelation("potato", 1, "cooked_potato", 1, SMELTING)
        );
    }
}
