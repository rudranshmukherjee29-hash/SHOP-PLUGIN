package net.shopplugin.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Owns the JDBC connection pool for either SQLite (default, zero-config) or
 * MySQL/MariaDB (for larger networks). All schema creation is idempotent
 * (CREATE TABLE IF NOT EXISTS) so startup is safe to run repeatedly.
 *
 * Callers must never run queries on the main server thread; see
 * StatisticsService and StockPersistenceService for how this is wrapped
 * in async tasks.
 */
public final class DatabaseManager {

    private final Logger logger;
    private HikariDataSource dataSource;
    private boolean mysql;

    public DatabaseManager(Logger logger) {
        this.logger = logger;
    }

    public void initSqlite(File dataFolder) {
        this.mysql = false;
        File dbFile = new File(dataFolder, "shopdata.db");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // SQLite is single-writer; keep the pool tiny and serialized.
        config.setPoolName("ShopPlugin-SQLite");
        this.dataSource = new HikariDataSource(config);
        createSchema();
        logger.info("Connected to SQLite database at " + dbFile.getAbsolutePath());
    }

    public void initMysql(String host, int port, String database, String username, String password, boolean useSsl) {
        this.mysql = true;
        HikariConfig config = new HikariConfig();
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl + "&autoReconnect=true&characterEncoding=utf8";
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(6);
        config.setPoolName("ShopPlugin-MySQL");
        config.setConnectionTimeout(10_000);
        this.dataSource = new HikariDataSource(config);
        createSchema();
        logger.info("Connected to MySQL database at " + host + ":" + port + "/" + database);
    }

    private void createSchema() {
        String autoIncrement = mysql ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS shop_transactions (" +
                    "id " + autoIncrement + ", " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "item_id VARCHAR(64) NOT NULL, " +
                    "action VARCHAR(8) NOT NULL, " +
                    "quantity BIGINT NOT NULL, " +
                    "amount DECIMAL(20,4) NOT NULL, " +
                    "timestamp BIGINT NOT NULL" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_item ON shop_transactions(item_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_player ON shop_transactions(player_uuid)");

            stmt.execute("CREATE TABLE IF NOT EXISTS shop_item_stats (" +
                    "item_id VARCHAR(64) PRIMARY KEY, " +
                    "total_bought BIGINT NOT NULL DEFAULT 0, " +
                    "total_sold BIGINT NOT NULL DEFAULT 0, " +
                    "money_spent DECIMAL(20,4) NOT NULL DEFAULT 0, " +
                    "money_earned DECIMAL(20,4) NOT NULL DEFAULT 0, " +
                    "buy_count BIGINT NOT NULL DEFAULT 0, " +
                    "sell_count BIGINT NOT NULL DEFAULT 0" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS shop_stock (" +
                    "item_id VARCHAR(64) PRIMARY KEY, " +
                    "current_stock BIGINT NOT NULL" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS shop_failed_transactions (" +
                    "id " + autoIncrement + ", " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "item_id VARCHAR(64), " +
                    "reason VARCHAR(64) NOT NULL, " +
                    "timestamp BIGINT NOT NULL" +
                    ")");
        } catch (SQLException e) {
            logger.severe("Failed to initialize database schema: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database has not been initialized.");
        }
        return dataSource.getConnection();
    }

    public boolean isMysql() {
        return mysql;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
