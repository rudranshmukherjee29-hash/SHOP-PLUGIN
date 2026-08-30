package net.shopplugin.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.shopplugin.config.GuiMessages;
import net.shopplugin.economy.EconomyOperationResult;
import net.shopplugin.economy.EconomyProvider;
import net.shopplugin.repository.ShopRepository;
import net.shopplugin.service.PriceValidationService;
import net.shopplugin.service.StatisticsService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.util.List;

public final class ShopAdminCommand implements CommandExecutor {

    private final ShopRepository repository;
    private final StatisticsService statisticsService;
    private final EconomyProvider economy;
    private final PriceValidationService priceValidationService;
    private final Runnable reloadAction;
    private final GuiMessages messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ShopAdminCommand(ShopRepository repository, StatisticsService statisticsService, EconomyProvider economy,
                             PriceValidationService priceValidationService, Runnable reloadAction, GuiMessages messages) {
        this.repository = repository;
        this.statisticsService = statisticsService;
        this.economy = economy;
        this.priceValidationService = priceValidationService;
        this.reloadAction = reloadAction;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /shopadmin <reload|stats|givebalance|audit|validateprices>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender, args);
            case "givebalance" -> handleGiveBalance(sender, args);
            case "audit" -> handleAudit(sender, args);
            case "validateprices" -> handleValidatePrices(sender);
            default -> sender.sendMessage("Unknown subcommand. Usage: /shopadmin <reload|stats|givebalance|audit|validateprices>");
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("shopplugin.admin.reload")) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to do that.</red>"));
            return;
        }
        try {
            reloadAction.run();
            sender.sendMessage(miniMessage.deserialize(messages.get("reload-success")));
        } catch (Exception e) {
            sender.sendMessage(miniMessage.deserialize(messages.get("reload-failure",
                    java.util.Map.of("reason", String.valueOf(e.getMessage())))));
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shopplugin.admin.stats")) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to do that.</red>"));
            return;
        }
        if (args.length >= 2) {
            String itemId = args[1];
            var stats = statisticsService.getItemStats(itemId);
            sender.sendMessage("=== Stats for " + itemId + " ===");
            sender.sendMessage("Total bought: " + stats.totalBought + " (spent: $" + stats.moneySpent + ")");
            sender.sendMessage("Total sold: " + stats.totalSold + " (earned: $" + stats.moneyEarned + ")");
            return;
        }

        var summary = statisticsService.getSummary();
        sender.sendMessage("=== Shop Summary ===");
        sender.sendMessage("Total transactions: " + summary.totalTransactions);
        sender.sendMessage("Total money spent by players: $" + summary.totalMoneySpent);
        sender.sendMessage("Total money earned by players: $" + summary.totalMoneyEarned);
        sender.sendMessage("Net money removed from economy: $" + summary.totalMoneySpent.subtract(summary.totalMoneyEarned));

        sender.sendMessage("--- Top Bought ---");
        for (var s : statisticsService.getTopBought(5)) {
            sender.sendMessage(s.itemId + ": " + s.totalBought);
        }
        sender.sendMessage("--- Top Sold ---");
        for (var s : statisticsService.getTopSold(5)) {
            sender.sendMessage(s.itemId + ": " + s.totalSold);
        }
    }

    private void handleGiveBalance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shopplugin.admin.givebalance")) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to do that.</red>"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /shopadmin givebalance <player> <amount>");
            return;
        }
        if (!economy.isReady()) {
            sender.sendMessage(miniMessage.deserialize("<red>No economy provider is currently available.</red>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        BigDecimal amount;
        try {
            amount = new BigDecimal(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(miniMessage.deserialize("<red>Invalid amount.</red>"));
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(miniMessage.deserialize("<red>Amount must be positive.</red>"));
            return;
        }
        EconomyOperationResult result = economy.deposit(target, amount);
        if (result.isSuccess()) {
            sender.sendMessage(miniMessage.deserialize("<green>Gave $" + amount + " to " + target.getName() + ".</green>"));
        } else {
            sender.sendMessage(miniMessage.deserialize("<red>Failed: " + result.getErrorMessage() + "</red>"));
        }
    }

    private void handleAudit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shopplugin.admin.audit")) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to do that.</red>"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /shopadmin audit <item>");
            return;
        }
        String itemId = args[1];
        var itemOpt = repository.getItem(itemId);
        if (itemOpt.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize("<red>Unknown item: " + itemId + "</red>"));
            return;
        }
        var stats = statisticsService.getItemStats(itemId);
        var item = itemOpt.get();
        sender.sendMessage("=== Audit: " + item.getId() + " ===");
        sender.sendMessage("Buy price: " + (item.isBuyable() ? "$" + item.getBuyPrice() : "disabled"));
        sender.sendMessage("Sell price: " + (item.isSellable() ? "$" + item.getSellPrice() : "disabled"));
        sender.sendMessage("Total bought: " + stats.totalBought + " | Total sold: " + stats.totalSold);
        sender.sendMessage("Money spent by players: $" + stats.moneySpent + " | Money earned by players: $" + stats.moneyEarned);
        BigDecimal net = stats.moneyEarned.subtract(stats.moneySpent);
        sender.sendMessage("Net player profit from this item: $" + net
                + (net.compareTo(BigDecimal.ZERO) > 0 ? " (players are net earning from this item)" : ""));
    }

    private void handleValidatePrices(CommandSender sender) {
        if (!sender.hasPermission("shopplugin.admin.validateprices")) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to do that.</red>"));
            return;
        }
        List<String> warnings = priceValidationService.validate();
        if (warnings.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize("<green>No pricing issues detected.</green>"));
            return;
        }
        sender.sendMessage(miniMessage.deserialize("<yellow>Found " + warnings.size() + " potential pricing issue(s):</yellow>"));
        for (String warning : warnings) {
            sender.sendMessage(miniMessage.deserialize("<gray>- " + warning + "</gray>"));
        }
    }
}
