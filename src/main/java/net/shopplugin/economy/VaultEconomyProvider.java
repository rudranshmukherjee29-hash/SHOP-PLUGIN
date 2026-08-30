package net.shopplugin.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Logger;

/**
 * Vault-backed implementation. This class is the single point of contact
 * with Vault; nothing else in the plugin should import net.milkbowl.vault
 * directly. Every mutating call inspects the {@link EconomyResponse} result
 * type instead of assuming the call succeeded because it didn't throw.
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private final Logger logger;
    private Economy economy;
    private boolean ready = false;

    public VaultEconomyProvider(Logger logger) {
        this.logger = logger;
    }

    /**
     * Attempts to bind to the Vault Economy service. Returns true on success.
     * Must be called on startup and safely handles Vault or the underlying
     * economy plugin being absent.
     */
    public boolean initialize(Server server) {
        if (server.getPluginManager().getPlugin("Vault") == null) {
            logger.severe("Vault is not installed. The shop cannot operate without an economy provider.");
            ready = false;
            return false;
        }

        ServicesManager servicesManager = server.getServicesManager();
        RegisteredServiceProvider<Economy> rsp = servicesManager.getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            logger.severe("No Vault Economy provider is registered (is EssentialsX or another economy plugin installed?).");
            ready = false;
            return false;
        }

        this.economy = rsp.getProvider();
        this.ready = true;
        logger.info("Hooked into economy provider: " + economy.getName());
        return true;
    }

    @Override
    public boolean isReady() {
        return ready && economy != null;
    }

    @Override
    public String getProviderName() {
        return isReady() ? economy.getName() : "NONE";
    }

    @Override
    public BigDecimal getBalance(OfflinePlayer player) {
        if (!isReady()) {
            return BigDecimal.ZERO;
        }
        double bal = economy.getBalance(player);
        return round(BigDecimal.valueOf(bal));
    }

    @Override
    public boolean has(OfflinePlayer player, BigDecimal amount) {
        if (!isReady()) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        return economy.has(player, amount.doubleValue());
    }

    @Override
    public EconomyOperationResult withdraw(OfflinePlayer player, BigDecimal amount) {
        if (!isReady()) {
            return EconomyOperationResult.failed("Economy provider is not available.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return EconomyOperationResult.failed("Invalid withdrawal amount.");
        }
        // Re-check funds immediately before withdrawing to reduce (not eliminate)
        // the window for a stale-balance race; the caller is still expected to
        // hold a per-player transaction lock around this call.
        if (!economy.has(player, amount.doubleValue())) {
            return EconomyOperationResult.failed("Insufficient funds at withdrawal time.");
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount.doubleValue());
        if (response == null) {
            return EconomyOperationResult.failed("Economy provider returned no response.");
        }
        if (response.transactionSuccess()) {
            return EconomyOperationResult.ok();
        }
        return EconomyOperationResult.failed(response.errorMessage != null ? response.errorMessage : "Unknown withdrawal failure.");
    }

    @Override
    public EconomyOperationResult deposit(OfflinePlayer player, BigDecimal amount) {
        if (!isReady()) {
            return EconomyOperationResult.failed("Economy provider is not available.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return EconomyOperationResult.failed("Invalid deposit amount.");
        }
        EconomyResponse response = economy.depositPlayer(player, amount.doubleValue());
        if (response == null) {
            return EconomyOperationResult.failed("Economy provider returned no response.");
        }
        if (response.transactionSuccess()) {
            return EconomyOperationResult.ok();
        }
        return EconomyOperationResult.failed(response.errorMessage != null ? response.errorMessage : "Unknown deposit failure.");
    }

    @Override
    public int getFractionalDigits() {
        if (!isReady()) {
            return 2;
        }
        int digits = economy.fractionalDigits();
        return digits < 0 ? 2 : digits;
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(getFractionalDigits(), RoundingMode.HALF_UP);
    }
}
