package net.shopplugin.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.shopplugin.config.GuiMessages;
import net.shopplugin.model.TransactionResult;
import net.shopplugin.service.TransactionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SellAllCommand implements CommandExecutor {

    private final TransactionService transactionService;
    private final GuiMessages messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SellAllCommand(TransactionService transactionService, GuiMessages messages) {
        this.transactionService = transactionService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!player.hasPermission("shopplugin.sellall")) {
            player.sendMessage(miniMessage.deserialize("<red>You do not have permission to do that.</red>"));
            return true;
        }

        List<TransactionResult> results = transactionService.sellAll(player);
        if (results.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(messages.get("sellall-nothing")));
            return true;
        }

        BigDecimal total = results.stream().map(TransactionResult::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(results.size()));
        placeholders.put("total", "$" + total.stripTrailingZeros().toPlainString());
        player.sendMessage(miniMessage.deserialize(messages.get("sellall-summary", placeholders)));
        return true;
    }
}
