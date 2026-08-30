package net.shopplugin.economy;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;

/**
 * Abstraction over the underlying economy so the rest of the plugin never
 * talks to Vault directly. This keeps a single, auditable choke point for
 * every balance mutation in the plugin.
 */
public interface EconomyProvider {

    boolean isReady();

    String getProviderName();

    BigDecimal getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, BigDecimal amount);

    /**
     * Withdraws the exact amount. Returns true only if the underlying
     * economy plugin confirms success. Never assume success without
     * checking the return value.
     */
    EconomyOperationResult withdraw(OfflinePlayer player, BigDecimal amount);

    /**
     * Deposits the exact amount. Returns true only if the underlying
     * economy plugin confirms success.
     */
    EconomyOperationResult deposit(OfflinePlayer player, BigDecimal amount);

    /**
     * Number of decimal places the underlying economy supports, used to
     * round prices consistently instead of leaking floating-point dust.
     */
    int getFractionalDigits();
}
