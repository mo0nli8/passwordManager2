package com.passwordmanager.model;

/** One historical password version for a LOGIN entry. */
public class PasswordHistory {
    private long   id;
    private long   entryId;
    private String password;   // decrypted; never persisted in this form
    private long   changedAt;  // epoch millis

    public PasswordHistory() {}
    public PasswordHistory(long id, long entryId, String password, long changedAt) {
        this.id        = id;
        this.entryId   = entryId;
        this.password  = password;
        this.changedAt = changedAt;
    }

    public long   getId()              { return id; }
    public void   setId(long id)       { this.id = id; }
    public long   getEntryId()         { return entryId; }
    public void   setEntryId(long e)   { this.entryId = e; }
    public String getPassword()        { return password; }
    public void   setPassword(String p){ this.password = p; }
    public long   getChangedAt()       { return changedAt; }
    public void   setChangedAt(long t) { this.changedAt = t; }
}
