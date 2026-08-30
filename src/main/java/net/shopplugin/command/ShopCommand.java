package net.shopplugin.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.shopplugin.gui.ShopGuiRenderer;
import net.shopplugin.model.ShopCategory;
import net.shopplugin.repository.ShopRepository;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopGuiRenderer renderer;
    private final ShopRepository repository;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ShopCommand(ShopGuiRenderer renderer, ShopRepository repository) {
        this.renderer = renderer;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!player.hasPermission("shopplugin.shop")) {
            player.sendMessage(miniMessage.deserialize("<red>You do not have permission to use the shop.</red>"));
            return true;
        }

        if (args.length == 0) {
            player.openInventory(renderer.renderMainMenu());
            return true;
        }

        if (args[0].equalsIgnoreCase("search")) {
            if (args.length < 2) {
                player.sendMessage(miniMessage.deserialize("<red>Usage: /shop search <item></red>"));
                return true;
            }
            String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            player.openInventory(renderer.renderSearch(query, 0, player));
            return true;
        }

        String categoryArg = args[0].toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            ShopCategory category = ShopCategory.valueOf(categoryArg);
            player.openInventory(renderer.renderCategory(category, 0, player));
        } catch (IllegalArgumentException e) {
            player.sendMessage(miniMessage.deserialize("<red>Unknown category. Available: "
                    + String.join(", ", availableCategoryNames()) + "</red>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(availableCategoryNames());
            options.add("search");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> availableCategoryNames() {
        List<String> names = new ArrayList<>();
        for (ShopCategory category : ShopCategory.values()) {
            names.add(category.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
