package com.passwordmanager.db;

import com.passwordmanager.config.ConfigLoader;
import com.zaxxer.hikari.*;

import java.sql.*;

/**
 * Initialises the HikariCP connection pool and creates/migrates the MySQL schema.
 *
 * Call {@link #init()} once at application startup, then use {@link #getConnection()}
 * anywhere in the DAO layer.
 */
public final class DatabaseManager {

    private static HikariDataSource dataSource;

    private DatabaseManager() {}

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    public static void init() throws SQLException {
        String host     = ConfigLoader.get("db.host",     "localhost");
        String port     = ConfigLoader.get("db.port",     "3306");
        String dbName   = ConfigLoader.get("db.name",     "password_manager");
        String user     = ConfigLoader.get("db.user",     "root");
        String password = ConfigLoader.get("db.password", "");

        // Ensure the database itself exists (connect without schema first)
        String rootUrl = String.format(
                "jdbc:mysql://%s:%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                host, port);
        try (Connection c = DriverManager.getConnection(rootUrl, user, password);
             Statement  s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + dbName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        // Now configure HikariCP for the target database
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                host, port, dbName));
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(10);
        cfg.setConnectionTimeout(5_000);
        cfg.setPoolName("PM-Pool");

        dataSource = new HikariDataSource(cfg);
        createSchema();
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) throw new IllegalStateException("DatabaseManager not initialized");
        return dataSource.getConnection();
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    // ── Schema creation ───────────────────────────────────────────────────────

    private static void createSchema() throws SQLException {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {

            // Vault-level metadata (salt, iterations, TOTP secret, etc.)
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS vault_meta (
                    key_name  VARCHAR(64)  NOT NULL,
                    value     TEXT         NOT NULL,
                    PRIMARY KEY (key_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Entry type lookup
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS entry_types (
                    id    INT          NOT NULL AUTO_INCREMENT,
                    name  VARCHAR(32)  NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uq_entry_types_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Seed entry types
            s.executeUpdate("""
                INSERT IGNORE INTO entry_types (name) VALUES
                    ('LOGIN'), ('NOTE'), ('CARD'), ('IDENTITY')
                """);

            // User-defined categories
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS categories (
                    id    INT          NOT NULL AUTO_INCREMENT,
                    name  VARCHAR(64)  NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uq_categories_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // User-defined tags
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tags (
                    id    INT          NOT NULL AUTO_INCREMENT,
                    name  VARCHAR(64)  NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uq_tags_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Main entries
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS entries (
                    id           BIGINT       NOT NULL AUTO_INCREMENT,
                    type_id      INT          NOT NULL,
                    title        VARCHAR(255) NOT NULL,
                    category_id  INT,
                    favorite     TINYINT(1)   NOT NULL DEFAULT 0,
                    created_at   BIGINT       NOT NULL,
                    updated_at   BIGINT       NOT NULL,
                    PRIMARY KEY (id),
                    INDEX idx_entries_title (title),
                    CONSTRAINT fk_entries_type     FOREIGN KEY (type_id)     REFERENCES entry_types(id),
                    CONSTRAINT fk_entries_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Encrypted field store (flexible per-type key-value pairs)
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS entry_fields (
                    id         BIGINT       NOT NULL AUTO_INCREMENT,
                    entry_id   BIGINT       NOT NULL,
                    field_key  VARCHAR(64)  NOT NULL,
                    value_enc  BLOB         NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uq_entry_fields (entry_id, field_key),
                    CONSTRAINT fk_fields_entry FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Password history (LOGIN entries only)
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS password_history (
                    id          BIGINT  NOT NULL AUTO_INCREMENT,
                    entry_id    BIGINT  NOT NULL,
                    value_enc   BLOB    NOT NULL,
                    changed_at  BIGINT  NOT NULL,
                    PRIMARY KEY (id),
                    INDEX idx_history_entry (entry_id),
                    CONSTRAINT fk_history_entry FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Entry ↔ Tag (many-to-many)
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS entry_tags (
                    entry_id  BIGINT  NOT NULL,
                    tag_id    INT     NOT NULL,
                    PRIMARY KEY (entry_id, tag_id),
                    CONSTRAINT fk_et_entry FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE,
                    CONSTRAINT fk_et_tag   FOREIGN KEY (tag_id)   REFERENCES tags(id)    ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Backup codes (bcrypt-hashed one-time codes)
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS backup_codes (
                    id         INT          NOT NULL AUTO_INCREMENT,
                    code_hash  VARCHAR(60)  NOT NULL,
                    used       TINYINT(1)   NOT NULL DEFAULT 0,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        }
    }
}
