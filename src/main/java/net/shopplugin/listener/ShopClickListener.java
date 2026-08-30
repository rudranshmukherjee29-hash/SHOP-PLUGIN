package net.shopplugin.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.shopplugin.config.GuiMessages;
import net.shopplugin.config.GuiTextConfig;
import net.shopplugin.economy.EconomyProvider;
import net.shopplugin.gui.ShopGuiHolder;
import net.shopplugin.gui.ShopGuiRenderer;
import net.shopplugin.model.ShopCategory;
import net.shopplugin.model.ShopItem;
import net.shopplugin.model.TransactionResult;
import net.shopplugin.repository.ShopRepository;
import net.shopplugin.security.ShopItemTagger;
import net.shopplugin.service.TransactionService;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interprets clicks inside plugin-owned shop GUIs and drives navigation and
 * transactions. Runs at NORMAL priority, before {@link ShopGuiProtectionListener}
 * (HIGH priority) so it can read the click intent; it always cancels the
 * event itself for top-inventory clicks so nothing depends on ordering for
 * safety — protection listener cancelling again is redundant-but-harmless
 * defense in depth, not the only thing standing between a click and a dupe.
 */
public final class ShopClickListener implements Listener {

    private final Logger logger;
    private final ShopRepository repository;
    private final ShopGuiRenderer renderer;
    private final TransactionService transactionService;
    private final ShopItemTagger tagger;
    private final GuiTextConfig guiText;
    private final GuiMessages messages;
    private final EconomyProvider economy;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Players currently expected to type a search query in chat.
    private final Set<java.util.UUID> awaitingSearch = ConcurrentHashMap.newKeySet();

    public ShopClickListener(Logger logger, ShopRepository repository, ShopGuiRenderer renderer,
                              TransactionService transactionService, ShopItemTagger tagger,
                              GuiTextConfig guiText, GuiMessages messages, EconomyProvider economy) {
        this.logger = logger;
        this.repository = repository;
        this.renderer = renderer;
        this.transactionService = transactionService;
        this.tagger = tagger;
        this.guiText = guiText;
        this.messages = messages;
        this.economy = economy;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopGuiHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Only interpret clicks that land on the top (shop) inventory; clicks
        // in the player's own inventory while a shop GUI is open do nothing
        // special here (ShopGuiProtectionListener still blocks depositing
        // into the shop side via shift-click).
        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof ShopGuiHolder;
        if (!clickedTop) {
            return;
        }

        // Always cancel: this inventory is a read-only display, never a real
        // container a player should be able to take items from.
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !tagger.isGuiButton(clicked)) {
            return;
        }
        Optional<String> idOpt = tagger.getShopItemId(clicked);
        if (idOpt.isEmpty()) {
            return;
        }
        String id = idOpt.get();

