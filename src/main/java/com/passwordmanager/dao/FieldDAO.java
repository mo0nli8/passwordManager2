package com.passwordmanager.dao;

import com.passwordmanager.crypto.CryptoUtil;
import com.passwordmanager.db.DatabaseManager;

import javax.crypto.SecretKey;
import java.sql.*;
import java.util.*;

/**
 * Reads and writes encrypted fields in the entry_fields table.
 * Every value is individually AES-256-GCM encrypted.
 */
public class FieldDAO {

    /** Encrypts and stores all fields for an entry (replaces existing). */
    public void setFields(long entryId, Map<String, String> fields, SecretKey key) throws Exception {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Delete existing fields
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM entry_fields WHERE entry_id = ?")) {
                    del.setLong(1, entryId);
                    del.executeUpdate();
                }
                // Insert encrypted fields
                String ins = "INSERT INTO entry_fields (entry_id, field_key, value_enc) VALUES (?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(ins)) {
                    for (Map.Entry<String, String> entry : fields.entrySet()) {
                        if (entry.getValue() == null || entry.getValue().isBlank()) continue;
                        byte[] encrypted = CryptoUtil.encryptString(entry.getValue(), key);
                        ps.setLong(1, entryId);
                        ps.setString(2, entry.getKey());
                        ps.setBytes(3, encrypted);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Decrypts and returns all fields for an entry as a plain-text map. */
    public Map<String, String> getFields(long entryId, SecretKey key) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        String sql = "SELECT field_key, value_enc FROM entry_fields WHERE entry_id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fieldKey = rs.getString("field_key");
                    byte[] enc      = rs.getBytes("value_enc");
                    result.put(fieldKey, CryptoUtil.decryptString(enc, key));
                }
            }
        }
        return result;
    }

    /** Returns the encrypted bytes for one specific field (used by audit/history). */
    public byte[] getRawField(long entryId, String fieldKey) throws SQLException {
        String sql = "SELECT value_enc FROM entry_fields WHERE entry_id = ? AND field_key = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            ps.setString(2, fieldKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes("value_enc") : null;
            }
        }
    }
}
