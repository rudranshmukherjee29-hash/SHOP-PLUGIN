package net.shopplugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import net.shopplugin.config.GuiTextConfig;
import net.shopplugin.economy.EconomyProvider;
import net.shopplugin.model.ShopCategory;
import net.shopplugin.model.ShopItem;
import net.shopplugin.repository.ShopRepository;
import net.shopplugin.security.ShopItemTagger;
import net.shopplugin.service.StockService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure rendering: builds Inventory contents from shop data. Contains no
 * transaction logic and mutates no economy/stock state — it only reads.
 */
public final class ShopGuiRenderer {

    private static final int MAIN_MENU_SIZE = 54;
    private static final int CATEGORY_SIZE = 54;

    private final ShopRepository repository;
    private final EconomyProvider economy;
    private final StockService stockService;
    private final ShopItemTagger tagger;
    private final GuiTextConfig guiText;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ShopGuiRenderer(ShopRepository repository, EconomyProvider economy, StockService stockService,
                            ShopItemTagger tagger, GuiTextConfig guiText) {
        this.repository = repository;
        this.economy = economy;
        this.stockService = stockService;
        this.tagger = tagger;
        this.guiText = guiText;
    }

    public Inventory renderMainMenu() {
        ShopGuiHolder holder = ShopGuiHolder.mainMenu();
        Inventory inv = Bukkit.createInventory(holder, MAIN_MENU_SIZE, mm(guiText.getMainMenuTitle()));
        holder.setInventory(inv);

        applyDecoration(inv);

        int[] categorySlots = {10, 12, 14, 16, 28, 30, 32};
        ShopCategory[] categories = ShopCategory.values();
        for (int i = 0; i < categories.length && i < categorySlots.length; i++) {
            ShopCategory category = categories[i];
            int count = repository.getItemsByCategory(category).size();
            ItemStack icon = new ItemStack(iconFor(category));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(mm("<gold><bold>" + category.getDisplayName() + "</bold></gold>").decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(mm("<gray>" + count + " items available</gray>").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(mm("<yellow>Click to browse</yellow>").decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
            tagger.tagAsGuiButton(icon, "category:" + category.name());
            inv.setItem(categorySlots[i], icon);
        }

        putSearchButton(inv);
        putCloseButton(inv);
        return inv;
    }

    public Inventory renderCategory(ShopCategory category, int page, OfflinePlayer viewer) {
        ShopGuiHolder holder = ShopGuiHolder.category(category, page);
        String title = guiText.getCategoryTitleTemplate().replace("{category}", category.getDisplayName());
        Inventory inv = Bukkit.createInventory(holder, CATEGORY_SIZE, mm(title));
        holder.setInventory(inv);

        applyDecoration(inv);
        List<ShopItem> items = repository.getItemsByCategory(category);
        renderPagedItems(inv, items, page, viewer);

        putBackButton(inv);
        putCloseButton(inv);
        putSearchButton(inv);
        putPaginationButtons(inv, items.size(), page);
        return inv;
    }

    public Inventory renderSearch(String query, int page, OfflinePlayer viewer) {
        ShopGuiHolder holder = ShopGuiHolder.search(query, page);
        Inventory inv = Bukkit.createInventory(holder, CATEGORY_SIZE, mm(guiText.getSearchTitle()));
        holder.setInventory(inv);

        applyDecoration(inv);
        List<ShopItem> items = repository.search(query);
        renderPagedItems(inv, items, page, viewer);

        putBackButton(inv);
        putCloseButton(inv);
        putPaginationButtons(inv, items.size(), page);
        return inv;
    }

    private void renderPagedItems(Inventory inv, List<ShopItem> items, int page, OfflinePlayer viewer) {
        int perPage = guiText.getItemsPerPage();
        int start = page * perPage;
        int[] contentSlots = contentSlots();
        for (int i = 0; i < contentSlots.length; i++) {
            int idx = start + i;
            if (idx >= items.size()) break;
            ShopItem item = items.get(idx);
            inv.setItem(contentSlots[i], renderItemButton(item, viewer));
        }
    }

    public ItemStack renderItemButton(ShopItem item, OfflinePlayer viewer) {
        ItemStack stack = new ItemStack(item.getMaterial());
        ItemMeta meta = stack.getItemMeta();

        String displayName = item.getDisplayNameOverride() != null
                ? item.getDisplayNameOverride()
                : "<white><bold>" + prettifyMaterialName(item.getMaterial()) + "</bold></white>";
        meta.displayName(mm(displayName).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        String symbol = "$";

        if (item.isBuyable()) {
            lore.add(mm("<green>Buy: " + symbol + formatPrice(item.getBuyPrice()) + " each</green>")
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(mm("<dark_gray>Not available for purchase</dark_gray>").decoration(TextDecoration.ITALIC, false));
        }

        if (item.isSellable()) {
            lore.add(mm("<yellow>Sell: " + symbol + formatPrice(item.getSellPrice()) + " each</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(mm("<dark_gray>Cannot be sold here</dark_gray>").decoration(TextDecoration.ITALIC, false));
        }

        if (item.isLimitedStock()) {
            long stock = stockService.getCurrentStock(item.getId());
            lore.add(Component.empty());
            lore.add(mm("<aqua>Stock: " + Math.max(stock, 0) + "</aqua>").decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        if (item.isBuyable()) {
            lore.add(mm("<gray>Left Click: <white>Buy 1</white></gray>").decoration(TextDecoration.ITALIC, false));
            lore.add(mm("<gray>Shift + Left Click: <white>Buy 64</white></gray>").decoration(TextDecoration.ITALIC, false));
        }
        if (item.isSellable()) {
            lore.add(mm("<gray>Right Click: <white>Sell 1</white></gray>").decoration(TextDecoration.ITALIC, false));
            lore.add(mm("<gray>Shift + Right Click: <white>Sell All</white></gray>").decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        stack.setItemMeta(meta);
        tagger.tagAsGuiButton(stack, item.getId());
        return stack;
    }

    private void putBackButton(Inventory inv) {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(mm("<yellow>Back</yellow>").decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(meta);
        tagger.tagAsGuiButton(back, "action:back");
        inv.setItem(guiText.getBackButtonSlot(), back);
    }

    private void putCloseButton(Inventory inv) {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta meta = close.getItemMeta();
        meta.displayName(mm("<red>Close</red>").decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(meta);
        tagger.tagAsGuiButton(close, "action:close");
        inv.setItem(guiText.getCloseButtonSlot(), close);
    }

    private void putSearchButton(Inventory inv) {
        ItemStack search = new ItemStack(Material.COMPASS);
        ItemMeta meta = search.getItemMeta();
        meta.displayName(mm("<aqua>Search</aqua>").decoration(TextDecoration.ITALIC, false));
        search.setItemMeta(meta);
        tagger.tagAsGuiButton(search, "action:search");
        inv.setItem(guiText.getSearchButtonSlot(), search);
    }

    private void putPaginationButtons(Inventory inv, int totalItems, int page) {
        int perPage = guiText.getItemsPerPage();
        int maxPage = Math.max(0, (int) Math.ceil(totalItems / (double) perPage) - 1);

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(mm("<yellow>Previous Page</yellow>").decoration(TextDecoration.ITALIC, false));
            prev.setItemMeta(meta);
            tagger.tagAsGuiButton(prev, "action:prevpage");
            inv.setItem(guiText.getPreviousPageSlot(), prev);
        }
        if (page < maxPage) {
            ItemStack next = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(mm("<yellow>Next Page</yellow>").decoration(TextDecoration.ITALIC, false));
            next.setItemMeta(meta);
            tagger.tagAsGuiButton(next, "action:nextpage");
            inv.setItem(guiText.getNextPageSlot(), next);
        }
    }

    private void applyDecoration(Inventory inv) {
        Material decorMat;
        try {
            decorMat = Material.valueOf(guiText.getDecorativeMaterialName().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            decorMat = Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack pane = new ItemStack(decorMat);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        tagger.tagAsGuiButton(pane, "decoration");

        for (int slot : guiText.getDecorativeSlots()) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, pane.clone());
            }
        }
        // Bottom row default border if no explicit decorative slots configured.
        if (guiText.getDecorativeSlots().isEmpty()) {
            for (int i = 45; i < 54; i++) {
                inv.setItem(i, pane.clone());
            }
        }
    }

    private int[] contentSlots() {
        // Rows 1-4 (indices 0-35) minus the outer border columns, a common
        // clean grid layout for a 54-slot inventory.
        List<Integer> slots = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private Material iconFor(ShopCategory category) {
        return switch (category) {
            case BLOCKS -> Material.BRICKS;
            case ORES_AND_MINERALS -> Material.DIAMOND_ORE;
            case FARMING -> Material.WHEAT;
            case MOB_DROPS -> Material.BONE;
            case FOOD -> Material.COOKED_BEEF;
            case REDSTONE_AND_UTILITY -> Material.REDSTONE;
            case NETHER_AND_END -> Material.CHORUS_FRUIT;
        };
    }

    private String prettifyMaterialName(Material material) {
        String[] parts = material.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT)).append(" ");
        }
        return sb.toString().trim();
    }

    private String formatPrice(BigDecimal price) {
        return price.stripTrailingZeros().toPlainString();
    }

    private Component mm(String input) {
        return miniMessage.deserialize(input);
    }
}
