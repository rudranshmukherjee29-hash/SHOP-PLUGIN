package net.shopplugin.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed, defaulted view over messages.yml. Every player-facing string comes
 * through here so translators/admins can edit one file, and so transaction
 * code never hardcodes literal player-facing text.
 */
public final class GuiMessages {

    private final Map<String, String> messages = new HashMap<>();

    public GuiMessages(FileConfiguration config) {
        load(config);
    }

    private void load(FileConfiguration config) {
        put(config, "prefix", "<gray>[<gold>Shop</gold>]</gray> ");
        put(config, "buy-success", "<prefix><green>Bought {quantity}x {item} for {price}.</green>");
        put(config, "sell-success", "<prefix><green>Sold {quantity}x {item} for {price}.</green>");
        put(config, "sellall-summary", "<prefix><green>Sold {count} item type(s) for a total of {total}.</green>");
        put(config, "sellall-nothing", "<prefix><yellow>You have nothing sellable in your inventory.</yellow>");
        put(config, "insufficient-funds", "<prefix><red>You don't have enough money for that.</red>");
        put(config, "insufficient-items", "<prefix><red>You don't have that many of this item.</red>");
        put(config, "inventory-full", "<prefix><red>Your inventory is full.</red>");
        put(config, "out-of-stock", "<prefix><red>That item is out of stock.</red>");
        put(config, "purchase-limit-reached", "<prefix><red>You've reached your daily limit for this item.</red>");
        put(config, "buy-disabled", "<prefix><red>This item cannot be purchased.</red>");
        put(config, "sell-disabled", "<prefix><red>This item cannot be sold here.</red>");
        put(config, "invalid-quantity", "<prefix><red>Invalid quantity.</red>");
        put(config, "economy-unavailable", "<prefix><red>The economy is temporarily unavailable. Please try again shortly.</red>");
        put(config, "economy-failure", "<prefix><red>Transaction failed: {reason}</red>");
        put(config, "transaction-conflict", "<prefix><red>Please wait for your current transaction to finish.</red>");
        put(config, "internal-error", "<prefix><red>Something went wrong. No money or items were exchanged.</red>");
        put(config, "no-permission", "<prefix><red>You don't have permission to do that.</red>");
        put(config, "item-not-found", "<prefix><red>That item doesn't exist.</red>");
        put(config, "search-prompt", "<prefix><yellow>Type your search in chat, or type 'cancel' to abort.</yellow>");
        put(config, "confirm-prompt", "<prefix><yellow>Confirm: {action} {quantity}x {item} for {price}?</yellow>");
        put(config, "reload-success", "<prefix><green>Configuration reloaded successfully.</green>");
        put(config, "reload-failure", "<prefix><red>Reload failed: {reason}</red>");
    }

    private void put(FileConfiguration config, String key, String def) {
        messages.put(key, config.getString("messages." + key, def));
    }

    public String get(String key) {
        return messages.getOrDefault(key, key);
    }

    public String get(String key, Map<String, String> placeholders) {
        String raw = get(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw.replace("{prefix}", get("prefix")).replace("<prefix>", get("prefix"));
    }
}
