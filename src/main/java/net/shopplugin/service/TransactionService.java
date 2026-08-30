package net.shopplugin.service;

import net.shopplugin.model.TransactionResult;
import org.bukkit.entity.Player;

import java.util.List;

public interface TransactionService {

    /**
     * Executes a buy transaction. MUST be called on the main server thread
     * (it touches player inventory), and internally guards against
     * concurrent/overlapping calls for the same player.
     */
    TransactionResult buy(Player player, String itemId, long quantity);

    /**
     * Executes a sell transaction for a specific quantity of a specific item.
     */
    TransactionResult sell(Player player, String itemId, long quantity);

    /**
     * Sells the exact stack currently in the player's main hand.
     */
    TransactionResult sellHand(Player player);

    /**
     * Sells every sellable item found in the player's main inventory
     * (armor and off-hand are never touched implicitly), one item type at
     * a time, returning one result per item type that had any sellable
     * quantity.
     */
    List<TransactionResult> sellAll(Player player);
}

