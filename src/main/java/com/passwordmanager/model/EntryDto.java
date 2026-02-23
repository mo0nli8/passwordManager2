package com.passwordmanager.model;

import java.util.*;

/**
 * Full entry with all fields decrypted – used by the service/UI layers.
 * Never persisted; lives only in memory while the vault is unlocked.
 */
public class EntryDto {
    private long              id;
    private EntryType         type;
    private String            title;
    private String            categoryName;
    private boolean           favorite;
    private Map<String,String> fields = new LinkedHashMap<>();  // fieldKey → plaintext value
    private List<String>      tags   = new ArrayList<>();
    private long              createdAt;
    private long              updatedAt;

    public EntryDto() {}

    // ── Convenience field accessors ───────────────────────────────────────────

    public String getField(String key) {
        return fields.getOrDefault(key, "");
    }

    public void setField(String key, String value) {
        if (value == null || value.isBlank()) fields.remove(key);
        else fields.put(key, value);
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public long               getId()                     { return id; }
    public void               setId(long id)              { this.id = id; }

    public EntryType          getType()                   { return type; }
    public void               setType(EntryType type)     { this.type = type; }

    public String             getTitle()                  { return title; }
    public void               setTitle(String title)      { this.title = title; }

    public String             getCategoryName()               { return categoryName; }
    public void               setCategoryName(String cat)     { this.categoryName = cat; }

    public boolean            isFavorite()                    { return favorite; }
    public void               setFavorite(boolean fav)        { this.favorite = fav; }

    public Map<String,String> getFields()                     { return fields; }
    public void               setFields(Map<String,String> f) { this.fields = f; }

    public List<String>       getTags()                       { return tags; }
    public void               setTags(List<String> tags)      { this.tags = tags; }

    public long               getCreatedAt()                  { return createdAt; }
    public void               setCreatedAt(long t)            { this.createdAt = t; }

    public long               getUpdatedAt()                  { return updatedAt; }
    public void               setUpdatedAt(long t)            { this.updatedAt = t; }
}
