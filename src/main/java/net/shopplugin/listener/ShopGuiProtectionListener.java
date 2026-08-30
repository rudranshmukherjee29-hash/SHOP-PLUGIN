package net.shopplugin.listener;

import net.shopplugin.gui.ShopGuiHolder;
import net.shopplugin.security.ShopItemTagger;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Locks down the shop GUI so decorative/button items can never leave it via
 * any known vector: shift-click, drag, number-key swap, double-click
 * collect, off-hand swap, or item drop while the GUI is open.
 *
 * The actual buy/sell logic lives in {@link net.shopplugin.listener.ShopClickListener};
 * this class's only job is "nothing real ever leaves or enters this inventory."
 */
public final class ShopGuiProtectionListener implements Listener {

    private final ShopItemTagger tagger;

    // Click types that could move an item out of the top inventory into the
    // player's inventory or cursor in ways beyond a simple single click.
    private static final Set<ClickType> DANGEROUS_CLICK_TYPES = Set.of(
            ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT,
            ClickType.DOUBLE_CLICK,
            ClickType.NUMBER_KEY,
            ClickType.SWAP_OFFHAND,
            ClickType.CREATIVE
    );

    public ShopGuiProtectionListener(ShopItemTagger tagger) {
        this.tagger = tagger;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ShopGuiHolder)) {
            return;
        }

        // Any click involving the shop GUI's top inventory is fully handled by
        // ShopClickListener at a lower priority for legitimate buy/sell
        // actions. Here we only ensure no item can actually be extracted or
        // inserted into the top inventory itself, regardless of click type.
        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof ShopGuiHolder;

        if (clickedTop) {
            // Never allow taking the cursor item and placing it in the shop GUI,
            // never allow picking up a GUI button as a real item.
            event.setCancelled(true);
        }

        if (DANGEROUS_CLICK_TYPES.contains(event.getClick()) && clickedTop) {
            event.setCancelled(true);
        }

        // Shift-clicking from the player's OWN inventory while the shop GUI is
        // open must never be able to deposit items into the shop GUI's top
        // inventory (there is no legitimate reason for it to accept items).
        if (!clickedTop && event.isShiftClick()) {
            Inventory clicked = event.getClickedInventory();
            if (clicked != null && !(clicked.getHolder() instanceof ShopGuiHolder)) {
                // shift-click from bottom inventory targeting top: Bukkit will try
                // to move it into the top inventory. Cancel defensively since the
                // shop GUI should never receive real items this way.
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ShopGuiHolder)) {
            return;
        }
        // Cancel any drag that touches a slot in the top (shop) inventory.
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (touchesTop) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        // Prevent hoppers or droppers from ever pulling from / pushing into a
        // shop GUI inventory (defense in depth; shop GUIs are not intended to
        // be placed where a hopper could reach them, but a holder inventory
        // could theoretically be targeted by a crafted setup).
        if (event.getSource().getHolder() instanceof ShopGuiHolder
                || event.getDestination().getHolder() instanceof ShopGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof ShopGuiHolder)) {
            return;
        }
        // Shop GUI buttons only ever exist in the top (shop) inventory, never in
        // the player's own hands, so there is no legitimate GUI-button item this
        // event could move. Cancel outright while a shop GUI is open purely as
        // cheap defense-in-depth against unforeseen edge cases, since off-hand
        // swapping has no legitimate purpose while browsing the shop.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (tagger.isGuiButton(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        // Defense in depth: if any GUI-tagged item somehow ended up in the
        // player's real inventory or cursor, strip it on close.
        if (!(event.getInventory().getHolder() instanceof ShopGuiHolder)) {
            return;
        }
        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) {
            return;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && tagger.isGuiButton(cursor)) {
            player.setItemOnCursor(null);
        }
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && tagger.isGuiButton(contents[i])) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setContents(contents);
        }
    }
}
