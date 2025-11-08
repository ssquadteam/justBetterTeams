package eu.kotori.justTeams.storage;

import eu.kotori.justTeams.JustTeams;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import eu.kotori.justTeams.storage.IDataStorage;
import eu.kotori.justTeams.team.BlacklistedPlayer;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class DatabaseStorage
implements IDataStorage {
    private final JustTeams plugin;
    private HikariDataSource hikari;
    private final String storageType;
    private static final String HEARTBEAT_SQL_MYSQL = "INSERT INTO donut_servers (server_name, last_heartbeat) VALUES (?, NOW()) ON DUPLICATE KEY UPDATE last_heartbeat = NOW()";
    private static final String HEARTBEAT_SQL_H2 = "MERGE INTO donut_servers (server_name, last_heartbeat) KEY(server_name) VALUES (?, NOW())";
    private volatile PreparedStatement heartbeatStatement;
    private final Object heartbeatLock = new Object();

    public DatabaseStorage(JustTeams plugin) {
        this.plugin = plugin;
        this.storageType = plugin.getConfig().getString("storage.type", "h2").toLowerCase();
    }

    @Override
    public boolean init() {
        boolean bl;
        block48: {
            boolean useSSL;
            String password;
            String username;
            String database;
            int port;
            String host;
            boolean useMySQL;
            HikariConfig config = new HikariConfig();
            config.setPoolName("justTeams-Pool");
            int maxPoolSize = this.plugin.getConfig().getInt("storage.connection_pool.max_size", 8);
            int minIdle = this.plugin.getConfig().getInt("storage.connection_pool.min_idle", 2);
            long idleTimeout = this.plugin.getConfig().getLong("storage.connection_pool.idle_timeout", 300000L);
            long maxLifetime = this.plugin.getConfig().getLong("storage.connection_pool.max_lifetime", 1800000L);
            long connectionTimeout = this.plugin.getConfig().getLong("storage.connection_pool.connection_timeout", 20000L);
            long validationTimeout = this.plugin.getConfig().getLong("storage.connection_pool.validation_timeout", 3000L);
            long leakDetectionThreshold = this.plugin.getConfig().getLong("storage.connection_pool.leak_detection_threshold", 0L);
            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(minIdle);
            config.setIdleTimeout(idleTimeout);
            config.setMaxLifetime(maxLifetime);
            config.setConnectionTimeout(connectionTimeout);
            config.setValidationTimeout(validationTimeout);
            config.setConnectionTestQuery("/* ping */ SELECT 1");
            config.setInitializationFailTimeout(connectionTimeout);
            config.setIsolateInternalQueries(false);
            config.setAllowPoolSuspension(false);
            config.setReadOnly(false);
            config.setRegisterMbeans(false);
            config.setKeepaliveTime(300000L);
            config.setMaxLifetime(1800000L);
            config.setIdleTimeout(600000L);
            if (leakDetectionThreshold > 0L) {
                config.setLeakDetectionThreshold(leakDetectionThreshold);
            } else if (this.plugin.getConfig().getBoolean("storage.connection_pool.enable_leak_detection", false)) {
                config.setLeakDetectionThreshold(60000L);
            }
            boolean bl2 = useMySQL = this.storageType.equals("mysql") || this.storageType.equals("mariadb");
            if (useMySQL) {
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                config.addDataSourceProperty("useLocalSessionState", "true");
                config.addDataSourceProperty("rewriteBatchedStatements", "true");
                config.addDataSourceProperty("cacheResultSetMetadata", "true");
                config.addDataSourceProperty("cacheServerConfiguration", "true");
                config.addDataSourceProperty("elideSetAutoCommits", "true");
                config.addDataSourceProperty("maintainTimeStats", "false");
                config.addDataSourceProperty("characterEncoding", "UTF-8");
                config.addDataSourceProperty("useUnicode", "true");
                config.addDataSourceProperty("tcpKeepAlive", "true");
            }
            if (useMySQL) {
                this.plugin.getLogger().info("Connecting to MySQL/MariaDB database...");
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                host = this.plugin.getConfig().getString("storage.mysql.host", "localhost");
                port = this.plugin.getConfig().getInt("storage.mysql.port", 3306);
                database = this.plugin.getConfig().getString("storage.mysql.database", "teams");
                username = this.plugin.getConfig().getString("storage.mysql.username", "root");
                password = this.plugin.getConfig().getString("storage.mysql.password", "");
                useSSL = this.plugin.getConfig().getBoolean("storage.mysql.use_ssl", false);
                String charset = this.plugin.getConfig().getString("storage.mysql.character_encoding", "utf8mb4");
                String collation = this.plugin.getConfig().getString("storage.mysql.collation", "utf8mb4_unicode_ci");
                if (host == null || host.isEmpty()) {
                    this.plugin.getLogger().severe("MySQL host is not configured! Please set storage.mysql.host in config.yml");
                    return false;
                }
                if (database == null || database.isEmpty()) {
                    this.plugin.getLogger().severe("MySQL database is not configured! Please set storage.mysql.database in config.yml");
                    return false;
                }
                String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, port, database, useSSL);
                config.setJdbcUrl(jdbcUrl);
                config.setUsername(username);
                config.setPassword(password);
                int mysqlConnectionTimeout = this.plugin.getConfig().getInt("storage.mysql.connection_timeout", 30000);
                config.setConnectionTimeout(mysqlConnectionTimeout);
                config.setValidationTimeout(5000L);
                config.setInitializationFailTimeout(mysqlConnectionTimeout);
                config.setMaximumPoolSize(Math.max(maxPoolSize, 10));
                config.setMinimumIdle(0);
                this.plugin.getLogger().info("MySQL connection configured:");
                this.plugin.getLogger().info("  Host: " + host + ":" + port);
                this.plugin.getLogger().info("  Database: " + database);
                this.plugin.getLogger().info("  Username: " + username);
                this.plugin.getLogger().info("  SSL: " + useSSL);
                this.plugin.getLogger().info("  Connection timeout: " + mysqlConnectionTimeout + "ms");
            } else {
                this.plugin.getLogger().info("MySQL not enabled or configured. Falling back to H2 file-based storage.");
                File dataFolder = new File(this.plugin.getDataFolder(), "data");
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }
                config.setDriverClassName("eu.kotori.justTeams.libs.h2.Driver");
                String h2Url = "jdbc:h2:" + dataFolder.getAbsolutePath().replace("\\", "/") + "/teams;AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE";
                config.setJdbcUrl(h2Url);
                this.plugin.getLogger().info("H2 JDBC URL: " + h2Url);
                config.setConnectionTimeout(8000L);
                config.setValidationTimeout(2000L);
                config.setMaximumPoolSize(3);
                config.setMinimumIdle(1);
                config.setIdleTimeout(300000L);
                config.setMaxLifetime(1800000L);
                config.getDataSourceProperties().clear();
            }
            if (useMySQL) {
                try {
                    this.plugin.getLogger().info("Pre-testing direct JDBC connection...");
                    host = this.plugin.getConfig().getString("storage.mysql.host", "localhost");
                    port = this.plugin.getConfig().getInt("storage.mysql.port", 3306);
                    database = this.plugin.getConfig().getString("storage.mysql.database", "teams");
                    username = this.plugin.getConfig().getString("storage.mysql.username", "root");
                    password = this.plugin.getConfig().getString("storage.mysql.password", "");
                    useSSL = this.plugin.getConfig().getBoolean("storage.mysql.use_ssl", false);
                    String testUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=10000", host, port, database, useSSL);
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    try (Connection testConn = DriverManager.getConnection(testUrl, username, password);){
                        this.plugin.getLogger().info("\u2713 Direct JDBC connection successful!");
                        try (Statement stmt = testConn.createStatement();
                             ResultSet rs = stmt.executeQuery("SELECT 1");){
                            if (rs.next()) {
                                this.plugin.getLogger().info("\u2713 Database query test successful!");
                            }
                        }
                    }
                } catch (ClassNotFoundException e) {
                    this.plugin.getLogger().severe("\u2717 MySQL JDBC Driver not found! This is a critical error.");
                    this.plugin.getLogger().severe("The MySQL driver should be shaded into the plugin JAR.");
                    return false;
                } catch (SQLException e) {
                    this.plugin.getLogger().severe("\u2717 Direct JDBC connection failed!");
                    this.plugin.getLogger().severe("Error: " + e.getMessage());
                    this.plugin.getLogger().severe("SQLState: " + e.getSQLState());
                    this.plugin.getLogger().severe("This means MySQL is not accessible. Fix this before continuing.");
                    return false;
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Pre-test connection check failed: " + e.getMessage());
                    this.plugin.getLogger().warning("Continuing with HikariCP initialization...");
                }
            }
            this.plugin.getLogger().info("Attempting to initialize HikariCP connection pool...");
            long startTime = System.currentTimeMillis();
            this.hikari = new HikariDataSource(config);
            this.plugin.getLogger().info("HikariCP pool created successfully");
            this.plugin.getLogger().info("Testing database connection (this may take up to " + config.getConnectionTimeout() / 1000L + " seconds)...");
            try (Connection testConn = this.hikari.getConnection()) {
                long connectionTime = System.currentTimeMillis() - startTime;
                this.plugin.getLogger().info("Database connection test successful! (took " + connectionTime + "ms)");
                DatabaseMetaData metaData = testConn.getMetaData();
                this.plugin.getLogger().info("Connected to: " + metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion());
                this.plugin.getLogger().info("JDBC URL: " + metaData.getURL());
                this.runMigrationsAndSchemaChecks();
                this.plugin.getLogger().info("Database initialization completed successfully!");
                bl = true;
            } catch (Exception e) {
                this.plugin.getLogger().severe("============================================");
                this.plugin.getLogger().severe("DATABASE CONNECTION FAILED!");
                this.plugin.getLogger().severe("============================================");
                this.plugin.getLogger().severe("Storage type: " + this.storageType);
                this.plugin.getLogger().severe("Error type: " + e.getClass().getSimpleName());
                this.plugin.getLogger().severe("Error message: " + e.getMessage());
                if (useMySQL) {
                    this.plugin.getLogger().severe("");
                    this.plugin.getLogger().severe("Troubleshooting steps:");
                    this.plugin.getLogger().severe("1. Verify MySQL server is running at " + this.plugin.getConfig().getString("storage.mysql.host") + ":" + this.plugin.getConfig().getInt("storage.mysql.port"));
                    this.plugin.getLogger().severe("2. Verify database '" + this.plugin.getConfig().getString("storage.mysql.database") + "' exists");
                    this.plugin.getLogger().severe("3. Verify username and password are correct");
                    this.plugin.getLogger().severe("4. Check firewall allows connection to MySQL port");
                    this.plugin.getLogger().severe("5. Verify MySQL user has proper permissions (GRANT ALL on database)");
                    this.plugin.getLogger().severe("");
                    this.plugin.getLogger().severe("To use H2 (local file storage) instead, change:");
                    this.plugin.getLogger().severe("  storage.type: \"h2\"");
                    this.plugin.getLogger().severe("in config.yml");
                }
                this.plugin.getLogger().severe("============================================");
                if (this.hikari != null && !this.hikari.isClosed()) {
                    try {
                        this.hikari.close();
                    } catch (Exception cleanup) {
                        this.plugin.getLogger().warning("Error during cleanup: " + cleanup.getMessage());
                    }
                }
                if (useMySQL) {
                    this.plugin.getLogger().severe("MySQL connection failed. Plugin will NOT start.");
                    this.plugin.getLogger().severe("Fix the database configuration or switch to H2 storage.");
                    return false;
                }
                if (!this.storageType.equals("mysql")) {
                    return this.tryMinimalH2Configuration();
                }
                return false;
            }
        }
        return bl;
    }

    private void runMigrationsAndSchemaChecks() throws SQLException {
        this.plugin.getLogger().info("Verifying database schema...");
        try (Connection conn = this.getConnection();){
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement();){
                if (!this.storageType.equals("mysql")) {
                    try {
                        stmt.execute("SET MODE MySQL");
                        this.plugin.getLogger().info("H2 MySQL compatibility mode enabled");
                    } catch (SQLException e) {
                        this.plugin.getLogger().info("Could not set MySQL mode (not critical): " + e.getMessage());
                    }
                    try {
                        stmt.execute("SET IGNORECASE TRUE");
                        this.plugin.getLogger().info("H2 ignore case mode enabled");
                    } catch (SQLException e) {
                        this.plugin.getLogger().info("Could not set ignore case mode (not critical): " + e.getMessage());
                    }
                }
                if ("mysql".equals(this.storageType)) {
                    this.createMySQLTables(stmt);
                } else {
                    this.createH2Tables(stmt);
                }
                try {
                    stmt.execute("ALTER TABLE `donut_teams` ADD COLUMN `is_public` BOOLEAN DEFAULT FALSE");
                    this.plugin.getLogger().info("Added is_public column to donut_teams table");
                } catch (SQLException e) {
                    if (e.getMessage().contains("Duplicate column name") || e.getMessage().contains("already exists") || e.getMessage().contains("duplicate")) {
                        if (this.plugin.getConfigManager().isDebugEnabled()) {
                            this.plugin.getLogger().info("is_public column already exists in donut_teams table");
                        }
                    }
                    this.plugin.getLogger().warning("Could not add is_public column: " + e.getMessage());
                }
                try {
                    if ("mysql".equals(this.storageType)) {
                        stmt.execute("ALTER TABLE `donut_cross_server_updates` ADD COLUMN `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                    } else {
                        stmt.execute("ALTER TABLE `donut_cross_server_updates` ADD COLUMN `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                    }
                    this.plugin.getLogger().info("Added created_at column to donut_cross_server_updates table");
                } catch (SQLException e) {
                    if (e.getMessage().contains("Duplicate column name") || e.getMessage().contains("already exists") || e.getMessage().contains("duplicate")) {
                        if (this.plugin.getConfigManager().isDebugEnabled()) {
                            this.plugin.getLogger().info("created_at column already exists in donut_cross_server_updates table");
                        }
                    }
                    this.plugin.getLogger().warning("Could not add created_at column to donut_cross_server_updates: " + e.getMessage());
                }
                try {
                    if ("mysql".equals(this.storageType)) {
                        stmt.execute("ALTER TABLE `donut_cross_server_messages` ADD COLUMN `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                    } else {
                        stmt.execute("ALTER TABLE `donut_cross_server_messages` ADD COLUMN `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                    }
                    this.plugin.getLogger().info("Added created_at column to donut_cross_server_messages table");
                } catch (SQLException e) {
                    if (e.getMessage().contains("Duplicate column name") || e.getMessage().contains("already exists") || e.getMessage().contains("duplicate")) {
                        if (this.plugin.getConfigManager().isDebugEnabled()) {
                            this.plugin.getLogger().info("created_at column already exists in donut_cross_server_messages table");
                        }
                    }
                    this.plugin.getLogger().warning("Could not add created_at column to donut_cross_server_messages: " + e.getMessage());
                }
                try {
                    stmt.execute("ALTER TABLE `donut_teams` ADD COLUMN `alias` VARCHAR(32) DEFAULT NULL");
                    this.plugin.getLogger().info("Added alias column to donut_teams table");
                } catch (SQLException e) {
                    if (e.getMessage().contains("Duplicate column name") || e.getMessage().contains("already exists") || e.getMessage().contains("duplicate")) {
                        if (this.plugin.getConfigManager().isDebugEnabled()) {
                            this.plugin.getLogger().info("alias column already exists in donut_teams table");
                        }
                    }
                    this.plugin.getLogger().warning("Could not add alias column to donut_teams: " + e.getMessage());
                }
                this.plugin.getLogger().info("Database schema verified successfully.");
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error during database schema setup: " + e.getMessage());
            throw e;
        }
    }

    private void createTable(Statement stmt, String tableName, String sql) {
        try {
            stmt.execute(sql);
            this.plugin.getLogger().info("\u2713 Table " + tableName + " verified/created successfully");
        } catch (SQLException e) {
            this.plugin.getLogger().warning("\u2717 Failed to create table " + tableName + ": " + e.getMessage());
            throw new RuntimeException("Failed to create table " + tableName, e);
        }
    }

    private void createIndex(Statement stmt, String indexName, String tableName, String columns) {
        try {
            if ("mysql".equals(this.storageType)) {
                stmt.execute("ALTER TABLE " + tableName + " ADD INDEX " + indexName + " (" + columns + ")");
            } else {
                stmt.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + " (" + columns + ")");
            }
            this.plugin.getLogger().info("\u2713 Index " + indexName + " created successfully");
        } catch (SQLException e) {
            this.plugin.getLogger().info("Note: Could not create index " + indexName + " (may already exist): " + e.getMessage());
        }
    }

    private void createUniqueIndex(Statement stmt, String indexName, String tableName, String columns) {
        try {
            if ("mysql".equals(this.storageType)) {
                stmt.execute("ALTER TABLE " + tableName + " ADD UNIQUE INDEX " + indexName + " (" + columns + ")");
            } else {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + " (" + columns + ")");
            }
            this.plugin.getLogger().info("\u2713 Unique index " + indexName + " created successfully");
        } catch (SQLException e) {
            this.plugin.getLogger().info("Note: Could not create unique index " + indexName + " (may already exist): " + e.getMessage());
        }
    }

    private void createMySQLTables(Statement stmt) throws SQLException {
        this.createTable(stmt, "donut_teams", "CREATE TABLE IF NOT EXISTS `donut_teams` (`id` INT AUTO_INCREMENT, `name` VARCHAR(16) NOT NULL UNIQUE, `tag` VARCHAR(6) NOT NULL, `owner_uuid` VARCHAR(36) NOT NULL, `home_location` VARCHAR(255), `home_server` VARCHAR(255), `pvp_enabled` BOOLEAN DEFAULT TRUE, `is_public` BOOLEAN DEFAULT FALSE, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, `description` VARCHAR(64) DEFAULT NULL, `balance` DOUBLE DEFAULT 0.0, `kills` INT DEFAULT 0, `deaths` INT DEFAULT 0, PRIMARY KEY (`id`))");
        this.createTable(stmt, "donut_team_members", "CREATE TABLE IF NOT EXISTS `donut_team_members` (`player_uuid` VARCHAR(36) NOT NULL, `team_id` INT NOT NULL, `role` VARCHAR(16) NOT NULL, `join_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, `can_withdraw` BOOLEAN DEFAULT FALSE, `can_use_enderchest` BOOLEAN DEFAULT TRUE, `can_set_home` BOOLEAN DEFAULT FALSE, `can_use_home` BOOLEAN DEFAULT TRUE, PRIMARY KEY (`player_uuid`), FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_team_enderchest", "CREATE TABLE IF NOT EXISTS `donut_team_enderchest` (`team_id` INT NOT NULL, `inventory_data` TEXT, PRIMARY KEY (`team_id`), FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_pending_teleports", "CREATE TABLE IF NOT EXISTS `donut_pending_teleports` (`player_uuid` VARCHAR(36) NOT NULL, `destination_server` VARCHAR(255) NOT NULL, `destination_location` VARCHAR(255) NOT NULL, `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`player_uuid`))");
        this.createTable(stmt, "donut_servers", "CREATE TABLE IF NOT EXISTS `donut_servers` (`server_name` VARCHAR(64) PRIMARY KEY, `last_heartbeat` TIMESTAMP NOT NULL)");
        this.createTable(stmt, "donut_team_locks", "CREATE TABLE IF NOT EXISTS `donut_team_locks` (`team_id` INT PRIMARY KEY, `server_identifier` VARCHAR(255) NOT NULL, `lock_time` TIMESTAMP NOT NULL, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_cross_server_updates", "CREATE TABLE IF NOT EXISTS `donut_cross_server_updates` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `update_type` VARCHAR(50) NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `server_name` VARCHAR(64) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_cross_server_messages", "CREATE TABLE IF NOT EXISTS `donut_cross_server_messages` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `message` TEXT NOT NULL, `server_name` VARCHAR(64) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_team_homes", "CREATE TABLE IF NOT EXISTS `donut_team_homes` (`team_id` INT PRIMARY KEY, `location` VARCHAR(255) NOT NULL, `server_name` VARCHAR(64) NOT NULL, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_team_warps", "CREATE TABLE IF NOT EXISTS `donut_team_warps` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `warp_name` VARCHAR(32) NOT NULL, `location` VARCHAR(255) NOT NULL, `server_name` VARCHAR(64) NOT NULL, `password` VARCHAR(64), `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY `team_warp` (`team_id`, `warp_name`), FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_join_requests", "CREATE TABLE IF NOT EXISTS `donut_join_requests` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY `team_player_request` (`team_id`, `player_uuid`), FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_ender_chest_locks", "CREATE TABLE IF NOT EXISTS `donut_ender_chest_locks` (`team_id` INT PRIMARY KEY, `server_identifier` VARCHAR(255) NOT NULL, `lock_time` TIMESTAMP NOT NULL, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_player_cache", "CREATE TABLE IF NOT EXISTS `donut_player_cache` (`player_uuid` VARCHAR(36) PRIMARY KEY, `player_name` VARCHAR(16) NOT NULL, `last_seen` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX `idx_player_name` (`player_name`))");
        this.createTable(stmt, "donut_team_invites", "CREATE TABLE IF NOT EXISTS `donut_team_invites` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `inviter_uuid` VARCHAR(36) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY `team_player_invite` (`team_id`, `player_uuid`), FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_team_blacklist", "CREATE TABLE IF NOT EXISTS `donut_team_blacklist` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `player_name` VARCHAR(16) NOT NULL, `reason` TEXT, `blacklisted_by_uuid` VARCHAR(36) NOT NULL, `blacklisted_by_name` VARCHAR(16) NOT NULL, `blacklisted_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY `team_player_blacklist` (`team_id`, `player_uuid`), FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_player_sessions", "CREATE TABLE IF NOT EXISTS `donut_player_sessions` (`player_uuid` VARCHAR(36) PRIMARY KEY, `server_name` VARCHAR(255) NOT NULL, `last_seen` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX `idx_server_name` (`server_name`), INDEX `idx_last_seen` (`last_seen`))");
        this.createTable(stmt, "donut_server_aliases", "CREATE TABLE IF NOT EXISTS `donut_server_aliases` (`server_name` VARCHAR(255) PRIMARY KEY, `alias` VARCHAR(64) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
        this.createTable(stmt, "donut_team_rename_cooldowns", "CREATE TABLE IF NOT EXISTS `donut_team_rename_cooldowns` (`team_id` INT PRIMARY KEY, `last_rename` TIMESTAMP NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
    }

    private void createH2Tables(Statement stmt) throws SQLException {
        this.createTable(stmt, "donut_teams", "CREATE TABLE IF NOT EXISTS `donut_teams` (`id` INT AUTO_INCREMENT, `name` VARCHAR(16) NOT NULL UNIQUE, `tag` VARCHAR(6) NOT NULL, `owner_uuid` VARCHAR(36) NOT NULL, `home_location` VARCHAR(255), `home_server` VARCHAR(255), `pvp_enabled` BOOLEAN DEFAULT TRUE, `is_public` BOOLEAN DEFAULT FALSE, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, `description` VARCHAR(64) DEFAULT NULL, `balance` DOUBLE DEFAULT 0.0, `kills` INT DEFAULT 0, `deaths` INT DEFAULT 0, PRIMARY KEY (`id`))");
        this.createTable(stmt, "donut_team_members", "CREATE TABLE IF NOT EXISTS `donut_team_members` (`player_uuid` VARCHAR(36) NOT NULL, `team_id` INT NOT NULL, `role` VARCHAR(16) NOT NULL, `join_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, `can_withdraw` BOOLEAN DEFAULT FALSE, `can_use_enderchest` BOOLEAN DEFAULT TRUE, `can_set_home` BOOLEAN DEFAULT FALSE, `can_use_home` BOOLEAN DEFAULT TRUE, PRIMARY KEY (`player_uuid`), FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_team_enderchest", "CREATE TABLE IF NOT EXISTS `donut_team_enderchest` (`team_id` INT NOT NULL, `inventory_data` TEXT, PRIMARY KEY (`team_id`), FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_pending_teleports", "CREATE TABLE IF NOT EXISTS `donut_pending_teleports` (`player_uuid` VARCHAR(36) NOT NULL, `destination_server` VARCHAR(255) NOT NULL, `destination_location` VARCHAR(255) NOT NULL, `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`player_uuid`))");
        this.createTable(stmt, "donut_servers", "CREATE TABLE IF NOT EXISTS `donut_servers` (`server_name` VARCHAR(64) PRIMARY KEY, `last_heartbeat` TIMESTAMP NOT NULL)");
        this.createTable(stmt, "donut_team_locks", "CREATE TABLE IF NOT EXISTS `donut_team_locks` (`team_id` INT PRIMARY KEY, `server_identifier` VARCHAR(255) NOT NULL, `lock_time` TIMESTAMP NOT NULL, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_cross_server_updates", "CREATE TABLE IF NOT EXISTS `donut_cross_server_updates` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `update_type` VARCHAR(50) NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `server_name` VARCHAR(64) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_cross_server_messages", "CREATE TABLE IF NOT EXISTS `donut_cross_server_messages` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `message` TEXT NOT NULL, `server_name` VARCHAR(64) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_team_homes", "CREATE TABLE IF NOT EXISTS `donut_team_homes` (`team_id` INT PRIMARY KEY, `location` VARCHAR(255) NOT NULL, `server_name` VARCHAR(64) NOT NULL, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_team_warps", "CREATE TABLE IF NOT EXISTS `donut_team_warps` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `warp_name` VARCHAR(32) NOT NULL, `location` VARCHAR(255) NOT NULL, `server_name` VARCHAR(64) NOT NULL, `password` VARCHAR(64), `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createUniqueIndex(stmt, "idx_team_warp", "`donut_team_warps`", "`team_id`, `warp_name`");
        this.createTable(stmt, "donut_join_requests", "CREATE TABLE IF NOT EXISTS `donut_join_requests` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createUniqueIndex(stmt, "idx_team_player_request", "`donut_join_requests`", "`team_id`, `player_uuid`");
        this.createTable(stmt, "donut_ender_chest_locks", "CREATE TABLE IF NOT EXISTS `donut_ender_chest_locks` (`team_id` INT PRIMARY KEY, `server_identifier` VARCHAR(255) NOT NULL, `lock_time` TIMESTAMP NOT NULL, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createTable(stmt, "donut_player_cache", "CREATE TABLE IF NOT EXISTS `donut_player_cache` (`player_uuid` VARCHAR(36) PRIMARY KEY, `player_name` VARCHAR(16) NOT NULL, `last_seen` TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        this.createIndex(stmt, "idx_player_name", "`donut_player_cache`", "`player_name`");
        this.createTable(stmt, "donut_team_invites", "CREATE TABLE IF NOT EXISTS `donut_team_invites` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `inviter_uuid` VARCHAR(36) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createUniqueIndex(stmt, "idx_team_player_invite", "`donut_team_invites`", "`team_id`, `player_uuid`");
        this.createTable(stmt, "donut_team_blacklist", "CREATE TABLE IF NOT EXISTS `donut_team_blacklist` (`id` INT AUTO_INCREMENT PRIMARY KEY, `team_id` INT NOT NULL, `player_uuid` VARCHAR(36) NOT NULL, `player_name` VARCHAR(16) NOT NULL, `reason` TEXT, `blacklisted_by_uuid` VARCHAR(36) NOT NULL, `blacklisted_by_name` VARCHAR(16) NOT NULL, `blacklisted_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
        this.createUniqueIndex(stmt, "idx_team_player_blacklist", "`donut_team_blacklist`", "`team_id`, `player_uuid`");
        this.createTable(stmt, "donut_player_sessions", "CREATE TABLE IF NOT EXISTS `donut_player_sessions` (`player_uuid` VARCHAR(36) PRIMARY KEY, `server_name` VARCHAR(255) NOT NULL, `last_seen` TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        this.createIndex(stmt, "idx_session_server", "`donut_player_sessions`", "`server_name`");
        this.createIndex(stmt, "idx_session_last_seen", "`donut_player_sessions`", "`last_seen`");
        this.createTable(stmt, "donut_server_aliases", "CREATE TABLE IF NOT EXISTS `donut_server_aliases` (`server_name` VARCHAR(255) PRIMARY KEY, `alias` VARCHAR(64) NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        this.createTable(stmt, "donut_team_rename_cooldowns", "CREATE TABLE IF NOT EXISTS `donut_team_rename_cooldowns` (`team_id` INT PRIMARY KEY, `last_rename` TIMESTAMP NOT NULL, `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (`team_id`) REFERENCES `donut_teams`(`id`) ON DELETE CASCADE)");
    }

    private boolean tryMinimalH2Configuration() {
        boolean bl;
        block12: {
            this.plugin.getLogger().info("Attempting minimal H2 configuration...");
            HikariConfig fallbackConfig = new HikariConfig();
            fallbackConfig.setPoolName("justTeams-Pool-Fallback");
            fallbackConfig.setMaximumPoolSize(2);
            fallbackConfig.setMinimumIdle(1);
            fallbackConfig.setConnectionTimeout(5000L);
            fallbackConfig.setValidationTimeout(1000L);
            fallbackConfig.setIdleTimeout(300000L);
            fallbackConfig.setMaxLifetime(600000L);
            fallbackConfig.setConnectionTestQuery("SELECT 1");
            File dataFolder = new File(this.plugin.getDataFolder(), "data");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            fallbackConfig.setDriverClassName("eu.kotori.justTeams.libs.h2.Driver");
            fallbackConfig.setJdbcUrl("jdbc:h2:" + dataFolder.getAbsolutePath().replace("\\", "/") + "/teams");
            this.plugin.getLogger().info("Testing minimal H2 configuration...");
            this.hikari = new HikariDataSource(fallbackConfig);
            try (Connection testConn = this.hikari.getConnection()) {
                this.plugin.getLogger().info("Minimal H2 configuration successful!");
                this.runMigrationsAndSchemaChecks();
                this.plugin.getLogger().info("Fallback H2 initialization completed successfully!");
                bl = true;
            } catch (Exception fallbackError) {
                this.plugin.getLogger().severe("Even minimal H2 configuration failed: " + fallbackError.getMessage());
                fallbackError.printStackTrace();
                if (this.hikari != null && !this.hikari.isClosed()) {
                    try {
                        this.hikari.close();
                    } catch (Exception cleanup) {
                        this.plugin.getLogger().warning("Error during fallback cleanup: " + cleanup.getMessage());
                    }
                }
                return false;
            }
        }
        return bl;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void shutdown() {
        this.plugin.getLogger().info("Shutting down database storage...");
        Object object = this.heartbeatLock;
        synchronized (object) {
            try {
                if (this.heartbeatStatement != null && !this.heartbeatStatement.isClosed()) {
                    this.heartbeatStatement.close();
                    this.plugin.getLogger().info("Heartbeat statement closed");
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Error closing heartbeat statement: " + e.getMessage());
            } finally {
                this.heartbeatStatement = null;
            }
        }
        if (this.isConnected()) {
            try {
                this.plugin.getLogger().info("Closing database connection pool...");
                this.hikari.close();
                this.plugin.getLogger().info("Database connection pool closed successfully");
            } catch (Exception e) {
                this.plugin.getLogger().warning("Error closing database connection pool: " + e.getMessage());
            }
        } else {
            this.plugin.getLogger().info("Database connection was already closed");
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return this.hikari != null && !this.hikari.isClosed();
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error checking connection status: " + e.getMessage());
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (!this.isConnected()) {
            throw new SQLException("Database connection pool is not available");
        }
        try {
            Connection conn = this.hikari.getConnection();
            if (!conn.isValid(3)) {
                conn.close();
                throw new SQLException("Connection validation failed");
            }
            return conn;
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get database connection: " + e.getMessage());
            throw e;
        }
    }

    public boolean attemptConnectionRecovery() {
        if (this.isConnected()) {
            return true;
        }
        this.plugin.getLogger().warning("Database connection lost, attempting recovery...");
        try {
            this.shutdown();
            Thread.sleep(1000L);
            return this.init();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.plugin.getLogger().severe("Connection recovery interrupted");
            return false;
        } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to recover database connection: " + e.getMessage());
            return false;
        }
    }

    /*
     * Loose catch block
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<Team> createTeam(String name, String tag, UUID ownerUuid, boolean defaultPvp, boolean defaultPublic) {
        String insertTeamSQL = "INSERT INTO donut_teams (name, tag, owner_uuid, pvp_enabled, is_public) VALUES (?, ?, ?, ?, ?)";
        String insertMemberSQL = "INSERT INTO donut_team_members (player_uuid, team_id, role, join_date, can_withdraw, can_use_enderchest, can_set_home, can_use_home) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = this.getConnection();
             PreparedStatement teamStmt = conn.prepareStatement(insertTeamSQL, 1)) {

            conn.setAutoCommit(false);

            teamStmt.setString(1, name);
            teamStmt.setString(2, tag);
            teamStmt.setString(3, ownerUuid.toString());
            teamStmt.setBoolean(4, defaultPvp);
            teamStmt.setBoolean(5, defaultPublic);
            teamStmt.executeUpdate();

            try (ResultSet generatedKeys = teamStmt.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    conn.rollback();
                    return Optional.empty();
                }

                int teamId = generatedKeys.getInt(1);

                try (PreparedStatement memberStmt = conn.prepareStatement(insertMemberSQL)) {
                    memberStmt.setString(1, ownerUuid.toString());
                    memberStmt.setInt(2, teamId);
                    memberStmt.setString(3, TeamRole.OWNER.name());
                    memberStmt.setTimestamp(4, Timestamp.from(Instant.now()));
                    memberStmt.setBoolean(5, true);
                    memberStmt.setBoolean(6, true);
                    memberStmt.setBoolean(7, true);
                    memberStmt.setBoolean(8, true);
                    memberStmt.executeUpdate();
                }

                conn.commit();
                return this.findTeamById(teamId);
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not create team in database: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void deleteTeam(int teamId) {
        String sql = "DELETE FROM donut_teams WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not delete team with ID " + teamId + ": " + e.getMessage());
        }
    }

    /*
     * Exception decompiling
     */
    @Override
    public boolean addMemberToTeam(int teamId, UUID playerUuid) {
        /*
         * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
         * 
         * org.benf.cfr.reader.util.ConfusedCFRException: Started 3 blocks at once
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.getStartingBlocks(Op04StructuredStatement.java:412)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:487)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
         *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
         *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:538)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1055)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
         *     at org.benf.cfr.reader.Driver.doJarVersionTypes(Driver.java:257)
         *     at org.benf.cfr.reader.Driver.doJar(Driver.java:139)
         *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:76)
         *     at org.benf.cfr.reader.Main.main(Main.java:54)
         *     at async.DecompilerRunnable.cfrDecompilation(DecompilerRunnable.java:348)
         *     at async.DecompilerRunnable.call(DecompilerRunnable.java:309)
         *     at async.DecompilerRunnable.call(DecompilerRunnable.java:31)
         *     at java.util.concurrent.FutureTask.run(FutureTask.java:266)
         *     at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
         *     at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
         *     at java.lang.Thread.run(Thread.java:750)
         */
        throw new IllegalStateException("Decompilation failed");
    }

    @Override
    public void removeMemberFromTeam(UUID playerUuid) {
        String sql = "DELETE FROM donut_team_members WHERE player_uuid = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not remove member " + String.valueOf(playerUuid) + ": " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<Team> findTeamByPlayer(UUID playerUuid) {
        String sql = "SELECT t.* FROM donut_teams t JOIN donut_team_members tm ON t.id = tm.team_id WHERE tm.player_uuid = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return Optional.empty();
            Optional<Team> optional = Optional.of(this.mapTeamFromResultSet(rs));
            return optional;
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error finding team by player: " + e.getMessage());
        }
        return Optional.empty();
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<Team> findTeamByName(String name) {
        String sql = "SELECT * FROM donut_teams WHERE name = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return Optional.empty();
            Optional<Team> optional = Optional.of(this.mapTeamFromResultSet(rs));
            return optional;
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error finding team by name: " + e.getMessage());
        }
        return Optional.empty();
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<Team> findTeamById(int id) {
        String sql = "SELECT * FROM donut_teams WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return Optional.empty();
            Optional<Team> optional = Optional.of(this.mapTeamFromResultSet(rs));
            return optional;
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error finding team by id: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<Team> getAllTeams() {
        ArrayList<Team> teams = new ArrayList<Team>();
        String sql = "SELECT * FROM donut_teams ORDER BY created_at DESC";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                teams.add(this.mapTeamFromResultSet(rs));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error getting all teams: " + e.getMessage());
        }
        return teams;
    }

    @Override
    public List<TeamPlayer> getTeamMembers(int teamId) {
        ArrayList<TeamPlayer> members = new ArrayList<TeamPlayer>();
        String sql = "SELECT * FROM donut_team_members WHERE team_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, teamId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                members.add(this.mapPlayerFromResultSet(rs));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error getting team members: " + e.getMessage());
        }
        return members;
    }

    @Override
    public void setTeamHome(int teamId, Location location, String serverName) {
        String sql = "UPDATE donut_teams SET home_location = ?, home_server = ? WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setString(1, this.serializeLocation(location));
            stmt.setString(2, serverName);
            stmt.setInt(3, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not set team home for team " + teamId + ": " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<IDataStorage.TeamHome> getTeamHome(int teamId) {
        String sql = "SELECT home_location, home_server FROM donut_teams WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, teamId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return Optional.empty();
            String locStr = rs.getString("home_location");
            String server = rs.getString("home_server");
            Location loc = this.deserializeLocation(locStr);
            if (loc == null) return Optional.empty();
            if (server == null) return Optional.empty();
            if (server.isEmpty()) return Optional.empty();
            Optional<IDataStorage.TeamHome> optional = Optional.of(new IDataStorage.TeamHome(loc, server));
            return optional;
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not retrieve team home for team " + teamId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void setTeamTag(int teamId, String tag) {
        String sql = "UPDATE donut_teams SET tag = ? WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setString(1, tag);
            stmt.setInt(2, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not set team tag for team " + teamId + ": " + e.getMessage());
        }
    }

    @Override
    public void setTeamDescription(int teamId, String description) {
        String sql = "UPDATE donut_teams SET description = ? WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setString(1, description);
            stmt.setInt(2, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not set team description for team " + teamId + ": " + e.getMessage());
        }
    }

    @Override
    public void transferOwnership(int teamId, UUID newOwnerUuid, UUID oldOwnerUuid) {
        String updateTeamOwner = "UPDATE donut_teams SET owner_uuid = ? WHERE id = ?";
        String updateNewOwnerRole = "UPDATE donut_team_members SET role = ?, can_withdraw = TRUE, can_use_enderchest = TRUE, can_set_home = TRUE, can_use_home = TRUE WHERE player_uuid = ?";
        String updateOldOwnerRole = "UPDATE donut_team_members SET role = ?, can_withdraw = FALSE, can_use_enderchest = TRUE, can_set_home = FALSE, can_use_home = TRUE WHERE player_uuid = ?";
        try (Connection conn = this.getConnection();){
            conn.setAutoCommit(false);
            try (PreparedStatement teamStmt = conn.prepareStatement(updateTeamOwner);
                 PreparedStatement newOwnerStmt = conn.prepareStatement(updateNewOwnerRole);
                 PreparedStatement oldOwnerStmt = conn.prepareStatement(updateOldOwnerRole);){
                teamStmt.setString(1, newOwnerUuid.toString());
                teamStmt.setInt(2, teamId);
                teamStmt.executeUpdate();
                newOwnerStmt.setString(1, TeamRole.OWNER.name());
                newOwnerStmt.setString(2, newOwnerUuid.toString());
                newOwnerStmt.executeUpdate();
                oldOwnerStmt.setString(1, TeamRole.MEMBER.name());
                oldOwnerStmt.setString(2, oldOwnerUuid.toString());
                oldOwnerStmt.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not transfer team ownership for team " + teamId + ": " + e.getMessage());
        }
    }

    @Override
    public void setPvpStatus(int teamId, boolean status) {
        String sql = "UPDATE donut_teams SET pvp_enabled = ? WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setBoolean(1, status);
            stmt.setInt(2, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not set pvp status for team " + teamId + ": " + e.getMessage());
        }
    }

    @Override
    public void updateTeamBalance(int teamId, double balance) {
        String sql = "UPDATE donut_teams SET balance = ? WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setDouble(1, balance);
            stmt.setInt(2, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not update balance for team " + teamId + ": " + e.getMessage());
        }
    }

    @Override
    public void updateTeamStats(int teamId, int kills, int deaths) {
        String sql = "UPDATE donut_teams SET kills = ?, deaths = ? WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, kills);
            stmt.setInt(2, deaths);
            stmt.setInt(3, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not update stats for team " + teamId + ": " + e.getMessage());
        }
    }

    @Override
    public void saveEnderChest(int teamId, String serializedInventory) {
        String sql = "mysql".equals(this.storageType) ? "INSERT INTO donut_team_enderchest (team_id, inventory_data) VALUES (?, ?) ON DUPLICATE KEY UPDATE inventory_data = VALUES(inventory_data)" : "MERGE INTO donut_team_enderchest (team_id, inventory_data) KEY(team_id) VALUES (?, ?)";
        if (this.plugin.getConfig().getBoolean("debug", false)) {
            this.plugin.getLogger().info("[DEBUG] Saving enderchest to " + this.storageType.toUpperCase() + " - Team ID: " + teamId + ", Data length: " + String.valueOf(serializedInventory != null ? Integer.valueOf(serializedInventory.length()) : "null"));
        }
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, teamId);
            stmt.setString(2, serializedInventory);
            int rowsAffected = stmt.executeUpdate();
            if (this.plugin.getConfig().getBoolean("debug", false)) {
                this.plugin.getLogger().info("[DEBUG] Database save successful - Rows affected: " + rowsAffected);
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not save ender chest for team " + teamId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public String getEnderChest(int teamId) {
        String sql = "SELECT inventory_data FROM donut_team_enderchest WHERE team_id = ?";
        if (this.plugin.getConfig().getBoolean("debug", false)) {
            this.plugin.getLogger().info("[DEBUG] Loading enderchest from " + this.storageType.toUpperCase() + " - Team ID: " + teamId);
        }
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, teamId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String data = rs.getString("inventory_data");
                if (this.plugin.getConfig().getBoolean("debug", false)) {
                    this.plugin.getLogger().info("[DEBUG] Database load successful - Data length: " + String.valueOf(data != null ? Integer.valueOf(data.length()) : "null"));
                }
                String string = data;
                return string;
            }
            if (!this.plugin.getConfig().getBoolean("debug", false)) return null;
            this.plugin.getLogger().info("[DEBUG] No enderchest data found in database for team " + teamId);
            return null;
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not load ender chest for team " + teamId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void updateMemberPermissions(int teamId, UUID memberUuid, boolean canWithdraw, boolean canUseEnderChest, boolean canSetHome, boolean canUseHome) {
        String sql = "UPDATE donut_team_members SET can_withdraw = ?, can_use_enderchest = ?, can_set_home = ?, can_use_home = ? WHERE player_uuid = ? AND team_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setBoolean(1, canWithdraw);
            stmt.setBoolean(2, canUseEnderChest);
            stmt.setBoolean(3, canSetHome);
            stmt.setBoolean(4, canUseHome);
            stmt.setString(5, memberUuid.toString());
            stmt.setInt(6, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not update permissions for member " + String.valueOf(memberUuid) + ": " + e.getMessage());
        }
    }

    @Override
    public void updateMemberPermission(int teamId, UUID memberUuid, String permission, boolean value) throws SQLException {
        String columnName = switch (permission.toLowerCase()) {
            case "withdraw" -> "can_withdraw";
            case "enderchest" -> "can_use_enderchest";
            case "sethome" -> "can_set_home";
            case "usehome" -> "can_use_home";
            default -> throw new IllegalArgumentException("Invalid permission: " + permission);
        };
        String sql = "UPDATE donut_team_members SET " + columnName + " = ? WHERE player_uuid = ? AND team_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setBoolean(1, value);
            stmt.setString(2, memberUuid.toString());
            stmt.setInt(3, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not update " + permission + " permission for member " + String.valueOf(memberUuid) + ": " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void updateMemberRole(int teamId, UUID memberUuid, TeamRole role) {
        String sql = role == TeamRole.CO_OWNER ? "UPDATE donut_team_members SET role = ?, can_withdraw = TRUE, can_use_enderchest = TRUE, can_set_home = TRUE, can_use_home = TRUE WHERE player_uuid = ? AND team_id = ?" : "UPDATE donut_team_members SET role = ?, can_withdraw = FALSE, can_use_enderchest = TRUE, can_set_home = FALSE, can_use_home = TRUE WHERE player_uuid = ? AND team_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setString(1, role.name());
            stmt.setString(2, memberUuid.toString());
            stmt.setInt(3, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not update role for member " + String.valueOf(memberUuid) + ": " + e.getMessage());
        }
    }

    private Map<Integer, Team> getTopTeams(String orderBy, int limit) {
        LinkedHashMap<Integer, Team> topTeams = new LinkedHashMap<Integer, Team>();
        String sql = "SELECT * FROM donut_teams ORDER BY " + orderBy + " DESC LIMIT ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            int rank = 1;
            while (rs.next()) {
                topTeams.put(rank++, this.mapTeamFromResultSet(rs));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not get top teams: " + e.getMessage());
        }
        return topTeams;
    }

    @Override
    public Map<Integer, Team> getTopTeamsByKills(int limit) {
        return this.getTopTeams("kills", limit);
    }

    @Override
    public Map<Integer, Team> getTopTeamsByBalance(int limit) {
        return this.getTopTeams("balance", limit);
    }

    @Override
    public Map<Integer, Team> getTopTeamsByMembers(int limit) {
        LinkedHashMap<Integer, Team> topTeams = new LinkedHashMap<Integer, Team>();
        String sql = "SELECT t.*, COUNT(tm.player_uuid) as member_count FROM donut_teams t JOIN donut_team_members tm ON t.id = tm.team_id GROUP BY t.id ORDER BY member_count DESC LIMIT ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            int rank = 1;
            while (rs.next()) {
                topTeams.put(rank++, this.mapTeamFromResultSet(rs));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not get top teams by members: " + e.getMessage());
        }
        return topTeams;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void updateServerHeartbeat(String serverName) {
        if (serverName == null || serverName.trim().isEmpty()) {
            this.plugin.getLogger().warning("Cannot update heartbeat: server name is null or empty");
            return;
        }
        Object object = this.heartbeatLock;
        synchronized (object) {
            try (Connection conn = this.getConnection();){
                String sql = "mysql".equals(this.storageType) ? HEARTBEAT_SQL_MYSQL : HEARTBEAT_SQL_H2;
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setString(1, serverName);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Could not update server heartbeat for " + serverName + ": " + e.getMessage());
            }
        }
    }

    @Override
    public Map<String, Timestamp> getActiveServers() {
        HashMap<String, Timestamp> activeServers = new HashMap<String, Timestamp>();
        String sql = "SELECT server_name, last_heartbeat FROM donut_servers WHERE last_heartbeat > NOW() - INTERVAL 2 MINUTE";
        if (!"mysql".equals(this.storageType)) {
            sql = "SELECT server_name, last_heartbeat FROM donut_servers WHERE last_heartbeat > DATEADD(MINUTE, -2, NOW())";
        }
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                activeServers.put(rs.getString("server_name"), rs.getTimestamp("last_heartbeat"));
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not retrieve active servers: " + e.getMessage());
        }
        return activeServers;
    }

    @Override
    public void addPendingTeleport(UUID playerUuid, String serverName, Location location) {
        String sql = "mysql".equals(this.storageType) ? "INSERT INTO donut_pending_teleports (player_uuid, destination_server, destination_location) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE destination_server = VALUES(destination_server), destination_location = VALUES(destination_location)" : "MERGE INTO donut_pending_teleports (player_uuid, destination_server, destination_location) KEY(player_uuid) VALUES (?, ?, ?)";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, serverName);
            stmt.setString(3, this.serializeLocation(location));
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Could not add pending teleport for " + String.valueOf(playerUuid) + ": " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<Location> getAndRemovePendingTeleport(UUID playerUuid, String currentServer) {
        String selectSql = "SELECT destination_location FROM donut_pending_teleports WHERE player_uuid = ? AND destination_server = ?";
        String deleteSql = "DELETE FROM donut_pending_teleports WHERE player_uuid = ?";
        try (Connection conn = this.getConnection();){
            PreparedStatement selectStmt;
            block22: {
                conn.setAutoCommit(false);
                try {
                    Optional<Location> optional;
                    selectStmt = conn.prepareStatement(selectSql);
                    try {
                        selectStmt.setString(1, playerUuid.toString());
                        selectStmt.setString(2, currentServer);
                        ResultSet rs = selectStmt.executeQuery();
                        if (!rs.next()) break block22;
                        Location loc = this.deserializeLocation(rs.getString("destination_location"));
                        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);){
                            deleteStmt.setString(1, playerUuid.toString());
                            deleteStmt.executeUpdate();
                        }
                        conn.commit();
                        optional = Optional.ofNullable(loc);
                        if (selectStmt == null) return optional;
                    } catch (Throwable throwable) {
                        if (selectStmt == null) throw throwable;
                        try {
                            selectStmt.close();
                            throw throwable;
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                        throw throwable;
                    }
                    selectStmt.close();
                    return optional;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
            if (selectStmt == null) return Optional.empty();
            selectStmt.close();
            return Optional.empty();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error handling pending teleport for " + String.valueOf(playerUuid) + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    private String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private Location deserializeLocation(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        String[] parts = s.split(",");
        if (parts.length != 6) {
            return null;
        }
        World w = Bukkit.getWorld((String)parts[0]);
        if (w == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(w, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Team mapTeamFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String tag = rs.getString("tag");
        UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
        boolean pvpEnabled = rs.getBoolean("pvp_enabled");
        boolean isPublic = rs.getBoolean("is_public");
        String description = rs.getString("description");
        double balance = rs.getDouble("balance");
        int kills = rs.getInt("kills");
        int deaths = rs.getInt("deaths");
        Team team = new Team(id, name, tag, ownerUuid, pvpEnabled, isPublic);
        team.setHomeLocation(this.deserializeLocation(rs.getString("home_location")));
        team.setHomeServer(rs.getString("home_server"));
        if (description != null) {
            team.setDescription(description);
        }
        team.setBalance(balance);
        team.setKills(kills);
        team.setDeaths(deaths);
        try {
            List<TeamPlayer> members = this.getTeamMembers(id);
            for (TeamPlayer member : members) {
                team.addMember(member);
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to load members for team " + id + ": " + e.getMessage());
        }
        return team;
    }

    private TeamPlayer mapPlayerFromResultSet(ResultSet rs) throws SQLException {
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        TeamRole role = TeamRole.valueOf(rs.getString("role"));
        Instant joinDate = rs.getTimestamp("join_date").toInstant();
        boolean canWithdraw = rs.getBoolean("can_withdraw");
        boolean canUseEnderChest = rs.getBoolean("can_use_enderchest");
        boolean canSetHome = rs.getBoolean("can_set_home");
        boolean canUseHome = rs.getBoolean("can_use_home");
        return new TeamPlayer(playerUuid, role, joinDate, canWithdraw, canUseEnderChest, canSetHome, canUseHome);
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public boolean acquireEnderChestLock(int teamId, String serverIdentifier) {
        String insertSql = "INSERT INTO donut_team_locks (team_id, server_identifier, lock_time) VALUES (?, ?, NOW())";
        String updateSql = "UPDATE donut_team_locks SET server_identifier = ?, lock_time = NOW() WHERE team_id = ?";
        try (Connection conn = this.getConnection()) {
            Optional<IDataStorage.TeamEnderChestLock> currentLock = this.getEnderChestLock(teamId);
            if (currentLock.isPresent()) {
                Map<String, Timestamp> activeServers = this.getActiveServers();
                if (activeServers.containsKey(currentLock.get().serverName())) {
                    this.plugin.getDebugLogger().log("Ender chest for team " + teamId + " is locked by an active server: " + currentLock.get().serverName());
                    return false;
                }
                this.plugin.getDebugLogger().log("Ender chest for team " + teamId + " is locked by an inactive server. Overriding lock.");
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, serverIdentifier);
                    stmt.setInt(2, teamId);
                    stmt.executeUpdate();
                    return true;
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setInt(1, teamId);
                stmt.setString(2, serverIdentifier);
                stmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error acquiring ender chest lock for team " + teamId + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public void releaseEnderChestLock(int teamId) {
        String sql = "DELETE FROM donut_team_locks WHERE team_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error releasing ender chest lock for team " + teamId + ": " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<IDataStorage.TeamEnderChestLock> getEnderChestLock(int teamId) {
        String sql = "SELECT server_identifier, lock_time FROM donut_team_locks WHERE team_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, teamId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return Optional.empty();
            Optional<IDataStorage.TeamEnderChestLock> optional = Optional.of(new IDataStorage.TeamEnderChestLock(teamId, rs.getString("server_identifier"), rs.getTimestamp("lock_time")));
            return optional;
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Error checking ender chest lock for team " + teamId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public void cleanupOldCrossServerData() {
        block15: {
            if (this.isConnected()) {
                try (Connection conn = this.getConnection();){
                    if (this.tableExists(conn, "donut_cross_server_updates") && this.tableExists(conn, "donut_cross_server_messages")) {
                        if (this.columnExists(conn, "donut_cross_server_updates", "created_at") && this.columnExists(conn, "donut_cross_server_messages", "created_at")) {
                            if ("mysql".equals(this.storageType)) {
                                conn.createStatement().execute("DELETE FROM donut_cross_server_updates WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)");
                                conn.createStatement().execute("DELETE FROM donut_cross_server_messages WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)");
                            } else {
                                conn.createStatement().execute("DELETE FROM donut_cross_server_updates WHERE created_at < DATEADD(DAY, -7, NOW())");
                                conn.createStatement().execute("DELETE FROM donut_cross_server_messages WHERE created_at < DATEADD(DAY, -7, NOW())");
                            }
                        } else if (this.plugin.getConfigManager().isDebugEnabled()) {
                            this.plugin.getLogger().info("created_at column not found in cross-server tables. Skipping cleanup until migration is complete.");
                        }
                    }
                } catch (SQLException e) {
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block15;
                    this.plugin.getLogger().warning("Failed to cleanup old cross-server data: " + e.getMessage());
                }
            }
        }
    }

    private boolean tableExists(Connection conn, String tableName) {
        try {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getTables(null, null, tableName, null)) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) {
        try {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, tableName, columnName)) {
                return rs.next();
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Error checking if column exists: " + e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getDatabaseStats() {
        HashMap<String, Object> stats = new HashMap<String, Object>();
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                stats.put("active_connections", this.hikari.getHikariPoolMXBean().getActiveConnections());
                stats.put("idle_connections", this.hikari.getHikariPoolMXBean().getIdleConnections());
                stats.put("total_connections", this.hikari.getHikariPoolMXBean().getTotalConnections());
                stats.put("threads_awaiting_connection", this.hikari.getHikariPoolMXBean().getThreadsAwaitingConnection());
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to get database stats: " + e.getMessage());
            }
        }
        return stats;
    }

    public void optimizeConnectionPool() {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                conn.createStatement().execute("OPTIMIZE TABLE donut_teams, donut_team_members, donut_team_enderchest");
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to optimize connection pool: " + e.getMessage());
            }
        }
    }

    @Override
    public List<BlacklistedPlayer> getTeamBlacklist(int teamId) throws SQLException {
        String sql = "SELECT player_uuid, player_name, reason, blacklisted_by_uuid, blacklisted_by_name, blacklisted_at FROM donut_team_blacklist WHERE team_id = ?";
        ArrayList<BlacklistedPlayer> blacklist = new ArrayList<BlacklistedPlayer>();
        try (Connection conn = this.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);){
            stmt.setInt(1, teamId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                blacklist.add(new BlacklistedPlayer(UUID.fromString(rs.getString("player_uuid")), rs.getString("player_name"), rs.getString("reason"), UUID.fromString(rs.getString("blacklisted_by_uuid")), rs.getString("blacklisted_by_name"), rs.getTimestamp("blacklisted_at").toInstant()));
            }
        }
        return blacklist;
    }

    @Override
    public boolean isPlayerBlacklisted(int teamId, UUID playerUuid) throws SQLException {
        String sql = "SELECT 1 FROM donut_team_blacklist WHERE team_id = ? AND player_uuid = ?";
        try (Connection conn = this.getConnection();){
            boolean bl;
            block12: {
                PreparedStatement stmt = conn.prepareStatement(sql);
                try {
                    stmt.setInt(1, teamId);
                    stmt.setString(2, playerUuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    bl = rs.next();
                    if (stmt == null) break block12;
                } catch (Throwable throwable) {
                    if (stmt != null) {
                        try {
                            stmt.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                stmt.close();
            }
            return bl;
        }
    }

    @Override
    public boolean removePlayerFromBlacklist(int teamId, UUID playerUuid) throws SQLException {
        String sql = "DELETE FROM donut_team_blacklist WHERE team_id = ? AND player_uuid = ?";
        try (Connection conn = this.getConnection();){
            boolean bl;
            block12: {
                PreparedStatement stmt = conn.prepareStatement(sql);
                try {
                    stmt.setInt(1, teamId);
                    stmt.setString(2, playerUuid.toString());
                    boolean bl2 = bl = stmt.executeUpdate() > 0;
                    if (stmt == null) break block12;
                } catch (Throwable throwable) {
                    if (stmt != null) {
                        try {
                            stmt.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                stmt.close();
            }
            return bl;
        }
    }

    @Override
    public boolean addPlayerToBlacklist(int teamId, UUID playerUuid, String playerName, String reason, UUID blacklistedByUuid, String blacklistedByName) throws SQLException {
        String sql = "INSERT INTO donut_team_blacklist (team_id, player_uuid, player_name, reason, blacklisted_by_uuid, blacklisted_by_name) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = this.getConnection();){
            boolean bl;
            block12: {
                PreparedStatement stmt = conn.prepareStatement(sql);
                try {
                    stmt.setInt(1, teamId);
                    stmt.setString(2, playerUuid.toString());
                    stmt.setString(3, playerName);
                    stmt.setString(4, reason);
                    stmt.setString(5, blacklistedByUuid.toString());
                    stmt.setString(6, blacklistedByName);
                    boolean bl2 = bl = stmt.executeUpdate() > 0;
                    if (stmt == null) break block12;
                } catch (Throwable throwable) {
                    if (stmt != null) {
                        try {
                            stmt.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                stmt.close();
            }
            return bl;
        }
    }

    @Override
    public void cleanupStaleEnderChestLocks(int hoursOld) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "mysql".equals(this.storageType) ? "DELETE FROM donut_team_locks WHERE lock_time < DATE_SUB(NOW(), INTERVAL ? HOUR)" : "DELETE FROM donut_team_locks WHERE lock_time < DATEADD(HOUR, -?, NOW())";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setInt(1, hoursOld);
                    int deleted = stmt.executeUpdate();
                    if (deleted > 0) {
                        this.plugin.getLogger().info("Cleaned up " + deleted + " stale ender chest locks");
                    }
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to cleanup stale ender chest locks: " + e.getMessage());
            }
        }
    }

    @Override
    public void cleanupAllEnderChestLocks() {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "DELETE FROM donut_team_locks";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    int deleted = stmt.executeUpdate();
                    if (deleted > 0) {
                        this.plugin.getLogger().info("Cleaned up all " + deleted + " ender chest locks");
                    }
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to cleanup all ender chest locks: " + e.getMessage());
            }
        }
    }

    @Override
    public void removeCrossServerMessage(int messageId) {
        block15: {
            if (this.isConnected()) {
                try (Connection conn = this.getConnection();){
                    String sql = "DELETE FROM donut_cross_server_messages WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);){
                        stmt.setInt(1, messageId);
                        stmt.executeUpdate();
                    }
                } catch (SQLException e) {
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block15;
                    this.plugin.getLogger().warning("Failed to remove cross server message: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public List<IDataStorage.CrossServerMessage> getCrossServerMessages(String serverName) {
        ArrayList<IDataStorage.CrossServerMessage> messages;
        block16: {
            messages = new ArrayList<IDataStorage.CrossServerMessage>();
            if (this.isConnected()) {
                try (Connection conn = this.getConnection();){
                    boolean hasCreatedAt = this.columnExists(conn, "donut_cross_server_messages", "created_at");
                    String sql = hasCreatedAt ? "SELECT id, team_id, player_uuid, message, server_name, created_at FROM donut_cross_server_messages WHERE server_name != ? ORDER BY created_at ASC LIMIT 100" : "SELECT id, team_id, player_uuid, message, server_name FROM donut_cross_server_messages WHERE server_name != ? LIMIT 100";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);){
                        stmt.setString(1, serverName);
                        ResultSet rs = stmt.executeQuery();
                        while (rs.next()) {
                            Timestamp timestamp = hasCreatedAt ? rs.getTimestamp("created_at") : new Timestamp(System.currentTimeMillis());
                            messages.add(new IDataStorage.CrossServerMessage(rs.getInt("id"), rs.getInt("team_id"), rs.getString("player_uuid"), rs.getString("message"), rs.getString("server_name"), timestamp));
                        }
                    }
                } catch (SQLException e) {
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block16;
                    this.plugin.getLogger().warning("Failed to get cross server messages: " + e.getMessage());
                }
            }
        }
        return messages;
    }

    @Override
    public void addCrossServerMessage(int teamId, String playerUuid, String message, String serverName) {
        block15: {
            if (this.isConnected()) {
                try (Connection conn = this.getConnection();){
                    String sql = "INSERT INTO donut_cross_server_messages (team_id, player_uuid, message, server_name) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);){
                        stmt.setInt(1, teamId);
                        stmt.setString(2, playerUuid);
                        stmt.setString(3, message);
                        stmt.setString(4, serverName);
                        stmt.executeUpdate();
                    }
                } catch (SQLException e) {
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block15;
                    this.plugin.getLogger().warning("Failed to add cross server message: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void removeCrossServerUpdate(int updateId) {
        block15: {
            if (this.isConnected()) {
                try (Connection conn = this.getConnection();){
                    String sql = "DELETE FROM donut_cross_server_updates WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);){
                        stmt.setInt(1, updateId);
                        stmt.executeUpdate();
                    }
                } catch (SQLException e) {
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block15;
                    this.plugin.getLogger().warning("Failed to remove cross server update: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public List<IDataStorage.CrossServerUpdate> getCrossServerUpdates(String serverName) {
        ArrayList<IDataStorage.CrossServerUpdate> updates;
        block16: {
            updates = new ArrayList<IDataStorage.CrossServerUpdate>();
            if (this.isConnected()) {
                try (Connection conn = this.getConnection();){
                    boolean hasCreatedAt = this.columnExists(conn, "donut_cross_server_updates", "created_at");
                    String sql = hasCreatedAt ? "SELECT id, team_id, update_type, player_uuid, server_name, created_at FROM donut_cross_server_updates WHERE server_name = ? OR server_name = 'ALL_SERVERS' ORDER BY created_at ASC LIMIT 100" : "SELECT id, team_id, update_type, player_uuid, server_name FROM donut_cross_server_updates WHERE server_name = ? OR server_name = 'ALL_SERVERS' LIMIT 100";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);){
                        stmt.setString(1, serverName);
                        ResultSet rs = stmt.executeQuery();
                        while (rs.next()) {
                            Timestamp timestamp = hasCreatedAt ? rs.getTimestamp("created_at") : new Timestamp(System.currentTimeMillis());
                            updates.add(new IDataStorage.CrossServerUpdate(rs.getInt("id"), rs.getInt("team_id"), rs.getString("update_type"), rs.getString("player_uuid"), rs.getString("server_name"), timestamp));
                        }
                    }
                } catch (SQLException e) {
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block16;
                    this.plugin.getLogger().warning("Failed to get cross server updates: " + e.getMessage());
                }
            }
        }
        return updates;
    }

    @Override
    public void addCrossServerUpdatesBatch(List<IDataStorage.CrossServerUpdate> updates) {
        block16: {
            if (this.isConnected() && !updates.isEmpty()) {
                try (Connection conn = this.getConnection();){
                    String sql = "INSERT INTO donut_cross_server_updates (team_id, update_type, player_uuid, server_name) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);){
                        for (IDataStorage.CrossServerUpdate update : updates) {
                            stmt.setInt(1, update.teamId());
                            stmt.setString(2, update.updateType());
                            stmt.setString(3, update.playerUuid());
                            stmt.setString(4, update.serverName());
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }
                } catch (SQLException e) {
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block16;
                    this.plugin.getLogger().warning("Failed to add cross server updates batch: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void addCrossServerUpdate(int teamId, String updateType, String playerUuid, String serverName) {
        block15: {
            if (this.isConnected()) {
                try (Connection conn = this.getConnection();){
                    String sql = "INSERT INTO donut_cross_server_updates (team_id, update_type, player_uuid, server_name) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);){
                        stmt.setInt(1, teamId);
                        stmt.setString(2, updateType);
                        stmt.setString(3, playerUuid);
                        stmt.setString(4, serverName);
                        stmt.executeUpdate();
                    }
                } catch (SQLException e) {
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block15;
                    this.plugin.getLogger().warning("Failed to add cross server update: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public List<IDataStorage.TeamWarp> getTeamWarps(int teamId) {
        ArrayList<IDataStorage.TeamWarp> warps = new ArrayList<IDataStorage.TeamWarp>();
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "SELECT warp_name, location, server_name, password FROM donut_team_warps WHERE team_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setInt(1, teamId);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        warps.add(new IDataStorage.TeamWarp(rs.getString("warp_name"), rs.getString("location"), rs.getString("server_name"), rs.getString("password")));
                    }
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to get team warps: " + e.getMessage());
            }
        }
        return warps;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<IDataStorage.TeamWarp> getTeamWarp(int teamId, String warpName) {
        if (!this.isConnected()) return Optional.empty();
        try (Connection conn = this.getConnection();){
            String sql = "SELECT warp_name, location, server_name, password FROM donut_team_warps WHERE team_id = ? AND warp_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setInt(1, teamId);
                stmt.setString(2, warpName);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return Optional.empty();
                Optional<IDataStorage.TeamWarp> optional = Optional.of(new IDataStorage.TeamWarp(rs.getString("warp_name"), rs.getString("location"), rs.getString("server_name"), rs.getString("password")));
                return optional;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get team warp: " + e.getMessage());
        }
        return Optional.empty();
    }

    /*
     * Enabled aggressive exception aggregation
     */
    @Override
    public boolean deleteTeamWarp(int teamId, String warpName) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                boolean bl;
                block15: {
                    String sql = "DELETE FROM donut_team_warps WHERE team_id = ? AND warp_name = ?";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    try {
                        stmt.setInt(1, teamId);
                        stmt.setString(2, warpName);
                        boolean bl2 = bl = stmt.executeUpdate() > 0;
                        if (stmt == null) break block15;
                    } catch (Throwable throwable) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        }
                        throw throwable;
                    }
                    stmt.close();
                }
                return bl;
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to delete team warp: " + e.getMessage());
            }
        }
        return false;
    }

    /*
     * Enabled aggressive exception aggregation
     */
    @Override
    public boolean setTeamWarp(int teamId, String warpName, String locationString, String serverName, String password) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                boolean bl;
                block15: {
                    String sql = "mysql".equals(this.storageType) ? "INSERT INTO donut_team_warps (team_id, warp_name, location, server_name, password) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE location = VALUES(location), server_name = VALUES(server_name), password = VALUES(password)" : "MERGE INTO donut_team_warps (team_id, warp_name, location, server_name, password) KEY(team_id, warp_name) VALUES (?, ?, ?, ?, ?)";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    try {
                        stmt.setInt(1, teamId);
                        stmt.setString(2, warpName);
                        stmt.setString(3, locationString);
                        stmt.setString(4, serverName);
                        stmt.setString(5, password);
                        boolean bl2 = bl = stmt.executeUpdate() > 0;
                        if (stmt == null) break block15;
                    } catch (Throwable throwable) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        }
                        throw throwable;
                    }
                    stmt.close();
                }
                return bl;
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to set team warp: " + e.getMessage());
            }
        }
        return false;
    }

    /*
     * Enabled aggressive exception aggregation
     */
    @Override
    public boolean teamWarpExists(int teamId, String warpName) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                boolean bl;
                block15: {
                    String sql = "SELECT 1 FROM donut_team_warps WHERE team_id = ? AND warp_name = ?";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    try {
                        stmt.setInt(1, teamId);
                        stmt.setString(2, warpName);
                        ResultSet rs = stmt.executeQuery();
                        bl = rs.next();
                        if (stmt == null) break block15;
                    } catch (Throwable throwable) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        }
                        throw throwable;
                    }
                    stmt.close();
                }
                return bl;
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to check team warp existence: " + e.getMessage());
            }
        }
        return false;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public int getTeamWarpCount(int teamId) {
        if (!this.isConnected()) return 0;
        try (Connection conn = this.getConnection();){
            String sql = "SELECT COUNT(*) FROM donut_team_warps WHERE team_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setInt(1, teamId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return 0;
                int n = rs.getInt(1);
                return n;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get team warp count: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public List<IDataStorage.TeamWarp> getWarps(int teamId) {
        return this.getTeamWarps(teamId);
    }

    @Override
    public Optional<IDataStorage.TeamWarp> getWarp(int teamId, String warpName) {
        return this.getTeamWarp(teamId, warpName);
    }

    @Override
    public void deleteWarp(int teamId, String warpName) {
        this.deleteTeamWarp(teamId, warpName);
    }

    @Override
    public void setWarp(int teamId, String warpName, Location location, String serverName, String password) {
        String locationString = location.getWorld().getName() + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ() + ":" + location.getYaw() + ":" + location.getPitch();
        this.setTeamWarp(teamId, warpName, locationString, serverName, password);
    }

    @Override
    public void clearAllJoinRequests(UUID playerUuid) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "DELETE FROM donut_join_requests WHERE player_uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setString(1, playerUuid.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to clear all join requests: " + e.getMessage());
            }
        }
    }

    /*
     * Enabled aggressive exception aggregation
     */
    @Override
    public boolean hasJoinRequest(int teamId, UUID playerUuid) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                boolean bl;
                block15: {
                    String sql = "SELECT 1 FROM donut_join_requests WHERE team_id = ? AND player_uuid = ?";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    try {
                        stmt.setInt(1, teamId);
                        stmt.setString(2, playerUuid.toString());
                        ResultSet rs = stmt.executeQuery();
                        bl = rs.next();
                        if (stmt == null) break block15;
                    } catch (Throwable throwable) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        }
                        throw throwable;
                    }
                    stmt.close();
                }
                return bl;
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to check join request: " + e.getMessage());
            }
        }
        return false;
    }

    @Override
    public List<UUID> getJoinRequests(int teamId) {
        ArrayList<UUID> requests = new ArrayList<UUID>();
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "SELECT player_uuid FROM donut_join_requests WHERE team_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setInt(1, teamId);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        requests.add(UUID.fromString(rs.getString("player_uuid")));
                    }
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to get join requests: " + e.getMessage());
            }
        }
        return requests;
    }

    @Override
    public void removeJoinRequest(int teamId, UUID playerUuid) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "DELETE FROM donut_join_requests WHERE team_id = ? AND player_uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setInt(1, teamId);
                    stmt.setString(2, playerUuid.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to remove join request: " + e.getMessage());
            }
        }
    }

    @Override
    public void addJoinRequest(int teamId, UUID playerUuid) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "INSERT INTO donut_join_requests (team_id, player_uuid) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setInt(1, teamId);
                    stmt.setString(2, playerUuid.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to add join request: " + e.getMessage());
            }
        }
    }

    @Override
    public void updateMemberEditingPermissions(int teamId, UUID memberUuid, boolean canEditMembers, boolean canEditCoOwners, boolean canKickMembers, boolean canPromoteMembers, boolean canDemoteMembers) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "UPDATE donut_team_members SET can_edit_members = ?, can_edit_co_owners = ?, can_kick_members = ?, can_promote_members = ?, can_demote_members = ? WHERE team_id = ? AND player_uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setBoolean(1, canEditMembers);
                    stmt.setBoolean(2, canEditCoOwners);
                    stmt.setBoolean(3, canKickMembers);
                    stmt.setBoolean(4, canPromoteMembers);
                    stmt.setBoolean(5, canDemoteMembers);
                    stmt.setInt(6, teamId);
                    stmt.setString(7, memberUuid.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to update member editing permissions: " + e.getMessage());
            }
        }
    }

    @Override
    public void setPublicStatus(int teamId, boolean isPublic) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "UPDATE donut_teams SET is_public = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setBoolean(1, isPublic);
                    stmt.setInt(2, teamId);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to set public status: " + e.getMessage());
            }
        }
    }

    @Override
    public void deleteTeamHome(int teamId) {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                String sql = "UPDATE donut_teams SET home_location = NULL, home_server = NULL WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setInt(1, teamId);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to delete team home: " + e.getMessage());
            }
        }
    }

    @Override
    public void cleanup() {
        if (this.isConnected()) {
            try (Connection conn = this.getConnection();){
                if (this.tableExists(conn, "donut_cross_server_updates") && this.tableExists(conn, "donut_cross_server_messages")) {
                    if (this.columnExists(conn, "donut_cross_server_updates", "created_at") && this.columnExists(conn, "donut_cross_server_messages", "created_at")) {
                        if ("mysql".equals(this.storageType)) {
                            conn.createStatement().execute("DELETE FROM donut_cross_server_updates WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)");
                            conn.createStatement().execute("DELETE FROM donut_cross_server_messages WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY)");
                        } else {
                            conn.createStatement().execute("DELETE FROM donut_cross_server_updates WHERE created_at < DATEADD(DAY, -7, NOW())");
                            conn.createStatement().execute("DELETE FROM donut_cross_server_messages WHERE created_at < DATEADD(DAY, -7, NOW())");
                        }
                    } else {
                        this.plugin.getLogger().info("created_at column not found in cross-server tables. Skipping cleanup until migration is complete.");
                    }
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("Failed to cleanup old data: " + e.getMessage());
            }
        }
    }

    public void optimizeDatabase() {
        if (!this.isConnected()) {
            this.plugin.getLogger().warning("Cannot optimize database - not connected");
            return;
        }
        try (Connection conn = this.getConnection();){
            if ("mysql".equals(this.storageType)) {
                conn.createStatement().execute("OPTIMIZE TABLE donut_teams, donut_team_members, donut_team_invites, donut_team_blacklist, donut_team_settings, donut_team_warps, donut_team_enderchest_locks, donut_servers, donut_cross_server_updates, donut_cross_server_messages, donut_player_cache");
                this.plugin.getLogger().info("MySQL database optimization completed");
            } else {
                conn.createStatement().execute("ANALYZE");
                this.plugin.getLogger().info("H2 database analysis completed");
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to optimize database: " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<UUID> getPlayerUuidByName(String playerName) {
        if (!this.isConnected()) return Optional.empty();
        if (playerName == null) return Optional.empty();
        if (playerName.isEmpty()) {
            return Optional.empty();
        }
        try (Connection conn = this.getConnection();){
            String sql = "SELECT player_uuid FROM donut_player_cache WHERE LOWER(player_name) = LOWER(?) ORDER BY last_seen DESC LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, playerName);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return Optional.empty();
                Optional<UUID> optional = Optional.of(UUID.fromString(rs.getString("player_uuid")));
                return optional;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get player UUID by name: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void cachePlayerName(UUID playerUuid, String playerName) {
        block15: {
            if (!this.isConnected() || playerUuid == null || playerName == null || playerName.isEmpty()) {
                return;
            }
            try (Connection conn = this.getConnection();){
                String sql = "mysql".equals(this.storageType) ? "INSERT INTO donut_player_cache (player_uuid, player_name, last_seen) VALUES (?, ?, NOW()) ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), last_seen = NOW()" : "MERGE INTO donut_player_cache (player_uuid, player_name, last_seen) KEY(player_uuid) VALUES (?, ?, NOW())";
                try (PreparedStatement stmt = conn.prepareStatement(sql);){
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, playerName);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                if (!this.plugin.getConfigManager().isDebugEnabled()) break block15;
                this.plugin.getLogger().warning("Failed to cache player name: " + e.getMessage());
            }
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<String> getPlayerNameByUuid(UUID playerUuid) {
        if (!this.isConnected()) return Optional.empty();
        if (playerUuid == null) {
            return Optional.empty();
        }
        try (Connection conn = this.getConnection();){
            String sql = "SELECT player_name FROM donut_player_cache WHERE player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return Optional.empty();
                Optional<String> optional = Optional.of(rs.getString("player_name"));
                return optional;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get player name by UUID: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void addTeamInvite(int teamId, UUID playerUuid, UUID inviterUuid) {
        if (!this.isConnected() || playerUuid == null || inviterUuid == null) {
            return;
        }
        try (Connection conn = this.getConnection();){
            String sql = "mysql".equals(this.storageType) ? "INSERT INTO donut_team_invites (team_id, player_uuid, inviter_uuid) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE inviter_uuid = VALUES(inviter_uuid), created_at = CURRENT_TIMESTAMP" : "MERGE INTO donut_team_invites (team_id, player_uuid, inviter_uuid) KEY(team_id, player_uuid) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setInt(1, teamId);
                stmt.setString(2, playerUuid.toString());
                stmt.setString(3, inviterUuid.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to add team invite: " + e.getMessage());
        }
    }

    @Override
    public void removeTeamInvite(int teamId, UUID playerUuid) {
        if (!this.isConnected() || playerUuid == null) {
            return;
        }
        try (Connection conn = this.getConnection();){
            String sql = "DELETE FROM donut_team_invites WHERE team_id = ? AND player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setInt(1, teamId);
                stmt.setString(2, playerUuid.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to remove team invite: " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive exception aggregation
     */
    @Override
    public boolean hasTeamInvite(int teamId, UUID playerUuid) {
        if (!this.isConnected() || playerUuid == null) {
            return false;
        }
        try (Connection conn = this.getConnection();){
            boolean bl;
            block15: {
                String sql = "SELECT 1 FROM donut_team_invites WHERE team_id = ? AND player_uuid = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                try {
                    stmt.setInt(1, teamId);
                    stmt.setString(2, playerUuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    bl = rs.next();
                    if (stmt == null) break block15;
                } catch (Throwable throwable) {
                    if (stmt != null) {
                        try {
                            stmt.close();
                        } catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                stmt.close();
            }
            return bl;
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to check team invite: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<Integer> getPlayerInvites(UUID playerUuid) {
        ArrayList<Integer> teamIds = new ArrayList<Integer>();
        if (!this.isConnected() || playerUuid == null) {
            return teamIds;
        }
        try (Connection conn = this.getConnection();){
            String sql = "SELECT team_id FROM donut_team_invites WHERE player_uuid = ? ORDER BY created_at DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    teamIds.add(rs.getInt("team_id"));
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get player invites: " + e.getMessage());
        }
        return teamIds;
    }

    @Override
    public void clearPlayerInvites(UUID playerUuid) {
        if (!this.isConnected() || playerUuid == null) {
            return;
        }
        try (Connection conn = this.getConnection();){
            String sql = "DELETE FROM donut_team_invites WHERE player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, playerUuid.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to clear player invites: " + e.getMessage());
        }
    }

    @Override
    public List<IDataStorage.TeamInvite> getPlayerInvitesWithDetails(UUID playerUuid) {
        ArrayList<IDataStorage.TeamInvite> invites = new ArrayList<IDataStorage.TeamInvite>();
        if (!this.isConnected() || playerUuid == null) {
            return invites;
        }
        try (Connection conn = this.getConnection();){
            String sql = "SELECT i.team_id, t.name as team_name, i.inviter_uuid, i.created_at FROM donut_team_invites i JOIN donut_teams t ON i.team_id = t.id WHERE i.player_uuid = ? ORDER BY i.created_at DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    int teamId = rs.getInt("team_id");
                    String teamName = rs.getString("team_name");
                    UUID inviterUuid = UUID.fromString(rs.getString("inviter_uuid"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    String inviterName = this.getPlayerNameByUuid(inviterUuid).orElse("Unknown");
                    invites.add(new IDataStorage.TeamInvite(teamId, teamName, inviterUuid, inviterName, createdAt));
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get player invites with details: " + e.getMessage());
        }
        return invites;
    }

    @Override
    public void updatePlayerSession(UUID playerUuid, String serverName) {
        if (!this.isConnected() || playerUuid == null || serverName == null) {
            return;
        }
        try (Connection conn = this.getConnection();){
            String sql = "mysql".equals(this.storageType) ? "INSERT INTO donut_player_sessions (player_uuid, server_name, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE server_name = VALUES(server_name), last_seen = CURRENT_TIMESTAMP" : "MERGE INTO donut_player_sessions (player_uuid, server_name, last_seen) KEY(player_uuid) VALUES (?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, serverName);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to update player session: " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<IDataStorage.PlayerSession> getPlayerSession(UUID playerUuid) {
        if (!this.isConnected()) return Optional.empty();
        if (playerUuid == null) {
            return Optional.empty();
        }
        try (Connection conn = this.getConnection();){
            String sql = "SELECT player_uuid, server_name, last_seen FROM donut_player_sessions WHERE player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return Optional.empty();
                Optional<IDataStorage.PlayerSession> optional = Optional.of(new IDataStorage.PlayerSession(UUID.fromString(rs.getString("player_uuid")), rs.getString("server_name"), rs.getTimestamp("last_seen")));
                return optional;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get player session: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Map<UUID, IDataStorage.PlayerSession> getTeamPlayerSessions(int teamId) {
        HashMap<UUID, IDataStorage.PlayerSession> sessions = new HashMap<UUID, IDataStorage.PlayerSession>();
        if (!this.isConnected()) {
            return sessions;
        }
        try (Connection conn = this.getConnection();){
            String sql = "SELECT s.player_uuid, s.server_name, s.last_seen FROM donut_player_sessions s JOIN donut_team_members m ON s.player_uuid = m.player_uuid WHERE m.team_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setInt(1, teamId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    IDataStorage.PlayerSession session = new IDataStorage.PlayerSession(playerUuid, rs.getString("server_name"), rs.getTimestamp("last_seen"));
                    sessions.put(playerUuid, session);
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get team player sessions: " + e.getMessage());
        }
        return sessions;
    }

    @Override
    public void cleanupStaleSessions(int minutesOld) {
        if (!this.isConnected()) {
            return;
        }
        try (Connection conn = this.getConnection();){
            String sql = "mysql".equals(this.storageType) ? "DELETE FROM donut_player_sessions WHERE last_seen < DATE_SUB(NOW(), INTERVAL ? MINUTE)" : "DELETE FROM donut_player_sessions WHERE last_seen < DATEADD('MINUTE', ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setInt(1, -minutesOld);
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    this.plugin.getLogger().info("Cleaned up " + deleted + " stale player sessions");
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to cleanup stale sessions: " + e.getMessage());
        }
    }

    @Override
    public void setServerAlias(String serverName, String alias) {
        if (!this.isConnected() || serverName == null || alias == null) {
            return;
        }
        try (Connection conn = this.getConnection();){
            String sql = "INSERT INTO donut_server_aliases (server_name, alias, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE alias = ?, updated_at = CURRENT_TIMESTAMP";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, serverName);
                stmt.setString(2, alias);
                stmt.setString(3, alias);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to set server alias: " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<String> getServerAlias(String serverName) {
        if (!this.isConnected()) return Optional.empty();
        if (serverName == null) {
            return Optional.empty();
        }
        try (Connection conn = this.getConnection();){
            String sql = "SELECT alias FROM donut_server_aliases WHERE server_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, serverName);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return Optional.empty();
                Optional<String> optional = Optional.of(rs.getString("alias"));
                return optional;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get server alias: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Map<String, String> getAllServerAliases() {
        HashMap<String, String> aliases = new HashMap<String, String>();
        if (!this.isConnected()) {
            return aliases;
        }
        try (Connection conn = this.getConnection();){
            String sql = "SELECT server_name, alias FROM donut_server_aliases";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    aliases.put(rs.getString("server_name"), rs.getString("alias"));
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get all server aliases: " + e.getMessage());
        }
        return aliases;
    }

    @Override
    public void removeServerAlias(String serverName) {
        if (!this.isConnected() || serverName == null) {
            return;
        }
        try (Connection conn = this.getConnection();){
            String sql = "DELETE FROM donut_server_aliases WHERE server_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, serverName);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to remove server alias: " + e.getMessage());
        }
    }

    @Override
    public void setTeamRenameTimestamp(int teamId, Timestamp timestamp) {
        if (!this.isConnected()) {
            return;
        }
        try (Connection conn = this.getConnection();){
            String sql = "INSERT INTO donut_team_rename_cooldowns (team_id, last_rename) VALUES (?, ?) ON DUPLICATE KEY UPDATE last_rename = ?";
            if (this.storageType.equals("h2")) {
                sql = "MERGE INTO donut_team_rename_cooldowns (team_id, last_rename) KEY(team_id) VALUES (?, ?)";
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setInt(1, teamId);
                stmt.setTimestamp(2, timestamp);
                if (!this.storageType.equals("h2")) {
                    stmt.setTimestamp(3, timestamp);
                }
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to set team rename timestamp: " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public Optional<Timestamp> getTeamRenameTimestamp(int teamId) {
        if (!this.isConnected()) {
            return Optional.empty();
        }
        try (Connection conn = this.getConnection();){
            String sql = "SELECT last_rename FROM donut_team_rename_cooldowns WHERE team_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setInt(1, teamId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) return Optional.empty();
                Optional<Timestamp> optional = Optional.of(rs.getTimestamp("last_rename"));
                return optional;
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to get team rename timestamp: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void setTeamName(int teamId, String newName) {
        if (!this.isConnected() || newName == null || newName.isEmpty()) {
            return;
        }
        try (Connection conn = this.getConnection();){
            String sql = "UPDATE donut_teams SET name = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql);){
                stmt.setString(1, newName);
                stmt.setInt(2, teamId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    this.plugin.getLogger().info("Team ID " + teamId + " renamed to: " + newName);
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to set team name: " + e.getMessage());
        }
    }
}

