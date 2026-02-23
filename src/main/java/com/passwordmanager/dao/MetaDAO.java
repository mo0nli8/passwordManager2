package com.passwordmanager.dao;

import com.passwordmanager.db.DatabaseManager;

import java.sql.*;

/** Reads and writes key-value pairs in the vault_meta table. */
public class MetaDAO {

    public String get(String key) throws SQLException {
        String sql = "SELECT value FROM vault_meta WHERE key_name = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        }
    }

    public void set(String key, String value) throws SQLException {
        String sql = "INSERT INTO vault_meta (key_name, value) VALUES (?, ?) "
                   + "ON DUPLICATE KEY UPDATE value = VALUES(value)";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public boolean exists(String key) throws SQLException {
        return get(key) != null;
    }
}
