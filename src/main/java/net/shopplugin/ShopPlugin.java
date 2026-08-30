package net.shopplugin;

import net.shopplugin.command.SellAllCommand;
import net.shopplugin.command.SellHandCommand;
import net.shopplugin.command.ShopAdminCommand;
import net.shopplugin.command.ShopCommand;
import net.shopplugin.config.DefaultConversions;
import net.shopplugin.config.GuiMessages;
import net.shopplugin.config.GuiTextConfig;
import net.shopplugin.config.ItemDataLoader;
import net.shopplugin.config.ShopSettings;
import net.shopplugin.economy.EconomyProvider;
import net.shopplugin.economy.VaultEconomyProvider;
import net.shopplugin.gui.ShopGuiRenderer;
import net.shopplugin.integration.ShopPlaceholders;
import net.shopplugin.listener.PlayerCleanupListener;
import net.shopplugin.listener.ShopClickListener;
import net.shopplugin.listener.ShopGuiProtectionListener;
import net.shopplugin.model.ShopItem;
import net.shopplugin.repository.DatabaseManager;
import net.shopplugin.repository.InMemoryShopRepository;
import net.shopplugin.repository.ShopRepository;
import net.shopplugin.security.RateLimiter;
import net.shopplugin.security.ShopItemTagger;
import net.shopplugin.security.TransactionGuard;
import net.shopplugin.service.DatabaseStatisticsService;
import net.shopplugin.service.DefaultTransactionService;
import net.shopplugin.service.InMemoryStockService;
import net.shopplugin.service.PriceValidationService;
import net.shopplugin.service.RestockTask;
import net.shopplugin.service.StatisticsService;
import net.shopplugin.service.StockService;
import net.shopplugin.service.TransactionService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

public final class ShopPlugin extends JavaPlugin {

    private EconomyProvider economyProvider;
    private ShopRepository shopRepository;
    private StockService stockService;
    private StatisticsService statisticsService;
    private DatabaseManager databaseManager;
    private TransactionService transactionService;
    private TransactionGuard transactionGuard;
    private RateLimiter rateLimiter;
    private ShopItemTagger shopItemTagger;
    private ShopGuiRenderer shopGuiRenderer;
    private PriceValidationService priceValidationService;

    private ShopSettings shopSettings;
    private GuiTextConfig guiTextConfig;
    private GuiMessages guiMessages;

    private RestockTask restockTask;

    @Override
    public void onEnable() {
        saveDefaultResource("config.yml");
        saveDefaultResource("prices.yml");
        saveDefaultResource("gui.yml");
        saveDefaultResource("messages.yml");
        saveDefaultResource("limits.yml");
        saveDefaultResource("shops.yml");

        this.shopItemTagger = new ShopItemTagger(this);
        this.transactionGuard = new TransactionGuard();

        // --- Economy (Vault) ---------------------------------------------------
        VaultEconomyProvider vaultProvider = new VaultEconomyProvider(getLogger());
        boolean economyReady = vaultProvider.initialize(getServer());
        this.economyProvider = vaultProvider;
        if (!economyReady) {
            getLogger().severe("==========================================================");
            getLogger().severe(" ShopPlugin could not find a working Vault economy provider.");
            getLogger().severe(" The shop will remain LOADED but all transactions will fail");
            getLogger().severe(" safely (no money or items will be exchanged) until an");
            getLogger().severe(" economy plugin (e.g. EssentialsX) is installed and the");
            getLogger().severe(" server is reloaded or restarted.");
            getLogger().severe("==========================================================");
        } else {
            getLogger().info("Using economy provider: " + economyProvider.getProviderName());
        }

        // --- Repository & catalog -----------------------------------------------
        this.shopRepository = new InMemoryShopRepository();
        reloadCatalog();

        // --- Database ------------------------------------------------------------
        this.databaseManager = new DatabaseManager(getLogger());
        FileConfiguration config = getConfig();
        if (config.getString("database.type", "sqlite").equalsIgnoreCase("mysql")) {
            databaseManager.initMysql(
                    config.getString("database.mysql.host", "localhost"),
                    config.getInt("database.mysql.port", 3306),
                    config.getString("database.mysql.database", "shopplugin"),
                    config.getString("database.mysql.username", "shopplugin"),
                    config.getString("database.mysql.password", ""),
                    config.getBoolean("database.mysql.use-ssl", false)
            );
        } else {
            databaseManager.initSqlite(getDataFolder());
        }

        this.stockService = new InMemoryStockService(shopRepository);
        initializeStockFromCatalog();

        this.statisticsService = new DatabaseStatisticsService(this, databaseManager);

        this.rateLimiter = new RateLimiter(getConfig().getLong("security.rate-limit-millis", 150));

        reloadTypedConfigs();

        this.transactionService = new DefaultTransactionService(
                getLogger(), shopRepository, economyProvider, stockService, statisticsService,
                transactionGuard, rateLimiter, shopSettings
        );

        this.priceValidationService = new PriceValidationService(shopRepository, DefaultConversions.get());

        this.shopGuiRenderer = new ShopGuiRenderer(shopRepository, economyProvider, stockService, shopItemTagger, guiTextConfig);

        // --- Listeners -------------------------------------------------------
        getServer().getPluginManager().registerEvents(new ShopGuiProtectionListener(shopItemTagger), this);
        getServer().getPluginManager().registerEvents(
                new ShopClickListener(getLogger(), shopRepository, shopGuiRenderer, transactionService,
                        shopItemTagger, guiTextConfig, guiMessages, economyProvider),
                this);
        getServer().getPluginManager().registerEvents(new PlayerCleanupListener(transactionGuard, rateLimiter), this);

        // --- Commands ----------------------------------------------------------
        registerCommand("shop", new ShopCommand(shopGuiRenderer, shopRepository));
        registerCommand("sellall", new SellAllCommand(transactionService, guiMessages));
        registerCommand("sellinventory", new SellAllCommand(transactionService, guiMessages));
        registerCommand("sellhand", new SellHandCommand(transactionService, guiMessages));
        registerCommand("shopadmin", new ShopAdminCommand(shopRepository, statisticsService, economyProvider,
                priceValidationService, this::reload, guiMessages));

        // --- Restock task --------------------------------------------------------
        boolean stockEnabled = loadLimitsConfig().getBoolean("stock.enabled", true);
        long restockIntervalTicks = loadLimitsConfig().getLong("stock.restock-check-interval-seconds", 60) * 20L;
        this.restockTask = new RestockTask(shopRepository, stockService, getLogger(), stockEnabled);
        restockTask.runTaskTimer(this, restockIntervalTicks, restockIntervalTicks);

        // --- Optional integrations ------------------------------------------
        setupPlaceholderApi();

        // Run price validation once at startup and log warnings (does not
        // block startup; this is diagnostic only, per spec section 11).
        List<String> warnings = priceValidationService.validate();
        if (!warnings.isEmpty()) {
            getLogger().warning("Price validation found " + warnings.size() + " potential issue(s) in prices.yml:");
            for (String warning : warnings) {
                getLogger().warning(" - " + warning);
            }
            getLogger().warning("Run /shopadmin validateprices in-game for details, or review prices.yml.");
        }

        getLogger().info("ShopPlugin enabled with " + shopRepository.getAllItems().size() + " catalog items.");
    }

