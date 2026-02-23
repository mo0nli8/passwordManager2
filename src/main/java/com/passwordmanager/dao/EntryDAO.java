package com.passwordmanager.dao;

import com.passwordmanager.db.DatabaseManager;
import com.passwordmanager.model.*;

import java.sql.*;
import java.util.*;

public class EntryDAO {

    // ── Create ────────────────────────────────────────────────────────────────

    /** Inserts a new entry and returns the generated id. */
    public long insert(EntryType type, String title, Long categoryId,
                       boolean favorite, long now) throws SQLException {
        String sql = """
            INSERT INTO entries (type_id, title, category_id, favorite, created_at, updated_at)
            VALUES ((SELECT id FROM entry_types WHERE name = ?), ?, ?, ?, ?, ?)
            """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type.name());
            ps.setString(2, title);
            if (categoryId != null) ps.setLong(3, categoryId); else ps.setNull(3, Types.BIGINT);
            ps.setBoolean(4, favorite);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public Entry findById(long id) throws SQLException {
        String sql = """
            SELECT e.id, et.name AS type_name, e.title, e.category_id,
                   e.favorite, e.created_at, e.updated_at
            FROM entries e
            JOIN entry_types et ON et.id = e.type_id
            WHERE e.id = ?
            """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapEntry(rs) : null;
            }
        }
    }

    /** Returns all entries as list items (no decryption needed). */
    public List<EntryListItem> findAll() throws SQLException {
        String sql = """
            SELECT e.id, et.name AS type_name, e.title, c.name AS cat_name,
                   e.favorite, e.updated_at
            FROM entries e
            JOIN entry_types et ON et.id = e.type_id
            LEFT JOIN categories c ON c.id = e.category_id
            ORDER BY e.title
            """;
        return queryListItems(sql);
    }

    /** Search by title (SQL LIKE). */
    public List<EntryListItem> searchByTitle(String query) throws SQLException {
        String sql = """
            SELECT e.id, et.name AS type_name, e.title, c.name AS cat_name,
                   e.favorite, e.updated_at
            FROM entries e
            JOIN entry_types et ON et.id = e.type_id
            LEFT JOIN categories c ON c.id = e.category_id
            WHERE e.title LIKE ?
            ORDER BY e.title
            """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            return mapListItems(ps.executeQuery());
        }
    }

    /** Filter by category id. */
    public List<EntryListItem> findByCategory(int categoryId) throws SQLException {
        String sql = """
            SELECT e.id, et.name AS type_name, e.title, c.name AS cat_name,
                   e.favorite, e.updated_at
            FROM entries e
            JOIN entry_types et ON et.id = e.type_id
            LEFT JOIN categories c ON c.id = e.category_id
            WHERE e.category_id = ?
            ORDER BY e.title
            """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            return mapListItems(ps.executeQuery());
        }
    }

    /** Favorites only. */
    public List<EntryListItem> findFavorites() throws SQLException {
        String sql = """
            SELECT e.id, et.name AS type_name, e.title, c.name AS cat_name,
                   e.favorite, e.updated_at
            FROM entries e
            JOIN entry_types et ON et.id = e.type_id
            LEFT JOIN categories c ON c.id = e.category_id
            WHERE e.favorite = 1
            ORDER BY e.title
            """;
        return queryListItems(sql);
    }

    /** All LOGIN entries – used by the audit service. */
    public List<Entry> findAllLogins() throws SQLException {
        String sql = """
            SELECT e.id, et.name AS type_name, e.title, e.category_id,
                   e.favorite, e.created_at, e.updated_at
            FROM entries e
            JOIN entry_types et ON et.id = e.type_id
            WHERE et.name = 'LOGIN'
            """;
        List<Entry> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapEntry(rs));
        }
        return list;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void update(long id, String title, Long categoryId,
                       boolean favorite, long now) throws SQLException {
        String sql = "UPDATE entries SET title=?, category_id=?, favorite=?, updated_at=? WHERE id=?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            if (categoryId != null) ps.setLong(2, categoryId); else ps.setNull(2, Types.BIGINT);
            ps.setBoolean(3, favorite);
            ps.setLong(4, now);
            ps.setLong(5, id);
            ps.executeUpdate();
        }
    }

    public void toggleFavorite(long id, boolean favorite) throws SQLException {
        String sql = "UPDATE entries SET favorite=? WHERE id=?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, favorite);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM entries WHERE id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<EntryListItem> queryListItems(String sql) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapListItems(rs);
        }
    }

    private List<EntryListItem> mapListItems(ResultSet rs) throws SQLException {
        List<EntryListItem> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new EntryListItem(
                    rs.getLong("id"),
                    EntryType.from(rs.getString("type_name")),
                    rs.getString("title"),
                    rs.getString("cat_name"),
                    rs.getBoolean("favorite"),
                    rs.getLong("updated_at")
            ));
        }
        return list;
    }

    private Entry mapEntry(ResultSet rs) throws SQLException {
        long catId = rs.getLong("category_id");
        return new Entry(
                rs.getLong("id"),
                EntryType.from(rs.getString("type_name")),
                rs.getString("title"),
                rs.wasNull() ? null : catId,
                rs.getBoolean("favorite"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
