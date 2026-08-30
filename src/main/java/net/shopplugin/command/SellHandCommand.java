package net.shopplugin.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.shopplugin.config.GuiMessages;
import net.shopplugin.model.TransactionResult;
import net.shopplugin.service.TransactionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class SellHandCommand implements CommandExecutor {

    private final TransactionService transactionService;
    private final GuiMessages messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SellHandCommand(TransactionService transactionService, GuiMessages messages) {
        this.transactionService = transactionService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!player.hasPermission("shopplugin.sell")) {
            player.sendMessage(miniMessage.deserialize("<red>You do not have permission to do that.</red>"));
            return true;
        }

        TransactionResult result = transactionService.sellHand(player);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("quantity", String.valueOf(result.getQuantity()));
        placeholders.put("item", result.getItemId() == null ? "item" : result.getItemId());
        placeholders.put("price", "$" + result.getAmount().stripTrailingZeros().toPlainString());
        placeholders.put("reason", result.getMessage() == null ? "unknown" : result.getMessage());

        String key = switch (result.getStatus()) {
            case SUCCESS -> "sell-success";
            case INSUFFICIENT_ITEMS -> "insufficient-items";
            case SELL_DISABLED -> "sell-disabled";
            case ECONOMY_UNAVAILABLE -> "economy-unavailable";
            case ECONOMY_FAILURE -> "economy-failure";
            case TRANSACTION_CONFLICT -> "transaction-conflict";
            default -> "internal-error";
        };
        player.sendMessage(miniMessage.deserialize(messages.get(key, placeholders)));
        return true;
    }
}
