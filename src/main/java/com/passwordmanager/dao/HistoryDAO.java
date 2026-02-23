package com.passwordmanager.dao;

import com.passwordmanager.crypto.CryptoUtil;
import com.passwordmanager.db.DatabaseManager;
import com.passwordmanager.model.PasswordHistory;

import javax.crypto.SecretKey;
import java.sql.*;
import java.util.*;

/** Manages the password_history table (last 5 versions per LOGIN entry). */
public class HistoryDAO {

    private static final int MAX_HISTORY = 5;

    /**
     * Saves a password into history and prunes entries beyond MAX_HISTORY.
     * Call this BEFORE updating the current password of an entry.
     */
    public void save(long entryId, String plainPassword, SecretKey key) throws Exception {
        byte[] encrypted = CryptoUtil.encryptString(plainPassword, key);
        String ins = "INSERT INTO password_history (entry_id, value_enc, changed_at) VALUES (?, ?, ?)";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(ins)) {
            ps.setLong(1, entryId);
            ps.setBytes(2, encrypted);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
        prune(entryId);
    }

    /** Returns the last MAX_HISTORY passwords for an entry (newest first). */
    public List<PasswordHistory> findByEntry(long entryId, SecretKey key) throws Exception {
        List<PasswordHistory> list = new ArrayList<>();
        String sql = "SELECT id, entry_id, value_enc, changed_at "
                   + "FROM password_history WHERE entry_id = ? ORDER BY changed_at DESC";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String plain = CryptoUtil.decryptString(rs.getBytes("value_enc"), key);
                    list.add(new PasswordHistory(
                            rs.getLong("id"),
                            rs.getLong("entry_id"),
                            plain,
                            rs.getLong("changed_at")
                    ));
                }
            }
        }
        return list;
    }

    // Delete oldest records so only MAX_HISTORY remain
    private void prune(long entryId) throws SQLException {
        String sql = """
            DELETE FROM password_history WHERE entry_id = ?
            AND id NOT IN (
                SELECT id FROM (
                    SELECT id FROM password_history
                    WHERE entry_id = ?
                    ORDER BY changed_at DESC
                    LIMIT ?
                ) AS keep
            )
            """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            ps.setLong(2, entryId);
            ps.setInt(3, MAX_HISTORY);
            ps.executeUpdate();
        }
    }
}