        try {
            switch (holder.getViewType()) {
                case MAIN_MENU -> handleMainMenuClick(player, id);
                case CATEGORY -> handleItemOrNavClick(player, holder, id, event.isShiftClick(), event.isRightClick());
                case SEARCH_RESULTS -> handleItemOrNavClick(player, holder, id, event.isShiftClick(), event.isRightClick());
                case CONFIRMATION -> handleConfirmationClick(player, holder, id);
            }
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Unexpected error handling shop GUI click for " + player.getName(), e);
            playError(player);
        }
    }

    private void handleMainMenuClick(Player player, String id) {
        if (id.equals("action:close")) {
            player.closeInventory();
            return;
        }
        if (id.equals("action:search")) {
            openSearchPrompt(player);
            return;
        }
        if (id.startsWith("category:")) {
            ShopCategory category = ShopCategory.valueOf(id.substring("category:".length()));
            player.openInventory(renderer.renderCategory(category, 0, player));
        }
    }

    private void handleItemOrNavClick(Player player, ShopGuiHolder holder, String id, boolean shift, boolean right) {
        switch (id) {
            case "action:close" -> {
                player.closeInventory();
                return;
            }
            case "action:back" -> {
                player.openInventory(renderer.renderMainMenu());
                return;
            }
            case "action:search" -> {
                openSearchPrompt(player);
                return;
            }
            case "action:nextpage" -> {
                openView(player, holder, holder.getPage() + 1);
                return;
            }
            case "action:prevpage" -> {
                openView(player, holder, Math.max(0, holder.getPage() - 1));
                return;
            }
            case "decoration" -> {
                return;
            }
            default -> {
                // A real shop item id.
                Optional<ShopItem> itemOpt = repository.getItem(id);
                if (itemOpt.isEmpty()) {
                    return;
                }
                handleItemClick(player, itemOpt.get(), shift, right, holder);
            }
        }
    }

    private void openView(Player player, ShopGuiHolder holder, int page) {
        switch (holder.getViewType()) {
            case CATEGORY -> player.openInventory(renderer.renderCategory(holder.getCategory(), page, player));
            case SEARCH_RESULTS -> player.openInventory(renderer.renderSearch(holder.getSearchQuery(), page, player));
            default -> { /* no-op */ }
        }
    }

    private void handleItemClick(Player player, ShopItem item, boolean shift, boolean right, ShopGuiHolder previousHolder) {
        boolean isBuy = !right;
        long quantity;

        if (isBuy) {
            if (!item.isBuyable()) {
                playError(player);
                return;
            }
            quantity = shift ? 64 : 1;
        } else {
            if (!item.isSellable()) {
                playError(player);
                return;
            }
            if (shift) {
                // Sell all matching items the player is holding, handled directly
                // (not via confirmation) since it's bounded by inventory contents.
                TransactionResult result = transactionService.sell(player, item.getId(), countHeld(player, item));
                announceResult(player, result, true);
                refreshView(player, previousHolder);
                return;
            }
            quantity = 1;
        }

        BigDecimal unitPrice = isBuy ? item.getBuyPrice() : item.getSellPrice();
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));

        boolean needsConfirmation = total.compareTo(BigDecimal.valueOf(5000)) >= 0;
        if (needsConfirmation) {
            // Large transaction: show a confirmation screen instead of acting immediately.
            openConfirmation(player, item.getId(), quantity, isBuy);
            return;
        }

        TransactionResult result = isBuy
                ? transactionService.buy(player, item.getId(), quantity)
                : transactionService.sell(player, item.getId(), quantity);
        announceResult(player, result, isBuy);
        refreshView(player, previousHolder);
    }

    private void handleConfirmationClick(Player player, ShopGuiHolder holder, String id) {
        if (id.equals("action:confirm")) {
            TransactionResult result = holder.isPendingIsBuy()
                    ? transactionService.buy(player, holder.getPendingItemId(), holder.getPendingQuantity())
                    : transactionService.sell(player, holder.getPendingItemId(), holder.getPendingQuantity());
            announceResult(player, result, holder.isPendingIsBuy());
            player.closeInventory();
        } else if (id.equals("action:cancel")) {
            player.closeInventory();
        }
    }

    private void openConfirmation(Player player, String itemId, long quantity, boolean isBuy) {
        // Confirmation GUI is built inline here (small, single-purpose) rather
        // than in ShopGuiRenderer, since it needs no pagination/category logic.
        var inv = Bukkit.createInventory(
                net.shopplugin.gui.ShopGuiHolder.confirmation(itemId, quantity, isBuy),
                27, miniMessage.deserialize("<gold>Confirm Transaction</gold>"));
        ((ShopGuiHolder) inv.getHolder()).setInventory(inv);

        Optional<ShopItem> itemOpt = repository.getItem(itemId);
        if (itemOpt.isEmpty()) {
            playError(player);
            return;
        }
        ShopItem item = itemOpt.get();
        BigDecimal unitPrice = isBuy ? item.getBuyPrice() : item.getSellPrice();
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));

        ItemStack info = renderer.renderItemButton(item, player);
        inv.setItem(13, info);

        ItemStack confirm = new ItemStack(org.bukkit.Material.LIME_WOOL);
        var confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(miniMessage.deserialize(
                "<green>Confirm " + (isBuy ? "Buy" : "Sell") + " " + quantity + "x for $" + total.stripTrailingZeros().toPlainString() + "</green>"));
        confirm.setItemMeta(confirmMeta);
        tagger.tagAsGuiButton(confirm, "action:confirm");
        inv.setItem(11, confirm);

        ItemStack cancel = new ItemStack(org.bukkit.Material.RED_WOOL);
        var cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(miniMessage.deserialize("<red>Cancel</red>"));
        cancel.setItemMeta(cancelMeta);
        tagger.tagAsGuiButton(cancel, "action:cancel");
        inv.setItem(15, cancel);

        player.openInventory(inv);
    }

    private void openSearchPrompt(Player player) {
        player.closeInventory();
        awaitingSearch.add(player.getUniqueId());
        player.sendMessage(miniMessage.deserialize(messages.get("search-prompt")));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingSearch.remove(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String query = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("ShopPlugin"),
                () -> {
                    if (query.equalsIgnoreCase("cancel")) {
                        return;
                    }
                    player.openInventory(renderer.renderSearch(query, 0, player));
                });
    }

    private void refreshView(Player player, ShopGuiHolder holder) {
        // Reopen the same view to reflect updated stock/price display.
        switch (holder.getViewType()) {
            case MAIN_MENU -> player.openInventory(renderer.renderMainMenu());
            case CATEGORY -> player.openInventory(renderer.renderCategory(holder.getCategory(), holder.getPage(), player));
            case SEARCH_RESULTS -> player.openInventory(renderer.renderSearch(holder.getSearchQuery(), holder.getPage(), player));
            default -> { /* confirmation views close instead of refreshing */ }
        }
    }

    private long countHeld(Player player, ShopItem item) {
        long total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == item.getMaterial()) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void announceResult(Player player, TransactionResult result, boolean isBuy) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantity", String.valueOf(result.getQuantity()));
        placeholders.put("item", result.getItemId() == null ? "item" : result.getItemId());
        placeholders.put("price", "$" + result.getAmount().stripTrailingZeros().toPlainString());
        placeholders.put("reason", result.getMessage() == null ? "unknown" : result.getMessage());

        String key = switch (result.getStatus()) {
            case SUCCESS -> isBuy ? "buy-success" : "sell-success";
            case INSUFFICIENT_FUNDS -> "insufficient-funds";
            case INSUFFICIENT_ITEMS -> "insufficient-items";
            case INVENTORY_FULL -> "inventory-full";
            case OUT_OF_STOCK -> "out-of-stock";
            case PURCHASE_LIMIT_REACHED -> "purchase-limit-reached";
            case BUY_DISABLED -> "buy-disabled";
            case SELL_DISABLED -> "sell-disabled";
            case INVALID_QUANTITY -> "invalid-quantity";
            case ECONOMY_UNAVAILABLE -> "economy-unavailable";
            case ECONOMY_FAILURE -> "economy-failure";
            case TRANSACTION_CONFLICT -> "transaction-conflict";
            case ITEM_NOT_FOUND -> "item-not-found";
            default -> "internal-error";
        };

        player.sendMessage(miniMessage.deserialize(messages.get(key, placeholders)));

        if (guiText.isSoundsEnabled()) {
            try {
                Sound sound = Sound.valueOf(result.isSuccess()
                        ? (isBuy ? guiText.getPurchaseSound() : guiText.getSellSound())
                        : guiText.getErrorSound());
                player.playSound(player.getLocation(), sound, 1f, 1f);
            } catch (IllegalArgumentException ignored) {
                // Misconfigured sound name; fail silently rather than breaking the transaction flow.
            }
        }
    }

    private void playError(Player player) {
        if (!guiText.isSoundsEnabled()) return;
        try {
            player.playSound(player.getLocation(), Sound.valueOf(guiText.getErrorSound()), 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
