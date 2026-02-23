package com.passwordmanager.service;

import com.passwordmanager.dao.*;
import com.passwordmanager.model.*;

import javax.crypto.SecretKey;
import java.util.*;

/**
 * Business logic for all vault CRUD operations.
 * Encrypts/decrypts field values using the session key from AuthService.
 */
public class VaultService {

    private final EntryDAO    entryDAO    = new EntryDAO();
    private final FieldDAO    fieldDAO    = new FieldDAO();
    private final HistoryDAO  historyDAO  = new HistoryDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final TagDAO      tagDAO      = new TagDAO();

    // ── Create ────────────────────────────────────────────────────────────────

    public long createEntry(EntryDto dto, SecretKey key) throws Exception {
        Long catId = resolveCategory(dto.getCategoryName());
        long now   = System.currentTimeMillis();

        long entryId = entryDAO.insert(dto.getType(), dto.getTitle(), catId, dto.isFavorite(), now);
        fieldDAO.setFields(entryId, dto.getFields(), key);
        tagDAO.setTagsForEntry(entryId, dto.getTags());
        return entryId;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public EntryDto getEntry(long entryId, SecretKey key) throws Exception {
        Entry entry = entryDAO.findById(entryId);
        if (entry == null) return null;
        return toDto(entry, key);
    }

    public List<EntryListItem> listAll() throws Exception {
        return entryDAO.findAll();
    }

    public List<EntryListItem> search(String query) throws Exception {
        return entryDAO.searchByTitle(query);
    }

    public List<EntryListItem> listByCategory(int categoryId) throws Exception {
        return entryDAO.findByCategory(categoryId);
    }

    public List<EntryListItem> listFavorites() throws Exception {
        return entryDAO.findFavorites();
    }

    public List<Category> listCategories() throws Exception {
        return categoryDAO.findAll();
    }

    public List<Tag> listTags() throws Exception {
        return tagDAO.findAll();
    }

    // ── Password history ──────────────────────────────────────────────────────

    public List<PasswordHistory> getHistory(long entryId, SecretKey key) throws Exception {
        return historyDAO.findByEntry(entryId, key);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public void updateEntry(EntryDto dto, SecretKey key) throws Exception {
        Entry existing = entryDAO.findById(dto.getId());
        if (existing == null) throw new IllegalArgumentException("Entry not found: " + dto.getId());

        // If it's a LOGIN entry and the password changed, push old password to history
        if (existing.getType() == EntryType.LOGIN) {
            Map<String, String> oldFields = fieldDAO.getFields(dto.getId(), key);
            String oldPw  = oldFields.getOrDefault("password", "");
            String newPw  = dto.getFields().getOrDefault("password", "");
            if (!oldPw.isBlank() && !oldPw.equals(newPw)) {
                historyDAO.save(dto.getId(), oldPw, key);
            }
        }

        Long catId = resolveCategory(dto.getCategoryName());
        long now   = System.currentTimeMillis();
        entryDAO.update(dto.getId(), dto.getTitle(), catId, dto.isFavorite(), now);
        fieldDAO.setFields(dto.getId(), dto.getFields(), key);
        tagDAO.setTagsForEntry(dto.getId(), dto.getTags());
    }

    public void toggleFavorite(long entryId, boolean favorite) throws Exception {
        entryDAO.toggleFavorite(entryId, favorite);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteEntry(long entryId) throws Exception {
        entryDAO.delete(entryId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long resolveCategory(String name) throws Exception {
        if (name == null || name.isBlank()) return null;
        return (long) categoryDAO.findOrCreate(name.trim()).getId();
    }

    private EntryDto toDto(Entry entry, SecretKey key) throws Exception {
        EntryDto dto = new EntryDto();
        dto.setId(entry.getId());
        dto.setType(entry.getType());
        dto.setTitle(entry.getTitle());
        dto.setFavorite(entry.isFavorite());
        dto.setCreatedAt(entry.getCreatedAt());
        dto.setUpdatedAt(entry.getUpdatedAt());
        dto.setFields(fieldDAO.getFields(entry.getId(), key));
        dto.setTags(tagDAO.findNamesByEntry(entry.getId()));

        if (entry.getCategoryId() != null) {
            Category cat = categoryDAO.findById(entry.getCategoryId().intValue());
            if (cat != null) dto.setCategoryName(cat.getName());
        }
        return dto;
    }
}
