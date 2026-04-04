package me.panhaskins.itemLimiter.utils.database;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Manages database connections for MySQL/MariaDB and SQLite.
 * <p>
 * Usage example:
 * <pre>{@code
 * // In your plugin main or manager class:
 * ConfigurationSection dbConfig = getConfig().getConfigurationSection("database");
 * DatabaseManager dbManager = new DatabaseManager(this, dbConfig);
 *
 * // Acquire connection when needed (automatically closed after use):
 * try (Connection conn = dbManager.openConnection()) {
 *     // perform queries
 * }
 * }</pre>
 *
 * @throws DatabaseConfigurationException if configuration is invalid or driver missing
 */
public class DatabaseManager {
    public enum DbType { MYSQL, SQLITE }

    private final String url;
    private final String user;
    private final String pass;
    private final DbType type;

    /**
     * Initializes DatabaseManager from plugin config.
     *
     * @param plugin Bukkit plugin instance
     * @param config ConfigurationSection with "type" = mysql/sqlite and respective sections
     * @throws DatabaseConfigurationException if config is invalid
     */
    public DatabaseManager(Plugin plugin, ConfigurationSection config) {
        Objects.requireNonNull(plugin, "Plugin cannot be null");
        Objects.requireNonNull(config, "ConfigurationSection cannot be null");

        String typeStr = config.getString("type", "sqlite").toUpperCase(Locale.ROOT);
        this.type = DbType.valueOf(typeStr);

        if (type == DbType.MYSQL) {
            ConfigurationSection mysql = config.getConfigurationSection("mysql");
            if (mysql == null) throw new DatabaseConfigurationException("Missing 'mysql' section");

            String host = mysql.getString("host");
            String database = mysql.getString("database");
            this.user = mysql.getString("username");
            this.pass = mysql.getString("password");
            long timeoutMs = Duration.ofSeconds(10).toMillis();

            this.url = String.format(
                    "jdbc:mysql://%s/%s?autoReconnect=true&connectTimeout=%d",
                    host, database, timeoutMs
            );
        } else {
            ConfigurationSection sqlite = config.getConfigurationSection("sqlite");
            if (sqlite == null) throw new DatabaseConfigurationException("Missing 'sqlite' section");

            String fileName = sqlite.getString("file");
            if (fileName == null || fileName.isEmpty()) {
                throw new DatabaseConfigurationException("SQLite 'file' must be specified");
            }

            // Ensure plugin data folder exists
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new DatabaseConfigurationException("Could not create plugin data folder: " + dataFolder.getAbsolutePath());
            }

            // Place DB file relative to plugin folder, handling nested paths
            File dbFile = new File(dataFolder, fileName);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                throw new DatabaseConfigurationException("Could not create directories for SQLite DB: " + parentDir.getAbsolutePath());
            }

            this.user = "";
            this.pass = "";
            this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        }

        loadDriver();
    }

    private void loadDriver() {
        try {
            if (type == DbType.MYSQL) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } else {
                Class.forName("org.sqlite.JDBC");
            }
        } catch (ClassNotFoundException e) {
            throw new DatabaseConfigurationException("JDBC Driver not found for " + type, e);
        }
    }
    public String getType() {
        return type.name();
    }

    /**
     * Opens a new Connection. Caller must close.
     *
     * @return fresh JDBC Connection
     * @throws DatabaseConnectionException if connection fails
     */
    public Connection openConnection() {
        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Unable to connect to " + url, e);
        }
    }

    public void close() {
        // no persistent connections to close in current implementation
    }

    /** Exception for configuration errors. */
    public static class DatabaseConfigurationException extends RuntimeException {
        public DatabaseConfigurationException(String message) { super(message); }
        public DatabaseConfigurationException(String message, Throwable cause) { super(message, cause); }
    }

    /** Exception for connection errors. */
    public static class DatabaseConnectionException extends RuntimeException {
        public DatabaseConnectionException(String message, Throwable cause) { super(message, cause); }
    }
}