    @Override
    public void onDisable() {
        if (restockTask != null) {
            restockTask.cancel();
        }
        if (statisticsService != null) {
            statisticsService.flush();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    /**
     * Fully reloads configuration and catalog. Rebuilds the repository
     * snapshot atomically (see InMemoryShopRepository#reload) so in-flight
     * reads never see a half-updated catalog.
     */
    public void reload() {
        reloadConfig();
        saveDefaultResource("config.yml");
        reloadCatalog();
        reloadTypedConfigs();
        initializeStockFromCatalog();
        getLogger().info("ShopPlugin configuration reloaded.");
    }

    private void reloadCatalog() {
        FileConfiguration prices = loadResourceConfig("prices.yml");
        ItemDataLoader loader = new ItemDataLoader(getLogger());
        List<ShopItem> items = loader.load(prices);
        shopRepository.reload(items);
    }

    private void reloadTypedConfigs() {
        this.shopSettings = new ShopSettings(getConfig());
        this.guiTextConfig = new GuiTextConfig(loadResourceConfig("gui.yml"));
        this.guiMessages = new GuiMessages(loadResourceConfig("messages.yml"));
    }

    private void initializeStockFromCatalog() {
        for (ShopItem item : shopRepository.getAllItems()) {
            if (item.isLimitedStock() && stockService.getCurrentStock(item.getId()) < 0) {
                stockService.setStock(item.getId(), item.getMaxStock() >= 0 ? item.getMaxStock() : 0);
            }
        }
    }

    private FileConfiguration loadLimitsConfig() {
        return loadResourceConfig("limits.yml");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is not declared in plugin.yml; skipping registration.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void setupPlaceholderApi() {
        if (!getConfig().getBoolean("integrations.placeholderapi", true)) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found; placeholder support disabled (this is optional).");
            return;
        }
        try {
            new ShopPlaceholders(economyProvider, statisticsService).register();
            getLogger().info("Registered PlaceholderAPI expansion.");
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion", t);
        }
    }

    private void saveDefaultResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    /**
     * Loads a config file from the plugin's data folder (creating it from
     * the bundled default first if it doesn't exist yet), rather than
     * reading the jar resource directly, so admin edits on disk are respected.
     */
    private FileConfiguration loadResourceConfig(String name) {
        saveDefaultResource(name);
        File file = new File(getDataFolder(), name);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Layer in defaults from the bundled resource so newly-added default
        // keys (from a plugin update) are available even if the admin's file
        // on disk predates them, without overwriting their customizations.
        try (InputStream defaultStream = getResource(name)) {
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                config.setDefaults(defaultConfig);
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not load bundled defaults for " + name, e);
        }

        return config;
    }
}
