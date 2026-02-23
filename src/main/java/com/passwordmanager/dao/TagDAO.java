package com.passwordmanager.dao;

import com.passwordmanager.db.DatabaseManager;
import com.passwordmanager.model.Tag;

import java.sql.*;
import java.util.*;

public class TagDAO {

    public List<Tag> findAll() throws SQLException {
        List<Tag> list = new ArrayList<>();
        String sql = "SELECT id, name FROM tags ORDER BY name";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new Tag(rs.getInt("id"), rs.getString("name")));
        }
        return list;
    }

    public Tag findOrCreate(String name) throws SQLException {
        String sel = "SELECT id, name FROM tags WHERE name = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sel)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new Tag(rs.getInt("id"), rs.getString("name"));
            }
        }
        String ins = "INSERT INTO tags (name) VALUES (?)";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Tag(keys.getInt(1), name);
            }
        }
    }

    /** Returns tag names attached to an entry. */
    public List<String> findNamesByEntry(long entryId) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT t.name FROM tags t "
                   + "JOIN entry_tags et ON et.tag_id = t.id "
                   + "WHERE et.entry_id = ? ORDER BY t.name";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString("name"));
            }
        }
        return names;
    }

    /** Replaces all tags for an entry with the provided list. */
    public void setTagsForEntry(long entryId, List<String> tagNames) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Remove existing associations
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM entry_tags WHERE entry_id = ?")) {
                    del.setLong(1, entryId);
                    del.executeUpdate();
                }
                // Re-insert
                for (String name : tagNames) {
                    Tag tag = findOrCreate(name.trim());
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT IGNORE INTO entry_tags (entry_id, tag_id) VALUES (?, ?)")) {
                        ins.setLong(1, entryId);
                        ins.setInt(2, tag.getId());
                        ins.executeUpdate();
                    }
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
}
