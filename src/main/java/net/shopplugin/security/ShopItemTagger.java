package net.shopplugin.security;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Tags GUI display ItemStacks with a secure, server-side-only marker so the
 * plugin can distinguish "this is a shop GUI button" from "this is a real
 * inventory item that happens to look similar" without ever trusting
 * display name, lore, or custom model data.
 *
 * Shop GUI buttons are also tagged with the internal shop item id, so click
 * handling reads the id from PDC rather than parsing lore text.
 */
public final class ShopItemTagger {

    private final NamespacedKey guiMarkerKey;
    private final NamespacedKey shopItemIdKey;

    public ShopItemTagger(Plugin plugin) {
        this.guiMarkerKey = new NamespacedKey(plugin, "shop_gui_button");
        this.shopItemIdKey = new NamespacedKey(plugin, "shop_item_id");
    }

    public void tagAsGuiButton(ItemStack stack, String shopItemId) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(guiMarkerKey, PersistentDataType.BYTE, (byte) 1);
        if (shopItemId != null) {
            meta.getPersistentDataContainer().set(shopItemIdKey, PersistentDataType.STRING, shopItemId);
        }
        stack.setItemMeta(meta);
    }

    public boolean isGuiButton(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(guiMarkerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public Optional<String> getShopItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String id = meta.getPersistentDataContainer().get(shopItemIdKey, PersistentDataType.STRING);
        return Optional.ofNullable(id);
    }
}
