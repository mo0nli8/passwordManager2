package com.passwordmanager.model;

/** Raw entry row as stored in the DB (no decrypted fields). */
public class Entry {
    private long      id;
    private EntryType type;
    private String    title;        // plaintext
    private Long      categoryId;
    private boolean   favorite;
    private long      createdAt;    // epoch millis
    private long      updatedAt;

    public Entry() {}

    public Entry(long id, EntryType type, String title, Long categoryId,
                 boolean favorite, long createdAt, long updatedAt) {
        this.id         = id;
        this.type       = type;
        this.title      = title;
        this.categoryId = categoryId;
        this.favorite   = favorite;
        this.createdAt  = createdAt;
        this.updatedAt  = updatedAt;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public long      getId()         { return id; }
    public void      setId(long id)  { this.id = id; }

    public EntryType getType()               { return type; }
    public void      setType(EntryType type) { this.type = type; }

    public String    getTitle()              { return title; }
    public void      setTitle(String title)  { this.title = title; }

    public Long      getCategoryId()                  { return categoryId; }
    public void      setCategoryId(Long categoryId)   { this.categoryId = categoryId; }

    public boolean   isFavorite()                { return favorite; }
    public void      setFavorite(boolean fav)    { this.favorite = fav; }

    public long      getCreatedAt()              { return createdAt; }
    public void      setCreatedAt(long t)        { this.createdAt = t; }

    public long      getUpdatedAt()              { return updatedAt; }
    public void      setUpdatedAt(long t)        { this.updatedAt = t; }
}
