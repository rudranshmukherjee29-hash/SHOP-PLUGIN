package net.shopplugin.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed view over config.yml's tunable settings, loaded once per (re)load
 * rather than re-reading the FileConfiguration on every transaction.
 */
public final class ShopSettings {

    private final int globalMaxTransactionSize;
    private final long rateLimitMillis;
    private final boolean requireConfirmationAboveValue;
    private final double confirmationThreshold;
    private final String currencySymbol;
    private final boolean debugLogging;
    private final boolean useMysql;

    public ShopSettings(FileConfiguration config) {
        this.globalMaxTransactionSize = config.getInt("limits.global-max-transaction-size", 6400);
        this.rateLimitMillis = config.getLong("security.rate-limit-millis", 150);
        this.requireConfirmationAboveValue = config.getBoolean("gui.confirm-large-transactions", true);
        this.confirmationThreshold = config.getDouble("gui.confirmation-threshold", 5000.0);
        this.currencySymbol = config.getString("gui.currency-symbol", "$");
        this.debugLogging = config.getBoolean("debug", false);
        this.useMysql = config.getString("database.type", "sqlite").equalsIgnoreCase("mysql");
    }

    public int getGlobalMaxTransactionSize() {
        return globalMaxTransactionSize;
    }

    public long getRateLimitMillis() {
        return rateLimitMillis;
    }

    public boolean isRequireConfirmationAboveValue() {
        return requireConfirmationAboveValue;
    }

    public double getConfirmationThreshold() {
        return confirmationThreshold;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public boolean isUseMysql() {
        return useMysql;
    }
}
