package com.passwordmanager.model;

/**
 * Lightweight summary shown in the centre-panel entry list.
 * Only contains plaintext metadata – no decryption required.
 */
public class EntryListItem {
    private final long      id;
    private final EntryType type;
    private final String    title;
    private final String    categoryName;  // may be null
    private final boolean   favorite;
    private final long      updatedAt;

    public EntryListItem(long id, EntryType type, String title,
                         String categoryName, boolean favorite, long updatedAt) {
        this.id           = id;
        this.type         = type;
        this.title        = title;
        this.categoryName = categoryName;
        this.favorite     = favorite;
        this.updatedAt    = updatedAt;
    }

    public long      getId()           { return id; }
    public EntryType getType()         { return type; }
    public String    getTitle()        { return title; }
    public String    getCategoryName() { return categoryName; }
    public boolean   isFavorite()      { return favorite; }
    public long      getUpdatedAt()    { return updatedAt; }

    /** Used by ListView's default toString / search. */
    @Override
    public String toString() { return title; }
}
