package com.passwordmanager.dao;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.passwordmanager.db.DatabaseManager;

import java.sql.*;
import java.util.*;

/** Stores and verifies one-time backup codes (bcrypt-hashed). */
public class BackupCodeDAO {

    /**
     * Replaces all existing backup codes with freshly hashed versions of the given plaintext codes.
     */
    public void replaceAll(List<String> plainCodes) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM backup_codes")) {
                    del.executeUpdate();
                }
                String ins = "INSERT INTO backup_codes (code_hash, used) VALUES (?, 0)";
                try (PreparedStatement ps = c.prepareStatement(ins)) {
                    for (String code : plainCodes) {
                        String hash = BCrypt.withDefaults().hashToString(12, code.toCharArray());
                        ps.setString(1, hash);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Checks if the plain code matches any unused backup code.
     * If matched, marks that code as used.
     *
     * @return true if valid and unused
     */
    public boolean verifyAndConsume(String plainCode) throws SQLException {
        String sel = "SELECT id, code_hash FROM backup_codes WHERE used = 0";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sel);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int    id   = rs.getInt("id");
                String hash = rs.getString("code_hash");
                BCrypt.Result result = BCrypt.verifyer().verify(plainCode.toCharArray(), hash);
                if (result.verified) {
                    markUsed(id);
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns how many unused backup codes remain. */
    public int countUnused() throws SQLException {
        String sql = "SELECT COUNT(*) FROM backup_codes WHERE used = 0";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void markUsed(int id) throws SQLException {
        String sql = "UPDATE backup_codes SET used = 1 WHERE id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
